package com.easylang.activitymonitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.NotificationType;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.model.WeeklyEstimate;
import com.easylang.activitymonitoring.model.WorkRecord;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.NotificationRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import com.easylang.activitymonitoring.repository.WeeklyEstimateRepository;
import com.easylang.activitymonitoring.repository.WorkRecordRepository;
import com.easylang.activitymonitoring.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReminderServiceIntegrationTest.ClockConfig.class)
class ReminderServiceIntegrationTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 23);
    private static final LocalDate WEEK_START = LocalDate.of(2026, 10, 19);

    @Autowired private ReminderService reminderService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkRecordRepository recordRepository;
    @Autowired private WeeklyEstimateRepository estimateRepository;
    @Autowired private MutableClock clock;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private Long createdRecordId;
    private Long createdEstimateId;

    @BeforeEach
    void reset() {
        notificationRepository.deleteAll();
        clock.set(Instant.parse("2026-10-23T10:59:00Z")); // 15:59 in Almaty
    }

    @AfterEach
    void clean() {
        notificationRepository.deleteAll();
        if (createdRecordId != null) recordRepository.deleteById(createdRecordId);
        if (createdEstimateId != null) estimateRepository.deleteById(createdEstimateId);
        createdRecordId = null;
        createdEstimateId = null;
    }

    @Test
    void cutoffsAndRepeatedRunsProduceOneRelevantReminderPerRequirement() {
        reminderService.evaluateDueReminders();
        assertEquals(0, notificationRepository.count());

        clock.set(Instant.parse("2026-10-23T11:00:00Z")); // Friday 16:00
        reminderService.evaluateDueReminders();
        reminderService.evaluateDueReminders();
        assertEquals(3, notificationRepository.count());
        assertEquals(3, notificationRepository.findDedupeKeys(NotificationType.WEEKLY_ESTIMATE_REMINDER, WEEK_START).size());

        clock.set(Instant.parse("2026-10-23T13:00:00Z")); // Friday 18:00
        reminderService.evaluateDueReminders();
        reminderService.evaluateDueReminders();
        assertEquals(5, notificationRepository.count());
        assertEquals(2, notificationRepository.findDedupeKeys(NotificationType.DAILY_RECORD_REMINDER, FRIDAY).size());

        Long mayaId = userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow().getId();
        assertEquals(3, reminderService.listVisible(mayaId).reminders().size());
        clock.set(Instant.parse("2026-10-24T13:00:00Z")); // Saturday
        reminderService.evaluateDueReminders();
        assertEquals(5, notificationRepository.count());
        assertEquals(2, reminderService.listVisible(mayaId).reminders().size()); // yesterday's daily reminder is stale
    }

    @Test
    void satisfyingDailyAndWeeklyRequirementsHidesReminders() {
        clock.set(Instant.parse("2026-10-23T13:00:00Z"));
        reminderService.evaluateDueReminders();
        User maya = userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow();
        Activity activity = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow();
        assertEquals(3, reminderService.listVisible(maya.getId()).reminders().size());

        createdRecordId = recordRepository.saveAndFlush(new WorkRecord(
                activity, maya, FRIDAY, new BigDecimal("1.00"), null
        )).getId();
        assertEquals(2, reminderService.listVisible(maya.getId()).reminders().size());

        createdEstimateId = estimateRepository.saveAndFlush(new WeeklyEstimate(
                activity, maya, WEEK_START, new BigDecimal("15.00"), clock.instant()
        )).getId();
        assertEquals(1, reminderService.listVisible(maya.getId()).reminders().size());
    }

    @Test
    void reminderApiEnforcesRoleOwnershipAndCsrf() throws Exception {
        clock.set(Instant.parse("2026-10-23T13:00:00Z"));
        reminderService.evaluateDueReminders();
        Long mayaId = userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow().getId();
        Long raviId = userRepository.findByEmailIgnoreCase("translator2@easylang.local").orElseThrow().getId();
        Long mayaReminderId = reminderService.listVisible(mayaId).reminders().get(0).id();
        Long raviReminderId = reminderService.listVisible(raviId).reminders().get(0).id();
        assertThrows(ResponseStatusException.class, () -> reminderService.markRead(mayaId, raviReminderId));

        Cookie translatorCookie = login("translator@easylang.local");
        mockMvc.perform(get("/api/v1/translator/reminders").cookie(translatorCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reminders.length()").value(3));
        mockMvc.perform(patch("/api/v1/translator/reminders/{id}/read", mayaReminderId).cookie(translatorCookie))
                .andExpect(status().isForbidden());

        Csrf csrf = csrf();
        mockMvc.perform(patch("/api/v1/translator/reminders/{id}/read", raviReminderId)
                        .cookie(translatorCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/translator/reminders/{id}/read", mayaReminderId)
                        .cookie(translatorCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent());
        assertEquals(2, reminderService.listVisible(mayaId).reminders().size());

        Cookie managerCookie = login("manager@easylang.local");
        mockMvc.perform(get("/api/v1/translator/reminders").cookie(managerCookie))
                .andExpect(status().isForbidden());
    }

    private Cookie login(String email) throws Exception {
        Csrf csrf = csrf();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Demo123!\"}"))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie(JwtAuthenticationFilter.ACCESS_COOKIE_NAME);
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Csrf(json.get("token").asText(), json.get("headerName").asText(),
                result.getResponse().getCookie("EASYLANG_XSRF_TOKEN"));
    }

    private record Csrf(String token, String headerName, Cookie cookie) {
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-10-23T10:59:00Z");
        private final ZoneId zone = ZoneId.of("Asia/Almaty");

        void set(Instant instant) {
            this.now = instant;
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
