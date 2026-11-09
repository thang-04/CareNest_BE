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
        long children = count(schema, "select count(*) from %s.children");
        jdbc.update("update " + schema + ".flyway_schema_history set checksum = 0 where script like 'R__dev_seed%'");
        flyway(schema, "classpath:db/migration", "classpath:db/dev").migrate();
        assertThat(classSnapshot(schema)).isEqualTo(snapshot);
        assertThat(count(schema, "select count(*) from %s.children")).isEqualTo(children);
        assertThat(count(
                        schema, "select count(*) from %s.staff_assignments where staff_role = 'AGE_GROUP_TEAM_LEADER'"))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("Seed Thượng Hồng: 2 điểm trường tên tạm, 10 lớp (5/cơ sở), mỗi lớp 2 GV đang dạy và có trẻ")
    void thuongHongSeedBuildsTestStructure() {
        String schema = "dev_seed_thuong_hong";
        flyway(schema, "classpath:db/migration", "classpath:db/dev").migrate();

        assertThat(count(schema, "select count(*) from %s.campuses where name like '%%MN Thượng Hồng'"))
                .isEqualTo(2);
        assertThat(count(schema, "select count(*) from %s.campuses where name like '%%(demo)%%'"))
                .isZero();
        assertThat(count(schema, "select count(*) from %s.classrooms")).isEqualTo(10);
        assertThat(
                        count(
                                schema,
                                "select count(*) from %1$s.classrooms cl where (select count(*) from %1$s.staff_assignments a"
                                        + " where a.classroom_id = cl.id and a.staff_role = 'TEACHER' and a.valid_to is null) <> 2"))
                .isZero();
        assertThat(count(
                        schema,
                        "select count(*) from %1$s.classrooms cl where (select count(*) from %1$s.enrollments e"
                                + " where e.classroom_id = cl.id and e.valid_to is null) not between 15 and 25"))
                .isZero();
        assertThat(count(
                        schema,
                        "select count(*) from %1$s.classrooms cl join %1$s.age_groups g on g.id = cl.age_group_id"
                                + " where (select count(*) from %1$s.enrollments e where e.classroom_id = cl.id"
                                + " and e.valid_to is null) > g.max_class_size"))
                .isZero();
        // Tuổi tại 01/09/2026 nằm trong khoảng tháng của nhóm tuổi lớp (SCH-01)
        assertThat(
                        count(
                                schema,
                                "select count(*) from %1$s.enrollments e join %1$s.classrooms cl on cl.id = e.classroom_id"
                                        + " join %1$s.age_groups g on g.id = cl.age_group_id join %1$s.children ch on ch.id = e.child_id"
                                        + " where ch.code >= 'HS26101' and not (date '2026-09-01' >= ch.date_of_birth + make_interval(months => g.age_from_months::int)"
                                        + " and date '2026-09-01' < ch.date_of_birth + make_interval(months => g.age_to_months::int))"))
                .isZero();
        assertThat(count(schema, "select count(*) from %s.children where status = 'ACTIVE'"))
                .isGreaterThan(200);
    }

    @Test
    @DisplayName("Seed Thượng Hồng: danh mục món, kho khô, thực đơn mẫu nhất quán và chạy lại không nhân đôi")
    void thuongHongCatalogIsConsistentAndIdempotent() {
        String schema = "dev_seed_th_catalog";
        flyway(schema, "classpath:db/migration", "classpath:db/dev").migrate();

        assertThat(count(schema, "select count(*) from %s.foods where code like 'VDD%%'"))
                .isEqualTo(80);
        // Mỗi món mới có công thức đủ 4 nhóm tuổi
        assertThat(count(
                        schema,
                        "select count(*) from %1$s.dishes d where d.code >= 'M010' and (select count(distinct"
                                + " i.age_group_id) from %1$s.dish_ingredients i where i.dish_id = d.id) <> 4"))
                .isZero();
        // Tồn đầu của mỗi lô = bút toán nhập
        assertThat(
                        count(
                                schema,
                                "select count(*) from %1$s.stock_lots l where l.quantity <> (select coalesce(sum(m.quantity), 0)"
                                        + " from %1$s.stock_movements m where m.lot_id = l.id and m.movement_type = 'RECEIPT')"))
                .isZero();
        // Mọi thực phẩm khô của thực đơn đã công bố đều có lô ở cả hai điểm trường
        assertThat(
                        count(
                                schema,
                                "select count(*) from %1$s.campuses c cross join (select distinct di.food_id from"
                                        + " %1$s.meal_plan_items i join %1$s.dish_ingredients di on di.dish_id = i.dish_id"
                                        + " join %1$s.foods f on f.id = di.food_id and f.storage = 'DRY') x where not exists"
                                        + " (select 1 from %1$s.stock_lots l where l.campus_id = c.id and l.food_id = x.food_id)"))
                .isZero();
        assertThat(count(schema, "select count(*) from %s.meal_plan_weeks where status = 'PUBLISHED'"))
                .isEqualTo(4);
        assertThat(count(
                        schema,
                        "select count(*) from %1$s.meal_plan_days d where (select count(distinct i.menu_block)"
                                + " from %1$s.meal_plan_items i where i.day_id = d.id) <> 2"))
                .isZero();
        assertThat(count(schema, "select count(*) from %s.menu_template_items")).isEqualTo(220);

        String snapshot = String.join(
                ",",
                count(schema, "select count(*) from %s.foods") + "",
                count(schema, "select count(*) from %s.dish_ingredients") + "",
                count(schema, "select count(*) from %s.stock_movements") + "",
                count(schema, "select count(*) from %s.meal_plan_items") + "");
        jdbc.update("update " + schema + ".flyway_schema_history set checksum = 0 where script like 'R__dev_seed%'");
        flyway(schema, "classpath:db/migration", "classpath:db/dev").migrate();
        assertThat(String.join(
                        ",",
                        count(schema, "select count(*) from %s.foods") + "",
                        count(schema, "select count(*) from %s.dish_ingredients") + "",
                        count(schema, "select count(*) from %s.stock_movements") + "",
                        count(schema, "select count(*) from %s.meal_plan_items") + ""))
                .isEqualTo(snapshot);
    }

    @Test
    @DisplayName(
            "Seed Thượng Hồng: vận hành 19/10–04/11 — điểm danh, sức khỏe, sổ báo ăn, trừ kho khớp nhau; chạy lại không nhân đôi")
    void thuongHongOperationsAreConsistentAndIdempotent() {
        String schema = "dev_seed_th_ops";
        flyway(schema, "classpath:db/migration", "classpath:db/dev").migrate();

        // 13 ngày học (thứ 2–6), mỗi trẻ đang học một bản ghi mỗi ngày, hôm nay 05/11 để trống
        assertThat(count(schema, "select count(distinct date) from %s.attendance_records"))
                .isEqualTo(13);
        assertThat(count(
                        schema,
                        "select count(*) from %1$s.attendance_records where extract(isodow from date) > 5"
                                + " or date >= date '2026-11-05'"))
                .isZero();
        assertThat(count(schema, "select count(*) from %s.attendance_records"))
                .isEqualTo(13 * count(schema, "select count(*) from %s.enrollments where valid_to is null"));
        // Có mặt ăn đủ bữa của nhóm tuổi, vắng không ăn bữa nào (ATT-02)
        assertThat(count(
                        schema,
                        "select count(*) from %1$s.attendance_records r join %1$s.classrooms cl on cl.id = r.classroom_id"
                                + " join %1$s.age_groups g on g.id = cl.age_group_id where r.meals <>"
                                + " case when r.status = 'PRESENT' then g.meal_sessions else '' end"))
                .isZero();
        assertThat(count(schema, "select count(*) from %s.attendance_record_history"))
                .isEqualTo(count(schema, "select count(*) from %s.attendance_records"));
        assertThat(count(schema, "select count(*) from %s.attendance_records where status <> 'PRESENT'"))
                .isPositive();
        // Sức khỏe đầu ngày: đúng một dòng cho mỗi trẻ có mặt; dòng cần lưu ý có biểu hiện, dòng bình thường thì không
        assertThat(count(schema, "select count(*) from %s.daily_health_entries"))
                .isEqualTo(count(schema, "select count(*) from %s.attendance_records where status = 'PRESENT'"));
        assertThat(
                        count(
                                schema,
                                "select count(*) from %s.daily_health_entries where (observation = 'NOTED') <> (symptoms <> '')"))
                .isZero();
        // Sổ báo ăn: 2 điểm trường × 13 ngày × 3 bữa đã xác nhận; mỗi dòng lớp khớp điểm danh của lớp–ngày
        assertThat(count(schema, "select count(*) from %s.meal_counts where status = 'CONFIRMED'"))
                .isEqualTo(78);
        assertThat(count(
                        schema,
                        "select count(*) from %1$s.meal_count_lines l join %1$s.meal_counts mc on mc.id = l.meal_count_id"
                                + " where l.present <> (select count(*) from %1$s.attendance_records r where"
                                + " r.classroom_id = l.classroom_id and r.date = mc.date and r.status = 'PRESENT')"
                                + " or l.normal <> l.present or l.present + l.absent + l.missing <>"
                                + " (select count(*) from %1$s.attendance_records r where r.classroom_id = l.classroom_id"
                                + " and r.date = mc.date)"))
                .isZero();
        // Kho: nhu cầu khớp tổng bút toán trừ, không lô nào âm, không thiếu kho
        assertThat(count(schema, "select count(*) from %s.stock_daily_demands")).isPositive();
        assertThat(count(schema, "select count(*) from %s.stock_daily_demands where shortfall_qty <> 0"))
                .isZero();
        assertThat(
                        count(
                                schema,
                                "select count(*) from %1$s.stock_daily_demands d where d.required_qty <> (select"
                                        + " coalesce(-sum(m.quantity), 0) from %1$s.stock_movements m where m.campus_id = d.campus_id"
                                        + " and m.food_id = d.food_id and m.meal_date = d.meal_date and m.movement_type = 'DEDUCT')"))
                .isZero();
        assertThat(count(
                        schema,
                        "select count(*) from %1$s.stock_lots l where (select sum(m.quantity) from %1$s.stock_movements m"
                                + " where m.lot_id = l.id) < 0"))
                .isZero();

        String snapshot = opsSnapshot(schema);
        jdbc.update("update " + schema + ".flyway_schema_history set checksum = 0 where script like 'R__dev_seed%'");
        flyway(schema, "classpath:db/migration", "classpath:db/dev").migrate();
        assertThat(opsSnapshot(schema)).isEqualTo(snapshot);
    }

    private String opsSnapshot(String schema) {
        return String.join(
                ",",
                count(schema, "select count(*) from %s.attendance_records") + "",
                count(schema, "select count(*) from %s.attendance_record_history") + "",
                count(schema, "select count(*) from %s.daily_health_entries") + "",
                count(schema, "select count(*) from %s.meal_counts") + "",
                count(schema, "select count(*) from %s.meal_count_lines") + "",
                count(schema, "select count(*) from %s.meal_count_history") + "",
                count(schema, "select count(*) from %s.stock_daily_demands") + "",
                count(schema, "select count(*) from %s.stock_movements") + "",
                count(schema, "select count(*) from %s.user_accounts") + "",
                count(schema, "select count(*) from %s.child_guardians where user_id is not null") + "",
                count(schema, "select count(*) from %s.leave_requests") + "",
                count(schema, "select count(*) from %s.pickup_records") + "",
                count(schema, "select count(*) from %s.notifications") + "",
                count(schema, "select count(*) from %s.daily_observations") + "",
                count(schema, "select count(*) from %s.class_daily_comments") + "");
    }

    @Test
    @DisplayName("Seed Thượng Hồng: phụ huynh, đơn nghỉ, đón trẻ, thông báo, đánh giá hằng ngày khớp điểm danh")
    void thuongHongParentsLeavePickupObservationsAreConsistent() {
        String schema = "dev_seed_th_daily";
        flyway(schema, "classpath:db/migration", "classpath:db/dev").migrate();

        // Một nửa trẻ Thượng Hồng có tài khoản phụ huynh liên kết; vắng có phép chỉ ở trẻ có phụ huynh (ATT-04)
        assertThat(
                        count(
                                schema,
                                "select count(*) from %s.user_accounts where keycloak_user_id like '00000000-0000-4000-d000-%%'"))
                .isGreaterThan(90);
        assertThat(
                        count(
                                schema,
                                "select count(*) from %1$s.attendance_records r where r.status = 'EXCUSED' and not exists"
                                        + " (select 1 from %1$s.child_guardians l where l.child_id = r.child_id and l.user_id is not null)"))
                .isZero();
        assertThat(count(schema, "select count(*) from %s.attendance_records where status = 'EXCUSED'"))
                .isPositive();
        assertThat(count(schema, "select count(*) from %s.leave_requests"))
                .isEqualTo(count(schema, "select count(*) from %s.attendance_records where status = 'EXCUSED'"));
        // Mỗi trẻ có mặt được trả đúng một lần/ngày; thông báo = số lượt trả × tài khoản phụ huynh liên kết
        assertThat(count(schema, "select count(*) from %s.pickup_records where outcome = 'HANDED_OVER'"))
                .isEqualTo(count(schema, "select count(*) from %s.attendance_records where status = 'PRESENT'"));
        assertThat(count(schema, "select count(*) from %s.pickup_records where outcome = 'NOT_HANDED_OVER'"))
                .isPositive();
        assertThat(count(
                        schema,
                        "select count(*) from %1$s.pickup_records p where p.outcome = 'HANDED_OVER' and p.notified_parents"
                                + " <> (select count(*) from %1$s.child_guardians l where l.child_id = p.child_id"
                                + " and l.user_id is not null)"))
                .isZero();
        assertThat(count(schema, "select count(*) from %s.notifications"))
                .isEqualTo(count(
                        schema,
                        "select count(*) from %1$s.pickup_records p join %1$s.child_guardians l on l.child_id ="
                                + " p.child_id and l.user_id is not null where p.outcome = 'HANDED_OVER'"));
        // Đánh giá ngày: đúng một dòng cho mỗi trẻ có mặt, mức ăn chỉ cho bữa của nhóm tuổi; một nhận xét lớp/ngày
        assertThat(count(schema, "select count(*) from %s.daily_observations"))
                .isEqualTo(count(schema, "select count(*) from %s.attendance_records where status = 'PRESENT'"));
        assertThat(count(
                        schema,
                        "select count(*) from %1$s.daily_observations o join %1$s.classrooms cl on cl.id = o.classroom_id"
                                + " join %1$s.age_groups g on g.id = cl.age_group_id where exists (select 1 from"
                                + " jsonb_object_keys(o.meals) k where position(k in g.meal_sessions) = 0)"))
                .isZero();
        assertThat(count(schema, "select count(*) from %s.class_daily_comments"))
                .isEqualTo(count(
                        schema,
                        "select count(*) from (select distinct classroom_id, date from %s.attendance_records"
                                + " where status = 'PRESENT') x"));
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
