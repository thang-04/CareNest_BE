package com.carenest.service.child.impl;

import com.carenest.config.DevActivationProperties;
import com.carenest.dto.child.AutoPlaceRequest;
import com.carenest.dto.child.AutoPlaceResponse;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.child.PlacementBoardResponse;
import com.carenest.dto.child.PrepClassesResponse;
import com.carenest.dto.child.SavePlacementsRequest;
import com.carenest.dto.child.YearReadinessResponse;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.dto.organization.YearSetupResponse;
import com.carenest.entity.child.Child;
import com.carenest.entity.child.ClassPlacement;
import com.carenest.entity.child.Enrollment;
import com.carenest.entity.child.PlannedAdmission;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.ChildRepository;
import com.carenest.repository.child.ClassPlacementRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.repository.child.PlannedAdmissionRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.child.ProgramCompletionLookup;
import com.carenest.service.child.YearTransitionService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.service.organization.YearSetupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PlacementAction;
import com.carenest.utils.enums.PlannedAdmissionStatus;
import com.carenest.utils.enums.SchoolYearStatus;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final PlannedAdmissionRepository plannedAdmissionRepository;
    private final ChildRepository childRepository;
    private final YearSetupService yearSetupService;
    private final SchoolYearService schoolYearService;
    private final ClassroomService classroomService;
    private final StaffAssignmentService staffAssignmentService;
    private final CampusAccessPolicy campusAccessPolicy;
    private final Clock clock;
    private final DevActivationProperties devActivation;
    private final ApplicationEventPublisher eventPublisher;
    private final List<ProgramCompletionLookup> programCompletionLookups;
    private final GuardianChildLinkService guardianChildLinkService;
    private final NotificationService notificationService;
    private final PlacementRules placementRules;

    @Override
    @Transactional(readOnly = true)
    public PrepClassesResponse getPrepClasses(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|getPrepClasses|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            // GV không thấy năm chưa tới ngày mở chuẩn bị ⇒ 404 từ service năm học
            SchoolYearDetailResponse year = schoolYearService.getSchoolYear(currentUser, schoolYearId);
            if (year.status() != SchoolYearStatus.PLANNED || !year.preparing()) {
                throw GlobalException.of(ApiCode.PREP_YEAR_NOT_OPEN, year.name(), DISPLAY_DATE.format(year.prepDate()));
            }
            LocalDate today = LocalDate.now(clock);
            YearSetupResponse setup = yearSetupService.getYearSetup(schoolYearId);
            // AUTH-20: HT, PHT còn phân công xem mọi lớp (chỉ xem); PHT hết phân công chỉ như GV
            boolean leader = campusAccessPolicy.canReadAllCampuses(currentUser);
            Set<Long> taught = staffAssignmentService.findCurrentTeacherClassroomIds(currentUser.userId(), today);
            Set<Long> led =
                    staffAssignmentService.findYearTeamLeaderAgeGroupIds(currentUser.userId(), schoolYearId, today);
            List<YearSetupResponse.SetupClass> classes = setup.classes().stream()
                    .filter(c -> leader || taught.contains(c.id()) || led.contains(c.ageGroupId()))
                    .toList();
            Set<Long> classIds =
                    classes.stream().map(YearSetupResponse.SetupClass::id).collect(Collectors.toSet());
            Map<Long, List<Long>> childIdsByClass =
                    classPlacementRepository.findAllBySchoolYearId(schoolYearId).stream()
                            .filter(p -> p.getAction().keepsChildInClass() && classIds.contains(p.getToClassroomId()))
                            .collect(Collectors.groupingBy(
                                    ClassPlacement::getToClassroomId,
                                    Collectors.mapping(ClassPlacement::getChildId, Collectors.toList())));
            plannedAdmissionRepository
                    .findBySchoolYearIdAndStatusInOrderById(
                            schoolYearId, List.of(PlannedAdmissionStatus.PLACED, PlannedAdmissionStatus.ACTIVATED))
                    .stream()
                    .filter(a -> a.getClassroomId() != null && classIds.contains(a.getClassroomId()))
                    .forEach(a -> childIdsByClass
                            .computeIfAbsent(a.getClassroomId(), ignored -> new ArrayList<>())
                            .add(a.getChildId()));
            Map<Long, Child> children =
                    childRepository
                            .findAllById(childIdsByClass.values().stream()
                                    .flatMap(List::stream)
                                    .toList())
                            .stream()
                            .collect(Collectors.toMap(Child::getId, Function.identity()));
            List<PrepClassesResponse.PrepClass> rows = classes.stream()
                    .map(c -> new PrepClassesResponse.PrepClass(
                            c.id(),
                            c.name(),
                            c.campusId(),
                            c.ageGroupId(),
                            taught.contains(c.id()),
                            childIdsByClass.getOrDefault(c.id(), List.of()).stream()
                                    .map(children::get)
                                    .filter(Objects::nonNull)
                                    .sorted(Comparator.comparing(Child::getFullName))
                                    .map(ch -> new PrepClassesResponse.PrepChild(
                                            ch.getId(), ch.getFullName(), ch.getDateOfBirth(), ch.getGender()))
                                    .toList()))
                    .toList();
            log.info("{}|classes={}|END", logPrefix, rows.size());
            return new PrepClassesResponse(year.id(), year.name(), year.prepDate(), year.startDate(), rows);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementBoardResponse getPlacements(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|getPlacements|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            boolean principal = isPrincipal(currentUser);
            checkCanPlace(currentUser, schoolYearId);
            YearSetupResponse setup = yearSetupService.getYearSetup(schoolYearId);
            Board board = loadBoard(setup);
            Map<Long, Boolean> completion = graduatingCompletion(setup, board);
            Set<Long> yearEndConfirmed = yearEndConfirmed(setup, board);
            LocalDate openFrom = placementOpenFrom(currentUser, setup);
            List<PlacementBoardResponse.SourceClass> classes = setup.sourceClasses().stream()
                    .filter(c -> placesCampus(currentUser, c.campusId()))
                    .map(c -> {
                        List<Child> sourceChildren = board.childrenOf(c.id());
                        List<Long> targetGroups = sourceChildren.stream()
                                .map(child -> ageGroupOf(setup, child))
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList();
                        Long next = targetGroups.size() == 1 ? targetGroups.getFirst() : null;
                        boolean graduatingClass = !sourceChildren.isEmpty()
                                && sourceChildren.stream().allMatch(child -> oldEnoughToGraduate(setup, child));
                        List<PlacementBoardResponse.ChildPlacement> children = sourceChildren.stream()
                                .map(child -> new PlacementBoardResponse.ChildPlacement(
                                        child.getId(),
                                        null,
                                        child.getFullName(),
                                        null,
                                        child.getDateOfBirth(),
                                        board.placementOf(child.getId()),
                                        completion.get(child.getId()),
                                        yearEndConfirmed.contains(child.getId())))
                                .sorted(Comparator.comparing(PlacementBoardResponse.ChildPlacement::fullName))
                                .toList();
                        return new PlacementBoardResponse.SourceClass(
                                c.id(),
                                c.name(),
                                c.campusId(),
                                c.ageGroupId(),
                                next,
                                graduatingClass,
                                next == null ? null : board.suggestTarget(c.campusId(), next),
                                children);
                    })
                    .toList();
            List<PlacementBoardResponse.TargetClass> targets = setup.classes().stream()
                    .filter(c -> placesCampus(currentUser, c.campusId()))
                    .map(c -> new PlacementBoardResponse.TargetClass(
                            c.id(), c.name(), c.campusId(), c.ageGroupId(), c.capacity(), board.taken(c.id())))
                    .toList();
            List<PlacementBoardResponse.NewAdmission> newAdmissions = board.admissions.values().stream()
                    .filter(a -> placesCampus(currentUser, a.getCampusId()))
                    .map(a -> {
                        Child child = board.children.get(a.getChildId());
                        return child == null
                                ? null
                                : new PlacementBoardResponse.NewAdmission(
                                        a.getId(),
                                        child.getId(),
                                        child.getFullName(),
                                        child.getDateOfBirth(),
                                        a.getCampusId(),
                                        a.getClassroomId(),
                                        a.getStartDate(),
                                        ageGroupOf(setup, child));
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(PlacementBoardResponse.NewAdmission::fullName))
                    .toList();
            PlacementBoardResponse response = new PlacementBoardResponse(
                    toBrief(setup.year()),
                    setup.sourceYear() == null ? null : toBrief(setup.sourceYear()),
                    // PHT có quyền nhưng hết phân công (không còn điểm trường nào) thì chỉ xem
                    (principal
                                    || (currentUser.hasPermission(Permission.YEAR_PLACEMENT_MANAGE)
                                            && setup.campuses().stream()
                                                    .anyMatch(c -> placesCampus(currentUser, c.id()))))
                            && setup.year().status() == SchoolYearStatus.PLANNED
                            && setup.sourceYear() != null
                            && isNextYear(setup)
                            && !placementNotOpen(openFrom),
                    setup.ageGroups().stream()
                            .map(g -> new PlacementBoardResponse.AgeGroupBrief(g.id(), g.shortName()))
                            .toList(),
                    setup.campuses().stream()
                            .filter(c -> placesCampus(currentUser, c.id()))
                            .map(c -> new PlacementBoardResponse.CampusBrief(c.id(), c.name()))
                            .toList(),
                    classes,
                    targets,
                    newAdmissions,
                    openFrom);
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
            checkCanPlace(currentUser, schoolYearId);
            // Hai người lưu xếp lớp cùng lúc phải chạy lần lượt, người sau đọc bảng đã có thay đổi của người trước
            schoolYearService.lockForUpdate(schoolYearId);
            YearSetupResponse setup = checkPlanned(schoolYearId);
            checkPlacementOpen(currentUser, setup);
            Board board = loadBoard(setup);
            board.purgeStale();
            Map<Long, YearSetupResponse.SetupClass> targets = setup.classes().stream()
                    .collect(Collectors.toMap(YearSetupResponse.SetupClass::id, Function.identity()));
            for (SavePlacementsRequest.Item item : request.items()) {
                YearSetupResponse.SetupClass from = board.sourceClassOf(item.childId());
                if (from == null) {
                    throw GlobalException.of(ApiCode.CHILD_NOT_IN_ACTIVE_YEAR, String.valueOf(item.childId()));
                }
                // Phân quyền động: trẻ và lớp năm mới đều phải trong phạm vi HT cấp
                if (!placesCampus(currentUser, from.campusId())) {
                    throw GlobalException.of(ApiCode.PLACEMENT_ACCESS_DENIED);
                }
                if (from.ageGroupId() == null && item.action() != PlacementAction.LEAVE) {
                    throw GlobalException.of(ApiCode.CLASS_WITHOUT_AGE_GROUP, from.name());
                }
                Child child = board.children.get(item.childId());
                // Trẻ nghỉ học hẳn trước khi năm mới bắt đầu không học năm mới: chỉ được chọn Nghỉ học
                if (child != null && leavesBeforeYear(setup, child) && item.action() != PlacementAction.LEAVE) {
                    throw GlobalException.of(
                            ApiCode.PLACEMENT_CHILD_LEAVING,
                            child.getFullName(),
                            DISPLAY_DATE.format(child.getScheduledLeaveOn()));
                }
                if (item.action() == PlacementAction.GRADUATE
                        && (child == null || !oldEnoughToGraduate(setup, child))) {
                    throw GlobalException.of(ApiCode.GRADUATION_NOT_ALLOWED);
                }
                if (item.action() == PlacementAction.PROMOTE && (child == null || ageMonths(setup, child) >= 72)) {
                    throw GlobalException.of(ApiCode.OLDEST_GROUP_GRADUATE_OR_STAY);
                }
                if (item.action().keepsChildInClass()) {
                    YearSetupResponse.SetupClass to = item.toClassId() == null ? null : targets.get(item.toClassId());
                    if (to == null) {
                        throw GlobalException.of(ApiCode.TARGET_CLASS_REQUIRED);
                    }
                    if (!placesCampus(currentUser, to.campusId())) {
                        throw GlobalException.of(ApiCode.PLACEMENT_ACCESS_DENIED);
                    }
                    // Xếp tay năm mới được chọn khác nhóm tuổi; lớp vẫn phải có nhóm tuổi đã cấu hình.
                    if (to.ageGroupId() == null) {
                        throw GlobalException.of(ApiCode.CLASS_WITHOUT_AGE_GROUP, to.name());
                    }
                }
                board.place(item.childId(), from.id(), item.action(), item.toClassId(), currentUser.userId());
            }
            for (SavePlacementsRequest.NewAdmissionItem item : request.newAdmissions()) {
                PlannedAdmission admission = board.admissions.get(item.admissionId());
                if (admission == null || !placesCampus(currentUser, admission.getCampusId())) {
                    throw GlobalException.of(ApiCode.PLACEMENT_ACCESS_DENIED);
                }
                if (item.toClassId() != null) {
                    YearSetupResponse.SetupClass to = targets.get(item.toClassId());
                    if (to == null) {
                        throw GlobalException.of(ApiCode.TARGET_CLASS_REQUIRED);
                    }
                    if (!placesCampus(currentUser, to.campusId())) {
                        throw GlobalException.of(ApiCode.PLACEMENT_ACCESS_DENIED);
                    }
                    Child child = board.children.get(admission.getChildId());
                    String childName = child == null ? "Trẻ" : child.getFullName();
                    if (!Objects.equals(to.campusId(), admission.getCampusId())) {
                        throw GlobalException.of(ApiCode.PLACEMENT_ADMISSION_WRONG_CAMPUS, to.name(), childName);
                    }
                    if (to.ageGroupId() == null) {
                        throw GlobalException.of(ApiCode.CLASS_WITHOUT_AGE_GROUP, to.name());
                    }
                }
                admission.assignClass(item.toClassId());
                plannedAdmissionRepository.save(admission);
            }
            classPlacementRepository.flush();
            plannedAdmissionRepository.flush();
            // Không vượt sĩ số tối đa của lớp năm mới (đã ≤ min(nhóm tuổi, sức chứa phòng)); lỗi ⇒ rollback cả lần lưu
            String over = setup.classes().stream()
                    .filter(c -> c.capacity() != null && board.taken(c.id()) > c.capacity())
                    .map(c -> c.name() + " (" + board.taken(c.id()) + "/" + c.capacity() + ")")
                    .collect(Collectors.joining(", "));
            if (!over.isEmpty()) {
                throw GlobalException.of(ApiCode.PLACEMENT_CLASS_FULL, over);
            }
            int saved = request.items().size() + request.newAdmissions().size();
            log.info("{}|saved={}|END", logPrefix, saved);
            return saved;
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
            checkCanPlace(currentUser, schoolYearId);
            schoolYearService.lockForUpdate(schoolYearId);
            YearSetupResponse setup = checkPlanned(schoolYearId);
            checkPlacementOpen(currentUser, setup);
            Board board = loadBoard(setup);
            board.purgeStale();
            int promoted = 0;
            int graduated = 0;
            int undecided = 0;
            List<AutoPlaceResponse.Unplaced> unplaced = new ArrayList<>();
            Map<Long, Boolean> completion = graduatingCompletion(setup, board);
            // Tính nhóm tuổi từng trẻ tại đầu năm mới; không suy từ thứ tự nhóm đang được trường áp dụng.
            for (YearSetupResponse.SetupClass from : setup.sourceClasses()) {
                if (!placesCampus(currentUser, from.campusId())
                        || (request.campusId() != null && !request.campusId().equals(from.campusId()))
                        || (request.ageGroupId() != null
                                && !request.ageGroupId().equals(from.ageGroupId()))) {
                    continue;
                }
                int left = 0;
                // Lớp cũ chưa gán nhóm tuổi: không biết lên nhóm nào, để Hiệu trưởng xếp tay
                boolean unknownGroup = from.ageGroupId() == null;
                for (Child child : board.childrenOf(from.id())) {
                    if (board.placementOf(child.getId()) != null) {
                        continue;
                    }
                    if (leavesBeforeYear(setup, child)) {
                        board.place(child.getId(), from.id(), PlacementAction.LEAVE, null, currentUser.userId());
                        continue;
                    }
                    if (unknownGroup) {
                        left++;
                        continue;
                    }
                    Long targetAgeGroup = ageGroupOf(setup, child);
                    // Trẻ đủ tuổi vào lớp 1 đang ở lớp nhóm lớn nhất không tự xếp học lại lớp mẫu giáo
                    boolean leavingPreschool = targetAgeGroup == null
                            || (oldEnoughToGraduate(setup, child) && isOldestGroup(setup, from.ageGroupId()));
                    if (leavingPreschool) {
                        // Chỉ trẻ đã hoàn thành chương trình tự ra trường; còn lại Hiệu trưởng chọn ra trường / ở lại
                        if (oldEnoughToGraduate(setup, child) && Boolean.TRUE.equals(completion.get(child.getId()))) {
                            board.place(child.getId(), from.id(), PlacementAction.GRADUATE, null, currentUser.userId());
                            graduated++;
                        } else {
                            undecided++;
                        }
                        continue;
                    }
                    Long target = board.suggestTarget(from.campusId(), targetAgeGroup);
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
            AutoPlaceResponse response = new AutoPlaceResponse(promoted, graduated, undecided, unplaced);
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
            YearReadinessResponse response =
                    buildReadiness(setup, loadBoard(setup), calendarCheckOf(currentUser, schoolYearId));
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
            // Không cho lưu xếp lớp chen vào giữa lúc kích hoạt: bảng xếp lớp đọc xong là phải khớp lúc áp dụng
            schoolYearService.lockForUpdate(schoolYearId);
            YearSetupResponse setup = yearSetupService.getYearSetup(schoolYearId);
            Board board = loadBoard(setup);
            if (setup.year().status() == SchoolYearStatus.PLANNED) {
                YearReadinessResponse readiness =
                        buildReadiness(setup, board, calendarCheckOf(currentUser, schoolYearId));
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
            int admitted = applyPlannedAdmissions(currentUser, setup, board);
            notifyGuardiansOfNewClass(setup, board);
            notifyGuardiansOfNewAdmissions(setup, board);
            List<Long> leftSchool = board.placements.values().stream()
                    .filter(placement -> !placement.getAction().keepsChildInClass())
                    .map(ClassPlacement::getChildId)
                    .toList();
            if (!leftSchool.isEmpty()) {
                eventPublisher.publishEvent(new ChildrenLeftSchoolEvent(leftSchool, transitionStart(setup)));
            }
            log.info("{}|moved={}|admitted={}|leftSchool={}|END", logPrefix, moved, admitted, leftSchool.size());
            return activated;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Trẻ rời lớp cũ hết ngày trước ngày bắt đầu mới; lên lớp/ở lại thì vào lớp mới từ ngày đó
    private int applyPlacements(YearSetupResponse setup, Board board) {
        LocalDate start = transitionStart(setup);
        // Một truy vấn cho mọi trẻ thay vì mỗi trẻ một truy vấn
        Map<Long, List<Enrollment>> open = board.placements.isEmpty()
                ? Map.of()
                : enrollmentRepository.findOpenByChildIds(board.placements.keySet(), start).stream()
                        .collect(Collectors.groupingBy(Enrollment::getChildId));
        for (ClassPlacement placement : board.placements.values()) {
            for (Enrollment enrollment : open.getOrDefault(placement.getChildId(), List.of())) {
                if (enrollment.getValidFrom().isBefore(start)) {
                    enrollment.endOn(start.minusDays(1));
                } else {
                    enrollmentRepository.delete(enrollment);
                }
            }
        }
        // V63 uk_enrollments_child_open: mỗi trẻ một enrollment mở, ghi việc đóng lớp cũ trước khi mở lớp mới
        enrollmentRepository.flush();
        int changed = 0;
        for (ClassPlacement placement : board.placements.values()) {
            if (placement.getAction().keepsChildInClass()) {
                enrollmentRepository.save(
                        Enrollment.start(placement.getChildId(), placement.getToClassroomId(), start));
            }
            changed++;
        }
        enrollmentRepository.flush();
        return changed;
    }

    private int applyPlannedAdmissions(CurrentUser currentUser, YearSetupResponse setup, Board board) {
        LocalDate transitionDate = transitionStart(setup);
        int changed = 0;
        // Lớp học và enrollment đang mở đọc một lần, không tra lại cho từng hồ sơ nhập học
        List<Long> placedChildIds = board.admissions.values().stream()
                .filter(a -> a.getStatus() == PlannedAdmissionStatus.PLACED && a.getClassroomId() != null)
                .map(PlannedAdmission::getChildId)
                .toList();
        Map<Long, List<Enrollment>> open = placedChildIds.isEmpty()
                ? Map.of()
                : enrollmentRepository.findOpenByChildIds(placedChildIds, transitionDate).stream()
                        .collect(Collectors.groupingBy(Enrollment::getChildId));
        Map<Long, ClassroomInfoResponse> classrooms = new HashMap<>();
        for (PlannedAdmission admission : board.admissions.values()) {
            Child child = board.children.get(admission.getChildId());
            if (admission.getStatus() != PlannedAdmissionStatus.PLACED || admission.getClassroomId() == null) {
                // Chưa có lớp: năm mới bắt đầu ⇒ trẻ "Chờ xếp lớp" ở điểm trường đã đăng ký (màn Xếp lớp cho trẻ)
                if (admission.getStatus() == PlannedAdmissionStatus.PENDING && child != null) {
                    child.keepCampus(admission.getCampusId());
                    admission.activate();
                }
                continue;
            }
            if (!classrooms.containsKey(admission.getClassroomId())) {
                classrooms.put(
                        admission.getClassroomId(),
                        classroomService
                                .findClassroomInfo(admission.getClassroomId())
                                .orElse(null));
            }
            var classroom = classrooms.get(admission.getClassroomId());
            if (child == null || classroom == null || !setup.year().id().equals(classroom.schoolYearId())) {
                continue;
            }
            LocalDate start =
                    admission.getStartDate().isAfter(transitionDate) ? admission.getStartDate() : transitionDate;
            boolean stillEnrolled = open.getOrDefault(child.getId(), List.of()).stream()
                    .anyMatch(e -> e.getValidTo() == null || !e.getValidTo().isBefore(start));
            if (stillEnrolled) {
                throw GlobalException.of(ApiCode.PLACEMENT_CHILD_ALREADY_ENROLLED, child.getFullName());
            }
            enrollmentRepository.save(Enrollment.start(child.getId(), classroom.id(), start, currentUser.userId()));
            child.placeInClass();
            admission.activate();
            placementRules.recordPlacement(
                    child.getId(),
                    child.getFullName(),
                    null,
                    classroom,
                    currentUser.userId(),
                    null,
                    clock.instant(),
                    start);
            changed++;
        }
        enrollmentRepository.flush();
        plannedAdmissionRepository.flush();
        return changed;
    }

    // Báo phụ huynh đã liên kết: trẻ lên lớp / ở lại vào lớp năm mới (Cần chốt 14/11/2026)
    private void notifyGuardiansOfNewClass(YearSetupResponse setup, Board board) {
        Map<Long, String> classNames = setup.classes().stream()
                .collect(Collectors.toMap(YearSetupResponse.SetupClass::id, YearSetupResponse.SetupClass::name));
        Map<Long, ClassPlacement> moved = board.placements.values().stream()
                .filter(p -> p.getAction().keepsChildInClass() && classNames.containsKey(p.getToClassroomId()))
                .collect(Collectors.toMap(ClassPlacement::getChildId, p -> p));
        if (moved.isEmpty()) {
            return;
        }
        String from = DateTimeFormatter.ofPattern("dd/MM/yyyy").format(transitionStart(setup));
        Map<Long, List<Long>> guardians = guardianChildLinkService.findLinksOfChildren(moved.keySet()).stream()
                .collect(Collectors.groupingBy(
                        GuardianLinkResponse::childId,
                        Collectors.mapping(GuardianLinkResponse::userId, Collectors.toList())));
        guardians.forEach((childId, userIds) -> {
            Child child = board.children.get(childId);
            String name = child == null ? "Bé" : "Bé " + child.getFullName();
            notificationService.notifyUsers(
                    userIds,
                    NotificationType.CHILD_PLACED,
                    "Lớp năm học " + setup.year().name(),
                    name + " học lớp " + classNames.get(moved.get(childId).getToClassroomId()) + " năm học "
                            + setup.year().name() + " từ ngày " + from + ".",
                    childId);
        });
    }

    private void notifyGuardiansOfNewAdmissions(YearSetupResponse setup, Board board) {
        Map<Long, String> classNames = setup.classes().stream()
                .collect(Collectors.toMap(YearSetupResponse.SetupClass::id, YearSetupResponse.SetupClass::name));
        Map<Long, PlannedAdmission> admitted = board.admissions.values().stream()
                .filter(a -> a.getStatus() == PlannedAdmissionStatus.ACTIVATED
                        && a.getClassroomId() != null
                        && classNames.containsKey(a.getClassroomId()))
                .collect(Collectors.toMap(PlannedAdmission::getChildId, Function.identity()));
        if (admitted.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> guardians = guardianChildLinkService.findLinksOfChildren(admitted.keySet()).stream()
                .collect(Collectors.groupingBy(
                        GuardianLinkResponse::childId,
                        Collectors.mapping(GuardianLinkResponse::userId, Collectors.toList())));
        guardians.forEach((childId, userIds) -> {
            PlannedAdmission admission = admitted.get(childId);
            Child child = board.children.get(childId);
            String name = child == null ? "Bé" : "Bé " + child.getFullName();
            LocalDate actualStart = admission.getStartDate().isAfter(transitionStart(setup))
                    ? admission.getStartDate()
                    : transitionStart(setup);
            notificationService.notifyUsers(
                    userIds,
                    NotificationType.CHILD_PLACED,
                    "Lớp năm học " + setup.year().name(),
                    name + " học lớp " + classNames.get(admission.getClassroomId()) + " từ ngày "
                            + DISPLAY_DATE.format(actualStart) + ".",
                    childId);
        });
    }

    // Nhóm tuổi theo đầu năm mới dùng cho gợi ý/tự xếp; không ràng buộc lựa chọn xếp tay.
    private static Long ageGroupOf(YearSetupResponse setup, Child child) {
        return setup.ageGroups().stream()
                .filter(g -> g.ageFromMonths() != null && g.ageToMonths() != null)
                .filter(g -> fitsAgeGroup(setup, g.id(), child))
                .map(AgeGroupDetailResponse::id)
                .findFirst()
                .orElse(null);
    }

    private static long ageMonths(YearSetupResponse setup, Child child) {
        return java.time.temporal.ChronoUnit.MONTHS.between(
                child.getDateOfBirth(), setup.year().startDate());
    }

    private static boolean oldEnoughToGraduate(YearSetupResponse setup, Child child) {
        return GraduationAge.oldEnoughForGradeOne(
                child.getDateOfBirth(), setup.year().startDate());
    }

    private static boolean isOldestGroup(YearSetupResponse setup, Long ageGroupId) {
        return setup.ageGroups().stream()
                .filter(g -> g.ageFromMonths() != null)
                .max(Comparator.comparing(AgeGroupDetailResponse::ageFromMonths))
                .map(g -> g.id().equals(ageGroupId))
                .orElse(false);
    }

    private static boolean fitsAgeGroup(YearSetupResponse setup, Long ageGroupId, Child child) {
        var group = setup.ageGroups().stream()
                .filter(g -> g.id().equals(ageGroupId))
                .findFirst()
                .orElse(null);
        if (group == null || group.ageFromMonths() == null || group.ageToMonths() == null) {
            return true;
        }
        long months = java.time.temporal.ChronoUnit.MONTHS.between(
                child.getDateOfBirth(), setup.year().startDate());
        return months >= group.ageFromMonths() && months < group.ageToMonths();
    }

    private LocalDate transitionStart(YearSetupResponse setup) {
        LocalDate today = LocalDate.now(clock);
        return today.isAfter(setup.year().startDate()) ? today : setup.year().startDate();
    }

    /** Số tuần thực học của năm (null nếu chưa nhập ngày học kỳ), tính một lần cho bước thông tin năm học. */
    /** Trẻ có lịch nghỉ học hẳn không sau ngày bắt đầu năm mới thì không học năm mới. */
    private static boolean leavesBeforeYear(YearSetupResponse setup, Child child) {
        return child.getScheduledLeaveOn() != null
                && !child.getScheduledLeaveOn().isAfter(setup.year().startDate());
    }

    private record CalendarCheck(Integer teachingWeeks, List<String> warnings) {}

    private CalendarCheck calendarCheckOf(CurrentUser currentUser, Long schoolYearId) {
        SchoolYearDetailResponse year = schoolYearService.getSchoolYear(currentUser, schoolYearId);
        List<TeachingWeeks.Break> breaks = schoolYearService.getHolidays(currentUser, schoolYearId).stream()
                .map(h -> new TeachingWeeks.Break(h.startDate(), h.endDate()))
                .toList();
        return new CalendarCheck(
                TeachingWeeks.count(
                        year.semester1Start(), year.semester1End(), year.semester2Start(), year.semester2End(), breaks),
                TeachingWeeks.warnings(
                        year.semester1Start(),
                        year.semester1End(),
                        year.semester2Start(),
                        year.semester2End(),
                        year.startDate(),
                        year.endDate(),
                        breaks));
    }

    private YearReadinessResponse buildReadiness(YearSetupResponse setup, Board board, CalendarCheck calendar) {
        Integer teachingWeeks = calendar.teachingWeeks();
        List<YearSetupResponse.SetupClass> classes = setup.classes();
        boolean hasClasses = !classes.isEmpty();
        List<YearReadinessResponse.Step> steps = new ArrayList<>();
        // Lệch khung năm học (thiếu tuần, quá hạn học kỳ) chỉ cảnh báo vàng, không chặn kích hoạt
        boolean weeksOff = teachingWeeks != null && teachingWeeks < TeachingWeeks.STANDARD;
        steps.add(new YearReadinessResponse.Step(
                "info",
                STEP_LABELS.get("info"),
                true,
                true,
                weeksOff || !calendar.warnings().isEmpty(),
                DISPLAY_DATE.format(setup.year().startDate()) + " – "
                        + DISPLAY_DATE.format(setup.year().endDate())
                        + (teachingWeeks == null
                                ? ""
                                : " · " + teachingWeeks + " tuần thực học"
                                        + (weeksOff ? " (chuẩn " + TeachingWeeks.STANDARD + " tuần)" : ""))
                        + calendar.warnings().stream().map(w -> " · " + w).collect(Collectors.joining())));
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
        // SCH-04 (Cần chốt 28/11/2026): mỗi lớp đủ 2 GV và có GV phụ trách chính mới kích hoạt được
        long noTeacher = classes.stream().filter(c -> c.teacherCount() == 0).count();
        long shortOne = classes.stream()
                .filter(c -> c.teacherCount() > 0 && c.teacherCount() < TEACHERS_PER_CLASS)
                .count();
        long noHomeroom = classes.stream()
                .filter(c -> c.teacherCount() > 0 && !c.hasHomeroom())
                .count();
        boolean teachersOk = hasClasses && noTeacher == 0 && shortOne == 0 && noHomeroom == 0;
        String teachersDetail;
        if (!hasClasses) {
            teachersDetail = "Chưa có lớp";
        } else if (!teachersOk) {
            teachersDetail = Stream.of(
                            noTeacher > 0 ? noTeacher + " lớp chưa có giáo viên" : null,
                            shortOne > 0 ? shortOne + " lớp thiếu 1 GV" : null,
                            noHomeroom > 0 ? noHomeroom + " lớp chưa có GV phụ trách chính" : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" · "));
        } else {
            teachersDetail = "Tất cả lớp đủ GV và có GV phụ trách chính";
        }
        steps.add(step("teachers", true, teachersOk, teachersDetail));
        long toPlace = board.childClass.size();
        long placed = board.childClass.keySet().stream()
                .filter(board.placements::containsKey)
                .count();
        // Kiểm lại lớp đích và sĩ số lúc kích hoạt; xếp tay khác nhóm tuổi vẫn hợp lệ.
        List<String> misfits = placementMisfits(setup, board);
        String placementDetail = setup.sourceYear() == null
                ? "Không có năm học đang hoạt động cần chuyển trẻ"
                : placed + "/" + toPlace + " trẻ của năm " + setup.sourceYear().name() + " đã xếp lớp";
        // Cần chốt 19/11/2026 (BUG-43): trẻ đã tiếp nhận cho năm phải được xếp lớp trước khi kích hoạt
        List<String> unplaced = board.admissions.values().stream()
                .filter(a -> a.getClassroomId() == null)
                .map(a -> board.children.containsKey(a.getChildId())
                        ? board.children.get(a.getChildId()).getFullName()
                        : "#" + a.getChildId())
                .sorted()
                .toList();
        if (!unplaced.isEmpty()) {
            misfits = new ArrayList<>(misfits);
            misfits.add("Còn " + unplaced.size() + " trẻ đã tiếp nhận chưa xếp lớp ("
                    + (unplaced.size() > 3
                            ? String.join(", ", unplaced.subList(0, 3)) + " và " + (unplaced.size() - 3) + " trẻ khác"
                            : String.join(", ", unplaced))
                    + "), xếp lớp trước khi kích hoạt năm");
        }
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
        // CFG-YR-05: chưa tới ngày mở chuẩn bị thì chưa kích hoạt (giai đoạn chuẩn bị không bị bỏ qua)
        LocalDate prepDate = prepDateOf(setup);
        if (prepDate != null && prepDate.isAfter(openFrom)) {
            openFrom = prepDate;
            if (!devActivation.skipDateWindow() && LocalDate.now(clock).isBefore(prepDate)) {
                blockers.add("Chưa tới ngày mở chuẩn bị " + DISPLAY_DATE.format(prepDate));
            }
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

    // Xếp khác nhóm tuổi được phép, nhưng lớp đích phải còn hợp lệ và không vượt sĩ số.
    private static List<String> placementMisfits(YearSetupResponse setup, Board board) {
        List<String> over = setup.classes().stream()
                .filter(c -> c.capacity() != null && board.taken(c.id()) > c.capacity())
                .map(c -> c.name() + " (" + board.taken(c.id()) + "/" + c.capacity() + ")")
                .toList();
        Map<Long, YearSetupResponse.SetupClass> targets = setup.classes().stream()
                .collect(Collectors.toMap(YearSetupResponse.SetupClass::id, Function.identity()));
        long invalidTargets = board.placements.values().stream()
                .filter(p -> p.getAction().keepsChildInClass())
                .filter(p -> {
                    YearSetupResponse.SetupClass from = board.sourceClassOf(p.getChildId());
                    YearSetupResponse.SetupClass to = targets.get(p.getToClassroomId());
                    return from == null || to == null || to.ageGroupId() == null;
                })
                .count();
        List<String> misfits = new ArrayList<>();
        if (!over.isEmpty()) {
            misfits.add("Vượt sĩ số: " + String.join(", ", over));
        }
        if (invalidTargets > 0) {
            misfits.add(invalidTargets + " trẻ có lớp xếp năm mới không còn hợp lệ");
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
        plannedAdmissionRepository
                .findBySchoolYearIdAndStatusInOrderById(
                        setup.year().id(), List.of(PlannedAdmissionStatus.PENDING, PlannedAdmissionStatus.PLACED))
                .forEach(a -> board.admissions.put(a.getId(), a));
        childRepository
                .findAllById(board.admissions.values().stream()
                        .map(PlannedAdmission::getChildId)
                        .toList())
                .forEach(c -> board.children.put(c.getId(), c));
        return board;
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

    // CFG-YR-03 (Cần chốt 25/11/2026): xếp lớp mở từ ngày kết thúc HK II của năm đang hoạt động; chưa đặt thì ngày kết
    // thúc năm
    private LocalDate placementOpenFrom(CurrentUser user, YearSetupResponse setup) {
        if (setup.sourceYear() == null) {
            return null;
        }
        SchoolYearDetailResponse source =
                schoolYearService.getSchoolYear(user, setup.sourceYear().id());
        return source.semester2End() != null ? source.semester2End() : source.endDate();
    }

    private boolean placementNotOpen(LocalDate openFrom) {
        return openFrom != null
                && !devActivation.skipDateWindow()
                && LocalDate.now(clock).isBefore(openFrom);
    }

    private void checkPlacementOpen(CurrentUser user, YearSetupResponse setup) {
        LocalDate openFrom = placementOpenFrom(user, setup);
        if (placementNotOpen(openFrom)) {
            throw GlobalException.of(
                    ApiCode.PLACEMENT_NOT_OPEN,
                    DISPLAY_DATE.format(openFrom),
                    setup.sourceYear().name());
        }
    }

    private Set<Long> yearEndConfirmed(YearSetupResponse setup, Board board) {
        if (setup.sourceYear() == null || programCompletionLookups.isEmpty()) {
            return Set.of();
        }
        List<Long> childIds = setup.sourceClasses().stream()
                .flatMap(c -> board.childrenOf(c.id()).stream())
                .map(Child::getId)
                .toList();
        return programCompletionLookups
                .getFirst()
                .yearEndConfirmed(setup.sourceYear().id(), childIds);
    }

    // CFG-YR-03: chỉ xếp lớp cho năm sắp diễn ra sớm nhất (năm kế tiếp năm đang hoạt động)
    private boolean isNextYear(YearSetupResponse setup) {
        SchoolYearDetailResponse next = schoolYearService.findNextPlannedYear();
        return next != null && next.id().equals(setup.year().id());
    }

    // AUTH-01: xếp lớp và kích hoạt năm do Hiệu trưởng có phân công còn hiệu lực
    // Phân quyền động (Cần chốt 10/11/2026): HT luôn xếp được; PHT khi HT cấp YEAR_PLACEMENT_MANAGE, ở đúng phạm vi
    // CFG-YR-05: năm chưa tới ngày mở chuẩn bị chỉ HT thấy, PHT không xem / xếp lớp được
    private void checkCanPlace(CurrentUser user, Long schoolYearId) {
        if (!isPrincipal(user) && !user.hasPermission(Permission.YEAR_PLACEMENT_MANAGE)) {
            throw GlobalException.of(ApiCode.PLACEMENT_ACCESS_DENIED);
        }
        schoolYearService.checkYearVisible(user, schoolYearId);
    }

    private boolean placesCampus(CurrentUser user, Long campusId) {
        return isPrincipal(user) || campusAccessPolicy.canEditCampus(user, Permission.YEAR_PLACEMENT_MANAGE, campusId);
    }

    private void checkPrincipal(CurrentUser user) {
        if (!isPrincipal(user)) {
            throw GlobalException.of(ApiCode.PRINCIPAL_ONLY, "quản lý chuyển năm học");
        }
    }

    private boolean isPrincipal(CurrentUser user) {
        return user.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(user.userId(), LocalDate.now(clock));
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

    // Năm được kích hoạt luôn là năm PLANNED sớm nhất (CFG-YR-03) nên lấy ngày mở chuẩn bị của năm đó
    private LocalDate prepDateOf(YearSetupResponse setup) {
        if (setup.year().status() != SchoolYearStatus.PLANNED) {
            return null;
        }
        SchoolYearDetailResponse next = schoolYearService.findNextPlannedYear();
        return next != null && next.id().equals(setup.year().id()) ? next.prepDate() : null;
    }

    private static YearReadinessResponse.Step step(String key, boolean required, boolean ok, String detail) {
        return new YearReadinessResponse.Step(key, STEP_LABELS.get(key), required, ok, false, detail);
    }

    private static PlacementBoardResponse.YearBrief toBrief(YearSetupResponse.Year year) {
        return new PlacementBoardResponse.YearBrief(year.id(), year.name());
    }

    /** Trạng thái xếp lớp trong một lần xử lý: trẻ của năm nguồn, kết quả đã xếp, chỗ đã dùng ở lớp năm mới. */
    // Kết quả hoàn thành chương trình của trẻ ở lớp nhóm tuổi lớn nhất (năm nguồn), từ đánh giá cuối năm
    private Map<Long, Boolean> graduatingCompletion(YearSetupResponse setup, Board board) {
        if (setup.sourceYear() == null || programCompletionLookups.isEmpty()) {
            return Map.of();
        }
        List<Long> childIds = setup.sourceClasses().stream()
                .flatMap(c -> board.childrenOf(c.id()).stream())
                .filter(child -> oldEnoughToGraduate(setup, child))
                .map(Child::getId)
                .toList();
        return programCompletionLookups
                .getFirst()
                .programCompletion(setup.sourceYear().id(), childIds);
    }

    private final class Board {
        private final YearSetupResponse setup;
        private final Map<Long, Long> childClass = new HashMap<>();
        private final Map<Long, Child> children = new HashMap<>();
        private final Map<Long, ClassPlacement> placements = new HashMap<>();
        private final Map<Long, PlannedAdmission> admissions = new HashMap<>();
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
            long continuing = placements.values().stream()
                    .filter(p -> p.getAction().keepsChildInClass() && targetClassId.equals(p.getToClassroomId()))
                    .count();
            long newChildren = admissions.values().stream()
                    .filter(a ->
                            a.getStatus() == PlannedAdmissionStatus.PLACED && targetClassId.equals(a.getClassroomId()))
                    .count();
            return continuing + newChildren;
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
