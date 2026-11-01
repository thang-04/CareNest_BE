package com.carenest.repository.organization;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
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
class DevSeedIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("NUT-17, SCH-03, AUTH-23: seed dev mới cho lớp demo có nhóm tuổi có bữa, phòng, sức chứa và tổ trưởng")
    void freshSeedSatisfiesClassInvariants() {
        String schema = "dev_seed_fresh";
        flyway(schema, "classpath:db/migration", "classpath:db/dev").migrate();

        assertClassInvariants(schema);
    }

    @Test
    @DisplayName("NUT-17: DB dev cũ có lớp A1/B1 thiếu nhóm tuổi/phòng ⇒ seed mới điền vào, chạy lại không đổi")
    void seedPatchesLegacyClassesWithoutAgeGroup() {
        String schema = "dev_seed_legacy";
        flyway(schema, "classpath:db/migration").migrate();
        // Trạng thái seed cũ: lớp chỉ có campus, năm, tên
        jdbc.update("insert into " + schema + ".campuses(code, name, short_name, address) values"
                + " ('CS-A','Cơ sở A (demo)','Cơ sở A','A'), ('CS-B','Cơ sở B (demo)','Cơ sở B','B')");
        jdbc.update("insert into " + schema + ".school_years(name, start_date, end_date)"
                + " values ('2026-2027','2026-09-01','2027-05-31')");
        jdbc.update("insert into " + schema + ".classrooms(campus_id, school_year_id, name)"
                + " select c.id, y.id, v.name from (values ('CS-A','Lớp A1'), ('CS-B','Lớp B1')) v(code, name)"
                + " join " + schema + ".campuses c on c.code = v.code"
                + " join " + schema + ".school_years y on y.name = '2026-2027'");

        flyway(schema, "classpath:db/migration", "classpath:db/dev").migrate();
        assertClassInvariants(schema);

        String snapshot = classSnapshot(schema);
        jdbc.update("update " + schema + ".flyway_schema_history set checksum = 0 where script = 'R__dev_seed.sql'");
        flyway(schema, "classpath:db/migration", "classpath:db/dev").migrate();
        assertThat(classSnapshot(schema)).isEqualTo(snapshot);
        assertThat(count(
                        schema, "select count(*) from %s.staff_assignments where staff_role = 'AGE_GROUP_TEAM_LEADER'"))
                .isEqualTo(2);
    }

    private void assertClassInvariants(String schema) {
        assertThat(count(schema, "select count(*) from %s.classrooms where name in ('Lớp A1', 'Lớp B1')"))
                .isEqualTo(2);
        assertThat(count(
                        schema,
                        "select count(*) from %1$s.classrooms cl left join %1$s.age_groups g on g.id = cl.age_group_id"
                                + " where cl.room_id is null or cl.capacity is null or g.meal_sessions is null"
                                + " or g.meal_sessions = ''"))
                .isZero();
        assertThat(count(
                        schema,
                        "select count(*) from %1$s.classrooms cl where not exists (select 1 from %1$s.staff_assignments a"
                                + " where a.staff_role = 'AGE_GROUP_TEAM_LEADER' and a.age_group_id = cl.age_group_id"
                                + " and a.school_year_id = cl.school_year_id)"))
                .isZero();
    }

    private String classSnapshot(String schema) {
        return String.join(
                ";",
                jdbc.queryForList(
                        "select name || ':' || age_group_id || ':' || room_id || ':' || capacity from " + schema
                                + ".classrooms order by name",
                        String.class));
    }

    private long count(String schema, String sql) {
        return jdbc.queryForObject(sql.formatted(schema), Long.class);
    }

    private Flyway flyway(String schema, String... locations) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .locations(locations)
                .load();
    }
}
