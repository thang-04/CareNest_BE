package com.carenest.service.child.impl;

import com.carenest.dto.child.ChildAddressResponse;
import com.carenest.dto.child.ChildGuardianContact;
import com.carenest.dto.child.ChildGuardianResponse;
import com.carenest.dto.child.ChildHistoryResponse;
import com.carenest.dto.child.ChildMedicalHistory;
import com.carenest.dto.child.ChildNextClassResponse;
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
import com.carenest.dto.child.HealthDeclarationResponse;
import com.carenest.dto.child.PlacementClassResponse;
import com.carenest.dto.child.PlannedAdmissionResponse;
import com.carenest.dto.child.ReadmitChildRequest;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.entity.child.Child;
import com.carenest.entity.child.ChildAllergy;
import com.carenest.entity.child.ChildGuardian;
import com.carenest.entity.child.ChildHealthDeclaration;
import com.carenest.entity.child.ChildHealthDeclarationHistory;
import com.carenest.entity.child.ChildHistory;
import com.carenest.entity.child.ChildProfile;
import com.carenest.entity.child.Enrollment;
import com.carenest.entity.child.PlannedAdmission;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.ChildAllergyRepository;
import com.carenest.repository.child.ChildCodeSequenceRepository;
import com.carenest.repository.child.ChildGuardianRepository;
import com.carenest.repository.child.ChildHealthDeclarationHistoryRepository;
import com.carenest.repository.child.ChildHealthDeclarationRepository;
import com.carenest.repository.child.ChildHistoryRepository;
import com.carenest.repository.child.ChildProfileRepository;
import com.carenest.repository.child.ChildRepository;
import com.carenest.repository.child.ClassPlacementRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.repository.child.PlannedAdmissionRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.organization.CampusService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.CitizenIds;
import com.carenest.utils.enums.CampusStatus;
import com.carenest.utils.enums.ChildHistoryAction;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.ClassroomStatus;
import com.carenest.utils.enums.DeclarationState;
import com.carenest.utils.enums.DuplicateChildKind;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.HealthDeclarationAction;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PlannedAdmissionStatus;
import com.carenest.utils.enums.SchoolYearStatus;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Không log họ tên, ngày sinh, SĐT, CCCD, sức khỏe — chỉ id, số lượng. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildIntakeServiceImpl implements ChildIntakeService {
    private static final String LOG_PREFIX = "[ChildIntakeServiceImpl]";
    private static final DateTimeFormatter YEAR_SUFFIX = DateTimeFormatter.ofPattern("yy");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String START_DATE_LABEL = "Ngày bắt đầu đi học";
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
    private final PlannedAdmissionRepository plannedAdmissionRepository;
    private final ClassPlacementRepository classPlacementRepository;
    private final ClassroomService classroomService;
    private final SchoolYearService schoolYearService;
    private final CampusService campusService;
    private final StaffAssignmentService staffAssignmentService;
    private final StaffAccessPolicy staffAccessPolicy;
    private final Clock clock;
    private final TbContactNotifier tbContactNotifier;
    private final ObjectMapper objectMapper;
    private final PlacementRules placementRules;
    private final GuardianViews guardianViews;
    private final ApplicationEventPublisher eventPublisher;

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
            Long childCampusId = currentCampusOf(child, today);
            DuplicateCheckResponse response = inScope(actor, childCampusId)
                    // Mở lại hồ sơ cần quyền tiếp nhận ở điểm trường của trẻ = đúng phạm vi vừa kiểm
                    ? new DuplicateCheckResponse(
                            kind,
                            true,
                            child.getId(),
                            child.getCode(),
                            child.getFullName(),
                            child.getLeftOn(),
                            kind == DuplicateChildKind.LEFT,
                            childCampusId)
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
    public List<PlacementClassResponse> listPlacementClasses(
            CurrentUser actor, Long schoolYearId, LocalDate from, Long childId) {
        String logPrefix = LOG_PREFIX + "|listPlacementClasses|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            SchoolYearDetailResponse requestedYear = schoolYearId == null
                    ? null
                    : schoolYearService
                            .findSchoolYearDetail(schoolYearId)
                            .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
            if (requestedYear != null
                    && requestedYear.status() != SchoolYearStatus.ACTIVE
                    && requestedYear.status() != SchoolYearStatus.PLANNED) {
                throw fieldError("schoolYearId", "Năm học đã kết thúc, không thể tiếp nhận trẻ");
            }
            if (requestedYear != null && requestedYear.status() == SchoolYearStatus.PLANNED) {
                // CHD-11: lớp tiếp nhận của năm sau chỉ mở từ ngày mở chuẩn bị
                plannedTarget(actor, schoolYearId, null);
            }
            List<ClassroomInfoResponse> classes = (schoolYearId == null
                            ? classroomService.findActiveYearClassrooms()
                            : classroomService.findYearClassrooms(schoolYearId))
                    .stream()
                            .filter(c -> c.status() == ClassroomStatus.ACTIVE)
                            .filter(c -> staffAccessPolicy.canEditCampus(
                                            actor, Permission.CHILD_PLACEMENT, c.campusId())
                                    || staffAccessPolicy.canEditCampus(actor, Permission.CHILD_ENROLL, c.campusId()))
                            .toList();
            List<Long> ids = classes.stream().map(ClassroomInfoResponse::id).toList();
            Map<Long, Long> enrolled = requestedYear != null && requestedYear.status() == SchoolYearStatus.PLANNED
                    ? plannedCounts(ids, requestedYear.id())
                    : enrolledCounts(ids, today);
            // CHD-14: sĩ số khớp lúc kiểm xếp lớp (trẻ hẹn vào sau ngày chọn cũng chiếm chỗ); năm sau = số dự kiến
            boolean planned = requestedYear != null && requestedYear.status() == SchoolYearStatus.PLANNED;
            LocalDate occupiedFrom = from == null ? today : from;
            Map<Long, Long> occupied = planned ? enrolled : occupiedCounts(ids, occupiedFrom, childId);
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
                            homeroom.get(c.id()),
                            occupied.getOrDefault(c.id(), 0L)))
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
            SchoolYearDetailResponse chosenYear = request.schoolYearId() == null
                    ? null
                    : schoolYearService
                            .findSchoolYearDetail(request.schoolYearId())
                            .orElseThrow(() -> fieldError("schoolYearId", "Năm học không tồn tại"));
            if (chosenYear != null && chosenYear.status() == SchoolYearStatus.CLOSED) {
                throw fieldError("schoolYearId", "Năm học đã kết thúc, không thể tiếp nhận trẻ");
            }
            // Mở lại cho năm sau: kiểm năm/ngày mở chuẩn bị trước khi đổi hồ sơ (CHD-11, 2412)
            PlannedTarget target = chosenYear != null && chosenYear.status() == SchoolYearStatus.PLANNED
                    ? plannedTarget(actor, request.schoolYearId(), request.startDate())
                    : null;
            // G94-180/CHD-14: mở lại vào năm đang học cũng chọn ngày bắt đầu đi học
            LocalDate startDate = target != null
                    ? target.startDate()
                    : currentYearStart(
                            actor, chosenYear != null ? chosenYear : activeYear(actor), request.startDate(), today);
            // Chọn điểm trường khác điểm trường cũ: kiểm như tiếp nhận (G94-180/CHD-02, user chốt 2026-11-22)
            Long chosen = request.campusId();
            if (chosen != null && !chosen.equals(campusId)) {
                if (campusService.findCampusStatus(chosen).orElse(null) != CampusStatus.ACTIVE) {
                    throw fieldError("campusId", "Điểm trường không tồn tại hoặc đã ngừng hoạt động");
                }
                if (!inScope(actor, chosen)) {
                    throw fieldError("campusId", "Chỉ tiếp nhận trẻ vào điểm trường bạn được giao");
                }
                campusId = chosen;
            }
            requireCampusInYear(
                    actor, campusId, target == null ? null : target.year().id());
            child.readmit(campusId, today);
            child.startOn(startDate);
            childRepository.save(child);
            if (target != null) {
                // Đăng ký chờ xếp lớp ở điểm trường cũ cho năm sau (CHD-03, CHD-11)
                // Unique (trẻ, năm): đã có dòng (đã hủy khi nghỉ) thì mở lại thay vì thêm mới
                PlannedAdmission admission = plannedAdmissionRepository
                        .findByChildIdAndSchoolYearId(childId, target.year().id())
                        .orElse(null);
                if (admission == null) {
                    admission = PlannedAdmission.create(
                            childId, target.year().id(), campusId, null, target.startDate(), actor.userId());
                } else {
                    admission.reopen(campusId, target.startDate(), actor.userId());
                }
                plannedAdmissionRepository.save(admission);
            }
            childHistoryRepository.save(ChildHistory.record(
                    childId, ChildHistoryAction.READMITTED, actor.userId(), reason, clock.instant()));
            // Feature account mở lại tài khoản phụ huynh hệ thống đã khóa khi trẻ nghỉ (sau commit; CHD-04, V90)
            eventPublisher.publishEvent(new ChildReadmittedEvent(childId));
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
            EnrollmentDraft draft = prepare(actor, request, new PlaceLookups());
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
    @Transactional(readOnly = true)
    public List<EnrollmentDraft> prepareEnrollments(CurrentUser actor, List<EnrollChildRequest> requests) {
        String logPrefix = LOG_PREFIX + "|prepareEnrollments|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            PlaceLookups lookups = new PlaceLookups();
            EnrollmentBatchValidator batch = new EnrollmentBatchValidator();
            List<EnrollmentDraft> drafts = requests.stream()
                    .map(request -> {
                        if (request == null) throw fieldError("file", "Dòng dữ liệu không hợp lệ");
                        EnrollmentDraft draft = prepare(actor, request, lookups);
                        batch.check(request);
                        return draft;
                    })
                    .toList();
            log.info("{}|count={}|END", logPrefix, drafts.size());
            return drafts;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private EnrollmentDraft prepare(CurrentUser actor, EnrollChildRequest request, PlaceLookups lookups) {
        LocalDate today = LocalDate.now(clock);
        EnrollmentFormValidator.Result result = EnrollmentFormValidator.validate(request, today);
        if (!result.errors().isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, result.errors());
        }
        EnrollmentDraft draft = placeOf(
                actor,
                request.classId(),
                request.campusId(),
                request.schoolYearId(),
                request.startDate(),
                today,
                result.draft(),
                lookups);
        Set<String> citizenIds =
                draft.guardians().stream().map(GuardianDraft::citizenId).collect(Collectors.toSet());
        // CP-06: hồ sơ đã nghỉ phải mở lại, không tiếp nhận thành hồ sơ mới.
        List<Child> duplicates =
                sameChildren(EnrollmentFormValidator.nameKey(draft.fullName()), draft.dateOfBirth(), citizenIds, null);
        if (duplicates.stream().anyMatch(c -> c.getStatus() != ChildStatus.LEFT)) {
            throw GlobalException.of(ApiCode.CHILD_DUPLICATE);
        }
        if (!duplicates.isEmpty()) {
            throw GlobalException.of(ApiCode.CHILD_DUPLICATE_LEFT);
        }
        return draft;
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
            boolean planned = draft.schoolYearId() != null;
            boolean placed = !planned && draft.classroomId() != null;
            Child enrolled = Child.enroll(
                    nextCode(today),
                    draft.fullName(),
                    draft.dateOfBirth(),
                    draft.gender(),
                    placed,
                    draft.campusId(),
                    today,
                    // CHD-13: cờ báo ăn của trẻ theo khai báo tiếp nhận
                    draft.declaration().mealRegistered());
            // G94-180/CHD-14: năm sau = ngày bắt đầu đã đăng ký; draft dựng tay không có ngày ⇒ hôm nay
            LocalDate startDate = draft.startDate() == null ? today : draft.startDate();
            enrolled.startOn(startDate);
            Child child = childRepository.save(enrolled);
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
            // CHD-07: khai Có tiếp xúc người bệnh lao khi tiếp nhận / nhập Excel
            if (d.tbContactState() == DeclarationState.REPORTED) {
                tbContactNotifier.contactReported(
                        childId, draft.fullName(), draft.campusId(), d.tbContactDate(), actor.userId());
            }
            childHistoryRepository.save(
                    ChildHistory.record(childId, ChildHistoryAction.ENROLLED, actor.userId(), null, now));
            if (placed) {
                // CHD-10, mục 6.2, G94-180/CHD-14: lớp từ ngày bắt đầu đi học, ghi lịch sử xếp lớp, báo GV lớp
                // (tiếp nhận form và nhập Excel)
                // Khóa lớp rồi kiểm lại sĩ số: lần kiểm lúc lập bản nháp có thể đã cũ (Điều 15 Điều lệ)
                classroomService.lockForUpdate(draft.classroomId());
                ClassroomInfoResponse classroom = classroomService
                        .findClassroomInfo(draft.classroomId())
                        .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_NOT_FOUND));
                String problem = placementRules.classProblem(actor, classroom, startDate);
                if (problem != null) {
                    throw fieldError("classId", problem);
                }
                placementRules.startEnrollment(childId, classroom, actor.userId(), startDate);
                placementRules.recordPlacement(
                        childId, draft.fullName(), null, classroom, actor.userId(), null, now, startDate, today);
            } else if (planned) {
                plannedAdmissionRepository.save(PlannedAdmission.create(
                        childId,
                        draft.schoolYearId(),
                        draft.campusId(),
                        draft.classroomId(),
                        draft.startDate(),
                        actor.userId()));
            }

            int linked = 0;
            for (GuardianDraft g : draft.guardians()) {
                Long userId = accountIdByPosition.get(g.position());
                ChildGuardian savedGuardian = ChildGuardian.create(
                        childId, g.position(), g.fullName(), g.relationship(), g.phone(), g.citizenId(), g.email());
                savedGuardian.selectAccountRecipient(g.accountRecipient());
                // Liên kết là nguồn scope của phụ huynh (AUTH-04)
                if (userId != null) {
                    savedGuardian.linkAccount(userId, now);
                    linked++;
                }
                childGuardianRepository.save(savedGuardian);
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
            // G94-180/CHD-14: lớp bắt đầu sau hôm nay; chưa có lớp nào bắt đầu thì lớp hiển thị là lớp sắp vào
            Enrollment upcoming = enrollmentRepository.findStartingAfter(List.of(childId), today).stream()
                    .findFirst()
                    .orElse(null);
            ClassroomInfoResponse classroom = activeClassroomId(childId, today)
                    .or(() -> Optional.ofNullable(upcoming).map(Enrollment::getClassroomId))
                    .flatMap(classroomService::findClassroomInfo)
                    .orElse(null);
            ChildNextClassResponse nextClass = upcoming == null
                    ? null
                    : new ChildNextClassResponse(
                            upcoming.getClassroomId(),
                            classroom != null && classroom.id().equals(upcoming.getClassroomId())
                                    ? classroom.name()
                                    : classroomService
                                            .findClassroomInfo(upcoming.getClassroomId())
                                            .map(ClassroomInfoResponse::name)
                                            .orElse(null),
                            upcoming.getValidFrom());
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
                    child.isMealRegistered(),
                    profile,
                    guardiansOf(childId),
                    historyOf(childId),
                    child.getPhotoKey() == null ? null : "/children/" + childId + "/photo",
                    findOpenPlannedAdmissions(List.of(childId)).get(childId),
                    child.getStartDate(),
                    child.isStartDue(today),
                    nextClass,
                    child.getScheduledLeaveOn(),
                    child.getScheduledLeaveReason());
            log.info("{}|END", logPrefix);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ChildNextClassResponse> findNextClasses(Collection<Long> childIds) {
        if (childIds.isEmpty()) {
            return Map.of();
        }
        LocalDate today = LocalDate.now(clock);
        Map<Long, String> classNames = new HashMap<>();
        Map<Long, ChildNextClassResponse> result = new HashMap<>();
        for (Enrollment e : enrollmentRepository.findStartingAfter(childIds, today)) {
            String className = classNames.computeIfAbsent(
                    e.getClassroomId(),
                    id -> classroomService
                            .findClassroomInfo(id)
                            .map(ClassroomInfoResponse::name)
                            .orElse(null));
            result.putIfAbsent(
                    e.getChildId(), new ChildNextClassResponse(e.getClassroomId(), className, e.getValidFrom()));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, PlannedAdmissionResponse> findOpenPlannedAdmissions(Collection<Long> childIds) {
        if (childIds.isEmpty()) {
            return Map.of();
        }
        // CHD-11: chỉ đăng ký còn mở; một trẻ có tối đa một đăng ký mở
        List<PlannedAdmission> open = plannedAdmissionRepository.findByChildIdInAndStatusIn(
                childIds, List.of(PlannedAdmissionStatus.PENDING, PlannedAdmissionStatus.PLACED));
        Map<Long, String> yearNames = new HashMap<>();
        Map<Long, String> classNames = new HashMap<>();
        Map<Long, PlannedAdmissionResponse> result = new HashMap<>();
        for (PlannedAdmission pa : open) {
            String yearName = yearNames.computeIfAbsent(
                    pa.getSchoolYearId(),
                    id -> schoolYearService
                            .findSchoolYearDetail(id)
                            .map(SchoolYearDetailResponse::name)
                            .orElse(null));
            String className = pa.getClassroomId() == null
                    ? null
                    : classNames.computeIfAbsent(
                            pa.getClassroomId(),
                            id -> classroomService
                                    .findClassroomInfo(id)
                                    .map(ClassroomInfoResponse::name)
                                    .orElse(null));
            result.put(
                    pa.getChildId(),
                    new PlannedAdmissionResponse(
                            pa.getSchoolYearId(),
                            yearName,
                            pa.getCampusId(),
                            pa.getClassroomId(),
                            className,
                            pa.getStartDate(),
                            pa.getStatus()));
        }
        return result;
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
    private record YearStart(Long schoolYearId, LocalDate startDate) {}

    private record IntakeTarget(SchoolYearDetailResponse year, boolean planned, LocalDate startDate) {}

    private record CampusYear(Long campusId, Long schoolYearId) {}

    private static final class PlaceLookups {
        private final Map<YearStart, IntakeTarget> targets = new HashMap<>();
        private final Set<CampusYear> campuses = new HashSet<>();
        private final Map<Long, Boolean> editableCampuses = new HashMap<>();
        private Boolean wholeSchool;
        private Set<Long> managed;
    }

    private IntakeTarget intakeTarget(
            CurrentUser actor, Long schoolYearId, LocalDate requestedStartDate, LocalDate today) {
        SchoolYearDetailResponse targetYear = schoolYearId == null
                ? null
                : schoolYearService
                        .findSchoolYearDetail(schoolYearId)
                        .orElseThrow(() -> fieldError("schoolYearId", "Năm học không tồn tại"));
        if (targetYear != null && targetYear.status() == SchoolYearStatus.CLOSED) {
            throw fieldError("schoolYearId", "Năm học đã kết thúc, không thể tiếp nhận trẻ");
        }
        boolean planned = targetYear != null && targetYear.status() == SchoolYearStatus.PLANNED;
        if (planned) {
            PlannedTarget target = plannedTarget(actor, targetYear, requestedStartDate);
            return new IntakeTarget(target.year(), true, target.startDate());
        }
        return new IntakeTarget(
                targetYear,
                false,
                currentYearStart(
                        actor, targetYear != null ? targetYear : activeYear(actor), requestedStartDate, today));
    }

    private EnrollmentDraft placeOf(
            CurrentUser actor,
            Long classId,
            Long campusId,
            Long schoolYearId,
            LocalDate requestedStartDate,
            LocalDate today,
            EnrollmentDraft draft,
            PlaceLookups lookups) {
        if (lookups.wholeSchool == null) {
            lookups.wholeSchool = staffAccessPolicy.managesWholeSchool(actor);
            lookups.managed = lookups.wholeSchool ? Set.of() : staffAccessPolicy.managedCampusIds(actor);
        }
        Set<Long> managed = lookups.managed;
        IntakeTarget intake = lookups.targets.computeIfAbsent(
                new YearStart(schoolYearId, requestedStartDate),
                key -> intakeTarget(actor, key.schoolYearId(), key.startDate(), today));
        SchoolYearDetailResponse targetYear = intake.year();
        boolean planned = intake.planned();
        LocalDate startDate = intake.startDate();
        if (classId != null) {
            ClassroomInfoResponse classroom =
                    classroomService.findClassroomInfo(classId).orElse(null);
            // Sĩ số tính tại ngày trẻ vào lớp (G94-180/CHD-14). Nhóm tuổi chỉ gợi ý: lớp khác nhóm tuổi của trẻ vẫn
            // nhận, cả năm sau (G94-180/CHD-13, user chốt 2026-11-22)
            String problem = planned
                    ? plannedClassProblem(actor, classroom, targetYear.id())
                    : placementRules.classProblem(actor, classroom, startDate, null, Permission.CHILD_ENROLL);
            if (problem != null) {
                throw fieldError("classId", problem);
            }
            if (campusId != null && !campusId.equals(classroom.campusId())) {
                throw fieldError("classId", "Lớp không thuộc điểm trường đã chọn");
            }
            EnrollmentDraft placed = draft.withPlacement(classroom.id(), classroom.campusId());
            return planned ? placed.withAdmission(targetYear.id(), startDate) : placed.withStartDate(startDate);
        }
        Long pinnedCampus = actor.pinnedCampusId(Permission.CHILD_ENROLL);
        Long target = campusId != null
                ? campusId
                : pinnedCampus != null
                        ? pinnedCampus
                        : (managed.size() == 1 ? managed.iterator().next() : null);
        if (target == null) {
            throw fieldError("campusId", "Chọn điểm trường tiếp nhận trẻ");
        }
        CampusYear campusKey = new CampusYear(target, targetYear == null ? null : targetYear.id());
        if (!lookups.campuses.contains(campusKey)
                && campusService.findCampusStatus(target).orElse(null) != CampusStatus.ACTIVE) {
            throw fieldError("campusId", "Điểm trường không tồn tại hoặc đã ngừng hoạt động");
        }
        if (!lookups.editableCampuses.computeIfAbsent(
                target, id -> staffAccessPolicy.canEditCampus(actor, Permission.CHILD_ENROLL, id))) {
            throw fieldError("campusId", "Chỉ tiếp nhận trẻ vào điểm trường bạn được giao");
        }
        if (!lookups.campuses.contains(campusKey)) {
            requireCampusInYear(actor, target, campusKey.schoolYearId());
            lookups.campuses.add(campusKey);
        }
        EnrollmentDraft pending = draft.withPlacement(null, target);
        return planned ? pending.withAdmission(targetYear.id(), startDate) : pending.withStartDate(startDate);
    }

    /** Năm học đang hoạt động: tiếp nhận vào năm đang học cần ngày kết thúc năm (G94-180/CHD-14). */
    private SchoolYearDetailResponse activeYear(CurrentUser actor) {
        return schoolYearService.getSchoolYears(actor, SchoolYearStatus.ACTIVE).stream()
                .findFirst()
                .orElseThrow(() -> fieldError("schoolYearId", "Chưa có năm học đang hoạt động"));
    }

    /**
     * G94-180/CHD-14: tiếp nhận / mở lại vào năm đang học phải chọn ngày bắt đầu đi học, từ hôm nay tới ngày kết thúc
     * năm và là ngày học. Lớp (nếu có) tính từ ngày này.
     */
    private LocalDate currentYearStart(
            CurrentUser actor, SchoolYearDetailResponse year, LocalDate requested, LocalDate today) {
        if (requested == null) {
            throw fieldError("startDate", "Chọn ngày bắt đầu đi học");
        }
        if (requested.isBefore(today)) {
            throw fieldError("startDate", "Ngày bắt đầu đi học không được trước hôm nay");
        }
        if (requested.isAfter(year.endDate())) {
            throw fieldError(
                    "startDate",
                    "Ngày bắt đầu đi học không được sau ngày kết thúc năm học (" + DISPLAY_DATE.format(year.endDate())
                            + ")");
        }
        requireSchoolDay(actor, year.id(), requested);
        return requested;
    }

    // G94-180/CHD-14: nói rõ ngày bắt đầu là cuối tuần hay trùng ngày nghỉ nào của năm đó
    private void requireSchoolDay(CurrentUser actor, Long schoolYearId, LocalDate startDate) {
        if (!schoolYearService.isSchoolDay(startDate)) {
            throw fieldError(
                    "startDate",
                    SchoolDayMessages.explain(
                            START_DATE_LABEL, startDate, schoolYearService.getHolidays(actor, schoolYearId)));
        }
    }

    /** Ngày học đầu tiên từ {@code from} (thứ 7, chủ nhật, ngày nghỉ bỏ qua); không có trong năm ⇒ {@code from}. */
    private LocalDate firstSchoolDay(LocalDate from, LocalDate yearEnd) {
        for (LocalDate d = from; !d.isAfter(yearEnd); d = d.plusDays(1)) {
            if (schoolYearService.isSchoolDay(d)) {
                return d;
            }
        }
        return from;
    }

    /** Năm sắp diễn ra được tiếp nhận (CHD-11) và ngày bắt đầu học của trẻ trong năm đó. */
    private record PlannedTarget(SchoolYearDetailResponse year, LocalDate startDate) {}

    /**
     * Năm sắp diễn ra gần nhất, chỉ nhận trẻ từ ngày mở chuẩn bị (CHD-11, CFG-YR-05; trước đó 2412). Ngày bắt đầu học
     * phải nằm trong năm học, để trống = ngày bắt đầu năm.
     */
    private PlannedTarget plannedTarget(CurrentUser actor, Long schoolYearId, LocalDate requestedStartDate) {
        SchoolYearDetailResponse year = schoolYearService
                .findSchoolYearDetail(schoolYearId)
                .orElseThrow(() -> fieldError("schoolYearId", "Năm học không tồn tại"));
        return plannedTarget(actor, year, requestedStartDate);
    }

    private PlannedTarget plannedTarget(
            CurrentUser actor, SchoolYearDetailResponse year, LocalDate requestedStartDate) {
        SchoolYearDetailResponse next = schoolYearService.findNextPlannedYear();
        if (next == null || !next.id().equals(year.id())) {
            throw fieldError("schoolYearId", "Chỉ được đăng ký cho năm học sắp diễn ra gần nhất");
        }
        if (!year.preparing()) {
            throw GlobalException.of(ApiCode.PREP_YEAR_NOT_OPEN, year.name(), DISPLAY_DATE.format(year.prepDate()));
        }
        // G94-180/CHD-14: để trống = ngày học đầu tiên của năm (thường là ngày bắt đầu năm HT cấu hình)
        LocalDate startDate =
                requestedStartDate == null ? firstSchoolDay(year.startDate(), year.endDate()) : requestedStartDate;
        if (startDate.isBefore(year.startDate()) || startDate.isAfter(year.endDate())) {
            throw fieldError("startDate", "Ngày bắt đầu phải nằm trong thời gian năm học đã chọn");
        }
        requireSchoolDay(actor, year.id(), startDate);
        return new PlannedTarget(year, startDate);
    }

    private String plannedClassProblem(CurrentUser actor, ClassroomInfoResponse classroom, Long schoolYearId) {
        if (classroom == null || classroom.status() != ClassroomStatus.ACTIVE) {
            return "Lớp không tồn tại hoặc đã ngừng hoạt động";
        }
        if (!schoolYearId.equals(classroom.schoolYearId()) || classroom.yearStatus() != SchoolYearStatus.PLANNED) {
            return "Lớp không thuộc năm học sắp diễn ra đã chọn";
        }
        if (!staffAccessPolicy.canEditCampus(actor, Permission.CHILD_ENROLL, classroom.campusId())) {
            return "Chỉ xếp được vào lớp thuộc điểm trường bạn được giao";
        }
        long taken = plannedCount(classroom.id(), schoolYearId);
        if (classroom.capacity() != null && taken >= classroom.capacity()) {
            return classroom.name() + " đã đủ " + classroom.capacity() + " trẻ. Chọn lớp khác.";
        }
        return null;
    }

    private Map<Long, Long> plannedCounts(List<Long> classroomIds, Long schoolYearId) {
        Map<Long, Long> counts = new HashMap<>();
        classroomIds.forEach(id -> counts.put(id, plannedCount(id, schoolYearId)));
        return counts;
    }

    private long plannedCount(Long classroomId, Long schoolYearId) {
        long newChildren = plannedAdmissionRepository.countByClassroomIdAndStatusIn(
                classroomId, List.of(PlannedAdmissionStatus.PLACED));
        long continuing = classPlacementRepository.findAllBySchoolYearId(schoolYearId).stream()
                .filter(p -> p.getAction().keepsChildInClass() && classroomId.equals(p.getToClassroomId()))
                .count();
        return newChildren + continuing;
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
        return staffAccessPolicy.canEditCampus(actor, Permission.CHILD_ENROLL, campusId);
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

    private Map<Long, Long> occupiedCounts(List<Long> classroomIds, LocalDate from, Long childId) {
        Map<Long, Long> counts = new HashMap<>();
        classroomIds.forEach(id -> counts.put(id, 0L));
        if (classroomIds.isEmpty()) {
            return counts;
        }
        // Một truy vấn cho mọi lớp (tránh N+1 khi mở danh sách lớp xếp trẻ)
        long exclude = childId == null ? -1L : childId;
        for (Object[] row : enrollmentRepository.countOccupyingFromByClassroomIds(classroomIds, from, exclude)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Map<Long, Long> enrolledCounts(List<Long> classroomIds, LocalDate today) {
        if (classroomIds.isEmpty()) {
            return Map.of();
        }
        return enrollmentRepository.findActiveByClassroomIds(classroomIds, today).stream()
                .collect(Collectors.groupingBy(Enrollment::getClassroomId, Collectors.counting()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findRecipientAccountId(Long childId) {
        String logPrefix = LOG_PREFIX + "|findRecipientAccountId|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            Optional<Long> result = childGuardianRepository.findAllByChildIdOrderByPositionAsc(childId).stream()
                    .filter(ChildGuardian::isAccountRecipient)
                    .map(ChildGuardian::getUserId)
                    .filter(java.util.Objects::nonNull)
                    .findFirst();
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Map<Long, Long> findRecipientAccountIds(Collection<Long> childIds) {
        String logPrefix = LOG_PREFIX + "|findRecipientAccountIds|children=" + childIds.size();
        log.info("{}|START", logPrefix);
        try {
            Map<Long, Long> result = new HashMap<>();
            if (!childIds.isEmpty()) {
                childGuardianRepository.findAllByChildIdInOrderByChildIdAscPositionAsc(childIds).stream()
                        .filter(g -> g.isAccountRecipient() && g.getUserId() != null)
                        .forEach(g -> result.putIfAbsent(g.getChildId(), g.getUserId()));
            }
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChildGuardianResponse> accountCandidates(Long childId) {
        String logPrefix = LOG_PREFIX + "|accountCandidates|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            List<ChildGuardianResponse> result = guardiansOf(childId);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void selectAccountRecipient(Long childId, Long guardianId, Long actorUserId) {
        String logPrefix = LOG_PREFIX + "|selectAccountRecipient|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            List<ChildGuardian> rows = childGuardianRepository.lockByChildId(childId);
            ChildGuardian chosen = rows.stream()
                    .filter(g -> g.getId().equals(guardianId))
                    .findFirst()
                    .orElseThrow(() -> GlobalException.of(ApiCode.INVALID_REQUEST_DATA));
            if (rows.stream()
                    .anyMatch(g -> g.isAccountRecipient()
                            && g.getUserId() != null
                            && !g.getId().equals(guardianId))) {
                throw GlobalException.of(ApiCode.GUARDIAN_IDENTITY_CONFLICT);
            }
            ChildGuardian previous = rows.stream()
                    .filter(ChildGuardian::isAccountRecipient)
                    .findFirst()
                    .orElse(null);
            if (previous != null && previous.getId().equals(guardianId)) {
                log.info("{}|END", logPrefix);
                return;
            }
            rows.forEach(g -> g.selectAccountRecipient(false));
            childGuardianRepository.flush();
            chosen.selectAccountRecipient(true);
            childHistoryRepository.save(ChildHistory.record(
                    childId,
                    ChildHistoryAction.UPDATED,
                    actorUserId,
                    "Chọn phụ huynh nhận tài khoản: " + chosen.getFullName(),
                    clock.instant()));
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void linkAccountRecipient(Long childId, Long guardianId, Long userId) {
        String logPrefix = LOG_PREFIX + "|linkAccountRecipient|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            ChildGuardian chosen = childGuardianRepository.lockByChildId(childId).stream()
                    .filter(g -> g.isAccountRecipient() && g.getId().equals(guardianId))
                    .findFirst()
                    .orElseThrow(() -> GlobalException.of(ApiCode.GUARDIAN_IDENTITY_CONFLICT));
            if (chosen.getUserId() != null && !chosen.getUserId().equals(userId)) {
                throw GlobalException.of(ApiCode.GUARDIAN_IDENTITY_CONFLICT);
            }
            chosen.linkAccount(userId, clock.instant());
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private List<ChildGuardianResponse> guardiansOf(Long childId) {
        return guardianViews.of(childId);
    }

    private static String guardianPhotoUrl(ChildGuardian guardian) {
        return guardian.getPhotoKey() == null
                ? null
                : "/children/" + guardian.getChildId() + "/guardian-photos/" + guardian.getId();
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

    @Override
    @Transactional(readOnly = true)
    public List<ChildGuardianContact> findGuardianContacts(Long childId) {
        String logPrefix = LOG_PREFIX + "|findGuardianContacts|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            List<ChildGuardianContact> contacts =
                    childGuardianRepository.findAllByChildIdOrderByPositionAsc(childId).stream()
                            .map(g -> new ChildGuardianContact(
                                    g.getPosition(),
                                    g.getFullName(),
                                    g.getRelationship(),
                                    g.getPhone(),
                                    g.getUserId(),
                                    guardianPhotoUrl(g)))
                            .toList();
            log.info("{}|END", logPrefix);
            return contacts;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChildMedicalHistory> findMedicalHistory(Long childId) {
        String logPrefix = LOG_PREFIX + "|findMedicalHistory|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            Optional<ChildMedicalHistory> history = declarationRepository
                    .findById(childId)
                    .map(d -> new ChildMedicalHistory(
                            d.getSelfHistoryState(),
                            d.getSelfHistoryText(),
                            d.getFamilyHistoryState(),
                            d.getFamilyHistoryText(),
                            d.getTbContactState(),
                            d.getTbContactDate()));
            log.info("{}|END", logPrefix);
            return history;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void updateMedicalHistory(
            Long childId,
            ChildMedicalHistory history,
            HealthDeclarationAction action,
            String note,
            String changeSource,
            Long userId,
            Instant at) {
        String logPrefix = LOG_PREFIX + "|updateMedicalHistory|childId=" + childId + "|action=" + action;
        log.info("{}|START", logPrefix);
        try {
            ChildHealthDeclaration.Answer self = new ChildHealthDeclaration.Answer(history.self(), history.selfText());
            ChildHealthDeclaration.Answer family =
                    new ChildHealthDeclaration.Answer(history.family(), history.familyText());
            Optional<ChildHealthDeclaration> existing = declarationRepository.findById(childId);
            HealthDeclarationResponse.DeclarationMedicalHistory before =
                    existing.map(ChildIntakeServiceImpl::medicalHistoryView).orElse(null);
            LocalDate beforeTbDate =
                    existing.map(ChildHealthDeclaration::getTbContactDate).orElse(null);
            if (existing.isPresent()) {
                existing.get().updateMedicalHistory(self, family, history.tbContact(), history.tbContactDate());
            } else {
                // Trẻ chưa có khai báo: các mục ngoài tiền sử là "chưa cung cấp"
                ChildHealthDeclaration.Answer notProvided =
                        new ChildHealthDeclaration.Answer(DeclarationState.NOT_PROVIDED, null);
                declarationRepository.save(ChildHealthDeclaration.declare(
                        childId,
                        DeclarationState.NOT_PROVIDED,
                        notProvided,
                        notProvided,
                        self,
                        family,
                        history.tbContact(),
                        history.tbContactDate(),
                        userId,
                        at));
            }
            HealthDeclarationResponse.DeclarationMedicalHistory after =
                    new HealthDeclarationResponse.DeclarationMedicalHistory(
                            new HealthDeclarationResponse.DeclarationAnswer(history.self(), history.selfText()),
                            new HealthDeclarationResponse.DeclarationAnswer(history.family(), history.familyText()),
                            new HealthDeclarationResponse.DeclarationTbContact(
                                    history.tbContact(), history.tbContactDate()));
            String historyChange = objectMapper.writeValueAsString(
                    new HealthDeclarationResponse.DeclarationHistoryChange(before, after));
            declarationHistoryRepository.save(
                    ChildHealthDeclarationHistory.record(childId, action, userId, note, null, historyChange, at));
            // CHD-07: ngày tiếp xúc lao đã có đổi (từ phiếu khám) ⇒ báo HT, PHT điểm trường, người lưu
            if (history.tbContact() == DeclarationState.REPORTED
                    && beforeTbDate != null
                    && history.tbContactDate() != null
                    && !beforeTbDate.equals(history.tbContactDate())) {
                Child child = childRepository
                        .findById(childId)
                        .orElseThrow(() -> GlobalException.of(ApiCode.CHILD_NOT_FOUND));
                tbContactNotifier.contactDateChanged(
                        childId,
                        child.getFullName(),
                        currentCampusOf(child, LocalDate.now(clock)),
                        beforeTbDate,
                        history.tbContactDate(),
                        changeSource,
                        userId);
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static HealthDeclarationResponse.DeclarationMedicalHistory medicalHistoryView(ChildHealthDeclaration d) {
        return new HealthDeclarationResponse.DeclarationMedicalHistory(
                new HealthDeclarationResponse.DeclarationAnswer(d.getSelfHistoryState(), d.getSelfHistoryText()),
                new HealthDeclarationResponse.DeclarationAnswer(d.getFamilyHistoryState(), d.getFamilyHistoryText()),
                new HealthDeclarationResponse.DeclarationTbContact(d.getTbContactState(), d.getTbContactDate()));
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

    /**
     * G94-180/CHD-02 (user chốt 2026-11-22): điểm trường tiếp nhận phải có trong danh sách điểm trường của năm (V151).
     * schoolYearId null = năm đang hoạt động. Danh sách rỗng (năm chưa có danh sách riêng) ⇒ không chặn.
     */
    private void requireCampusInYear(CurrentUser actor, Long campusId, Long schoolYearId) {
        List<CampusResponse> inYear = campusService.getCampuses(actor, schoolYearId);
        if (!inYear.isEmpty() && inYear.stream().noneMatch(c -> campusId.equals(c.id()))) {
            throw GlobalException.withFieldErrors(
                    ApiCode.CAMPUS_NOT_IN_YEAR,
                    List.of(new FieldErrorResponse(
                            "campusId", "Điểm trường không có trong năm học đã chọn. Chọn điểm trường khác")));
        }
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
