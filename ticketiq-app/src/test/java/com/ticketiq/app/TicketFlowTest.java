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

    @Test
    void ticketIsTriagedByTheAiThenOverriddenAndResolvedByAnAgent() throws Exception {
        String user = TestSupport.login(mvc, "user@ticketiq.local", "User@123");
        String agent = TestSupport.login(mvc, "agent@ticketiq.local", "Agent@123");

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
        String agent = TestSupport.login(mvc, "agent@ticketiq.local", "Agent@123");
        String user = TestSupport.login(mvc, "user@ticketiq.local", "User@123");
        String created = mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + agent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("title", "Agent's own ticket", "description", "Private to its owner."))))
                .andReturn().getResponse().getContentAsString();
        long id = Json.number(Json.parseObject(created), "id").longValue();
        mvc.perform(get("/api/tickets/" + id).header("Authorization", "Bearer " + user)).andExpect(status().isNotFound());
    }

    @Test
    void usersCannotBrowseResolvedIncidentHistoryButAgentsCan() throws Exception {
        String user = TestSupport.login(mvc, "user@ticketiq.local", "User@123");
        String agent = TestSupport.login(mvc, "agent@ticketiq.local", "Agent@123");

        mvc.perform(get("/api/tickets?status=RESOLVED").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items").isEmpty());

        String response = mvc.perform(get("/api/tickets?status=RESOLVED")
                        .header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> page = Json.parseObject(response);
        Map<String, Object> firstResolved = Json.asMap(Json.asList(page.get("items")).getFirst());
        long resolvedId = Json.number(firstResolved, "id").longValue();

        mvc.perform(get("/api/tickets/" + resolvedId).header("Authorization", "Bearer " + user))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tickets/" + resolvedId).header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk());
    }
}
