package com.carenest.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class EduDomainBootstrap {
    @Bean
    ApplicationRunner initializeEducationTemplates(JdbcTemplate jdbc) {
        return args -> jdbc.update(new org.springframework.core.io.ClassPathResource("db/edu_plan_defaults_seed.sql")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Bean
    ApplicationRunner initializeEducationDomains(JdbcTemplate jdbc) {
        return args -> jdbc.update("""
                INSERT INTO edu_domains (name, display_label, short_label, sort_order, standard) VALUES
                    ('Phát triển thể chất', NULL, 'Thể chất', 1, TRUE),
                    ('Phát triển nhận thức', NULL, 'Nhận thức', 2, TRUE),
                    ('Phát triển ngôn ngữ', NULL, 'Ngôn ngữ', 3, TRUE),
                    ('Phát triển tình cảm và kỹ năng xã hội', NULL, 'Tình cảm – xã hội', 4, TRUE),
                    ('Phát triển thẩm mỹ', 'Phát triển thẩm mỹ (Nghệ thuật)', 'Thẩm mỹ (Nghệ thuật)', 5, TRUE)
                ON CONFLICT (name) DO NOTHING
                """);
    }
}
