package com.ticketiq.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketiq.core.json.Json;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seed-password-configuration",
        "ticketiq.seed.password=RenderDemoPass9!"
})
@AutoConfigureMockMvc
class SeedPasswordConfigurationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void seededAdministratorUsesTheConfiguredInitialPassword() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("userId", "admin@ticketiq.local",
                                "password", "RenderDemoPass9!", "role", "ADMIN"))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("userId", "admin@ticketiq.local",
                                "password", "12345678", "role", "ADMIN"))))
                .andExpect(status().isUnauthorized());
    }
}