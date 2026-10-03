package com.carenest.config;

import com.carenest.entity.Role;
import com.carenest.entity.User;
import com.carenest.repository.UserRepository;
import com.carenest.utils.IdentifierUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Creates the first PRINCIPAL account at startup when its email does not exist yet,
 * so that the principal can log in and create staff accounts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = IdentifierUtils.normalizeEmail(adminEmail);
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.builder()
                .email(email)
                .fullName("Hiệu trưởng")
                .password(passwordEncoder.encode(adminPassword))
                .roles(new HashSet<>(Set.of(Role.PRINCIPAL)))
                .mustChangePassword(true)
                .build());
        log.info("Created default principal account {}", email);
    }
}
