package com.ticketiq.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketiq.core.json.Json;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Who may call what, and what bad input looks like. Runs against an in-memory database. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:security", "ticketiq.ai.sweep-ms=600000"})
@AutoConfigureMockMvc
class SecurityAndValidationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anonymousRequestsGetJson401() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void publicEndpointsStayOpen() throws Exception {
        mvc.perform(get("/api/meta")).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiProvider").value("mock"))
                .andExpect(jsonPath("$.teams.ACCESS").value("Identity and Access"))
                .andExpect(jsonPath("$.teams.BILLING").value("Billing and Payments"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsRejectedWithoutSayingWhichPartWasWrong() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("userId", "user@ticketiq.local", "password", "nope"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong user ID or password"));
    }

    @Test
    void normalUsersCannotReachStaffEndpoints() throws Exception {
        String token = TestSupport.login(mvc, "user@ticketiq.local", "User@123");
        mvc.perform(get("/api/stats").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/kb").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ai/stats").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void agentsCannotUseAdminOnlyEndpoints() throws Exception {
        String token = TestSupport.login(mvc, "agent@ticketiq.local", "Agent@123");
        mvc.perform(get("/api/ai/stats").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/kb").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/kb").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("title", "Agent article", "content", "Not allowed to edit."))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminsCanUseAdministrativeEndpoints() throws Exception {
        String token = TestSupport.login(mvc, "admin@ticketiq.local", "Admin@123");
        mvc.perform(get("/api/ai/stats").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/kb").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void blankTicketFieldsReturnFieldErrors() throws Exception {
        String token = TestSupport.login(mvc, "user@ticketiq.local", "User@123");
        mvc.perform(post("/api/tickets").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("title", "", "description", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    void aTamperedTokenIsTreatedAsAnonymous() throws Exception {
        String token = TestSupport.login(mvc, "user@ticketiq.local", "User@123");
        mvc.perform(get("/api/tickets").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
    }
}
