package com.carenest.service.child.impl;

import com.carenest.config.DevActivationProperties;
import com.carenest.dto.child.AutoPlaceRequest;
import com.carenest.dto.child.AutoPlaceResponse;
import com.carenest.dto.child.PlacementBoardResponse;
import com.carenest.dto.child.SavePlacementsRequest;
import com.carenest.dto.child.YearReadinessResponse;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.dto.organization.YearSetupResponse;
import com.carenest.entity.child.Child;
import com.carenest.entity.child.ClassPlacement;
import com.carenest.entity.child.Enrollment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.ChildRepository;
import com.carenest.repository.child.ClassPlacementRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.YearTransitionService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.service.organization.YearSetupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.PlacementAction;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class YearTransitionServiceImpl implements YearTransitionService {
    private static final String LOG_PREFIX = "[YearTransitionServiceImpl]";
    // Đề xuất FE 13/10/2026 (ACTIVATION_LEAD_DAYS): được kích hoạt từ 7 ngày trước ngày bắt đầu
    private static final int ACTIVATION_LEAD_DAYS = 7;
    private static final int TEACHERS_PER_CLASS = 2;
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ClassPlacementRepository classPlacementRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ChildRepository childRepository;
    private final YearSetupService yearSetupService;
    private final SchoolYearService schoolYearService;
    private final StaffAssignmentService staffAssignmentService;
    private final Clock clock;
    private final DevActivationProperties devActivation;

    @Override
    @Transactional(readOnly = true)
    public PlacementBoardResponse getPlacements(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|getPlacements|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            Set<Long> campusScope = viewableCampusIds(currentUser);
            YearSetupResponse setup = yearSetupService.getYearSetup(schoolYearId);
            Board board = loadBoard(setup);
            Map<Long, Long> nextGroups = nextAgeGroups(setup.ageGroups());
            List<PlacementBoardResponse.SourceClass> classes = setup.sourceClasses().stream()
                    .filter(c -> campusScope == null || campusScope.contains(c.campusId()))
                    .map(c -> {
                        Long next = nextGroups.get(c.ageGroupId());
                        List<PlacementBoardResponse.ChildPlacement> children = board.childrenOf(c.id()).stream()
                                .map(child -> new PlacementBoardResponse.ChildPlacement(
                                        child.getId(),
                                        null,
                                        child.getFullName(),
                                        null,
                                        child.getDateOfBirth(),
                                        board.placementOf(child.getId())))
                                .sorted(Comparator.comparing(PlacementBoardResponse.ChildPlacement::fullName))
                                .toList();
                        return new PlacementBoardResponse.SourceClass(
                                c.id(),
                                c.name(),
                                c.campusId(),
                                c.ageGroupId(),
                                next,
                                next == null && c.ageGroupId() != null,
                                next == null ? null : board.suggestTarget(c.campusId(), next),
                                children);
                    })
                    .toList();
            List<PlacementBoardResponse.TargetClass> targets = setup.classes().stream()
                    .filter(c -> campusScope == null || campusScope.contains(c.campusId()))
                    .map(c -> new PlacementBoardResponse.TargetClass(
                            c.id(), c.name(), c.campusId(), c.ageGroupId(), c.capacity(), board.taken(c.id())))
                    .toList();
            PlacementBoardResponse response = new PlacementBoardResponse(
                    toBrief(setup.year()),
                    setup.sourceYear() == null ? null : toBrief(setup.sourceYear()),
                    campusScope == null
                            && setup.year().status() == SchoolYearStatus.PLANNED
                            && setup.sourceYear() != null
                            && isNextYear(setup),
                    setup.ageGroups().stream()
                            .map(g -> new PlacementBoardResponse.AgeGroupBrief(g.id(), g.shortName()))
                            .toList(),
                    setup.campuses().stream()
                            .filter(c -> campusScope == null || campusScope.contains(c.id()))
                            .map(c -> new PlacementBoardResponse.CampusBrief(c.id(), c.name()))
                            .toList(),
                    classes,
                    targets);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public int savePlacements(CurrentUser currentUser, Long schoolYearId, SavePlacementsRequest request) {
        String logPrefix =
                LOG_PREFIX + "|savePlacements|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            YearSetupResponse setup = checkPlanned(schoolYearId);
            Board board = loadBoard(setup);
            board.purgeStale();
            Map<Long, Long> nextGroups = nextAgeGroups(setup.ageGroups());
            Map<Long, YearSetupResponse.SetupClass> targets = setup.classes().stream()
                    .collect(Collectors.toMap(YearSetupResponse.SetupClass::id, Function.identity()));
            for (SavePlacementsRequest.Item item : request.items()) {
                YearSetupResponse.SetupClass from = board.sourceClassOf(item.childId());
                if (from == null) {
                    throw GlobalException.of(ApiCode.CHILD_NOT_IN_ACTIVE_YEAR, String.valueOf(item.childId()));
                }
                if (from.ageGroupId() == null && item.action() != PlacementAction.LEAVE) {
                    throw GlobalException.of(ApiCode.CLASS_WITHOUT_AGE_GROUP, from.name());
                }
                Long next = nextGroups.get(from.ageGroupId());
                if (item.action() == PlacementAction.GRADUATE && next != null) {
                    throw GlobalException.of(ApiCode.GRADUATION_NOT_ALLOWED);
                }
                if (item.action() == PlacementAction.PROMOTE && next == null) {
                    throw GlobalException.of(ApiCode.OLDEST_GROUP_GRADUATE_OR_STAY);
                }
                if (item.action().keepsChildInClass()) {
                    YearSetupResponse.SetupClass to = item.toClassId() == null ? null : targets.get(item.toClassId());
                    if (to == null) {
                        throw GlobalException.of(ApiCode.TARGET_CLASS_REQUIRED);
                    }
                    Long wanted = item.action() == PlacementAction.PROMOTE ? next : from.ageGroupId();
                    if (!Objects.equals(to.ageGroupId(), wanted)) {
                        throw GlobalException.of(
                                ApiCode.PLACEMENT_WRONG_AGE_GROUP, to.name(), actionLabel(item.action()));
                    }
                }
                board.place(item.childId(), from.id(), item.action(), item.toClassId(), currentUser.userId());
            }
            classPlacementRepository.flush();
            // Không vượt sĩ số tối đa của lớp năm mới (đã ≤ min(nhóm tuổi, sức chứa phòng)); lỗi ⇒ rollback cả lần lưu
            String over = setup.classes().stream()
                    .filter(c -> c.capacity() != null && board.taken(c.id()) > c.capacity())
                    .map(c -> c.name() + " (" + board.taken(c.id()) + "/" + c.capacity() + ")")
                    .collect(Collectors.joining(", "));
            if (!over.isEmpty()) {
                throw GlobalException.of(ApiCode.PLACEMENT_CLASS_FULL, over);
            }
            log.info("{}|saved={}|END", logPrefix, request.items().size());
            return request.items().size();
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AutoPlaceResponse autoPlace(CurrentUser currentUser, Long schoolYearId, AutoPlaceRequest request) {
        String logPrefix = LOG_PREFIX + "|autoPlace|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            YearSetupResponse setup = checkPlanned(schoolYearId);
            Board board = loadBoard(setup);
            board.purgeStale();
            Map<Long, Long> nextGroups = nextAgeGroups(setup.ageGroups());
            int promoted = 0;
            int graduated = 0;
            List<AutoPlaceResponse.Unplaced> unplaced = new ArrayList<>();
            // Cả lớp lên nhóm tuổi kế tiếp, vào lớp còn nhiều chỗ nhất; nhóm lớn nhất ra trường. Trẻ đã xếp giữ nguyên
            for (YearSetupResponse.SetupClass from : setup.sourceClasses()) {
                if ((request.campusId() != null && !request.campusId().equals(from.campusId()))
                        || (request.ageGroupId() != null
                                && !request.ageGroupId().equals(from.ageGroupId()))) {
                    continue;
                }
                Long next = nextGroups.get(from.ageGroupId());
                int left = 0;
                // Lớp cũ chưa gán nhóm tuổi: không biết lên nhóm nào, để Hiệu trưởng xếp tay
                boolean unknownGroup = from.ageGroupId() == null;
                for (Child child : board.childrenOf(from.id())) {
                    if (board.placementOf(child.getId()) != null) {
                        continue;
                    }
                    if (unknownGroup) {
                        left++;
                        continue;
                    }
                    if (next == null) {
                        board.place(child.getId(), from.id(), PlacementAction.GRADUATE, null, currentUser.userId());
                        graduated++;
                        continue;
                    }
                    Long target = board.suggestTarget(from.campusId(), next);
                    if (target == null) {
                        left++;
                        continue;
                    }
                    board.place(child.getId(), from.id(), PlacementAction.PROMOTE, target, currentUser.userId());
                    promoted++;
                }
                if (left > 0) {
                    unplaced.add(new AutoPlaceResponse.Unplaced(from.name(), left));
                }
            }
            classPlacementRepository.flush();
            AutoPlaceResponse response = new AutoPlaceResponse(promoted, graduated, unplaced);
            log.info("{}|promoted={}|graduated={}|END", logPrefix, promoted, graduated);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public YearReadinessResponse getReadiness(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|getReadiness|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            YearSetupResponse setup = yearSetupService.getYearSetup(schoolYearId);
            YearReadinessResponse response = buildReadiness(setup, loadBoard(setup));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public SchoolYearDetailResponse activateSchoolYear(CurrentUser currentUser, Long schoolYearId, String confirmName) {
        String logPrefix =
                LOG_PREFIX + "|activateSchoolYear|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(currentUser);
            YearSetupResponse setup = yearSetupService.getYearSetup(schoolYearId);
            Board board = loadBoard(setup);
            if (setup.year().status() == SchoolYearStatus.PLANNED) {
                YearReadinessResponse readiness = buildReadiness(setup, board);
                List<String> stepBlockers = readiness.steps().stream()
                        .filter(s -> s.required() && !s.ok())
                        .map(s -> s.label() + ": " + s.detail())
                        .toList();
                if (!stepBlockers.isEmpty()) {
                    throw GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_READY, String.join("; ", stepBlockers));
                }
            }
            // Đóng năm cũ + mở năm mới (school-structure) rồi chuyển trẻ, cùng một transaction
            board.purgeStale();
            SchoolYearDetailResponse activated =
                    schoolYearService.activateSchoolYear(currentUser, schoolYearId, confirmName);
            int moved = applyPlacements(setup, board);
            log.info("{}|moved={}|END", logPrefix, moved);
            return activated;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Trẻ rời lớp cũ hết ngày trước ngày bắt đầu mới; lên lớp/ở lại thì vào lớp mới từ ngày đó
    private int applyPlacements(YearSetupResponse setup, Board board) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start =
                today.isAfter(setup.year().startDate()) ? today : setup.year().startDate();
        int changed = 0;
        for (ClassPlacement placement : board.placements.values()) {
            for (Enrollment enrollment : enrollmentRepository.findOpenByChildId(placement.getChildId(), start)) {
                if (enrollment.getValidFrom().isBefore(start)) {
                    enrollment.endOn(start.minusDays(1));
                } else {
                    enrollmentRepository.delete(enrollment);
                }
            }
            // V10 uk_enrollments_child_open: mỗi trẻ một enrollment mở, ghi việc đóng lớp cũ trước khi mở lớp mới
            enrollmentRepository.flush();
            if (placement.getAction().keepsChildInClass()) {
                enrollmentRepository.save(
                        Enrollment.start(placement.getChildId(), placement.getToClassroomId(), start));
            }
            changed++;
        }
        enrollmentRepository.flush();
        return changed;
    }

    private YearReadinessResponse buildReadiness(YearSetupResponse setup, Board board) {
        List<YearSetupResponse.SetupClass> classes = setup.classes();
        boolean hasClasses = !classes.isEmpty();
        List<YearReadinessResponse.Step> steps = new ArrayList<>();
        steps.add(step(
                "info",
                true,
                true,
                DISPLAY_DATE.format(setup.year().startDate()) + " – "
                        + DISPLAY_DATE.format(setup.year().endDate())));
        Set<Long> groupsWithClasses = classes.stream()
                .map(YearSetupResponse.SetupClass::ageGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        boolean ageGroupsOk = !setup.ageGroups().isEmpty()
                && setup.ageGroups().stream()
                        .allMatch(g -> !g.mealSessions().isEmpty() && g.maxClassSize() != null && g.maxClassSize() > 0);
        steps.add(step(
                "ageGroups",
                true,
                ageGroupsOk,
                setup.ageGroups().size() + " nhóm tuổi · " + groupsWithClasses.size() + " nhóm có lớp"));
        steps.add(step("classes", true, hasClasses, hasClasses ? classes.size() + " lớp" : "Chưa có lớp nào"));
        long noRoom = classes.stream().filter(c -> c.roomId() == null).count();
        long overRoom = classes.stream()
                .filter(c -> c.roomCapacity() != null && c.capacity() != null && c.capacity() > c.roomCapacity())
                .count();
        steps.add(step(
                "rooms",
                true,
                hasClasses && noRoom == 0 && overRoom == 0,
                noRoom > 0
                        ? noRoom + " lớp chưa có phòng"
                        : overRoom > 0 ? overRoom + " lớp vượt sức chứa phòng" : classes.size() + " lớp có phòng"));
        boolean vpOk = setup.campusesWithoutVicePrincipal().isEmpty();
        String vpDetail = vpOk
                ? "Đủ các điểm trường"
                : "Chưa phân công " + setup.campusesWithoutVicePrincipal().size() + " điểm trường";
        // Dev: bỏ qua bước này (màn Phân công PHT chưa có BE), hiện vàng
        boolean vpSkipped = !vpOk && devActivation.skipVicePrincipalStep();
        steps.add(new YearReadinessResponse.Step(
                "vicePrincipals",
                STEP_LABELS.get("vicePrincipals"),
                true,
                vpOk || vpSkipped,
                vpSkipped,
                vpSkipped ? vpDetail + " (bỏ qua ở dev)" : vpDetail));
        long leaderMissing = groupsWithClasses.stream()
                .filter(g -> !setup.ageGroupsWithTeamLeader().contains(g))
                .count();
        steps.add(step(
                "teamLeaders",
                false,
                !groupsWithClasses.isEmpty() && leaderMissing == 0,
                groupsWithClasses.isEmpty()
                        ? "Chưa có lớp"
                        : (groupsWithClasses.size() - leaderMissing) + "/" + groupsWithClasses.size()
                                + " nhóm tuổi có tổ trưởng (toàn trường)"));
        // Lớp chưa có giáo viên hoặc chưa có chủ nhiệm chặn kích hoạt; lớp thiếu 1 GV chỉ cảnh báo (Cần chốt
        // 22/10/2026)
        long noTeacher = classes.stream().filter(c -> c.teacherCount() == 0).count();
        long shortOne = classes.stream()
                .filter(c -> c.teacherCount() > 0 && c.teacherCount() < TEACHERS_PER_CLASS)
                .count();
        long noHomeroom = classes.stream()
                .filter(c -> c.teacherCount() > 0 && !c.hasHomeroom())
                .count();
        boolean teachersOk = hasClasses && noTeacher == 0 && noHomeroom == 0;
        String teachersDetail;
        if (!hasClasses) {
            teachersDetail = "Chưa có lớp";
        } else if (!teachersOk) {
            teachersDetail = Stream.of(
                            noTeacher > 0 ? noTeacher + " lớp chưa có giáo viên" : null,
                            noHomeroom > 0 ? noHomeroom + " lớp chưa có chủ nhiệm" : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" · "));
        } else {
            teachersDetail =
                    "Tất cả lớp có GV và chủ nhiệm" + (shortOne > 0 ? " · " + shortOne + " lớp thiếu 1 GV" : "");
        }
        steps.add(new YearReadinessResponse.Step(
                "teachers", STEP_LABELS.get("teachers"), true, teachersOk, teachersOk && shortOne > 0, teachersDetail));
        long toPlace = board.childClass.size();
        long placed = board.childClass.keySet().stream()
                .filter(board.placements::containsKey)
                .count();
        // Kiểm lại lúc kích hoạt: lớp có thể đã bị hạ sĩ số / đổi nhóm tuổi sau khi xếp
        List<String> misfits = placementMisfits(setup, board);
        String placementDetail = setup.sourceYear() == null
                ? "Không có năm học đang hoạt động cần chuyển trẻ"
                : placed + "/" + toPlace + " trẻ của năm " + setup.sourceYear().name() + " đã xếp lớp";
        steps.add(step(
                "placement",
                true,
                placed == toPlace && misfits.isEmpty(),
                misfits.isEmpty() ? placementDetail : placementDetail + " · " + String.join(" · ", misfits)));
        steps.add(step("cutoff", true, setup.cutoffSet(), setup.cutoffSet() ? "Đã cài đặt" : "Chưa cài đặt"));
        LocalDate openFrom = setup.year().startDate().minusDays(ACTIVATION_LEAD_DAYS);
        List<String> blockers = new ArrayList<>(steps.stream()
                .filter(s -> s.required() && !s.ok())
                .map(s -> s.label() + ": " + s.detail())
                .toList());
        if (!devActivation.skipDateWindow() && LocalDate.now(clock).isBefore(openFrom)) {
            blockers.add("Chỉ kích hoạt được từ " + DISPLAY_DATE.format(openFrom) + " (" + ACTIVATION_LEAD_DAYS
                    + " ngày trước ngày bắt đầu)");
        }
        long move = board.placements.values().stream()
                .filter(p -> p.getAction().keepsChildInClass())
                .count();
        return new YearReadinessResponse(
                setup.year().id(),
                steps,
                openFrom,
                setup.sourceYear() == null ? null : toBrief(setup.sourceYear()),
                new YearReadinessResponse.Impact(move, board.placements.size() - move),
                blockers,
                setup.year().status() == SchoolYearStatus.PLANNED && blockers.isEmpty());
    }

    // Lớp năm mới vượt sĩ số tối đa hoặc không còn đúng nhóm tuổi so với bản xếp đã lưu
    private static List<String> placementMisfits(YearSetupResponse setup, Board board) {
        Map<Long, Long> nextGroups = nextAgeGroups(setup.ageGroups());
        List<String> over = setup.classes().stream()
                .filter(c -> c.capacity() != null && board.taken(c.id()) > c.capacity())
                .map(c -> c.name() + " (" + board.taken(c.id()) + "/" + c.capacity() + ")")
                .toList();
        Map<Long, YearSetupResponse.SetupClass> targets = setup.classes().stream()
                .collect(Collectors.toMap(YearSetupResponse.SetupClass::id, Function.identity()));
        long wrongGroup = board.placements.values().stream()
                .filter(p -> p.getAction().keepsChildInClass())
                .filter(p -> {
                    YearSetupResponse.SetupClass from = board.sourceClassOf(p.getChildId());
                    YearSetupResponse.SetupClass to = targets.get(p.getToClassroomId());
                    if (from == null || to == null) {
                        return true;
                    }
                    Long wanted = p.getAction() == PlacementAction.PROMOTE
                            ? nextGroups.get(from.ageGroupId())
                            : from.ageGroupId();
                    return !Objects.equals(to.ageGroupId(), wanted);
                })
                .count();
        List<String> misfits = new ArrayList<>();
        if (!over.isEmpty()) {
            misfits.add("Vượt sĩ số: " + String.join(", ", over));
        }
        if (wrongGroup > 0) {
            misfits.add(wrongGroup + " trẻ xếp vào lớp không đúng nhóm tuổi");
        }
        return misfits;
    }

    private Board loadBoard(YearSetupResponse setup) {
        Board board = new Board(setup);
        if (!setup.sourceClasses().isEmpty()) {
            List<Enrollment> enrollments = enrollmentRepository.findActiveByClassroomIds(
                    setup.sourceClasses().stream()
                            .map(YearSetupResponse.SetupClass::id)
                            .toList(),
                    LocalDate.now(clock));
            enrollments.forEach(e -> board.childClass.putIfAbsent(e.getChildId(), e.getClassroomId()));
            childRepository.findAllById(board.childClass.keySet()).forEach(c -> board.children.put(c.getId(), c));
        }
        // Bản xếp chỉ hợp lệ khi trẻ vẫn ở đúng lớp nguồn của năm đang hoạt động; bản cũ (năm nguồn khác, trẻ đã đổi
        // lớp/rời trường) không tính, không áp khi kích hoạt và bị xóa ở lần ghi kế tiếp
        classPlacementRepository.findAllBySchoolYearId(setup.year().id()).forEach(p -> {
            if (p.getFromClassroomId() != null && p.getFromClassroomId().equals(board.childClass.get(p.getChildId()))) {
                board.placements.put(p.getChildId(), p);
            } else {
                board.stale.add(p);
            }
        });
        return board;
    }

    private static String actionLabel(PlacementAction action) {
        return switch (action) {
            case PROMOTE -> "Lên lớp";
            case STAY -> "Ở lại";
            case LEAVE -> "Nghỉ học";
            case GRADUATE -> "Ra trường";
        };
    }

    // Nhóm tuổi kế tiếp theo độ tuổi; nhóm lớn nhất không có nhóm kế tiếp (ra trường)
    private static Map<Long, Long> nextAgeGroups(List<AgeGroupDetailResponse> ageGroups) {
        Map<Long, Long> next = new HashMap<>();
        for (int i = 0; i + 1 < ageGroups.size(); i++) {
            next.put(ageGroups.get(i).id(), ageGroups.get(i + 1).id());
        }
        return next;
    }

    private YearSetupResponse checkPlanned(Long schoolYearId) {
        YearSetupResponse setup = yearSetupService.getYearSetup(schoolYearId);
        if (setup.year().status() != SchoolYearStatus.PLANNED) {
            throw GlobalException.of(ApiCode.PLACEMENT_YEAR_NOT_UPCOMING);
        }
        if (setup.sourceYear() == null) {
            throw GlobalException.of(ApiCode.NO_ACTIVE_YEAR_TO_MOVE_FROM);
        }
        if (!isNextYear(setup)) {
            SchoolYearDetailResponse next = schoolYearService.findNextPlannedYear();
            throw GlobalException.of(ApiCode.PLACEMENT_NOT_NEXT_YEAR, next == null ? "" : next.name());
        }
        return setup;
    }

    // CFG-YR-03: chỉ xếp lớp cho năm sắp diễn ra sớm nhất (năm kế tiếp năm đang hoạt động)
    private boolean isNextYear(YearSetupResponse setup) {
        SchoolYearDetailResponse next = schoolYearService.findNextPlannedYear();
        return next != null && next.id().equals(setup.year().id());
    }

    // AUTH-01: xếp lớp và kích hoạt năm do Hiệu trưởng có phân công còn hiệu lực
    private void checkPrincipal(CurrentUser user) {
        if (!user.hasRole(CareNestRole.PRINCIPAL)
                || !staffAssignmentService.hasActivePrincipalAssignment(user.userId(), LocalDate.now(clock))) {
            throw GlobalException.of(ApiCode.PRINCIPAL_ONLY, "quản lý chuyển năm học");
        }
    }

    // TODO(Cần): PHT xếp lớp theo quyền "Xếp lớp năm học" — hiện PHT chỉ xem điểm trường mình
    private Set<Long> viewableCampusIds(CurrentUser user) {
        LocalDate today = LocalDate.now(clock);
        if (user.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(user.userId(), today)) {
            return null;
        }
        Set<Long> campusIds = user.hasRole(CareNestRole.VICE_PRINCIPAL)
                ? staffAssignmentService.findActiveAssignments(user.userId(), today).stream()
                        .filter(a -> a.staffRole() == StaffRole.VICE_PRINCIPAL && a.campusId() != null)
                        .map(StaffAssignmentSummaryResponse::campusId)
                        .collect(Collectors.toSet())
                : Set.of();
        if (campusIds.isEmpty()) {
            throw GlobalException.of(ApiCode.PLACEMENT_ACCESS_DENIED);
        }
        return campusIds;
    }

    // Nhãn bước theo FE READINESS_STEPS (schoolConfigConstants.js)
    private static final Map<String, String> STEP_LABELS = Map.of(
            "info", "Thông tin năm học",
            "ageGroups", "Nhóm tuổi & bữa ăn",
            "classes", "Lớp học",
            "rooms", "Phòng & sức chứa",
            "vicePrincipals", "Phó hiệu trưởng từng điểm trường",
            "teamLeaders", "Tổ trưởng nhóm tuổi",
            "teachers", "Giáo viên lớp",
            "placement", "Xếp lớp trẻ năm mới",
            "cutoff", "Giờ chốt điểm danh");

    private static YearReadinessResponse.Step step(String key, boolean required, boolean ok, String detail) {
        return new YearReadinessResponse.Step(key, STEP_LABELS.get(key), required, ok, false, detail);
    }

    private static PlacementBoardResponse.YearBrief toBrief(YearSetupResponse.Year year) {
        return new PlacementBoardResponse.YearBrief(year.id(), year.name());
    }

    /** Trạng thái xếp lớp trong một lần xử lý: trẻ của năm nguồn, kết quả đã xếp, chỗ đã dùng ở lớp năm mới. */
    private final class Board {
        private final YearSetupResponse setup;
        private final Map<Long, Long> childClass = new HashMap<>();
        private final Map<Long, Child> children = new HashMap<>();
        private final Map<Long, ClassPlacement> placements = new HashMap<>();
        private final List<ClassPlacement> stale = new ArrayList<>();

        private Board(YearSetupResponse setup) {
            this.setup = setup;
        }

        List<Child> childrenOf(Long classId) {
            return childClass.entrySet().stream()
                    .filter(e -> e.getValue().equals(classId))
                    .map(e -> children.get(e.getKey()))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Child::getFullName))
                    .toList();
        }

        YearSetupResponse.SetupClass sourceClassOf(Long childId) {
            Long classId = childClass.get(childId);
            return classId == null
                    ? null
                    : setup.sourceClasses().stream()
                            .filter(c -> c.id().equals(classId))
                            .findFirst()
                            .orElse(null);
        }

        PlacementBoardResponse.Placement placementOf(Long childId) {
            ClassPlacement p = placements.get(childId);
            return p == null
                    ? null
                    : new PlacementBoardResponse.Placement(
                            p.getSchoolYearId(),
                            p.getChildId(),
                            p.getFromClassroomId(),
                            p.getAction(),
                            p.getToClassroomId(),
                            p.getUpdatedBy(),
                            p.getUpdatedAt());
        }

        long taken(Long targetClassId) {
            return placements.values().stream()
                    .filter(p -> p.getAction().keepsChildInClass() && targetClassId.equals(p.getToClassroomId()))
                    .count();
        }

        // Lớp năm mới cùng điểm trường, đúng nhóm tuổi, còn nhiều chỗ nhất (hết chỗ ⇒ null)
        Long suggestTarget(Long campusId, Long ageGroupId) {
            return setup.classes().stream()
                    .filter(c -> c.campusId().equals(campusId) && Objects.equals(c.ageGroupId(), ageGroupId))
                    .filter(c -> c.capacity() == null || taken(c.id()) < c.capacity())
                    .max(Comparator.comparingLong((YearSetupResponse.SetupClass c) ->
                                    (c.capacity() == null ? Integer.MAX_VALUE : c.capacity()) - taken(c.id()))
                            .thenComparing(YearSetupResponse.SetupClass::name, Comparator.reverseOrder()))
                    .map(YearSetupResponse.SetupClass::id)
                    .orElse(null);
        }

        // Xóa bản xếp cũ trước khi ghi (unique theo năm + trẻ)
        void purgeStale() {
            if (!stale.isEmpty()) {
                classPlacementRepository.deleteAll(stale);
                classPlacementRepository.flush();
                stale.clear();
            }
        }

        void place(Long childId, Long fromClassId, PlacementAction action, Long toClassId, Long userId) {
            ClassPlacement placement = placements.computeIfAbsent(
                    childId, id -> ClassPlacement.create(setup.year().id(), id));
            placement.decide(fromClassId, action, toClassId, userId);
            classPlacementRepository.save(placement);
        }
    }
}
