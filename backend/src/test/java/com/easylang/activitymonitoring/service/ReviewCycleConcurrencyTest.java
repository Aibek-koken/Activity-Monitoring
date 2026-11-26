package com.easylang.activitymonitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.easylang.activitymonitoring.dto.ReviewDtos.ReviewDecisionRequest;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.Project;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.ReviewResult;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.NotificationRepository;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import com.easylang.activitymonitoring.repository.ProjectRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import com.easylang.activitymonitoring.storage.PrivateDocumentStorageService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class ReviewCycleConcurrencyTest {

    @Autowired private ChiefEditorReviewService chiefEditorReviewService;
    @Autowired private TranslatorProgressService translatorProgressService;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityTranslatorRepository assignmentRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectChiefEditorRepository editorRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PrivateDocumentStorageService documentStorage;

    private Long activityId;

    @AfterEach
    void clean() {
        if (activityId != null) {
            reviewRepository.findByActivityIdOrderByCycleNumberDesc(activityId).stream()
                    .filter(Review::hasDocument)
                    .forEach(review -> documentStorage.deleteIfExists(review.getDocumentStorageKey()));
            activityRepository.deleteById(activityId);
        }
    }

    @Test
    void concurrentDecisionsCloseReviewOnceAndNeverDuplicateCorrectionNotification() throws Exception {
        Activity activity = newActivity(ActivityStatus.UNDER_CHECKING);
        User editor = responsibleEditor(activity.getProject());
        Review review = new Review(activity, editor, 1, Instant.parse("2026-11-10T08:00:00Z"));
        review.attachDocument(UUID.randomUUID().toString(), "first.pdf", "application/pdf", 321, "a".repeat(64));
        review = reviewRepository.saveAndFlush(review);
        Long reviewId = review.getId();

        assertEquals(List.of(200, 409), concurrently(
                () -> statusOf(() -> chiefEditorReviewService.decide(
                        editor.getId(), reviewId, new ReviewDecisionRequest(ReviewResult.APPROVED, null, null)
                )),
                () -> statusOf(() -> chiefEditorReviewService.decide(
                        editor.getId(), reviewId,
                        new ReviewDecisionRequest(ReviewResult.CORRECTION_REQUESTED, "Fix section 2.", 2)
                ))
        ).stream().sorted().toList());

        Review persisted = reviewRepository.findById(reviewId).orElseThrow();
        assertTrue(persisted.getResult() == ReviewResult.APPROVED
                || persisted.getResult() == ReviewResult.CORRECTION_REQUESTED);
        long correctionNotifications = notificationRepository.findAll().stream()
                .filter(notification -> notification.getActivity() != null)
                .filter(notification -> notification.getActivity().getId().equals(activityId))
                .count();
        assertEquals(persisted.getResult() == ReviewResult.CORRECTION_REQUESTED ? 1 : 0, correctionNotifications);
    }

    @Test
    void concurrentCorrectionResubmissionsCreateExactlyOneNextCycle() throws Exception {
        Activity activity = newActivity(ActivityStatus.CORRECTION_REQUIRED);
        User editor = responsibleEditor(activity.getProject());
        Review review = new Review(activity, editor, 1, Instant.parse("2026-11-10T08:00:00Z"));
        review.attachDocument(UUID.randomUUID().toString(), "first.pdf", "application/pdf", 321, "a".repeat(64));
        review.close(
                ReviewResult.CORRECTION_REQUESTED,
                "Fix section 2.",
                2,
                Instant.parse("2026-11-10T10:00:00Z"),
                LocalDate.of(2026, 11, 10)
        );
        reviewRepository.saveAndFlush(review);
        User translator = userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow();

        assertEquals(List.of(200, 409), concurrently(
                () -> statusOf(() -> translatorProgressService.resubmitForChecking(
                        translator.getId(), activityId, validPdf("corrected-a.pdf", "a")
                )),
                () -> statusOf(() -> translatorProgressService.resubmitForChecking(
                        translator.getId(), activityId, validPdf("corrected-b.pdf", "b")
                ))
        ).stream().sorted().toList());

        assertEquals(2, reviewRepository.countByActivityId(activityId));
        assertEquals(ActivityStatus.UNDER_CHECKING, activityRepository.findById(activityId).orElseThrow().getStatus());
        assertEquals(2, reviewRepository.findTopByActivityIdOrderByCycleNumberDesc(activityId).orElseThrow().getCycleNumber());
    }

    private Activity newActivity(ActivityStatus status) {
        Project project = projectRepository.findByProjectName("Central Asia Legal Portal").orElseThrow();
        Activity activity = activityRepository.saveAndFlush(new Activity(
                "EL-REVIEW-" + UUID.randomUUID().toString().substring(0, 8),
                "Review concurrency test",
                project,
                status,
                LocalDate.of(2026, 11, 10)
        ));
        activityId = activity.getId();
        User translator = userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow();
        assignmentRepository.saveAndFlush(new ActivityTranslator(
                activity, translator, true, LocalDate.of(2026, 11, 10)
        ));
        return activity;
    }

    private User responsibleEditor(Project project) {
        return editorRepository.findByProjectIdAndResponsibleTrue(project.getId()).orElseThrow().getChiefEditor();
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

    private int statusOf(Runnable operation) {
        try {
            operation.run();
            return 200;
        } catch (ResponseStatusException exception) {
            HttpStatusCode status = exception.getStatusCode();
            return status.value();
        }
    }

    private List<Integer> concurrently(Supplier<Integer> first, Supplier<Integer> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> a = executor.submit(() -> {
                start.await();
                return first.get();
            });
            Future<Integer> b = executor.submit(() -> {
                start.await();
                return second.get();
            });
            start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
