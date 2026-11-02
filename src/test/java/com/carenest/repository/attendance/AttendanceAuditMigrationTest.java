package com.carenest.repository.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class AttendanceAuditMigrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void existingTeacherAuditIsBackfilledAndSurvivesDeletionAfterUpgrade() {
        var config = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        config.target("51").load().migrate();
        var jdbc = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        long campus = id(jdbc, "INSERT INTO campuses (code,name) VALUES ('AUDIT','Demo') RETURNING id");
        long year = id(
                jdbc,
                "INSERT INTO school_years (name,start_date,end_date) VALUES ('2026-2027','2026-09-01','2027-05-31') RETURNING id");
        long cls = id(
                jdbc,
                "INSERT INTO classrooms (campus_id,school_year_id,name) VALUES (?,?,'Demo') RETURNING id",
                campus,
                year);
        long child =
                id(jdbc, "INSERT INTO children (full_name,date_of_birth) VALUES ('Demo','2023-01-01') RETURNING id");
        long teacher = id(
                jdbc,
                "INSERT INTO user_accounts (keycloak_user_id,username,full_name) VALUES ('audit-demo','audit-demo','Demo') RETURNING id");
        long record = id(
                jdbc,
                "INSERT INTO attendance_records (child_id,classroom_id,campus_id,date,status,meals,recorded_by,recorded_at) VALUES (?,?,?,'2026-10-28','PRESENT','LUNCH',?,now()) RETURNING id",
                child,
                cls,
                campus,
                teacher);
        jdbc.update(
                "INSERT INTO attendance_record_history (record_id,action,status,meals,user_id,at) VALUES (?,'RECORDED','PRESENT','LUNCH',?,now())",
                record,
                teacher);
        config.target("latest").load().migrate();
        jdbc.update("DELETE FROM attendance_records WHERE id = ?", record);
        var row = jdbc.queryForMap("SELECT * FROM attendance_record_history WHERE record_id = ?", record);
        assertThat(row.get("child_id")).isEqualTo(child);
        assertThat(row.get("classroom_id")).isEqualTo(cls);
        assertThat(row.get("campus_id")).isEqualTo(campus);
        assertThat(row.get("date").toString()).isEqualTo("2026-10-28");
        assertThat(row.get("status")).isEqualTo("PRESENT");
        assertThat(row.get("meals")).isEqualTo("LUNCH");
        assertThat(row.get("user_id")).isEqualTo(teacher);
    }

    private long id(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
