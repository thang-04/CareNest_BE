package com.carenest.repository.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class NotificationPushDeliveryMigrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Test
    void oldTerminalRowsAreNotReplayedAndLedgerConstraintsPreserveHistory() {
        String schema = "push_" + UUID.randomUUID().toString().replace("-", "");
        flyway(schema, "138").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                postgres.getUsername(),
                postgres.getPassword()));
        Long user = jdbc.queryForObject(
                "INSERT INTO user_accounts(keycloak_user_id, username, full_name) VALUES ('demo', 'demo', 'Demo') RETURNING id",
                Long.class);
        Long old = jdbc.queryForObject(
                "INSERT INTO notifications(user_id, type, title, body, created_at) VALUES (?, 'CHILD_WITHDRAWN', 'Tin cũ', 'Nội dung', now()) RETURNING id",
                Long.class,
                user);
        jdbc.update(
                "INSERT INTO notifications(user_id, type, title, body, created_at) VALUES (?, 'HEALTH_TREND_APPROVED', 'Tin cũ', 'Nội dung', now()), (?, 'WEEKLY_COMMENT_SENT', 'Tin cũ', 'Nội dung', now())",
                user,
                user);
        flyway(schema, "139").migrate();
        assertThat(jdbc.queryForObject("SELECT push_state FROM notifications WHERE id=?", String.class, old))
                .isEqualTo("SKIPPED");
        Long pending = jdbc.queryForObject(
                "INSERT INTO notifications(user_id, type, title, body, created_at, push_next_at) VALUES (?, 'CHILD_WITHDRAWN', 'Tin mới', 'Nội dung', now(), now() + interval '1 hour') RETURNING id",
                Long.class,
                user);
        Long sent = jdbc.queryForObject(
                "INSERT INTO notifications(user_id, type, title, body, created_at, push_state) VALUES (?, 'CHILD_WITHDRAWN', 'Đã gửi', 'Nội dung', now(), 'SENT') RETURNING id",
                Long.class,
                user);
        Long device = jdbc.queryForObject(
                "INSERT INTO device_tokens(user_id, token, platform, created_at, updated_at) VALUES (?, 'fake', 'ANDROID', now(), now()) RETURNING id",
                Long.class,
                user);
        flyway(schema, "142").migrate();
        assertThat(jdbc.queryForList("SELECT push_state FROM notifications ORDER BY id", String.class))
                .containsExactly("SKIPPED", "SKIPPED", "SKIPPED", "PENDING", "SENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_push_deliveries", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT push_initialized_at FROM notifications WHERE id=?", Object.class, pending))
                .isNull();
        jdbc.update(
                "INSERT INTO notification_push_deliveries(notification_id, device_id, created_at) VALUES (?, ?, now())",
                pending,
                device);
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO notification_push_deliveries(notification_id, device_id, created_at) VALUES (?, ?, now())",
                        pending,
                        device))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM device_tokens WHERE id=?", device);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notification_push_deliveries WHERE notification_id=?",
                        Integer.class,
                        pending))
                .isEqualTo(1);
        jdbc.update("DELETE FROM notifications WHERE id=?", pending);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notification_push_deliveries WHERE notification_id=?",
                        Integer.class,
                        pending))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT push_state FROM notifications WHERE id=?", String.class, sent))
                .isEqualTo("SENT");
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }
}
