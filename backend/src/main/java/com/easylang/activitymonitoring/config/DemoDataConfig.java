package com.easylang.activitymonitoring.config;

import com.easylang.activitymonitoring.model.Activity;
import com.easylang.activitymonitoring.model.ActivityStatus;
import com.easylang.activitymonitoring.model.ActivityTranslator;
import com.easylang.activitymonitoring.model.Project;
import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.model.WorkRecord;
import com.easylang.activitymonitoring.repository.ActivityRepository;
import com.easylang.activitymonitoring.repository.ActivityTranslatorRepository;
import com.easylang.activitymonitoring.repository.ProjectRepository;
import com.easylang.activitymonitoring.repository.UserRepository;
import com.easylang.activitymonitoring.repository.WorkRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
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
    private final ActivityRepository activityRepository;
    private final ActivityTranslatorRepository activityTranslatorRepository;
    private final WorkRecordRepository workRecordRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataConfig(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ActivityRepository activityRepository,
            ActivityTranslatorRepository activityTranslatorRepository,
            WorkRecordRepository workRecordRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.activityRepository = activityRepository;
        this.activityTranslatorRepository = activityTranslatorRepository;
        this.workRecordRepository = workRecordRepository;
        this.passwordEncoder = passwordEncoder;
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

    private record SeedUser(String email, String initials, String fullName, Role role) {
    }
}
