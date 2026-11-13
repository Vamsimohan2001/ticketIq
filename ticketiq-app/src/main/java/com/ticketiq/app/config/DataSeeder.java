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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Fills an empty database with demo data: three users, ten help articles and fifteen resolved tickets,
 * so duplicate detection and suggested replies have something to work with on the very first run.
 * Demo passwords are listed in the README and are for local use only.
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
        if (!props.seed().enabled() || users.count() > 0) {
            return;
        }
        User admin = users.save(new User("admin@ticketiq.local", encoder.encode("Admin@123"), "Alex Admin", Role.ADMIN, "90001"));
        User agent = users.save(new User("agent@ticketiq.local", encoder.encode("Agent@123"), "Asha Agent", Role.AGENT, "90002"));
        User user = users.save(new User("user@ticketiq.local", encoder.encode("User@123"), "Uma User", Role.USER, "90003"));

        for (SeedData.KbSeed a : SeedData.kbArticles()) {
            kb.save(new KbArticle(a.title(), a.content(), a.tags()));
        }

        List<SeedData.ResolvedSeed> resolved = SeedData.resolvedTickets();
        int day = resolved.size();
        for (SeedData.ResolvedSeed r : resolved) {
            Ticket t = new Ticket(r.title(), r.description(), user);
            t.setCategory(r.category());
            t.setPriority(r.priority());
            t.setStatus(TicketStatus.RESOLVED);
            t.setTriageSource(TriageSource.MANUAL);
            t.setAssignee(agent);
            t.setResolution(r.resolution());
            t.setCreatedAt(Instant.now().minus(day--, ChronoUnit.DAYS));
            tickets.save(t);
        }
        log.info("Seeded demo data: {} users, {} articles, {} resolved tickets",
                users.count(), kb.count(), resolved.size());
        log.info("Demo sign-ins: {} / agent / user (see README for the passwords)", admin.getUserId());
    }
}
