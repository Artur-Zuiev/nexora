package com.nexora.os.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "NEXORA_ADMIN_PASSWORD=integration-test-secret-123")
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/leads")).andExpect(status().isUnauthorized());
    }

    @Test
    void incorrectPasswordIsRejected() throws Exception {
        mvc.perform(get("/api/leads")
                .header(HttpHeaders.AUTHORIZATION, basic("admin", "incorrect-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void configuredAdminCanReadLeads() throws Exception {
        mvc.perform(get("/api/leads")
                .header(HttpHeaders.AUTHORIZATION, basic("admin", "integration-test-secret-123")))
                .andExpect(status().isOk());
    }

    private static String basic(String user, String password) {
        String value = Base64.getEncoder().encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + value;
    }
}
