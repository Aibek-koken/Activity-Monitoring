package com.easylang.activitymonitoring.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.ReviewResult;
import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.WorkRecord;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import com.easylang.activitymonitoring.repository.WorkRecordRepository;
import com.easylang.activitymonitoring.repository.WeeklyEstimateRepository;
import com.easylang.activitymonitoring.security.JwtAuthenticationFilter;
import com.easylang.activitymonitoring.storage.DocumentStorageException;
import com.easylang.activitymonitoring.storage.DocumentUploadService;
import com.easylang.activitymonitoring.storage.PrivateDocumentStorageService;
import com.easylang.activitymonitoring.storage.UploadedDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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
    private ActivityTranslatorRepository activityTranslatorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkRecordRepository workRecordRepository;

    @Autowired
    private WeeklyEstimateRepository weeklyEstimateRepository;

    @Autowired
    private ProjectChiefEditorRepository editorRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DocumentUploadService documentUploadService;

    @Autowired
    private PrivateDocumentStorageService documentStorage;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

        mockMvc.perform(get("/api/v1/translator/activities")
                        .queryParam("q", "%")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities.length()").value(0));
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
    void responsibleTranslatorUpdatesOneEstimateForCurrentWeek() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();

        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/weekly-estimates/current", activityId)
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"remainingTextVolume":120.50}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeekEstimate.remainingTextVolume").value(120.50))
                .andExpect(jsonPath("$.weeklyEstimates.length()").value(1))
                .andExpect(jsonPath("$.permissions.canFinish").value(false));

        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/weekly-estimates/current", activityId)
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"remainingTextVolume":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeekEstimate.remainingTextVolume").value(0))
                .andExpect(jsonPath("$.permissions.canFinish").value(true));
        assertEquals(1, weeklyEstimateRepository.findByActivityIdOrderByWeekStartDateDesc(activityId).size());
    }

    @Test
    void assistantCanReadButCannotSaveWeeklyEstimate() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-003").orElseThrow().getId();

        mockMvc.perform(get("/api/v1/translator/activities/{activityId}", activityId).cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.canEditWeeklyEstimate").value(false));

        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/weekly-estimates/current", activityId)
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"remainingTextVolume":10}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void weeklyEstimateRequiresCsrfAndValidDecimal() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();

        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/weekly-estimates/current", activityId)
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"remainingTextVolume":1}
                                """))
                .andExpect(status().isForbidden());

        Csrf csrf = csrf();
        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/weekly-estimates/current", activityId)
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"remainingTextVolume":-1.123}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void finishAndSubmitRequireCurrentZeroAndCreateOneReview() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/finish", activityId)
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict());
        mockMvc.perform(multipart("/api/v1/translator/activities/{activityId}/submit-for-checking", activityId)
                        .file(validPdf())
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict());

        saveEstimate(activityId, accessCookie, csrf, "12.00");
        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/finish", activityId)
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict());

        saveEstimate(activityId, accessCookie, csrf, "0.00");
        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/finish", activityId)
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.permissions.canEditWorkRecords").value(false))
                .andExpect(jsonPath("$.permissions.canSubmitForChecking").value(true));

        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/weekly-estimates/current", activityId)
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"remainingTextVolume\":5}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activityId)
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordDate\":\"2026-09-28\",\"translatedVolume\":3}"))
                .andExpect(status().isConflict());

        mockMvc.perform(multipart("/api/v1/translator/activities/{activityId}/submit-for-checking", activityId)
                        .file(validPdf())
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_CHECKING"))
                .andExpect(jsonPath("$.permissions.canSubmitForChecking").value(false));
        assertEquals(1, reviewRepository.count());
        assertEquals("editor@easylang.local", reviewRepository.findAll().get(0).getChiefEditor().getEmail());
        assertEquals("translation.pdf", reviewRepository.findAll().get(0).getDocumentOriginalName());
        assertEquals("application/pdf", reviewRepository.findAll().get(0).getDocumentContentType());

        mockMvc.perform(multipart("/api/v1/translator/activities/{activityId}/submit-for-checking", activityId)
                        .file(validPdf())
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict());
        assertEquals(1, reviewRepository.count());
    }

    @Test
    void submissionRequiresMultipartFile() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();
        saveEstimate(activityId, accessCookie, csrf, "0");
        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/finish", activityId)
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/v1/translator/activities/{activityId}/submit-for-checking", activityId)
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isBadRequest());

        assertEquals(0, reviewRepository.count());
    }

    @Test
    void responsibleTranslatorAndAssignedEditorCanReadHistoryAndDownloadDocx() throws Exception {
        Cookie translatorCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Activity activity = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow();
        saveEstimate(activity.getId(), translatorCookie, csrf, "0");
        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/finish", activity.getId())
                        .cookie(translatorCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());

        byte[] documentBytes = validDocxBytes();
        mockMvc.perform(multipart("/api/v1/translator/activities/{activityId}/submit-for-checking", activity.getId())
                        .file(new MockMultipartFile("file", "translation.docx", "application/octet-stream", documentBytes))
                        .cookie(translatorCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());
        Review review = reviewRepository.findAll().get(0);

        mockMvc.perform(get("/api/v1/translator/activities/{activityId}/reviews", activity.getId())
                        .cookie(translatorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(1))
                .andExpect(jsonPath("$.reviews[0].id").value(review.getId()))
                .andExpect(jsonPath("$.reviews[0].cycleNumber").value(1))
                .andExpect(jsonPath("$.reviews[0].document.originalName").value("translation.docx"))
                .andExpect(jsonPath("$.reviews[0].document.contentType").value(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

        for (String email : new String[] {"translator@easylang.local", "editor@easylang.local"}) {
            MvcResult download = mockMvc.perform(get("/api/v1/reviews/{reviewId}/document", review.getId())
                            .cookie(login(email)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .andExpect(header().string("Content-Disposition", containsString("translation.docx")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andReturn();
            assertArrayEquals(documentBytes, download.getResponse().getContentAsByteArray());
        }

        mockMvc.perform(get("/api/v1/reviews/{reviewId}/document", review.getId())
                        .cookie(login("translator2@easylang.local")))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Content-Disposition"));
        userRepository.saveAndFlush(new User(
                "other-editor@easylang.local",
                "OE",
                "Other Editor",
                passwordEncoder.encode("Demo123!"),
                Role.CHIEF_EDITOR
        ));
        mockMvc.perform(get("/api/v1/reviews/{reviewId}/document", review.getId())
                        .cookie(login("other-editor@easylang.local")))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Content-Disposition"));
        mockMvc.perform(get("/api/v1/reviews/{reviewId}/document", review.getId())
                        .cookie(login("manager@easylang.local")))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Content-Disposition"));
    }

    @Test
    void openLegacyReviewAcceptsOneAttachmentWithoutCreatingOrReplacingReview() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Activity activity = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow();
        saveEstimate(activity.getId(), accessCookie, csrf, "0");
        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/finish", activity.getId())
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());
        activity.submitForChecking();
        User editor = editorRepository.findByProjectIdAndResponsibleTrue(activity.getProject().getId())
                .orElseThrow().getChiefEditor();
        Review legacyReview = reviewRepository.saveAndFlush(
                new Review(activity, editor, 1, java.time.Instant.parse("2026-10-24T10:00:00Z"))
        );

        mockMvc.perform(multipart(
                        "/api/v1/translator/activities/{activityId}/reviews/{reviewId}/legacy-attachment",
                        activity.getId(), legacyReview.getId())
                        .file(validPdf())
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(legacyReview.getId()))
                .andExpect(jsonPath("$.document.originalName").value("translation.pdf"));

        String originalSha = legacyReview.getDocumentSha256();
        mockMvc.perform(multipart(
                        "/api/v1/translator/activities/{activityId}/reviews/{reviewId}/legacy-attachment",
                        activity.getId(), legacyReview.getId())
                        .file(new MockMultipartFile("file", "replacement.pdf", "application/pdf",
                                "%PDF-1.4\nreplacement\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict());

        assertEquals(1, reviewRepository.countByActivityId(activity.getId()));
        assertEquals(originalSha, reviewRepository.findById(legacyReview.getId()).orElseThrow().getDocumentSha256());
    }

    @Test
    void legacyAttachmentRequiresCsrfAndResponsibleOwnership() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Long activityId = activityRepository.findByActivityNumber("EL-2026-003").orElseThrow().getId();

        mockMvc.perform(multipart(
                        "/api/v1/translator/activities/{activityId}/reviews/{reviewId}/legacy-attachment",
                        activityId, 999999L)
                        .file(validPdf()).cookie(accessCookie))
                .andExpect(status().isForbidden());

        Csrf csrf = csrf();
        mockMvc.perform(multipart(
                        "/api/v1/translator/activities/{activityId}/reviews/{reviewId}/legacy-attachment",
                        activityId, 999999L)
                        .file(validPdf()).cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void reviewHistoryIsHiddenFromAssistantsAndUnassignedTranslators() throws Exception {
        Cookie maya = login("translator@easylang.local");
        Long assistantActivityId = activityRepository.findByActivityNumber("EL-2026-003").orElseThrow().getId();
        mockMvc.perform(get("/api/v1/translator/activities/{activityId}/reviews", assistantActivityId)
                        .cookie(maya))
                .andExpect(status().isNotFound());

        Cookie ravi = login("translator2@easylang.local");
        Long mayaActivityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();
        mockMvc.perform(get("/api/v1/translator/activities/{activityId}/reviews", mayaActivityId)
                        .cookie(ravi))
                .andExpect(status().isNotFound());
    }

    @Test
    void closedLegacyReviewCannotReceiveAnAttachment() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Activity activity = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow();
        saveEstimate(activity.getId(), accessCookie, csrf, "0");
        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/finish", activity.getId())
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());
        activity.submitForChecking();
        User editor = editorRepository.findByProjectIdAndResponsibleTrue(activity.getProject().getId())
                .orElseThrow().getChiefEditor();
        Review review = reviewRepository.saveAndFlush(
                new Review(activity, editor, 1, java.time.Instant.parse("2026-10-24T10:00:00Z"))
        );
        entityManager.flush();
        jdbcTemplate.update("UPDATE reviews SET result = 'APPROVED', open_slot = NULL WHERE id = ?", review.getId());
        entityManager.clear();

        mockMvc.perform(multipart(
                        "/api/v1/translator/activities/{activityId}/reviews/{reviewId}/legacy-attachment",
                        activity.getId(), review.getId())
                        .file(validPdf()).cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict());

        assertNull(reviewRepository.findById(review.getId()).orElseThrow().getDocumentStorageKey());
    }

    @Test
    void correctionResubmissionRequiresCsrfAndResponsibleOwnershipAndCreatesOneNewCycle() throws Exception {
        Cookie translator = login("translator@easylang.local");
        Csrf csrf = csrf();
        Activity activity = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow();
        activity.finish();
        activity.submitForChecking();
        User editor = editorRepository.findByProjectIdAndResponsibleTrue(activity.getProject().getId())
                .orElseThrow().getChiefEditor();
        Review first = new Review(activity, editor, 1, java.time.Instant.parse("2026-11-10T08:00:00Z"));
        first.attachDocument("cycle-1", "first.pdf", "application/pdf", 321, "a".repeat(64));
        first.schedule(new BigDecimal("2.50"));
        first.close(
                ReviewResult.CORRECTION_REQUESTED,
                "Fix the legal terminology.",
                2,
                java.time.Instant.parse("2026-11-10T10:00:00Z"),
                LocalDate.of(2026, 11, 10)
        );
        activity.requestCorrection();
        first = reviewRepository.saveAndFlush(first);

        mockMvc.perform(multipart("/api/v1/translator/activities/{activityId}/resubmit-for-checking", activity.getId())
                        .file(validPdf()).cookie(translator))
                .andExpect(status().isForbidden());

        Long assistantActivityId = activityRepository.findByActivityNumber("EL-2026-003").orElseThrow().getId();
        mockMvc.perform(multipart(
                        "/api/v1/translator/activities/{activityId}/resubmit-for-checking", assistantActivityId)
                        .file(validPdf()).cookie(translator, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/translator/activities/{activityId}/resubmit-for-checking", activity.getId())
                        .file(new MockMultipartFile("file", "corrected.pdf", "application/pdf", validPdfBytes()))
                        .cookie(translator, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_CHECKING"));

        var cycles = reviewRepository.findByActivityIdOrderByCycleNumberDesc(activity.getId());
        assertEquals(2, cycles.size());
        assertEquals(2, cycles.get(0).getCycleNumber());
        assertEquals("corrected.pdf", cycles.get(0).getDocumentOriginalName());
        assertEquals(ReviewResult.CORRECTION_REQUESTED, cycles.get(1).getResult());
        assertEquals("Fix the legal terminology.", cycles.get(1).getMistakesNotes());
        assertEquals(new BigDecimal("2.50"), cycles.get(1).getExpectedCheckingHours());
        assertEquals("cycle-1", cycles.get(1).getDocumentStorageKey());
        assertEquals(ActivityStatus.UNDER_CHECKING,
                activityRepository.findById(activity.getId()).orElseThrow().getStatus());

        mockMvc.perform(multipart("/api/v1/translator/activities/{activityId}/resubmit-for-checking", activity.getId())
                        .file(validPdf()).cookie(translator, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict());
        assertEquals(2, reviewRepository.countByActivityId(activity.getId()));

        mockMvc.perform(get("/api/v1/translator/activities/{activityId}/reviews", activity.getId())
                        .cookie(translator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[0].cycleNumber").value(2))
                .andExpect(jsonPath("$.reviews[1].cycleNumber").value(1))
                .andExpect(jsonPath("$.reviews[1].result").value("CORRECTION_REQUESTED"))
                .andExpect(jsonPath("$.reviews[1].feedback").value("Fix the legal terminology."))
                .andExpect(jsonPath("$.reviews[1].expectedCheckingHours").value(2.50))
                .andExpect(jsonPath("$.reviews[1].document.originalName").value("first.pdf"));
    }

    @Test
    void rolledBackUploadRemovesStoredDocument() {
        AtomicReference<UploadedDocument> uploaded = new AtomicReference<>();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            uploaded.set(documentUploadService.validateAndStore(validPdf()));
            status.setRollbackOnly();
        });

        assertThrows(
                DocumentStorageException.class,
                () -> documentStorage.open(uploaded.get().storageKey())
        );
    }

    @Test
    void assistantAndUnassignedTranslatorCannotFinishOrSubmit() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Long assistantActivityId = activityRepository.findByActivityNumber("EL-2026-003").orElseThrow().getId();
        Long unassignedActivityId = activityRepository.findByActivityNumber("EL-2026-900").orElseThrow().getId();

        for (Long activityId : new Long[] {assistantActivityId, unassignedActivityId}) {
            mockMvc.perform(post("/api/v1/translator/activities/{activityId}/finish", activityId)
                            .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                    .andExpect(activityId.equals(assistantActivityId) ? status().isForbidden() : status().isNotFound());
            mockMvc.perform(multipart("/api/v1/translator/activities/{activityId}/submit-for-checking", activityId)
                            .file(validPdf())
                            .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                    .andExpect(activityId.equals(assistantActivityId) ? status().isForbidden() : status().isNotFound());
        }
    }

    @Test
    void missingResponsibleEditorKeepsActivityFinished() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Activity activity = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow();
        saveEstimate(activity.getId(), accessCookie, csrf, "0");
        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/finish", activity.getId())
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());

        editorRepository.delete(editorRepository.findByProjectIdAndResponsibleTrue(activity.getProject().getId()).orElseThrow());
        editorRepository.flush();
        mockMvc.perform(get("/api/v1/translator/activities/{activityId}", activity.getId())
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.canSubmitForChecking").value(false))
                .andExpect(jsonPath("$.permissions.hasResponsibleChiefEditor").value(false));
        mockMvc.perform(multipart("/api/v1/translator/activities/{activityId}/submit-for-checking", activity.getId())
                        .file(validPdf())
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No responsible Chief Editor is assigned to this project"));
        assertEquals("FINISHED", activityRepository.findById(activity.getId()).orElseThrow().getStatus().name());
        assertEquals(0, reviewRepository.count());
    }

    @Test
    void finishRequiresCsrf() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();
        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/finish", activityId).cookie(accessCookie))
                .andExpect(status().isForbidden());
    }

    private void saveEstimate(Long activityId, Cookie accessCookie, Csrf csrf, String volume) throws Exception {
        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/weekly-estimates/current", activityId)
                        .cookie(accessCookie, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remainingTextVolume\":" + volume + "}"))
                .andExpect(status().isOk());
    }


    @Test
    void reworkPagesReduceOnlyTheOutstandingCorrection() throws Exception {
        Cookie translator = login("translator@easylang.local");
        Csrf csrf = csrf();
        Activity activity = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow();
        activity.finish();
        activity.submitForChecking();
        User editor = editorRepository.findByProjectIdAndResponsibleTrue(activity.getProject().getId())
                .orElseThrow().getChiefEditor();
        Review review = new Review(activity, editor, 1, Instant.parse("2026-11-10T08:00:00Z"));
        review.attachDocument("rework-cycle-1", "first.pdf", "application/pdf", 321, "b".repeat(64));
        review.close(
                ReviewResult.CORRECTION_REQUESTED,
                "Redo three pages.",
                3,
                Instant.parse("2026-11-10T10:00:00Z"),
                LocalDate.of(2026, 11, 10)
        );
        activity.requestCorrection();
        reviewRepository.saveAndFlush(review);

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activity.getId())
                        .cookie(translator, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"2026-11-11","translatedVolume":0,"reworkPages":2,"workHours":1.50}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.totalTranslatedVolume").value(0))
                .andExpect(jsonPath("$.rework.correctionPages").value(3))
                .andExpect(jsonPath("$.rework.reworkRecordedPages").value(2))
                .andExpect(jsonPath("$.rework.outstandingReworkPages").value(1))
                .andExpect(jsonPath("$.rework.totalRemainingPages").value(1))
                .andExpect(jsonPath("$.permissions.hasOutstandingRework").value(true));

        csrf = csrf();
        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activity.getId())
                        .cookie(translator, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"2026-11-12","translatedVolume":0,"reworkPages":2}
                                """))
                .andExpect(status().isBadRequest());
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
    void createDailyWorkRecordWithoutCsrfIsRejected() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Long activityId = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activityId)
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"2026-09-27","translatedVolume":4.50}
                                """))
                .andExpect(status().isForbidden());
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
    void translatorCanCreateWorkRecordForUtcPlus14Today() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-002").orElseThrow().getId();
        LocalDate latestAllowedDate = LocalDate.now(ZoneOffset.ofHours(14));

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activityId)
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"%s","translatedVolume":4.50}
                                """.formatted(latestAllowedDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.lastRecordDate").value(latestAllowedDate.toString()));
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
        LocalDate tooFarAhead = LocalDate.now(ZoneOffset.ofHours(14)).plusDays(1);

        mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records", activityId)
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordDate":"%s","translatedVolume":3.00}
                                """.formatted(tooFarAhead)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Record date cannot be in the future"));
    }

    @Test
    void editDailyWorkRecordWithoutCsrfIsRejected() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();
        Long recordId = workRecordRepository
                .findByActivityIdAndTranslatorIdAndRecordDate(
                        activityId,
                        userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow().getId(),
                        LocalDate.of(2026, 9, 30)
                )
                .orElseThrow()
                .getId();

        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/work-records/{recordId}", activityId, recordId)
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"translatedVolume":6.75}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void translatorCannotEditAnotherTranslatorsRecord() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Activity activity = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow();
        User secondTranslator = userRepository.findByEmailIgnoreCase("translator2@easylang.local").orElseThrow();

        activityTranslatorRepository.save(new ActivityTranslator(
                activity,
                secondTranslator,
                false,
                LocalDate.of(2026, 9, 28)
        ));
        WorkRecord otherTranslatorRecord = workRecordRepository.saveAndFlush(new WorkRecord(
                activity,
                secondTranslator,
                LocalDate.of(2026, 9, 28),
                new BigDecimal("3.00"),
                null
        ));

        mockMvc.perform(put(
                        "/api/v1/translator/activities/{activityId}/work-records/{recordId}",
                        activity.getId(),
                        otherTranslatorRecord.getId()
                )
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"translatedVolume":6.75}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void translatorCannotEditRecordThroughDifferentActivity() throws Exception {
        Cookie accessCookie = login("translator@easylang.local");
        Csrf csrf = csrf();
        Long activityId = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow().getId();
        Long otherActivityId = activityRepository.findByActivityNumber("EL-2026-003").orElseThrow().getId();
        Long recordId = workRecordRepository
                .findByActivityIdAndTranslatorIdAndRecordDate(
                        otherActivityId,
                        userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow().getId(),
                        LocalDate.of(2026, 9, 30)
                )
                .orElseThrow()
                .getId();

        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/work-records/{recordId}", activityId, recordId)
                        .cookie(accessCookie, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"translatedVolume":6.75}
                                """))
                .andExpect(status().isNotFound());
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
                        .content("{\"translatedVolume\":6.75,\"workHours\":1.50}"))
                .andExpect(status().isForbidden());

        MvcResult editRequest = mockMvc.perform(post("/api/v1/translator/activities/{activityId}/work-records/{recordId}/edit-requests", activityId, recordId)
                        .cookie(accessCookie, updateCsrf.cookie())
                        .header(updateCsrf.headerName(), updateCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Correct my daily hours\"}"))
                .andExpect(status().isCreated()).andReturn();
        long editRequestId = objectMapper.readTree(editRequest.getResponse().getContentAsString()).get("id").asLong();
        Cookie managerCookie = login("manager@easylang.local");
        Csrf managerCsrf = csrf();
        mockMvc.perform(post("/api/v1/project-manager/work-record-edit-requests/{id}/decision", editRequestId)
                        .cookie(managerCookie, managerCsrf.cookie())
                        .header(managerCsrf.headerName(), managerCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/work-records/{recordId}", activityId, recordId)
                        .cookie(accessCookie, updateCsrf.cookie())
                        .header(updateCsrf.headerName(), updateCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"translatedVolume\":6.75,\"workHours\":1.50,\"editRequestId\":" + editRequestId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.totalTranslatedVolume").value(6.75))
                .andExpect(jsonPath("$.progress.recordCount").value(1))
                .andExpect(jsonPath("$.workRecords[0].recordDate").value("2026-09-28"))
                .andExpect(jsonPath("$.workRecords[0].translatedVolume").value(6.75))
                .andExpect(jsonPath("$.workRecords[0].workHours").value(1.50));

        mockMvc.perform(put("/api/v1/translator/activities/{activityId}/work-records/{recordId}", activityId, recordId)
                        .cookie(accessCookie, updateCsrf.cookie())
                        .header(updateCsrf.headerName(), updateCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"translatedVolume\":7,\"editRequestId\":" + editRequestId + "}"))
                .andExpect(status().isForbidden());
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

    private MockMultipartFile validPdf() {
        return new MockMultipartFile(
                "file",
                "translation.pdf",
                "application/pdf",
                validPdfBytes()
        );
    }

    private byte[] validPdfBytes() {
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
        return (header + catalog + pages + xref + trailer).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private byte[] validDocxBytes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            ZipEntry contentTypes = new ZipEntry("[Content_Types].xml");
            contentTypes.setTime(0);
            zip.putNextEntry(contentTypes);
            zip.write(("<Types><Override PartName=\"/word/document.xml\" ContentType=\""
                    + "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
                    + "\"/></Types>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            ZipEntry document = new ZipEntry("word/document.xml");
            document.setTime(0);
            zip.putNextEntry(document);
            zip.write("<w:document/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
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
