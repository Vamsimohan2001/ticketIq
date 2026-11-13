package com.ticketiq.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketiq.core.json.Json;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Small helpers shared by the integration tests. */
final class TestSupport {

    private TestSupport() {
    }

    static String login(MockMvc mvc, String userId, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("userId", userId, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return Json.string(Json.parseObject(body), "accessToken");
    }
}
