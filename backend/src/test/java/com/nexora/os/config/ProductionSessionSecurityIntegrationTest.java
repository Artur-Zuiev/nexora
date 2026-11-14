package com.nexora.os.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Uses the single session-only security configuration.
 * Verifies that HTTP Basic cannot authenticate or bypass CSRF.
 */
@SpringBootTest(properties = "NEXORA_ADMIN_PASSWORD=integration-test-secret-123")
@AutoConfigureMockMvc
class ProductionSessionSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void basicHeadersNeverAuthenticateAndNeverBypassCsrf() throws Exception {
        String basic = "Basic " + Base64.getEncoder().encodeToString(
                "admin:integration-test-secret-123".getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, basic))
                .andExpect(status().isUnauthorized()); // Missing session gets a consistent 401.

        mvc.perform(post("/api/team")
                .header(HttpHeaders.AUTHORIZATION, basic)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"new-member\",\"password\":\"long-password-123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sessionLoginStillWorksWithoutLegacyBasic() throws Exception {
        MvcResult csrfResponse = mvc.perform(get("/api/session/csrf"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResponse.getRequest().getSession(false);
        JsonNode csrf = json.readTree(csrfResponse.getResponse().getContentAsString());

        mvc.perform(post("/api/session/login").session(session)
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "username", "admin", "password", "integration-test-secret-123"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
