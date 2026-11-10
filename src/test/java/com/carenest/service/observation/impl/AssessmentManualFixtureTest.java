package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AssessmentManualFixtureTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void documentedPsqlFixtureCommitsHistoryAndRejectsInvalidClassPairWithoutAddingChildren() throws Exception {
        long campus = id("INSERT INTO campuses(code,name,is_main) VALUES ('FIXTURE','Fixture',true) RETURNING id");
        long year = id(
                "INSERT INTO school_years(name,start_date,end_date,status) VALUES ('2026-2027','2026-09-01','2027-05-31','ACTIVE') RETURNING id");
        long a = id("INSERT INTO classrooms(campus_id,school_year_id,name) VALUES (" + campus + "," + year
                + ",'Fixture A') RETURNING id");
        long b = id("INSERT INTO classrooms(campus_id,school_year_id,name) VALUES (" + campus + "," + year
                + ",'Fixture B') RETURNING id");
        long teacherA = id(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name) VALUES ('fixture-a','fixture-a','Fixture A') RETURNING id");
        long teacherB = id(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name) VALUES ('fixture-b','fixture-b','Fixture B') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,classroom_id,valid_from) VALUES (?, 'TEACHER', ?, '2026-09-01'), (?, 'TEACHER', ?, '2026-09-01')",
                teacherA,
                a,
                teacherB,
                b);
        postgres.copyFileToContainer(
                MountableFile.forHostPath("docs/quality/fixtures/assessment-continuity.sql"), "/tmp/continuity.sql");
        var result = run(a, b, teacherA, teacherB);
        assertThat(result.getExitCode())
                .withFailMessage(result.getStderr() + result.getStdout())
                .isZero();
        long child = jdbc.queryForObject("SELECT id FROM children WHERE full_name LIKE 'TEST Lịch sử %'", Long.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM enrollments WHERE child_id=?", Integer.class, child))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM attendance_records WHERE child_id=?", Integer.class, child))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM daily_observations WHERE child_id=? AND flag", Integer.class, child))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM child_evaluations WHERE child_id=?", String.class, child))
                .isEqualTo("CONFIRMED");
        assertThat(run(a, a, teacherA, teacherB).getExitCode()).isNotZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM children", Integer.class))
                .isEqualTo(1);
    }

    private org.testcontainers.containers.Container.ExecResult run(long a, long b, long ta, long tb) throws Exception {
        return postgres.execInContainer(
                "psql",
                "-U",
                postgres.getUsername(),
                "-d",
                postgres.getDatabaseName(),
                "-v",
                "class_a=" + a,
                "-v",
                "class_b=" + b,
                "-v",
                "teacher_a=" + ta,
                "-v",
                "teacher_b=" + tb,
                "-v",
                "as_of=2026-11-03",
                "-f",
                "/tmp/continuity.sql");
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
