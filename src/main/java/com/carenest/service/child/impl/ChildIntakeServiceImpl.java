package com.carenest.service.child.impl;

import com.carenest.dto.child.ChildAddressResponse;
import com.carenest.dto.child.ChildGuardianResponse;
import com.carenest.dto.child.ChildHistoryResponse;
import com.carenest.dto.child.ChildProfileDetailResponse;
import com.carenest.dto.child.ChildProfileDraft;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.DuplicateCheckResponse;
import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.EnrollmentDraft;
import com.carenest.dto.child.GuardianChildSummary;
import com.carenest.dto.child.GuardianDraft;
import com.carenest.dto.child.GuardianRecord;
import com.carenest.dto.child.HealthDeclarationDraft;
import com.carenest.dto.child.PlacementClassResponse;
import com.carenest.dto.child.ReadmitChildRequest;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.child.Child;
import com.carenest.entity.child.ChildAllergy;
import com.carenest.entity.child.ChildGuardian;
import com.carenest.entity.child.ChildHealthDeclaration;
import com.carenest.entity.child.ChildHealthDeclarationHistory;
import com.carenest.entity.child.ChildHistory;
import com.carenest.entity.child.ChildProfile;
import com.carenest.entity.child.Enrollment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.ChildAllergyRepository;
import com.carenest.repository.child.ChildCodeSequenceRepository;
import com.carenest.repository.child.ChildGuardianRepository;
import com.carenest.repository.child.ChildHealthDeclarationHistoryRepository;
import com.carenest.repository.child.ChildHealthDeclarationRepository;
import com.carenest.repository.child.ChildHistoryRepository;
import com.carenest.repository.child.ChildProfileRepository;
import com.carenest.repository.child.ChildRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.CampusService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.CitizenIds;
import com.carenest.utils.enums.CampusStatus;
import com.carenest.utils.enums.ChildHistoryAction;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.ClassroomStatus;
import com.carenest.utils.enums.DuplicateChildKind;
import com.carenest.utils.enums.GuardianAccountStatus;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.HealthDeclarationAction;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.SmsStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Không log họ tên, ngày sinh, SĐT, CCCD, sức khỏe — chỉ id, số lượng. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildIntakeServiceImpl implements ChildIntakeService {
    private static final String LOG_PREFIX = "[ChildIntakeServiceImpl]";
    private static final DateTimeFormatter YEAR_SUFFIX = DateTimeFormatter.ofPattern("yy");
    // Không khớp SĐT/CCCD nào (thay danh sách rỗng trong IN)
    private static final Set<String> NO_MATCH = Set.of("-");

    private final ChildRepository childRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ChildGuardianRepository childGuardianRepository;
    private final ChildHealthDeclarationRepository declarationRepository;
    private final ChildAllergyRepository allergyRepository;
    private final ChildHealthDeclarationHistoryRepository declarationHistoryRepository;
    private final ChildHistoryRepository childHistoryRepository;
    private final ChildCodeSequenceRepository codeSequenceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final GuardianChildLinkService guardianChildLinkService;
    private final ClassroomService classroomService;
    private final CampusService campusService;
    private final StaffAssignmentService staffAssignmentService;
    private final StaffAccessPolicy staffAccessPolicy;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public DuplicateCheckResponse checkDuplicate(
            CurrentUser actor, String fullName, String dateOfBirth, List<String> guardianIdNumbers, Long excludeId) {
        String logPrefix = LOG_PREFIX + "|checkDuplicate|userId=" + actor.userId() + "|excludeId=" + excludeId;
        log.info("{}|START", logPrefix);
        try {
            String nameKey = EnrollmentFormValidator.nameKey(fullName);
            LocalDate birthDate = parseDate(dateOfBirth);
            Set<String> citizenIds = (guardianIdNumbers == null ? List.<String>of() : guardianIdNumbers)
                    .stream()
                            .map(CitizenIds::normalize)
                            .flatMap(Optional::stream)
                            .collect(Collectors.toSet());
            if (nameKey.isEmpty() || birthDate == null || citizenIds.isEmpty()) {
                log.info("{}|incomplete|END", logPrefix);
                return null;
            }
            List<Child> candidates = sameChildren(nameKey, birthDate, citizenIds, excludeId);
            Optional<Child> active = candidates.stream()
                    .filter(c -> c.getStatus() != ChildStatus.LEFT)
                    .findFirst();
            Optional<Child> match = active.or(() -> candidates.stream()
                    .filter(c -> c.getStatus() == ChildStatus.LEFT)
                    .findFirst());
            if (match.isEmpty()) {
                log.info("{}|duplicate=false|END", logPrefix);
                return null;
            }
            DuplicateChildKind kind = active.isPresent() ? DuplicateChildKind.ACTIVE : DuplicateChildKind.LEFT;
            Child child = match.get();
            LocalDate today = LocalDate.now(clock);
            DuplicateCheckResponse response = inScope(actor, currentCampusOf(child, today))
                    // Mở lại hồ sơ cần quyền tiếp nhận ở điểm trường của trẻ = đúng phạm vi vừa kiểm
                    ? new DuplicateCheckResponse(
                            kind,
                            true,
                            child.getId(),
                            child.getCode(),
                            child.getFullName(),
                            child.getLeftOn(),
                            kind == DuplicateChildKind.LEFT)
                    : DuplicateCheckResponse.otherCampus(kind);
            log.info("{}|duplicate={}|sameCampus={}|END", logPrefix, kind, response.sameCampus());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlacementClassResponse> listPlacementClasses(CurrentUser actor) {
        String logPrefix = LOG_PREFIX + "|listPlacementClasses|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            boolean wholeSchool = staffAccessPolicy.managesWholeSchool(actor);
            Set<Long> managed = wholeSchool ? Set.of() : staffAccessPolicy.managedCampusIds(actor);
            List<ClassroomInfoResponse> classes = classroomService.findActiveYearClassrooms().stream()
                    .filter(ChildIntakeServiceImpl::isOpenForPlacement)
                    .filter(c -> wholeSchool || managed.contains(c.campusId()))
                    .toList();
            List<Long> ids = classes.stream().map(ClassroomInfoResponse::id).toList();
            Map<Long, Long> enrolled = enrolledCounts(ids, today);
            Map<Long, Long> homeroom =
                    ids.isEmpty() ? Map.of() : staffAssignmentService.findHomeroomTeacherIds(ids, today);
            List<PlacementClassResponse> response = classes.stream()
                    .map(c -> new PlacementClassResponse(
                            c.id(),
                            c.name(),
                            c.campusId(),
                            c.ageGroupId(),
                            c.schoolYearId(),
                            c.capacity(),
                            enrolled.getOrDefault(c.id(), 0L),
                            homeroom.get(c.id())))
                    .toList();
            log.info("{}|count={}|END", logPrefix, response.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ChildViewResponse readmit(CurrentUser actor, Long childId, ReadmitChildRequest request) {
        String logPrefix = LOG_PREFIX + "|readmit|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            boolean wholeSchool = staffAccessPolicy.managesWholeSchool(actor);
            // Như xem hồ sơ: với PHT, trẻ không tồn tại và trẻ ngoài điểm trường cùng 403 (không dò được id)
            Child child = childRepository
                    .findById(childId)
                    .orElseThrow(() ->
                            GlobalException.of(wholeSchool ? ApiCode.CHILD_NOT_FOUND : ApiCode.CHILD_ACCESS_DENIED));
            Long campusId = currentCampusOf(child, today);
            if (!inScope(actor, campusId)) {
                throw GlobalException.of(ApiCode.CHILD_ACCESS_DENIED);
            }
            if (child.getStatus() != ChildStatus.LEFT) {
                throw GlobalException.of(ApiCode.CHILD_NOT_LEFT);
            }
            String reason = request == null || request.reason() == null
                    ? ""
                    : request.reason().strip();
            if (reason.isEmpty()) {
                throw fieldError("reason", "Nhập lý do tiếp nhận lại");
            }
            if (reason.length() > 300) {
                throw fieldError("reason", "Lý do tối đa 300 ký tự");
            }
            child.readmit(campusId, today);
            childRepository.save(child);
            childHistoryRepository.save(ChildHistory.record(
                    childId, ChildHistoryAction.READMITTED, actor.userId(), reason, clock.instant()));
            ChildViewResponse view = getChildView(childId);
            log.info("{}|campusId={}|END", logPrefix, campusId);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentDraft prepareEnrollment(CurrentUser actor, EnrollChildRequest request) {
        String logPrefix = LOG_PREFIX + "|prepareEnrollment|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            EnrollmentFormValidator.Result result = EnrollmentFormValidator.validate(request, today);
            if (!result.errors().isEmpty()) {
                throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, result.errors());
            }
            EnrollmentDraft draft = placeOf(actor, request.classId(), request.campusId(), today, result.draft());
            Set<String> citizenIds =
                    draft.guardians().stream().map(GuardianDraft::citizenId).collect(Collectors.toSet());
            // CHD-04: trùng trẻ đang học / chờ xếp lớp thì không tiếp nhận; trùng trẻ đã nghỉ thì FE gợi ý mở lại
            boolean duplicate =
                    sameChildren(
                                    EnrollmentFormValidator.nameKey(draft.fullName()),
                                    draft.dateOfBirth(),
                                    citizenIds,
                                    null)
                            .stream()
                            .anyMatch(c -> c.getStatus() != ChildStatus.LEFT);
            if (duplicate) {
                throw GlobalException.of(ApiCode.CHILD_DUPLICATE);
            }
            log.info(
                    "{}|guardians={}|classroomId={}|campusId={}|END",
                    logPrefix,
                    draft.guardians().size(),
                    draft.classroomId(),
                    draft.campusId());
            return draft;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public Long createEnrolledChild(CurrentUser actor, EnrollmentDraft draft, Map<Integer, Long> accountIdByPosition) {
        String logPrefix = LOG_PREFIX + "|createEnrolledChild|userId=" + actor.userId() + "|classroomId="
                + draft.classroomId() + "|campusId=" + draft.campusId();
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Instant now = clock.instant();
            boolean placed = draft.classroomId() != null;
            Child child = childRepository.save(Child.enroll(
                    nextCode(today),
                    draft.fullName(),
                    draft.dateOfBirth(),
                    draft.gender(),
                    placed,
                    draft.campusId(),
                    today));
            Long childId = child.getId();

            ChildProfileDraft p = draft.profile();
            childProfileRepository.save(ChildProfile.create(
                    childId,
                    p.idNumber(),
                    p.premature(),
                    p.gestationWeeks(),
                    p.ethnicity(),
                    p.beneficiary(),
                    p.bloodType(),
                    p.addressProvince(),
                    p.addressWard(),
                    p.addressStreet()));

            // CHD-06: dị ứng có hiệu lực ngay khi lưu (bếp, điểm danh đọc child_allergies); khai báo ghi trước dị ứng
            // (FK)
            HealthDeclarationDraft d = draft.declaration();
            declarationRepository.saveAndFlush(ChildHealthDeclaration.declare(
                    childId,
                    d.allergiesState(),
                    answer(d.diet()),
                    answer(d.otherNotes()),
                    answer(d.selfHistory()),
                    answer(d.familyHistory()),
                    d.tbContactState(),
                    d.tbContactDate(),
                    actor.userId(),
                    now));
            allergyRepository.saveAll(d.allergies().stream()
                    .map(name -> ChildAllergy.of(childId, name))
                    .toList());
            declarationHistoryRepository.save(ChildHealthDeclarationHistory.record(
                    childId, HealthDeclarationAction.DECLARED, actor.userId(), null, now));
            childHistoryRepository.save(
                    ChildHistory.record(childId, ChildHistoryAction.ENROLLED, actor.userId(), null, now));
            if (placed) {
                enrollmentRepository.save(Enrollment.start(childId, draft.classroomId(), today, actor.userId()));
            }

            int linked = 0;
            for (GuardianDraft g : draft.guardians()) {
                Long userId = accountIdByPosition.get(g.position());
                childGuardianRepository.save(ChildGuardian.create(
                        childId,
                        g.position(),
                        g.fullName(),
                        g.relationship(),
                        g.phone(),
                        g.citizenId(),
                        g.email(),
                        userId));
                // Liên kết là nguồn scope của phụ huynh (AUTH-04)
                if (userId != null) {
                    guardianChildLinkService.createLink(userId, childId, g.relationship());
                    linked++;
                }
            }
            log.info(
                    "{}|childId={}|guardians={}|linked={}|END",
                    logPrefix,
                    childId,
                    draft.guardians().size(),
                    linked);
            return childId;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ChildViewResponse getChildView(Long childId) {
        String logPrefix = LOG_PREFIX + "|getChildView|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            Child child =
                    childRepository.findById(childId).orElseThrow(() -> GlobalException.of(ApiCode.CHILD_NOT_FOUND));
            LocalDate today = LocalDate.now(clock);
            ClassroomInfoResponse classroom = activeClassroomId(childId, today)
                    .flatMap(classroomService::findClassroomInfo)
                    .orElse(null);
            Long campusId = classroom != null ? classroom.campusId() : campusWithoutClass(child);

            ChildProfileDetailResponse profile = childProfileRepository
                    .findById(childId)
                    .map(ChildIntakeServiceImpl::toProfileResponse)
                    .orElse(null);
            List<String> allergies = allergyRepository.findAllByChildIdOrderByIdAsc(childId).stream()
                    .map(ChildAllergy::getName)
                    .toList();
            ChildViewResponse view = new ChildViewResponse(
                    child.getId(),
                    child.getCode(),
                    child.getFullName(),
                    child.getDateOfBirth(),
                    child.getGender(),
                    child.getStatus(),
                    campusId,
                    classroom == null ? null : classroom.id(),
                    classroom == null ? null : classroom.name(),
                    classroom == null ? null : classroom.ageGroupId(),
                    classroom == null ? null : classroom.schoolYearId(),
                    child.getEnrolledOn(),
                    child.getLeftOn(),
                    child.getLeftReason(),
                    child.getReadmittedOn(),
                    allergies,
                    declarationRepository.existsById(childId),
                    profile,
                    guardiansOf(childId),
                    historyOf(childId));
            log.info("{}|END", logPrefix);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuardianRecord> findGuardianRecords(Collection<String> phones, Collection<String> citizenIds) {
        String logPrefix =
                LOG_PREFIX + "|findGuardianRecords|phones=" + phones.size() + "|citizenIds=" + citizenIds.size();
        log.info("{}|START", logPrefix);
        try {
            List<GuardianRecord> records = phones.isEmpty() && citizenIds.isEmpty()
                    ? List.of()
                    : childGuardianRepository
                            .findAllByPhoneOrCitizenId(
                                    phones.isEmpty() ? NO_MATCH : phones, citizenIds.isEmpty() ? NO_MATCH : citizenIds)
                            .stream()
                            .map(g -> new GuardianRecord(g.getChildId(), g.getPhone(), g.getCitizenId(), g.getUserId()))
                            .toList();
            log.info("{}|records={}|END", logPrefix, records.size());
            return records;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuardianChildSummary> findChildSummaries(Collection<Long> childIds) {
        String logPrefix = LOG_PREFIX + "|findChildSummaries|children=" + childIds.size();
        log.info("{}|START", logPrefix);
        try {
            if (childIds.isEmpty()) {
                log.info("{}|END", logPrefix);
                return List.of();
            }
            LocalDate today = LocalDate.now(clock);
            Map<Long, Long> classroomByChild = new HashMap<>();
            // Sắp xếp validFrom giảm dần: lấy enrollment mới nhất nếu dữ liệu cũ chồng thời gian
            enrollmentRepository
                    .findActiveByChildIdIn(childIds, today)
                    .forEach(e -> classroomByChild.putIfAbsent(e.getChildId(), e.getClassroomId()));
            Map<Long, ClassroomInfoResponse> classrooms = new HashSet<>(classroomByChild.values())
                    .stream()
                            .map(classroomService::findClassroomInfo)
                            .flatMap(Optional::stream)
                            .collect(Collectors.toMap(ClassroomInfoResponse::id, Function.identity()));
            List<GuardianChildSummary> summaries = childRepository.findAllById(childIds).stream()
                    .sorted((a, b) -> a.getId().compareTo(b.getId()))
                    .map(c -> {
                        ClassroomInfoResponse classroom = classrooms.get(classroomByChild.get(c.getId()));
                        return new GuardianChildSummary(
                                c.getId(),
                                c.getCode(),
                                c.getFullName(),
                                c.getStatus(),
                                classroom == null ? null : classroom.name(),
                                classroom != null ? classroom.campusId() : campusWithoutClass(c));
                    })
                    .toList();
            log.info("{}|END", logPrefix);
            return summaries;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GuardianRelationship> findRelationshipOfAccount(Long userId, String username) {
        String logPrefix = LOG_PREFIX + "|findRelationshipOfAccount|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            List<ChildGuardian> records = childGuardianRepository.findAllOfAccount(userId, username);
            // Bản ghi đã liên kết đúng tài khoản trước, rồi bản ghi cùng SĐT (liên kết cũ)
            Optional<GuardianRelationship> relationship = records.stream()
                    .filter(g -> userId.equals(g.getUserId()))
                    .findFirst()
                    .or(() -> records.stream().findFirst())
                    .map(ChildGuardian::getRelationship);
            log.info("{}|END", logPrefix);
            return relationship;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /**
     * Lớp/điểm trường nhận trẻ (CHD-02, CHD-10): có lớp ⇒ lớp đang hoạt động của năm đang hoạt động, trong phạm vi,
     * chưa đủ sĩ số; không có lớp ⇒ điểm trường tiếp nhận (PHT một điểm trường bỏ trống = điểm trường của mình).
     */
    private EnrollmentDraft placeOf(
            CurrentUser actor, Long classId, Long campusId, LocalDate today, EnrollmentDraft draft) {
        boolean wholeSchool = staffAccessPolicy.managesWholeSchool(actor);
        Set<Long> managed = wholeSchool ? Set.of() : staffAccessPolicy.managedCampusIds(actor);
        if (classId != null) {
            ClassroomInfoResponse classroom =
                    classroomService.findClassroomInfo(classId).orElse(null);
            if (classroom == null || classroom.status() != ClassroomStatus.ACTIVE) {
                throw fieldError("classId", "Lớp không tồn tại hoặc đã ngừng hoạt động");
            }
            if (classroom.yearStatus() != SchoolYearStatus.ACTIVE) {
                throw fieldError(
                        "classId",
                        "Chỉ xếp vào lớp của năm học đang hoạt động. Xếp lớp cho năm học sau ở màn Xếp lớp năm học mới.");
            }
            if (!wholeSchool && !managed.contains(classroom.campusId())) {
                throw fieldError("classId", "Chỉ xếp được vào lớp thuộc điểm trường bạn được giao");
            }
            long enrolled = enrolledCounts(List.of(classroom.id()), today).getOrDefault(classroom.id(), 0L);
            if (classroom.capacity() != null && enrolled >= classroom.capacity()) {
                throw fieldError(
                        "classId", classroom.name() + " đã đủ " + classroom.capacity() + " trẻ. Chọn lớp khác.");
            }
            if (campusId != null && !campusId.equals(classroom.campusId())) {
                throw fieldError("classId", "Lớp không thuộc điểm trường đã chọn");
            }
            return draft.withPlacement(classroom.id(), classroom.campusId());
        }
        Long target = campusId != null
                ? campusId
                : (managed.size() == 1 ? managed.iterator().next() : null);
        if (target == null) {
            throw fieldError("campusId", "Chọn điểm trường tiếp nhận trẻ");
        }
        if (campusService.findCampusStatus(target).orElse(null) != CampusStatus.ACTIVE) {
            throw fieldError("campusId", "Điểm trường không tồn tại hoặc đã ngừng hoạt động");
        }
        if (!wholeSchool && !managed.contains(target)) {
            throw fieldError("campusId", "Chỉ tiếp nhận trẻ vào điểm trường bạn được giao");
        }
        return draft.withPlacement(null, target);
    }

    private List<Child> sameChildren(String nameKey, LocalDate birthDate, Set<String> citizenIds, Long excludeId) {
        if (citizenIds.isEmpty()) {
            return List.of();
        }
        return childRepository.findDuplicateCandidates(birthDate, citizenIds, excludeId).stream()
                .filter(c -> EnrollmentFormValidator.nameKey(c.getFullName()).equals(nameKey))
                .toList();
    }

    private boolean inScope(CurrentUser actor, Long campusId) {
        return staffAccessPolicy.managesWholeSchool(actor)
                || (campusId != null
                        && staffAccessPolicy.managedCampusIds(actor).contains(campusId));
    }

    /** Điểm trường hiện tại: theo lớp đang học, không có lớp thì điểm trường tiếp nhận, rồi lớp cuối cùng. */
    private Long currentCampusOf(Child child, LocalDate today) {
        return activeClassroomId(child.getId(), today)
                .flatMap(classroomService::findCampusId)
                .orElseGet(() -> campusWithoutClass(child));
    }

    private Long campusWithoutClass(Child child) {
        if (child.getIntakeCampusId() != null) {
            return child.getIntakeCampusId();
        }
        return enrollmentRepository
                .findFirstByChildIdOrderByValidFromDescIdDesc(child.getId())
                .flatMap(e -> classroomService.findCampusId(e.getClassroomId()))
                .orElse(null);
    }

    private Optional<Long> activeClassroomId(Long childId, LocalDate today) {
        return enrollmentRepository.findActiveByChildIdIn(List.of(childId), today).stream()
                .findFirst()
                .map(Enrollment::getClassroomId);
    }

    private Map<Long, Long> enrolledCounts(List<Long> classroomIds, LocalDate today) {
        if (classroomIds.isEmpty()) {
            return Map.of();
        }
        return enrollmentRepository.findActiveByClassroomIds(classroomIds, today).stream()
                .collect(Collectors.groupingBy(Enrollment::getClassroomId, Collectors.counting()));
    }

    private List<ChildGuardianResponse> guardiansOf(Long childId) {
        Map<Long, ChildGuardianRepository.GuardianAccountRow> accounts =
                childGuardianRepository.findAccountRows(childId).stream()
                        .collect(Collectors.toMap(
                                ChildGuardianRepository.GuardianAccountRow::getGuardianId, Function.identity()));
        return childGuardianRepository.findAllByChildIdOrderByPositionAsc(childId).stream()
                .map(g -> {
                    ChildGuardianRepository.GuardianAccountRow row = accounts.get(g.getId());
                    String accountStatus = row == null ? null : row.getAccountStatus();
                    String smsStatus = row == null ? null : row.getSmsStatus();
                    Long smsAt = row == null ? null : row.getSmsAtMillis();
                    return new ChildGuardianResponse(
                            g.getFullName(),
                            g.getRelationship(),
                            g.getPhone(),
                            g.getCitizenId(),
                            g.getEmail(),
                            accountStatus == null
                                    ? GuardianAccountStatus.NOT_ACTIVATED
                                    : GuardianAccountStatus.valueOf(accountStatus),
                            smsStatus == null ? null : SmsStatus.valueOf(smsStatus),
                            smsAt == null ? null : Instant.ofEpochMilli(smsAt));
                })
                .toList();
    }

    private List<ChildHistoryResponse> historyOf(Long childId) {
        List<ChildHistory> entries = childHistoryRepository.findAllByChildIdOrderByCreatedAtAscIdAsc(childId);
        Set<Long> userIds = entries.stream().map(ChildHistory::getUserId).collect(Collectors.toSet());
        Map<Long, String> names = userIds.isEmpty()
                ? Map.of()
                : childHistoryRepository.findUserNames(userIds).stream()
                        .collect(Collectors.toMap(
                                ChildHistoryRepository.UserName::getUserId,
                                ChildHistoryRepository.UserName::getFullName));
        return entries.stream()
                .map(h -> new ChildHistoryResponse(
                        h.getAction(), h.getUserId(), names.get(h.getUserId()), h.getCreatedAt(), h.getNote()))
                .toList();
    }

    /** Mã trẻ HS + 2 số cuối năm tiếp nhận + số thứ tự trong năm, tối thiểu 3 chữ số (như V10). */
    private String nextCode(LocalDate today) {
        String prefix = "HS" + YEAR_SUFFIX.format(today);
        codeSequenceRepository.increment(prefix);
        int number = codeSequenceRepository.currentNumber(prefix);
        return prefix + (number < 1000 ? String.format("%03d", number) : String.valueOf(number));
    }

    private static boolean isOpenForPlacement(ClassroomInfoResponse classroom) {
        return classroom.status() == ClassroomStatus.ACTIVE && classroom.yearStatus() == SchoolYearStatus.ACTIVE;
    }

    private static ChildHealthDeclaration.Answer answer(HealthDeclarationDraft.Answer answer) {
        return new ChildHealthDeclaration.Answer(answer.state(), answer.text());
    }

    private static ChildProfileDetailResponse toProfileResponse(ChildProfile p) {
        return new ChildProfileDetailResponse(
                p.getIdNumber(),
                p.getPremature(),
                p.getGestationWeeks(),
                p.getEthnicity(),
                p.getBeneficiary(),
                p.getPaymentSource(),
                p.getBloodType(),
                new ChildAddressResponse(p.getAddressProvince(), p.getAddressWard(), p.getAddressStreet()));
    }

    private static GlobalException fieldError(String field, String message) {
        return GlobalException.withFieldErrors(
                ApiCode.INVALID_REQUEST_DATA, List.of(new FieldErrorResponse(field, message)));
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
