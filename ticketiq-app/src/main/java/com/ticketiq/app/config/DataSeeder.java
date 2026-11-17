package com.ticketiq.app.config;

import com.ticketiq.app.kb.KbArticle;
import com.ticketiq.app.kb.KbRepository;
import com.ticketiq.app.ticket.Ticket;
import com.ticketiq.app.ticket.TicketRepository;
import com.ticketiq.app.ticket.TicketStatus;
import com.ticketiq.app.ticket.TriageSource;
import com.ticketiq.app.user.User;
import com.ticketiq.app.user.UserRepository;
import com.ticketiq.core.model.Role;
import com.ticketiq.core.seed.SeedData;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adds demo identities and a curated closed-incident corpus without changing existing tickets.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppProperties props;
    private final UserRepository users;
    private final KbRepository kb;
    private final TicketRepository tickets;
    private final PasswordEncoder encoder;

    public DataSeeder(AppProperties props, UserRepository users, KbRepository kb, TicketRepository tickets,
                      PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.kb = kb;
        this.tickets = tickets;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (!props.seed().enabled()) {
            return;
        }
        User admin = ensureUser("admin@ticketiq.local", "Alex Admin", Role.ADMIN, "90001", "Service Desk");
        List<User> agents = new ArrayList<>();
        agents.add(ensureUser("agent@ticketiq.local", "Asha Agent", Role.AGENT, "90002", "Service Desk"));
        agents.add(ensureUser("agent.hr@ticketiq.local", "Harper HR", Role.AGENT, "90004", "HR Systems"));
        agents.add(ensureUser("agent.finance@ticketiq.local", "Finley Finance", Role.AGENT, "90005", "Finance Systems"));
        agents.add(ensureUser("agent.engineering@ticketiq.local", "Taylor Technical", Role.AGENT, "90006", "Application Engineering"));
        agents.add(ensureUser("agent.access@ticketiq.local", "Ari Access", Role.AGENT, "90007", "Identity and Access"));
        ensureUser("user@ticketiq.local", "Uma User", Role.USER, "90003", null);

        if (kb.count() == 0) {
            for (SeedData.KbSeed a : SeedData.kbArticles()) {
                kb.save(new KbArticle(a.title(), a.content(), a.tags()));
            }
        }

        List<SeedData.ResolvedSeed> resolved = SeedData.resolvedTickets();
        int day = 1;
        int index = 0;
        for (SeedData.ResolvedSeed r : resolved) {
            if (tickets.existsBySeedKey(r.id())) {
                continue;
            }
            Ticket t = new Ticket(r.title(), r.description(), admin);
            t.setSeedKey(r.id());
            t.setCategory(r.category());
            t.setPriority(r.priority());
            t.setSeverityBand(com.ticketiq.app.ticket.SeverityBand.forPriority(r.priority()));
            t.setStatus(index++ % 2 == 0 ? TicketStatus.CLOSED : TicketStatus.RESOLVED);
            t.setTriageSource(TriageSource.MANUAL);
            t.setAssignee(agents.get(index % agents.size()));
            t.setResolution(r.resolution());
            t.setSupportTeam(r.team());
            t.setHandlingNotes(r.handling());
            t.setSelfService(r.selfService());
            t.setResolutionDays(r.resolutionDays());
            t.setExpectedResolutionDays(r.resolutionDays());
            Instant createdAt = Instant.now().minus(day++, ChronoUnit.DAYS);
            t.setCreatedAt(createdAt);
            t.setResolutionDueAt(createdAt.plus(r.resolutionDays(), ChronoUnit.DAYS));
            tickets.save(t);
        }
        log.info("Seeded demo data: {} users, {} articles, {} resolved tickets",
                users.count(), kb.count(),
                tickets.findByStatusIn(List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED)).size());
        log.info("Demo administrator account ready: {}", admin.getUserId());
    }

    private User ensureUser(String userId, String name, Role role, String employeeId, String team) {
        User user = users.findByUserIdIgnoreCase(userId).orElse(null);
        if (user == null) {
            return users.save(new User(userId, encoder.encode("12345678"), name, role, employeeId, team, true));
        }
        String oldDemoPassword = role == Role.ADMIN ? "Admin@123" : role == Role.USER ? "User@123" : "Agent@123";
        if (encoder.matches(oldDemoPassword, user.getPasswordHash())) {
            user.setPasswordHash(encoder.encode("12345678"));
        }
        user.setTeam(team);
        return users.save(user);
    }
}
