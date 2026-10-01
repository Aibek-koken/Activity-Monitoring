package com.easylang.activitymonitoring.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class TranslatorActivityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ActivityRepository activityRepository;

    @Test
    void unauthenticatedUserCannotListTranslatorActivities() throws Exception {
        mockMvc.perform(get("/api/v1/translator/activities"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void projectManagerCannotListTranslatorActivities() throws Exception {
        Cookie accessCookie = login("manager@easylang.local");

        mockMvc.perform(get("/api/v1/translator/activities").cookie(accessCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void translatorSeesOnlyAssignedActivities() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");

        mockMvc.perform(get("/api/v1/translator/activities").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[*].activityNumber", hasItem("EL-2026-001")))
                .andExpect(jsonPath("$.activities[*].activityNumber", hasItem("EL-2026-002")))
                .andExpect(jsonPath("$.activities[*].activityNumber", hasItem("EL-2026-003")))
                .andExpect(jsonPath("$.activities[*].activityNumber", not(hasItem("EL-2026-900"))));
    }

    @Test
    void translatorCanSearchAssignedActivitiesByNumberOrName() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");

        mockMvc.perform(get("/api/v1/translator/activities")
                        .queryParam("q", "privacy")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[*].activityNumber", everyItem(org.hamcrest.Matchers.is("EL-2026-001"))));

        mockMvc.perform(get("/api/v1/translator/activities")
                        .queryParam("q", "003")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[*].activityName", everyItem(org.hamcrest.Matchers.is("Onboarding email sequence"))));
    }

    @Test
    void translatorCanViewAssignedActivityDetailsAndProgress() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();

        mockMvc.perform(get("/api/v1/translator/activities/{activityId}", activityId).cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityNumber").value("EL-2026-001"))
                .andExpect(jsonPath("$.activityName").value("Privacy policy translation"))
                .andExpect(jsonPath("$.projectName").value("Central Asia Legal Portal"))
                .andExpect(jsonPath("$.progress.totalTranslatedPages").value(20.75))
                .andExpect(jsonPath("$.progress.recordCount").value(2))
                .andExpect(jsonPath("$.progress.lastRecordDate").value("2026-09-30"))
                .andExpect(jsonPath("$.workRecords[0].translatedVolumePages").value(8.25));
    }

    @Test
    void translatorCannotViewUnassignedActivityDetails() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Long activityId = activityRepository.findByActivityNumber("EL-2026-900").orElseThrow().getId();

        mockMvc.perform(get("/api/v1/translator/activities/{activityId}", activityId).cookie(accessCookie))
                .andExpect(status().isNotFound());
    }

    private Cookie login(String email) throws Exception {
        Csrf csrf = csrf();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Demo123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie(JwtAuthenticationFilter.ACCESS_COOKIE_NAME);
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
