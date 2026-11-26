package com.easylang.activitymonitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.ActivityDetailResponse;
import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.SaveWeeklyEstimateRequest;
import com.easylang.activitymonitoring.dto.ReviewDtos.ReviewDecisionRequest;
import com.easylang.activitymonitoring.dto.ReviewDtos.UpdateExpectedTimeRequest;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.ReviewResult;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
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
import org.springframework.mock.web.MockMultipartFile;
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

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProjectChiefEditorRepository editorRepository;

    @Autowired
    private ChiefEditorReviewService chiefEditorReviewService;

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

    @Test
    void correctionResubmissionCreatesNextImmutableReviewCycle() {
        User translator = userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow();
        Activity activity = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow();
        activity.finish();
        activity.submitForChecking();
        User editor = editorRepository.findByProjectIdAndResponsibleTrue(activity.getProject().getId())
                .orElseThrow().getChiefEditor();
        Review first = new Review(activity, editor, 1, Instant.parse("2026-11-10T08:00:00Z"));
        first.attachDocument("cycle-1", "first.pdf", "application/pdf", 321, "a".repeat(64));
        first = reviewRepository.saveAndFlush(first);

        chiefEditorReviewService.updateExpectedTime(
                editor.getId(), first.getId(), new UpdateExpectedTimeRequest(new BigDecimal("2.50"))
        );
        chiefEditorReviewService.decide(
                editor.getId(),
                first.getId(),
                new ReviewDecisionRequest(ReviewResult.CORRECTION_REQUESTED, "Fix the legal terminology.", 2)
        );

        progressService.resubmitForChecking(
                translator.getId(),
                activity.getId(),
                validPdf("corrected.pdf", "corrected cycle")
        );

        var cycles = reviewRepository.findByActivityIdOrderByCycleNumberDesc(activity.getId());
        assertEquals(2, cycles.size());
        Review second = cycles.get(0);
        Review persistedFirst = cycles.get(1);
        assertEquals(2, second.getCycleNumber());
        assertEquals(editor.getId(), second.getChiefEditor().getId());
        assertEquals("corrected.pdf", second.getDocumentOriginalName());
        assertNotEquals(persistedFirst.getDocumentStorageKey(), second.getDocumentStorageKey());
        assertEquals(ActivityStatus.UNDER_CHECKING, second.getActivity().getStatus());

        assertEquals(ReviewResult.CORRECTION_REQUESTED, persistedFirst.getResult());
        assertEquals("Fix the legal terminology.", persistedFirst.getMistakesNotes());
        assertEquals(new BigDecimal("2.50"), persistedFirst.getExpectedCheckingHours());
        assertEquals("cycle-1", persistedFirst.getDocumentStorageKey());
        assertEquals("a".repeat(64), persistedFirst.getDocumentSha256());
    }

    private MockMultipartFile validPdf(String name, String marker) {
        String header = "%PDF-1.4\n";
        int catalogOffset = header.length();
        String catalog = "1 0 obj\n<</Type /Catalog /Pages 2 0 R>>\nendobj\n";
        int pagesOffset = catalogOffset + catalog.length();
        String pages = "2 0 obj\n<</Type /Pages /Kids [] /Count 0>>\nendobj\n";
        int markerOffset = pagesOffset + pages.length();
        String markerObject = "3 0 obj\n(" + marker + ")\nendobj\n";
        int xrefOffset = markerOffset + markerObject.length();
        String xref = "xref\n0 4\n0000000000 65535 f \n"
                + String.format(java.util.Locale.ROOT, "%010d 00000 n \n", catalogOffset)
                + String.format(java.util.Locale.ROOT, "%010d 00000 n \n", pagesOffset)
                + String.format(java.util.Locale.ROOT, "%010d 00000 n \n", markerOffset);
        String trailer = "trailer\n<</Size 4 /Root 1 0 R>>\nstartxref\n" + xrefOffset + "\n%%EOF\n";
        return new MockMultipartFile(
                "file", name, "application/pdf",
                (header + catalog + pages + markerObject + xref + trailer)
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        );
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
