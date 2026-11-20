package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.AgeGroupRequest;
import com.carenest.dto.organization.CampusRequest;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.dto.organization.RoomRequest;
import com.carenest.dto.organization.RoomResponse;
import com.carenest.exception.GlobalException;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.CampusService;
import com.carenest.service.organization.ClassManagementService;
import com.carenest.service.organization.RoomService;
import com.carenest.service.organization.YearSetupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CampusStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.RoomType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Cấu hình theo năm học (V154, Cần chốt 19/11/2026): sửa ở năm nào chỉ năm đó đổi. Mỗi test rollback. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class YearConfigPerYearIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private AgeGroupService ageGroupService;

    @Autowired
    private CampusService campusService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private ClassManagementService classManagementService;

    @Autowired
    private YearSetupService yearSetupService;

    @Autowired
    private AgeGroupRepository ageGroupRepository;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private long campusA;
    private long campusB;
    private long active;
    private long planned;
    private long group;

    @BeforeEach
    void setUp() {
        long principalId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-p', 'principal.it', 'Principal IT') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'Campus A', TRUE) RETURNING id");
        campusB = insertId("INSERT INTO campuses (code, name) VALUES ('CS-B', 'Campus B') RETURNING id");
        active = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        planned = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2027-2028', '2027-09-01', '2028-05-31', 'PLANNED') RETURNING id");
        group = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        for (long year : List.of(active, planned)) {
            ageGroupRepository.addAllToYear(year);
            campusRepository.addAllToYear(year);
            roomRepository.addAllToYear(year);
        }
        principal = new CurrentUser(
                principalId,
                "kc-p",
                "principal.it",
                "P",
                null,
                Set.of(CareNestRole.PRINCIPAL),
                Set.of(Permission.ROOM_MANAGE));
    }

    @Test
    @DisplayName("Không thêm nhóm tuổi mới vào năm học đang hoạt động")
    void activeYearRejectsNewAgeGroup() {
        AgeGroupRequest request =
                new AgeGroupRequest("Nhóm giữa năm", "Giữa năm", 73, 84, List.of(MealSession.LUNCH), 20, null);

        assertThatThrownBy(() -> ageGroupService.createAgeGroup(principal, request, active))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.AGE_GROUP_CREATE_YEAR_NOT_PLANNED);
    }

    @Test
    @DisplayName("Sửa nhóm tuổi ở năm sắp diễn ra: năm đang hoạt động và bảng chung không đổi; kích hoạt thì áp")
    void ageGroupInfoIsPerYear() {
        String before = jdbc.queryForObject("SELECT name FROM age_groups WHERE id = ?", String.class, group);
        AgeGroupDetailResponse updated = ageGroupService.updateAgeGroup(
                principal,
                group,
                new AgeGroupRequest(
                        "Mẫu giáo bé mới", "MG bé", 36, 48, List.of(MealSession.LUNCH), 28, "Ghi chú năm sau"),
                planned);

        assertThat(updated.maxClassSize()).isEqualTo(28);
        assertThat(ageGroupService.getYearAgeGroups(planned))
                .filteredOn(g -> g.id().equals(group))
                .extracting(AgeGroupDetailResponse::name, AgeGroupDetailResponse::ageToMonths)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Mẫu giáo bé mới", 48));
        assertThat(ageGroupService.getYearAgeGroups(active))
                .filteredOn(g -> g.id().equals(group))
                .extracting(AgeGroupDetailResponse::name)
                .containsExactly(before);
        assertThat(jdbc.queryForObject("SELECT name FROM age_groups WHERE id = ?", String.class, group))
                .isEqualTo(before);

        // Kích hoạt năm mới: bảng chung nhận thông tin của năm đó
        ageGroupRepository.applyYearInfo(planned);
        assertThat(jdbc.queryForObject("SELECT name FROM age_groups WHERE id = ?", String.class, group))
                .isEqualTo("Mẫu giáo bé mới");
    }

    @Test
    @DisplayName("Sửa nhóm tuổi ở năm đang hoạt động: bảng chung đổi theo, năm sau giữ nguyên")
    void activeYearUpdatesSharedTable() {
        ageGroupService.updateAgeGroup(
                principal,
                group,
                new AgeGroupRequest("Mẫu giáo bé", "MG bé", 36, 48, List.of(MealSession.LUNCH), 26, ""),
                active);

        assertThat(jdbc.queryForObject("SELECT max_class_size FROM age_groups WHERE id = ?", Integer.class, group))
                .isEqualTo(26);
        assertThat(ageGroupService.getYearAgeGroups(planned))
                .filteredOn(g -> g.id().equals(group))
                .extracting(AgeGroupDetailResponse::maxClassSize)
                .doesNotContain(26);
    }

    @Test
    @DisplayName("Ngừng điểm trường ở năm này, năm sau vẫn hoạt động; bật lại năm sau không đổi năm này")
    void campusStatusIsPerYear() {
        campusService.changeCampusStatus(principal, campusB, CampusStatus.INACTIVE, active);

        assertThat(status(campusService.getCampuses(principal, active), campusB))
                .isEqualTo(CampusStatus.INACTIVE);
        assertThat(status(campusService.getCampuses(principal, planned), campusB))
                .isEqualTo(CampusStatus.ACTIVE);
        assertThat(jdbc.queryForObject("SELECT status FROM campuses WHERE id = ?", String.class, campusB))
                .isEqualTo("INACTIVE");

        campusService.changeCampusStatus(principal, campusB, CampusStatus.INACTIVE, planned);
        campusService.changeCampusStatus(principal, campusB, CampusStatus.ACTIVE, planned);
        assertThat(status(campusService.getCampuses(principal, active), campusB))
                .isEqualTo(CampusStatus.INACTIVE);
        assertThat(status(campusService.getCampuses(principal, planned), campusB))
                .isEqualTo(CampusStatus.ACTIVE);

        // Không ngừng được điểm trường chính, ở năm nào cũng vậy
        assertThatThrownBy(() -> campusService.changeCampusStatus(principal, campusA, CampusStatus.INACTIVE, planned))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.MAIN_CAMPUS_NOT_DEACTIVATABLE);

        // Kích hoạt năm sau: bảng chung theo trạng thái của năm đó
        campusRepository.applyYearInfo(planned);
        assertThat(jdbc.queryForObject("SELECT status FROM campuses WHERE id = ?", String.class, campusB))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Phòng thêm, sửa, xóa ở năm sắp diễn ra chỉ đổi năm đó")
    void roomsArePerYear() {
        // Phòng có ở cả hai năm
        RoomResponse existing = roomService.createRoom(
                principal, new RoomRequest(campusA, "P101", "Phòng Mai", RoomType.CLASS, 25, null, ""), active);
        roomRepository.addToYear(planned, existing.id());

        RoomResponse created = roomService.createRoom(
                principal, new RoomRequest(campusA, "P301", "Phòng Sen", RoomType.CLASS, 25, null, ""), planned);

        assertThat(rooms(planned)).extracting(RoomResponse::id).contains(created.id());
        assertThat(rooms(active)).extracting(RoomResponse::id).doesNotContain(created.id());

        roomService.updateRoom(
                principal,
                existing.id(),
                new RoomRequest(
                        existing.campusId(),
                        existing.room(),
                        "Tên năm sau",
                        existing.type(),
                        existing.capacity(),
                        existing.managerUserId(),
                        existing.note()),
                planned);
        assertThat(rooms(planned))
                .filteredOn(r -> r.id().equals(existing.id()))
                .extracting(RoomResponse::name)
                .containsExactly("Tên năm sau");
        assertThat(rooms(active))
                .filteredOn(r -> r.id().equals(existing.id()))
                .extracting(RoomResponse::name)
                .containsExactly(existing.name());

        roomService.deleteRoom(principal, existing.id(), planned);
        assertThat(rooms(planned)).extracting(RoomResponse::id).doesNotContain(existing.id());
        assertThat(rooms(active)).extracting(RoomResponse::id).contains(existing.id());
        assertThatThrownBy(() -> roomService.updateRoom(
                        principal,
                        existing.id(),
                        new RoomRequest(campusA, "P101", "Không được sửa chéo năm", RoomType.CLASS, 25, null, ""),
                        planned))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("Phòng cùng mã và tên ở năm khác dùng lại phòng vật lý, không bị unique toàn cục chặn")
    void roomCanBeReusedInAnotherYear() {
        RoomRequest request = new RoomRequest(campusA, "P401", "Phòng Lá", RoomType.CLASS, 25, null, "");
        RoomResponse activeRoom = roomService.createRoom(principal, request, active);

        RoomResponse plannedRoom = roomService.createRoom(principal, request, planned);

        assertThat(plannedRoom.id()).isEqualTo(activeRoom.id());
        assertThat(rooms(active)).extracting(RoomResponse::id).contains(activeRoom.id());
        assertThat(rooms(planned)).extracting(RoomResponse::id).contains(activeRoom.id());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM rooms WHERE campus_id = ? AND lower(code) = lower(?)",
                        Long.class,
                        campusA,
                        request.room()))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Năm chưa phòng nào có người phụ trách: phòng không phải phòng học vẫn liệt kê được")
    void roomsWithoutAnyManagerAreListed() {
        RoomResponse kitchen = roomService.createRoom(
                principal, new RoomRequest(campusA, "BEP1", "Bếp", RoomType.KITCHEN, null, null, ""), active);

        assertThat(rooms(active))
                .filteredOn(r -> r.id().equals(kitchen.id()))
                .singleElement()
                .satisfies(r -> assertThat(r.managerName()).isNull());
    }

    @Test
    @DisplayName("Danh sách cấu hình rỗng của một năm không tự rơi về toàn bộ danh mục")
    void emptyYearConfigurationStaysEmpty() {
        // Phòng phụ thuộc điểm trường trong cùng năm; xóa theo đúng thứ tự quan hệ.
        jdbc.update("DELETE FROM school_year_room_members WHERE school_year_id = ?", planned);
        jdbc.update("DELETE FROM school_year_age_group_members WHERE school_year_id = ?", planned);
        jdbc.update("DELETE FROM school_year_campus_members WHERE school_year_id = ?", planned);

        assertThat(ageGroupService.getYearAgeGroups(planned)).isEmpty();
        assertThat(campusService.getCampuses(principal, planned)).isEmpty();
        assertThat(roomService.getRooms(principal, null, planned)).isEmpty();
        assertThatThrownBy(() -> campusService.getCampusDetail(principal, campusA, planned))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CAMPUS_NOT_IN_YEAR);

        assertThat(ageGroupService.getYearAgeGroups(active)).isNotEmpty();
        assertThat(campusService.getCampuses(principal, active)).isNotEmpty();
    }

    @Test
    @DisplayName("Màn lớp và wizard đọc tên/sức chứa phòng, tên/trạng thái điểm trường của đúng năm")
    void classAndYearSetupUseSelectedYearInformation() {
        RoomResponse room = roomService.createRoom(
                principal, new RoomRequest(campusA, "P201", "Phòng năm nay", RoomType.CLASS, 25, null, ""), active);
        roomRepository.addToYear(planned, room.id());
        roomService.updateRoom(
                principal,
                room.id(),
                new RoomRequest(campusA, "P201", "Phòng năm sau", RoomType.CLASS, 19, null, ""),
                planned);
        campusService.updateCampus(
                principal,
                campusB,
                new CampusRequest("CS-B", "Campus B năm sau", "B năm sau", "Địa chỉ B", "", false),
                planned);
        campusService.changeCampusStatus(principal, campusB, CampusStatus.INACTIVE, planned);

        long classId = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id,"
                + " capacity, status) VALUES (" + campusA + ", " + planned + ", 'Lớp năm sau', " + group + ", "
                + room.id() + ", 18, 'ACTIVE') RETURNING id");

        assertThat(classManagementService
                        .getSchoolStructure(principal, planned, campusA)
                        .classes())
                .filteredOn(c -> c.id().equals(classId))
                .extracting(c -> c.roomName())
                .containsExactly("Phòng năm sau");
        assertThat(yearSetupService.getYearSetup(planned).classes())
                .filteredOn(c -> c.id().equals(classId))
                .extracting(c -> c.roomCapacity())
                .containsExactly(19);
        assertThat(yearSetupService.getYearSetup(planned).campuses())
                .filteredOn(c -> c.id().equals(campusB))
                .extracting(c -> c.name())
                .containsExactly("B năm sau");
        assertThat(yearSetupService.getYearSetup(planned).campusesWithoutVicePrincipal())
                .doesNotContain(campusB);

        assertThat(roomService.getRooms(principal, null, active))
                .filteredOn(r -> r.id().equals(room.id()))
                .extracting(RoomResponse::name, RoomResponse::capacity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Phòng năm nay", 25));
    }

    private List<RoomResponse> rooms(long yearId) {
        return roomService.getRooms(principal, null, yearId);
    }

    private static CampusStatus status(List<CampusResponse> campuses, long campusId) {
        return campuses.stream()
                .filter(c -> c.id().equals(campusId))
                .findFirst()
                .orElseThrow()
                .status();
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
