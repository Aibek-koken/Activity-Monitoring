package com.easylang.activitymonitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.easylang.activitymonitoring.dto.ReviewDtos.ReviewDecisionRequest;
import com.easylang.activitymonitoring.dto.ReviewDtos.UpdateExpectedTimeRequest;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.NotificationType;
import com.easylang.activitymonitoring.model.ProjectChiefEditor;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.ReviewResult;
import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.NotificationRepository;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChiefEditorReviewServiceTest {

    @Autowired private ChiefEditorReviewService reviewService;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityTranslatorRepository assignmentRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ProjectChiefEditorRepository editorRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void correctionDecisionClosesReviewAndNotifiesOnlyResponsibleTranslatorOnce() {
        Review review = openReview("EL-2026-002");
        Long editorId = review.getChiefEditor().getId();

        var scheduled = reviewService.updateExpectedTime(
                editorId, review.getId(), new UpdateExpectedTimeRequest(new BigDecimal("3.50"))
        );
        assertEquals(new BigDecimal("3.50"), scheduled.expectedCheckingHours());

        var decided = reviewService.decide(
                editorId,
                review.getId(),
                new ReviewDecisionRequest(
                        ReviewResult.CORRECTION_REQUESTED,
                        "Correct the terminology in section 2.",
                        2
                )
        );

        assertEquals(ReviewResult.CORRECTION_REQUESTED, decided.result());
        assertEquals("Correct the terminology in section 2.", decided.feedback());
        assertEquals(ActivityStatus.CORRECTION_REQUIRED,
                activityRepository.findById(review.getActivity().getId()).orElseThrow().getStatus());
        Review persisted = reviewRepository.findById(review.getId()).orElseThrow();
        assertEquals(new BigDecimal("3.50"), persisted.getExpectedCheckingHours());
        assertNull(persisted.getOpenSlot());

        var notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size());
        assertEquals(NotificationType.CORRECTION_REQUESTED, notifications.get(0).getType());
        assertEquals(
                assignmentRepository.findByActivityIdAndResponsibleTrue(review.getActivity().getId())
                        .orElseThrow().getTranslator().getId(),
                notifications.get(0).getUser().getId()
        );
        assertEquals("review:" + review.getId() + ":correction-requested", notifications.get(0).getDedupeKey());

        assertConflict(() -> reviewService.decide(
                editorId, review.getId(), new ReviewDecisionRequest(ReviewResult.APPROVED, null, null)
        ));
        assertConflict(() -> reviewService.updateExpectedTime(
                editorId, review.getId(), new UpdateExpectedTimeRequest(new BigDecimal("4.00"))
        ));
        assertEquals(1, notificationRepository.count());
    }

    @Test
    void reviewOwnerKeepsAccessAfterProjectReassignmentAndReplacementEditorDoesNot() {
        Review review = openReview("EL-2026-002");
        User originalEditor = review.getChiefEditor();
        User replacement = userRepository.saveAndFlush(new User(
                "replacement-editor@easylang.local",
                "RE",
                "Replacement Editor",
                passwordEncoder.encode("Demo123!"),
                Role.CHIEF_EDITOR
        ));
        ProjectChiefEditor currentAssignment = editorRepository
                .findByProjectIdAndResponsibleTrue(review.getActivity().getProject().getId()).orElseThrow();
        editorRepository.delete(currentAssignment);
        editorRepository.flush();
        editorRepository.saveAndFlush(new ProjectChiefEditor(review.getActivity().getProject(), replacement, true));

        assertEquals(review.getId(), reviewService.getReview(originalEditor.getId(), review.getId()).id());
        assertNotFound(() -> reviewService.getReview(replacement.getId(), review.getId()));
        assertNotFound(() -> reviewService.updateExpectedTime(
                replacement.getId(), review.getId(), new UpdateExpectedTimeRequest(new BigDecimal("2.00"))
        ));
        assertNotFound(() -> reviewService.decide(
                replacement.getId(), review.getId(), new ReviewDecisionRequest(ReviewResult.APPROVED, null, null)
        ));
    }

    @Test
    void approvalCompletesActivityWithoutCorrectionNotification() {
        Review review = openReview("EL-2026-002");

        var decided = reviewService.decide(
                review.getChiefEditor().getId(),
                review.getId(),
                new ReviewDecisionRequest(ReviewResult.APPROVED, "Ready for delivery.", null)
        );

        assertEquals(ReviewResult.APPROVED, decided.result());
        assertEquals(ActivityStatus.COMPLETED,
                activityRepository.findById(review.getActivity().getId()).orElseThrow().getStatus());
        assertEquals(0, notificationRepository.count());
    }

    private Review openReview(String activityNumber) {
        Activity activity = activityRepository.findByActivityNumber(activityNumber).orElseThrow();
        activity.finish();
        activity.submitForChecking();
        User editor = editorRepository.findByProjectIdAndResponsibleTrue(activity.getProject().getId())
                .orElseThrow().getChiefEditor();
        Review review = new Review(activity, editor, 1, Instant.parse("2026-11-10T08:00:00Z"));
        review.attachDocument(
                UUID.randomUUID().toString(),
                "translation.pdf",
                "application/pdf",
                321,
                "a".repeat(64)
        );
        return reviewRepository.saveAndFlush(review);
    }

    private void assertConflict(Runnable operation) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, operation::run);
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    private void assertNotFound(Runnable operation) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, operation::run);
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
