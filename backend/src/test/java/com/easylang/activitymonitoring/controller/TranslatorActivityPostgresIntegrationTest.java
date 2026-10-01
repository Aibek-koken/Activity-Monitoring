package com.easylang.activitymonitoring.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.WorkRecord;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import com.easylang.activitymonitoring.repository.WorkRecordRepository;
import com.easylang.activitymonitoring.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres-query-test")
@EnabledIfEnvironmentVariable(named = "POSTGRES_INTEGRATION_TESTS", matches = "true")
class TranslatorActivityPostgresIntegrationTest {

    private static final LocalDate MUTATION_DATE = LocalDate.of(2026, 9, 27);

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

    @BeforeEach
    void cleanMutableDemoRecord() {
        Activity activity = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow();
        Long translatorId = userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow().getId();

        workRecordRepository.findByActivityIdAndTranslatorIdAndRecordDate(
                activity.getId(),
                translatorId,
                MUTATION_DATE
        ).ifPresent(workRecordRepository::delete);
    }

    @Test
    void translatorReadQueriesRunAgainstPostgres() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();

        mockMvc.perform(get("/api/v1/translator/activities").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[*].activityNumber", hasItem("EL-2026-001")))
                .andExpect(jsonPath("$.activities[*].activityNumber", hasItem("EL-2026-002")))
                .andExpect(jsonPath("$.activities[*].activityNumber", hasItem("EL-2026-003")))
                .andExpect(jsonPath("$.activities[*].activityNumber", not(hasItem("EL-2026-900"))));

        mockMvc.perform(get("/api/v1/translator/activities")
                        .queryParam("q", "003")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[*].activityName", everyItem(org.hamcrest.Matchers.is("Onboarding email sequence"))));

        mockMvc.perform(get("/api/v1/translator/activities")
                        .queryParam("q", "privacy")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[*].activityNumber", everyItem(org.hamcrest.Matchers.is("EL-2026-001"))));

        mockMvc.perform(get("/api/v1/translator/activities")
                        .queryParam("q", "does-not-exist")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities.length()").value(0));

        mockMvc.perform(get("/api/v1/translator/activities")
                        .queryParam("q", "%")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities.length()").value(0));

        mockMvc.perform(get("/api/v1/translator/activities/{activityId}", activityId).cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityNumber").value("EL-2026-001"))
                .andExpect(jsonPath("$.progress.totalTranslatedVolume").value(20.75))
                .andExpect(jsonPath("$.progress.recordCount").value(2));
    }

    @Test
    void translatorWriteQueriesRunAgainstPostgres() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Long activityId = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow().getId();
        Long translatorId = userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow().getId();
        Csrf createCsrf = csrf();

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activityId)
                        .cookie(accessCookie, createCsrf.cookie())
                        .header(createCsrf.headerName(), createCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"2026-09-27","translatedVolume":4.50,"workHours":1.25}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.totalTranslatedVolume").value(4.50))
                .andExpect(jsonPath("$.progress.recordCount").value(1))
                .andExpect(jsonPath("$.workRecords[0].recordDate").value("2026-09-27"))
                .andExpect(jsonPath("$.workRecords[0].translatedVolume").value(4.50));

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activityId)
                        .cookie(accessCookie, createCsrf.cookie())
                        .header(createCsrf.headerName(), createCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"2026-09-27","translatedVolume":2.00}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A work record already exists for this date"));

        WorkRecord record = workRecordRepository.findByActivityIdAndTranslatorIdAndRecordDate(
                activityId,
                translatorId,
                MUTATION_DATE
        ).orElseThrow();
        Csrf updateCsrf = csrf();

        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/work-records/{recordId}", activityId, record.getId())
                        .cookie(accessCookie, updateCsrf.cookie())
                        .header(updateCsrf.headerName(), updateCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"translatedVolume":6.75,"workHours":2.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.totalTranslatedVolume").value(6.75))
                .andExpect(jsonPath("$.progress.recordCount").value(1))
                .andExpect(jsonPath("$.workRecords[0].recordDate").value("2026-09-27"))
                .andExpect(jsonPath("$.workRecords[0].translatedVolume").value(6.75))
                .andExpect(jsonPath("$.workRecords[0].workHours").value(2.00));
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
