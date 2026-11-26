package com.easylang.activitymonitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("postgres-query-test")
@EnabledIfEnvironmentVariable(named = "POSTGRES_INTEGRATION_TESTS", matches = "true")
class Sprint4PostgresAcceptanceTest {

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
    @Autowired private DataSource dataSource;

    private Long activityId;

    @AfterEach
    void cleanCreatedActivity() {
        if (activityId == null) {
            return;
        }
        reviewRepository.findByActivityIdOrderByCycleNumberDesc(activityId).stream()
                .filter(Review::hasDocument)
                .forEach(review -> documentStorage.deleteIfExists(review.getDocumentStorageKey()));
        activityRepository.deleteById(activityId);
        activityId = null;
    }

    @Test
    void postgresMigratesAFilelessV4ReviewAndEnforcesV5Constraints() {
        String schema = "sprint4_migration_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA " + schema);

        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .target("4")
                    .load()
                    .migrate();
            seedOpenSprint3Review(jdbc, schema);

            var result = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .target("5.2")
                    .load()
                    .migrate();

            assertEquals("5.2", result.targetSchemaVersion.toString());
            Map<String, Object> migrated = jdbc.queryForMap("""
                    SELECT result, open_slot, document_storage_key, document_original_name,
                           document_content_type, document_size_bytes, document_sha256,
                           expected_checking_hours, decided_at
                    FROM %s.reviews WHERE id = 1
                    """.formatted(schema));
            assertNull(migrated.get("result"));
            assertEquals(1, ((Number) migrated.get("open_slot")).intValue());
            assertNull(migrated.get("document_storage_key"));
            assertNull(migrated.get("document_original_name"));
            assertNull(migrated.get("document_content_type"));
            assertNull(migrated.get("document_size_bytes"));
            assertNull(migrated.get("document_sha256"));
            assertNull(migrated.get("expected_checking_hours"));
            assertNull(migrated.get("decided_at"));

            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO %s.reviews (activity_id, chief_editor_id, cycle_number, submitted_at)
                    VALUES (1, 2, 2, CURRENT_TIMESTAMP)
                    """.formatted(schema)));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    UPDATE %s.reviews SET result = 'REJECTED', open_slot = NULL WHERE id = 1
                    """.formatted(schema)));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    UPDATE %s.reviews SET document_storage_key = 'orphan-key' WHERE id = 1
                    """.formatted(schema)));

            jdbc.update("""
                    INSERT INTO %s.notifications
                        (user_id, activity_id, type, period_date, dedupe_key, message, created_at)
                    VALUES (1, 1, 'CORRECTION_REQUESTED', CURRENT_DATE,
                            'review:1:correction', 'Corrections requested', CURRENT_TIMESTAMP)
                    """.formatted(schema));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".notifications WHERE type = 'CORRECTION_REQUESTED'",
                    Integer.class
            ));
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void postgresRowLocksAllowOnlyOneConcurrentReviewDecision() throws Exception {
        Activity activity = newActivity(ActivityStatus.UNDER_CHECKING);
        User editor = responsibleEditor(activity.getProject());
        Review review = new Review(activity, editor, 1, Instant.parse("2026-11-10T08:00:00Z"));
        review.attachDocument(UUID.randomUUID().toString(), "first.pdf", "application/pdf", 321, "a".repeat(64));
        Long reviewId = reviewRepository.saveAndFlush(review).getId();

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
    void postgresRowLocksAllowOnlyOneConcurrentCorrectionUpload() throws Exception {
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
        assertEquals(2, reviewRepository.findTopByActivityIdOrderByCycleNumberDesc(activityId)
                .orElseThrow().getCycleNumber());
    }

    private void seedOpenSprint3Review(JdbcTemplate jdbc, String schema) {
        jdbc.update("""
                INSERT INTO %s.app_users (id, email, initials, full_name, password_hash, role)
                VALUES
                    (1, 'translator@example.test', 'TR', 'Test Translator', 'not-used', 'TRANSLATOR'),
                    (2, 'editor@example.test', 'ED', 'Test Editor', 'not-used', 'CHIEF_EDITOR'),
                    (3, 'manager@example.test', 'PM', 'Test Manager', 'not-used', 'PROJECT_MANAGER')
                """.formatted(schema));
        jdbc.update("""
                INSERT INTO %s.projects (id, project_name, project_manager_id, created_date)
                VALUES (1, 'Migration Project', 3, CURRENT_DATE)
                """.formatted(schema));
        jdbc.update("""
                INSERT INTO %s.activities (id, activity_number, activity_name, project_id, status, created_date)
                VALUES (1, 'EL-MIGRATION', 'Legacy review', 1, 'UNDER_CHECKING', CURRENT_DATE)
                """.formatted(schema));
        jdbc.update("""
                INSERT INTO %s.reviews (id, activity_id, chief_editor_id, cycle_number, submitted_at)
                VALUES (1, 1, 2, 1, CURRENT_TIMESTAMP)
                """.formatted(schema));
    }

    private Activity newActivity(ActivityStatus status) {
        Project project = projectRepository.findByProjectName("Central Asia Legal Portal").orElseThrow();
        Activity activity = activityRepository.saveAndFlush(new Activity(
                "EL-PG-" + UUID.randomUUID().toString().substring(0, 8),
                "PostgreSQL Sprint 4 acceptance",
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
                + String.format(Locale.ROOT, "%010d 00000 n \n", catalogOffset)
                + String.format(Locale.ROOT, "%010d 00000 n \n", pagesOffset)
                + String.format(Locale.ROOT, "%010d 00000 n \n", markerOffset);
        String trailer = "trailer\n<</Size 4 /Root 1 0 R>>\nstartxref\n" + xrefOffset + "\n%%EOF\n";
        return new MockMultipartFile(
                "file", name, "application/pdf",
                (header + catalog + pages + markerObject + xref + trailer).getBytes(StandardCharsets.US_ASCII)
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
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
