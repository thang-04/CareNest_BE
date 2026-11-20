package com.carenest.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Một PostgreSQL dùng chung cho mọi test Spring trong cùng JVM: Flyway chỉ migrate một lần. Test cần DB sạch riêng
 * (migration theo version, seed) tự khai báo container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    // Đủ cho tối đa 32 context trong cache Spring × pool Hikari 10
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withCommand("postgres", "-c", "max_connections=400");

    // Không khai báo container là bean: Spring dừng container khi context bị đóng/evict khỏi cache,
    // làm hỏng các context khác còn dùng chung. Ryuk của Testcontainers dọn container khi JVM kết thúc.
    @Bean
    DynamicPropertyRegistrar postgresProperties() {
        start();
        return registry -> {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        };
    }

    private static synchronized void start() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
    }
}
