package com.nexora.os.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Exercises real session login and CSRF, without bypassing Spring Security. */
public final class SessionTestSupport {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SessionTestSupport() {}

    public static RequestPostProcessor asUser(MockMvc mvc, String username, String password) throws Exception {
        MvcResult csrfResponse = mvc.perform(get("/api/session/csrf"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResponse.getRequest().getSession(false);
        if (session == null) throw new IllegalStateException("No session for CSRF");
        JsonNode csrf = JSON.readTree(csrfResponse.getResponse().getContentAsString());
        String header = csrf.path("headerName").asText();
        String token = csrf.path("token").asText();

        mvc.perform(post("/api/session/login").session(session)
                .header(header, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk());

        return request -> {
            request.setSession(session);
            request.addHeader(header, token);
            return request;
        };
    }

    /** Asserts a rejected login through the same real session API. */
    public static void expectLoginFailure(MockMvc mvc, String username, String password) throws Exception {
        MvcResult csrfResponse = mvc.perform(get("/api/session/csrf"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResponse.getRequest().getSession(false);
        JsonNode csrf = JSON.readTree(csrfResponse.getResponse().getContentAsString());
        mvc.perform(post("/api/session/login").session(session)
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isUnauthorized());
    }
}
