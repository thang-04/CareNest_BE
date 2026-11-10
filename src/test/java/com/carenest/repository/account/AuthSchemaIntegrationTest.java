package com.carenest.repository.account;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AuthSchemaIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("AUTH-07: schema hiện tại chỉ còn password_tokens và otp_send_limits, không còn tên web_auth")
    void keepsOnlyRenamedRecoveryStateTables() {
        assertThat(table("password_tokens")).isTrue();
        assertThat(table("otp_send_limits")).isTrue();
        for (String old : java.util.List.of("web_auth_sessions", "web_auth_challenges", "web_auth_rate_limits"))
            assertThat(table(old)).isFalse();
        assertThat(jdbc.queryForObject(
                        "select count(*) from pg_indexes where schemaname='public' and indexname like '%web_auth%'",
                        Long.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from pg_constraint c join pg_namespace n on n.oid=c.connamespace"
                                + " where n.nspname='public' and c.conname like '%web_auth%'",
                        Long.class))
                .isZero();
    }

    @Test
    @DisplayName(
            "AUTH-07: V5 xóa bảng phiên, V6 đổi tên bảng; dữ liệu tài khoản, token mật khẩu, giới hạn OTP giữ nguyên")
    void upgradeFromV4PreservesRecoveryStateAndAccounts() {
        String schema = "v5_upgrade_check";
        var flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .locations("classpath:db/migration");
        flyway.target("4").load().migrate();
        jdbc.update("insert into " + schema
                + ".user_accounts(keycloak_user_id,username,full_name) values ('kc-v5','v5.user','Demo')");
        Long userId =
                jdbc.queryForObject("select id from " + schema + ".user_accounts where username='v5.user'", Long.class);
        jdbc.update(
                "insert into " + schema
                        + ".web_auth_sessions(token_hash,user_id,keycloak_user_id,access_cipher,refresh_cipher,"
                        + "access_expires_at,created_at,expires_at,idle_expires_at,remember) values (?,?,'kc-v5','a','r',now(),now(),"
                        + "now()+interval '10 hours',now()+interval '30 minutes',true)",
                "a".repeat(64),
                userId);
        jdbc.update(
                "insert into " + schema
                        + ".web_auth_challenges(token_hash,purpose,lookup_hash,otp_hmac,created_at,expires_at,"
                        + "resend_available_at,attempts) values (?, 'OTP', ?, ?, now(), now()+interval '5 minutes', now(), 0)",
                "b".repeat(64),
                "c".repeat(64),
                "d".repeat(64));
        jdbc.update(
                "insert into " + schema
                        + ".web_auth_rate_limits(bucket_hash,used,expires_at) values (?,2,now()+interval '1 minute')",
                "e".repeat(64));
        flyway.target("latest").load().migrate();
        assertThat(jdbc.queryForObject(
                        "select count(*) from information_schema.tables where table_schema=? and table_name='web_auth_sessions'",
                        Long.class,
                        schema))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from pg_indexes where schemaname=? and indexname='idx_web_auth_challenges_lookup'",
                        Long.class,
                        schema))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".user_accounts", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select lookup_hash from " + schema + ".password_tokens", String.class))
                .isEqualTo("c".repeat(64));
        assertThat(jdbc.queryForObject("select used from " + schema + ".otp_send_limits", Integer.class))
                .isEqualTo(2);
        jdbc.execute("drop schema " + schema + " cascade");
    }

    private boolean table(String name) {
        return jdbc.queryForObject(
                        "select count(*) from information_schema.tables where table_schema='public' and table_name=?",
                        Long.class,
                        name)
                == 1;
    }

    @Test
    void recoveryDecoyCanExistWithoutAccountButPasswordCapabilityCannot() {
        assertThatNoException().isThrownBy(() -> jdbc.update("""
            insert into password_tokens(token_hash,purpose,lookup_hash,otp_hmac,created_at,expires_at,resend_available_at,attempts)
            values (?, 'OTP', ?, ?, now(), now()+interval '5 minutes', now()+interval '60 seconds', 0)
            """, "a".repeat(64), "b".repeat(64), "c".repeat(64)));
        assertThatThrownBy(() -> jdbc.update("""
            insert into password_tokens(token_hash,purpose,created_at,expires_at,attempts)
            values (?, 'RESET', now(), now()+interval '10 minutes', 0)
            """, "d".repeat(64)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("delete from password_tokens");
    }

    @Test
    void rateLimitClaimIsAtomicAndNeverExceedsItsBudget() {
        assertThatNoException().isThrownBy(() -> {
            for (int index = 0; index < 8; index++) jdbc.update("""
                insert into otp_send_limits(bucket_hash,used,expires_at) values (?,1,now()+interval '1 minute')
                on conflict(bucket_hash) do update set used=otp_send_limits.used+1 where otp_send_limits.used<5
                """, "e".repeat(64));
            assertThat(jdbc.queryForObject(
                            "select used from otp_send_limits where bucket_hash=?", Integer.class, "e".repeat(64)))
                    .isEqualTo(5);
        });
        jdbc.update("delete from otp_send_limits");
    }
}
