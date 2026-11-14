package com.nexora.os.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "NEXORA_ADMIN_PASSWORD=integration-test-secret-123")
@AutoConfigureMockMvc
@Transactional
class SessionAuthenticationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private String createUser() {
        String username = "session-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (tenant_id,username,password_hash,role) " +
                "VALUES ('local-admin',?,?,'MEMBER')",
                username, encoder.encode("session-test-password-123"));
        return username;
    }

    private LoginContext login(String username, String password) throws Exception {
        MvcResult csrfResult = mvc.perform(get("/api/session/csrf"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        assertNotNull(session);
        JsonNode csrf = json.readTree(csrfResult.getResponse().getContentAsString());
        String token = csrf.path("token").asText();
        assertFalse(token.isBlank());
        MvcResult result = mvc.perform(post("/api/session/login").session(session)
                .header(csrf.path("headerName").asText(), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        assertEquals(username, json.readTree(result.getResponse().getContentAsString())
                .path("username").asText());
        return new LoginContext(session, token, csrf.path("headerName").asText());
    }

    @Test
    void sessionCanReadAndLogoutButWritesNeedCsrf() throws Exception {
        String username = createUser();
        LoginContext login = login(username, "session-test-password-123");
        mvc.perform(get("/api/me").session(login.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        mvc.perform(post("/api/me/password").session(login.session())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()); // CSRF checked before validation.
        mvc.perform(post("/api/me/password").session(login.session())
                .header(login.header(), login.csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()); // CSRF allowed, request invalid.

        mvc.perform(post("/api/session/logout").session(login.session())
                .header(login.header(), login.csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountLosesSessionAccessImmediately() throws Exception {
        String username = createUser();
        LoginContext login = login(username, "session-test-password-123");
        jdbc.update("UPDATE app_users SET enabled=false WHERE username=?", username);
        mvc.perform(get("/api/me").session(login.session()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordRejectedEvenWithValidCsrf() throws Exception {
        String username = createUser();
        MvcResult result = mvc.perform(get("/api/session/csrf"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        JsonNode csrf = json.readTree(result.getResponse().getContentAsString());
        mvc.perform(post("/api/session/login").session(session)
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "username", username, "password", "wrong-password-123"))))
                .andExpect(status().isUnauthorized());
    }

    private record LoginContext(MockHttpSession session, String csrf, String header) {}
}
