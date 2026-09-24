package com.easylang.activitymonitoring.config;

import com.easylang.activitymonitoring.model.Role;
import com.easylang.activitymonitoring.model.User;
import com.easylang.activitymonitoring.repository.UserRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.seed-demo-users", havingValue = "true", matchIfMissing = true)
public class DemoDataConfig implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "Demo123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataConfig(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SeedUser> users = List.of(
                new SeedUser("translator@easylang.local", "MC", "Maya Chen", Role.TRANSLATOR),
                new SeedUser("editor@easylang.local", "EB", "Elias Brooks", Role.CHIEF_EDITOR),
                new SeedUser("manager@easylang.local", "OG", "Oliver Grant", Role.PROJECT_MANAGER)
        );

        users.forEach(seed -> userRepository.findByEmailIgnoreCase(seed.email()).orElseGet(() ->
                userRepository.save(new User(
                        seed.email(),
                        seed.initials(),
                        seed.fullName(),
                        passwordEncoder.encode(DEMO_PASSWORD),
                        seed.role()
                ))
        ));
    }

    private record SeedUser(String email, String initials, String fullName, Role role) {
    }
}
