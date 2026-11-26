package com.easylang.activitymonitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.easylang.activitymonitoring.dto.TranslatorActivityDtos.SaveWeeklyEstimateRequest;
import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.Project;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.ProjectRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import com.easylang.activitymonitoring.repository.WeeklyEstimateRepository;
import com.easylang.activitymonitoring.storage.PrivateDocumentStorageService;
import java.math.BigDecimal;
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("postgres-query-test")
@EnabledIfEnvironmentVariable(named = "POSTGRES_INTEGRATION_TESTS", matches = "true")
class TranslatorSprint3PostgresConcurrencyTest {

    @Autowired private TranslatorProgressService progressService;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityTranslatorRepository assignmentRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WeeklyEstimateRepository estimateRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private PrivateDocumentStorageService documentStorage;

    private Long activityId;

    @AfterEach
    void clean() {
        if (activityId != null) {
            reviewRepository.findByActivityIdOrderByCycleNumberDesc(activityId).stream()
                    .filter(review -> review.getDocumentStorageKey() != null)
                    .forEach(review -> documentStorage.deleteIfExists(review.getDocumentStorageKey()));
            activityRepository.deleteById(activityId);
        }
    }

    @Test
    void concurrentEstimateFinishAndSubmitRequestsRemainConsistent() throws Exception {
        User translator = userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow();
        Project project = projectRepository.findByProjectName("Central Asia Legal Portal").orElseThrow();
        Activity activity = activityRepository.saveAndFlush(new Activity(
                "EL-CONC-" + UUID.randomUUID().toString().substring(0, 8),
                "Concurrent submission test", project, ActivityStatus.ASSIGNED, LocalDate.now()
        ));
        activityId = activity.getId();
        assignmentRepository.saveAndFlush(new ActivityTranslator(activity, translator, true, LocalDate.now()));

        assertEquals(List.of(200, 200), concurrently(
                () -> statusOf(() -> progressService.saveCurrentEstimate(translator.getId(), activityId,
                        new SaveWeeklyEstimateRequest(new BigDecimal("7.00")))),
                () -> statusOf(() -> progressService.saveCurrentEstimate(translator.getId(), activityId,
                        new SaveWeeklyEstimateRequest(new BigDecimal("8.00"))))
        ).stream().sorted().toList());
        assertEquals(1, estimateRepository.findByActivityIdOrderByWeekStartDateDesc(activityId).size());

        progressService.saveCurrentEstimate(translator.getId(), activityId,
                new SaveWeeklyEstimateRequest(BigDecimal.ZERO));
        assertEquals(List.of(200, 409), concurrently(
                () -> statusOf(() -> progressService.finish(translator.getId(), activityId)),
                () -> statusOf(() -> progressService.finish(translator.getId(), activityId))
        ).stream().sorted().toList());
        assertEquals(ActivityStatus.FINISHED, activityRepository.findById(activityId).orElseThrow().getStatus());

        assertEquals(List.of(200, 409), concurrently(
                () -> statusOf(() -> progressService.submitForChecking(translator.getId(), activityId, validPdf())),
                () -> statusOf(() -> progressService.submitForChecking(translator.getId(), activityId, validPdf()))
        ).stream().sorted().toList());
        assertEquals(ActivityStatus.UNDER_CHECKING, activityRepository.findById(activityId).orElseThrow().getStatus());
        assertEquals(1, reviewRepository.countByActivityId(activityId));
    }

    private MockMultipartFile validPdf() {
        String header = "%PDF-1.4\n";
        int catalogOffset = header.length();
        String catalog = "1 0 obj\n<</Type /Catalog /Pages 2 0 R>>\nendobj\n";
        int pagesOffset = catalogOffset + catalog.length();
        String pages = "2 0 obj\n<</Type /Pages /Kids [] /Count 0>>\nendobj\n";
        int xrefOffset = pagesOffset + pages.length();
        String xref = "xref\n0 3\n0000000000 65535 f \n"
                + String.format(java.util.Locale.ROOT, "%010d 00000 n \n", catalogOffset)
                + String.format(java.util.Locale.ROOT, "%010d 00000 n \n", pagesOffset);
        String trailer = "trailer\n<</Size 3 /Root 1 0 R>>\nstartxref\n" + xrefOffset + "\n%%EOF\n";
        return new MockMultipartFile(
                "file", "translation.pdf", "application/pdf",
                (header + catalog + pages + xref + trailer)
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
            Future<Integer> a = executor.submit(() -> { start.await(); return first.get(); });
            Future<Integer> b = executor.submit(() -> { start.await(); return second.get(); });
            start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
