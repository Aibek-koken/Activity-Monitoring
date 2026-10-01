package com.easylang.activitymonitoring.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import com.easylang.activitymonitoring.repository.WorkRecordRepository;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TranslatorActivityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkRecordRepository workRecordRepository;

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
    void projectManagerCannotOpenTranslatorActivityDetails() throws Exception {
        Cookie accessCookie = login("manager@easylang.local");
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();

        mockMvc.perform(get("/api/v1/translator/activities/{activityId}", activityId).cookie(accessCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void projectManagerCannotCreateTranslatorWorkRecord() throws Exception {
        Cookie accessCookie = login("manager@easylang.local");
        Csrf csrf = csrf();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activityId)
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"2026-09-28","translatedVolume":3.00}
                                """))
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
                .andExpect(jsonPath("$.progress.totalTranslatedVolume").value(20.75))
                .andExpect(jsonPath("$.progress.recordCount").value(2))
                .andExpect(jsonPath("$.progress.lastRecordDate").value("2026-09-30"))
                .andExpect(jsonPath("$.workRecords[0].translatedVolume").value(8.25));
    }

    @Test
    void translatorCannotViewUnassignedActivityDetails() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Long activityId = activityRepository.findByActivityNumber("EL-2026-900").orElseThrow().getId();

        mockMvc.perform(get("/api/v1/translator/activities/{activityId}", activityId).cookie(accessCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void translatorCannotCreateWorkRecordForUnassignedActivity() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-900").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activityId)
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"2026-09-28","translatedVolume":3.00}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void translatorCanCreateDailyWorkRecordAndProgressUpdates() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activityId)
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"2026-09-27","translatedVolume":4.50,"workHours":1.25}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.progress.totalTranslatedVolume").value(4.50))
                .andExpect(jsonPath("$.progress.recordCount").value(1))
                .andExpect(jsonPath("$.progress.lastRecordDate").value("2026-09-27"))
                .andExpect(jsonPath("$.workRecords[0].translatedVolume").value(4.50))
                .andExpect(jsonPath("$.workRecords[0].workHours").value(1.25));
    }

    @Test
    void duplicateDailyWorkRecordIsRejected() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activityId)
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"2026-09-30","translatedVolume":3.00}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A work record already exists for this date"));
    }

    @Test
    void futureDailyWorkRecordDateIsRejected() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activityId)
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"2999-01-01","translatedVolume":3.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Record date cannot be in the future"));
    }

    @Test
    void translatorCanEditOwnDailyWorkRecordWithoutChangingDate() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf createCsrf = csrf();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activityId)
                        .cookie(accessCookie, createCsrf.cookie())
                        .header(createCsrf.headerName(), createCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"2026-09-28","translatedVolume":2.00,"workHours":0.50}
                                """))
                .andExpect(status().isOk());

        Long recordId = workRecordRepository
                .findByActivityIdAndTranslatorIdAndRecordDate(
                        activityId,
                        userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow().getId(),
                        java.time.LocalDate.of(2026, 9, 28)
                )
                .orElseThrow()
                .getId();
        Csrf updateCsrf = csrf();

        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/work-records/{recordId}", activityId, recordId)
                        .cookie(accessCookie, updateCsrf.cookie())
                        .header(updateCsrf.headerName(), updateCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"translatedVolume":6.75,"workHours":1.50}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.totalTranslatedVolume").value(6.75))
                .andExpect(jsonPath("$.progress.recordCount").value(1))
                .andExpect(jsonPath("$.workRecords[0].recordDate").value("2026-09-28"))
                .andExpect(jsonPath("$.workRecords[0].translatedVolume").value(6.75))
                .andExpect(jsonPath("$.workRecords[0].workHours").value(1.50));
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
