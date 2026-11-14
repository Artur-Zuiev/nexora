package com.nexora.os.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "NEXORA_ADMIN_PASSWORD=integration-test-secret-123")
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/leads")).andExpect(status().isUnauthorized());
    }

    @Test
    void incorrectPasswordIsRejected() throws Exception {
        SessionTestSupport.expectLoginFailure(mvc, "admin", "incorrect-password");
    }

    @Test
    void configuredAdminCanReadLeads() throws Exception {
        mvc.perform(get("/api/leads").with(
                SessionTestSupport.asUser(mvc, "admin", "integration-test-secret-123")))
                .andExpect(status().isOk());
    }
}
