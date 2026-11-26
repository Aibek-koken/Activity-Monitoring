package com.easylang.activitymonitoring.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.NotificationType;
import com.easylang.activitymonitoring.model.Project;
import com.easylang.activitymonitoring.model.ProjectChiefEditor;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.ReviewKind;
import com.easylang.activitymonitoring.model.ReviewResult;
import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.NotificationRepository;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import com.easylang.activitymonitoring.repository.ProjectRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import com.easylang.activitymonitoring.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChiefEditorReviewControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ProjectChiefEditorRepository editorRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void assignedEditorCanOpenScheduleAndRequestCorrectionOnlyOnce() throws Exception {
        Review review = openReview("EL-2026-002");
        Cookie editor = login("editor@easylang.local");
        Csrf csrf = csrf();

        mockMvc.perform(get("/api/v1/chief-editor/reviews/{reviewId}", review.getId()).cookie(editor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(review.getId()))
                .andExpect(jsonPath("$.activityNumber").value("EL-2026-002"))
                .andExpect(jsonPath("$.cycleNumber").value(1))
                .andExpect(jsonPath("$.document.originalName").value("translation.pdf"));

        mockMvc.perform(put("/api/v1/chief-editor/reviews/{reviewId}/expected-time", review.getId())
                        .cookie(editor, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCheckingHours\":3.25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCheckingHours").value(3.25));

        mockMvc.perform(post("/api/v1/chief-editor/reviews/{reviewId}/decision", review.getId())
                        .cookie(editor, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"CORRECTION_REQUESTED","feedback":"  Fix section 2 terminology.  ","correctionPages":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("CORRECTION_REQUESTED"))
                .andExpect(jsonPath("$.feedback").value("Fix section 2 terminology."))
                .andExpect(jsonPath("$.expectedCheckingHours").value(3.25));

        assertEquals(ActivityStatus.CORRECTION_REQUIRED,
                activityRepository.findById(review.getActivity().getId()).orElseThrow().getStatus());
        assertEquals(1, notificationRepository.count());
        assertEquals(NotificationType.CORRECTION_REQUESTED, notificationRepository.findAll().get(0).getType());

        mockMvc.perform(post("/api/v1/chief-editor/reviews/{reviewId}/decision", review.getId())
                        .cookie(editor, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"APPROVED\"}"))
                .andExpect(status().isConflict());
        assertEquals(1, notificationRepository.count());
    }

    @Test
    void editorReviewEndpointsEnforceAuthenticationRoleOwnershipAndCsrf() throws Exception {
        Review review = openReview("EL-2026-002");

        mockMvc.perform(get("/api/v1/chief-editor/reviews/{reviewId}", review.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/chief-editor/reviews/{reviewId}", review.getId())
                        .cookie(login("translator@easylang.local")))
                .andExpect(status().isForbidden());

        userRepository.saveAndFlush(new User(
                "other-editor@easylang.local",
                "OE",
                "Other Editor",
                passwordEncoder.encode("Demo123!"),
                Role.CHIEF_EDITOR
        ));
        mockMvc.perform(get("/api/v1/chief-editor/reviews/{reviewId}", review.getId())
                        .cookie(login("other-editor@easylang.local")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/chief-editor/reviews/{reviewId}/decision", review.getId())
                        .cookie(login("editor@easylang.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void expectedTimeAndCorrectionDecisionAreValidatedBeforeMutation() throws Exception {
        Review review = openReview("EL-2026-002");
        Cookie editor = login("editor@easylang.local");
        Csrf csrf = csrf();

        mockMvc.perform(put("/api/v1/chief-editor/reviews/{reviewId}/expected-time", review.getId())
                        .cookie(editor, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCheckingHours\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/chief-editor/reviews/{reviewId}/expected-time", review.getId())
                        .cookie(editor, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCheckingHours\":1000.001}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/chief-editor/reviews/{reviewId}/decision", review.getId())
                        .cookie(editor, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"CORRECTION_REQUESTED\",\"feedback\":\"   \"}"))
                .andExpect(status().isBadRequest());

        Review unchanged = reviewRepository.findById(review.getId()).orElseThrow();
        assertNull(unchanged.getExpectedCheckingHours());
        assertNull(unchanged.getResult());
        assertEquals(ActivityStatus.UNDER_CHECKING, unchanged.getActivity().getStatus());
        assertEquals(0, notificationRepository.count());
    }

    @Test
    void projectsQueueAndWorkloadUseTheirDistinctCurrentAndRecordedOwnershipScopes() throws Exception {
        User editor = userRepository.findByEmailIgnoreCase("editor@easylang.local").orElseThrow();
        User otherEditor = createEditor("queue-other-editor@easylang.local");

        Review oldest = openReview("EL-2026-001", editor, "2026-11-10T08:00:00Z", new BigDecimal("1.25"));
        Review middle = openReview("EL-2026-002", editor, "2026-11-10T10:00:00Z", null);
        Review newest = openReview("EL-2026-003", editor, "2026-11-10T12:00:00Z", new BigDecimal("2.50"));
        Review closed = openReview("EL-2026-900", editor, "2026-11-10T13:00:00Z", new BigDecimal("20.00"));
        closed.close(
                ReviewResult.APPROVED,
                null,
                null,
                Instant.parse("2026-11-10T13:30:00Z"),
                LocalDate.of(2026, 11, 10)
        );
        closed.getActivity().completeReview();
        reviewRepository.saveAndFlush(closed);

        Project reassignedProject = oldest.getActivity().getProject();
        ProjectChiefEditor previousAssignment = editorRepository
                .findByProjectIdAndResponsibleTrue(reassignedProject.getId()).orElseThrow();
        editorRepository.delete(previousAssignment);
        editorRepository.flush();
        editorRepository.saveAndFlush(new ProjectChiefEditor(reassignedProject, otherEditor, true));

        Project currentProject = projectRepository.saveAndFlush(new Project(
                "Current editor project",
                userRepository.findByEmailIgnoreCase("manager@easylang.local").orElseThrow(),
                LocalDate.of(2026, 11, 1)
        ));
        editorRepository.saveAndFlush(new ProjectChiefEditor(currentProject, editor, true));

        Project foreignProject = projectRepository.saveAndFlush(new Project(
                "Foreign queue project",
                userRepository.findByEmailIgnoreCase("manager@easylang.local").orElseThrow(),
                LocalDate.of(2026, 11, 1)
        ));
        editorRepository.saveAndFlush(new ProjectChiefEditor(foreignProject, otherEditor, true));
        Activity foreignActivity = activityRepository.saveAndFlush(new Activity(
                "EL-FOREIGN-QUEUE",
                "Foreign review",
                foreignProject,
                ActivityStatus.FINISHED,
                LocalDate.of(2026, 11, 2)
        ));
        foreignActivity.submitForChecking();
        reviewRepository.saveAndFlush(review(
                foreignActivity, otherEditor, "2026-11-10T14:00:00Z", new BigDecimal("9.00")
        ));

        Cookie editorCookie = login(editor.getEmail());

        mockMvc.perform(get("/api/v1/chief-editor/projects").cookie(editorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.length()").value(1))
                .andExpect(jsonPath("$.projects[0].projectName").value("Current editor project"))
                .andExpect(jsonPath("$.projects[0].responsible").value(true));

        mockMvc.perform(get("/api/v1/chief-editor/reviews")
                        .param("status", "open")
                        .param("page", "0")
                        .param("size", "2")
                        .cookie(editorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(2))
                .andExpect(jsonPath("$.reviews[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.reviews[1].id").value(middle.getId()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/chief-editor/reviews")
                        .param("status", "open")
                        .param("page", "1")
                        .param("size", "2")
                        .cookie(editorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(1))
                .andExpect(jsonPath("$.reviews[0].id").value(oldest.getId()));

        mockMvc.perform(get("/api/v1/chief-editor/workload").cookie(editorCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openCount").value(3))
                .andExpect(jsonPath("$.knownExpectedHours").value(3.75))
                .andExpect(jsonPath("$.missingEstimateCount").value(1))
                .andExpect(jsonPath("$.currentWeek").doesNotExist())
                .andExpect(jsonPath("$.thisWeek").doesNotExist());
    }

    @Test
    void dashboardEndpointsEnforceRoleAndRejectUnsupportedQueueRequests() throws Exception {
        Cookie translator = login("translator@easylang.local");

        mockMvc.perform(get("/api/v1/chief-editor/projects"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/chief-editor/projects").cookie(translator))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/chief-editor/reviews").param("status", "open"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/chief-editor/workload").cookie(translator))
                .andExpect(status().isForbidden());

        Cookie editor = login("editor@easylang.local");
        mockMvc.perform(get("/api/v1/chief-editor/reviews")
                        .param("status", "closed")
                        .cookie(editor))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/chief-editor/reviews")
                        .param("status", "open")
                        .param("page", "-1")
                        .cookie(editor))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/chief-editor/reviews")
                        .param("status", "open")
                        .param("size", "101")
                        .cookie(editor))
                .andExpect(status().isBadRequest());
    }


    @Test
    void weeklyVersionKeepsActivityActiveAndSupportsMarksAndReturn() throws Exception {
        Cookie translator = login("translator@easylang.local");
        Activity activity = activityRepository.findByActivityNumber("EL-2026-001").orElseThrow();
        Long activityId = activity.getId();
        Csrf csrf = csrf();

        mockMvc.perform(multipart("/api/v1/translator/activities/{activityId}/weekly-versions", activityId)
                        .file(validPdf())
                        .cookie(translator, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.permissions.hasOpenReview").value(true))
                .andExpect(jsonPath("$.permissions.canSubmitWeeklyVersion").value(false));

        Review review = reviewRepository.findTopByActivityIdOrderByCycleNumberDesc(activityId).orElseThrow();
        assertEquals(ReviewKind.WEEKLY, review.getKind());
        assertEquals(LocalDate.now(ZoneId.of("Asia/Almaty"))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), review.getWeekStartDate());
        assertTrue(review.isOpen());
        assertEquals(ActivityStatus.IN_PROGRESS,
                activityRepository.findById(activityId).orElseThrow().getStatus());

        csrf = csrf();
        mockMvc.perform(multipart("/api/v1/translator/activities/{activityId}/weekly-versions", activityId)
                        .file(validPdf())
                        .cookie(translator, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk());
        assertEquals(1, reviewRepository.countByActivityId(activityId));

        Cookie editor = login("editor@easylang.local");
        csrf = csrf();
        mockMvc.perform(post("/api/v1/chief-editor/reviews/{reviewId}/annotations", review.getId())
                        .cookie(editor, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pageNumber":1,"kind":"HIGHLIGHT",
                                 "rects":[{"x":0.1,"y":0.2,"width":0.3,"height":0.04}],
                                 "quotedText":"Раздел 1","comment":"Проверить глоссарий.",
                                 "suggestedReplacement":"Approved term"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotedText").value("Раздел 1"));

        mockMvc.perform(get("/api/v1/reviews/{reviewId}/annotations", review.getId()).cookie(translator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].comment").value("Проверить глоссарий."));

        mockMvc.perform(get("/api/v1/reviews/{reviewId}/annotated-document", review.getId()).cookie(editor))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        csrf = csrf();
        mockMvc.perform(post("/api/v1/chief-editor/reviews/{reviewId}/decision", review.getId())
                        .cookie(editor, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"result":"CORRECTION_REQUESTED","feedback":"Fix the glossary term.",
                                 "correctionPages":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("CORRECTION_REQUESTED"))
                .andExpect(jsonPath("$.correctionPages").value(2));

        assertEquals(ActivityStatus.IN_PROGRESS,
                activityRepository.findById(activityId).orElseThrow().getStatus());
        Review closed = reviewRepository.findById(review.getId()).orElseThrow();
        assertEquals(ReviewResult.CORRECTION_REQUESTED, closed.getResult());
        assertEquals(2, closed.getCorrectionPages());
        assertEquals(1, notificationRepository.count());

        csrf = csrf();
        mockMvc.perform(put("/api/v1/chief-editor/reviews/{reviewId}/annotations/{annotationId}",
                        review.getId(), closed.getId() + 1000)
                        .cookie(editor, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pageNumber":1,"kind":"RECT",
                                 "rects":[{"x":0.1,"y":0.2,"width":0.2,"height":0.2}],
                                 "comment":"Late edit"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void editorAvailabilityMakesTheWholeQueueComparisonHonest() throws Exception {
        Cookie editor = login("editor@easylang.local");
        openReview("EL-2026-001", userRepository.findByEmailIgnoreCase("editor@easylang.local").orElseThrow(),
                "2026-11-10T08:00:00Z", new BigDecimal("9.00"));
        openReview("EL-2026-002", userRepository.findByEmailIgnoreCase("editor@easylang.local").orElseThrow(),
                "2026-11-10T09:00:00Z", null);

        mockMvc.perform(get("/api/v1/chief-editor/workload").cookie(editor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableWeeklyHours").doesNotExist())
                .andExpect(jsonPath("$.queueExceedsWeek").doesNotExist())
                .andExpect(jsonPath("$.completeForecast").value(false));

        Csrf csrf = csrf();
        mockMvc.perform(put("/api/v1/chief-editor/availability")
                        .cookie(editor, csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weeklyHours\":8.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyHours").value(8.00));

        mockMvc.perform(get("/api/v1/chief-editor/workload").cookie(editor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableWeeklyHours").value(8.00))
                .andExpect(jsonPath("$.queueExceedsWeek").value(true))
                .andExpect(jsonPath("$.remainingHours").value(-1.00))
                .andExpect(jsonPath("$.completeForecast").value(false));
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
                "file",
                "weekly-version.pdf",
                "application/pdf",
                (header + catalog + pages + xref + trailer).getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        );
    }

    private Review openReview(String activityNumber) {
        Activity activity = activityRepository.findByActivityNumber(activityNumber).orElseThrow();
        activity.finish();
        activity.submitForChecking();
        User editor = editorRepository.findByProjectIdAndResponsibleTrue(activity.getProject().getId())
                .orElseThrow().getChiefEditor();
        return reviewRepository.saveAndFlush(review(activity, editor, "2026-11-10T08:00:00Z", null));
    }

    private Review openReview(
            String activityNumber,
            User editor,
            String submittedAt,
            BigDecimal expectedCheckingHours
    ) {
        Activity activity = activityRepository.findByActivityNumber(activityNumber).orElseThrow();
        activity.finish();
        activity.submitForChecking();
        return reviewRepository.saveAndFlush(review(activity, editor, submittedAt, expectedCheckingHours));
    }

    private Review review(
            Activity activity,
            User editor,
            String submittedAt,
            BigDecimal expectedCheckingHours
    ) {
        Review review = new Review(activity, editor, 1, Instant.parse(submittedAt));
        review.attachDocument(
                UUID.randomUUID().toString(),
                "translation.pdf",
                "application/pdf",
                321,
                "a".repeat(64)
        );
        if (expectedCheckingHours != null) {
            review.schedule(expectedCheckingHours);
        }
        return review;
    }

    private User createEditor(String email) {
        return userRepository.saveAndFlush(new User(
                email,
                "QE",
                "Queue Editor",
                passwordEncoder.encode("Demo123!"),
                Role.CHIEF_EDITOR
        ));
    }

    private Cookie login(String email) throws Exception {
        Csrf csrf = csrf();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Demo123!\"}"))
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
