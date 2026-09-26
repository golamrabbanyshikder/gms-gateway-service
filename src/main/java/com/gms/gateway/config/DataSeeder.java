package com.gms.gateway.config;

import com.gms.gateway.entity.Role;
import com.gms.gateway.entity.User;
import com.gms.gateway.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a default SUPER_ADMIN user on startup if one doesn't already exist.
 * Replaces the old hardcoded admin/password credentials.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("Admin@123"));
            admin.setFullName("System Administrator");
            admin.setRole(Role.SUPER_ADMIN);
            admin.setEnabled(true);
            userRepository.save(admin);
            logger.info("Seeded default admin user (username=admin) - please change the default password after first login.");
        }
    }
}
