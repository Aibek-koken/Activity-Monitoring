package com.easylang.activitymonitoring.config;

import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.Notification;
import com.easylang.activitymonitoring.model.NotificationType;
import com.easylang.activitymonitoring.model.Project;
import com.easylang.activitymonitoring.model.ProjectChiefEditor;
import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.model.WeeklyEstimate;
import com.easylang.activitymonitoring.model.WorkRecord;
import com.easylang.activitymonitoring.model.Review;
import com.easylang.activitymonitoring.model.ReviewKind;
import com.easylang.activitymonitoring.model.ReviewAnnotation;
import com.easylang.activitymonitoring.model.AnnotationKind;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.NotificationRepository;
import com.easylang.activitymonitoring.repository.ProjectRepository;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import com.easylang.activitymonitoring.service.ChiefEditorAssignmentService;
import com.easylang.activitymonitoring.repository.WeeklyEstimateRepository;
import com.easylang.activitymonitoring.repository.WorkRecordRepository;
import com.easylang.activitymonitoring.repository.ReviewRepository;
import com.easylang.activitymonitoring.repository.ReviewAnnotationRepository;
import com.easylang.activitymonitoring.storage.PrivateDocumentStorageService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!prod")
@ConditionalOnProperty(name = "app.seed-demo-users", havingValue = "true", matchIfMissing = true)
public class DemoDataConfig implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "Demo123!";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectChiefEditorRepository projectChiefEditorRepository;
    private final ActivityRepository activityRepository;
    private final ActivityTranslatorRepository activityTranslatorRepository;
    private final WorkRecordRepository workRecordRepository;
    private final WeeklyEstimateRepository weeklyEstimateRepository;
    private final NotificationRepository notificationRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewAnnotationRepository reviewAnnotationRepository;
    private final PrivateDocumentStorageService documentStorage;
    private final PasswordEncoder passwordEncoder;
    private final Clock businessClock;
    private final Environment environment;
    private final ChiefEditorAssignmentService chiefEditorAssignments;

    public DemoDataConfig(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectChiefEditorRepository projectChiefEditorRepository,
            ActivityRepository activityRepository,
            ActivityTranslatorRepository activityTranslatorRepository,
            WorkRecordRepository workRecordRepository,
            WeeklyEstimateRepository weeklyEstimateRepository,
            NotificationRepository notificationRepository,
            ReviewRepository reviewRepository,
            ReviewAnnotationRepository reviewAnnotationRepository,
            PrivateDocumentStorageService documentStorage,
            PasswordEncoder passwordEncoder,
            Clock businessClock,
            Environment environment,
            ChiefEditorAssignmentService chiefEditorAssignments
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectChiefEditorRepository = projectChiefEditorRepository;
        this.activityRepository = activityRepository;
        this.activityTranslatorRepository = activityTranslatorRepository;
        this.workRecordRepository = workRecordRepository;
        this.weeklyEstimateRepository = weeklyEstimateRepository;
        this.notificationRepository = notificationRepository;
        this.reviewRepository = reviewRepository;
        this.reviewAnnotationRepository = reviewAnnotationRepository;
        this.documentStorage = documentStorage;
        this.passwordEncoder = passwordEncoder;
        this.businessClock = businessClock;
        this.environment = environment;
        this.chiefEditorAssignments = chiefEditorAssignments;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SeedUser> seedUsers = List.of(
                new SeedUser("translator@easylang.local", "MC", "Maya Chen", Role.TRANSLATOR),
                new SeedUser("translator2@easylang.local", "RA", "Ravi Anand", Role.TRANSLATOR),
                new SeedUser("editor@easylang.local", "EB", "Elias Brooks", Role.CHIEF_EDITOR),
                new SeedUser("editor2@easylang.local", "SB", "Sofia Bianchi", Role.CHIEF_EDITOR),
                new SeedUser("manager@easylang.local", "OG", "Oliver Grant", Role.PROJECT_MANAGER)
        );

        seedUsers.forEach(this::findOrCreateUser);

        User translator = userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow();
        User secondTranslator = userRepository.findByEmailIgnoreCase("translator2@easylang.local").orElseThrow();
        User manager = userRepository.findByEmailIgnoreCase("manager@easylang.local").orElseThrow();
        User editor = userRepository.findByEmailIgnoreCase("editor@easylang.local").orElseThrow();
        User assistantEditor = userRepository.findByEmailIgnoreCase("editor2@easylang.local").orElseThrow();

        Project legalPortal = findOrCreateProject(
                "Central Asia Legal Portal",
                manager,
                LocalDate.of(2026, 9, 22)
        );
        Project productLaunch = findOrCreateProject(
                "Retail Launch Localization",
                manager,
                LocalDate.of(2026, 9, 25)
        );
        // A Chief Editor is responsible for exactly one project; others may assist.
        chiefEditorAssignments.assign(legalPortal, editor, true);
        chiefEditorAssignments.move(productLaunch, editor, assistantEditor, true);

        Activity privacyPolicy = findOrCreateActivity(
                "EL-2026-001",
                "Privacy policy translation",
                legalPortal,
                ActivityStatus.IN_PROGRESS,
                LocalDate.of(2026, 9, 22)
        );
        Activity contractAppendix = findOrCreateActivity(
                "EL-2026-002",
                "Supplier contract appendix",
                legalPortal,
                ActivityStatus.ASSIGNED,
                LocalDate.of(2026, 9, 23)
        );
        Activity launchEmails = findOrCreateActivity(
                "EL-2026-003",
                "Onboarding email sequence",
                productLaunch,
                ActivityStatus.IN_PROGRESS,
                LocalDate.of(2026, 9, 25)
        );
        Activity otherTranslatorActivity = findOrCreateActivity(
                "EL-2026-900",
                "Archive glossary cleanup",
                productLaunch,
                ActivityStatus.ASSIGNED,
                LocalDate.of(2026, 9, 26)
        );

        assignIfMissing(privacyPolicy, translator, true, LocalDate.of(2026, 9, 22));
        assignIfMissing(contractAppendix, translator, true, LocalDate.of(2026, 9, 23));
        assignIfMissing(launchEmails, translator, false, LocalDate.of(2026, 9, 25));
        assignIfMissing(otherTranslatorActivity, secondTranslator, true, LocalDate.of(2026, 9, 26));

        recordIfMissing(
                privacyPolicy,
                translator,
                LocalDate.of(2026, 9, 29),
                new BigDecimal("12.50"),
                new BigDecimal("3.00")
        );
        recordIfMissing(
                privacyPolicy,
                translator,
                LocalDate.of(2026, 9, 30),
                new BigDecimal("8.25"),
                new BigDecimal("2.50")
        );
        recordIfMissing(
                launchEmails,
                translator,
                LocalDate.of(2026, 9, 30),
                new BigDecimal("5.75"),
                null
        );

        if (!environment.acceptsProfiles(Profiles.of("test"))) {
            rehomeDemoPortfolio(editor, assistantEditor);
            seedSprint3Showcase(translator, privacyPolicy, contractAppendix);
            if (environment.getProperty("app.seed-demo-portfolio", Boolean.class, true)) {
                seedDemoPortfolio(translator, manager, assistantEditor);
            }
            assignMayaDemoProjects(translator, legalPortal, assistantEditor);
            seedNotificationShowcase(translator, manager, privacyPolicy, contractAppendix, launchEmails);
        }
    }

    private void assignMayaDemoProjects(User maya, Project eliasProject, User sofia) {
        // Demo-only exception: Sofia needs every Maya project to exercise the review flow.
        // The production assignment service still enforces one responsible project per editor.
        for (Project project : projectRepository.findAssignedToTranslator(maya.getId())) {
            if (project.getId().equals(eliasProject.getId())) continue;
            var existingResponsible = projectChiefEditorRepository.findByProjectIdAndResponsibleTrue(project.getId());
            if (existingResponsible.isPresent() && !existingResponsible.get().getChiefEditor().getId().equals(sofia.getId())) {
                continue;
            }
            ProjectChiefEditor assignment = projectChiefEditorRepository
                    .findByProjectIdAndChiefEditorId(project.getId(), sofia.getId())
                    .orElseGet(() -> projectChiefEditorRepository.save(new ProjectChiefEditor(project, sofia, true)));
            if (!assignment.isResponsible()) {
                assignment.makeResponsible();
                projectChiefEditorRepository.save(assignment);
            }
        }
    }

    private void rehomeDemoPortfolio(User previousEditor, User assistantEditor) {
        projectRepository.findByProjectNameStartingWith("Demo · ").forEach(project ->
                chiefEditorAssignments.move(project, previousEditor, assistantEditor, false));
        reviewRepository.reassignChiefEditorForActivityNumbers(
                previousEditor.getId(), assistantEditor, "EL-DEMO-%");
    }

    private void seedDemoPortfolio(User maya, User manager, User assistantEditor) {
        String[] names = {"Clinical Device Guides", "Rail Safety Handbook", "Energy Contract Library",
                "Public Health Portal", "University Courseware", "Agricultural Export Forms",
                "Aviation Service Manuals", "Museum Archive Translation", "Mobile Banking Help",
                "City Transport Notices"};
        LocalDate weekStart = LocalDate.now(businessClock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int projectIndex = 0; projectIndex < names.length; projectIndex++) {
            Project project = findOrCreateProject("Demo · " + names[projectIndex], manager,
                    LocalDate.of(2026, 9, 1).plusDays(projectIndex));
            chiefEditorAssignments.assign(project, assistantEditor, false);
            for (int activityIndex = 0; activityIndex < 2; activityIndex++) {
                int number = 101 + projectIndex * 2 + activityIndex;
                Activity activity = findOrCreateActivity("EL-DEMO-" + number,
                        activityIndex == 0 ? "Main translation" : "Terminology appendix",
                        project, ActivityStatus.IN_PROGRESS, LocalDate.of(2026, 9, 1).plusDays(projectIndex));
                assignIfMissing(activity, maya, true, activity.getCreatedDate());
                if (reviewRepository.countByActivityId(activity.getId()) > 0) continue;
                try {
                    byte[] pdf = demoPdf(activity.getActivityNumber(), project.getProjectName());
                    var stored = documentStorage.store(new ByteArrayInputStream(pdf));
                    Review review = new Review(activity, assistantEditor, 1, ReviewKind.WEEKLY, weekStart,
                            businessClock.instant());
                    review.attachDocument(stored.storageKey(), activity.getActivityNumber() + ".pdf",
                            "application/pdf", stored.sizeBytes(), stored.sha256());
                    review = reviewRepository.saveAndFlush(review);
                    float x = (72 + PDType1Font.HELVETICA.getStringWidth("Check ") / 1000f * 14) / PDRectangle.A4.getWidth();
                    float width = PDType1Font.HELVETICA.getStringWidth("terminology") / 1000f * 14 / PDRectangle.A4.getWidth();
                    float y = (PDRectangle.A4.getHeight() - 712) / PDRectangle.A4.getHeight();
                    float height = 16 / PDRectangle.A4.getHeight();
                    String rect = String.format(Locale.ROOT,
                            "[{\"x\":%.5f,\"y\":%.5f,\"width\":%.5f,\"height\":%.5f}]", x, y, width, height);
                    reviewAnnotationRepository.save(new ReviewAnnotation(review, 1, AnnotationKind.HIGHLIGHT,
                            rect, "terminology", "Check this terminology against the source.", null,
                            businessClock.instant()));
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not create local demo PDF", exception);
                }
            }
        }
    }

    private byte[] demoPdf(String activityNumber, String projectName) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 14);
                content.newLineAtOffset(72, 700);
                content.showText("Check terminology in this translation sample.");
                content.endText();
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 10);
                content.newLineAtOffset(72, 665);
                content.showText(activityNumber + " - " + projectName.replace("Demo · ", ""));
                content.endText();
            }
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    private User findOrCreateUser(SeedUser seed) {
        return userRepository.findByEmailIgnoreCase(seed.email()).orElseGet(() ->
                userRepository.save(new User(
                        seed.email(),
                        seed.initials(),
                        seed.fullName(),
                        passwordEncoder.encode(DEMO_PASSWORD),
                        seed.role()
                ))
        );
    }

    private Project findOrCreateProject(String projectName, User manager, LocalDate createdDate) {
        return projectRepository.findByProjectName(projectName)
                .orElseGet(() -> projectRepository.save(new Project(projectName, manager, createdDate)));
    }

    private Activity findOrCreateActivity(
            String activityNumber,
            String activityName,
            Project project,
            ActivityStatus status,
            LocalDate createdDate
    ) {
        return activityRepository.findByActivityNumber(activityNumber)
                .orElseGet(() -> activityRepository.save(
                        new Activity(activityNumber, activityName, project, status, createdDate)
                ));
    }

    private void assignIfMissing(Activity activity, User translator, boolean responsible, LocalDate assignedDate) {
        if (!activityTranslatorRepository.existsByActivityIdAndTranslatorId(activity.getId(), translator.getId())) {
            activityTranslatorRepository.save(new ActivityTranslator(activity, translator, responsible, assignedDate));
        }
    }

    private void recordIfMissing(
            Activity activity,
            User translator,
            LocalDate recordDate,
            BigDecimal translatedVolume,
            BigDecimal workHours
    ) {
        workRecordRepository.findByActivityIdAndTranslatorIdAndRecordDate(
                activity.getId(),
                translator.getId(),
                recordDate
        ).orElseGet(() -> workRecordRepository.save(
                new WorkRecord(activity, translator, recordDate, translatedVolume, workHours)
        ));
    }

    private void seedSprint3Showcase(User translator, Activity estimateActivity, Activity reminderActivity) {
        LocalDate today = LocalDate.now(businessClock);
        LocalDate currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        estimateIfMissing(estimateActivity, translator, currentWeek.minusWeeks(2), new BigDecimal("410.00"));
        estimateIfMissing(estimateActivity, translator, currentWeek.minusWeeks(1), new BigDecimal("260.00"));
        estimateIfMissing(estimateActivity, translator, currentWeek, new BigDecimal("120.00"));

        notificationIfMissing(new Notification(
                translator,
                null,
                NotificationType.DAILY_RECORD_REMINDER,
                today,
                "Record today's translated volume for your active work.",
                businessClock.instant()
        ));
        notificationIfMissing(new Notification(
                translator,
                reminderActivity,
                NotificationType.WEEKLY_ESTIMATE_REMINDER,
                currentWeek,
                "Add this week's remaining-volume estimate for " + reminderActivity.getActivityNumber() + ".",
                businessClock.instant()
        ));
    }

    private void seedNotificationShowcase(User maya, User manager, Activity privacyPolicy,
                                          Activity contractAppendix, Activity launchEmails) {
        LocalDate today = LocalDate.now(businessClock);
        LocalDate week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var now = businessClock.instant();
        notificationIfMissing(new Notification(maya, null, NotificationType.DAILY_RECORD_REMINDER,
                today.minusDays(1), "Demo history: yesterday's daily record reminder.", now.minusSeconds(86400)));
        notificationIfMissing(new Notification(maya, null, NotificationType.DAILY_RECORD_REMINDER,
                today.minusDays(2), "Demo history: remember to log translated pages each day.", now.minusSeconds(172800)));
        notificationIfMissing(new Notification(maya, privacyPolicy, NotificationType.WEEKLY_ESTIMATE_REMINDER,
                week, "Demo: update the remaining pages for " + privacyPolicy.getActivityNumber() + ".", now.minusSeconds(7200)));
        notificationIfMissing(new Notification(maya, launchEmails, NotificationType.WEEKLY_ESTIMATE_REMINDER,
                week, "Demo: check this week's estimate for " + launchEmails.getActivityNumber() + ".", now.minusSeconds(10800)));
        notificationIfMissing(new Notification(maya, privacyPolicy, NotificationType.WEEKLY_SUBMISSION_REMINDER,
                week, "Demo: submit a weekly version for " + privacyPolicy.getActivityNumber() + ".", now.minusSeconds(14400)));
        notificationIfMissing(new Notification(maya, launchEmails, NotificationType.WEEKLY_SUBMISSION_REMINDER,
                week, "Demo: the weekly version for " + launchEmails.getActivityNumber() + " is due.", now.minusSeconds(18000)));
        notificationIfMissing(new Notification(maya, contractAppendix, NotificationType.CORRECTION_REQUESTED,
                week, "Demo: Chief Editor requested corrections for " + contractAppendix.getActivityNumber() + ".", now.minusSeconds(21600)));
        notificationIfMissing(new Notification(maya, privacyPolicy, NotificationType.RECORD_EDIT_APPROVED,
                week.minusWeeks(1), "Demo history: a daily record edit was approved for " + privacyPolicy.getActivityNumber() + ".", now.minusSeconds(259200)));
        notificationIfMissing(new Notification(maya, launchEmails, NotificationType.RECORD_EDIT_REJECTED,
                week.minusWeeks(1), "Demo history: a daily record edit was declined for " + launchEmails.getActivityNumber() + ".", now.minusSeconds(345600)));
        notificationIfMissing(new Notification(manager, privacyPolicy, NotificationType.RECORD_EDIT_REQUESTED,
                week.minusWeeks(1), "Demo history: Maya requested a daily record edit for " + privacyPolicy.getActivityNumber() + ".", now.minusSeconds(432000)));
    }

    private void estimateIfMissing(
            Activity activity,
            User translator,
            LocalDate weekStart,
            BigDecimal remainingVolume
    ) {
        if (!weeklyEstimateRepository.existsByActivityIdAndWeekStartDate(activity.getId(), weekStart)) {
            weeklyEstimateRepository.save(new WeeklyEstimate(
                    activity,
                    translator,
                    weekStart,
                    remainingVolume,
                    businessClock.instant()
            ));
        }
    }

    private void notificationIfMissing(Notification notification) {
        if (!notificationRepository.existsByDedupeKey(notification.getDedupeKey())) {
            notificationRepository.save(notification);
        }
    }

    private record SeedUser(String email, String initials, String fullName, Role role) {
    }
}
