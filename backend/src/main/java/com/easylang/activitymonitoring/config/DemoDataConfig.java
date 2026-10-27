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
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.NotificationRepository;
import com.easylang.activitymonitoring.repository.ProjectRepository;
import com.easylang.activitymonitoring.repository.ProjectChiefEditorRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import com.easylang.activitymonitoring.repository.WeeklyEstimateRepository;
import com.easylang.activitymonitoring.repository.WorkRecordRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
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
    private final PasswordEncoder passwordEncoder;
    private final Clock businessClock;
    private final Environment environment;

    public DemoDataConfig(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectChiefEditorRepository projectChiefEditorRepository,
            ActivityRepository activityRepository,
            ActivityTranslatorRepository activityTranslatorRepository,
            WorkRecordRepository workRecordRepository,
            WeeklyEstimateRepository weeklyEstimateRepository,
            NotificationRepository notificationRepository,
            PasswordEncoder passwordEncoder,
            Clock businessClock,
            Environment environment
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectChiefEditorRepository = projectChiefEditorRepository;
        this.activityRepository = activityRepository;
        this.activityTranslatorRepository = activityTranslatorRepository;
        this.workRecordRepository = workRecordRepository;
        this.weeklyEstimateRepository = weeklyEstimateRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.businessClock = businessClock;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SeedUser> seedUsers = List.of(
                new SeedUser("translator@easylang.local", "MC", "Maya Chen", Role.TRANSLATOR),
                new SeedUser("translator2@easylang.local", "RA", "Ravi Anand", Role.TRANSLATOR),
                new SeedUser("editor@easylang.local", "EB", "Elias Brooks", Role.CHIEF_EDITOR),
                new SeedUser("manager@easylang.local", "OG", "Oliver Grant", Role.PROJECT_MANAGER)
        );

        seedUsers.forEach(this::findOrCreateUser);

        User translator = userRepository.findByEmailIgnoreCase("translator@easylang.local").orElseThrow();
        User secondTranslator = userRepository.findByEmailIgnoreCase("translator2@easylang.local").orElseThrow();
        User manager = userRepository.findByEmailIgnoreCase("manager@easylang.local").orElseThrow();
        User editor = userRepository.findByEmailIgnoreCase("editor@easylang.local").orElseThrow();

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
        assignEditorIfMissing(legalPortal, editor);
        assignEditorIfMissing(productLaunch, editor);

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
            seedSprint3Showcase(translator, privacyPolicy, contractAppendix);
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

    private void assignEditorIfMissing(Project project, User editor) {
        if (!projectChiefEditorRepository.existsByProjectIdAndChiefEditorId(project.getId(), editor.getId())) {
            projectChiefEditorRepository.save(new ProjectChiefEditor(project, editor, true));
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
