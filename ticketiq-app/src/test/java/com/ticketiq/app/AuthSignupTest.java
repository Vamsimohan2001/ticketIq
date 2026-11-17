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

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:signup", "ticketiq.ai.sweep-ms=600000"})
@AutoConfigureMockMvc
class AuthSignupTest {

    @Autowired
    MockMvc mvc;

    @Test
    void signupRequiresAdministratorApprovalBeforeTheNewRoleCanSignIn() throws Exception {
        var signup = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("employeeId", "123456", "fullName", "Taylor User",
                                "userId", "taylor.user", "password", "StrongPass9!", "role", "USER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("taylor.user"))
                .andExpect(jsonPath("$.employeeId").value("123456"))
                .andExpect(jsonPath("$.fullName").value("Taylor User"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.approvalPending").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("userId", "TAYLOR.USER", "password", "StrongPass9!"))))
                .andExpect(status().isForbidden());

        long userId = Json.number(Json.parseObject(signup.andReturn().getResponse().getContentAsString()), "id").longValue();
        String admin = TestSupport.login(mvc, "admin@ticketiq.local", "12345678");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/users/" + userId + "/approval")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(Json.write(Map.of("approved", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("userId", "TAYLOR.USER", "password", "StrongPass9!", "role", "USER"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = Json.string(Json.parseObject(login), "accessToken");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("taylor.user"))
                .andExpect(jsonPath("$.employeeId").value("123456"))
                .andExpect(jsonPath("$.fullName").value("Taylor User"));
    }

    @Test
    void signupRejectsPasswordsWithoutAllRequiredCharacterTypes() throws Exception {
        for (String password : new String[] {"lowercase9!", "UPPERCASE9!", "NoNumber!", "NoSymbol99", "Short1!"}) {
            mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(Json.write(Map.of("employeeId", "7654321", "fullName", "Test User",
                                    "userId", "weak-" + password, "password", password))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password").exists());
        }
    }

    @Test
    void requestedAgentAccountsMustChooseAValidTeamAndStillNeedApproval() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("fullName", "Support Agent", "userId", "support.agent",
                                "password", "StrongPass9!", "role", "AGENT"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("fullName", "Support Agent", "userId", "support.agent",
                                "password", "StrongPass9!", "role", "AGENT", "team", "HR Systems"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.team").value("HR Systems"))
                .andExpect(jsonPath("$.approvalPending").value(true));
    }

    @Test
    void signupRejectsDuplicateUserIdsAndEmployeeIds() throws Exception {
        signup("345678", "duplicate.user");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("employeeId", "345678", "fullName", "Another User",
                                "userId", "another.user", "password", "StrongPass9!"))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("employeeId", "456789", "fullName", "Another User",
                                "userId", "duplicate.user", "password", "StrongPass9!"))))
                .andExpect(status().isConflict());
    }

    private void signup(String employeeId, String userId) throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("employeeId", employeeId, "fullName", "Test User",
                                "userId", userId, "password", "StrongPass9!"))))
                .andExpect(status().isCreated());
    }
}
