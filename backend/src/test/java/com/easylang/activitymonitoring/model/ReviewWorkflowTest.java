package com.easylang.activitymonitoring.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReviewWorkflowTest {

    @Test
    void openReviewCanBeScheduledAndClosedOnlyOnce() {
        Review review = new Review(null, null, 1, Instant.parse("2026-11-10T08:00:00Z"));

        review.schedule(new BigDecimal("3.50"));
        review.close(
                ReviewResult.CORRECTION_REQUESTED,
                "Correct the terminology in section 2.",
                2,
                Instant.parse("2026-11-10T10:00:00Z"),
                LocalDate.of(2026, 11, 10)
        );

        assertEquals(new BigDecimal("3.50"), review.getExpectedCheckingHours());
        assertEquals(ReviewResult.CORRECTION_REQUESTED, review.getResult());
        assertEquals("Correct the terminology in section 2.", review.getMistakesNotes());
        assertEquals(Instant.parse("2026-11-10T10:00:00Z"), review.getDecidedAt());
        assertEquals(LocalDate.of(2026, 11, 10), review.getCheckingDate());
        assertNull(review.getOpenSlot());
        assertThrows(IllegalStateException.class, () -> review.schedule(new BigDecimal("4.00")));
        assertThrows(IllegalStateException.class, () -> review.close(
                ReviewResult.APPROVED,
                null,
                null,
                Instant.parse("2026-11-10T11:00:00Z"),
                LocalDate.of(2026, 11, 10)
        ));
    }

    @Test
    void activityMovesFromCheckingToCorrectionAndBackToChecking() {
        Activity activity = activity(ActivityStatus.UNDER_CHECKING);

        activity.requestCorrection();
        assertEquals(ActivityStatus.CORRECTION_REQUIRED, activity.getStatus());

        activity.resubmitForChecking();
        assertEquals(ActivityStatus.UNDER_CHECKING, activity.getStatus());
    }

    @Test
    void approvalCompletesOnlyAnActivityUnderChecking() {
        Activity activity = activity(ActivityStatus.UNDER_CHECKING);
        activity.completeReview();
        assertEquals(ActivityStatus.COMPLETED, activity.getStatus());

        assertThrows(IllegalStateException.class, activity::completeReview);
        assertThrows(IllegalStateException.class, activity::requestCorrection);
        assertThrows(IllegalStateException.class, activity::resubmitForChecking);
    }

    @Test
    void closedReviewCannotReceiveItsFirstDocument() {
        Review review = new Review(null, null, 1, Instant.parse("2026-11-10T08:00:00Z"));
        review.close(
                ReviewResult.APPROVED,
                null,
                null,
                Instant.parse("2026-11-10T10:00:00Z"),
                LocalDate.of(2026, 11, 10)
        );

        assertThrows(IllegalStateException.class, () -> review.attachDocument(
                "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                "late.pdf",
                "application/pdf",
                321,
                "a".repeat(64)
        ));
        assertFalse(review.hasDocument());
    }

    private Activity activity(ActivityStatus status) {
        return new Activity("EL-TEST", "Review workflow", null, status, LocalDate.of(2026, 11, 10));
    }
}
