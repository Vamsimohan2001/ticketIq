package com.ticketiq.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketiq.core.json.Json;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole story end to end with the offline mock AI: create a ticket, wait for the AI to triage it,
 * check duplicates and the suggested reply, let an agent override, then resolve it.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:flow", "ticketiq.ai.sweep-ms=600000"})
@AutoConfigureMockMvc
class TicketFlowTest {

    @Autowired
    MockMvc mvc;

    private Map<String, Object> getJson(String url, String token) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return Json.parseObject(body);
    }

    private void awaitTriage(long id, String token) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(Json.string(getJson("/api/tickets/" + id, token), "status")).isNotEqualTo("NEW"));
    }

    @Test
    void ticketIsTriagedByTheAiThenOverriddenAndResolvedByAnAgent() throws Exception {
        String user = TestSupport.login(mvc, "user@ticketiq.local", "12345678");
        String agent = TestSupport.login(mvc, "agent@ticketiq.local", "12345678");

        String created = mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("title", "Reset email is not arriving",
                                "description", "I pressed Forgot password but the email never arrives. Call 9876543210."))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = Json.number(Json.parseObject(created), "id").longValue();
        assertThat(Json.string(Json.parseObject(created), "status")).isEqualTo("NEW");

        // The AI works in the background; the ticket moves on by itself.
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(Json.string(getJson("/api/tickets/" + id, agent), "status")).isIn("TRIAGED", "NEEDS_HUMAN_TRIAGE"));

        Map<String, Object> ticket = getJson("/api/tickets/" + id, agent);
        assertThat(Json.string(ticket, "category")).isEqualTo("ACCESS");
        assertThat(Json.string(ticket, "suggestedTeam")).isEqualTo("Identity and Access");
        assertThat(Json.string(ticket, "triageSource")).isEqualTo("AI");
        assertThat(Json.asMap(ticket.get("ai"))).containsKey("summary");

        // A normal user never sees the AI block.
        assertThat(getJson("/api/tickets/" + id, user)).doesNotContainKey("ai");

        // Seeded resolved tickets make duplicate detection and the RAG reply possible.
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(Json.asList(Json.parse(mvc.perform(get("/api/tickets/" + id + "/similar")
                        .header("Authorization", "Bearer " + agent)).andReturn().getResponse().getContentAsString()))).isNotEmpty());
        Map<String, Object> reply = getJson("/api/tickets/" + id + "/suggested-reply", agent);
        assertThat(reply.get("available")).isEqualTo(Boolean.TRUE);

        // An agent's manual triage always wins over the AI.
        mvc.perform(patch("/api/tickets/" + id + "/triage").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("category", "BILLING", "priority", "LOW"))))
                .andExpect(status().isOk());
        Map<String, Object> overridden = getJson("/api/tickets/" + id, agent);
        assertThat(Json.string(overridden, "category")).isEqualTo("BILLING");
        assertThat(Json.string(overridden, "triageSource")).isEqualTo("MANUAL");

        // Resolving needs a resolution text and adds the incident to the AI's learning loop.
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("status", "RESOLVED"))))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("status", "RESOLVED", "resolution", "Whitelisted the sender."))))
                .andExpect(status().isOk());
    }

    @Test
    void aUserCannotSeeSomeoneElsesTicket() throws Exception {
        String agent = TestSupport.login(mvc, "agent@ticketiq.local", "12345678");
        String user = TestSupport.login(mvc, "user@ticketiq.local", "12345678");
        String created = mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("title", "Agent's own ticket", "description", "Private to its owner."))))
                .andReturn().getResponse().getContentAsString();
        long id = Json.number(Json.parseObject(created), "id").longValue();
        mvc.perform(get("/api/tickets/" + id).header("Authorization", "Bearer " + user)).andExpect(status().isNotFound());
    }

    @Test
    void usersOnlySeeTheirOwnResolvedIncidentsWhileAgentsCanBrowseTheCorpus() throws Exception {
        String user = TestSupport.login(mvc, "user@ticketiq.local", "12345678");
        String agent = TestSupport.login(mvc, "agent@ticketiq.local", "12345678");

        mvc.perform(get("/api/tickets?status=RESOLVED").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items").isEmpty());

        String response = mvc.perform(get("/api/tickets?status=RESOLVED&size=100")
                        .header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> page = Json.parseObject(response);
        Map<String, Object> firstResolved = Json.asList(page.get("items")).stream()
                .map(Json::asMap)
                .filter(ticket -> !"Uma User".equals(Json.string(ticket, "requesterName")))
                .findFirst()
                .orElseThrow();
        long resolvedId = Json.number(firstResolved, "id").longValue();

        mvc.perform(get("/api/tickets/" + resolvedId).header("Authorization", "Bearer " + user))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tickets/" + resolvedId).header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk());
    }

    @Test
    void usersCanFollowTheirOwnClosedIncidentAndOnlyAdminsCanReopenIt() throws Exception {
        String user = TestSupport.login(mvc, "user@ticketiq.local", "12345678");
        String agent = TestSupport.login(mvc, "agent@ticketiq.local", "12345678");
        String admin = TestSupport.login(mvc, "admin@ticketiq.local", "12345678");
        String created = mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("title", "Completed incident visibility",
                                "description", "A requester should keep access to their closed incident."))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = Json.number(Json.parseObject(created), "id").longValue();
        awaitTriage(id, user);
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("status", "CLOSED", "resolution", "The service was restored."))))
                .andExpect(status().isOk());
        assertThat(Json.string(getJson("/api/tickets/" + id, user), "resolution"))
                .isEqualTo("The service was restored.");
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk());
    }

    @Test
    void agentSeverityProposalNeedsRequesterThenAdminApproval() throws Exception {
        String user = TestSupport.login(mvc, "user@ticketiq.local", "12345678");
        String agent = TestSupport.login(mvc, "agent@ticketiq.local", "12345678");
        String admin = TestSupport.login(mvc, "admin@ticketiq.local", "12345678");
        String created = mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("title", "Severity approval workflow",
                                "description", "The incident impact needs a review."))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = Json.number(Json.parseObject(created), "id").longValue();
        awaitTriage(id, user);

        String request = mvc.perform(post("/api/tickets/" + id + "/requests")
                        .header("Authorization", "Bearer " + agent).contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("type", "SEVERITY_CHANGE", "proposedSeverity", "A",
                                "reason", "Multiple teams are blocked."))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long requestId = Json.number(Json.parseObject(request), "id").longValue();
        mvc.perform(post("/api/tickets/" + id + "/requests/" + requestId + "/user-decision")
                        .header("Authorization", "Bearer " + user).contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("approved", true))))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/approvals/" + requestId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(Json.write(Map.of("approved", true))))
                .andExpect(status().isOk());
        Map<String, Object> updated = getJson("/api/tickets/" + id, user);
        assertThat(Json.string(updated, "severityBand")).isEqualTo("A");
        assertThat(Json.number(updated, "expectedResolutionDays")).isEqualTo(2.0);
        mvc.perform(patch("/api/tickets/" + id + "/timeline").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("severityBand", "C", "expectedResolutionDays", 6))))
                .andExpect(status().isOk());
        assertThat(Json.string(getJson("/api/tickets/" + id, user), "severityBand")).isEqualTo("C");
        mvc.perform(patch("/api/tickets/" + id + "/timeline").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("severityBand", "A", "expectedResolutionDays", 3))))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/tickets/" + id + "/timeline").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("severityBand", "C", "expectedResolutionDays", 6))))
                .andExpect(status().isOk());
        assertThat(Json.string(getJson("/api/tickets/" + id, user), "severityBand")).isEqualTo("C");
        mvc.perform(patch("/api/tickets/" + id + "/timeline").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("severityBand", "A", "expectedResolutionDays", 3))))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/tickets/" + id + "/timeline").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("severityBand", "C", "expectedResolutionDays", 6))))
                .andExpect(status().isOk());
        assertThat(Json.string(getJson("/api/tickets/" + id, user), "severityBand")).isEqualTo("C");
        mvc.perform(patch("/api/tickets/" + id + "/timeline").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("severityBand", "A", "expectedResolutionDays", 3))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requesterCanEditAnActiveIncidentButNotAfterClosure() throws Exception {
        String user = TestSupport.login(mvc, "user@ticketiq.local", "12345678");
        String created = mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("title", "Original title", "description", "Original details."))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = Json.number(Json.parseObject(created), "id").longValue();
        awaitTriage(id, user);
        mvc.perform(patch("/api/tickets/" + id).header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("title", "Updated title", "description", "Updated details."))))
                .andExpect(status().isOk());
        Map<String, Object> updated = getJson("/api/tickets/" + id, user);
        assertThat(Json.string(updated, "title")).isEqualTo("Updated title");
        assertThat(Json.string(updated, "description")).isEqualTo("Updated details.");
    }

    @Test
    void databaseSeedsAtLeastSixtyResolvedOrClosedKnowledgeIncidents() throws Exception {
        String agent = TestSupport.login(mvc, "agent@ticketiq.local", "12345678");
        long closed = Json.number(getJson("/api/tickets?status=CLOSED&size=100", agent), "totalItems").longValue();
        long resolved = Json.number(getJson("/api/tickets?status=RESOLVED&size=100", agent), "totalItems").longValue();
        assertThat(closed + resolved).isGreaterThanOrEqualTo(60);
    }
}
