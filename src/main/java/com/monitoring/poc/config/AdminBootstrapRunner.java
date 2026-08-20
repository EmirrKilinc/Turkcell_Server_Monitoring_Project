package com.monitoring.poc.config;

import com.monitoring.poc.entity.User;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.enums.UserStatus;
import com.monitoring.poc.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds exactly one APPROVED admin on first boot. Without this, nobody could
 * ever approve the very first user - registration always starts PENDING and
 * only an admin can approve.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrapRunner(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${app.bootstrap.admin-username}") String adminUsername,
                                 @Value("${app.bootstrap.admin-email}") String adminEmail,
                                 @Value("${app.bootstrap.admin-password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            return;
        }

        User admin = new User(
                adminUsername,
                adminEmail,
                passwordEncoder.encode(adminPassword),
                Role.ADMIN,
                UserStatus.APPROVED
        );
        userRepository.save(admin);

        log.warn("Bootstrap admin olusturuldu: username='{}'. Ilk girişten sonra sifreyi degistirin.", adminUsername);
    }
}
