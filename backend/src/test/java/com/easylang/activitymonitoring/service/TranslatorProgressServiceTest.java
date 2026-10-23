package com.easylang.activitymonitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityDetailResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.SaveWeeklyEstimateRequest;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TranslatorProgressServiceTest.FixedBusinessClock.class)
@Transactional
class TranslatorProgressServiceTest {

    @Autowired
    private TranslatorProgressService progressService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void yearBoundaryUsesMondayInAlmatyAndPersistsOneCurrentEstimate() {
        Long translatorId = userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow().getId();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();

        ActivityDetailResponse saved = progressService.saveCurrentEstimate(
                translatorId, activityId, new SaveWeeklyEstimateRequest(new BigDecimal("42.50"))
        );

        assertEquals(LocalDate.of(2026, 12, 28), saved.currentWeek().startDate());
        assertEquals(LocalDate.of(2027, 1, 3), saved.currentWeek().endDate());
        assertEquals(LocalDate.of(2026, 12, 28), saved.currentWeekEstimate().weekStartDate());
        assertEquals(new BigDecimal("42.50"), saved.currentWeekEstimate().remainingTextVolume());
        assertEquals(1, saved.weeklyEstimates().size());
        assertFalse(saved.permissions().canFinish());
    }

    @TestConfiguration
    static class FixedBusinessClock {
        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-12-31T20:00:00Z"), ZoneId.of("Asia/Almaty"));
        }
    }
}
