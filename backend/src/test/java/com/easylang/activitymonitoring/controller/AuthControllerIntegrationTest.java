package com.easylang.activitymonitoring.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.easylang.activitymonitoring.security.JwtAuthenticationFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validLoginReturnsUserAndSetsHttpOnlyCookie() throws Exception {
        Csrf csrf = csrf();

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"translator@easylang.local","password":"Demo123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(JwtAuthenticationFilter.ACCESS_COOKIE_NAME, true))
                .andExpect(cookie().value(JwtAuthenticationFilter.ACCESS_COOKIE_NAME, containsString(".")))
                .andExpect(jsonPath("$.user.role").value("TRANSLATOR"))
                .andExpect(jsonPath("$.user.fullName").value("Maya Chen"));
    }

    @Test
    void allSeededDemoAccountsCanLoginWithOriginalPassword() throws Exception {
        assertDemoLogin("translator@easylang.local", "TRANSLATOR");
        assertDemoLogin("editor@easylang.local", "CHIEF_EDITOR");
        assertDemoLogin("manager@easylang.local", "PROJECT_MANAGER");
    }

    @Test
    void invalidCredentialsAreRejectedWithGenericMessage() throws Exception {
        Csrf csrf = csrf();

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"translator@easylang.local","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void shortPasswordIsRejectedBeforeAuthentication() throws Exception {
        Csrf csrf = csrf();

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"translator@easylang.local","password":"123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("Password must be at least 8 characters"));
    }

    @Test
    void unauthenticatedRequestCannotOpenWorkspace() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/translator"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void translatorCannotOpenProjectManagerWorkspace() throws Exception {
        Cookie accessCookie = loginAsTranslator();

        mockMvc.perform(get("/api/v1/workspaces/project-manager").cookie(accessCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your role does not have access to this resource"));
    }

    @Test
    void localDevelopmentOriginIsAllowedByCors() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
    }

    private Cookie loginAsTranslator() throws Exception {
        Csrf csrf = csrf();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"translator@easylang.local","password":"Demo123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie(JwtAuthenticationFilter.ACCESS_COOKIE_NAME);
    }

    private void assertDemoLogin(String email, String expectedRole) throws Exception {
        Csrf csrf = csrf();
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Demo123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(JwtAuthenticationFilter.ACCESS_COOKIE_NAME, true))
                .andExpect(jsonPath("$.user.role").value(expectedRole));
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Csrf(
                json.get("token").asText(),
                json.get("headerName").asText(),
                result.getResponse().getCookie("EASYLANG_XSRF_TOKEN")
        );
    }

    private record Csrf(String token, String headerName, Cookie cookie) {
    }
}
