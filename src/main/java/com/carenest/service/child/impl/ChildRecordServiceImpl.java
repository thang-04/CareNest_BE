package com.carenest.service.child.impl;

import com.carenest.dto.child.ChildClassResponse;
import com.carenest.dto.child.ChildEditDraft;
import com.carenest.dto.child.ChildEditDraft.GuardianEdit;
import com.carenest.dto.child.ChildFieldChange;
import com.carenest.dto.child.ChildPlacementResponse;
import com.carenest.dto.child.ChildProfileDraft;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.EnrollmentDraft;
import com.carenest.dto.child.GuardianDraft;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.child.GuardianPhoneTarget;
import com.carenest.dto.child.HealthDeclarationDraft;
import com.carenest.dto.child.HealthDeclarationRequest;
import com.carenest.dto.child.HealthDeclarationResponse;
import com.carenest.dto.child.PlaceChildRequest;
import com.carenest.dto.child.UpdateChildRequest;
import com.carenest.dto.child.WithdrawChildRequest;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.child.Child;
import com.carenest.entity.child.ChildAllergy;
import com.carenest.entity.child.ChildGuardian;
import com.carenest.entity.child.ChildHealthDeclaration;
import com.carenest.entity.child.ChildHealthDeclarationHistory;
import com.carenest.entity.child.ChildHistory;
import com.carenest.entity.child.ChildPlacement;
import com.carenest.entity.child.ChildProfile;
import com.carenest.entity.child.Enrollment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.ChildAllergyRepository;
import com.carenest.repository.child.ChildGuardianRepository;
import com.carenest.repository.child.ChildHealthDeclarationHistoryRepository;
import com.carenest.repository.child.ChildHealthDeclarationRepository;
import com.carenest.repository.child.ChildHistoryRepository;
import com.carenest.repository.child.ChildPlacementRepository;
import com.carenest.repository.child.ChildProfileRepository;
import com.carenest.repository.child.ChildRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildRecordService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.child.YearTransitionService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ChildEditRoles;
import com.carenest.utils.enums.ChildHistoryAction;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.DeclarationState;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.HealthDeclarationAction;
import com.carenest.utils.enums.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Không log họ tên, ngày sinh, SĐT, CCCD — chỉ id, số lượng. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildRecordServiceImpl implements ChildRecordService {
    private static final String LOG_PREFIX = "[ChildRecordServiceImpl]";
    private static final int MAX_REASON = 300;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ChildRepository childRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ChildGuardianRepository childGuardianRepository;
    private final ChildHistoryRepository childHistoryRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ChildIntakeService childIntakeService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final ClassroomService classroomService;
    private final StaffAssignmentService staffAssignmentService;
    private final StaffAccessPolicy staffAccessPolicy;
    private final ChildAccessPolicy childAccessPolicy;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ChildHealthDeclarationRepository declarationRepository;
    private final ChildAllergyRepository allergyRepository;
    private final ChildHealthDeclarationHistoryRepository declarationHistoryRepository;
    private final TbContactNotifier tbContactNotifier;
    private final ChildPlacementRepository placementRepository;
    private final PlacementRules placementRules;
    private final com.carenest.repository.child.PlannedAdmissionRepository plannedAdmissionRepository;
    private final List<com.carenest.service.child.ChildDayUsageCheck> dayUsageChecks;
    private final SchoolYearService schoolYearService;

    @Override
    @Transactional(readOnly = true)
    public HealthDeclarationResponse findDeclaration(Long childId) {
        String logPrefix = LOG_PREFIX + "|findDeclaration|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            Optional<ChildHealthDeclaration> found = declarationRepository.findById(childId);
            if (found.isEmpty()) {
                log.info("{}|found=false|END", logPrefix);
                return null;
            }
            ChildHealthDeclaration d = found.get();
            List<ChildHealthDeclarationHistory> entries =
                    declarationHistoryRepository.findAllByChildIdOrderByCreatedAtAscIdAsc(childId);
            Set<Long> userIds = new HashSet<>();
            userIds.add(d.getDeclaredBy());
            entries.forEach(h -> userIds.add(h.getUserId()));
            Map<Long, String> names = childHistoryRepository.findUserNames(userIds).stream()
                    .collect(Collectors.toMap(
                            ChildHistoryRepository.UserName::getUserId, ChildHistoryRepository.UserName::getFullName));
            List<HealthDeclarationResponse.DeclarationHistoryEntry> history = entries.stream()
                    .map(h -> new HealthDeclarationResponse.DeclarationHistoryEntry(
                            h.getAction(),
                            h.getUserId(),
                            names.get(h.getUserId()),
                            h.getCreatedAt(),
                            h.getNote(),
                            read(h.getAllergyChange(), HealthDeclarationResponse.DeclarationAllergyChange.class),
                            read(h.getHistoryChange(), HealthDeclarationResponse.DeclarationHistoryChange.class)))
                    .toList();
            HealthDeclarationResponse response = new HealthDeclarationResponse(
                    childId,
                    new HealthDeclarationResponse.DeclarationAllergies(d.getAllergiesState(), allergyNames(childId)),
                    new HealthDeclarationResponse.DeclarationAnswer(d.getDietState(), d.getDietText()),
                    new HealthDeclarationResponse.DeclarationAnswer(d.getOtherNotesState(), d.getOtherNotesText()),
                    medicalHistoryOf(d),
                    d.getDeclaredBy(),
                    names.get(d.getDeclaredBy()),
                    d.getDeclaredAt(),
                    history);
            log.info("{}|history={}|END", logPrefix, history.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public HealthDeclarationResponse saveDeclaration(
            CurrentUser actor, Long childId, HealthDeclarationRequest request) {
        String logPrefix = LOG_PREFIX + "|saveDeclaration|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            // CHD-11: GV không ghi thẳng, gửi yêu cầu qua ChildProfileChangeRequestService
            if (ChildEditRoles.teacherOnly(actor)) {
                throw GlobalException.of(ApiCode.CHILD_PROFILE_CHANGE_NEEDS_APPROVAL);
            }
            LocalDate today = LocalDate.now(clock);
            Instant now = clock.instant();
            ValidDeclaration valid = validateDeclaration(actor, childId, request, today);
            Child child = valid.child();
            HealthDeclarationDraft d = valid.draft();
            Optional<ChildHealthDeclaration> existing = valid.existing();
            String reason = valid.reason();

            HealthDeclarationResponse.DeclarationAllergies beforeAllergies = existing.map(
                            e -> new HealthDeclarationResponse.DeclarationAllergies(
                                    e.getAllergiesState(), allergyNames(childId)))
                    .orElse(null);
            HealthDeclarationResponse.DeclarationMedicalHistory beforeHistory =
                    existing.map(ChildRecordServiceImpl::medicalHistoryOf).orElse(null);
            DeclarationState beforeTb =
                    existing.map(ChildHealthDeclaration::getTbContactState).orElse(null);
            LocalDate beforeTbDate =
                    existing.map(ChildHealthDeclaration::getTbContactDate).orElse(null);

            // CHD-13: không báo ăn ⇒ giữ dị ứng, chế độ ăn đã khai; chưa có khai báo thì dùng giá trị của form
            boolean keepStored = !d.mealRegistered() && existing.isPresent();
            DeclarationState allergiesState = keepStored ? existing.get().getAllergiesState() : d.allergiesState();
            List<String> allergyItems = keepStored ? allergyNames(childId) : d.allergies();
            ChildHealthDeclaration.Answer diet = keepStored
                    ? new ChildHealthDeclaration.Answer(
                            existing.get().getDietState(), existing.get().getDietText())
                    : answer(d.diet());
            ChildHealthDeclaration.Answer otherNotes = answer(d.otherNotes());
            ChildHealthDeclaration.Answer self = answer(d.selfHistory());
            ChildHealthDeclaration.Answer family = answer(d.familyHistory());
            if (existing.isPresent()) {
                existing.get()
                        .update(
                                allergiesState,
                                diet,
                                otherNotes,
                                self,
                                family,
                                d.tbContactState(),
                                d.tbContactDate(),
                                actor.userId(),
                                now);
            } else {
                declarationRepository.save(ChildHealthDeclaration.declare(
                        childId,
                        allergiesState,
                        diet,
                        otherNotes,
                        self,
                        family,
                        d.tbContactState(),
                        d.tbContactDate(),
                        actor.userId(),
                        now));
            }
            // CHD-06: dị ứng có hiệu lực ngay cho bếp, điểm danh (đọc child_allergies)
            if (!keepStored) {
                allergyRepository.deleteByChildId(childId);
                allergyRepository.saveAll(d.allergies().stream()
                        .map(name -> ChildAllergy.of(childId, name))
                        .toList());
            }
            // CHD-13: đổi "Báo ăn" ghi vào lịch sử hồ sơ (lịch sử khai báo chỉ có dị ứng, tiền sử)
            List<ChildFieldChange> mealChange = new ArrayList<>();
            change(mealChange, "mealRegistered", "Báo ăn", yesNo(child.isMealRegistered()), yesNo(d.mealRegistered()));
            child.changeMealRegistration(d.mealRegistered());
            if (!mealChange.isEmpty()) {
                childHistoryRepository.save(ChildHistory.record(
                        childId,
                        ChildHistoryAction.UPDATED,
                        actor.userId(),
                        reason.isEmpty() ? null : reason,
                        objectMapper.writeValueAsString(mealChange),
                        now));
            }

            HealthDeclarationResponse.DeclarationAllergies afterAllergies =
                    new HealthDeclarationResponse.DeclarationAllergies(allergiesState, allergyItems);
            HealthDeclarationResponse.DeclarationMedicalHistory afterHistory = medicalHistoryOf(d);
            String allergyChange = Objects.equals(beforeAllergies, afterAllergies)
                    ? null
                    : objectMapper.writeValueAsString(
                            new HealthDeclarationResponse.DeclarationAllergyChange(beforeAllergies, afterAllergies));
            String historyChange = Objects.equals(beforeHistory, afterHistory)
                    ? null
                    : objectMapper.writeValueAsString(
                            new HealthDeclarationResponse.DeclarationHistoryChange(beforeHistory, afterHistory));
            declarationHistoryRepository.save(ChildHealthDeclarationHistory.record(
                    childId,
                    existing.isPresent() ? HealthDeclarationAction.UPDATED : HealthDeclarationAction.DECLARED,
                    actor.userId(),
                    reason.isEmpty() ? null : reason,
                    allergyChange,
                    historyChange,
                    now));

            // CHD-07: chuyển sang Có ⇒ báo; đã Có mà đổi ngày ⇒ báo đổi ngày; lưu lại không đổi ⇒ không báo
            Long campusId = campusOf(child, today);
            if (d.tbContactState() == DeclarationState.REPORTED && beforeTb != DeclarationState.REPORTED) {
                tbContactNotifier.contactReported(
                        childId, child.getFullName(), campusId, d.tbContactDate(), actor.userId());
            } else if (d.tbContactState() == DeclarationState.REPORTED
                    && beforeTbDate != null
                    && !beforeTbDate.equals(d.tbContactDate())) {
                tbContactNotifier.contactDateChanged(
                        childId,
                        child.getFullName(),
                        campusId,
                        beforeTbDate,
                        d.tbContactDate(),
                        "sửa khai báo ăn và sức khỏe",
                        actor.userId());
            }
            HealthDeclarationResponse response = findDeclaration(childId);
            log.info("{}|existing={}|END", logPrefix, existing.isPresent());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private List<String> allergyNames(Long childId) {
        return allergyRepository.findAllByChildIdOrderByIdAsc(childId).stream()
                .map(ChildAllergy::getName)
                .toList();
    }

    private static HealthDeclarationResponse.DeclarationMedicalHistory medicalHistoryOf(ChildHealthDeclaration d) {
        return new HealthDeclarationResponse.DeclarationMedicalHistory(
                new HealthDeclarationResponse.DeclarationAnswer(d.getSelfHistoryState(), d.getSelfHistoryText()),
                new HealthDeclarationResponse.DeclarationAnswer(d.getFamilyHistoryState(), d.getFamilyHistoryText()),
                new HealthDeclarationResponse.DeclarationTbContact(d.getTbContactState(), d.getTbContactDate()));
    }

    private static HealthDeclarationResponse.DeclarationMedicalHistory medicalHistoryOf(HealthDeclarationDraft d) {
        return new HealthDeclarationResponse.DeclarationMedicalHistory(
                new HealthDeclarationResponse.DeclarationAnswer(
                        d.selfHistory().state(), d.selfHistory().text()),
                new HealthDeclarationResponse.DeclarationAnswer(
                        d.familyHistory().state(), d.familyHistory().text()),
                new HealthDeclarationResponse.DeclarationTbContact(d.tbContactState(), d.tbContactDate()));
    }

    private static ChildHealthDeclaration.Answer answer(HealthDeclarationDraft.Answer a) {
        return new ChildHealthDeclaration.Answer(a.state(), a.text());
    }

    private <T> T read(String json, Class<T> type) {
        return json == null ? null : objectMapper.readValue(json, type);
    }

    @Override
    @Transactional
    public ChildViewResponse place(CurrentUser actor, Long childId, PlaceChildRequest request) {
        String logPrefix = LOG_PREFIX + "|place|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Child child = loadInScope(actor, childId, today);
            if (child.getStatus() == ChildStatus.LEFT) {
                throw GlobalException.of(ApiCode.CHILD_ALREADY_LEFT);
            }
            // Trẻ đăng ký cho năm học sắp tới: không xếp vào lớp năm đang học (xếp ở Xếp lớp năm học mới)
            if (plannedAdmissionRepository
                    .findFirstByChildIdAndStatusIn(
                            childId,
                            List.of(
                                    com.carenest.utils.enums.PlannedAdmissionStatus.PENDING,
                                    com.carenest.utils.enums.PlannedAdmissionStatus.PLACED))
                    .isPresent()) {
                throw GlobalException.of(ApiCode.CHILD_PLANNED_FOR_NEXT_YEAR, child.getFullName());
            }
            Optional<Enrollment> current = enrollmentRepository.findActiveByChildIdIn(List.of(childId), today).stream()
                    .findFirst();
            // G94-180/CHD-14: lớp chưa bắt đầu (lịch chuyển hoặc ngày bắt đầu đi học ở tương lai), mỗi trẻ tối đa một
            Optional<Enrollment> upcoming = enrollmentRepository.findStartingAfter(List.of(childId), today).stream()
                    .findFirst();
            Long fromClassroomId = current.map(Enrollment::getClassroomId).orElse(null);
            // Xếp lần đầu không trước ngày bắt đầu đi học; chuyển lớp từ hôm nay. Trống = mốc sớm nhất
            LocalDate earliest = fromClassroomId == null
                            && child.getStartDate() != null
                            && child.getStartDate().isAfter(today)
                    ? child.getStartDate()
                    : today;
            LocalDate effectiveDate =
                    request == null || request.effectiveDate() == null ? earliest : request.effectiveDate();

            Map<String, String> errors = new LinkedHashMap<>();
            Long classId = request == null ? null : request.classId();
            ClassroomInfoResponse target = null;
            if (classId == null) {
                errors.put("classId", "Chọn lớp cho trẻ");
            } else if (classId.equals(fromClassroomId)) {
                errors.put("classId", "Trẻ đang học lớp này. Chọn lớp khác.");
            } else if (!declarationRepository.existsById(childId)) {
                // CHD-06: trẻ có khai báo sức khỏe trước khi vào lớp (bếp, điểm danh dùng dị ứng)
                errors.put("classId", "Trẻ chưa có khai báo ăn và sức khỏe. Khai báo trước khi xếp lớp.");
            } else {
                target = classroomService.findClassroomInfo(classId).orElse(null);
                // Sĩ số tính từ ngày vào lớp trở đi, không tính lịch cũ của chính trẻ (G94-180/CHD-14). Nhóm tuổi chỉ
                // gợi ý: xếp, chuyển lớp trong năm được sang lớp nhóm khác (G94-180/CHD-13, user chốt 2026-11-22,
                // 23/11)
                String problem = placementRules.classProblem(actor, target, effectiveDate, childId);
                if (problem != null) {
                    errors.put("classId", problem);
                }
            }
            String dateProblem = effectiveDateProblem(effectiveDate, earliest, today, fromClassroomId != null, target);
            if (dateProblem != null) {
                errors.put("effectiveDate", dateProblem);
            }
            String reason = request == null || request.reason() == null
                    ? ""
                    : request.reason().strip();
            if (fromClassroomId != null && reason.isEmpty()) {
                errors.put("reason", "Nhập lý do chuyển lớp");
            } else if (reason.length() > MAX_REASON) {
                errors.put("reason", "Lý do tối đa " + MAX_REASON + " ký tự");
            }
            // Ngày đã có điểm danh, sức khỏe, đánh giá thì không đổi lớp từ ngày đó (xét ở ngày chuyển lớp)
            if (fromClassroomId != null && dateProblem == null && hasDailyOperationalData(childId, effectiveDate)) {
                errors.put(
                        "effectiveDate",
                        "Trẻ đã có điểm danh, sức khỏe hoặc đánh giá trong ngày này. Chọn ngày chuyển lớp từ ngày học"
                                + " tiếp theo.");
            }
            if (!errors.isEmpty()) {
                throw GlobalException.withFieldErrors(
                        ApiCode.INVALID_REQUEST_DATA, EnrollmentFormValidator.toList(errors));
            }

            Instant now = clock.instant();
            // Chuyển / xếp lại khi đã có lớp chưa bắt đầu ⇒ thay lịch cũ (G94-180/CHD-14); chỉ đổi ngày cùng lớp thì
            // không báo hủy
            upcoming.ifPresent(old -> placementRules.cancelUpcoming(
                    childId,
                    child.getFullName(),
                    old,
                    old.getClassroomId().equals(classId) ? null : classNameOf(old.getClassroomId()),
                    actor.userId(),
                    now,
                    today));
            // Lớp cũ học tới hết ngày trước ngày vào lớp mới (lịch chuyển: tới ngày đó vẫn ở lớp cũ); vào lớp cũ đúng
            // ngày này thì bỏ lớp đó (một trẻ một lớp tại một ngày). Flush trước khi mở lớp mới: UNIQUE một enrollment
            // đang mở / trẻ, Hibernate ghi INSERT trước UPDATE/DELETE
            current.ifPresent(enrollment -> {
                if (enrollment.getValidFrom().isBefore(effectiveDate)) {
                    enrollment.endOn(effectiveDate.minusDays(1));
                    enrollmentRepository.saveAndFlush(enrollment);
                } else {
                    enrollmentRepository.delete(enrollment);
                    enrollmentRepository.flush();
                }
            });
            placementRules.startEnrollment(childId, target, actor.userId(), effectiveDate);
            // G94-180/CHD-02: lớp ở điểm trường khác ⇒ trẻ chuyển điểm trường (điểm trường suy từ lớp)
            child.placeInClass();
            childRepository.save(child);
            placementRules.recordPlacement(
                    childId,
                    child.getFullName(),
                    fromClassroomId,
                    target,
                    actor.userId(),
                    reason.isEmpty() ? null : reason,
                    now,
                    effectiveDate,
                    today);
            ChildViewResponse view = childIntakeService.getChildView(childId);
            log.info("{}|fromClassroomId={}|toClassroomId={}|END", logPrefix, fromClassroomId, target.id());
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ChildViewResponse cancelScheduledPlacement(CurrentUser actor, Long childId) {
        String logPrefix = LOG_PREFIX + "|cancelScheduledPlacement|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Child child = loadInScope(actor, childId, today);
            if (child.getStatus() == ChildStatus.LEFT) {
                throw GlobalException.of(ApiCode.CHILD_ALREADY_LEFT);
            }
            Enrollment upcoming = enrollmentRepository.findStartingAfter(List.of(childId), today).stream()
                    .findFirst()
                    .orElseThrow(() -> GlobalException.withFieldErrors(
                            ApiCode.INVALID_REQUEST_DATA,
                            EnrollmentFormValidator.toList(
                                    Map.of("classId", "Trẻ không có lịch vào lớp hoặc chuyển lớp chưa tới ngày"))));
            Optional<Enrollment> current = enrollmentRepository.findActiveByChildIdIn(List.of(childId), today).stream()
                    .findFirst();
            Long campusId =
                    classroomService.findCampusId(upcoming.getClassroomId()).orElse(null);
            // G94-180/CHD-14: hủy lịch ⇒ bỏ lớp chưa bắt đầu, báo GV lớp đó
            placementRules.cancelUpcoming(
                    childId,
                    child.getFullName(),
                    upcoming,
                    classNameOf(upcoming.getClassroomId()),
                    actor.userId(),
                    clock.instant(),
                    today);
            if (current.isPresent()) {
                // Trẻ ở lại lớp hiện tại, lớp không còn ngày kết thúc
                Enrollment stay = current.get();
                stay.reopen();
                enrollmentRepository.save(stay);
                placementRules.notifyTeachers(
                        stay.getClassroomId(),
                        today,
                        "Hủy lịch chuyển lớp",
                        child.getFullName() + " ở lại lớp, không chuyển lớp nữa.",
                        childId);
            } else {
                // Chưa học lớp nào ⇒ về Chờ xếp lớp ở điểm trường của lớp bị hủy
                child.awaitPlacement(campusId);
                childRepository.save(child);
            }
            ChildViewResponse view = childIntakeService.getChildView(childId);
            log.info("{}|classroomId={}|END", logPrefix, upcoming.getClassroomId());
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ChildClassResponse findClass(Long childId) {
        String logPrefix = LOG_PREFIX + "|findClass|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            Child child =
                    childRepository.findById(childId).orElseThrow(() -> GlobalException.of(ApiCode.CHILD_NOT_FOUND));
            LocalDate today = LocalDate.now(clock);
            Optional<Long> classroomId = child.getStatus() == ChildStatus.PENDING_PLACEMENT
                    ? Optional.empty()
                    : enrollmentRepository.findActiveByChildIdIn(List.of(childId), today).stream()
                            .findFirst()
                            // Đã nghỉ ⇒ lớp cuối; chưa có lớp nào bắt đầu ⇒ lớp sắp vào (G94-180/CHD-14)
                            .or(() -> child.getStatus() == ChildStatus.LEFT
                                    ? enrollmentRepository.findFirstByChildIdOrderByValidFromDescIdDesc(childId)
                                    : enrollmentRepository.findStartingAfter(List.of(childId), today).stream()
                                            .findFirst())
                            .map(Enrollment::getClassroomId);
            ChildClassResponse cls = classroomId
                    .flatMap(classroomService::findClassroomInfo)
                    .map(c -> new ChildClassResponse(
                            c.id(),
                            c.name(),
                            c.campusId(),
                            c.ageGroupId(),
                            c.schoolYearId(),
                            c.capacity(),
                            c.status(),
                            staffAssignmentService
                                    .findHomeroomTeacherIds(List.of(c.id()), today)
                                    .get(c.id()),
                            List.copyOf(staffAssignmentService
                                    .findCurrentTeacherIds(List.of(c.id()), today)
                                    .getOrDefault(c.id(), List.of()))))
                    .orElse(null);
            log.info("{}|found={}|END", logPrefix, cls != null);
            return cls;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChildPlacementResponse> findPlacements(Long childId) {
        String logPrefix = LOG_PREFIX + "|findPlacements|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            List<ChildPlacement> rows = placementRepository.findAllByChildIdOrderByPlacedAtDescIdDesc(childId);
            Map<Long, String> classNames = new HashMap<>();
            rows.forEach(p -> {
                if (p.getFromClassroomId() != null) {
                    classNames.computeIfAbsent(p.getFromClassroomId(), this::classNameOf);
                }
                classNames.computeIfAbsent(p.getToClassroomId(), this::classNameOf);
            });
            Set<Long> userIds = rows.stream().map(ChildPlacement::getPlacedBy).collect(Collectors.toSet());
            Map<Long, String> userNames = userIds.isEmpty()
                    ? Map.of()
                    : childHistoryRepository.findUserNames(userIds).stream()
                            .collect(Collectors.toMap(
                                    ChildHistoryRepository.UserName::getUserId,
                                    ChildHistoryRepository.UserName::getFullName));
            List<ChildPlacementResponse> placements = rows.stream()
                    .map(p -> new ChildPlacementResponse(
                            p.getId(),
                            p.getFromClassroomId(),
                            p.getFromClassroomId() == null ? null : classNames.get(p.getFromClassroomId()),
                            p.getToClassroomId(),
                            classNames.get(p.getToClassroomId()),
                            p.getPlacedBy(),
                            userNames.get(p.getPlacedBy()),
                            p.getPlacedAt(),
                            p.getReason(),
                            p.effectiveDate(),
                            p.getCancelledAt()))
                    .toList();
            log.info("{}|count={}|END", logPrefix, placements.size());
            return placements;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private String classNameOf(Long classroomId) {
        return classroomService
                .findClassroomInfo(classroomId)
                .map(ClassroomInfoResponse::name)
                .orElse(null);
    }

    @Override
    @Transactional
    public ChildViewResponse withdraw(CurrentUser actor, Long childId, WithdrawChildRequest request) {
        String logPrefix = LOG_PREFIX + "|withdraw|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Child child = loadInScope(actor, childId, today);
            if (child.getStatus() == ChildStatus.LEFT) {
                throw GlobalException.of(ApiCode.CHILD_ALREADY_LEFT);
            }
            // G94-180/CHD-03, CHD-14: lớp đang học (kể cả đang có lịch chuyển) và lớp chưa bắt đầu
            List<Enrollment> open = enrollmentRepository.findOpenByChildId(childId, today).stream()
                    .sorted(java.util.Comparator.comparing(Enrollment::getValidFrom))
                    .toList();

            Map<String, String> errors = new LinkedHashMap<>();
            LocalDate leftAt = parseDate(request == null ? null : request.leftAt());
            if (leftAt == null) {
                errors.put("leftAt", "Chọn ngày nghỉ học");
            } else if (!leftAt.equals(today)) {
                errors.put("leftAt", "Ngày nghỉ học phải là ngày thực hiện thao tác");
            } else if (!schoolYearService.isSchoolDay(leftAt)) {
                errors.put("leftAt", "Chỉ cho nghỉ học vào ngày học. Thực hiện vào ngày học kế tiếp.");
            } else if (leftAt.isBefore(child.getEnrolledOn())) {
                errors.put(
                        "leftAt",
                        "Ngày nghỉ học không được trước ngày tiếp nhận (" + DAY.format(child.getEnrolledOn()) + ")");
            } else if (child.getReadmittedOn() != null && leftAt.isBefore(child.getReadmittedOn())) {
                errors.put(
                        "leftAt",
                        "Ngày nghỉ học không được trước ngày mở lại hồ sơ (" + DAY.format(child.getReadmittedOn())
                                + ")");
            }
            String reason = request == null || request.reason() == null
                    ? ""
                    : request.reason().strip();
            if (reason.isEmpty()) {
                errors.put("reason", "Nhập lý do cho trẻ nghỉ học");
            } else if (reason.length() > MAX_REASON) {
                errors.put("reason", "Lý do tối đa " + MAX_REASON + " ký tự");
            }
            if (leftAt != null && leftAt.equals(today) && hasDailyOperationalData(childId, today)) {
                errors.put(
                        "leftAt",
                        "Trẻ đã có điểm danh, sức khỏe hoặc đánh giá trong hôm nay. Thực hiện nghỉ học từ ngày học tiếp theo.");
            }
            if (!errors.isEmpty()) {
                throw GlobalException.withFieldErrors(
                        ApiCode.INVALID_REQUEST_DATA, EnrollmentFormValidator.toList(errors));
            }

            // Báo GV lớp đang học và lớp chưa bắt đầu (GV lớp đó đã được báo trẻ sẽ vào)
            List<Long> classroomIds =
                    open.stream().map(Enrollment::getClassroomId).distinct().toList();
            Long classroomId = classroomIds.isEmpty() ? null : classroomIds.getFirst();
            boolean keepsClass = open.stream().anyMatch(e -> e.getValidFrom().isBefore(leftAt));
            boolean campusKept = false;
            Instant now = clock.instant();
            for (Enrollment enrollment : open) {
                // Nghỉ từ leftAt ⇒ học tới hết ngày trước đó; nghỉ ngay ngày vào lớp thì bỏ lớp đó (như chuyển năm học)
                if (enrollment.getValidFrom().isBefore(leftAt)) {
                    enrollment.endOn(leftAt.minusDays(1));
                    enrollmentRepository.save(enrollment);
                    continue;
                }
                // Bỏ hết lớp thì không còn lớp nào chỉ ra điểm trường của trẻ (xếp lớp đã bỏ điểm trường tiếp nhận) ⇒
                // ghi điểm trường của lớp đầu tiên bị bỏ để trẻ đã nghỉ vẫn thuộc điểm trường đó (mở lại, phạm vi PHT)
                if (!keepsClass && !campusKept) {
                    classroomService.findCampusId(enrollment.getClassroomId()).ifPresent(child::keepCampus);
                    campusKept = true;
                }
                if (enrollment.getValidFrom().isAfter(leftAt)) {
                    // Lịch chuyển / lớp chưa bắt đầu: xóa và đánh dấu dòng lịch sử đã hủy (G94-180/CHD-14)
                    placementRules.cancelUpcoming(
                            childId, child.getFullName(), enrollment, null, actor.userId(), now, today);
                } else {
                    enrollmentRepository.delete(enrollment);
                }
            }
            child.withdraw(leftAt, reason);
            childRepository.save(child);
            // CHD-11: trẻ đã đăng ký năm sau mà nghỉ học ⇒ hủy đăng ký, kẻo kích hoạt năm mới vẫn nhận trẻ
            plannedAdmissionRepository
                    .findFirstByChildIdAndStatusIn(
                            childId,
                            List.of(
                                    com.carenest.utils.enums.PlannedAdmissionStatus.PENDING,
                                    com.carenest.utils.enums.PlannedAdmissionStatus.PLACED))
                    .ifPresent(admission -> {
                        admission.cancel();
                        plannedAdmissionRepository.save(admission);
                    });
            childHistoryRepository.save(ChildHistory.record(
                    childId, ChildHistoryAction.WITHDRAWN, actor.userId(), reason, clock.instant()));
            for (Long notifiedClassId : classroomIds) {
                Set<Long> teacherIds = new HashSet<>(staffAssignmentService
                        .findCurrentTeacherIds(List.of(notifiedClassId), today)
                        .getOrDefault(notifiedClassId, List.of()));
                notificationService.notifyUsers(
                        teacherIds,
                        NotificationType.CHILD_WITHDRAWN,
                        "Trẻ nghỉ học",
                        child.getFullName() + " nghỉ học từ ngày " + DAY.format(leftAt) + ".",
                        childId);
            }
            // CHD-04: tài khoản chỉ là phụ huynh không còn con đang học ⇒ khóa sau commit (listener feature account)
            eventPublisher.publishEvent(new YearTransitionService.ChildrenLeftSchoolEvent(List.of(childId), leftAt));
            ChildViewResponse view = childIntakeService.getChildView(childId);
            log.info("{}|classroomId={}|END", logPrefix, classroomId);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /**
     * G94-180/CHD-14: ngày vào lớp mới không trước {@code earliest} (hôm nay, hoặc ngày bắt đầu đi học khi xếp lần đầu),
     * không sau ngày kết thúc năm học của lớp, là ngày học. Null khi hợp lệ.
     */
    private String effectiveDateProblem(
            LocalDate date, LocalDate earliest, LocalDate today, boolean transfer, ClassroomInfoResponse target) {
        String label = transfer ? "Ngày chuyển lớp" : "Ngày vào lớp";
        if (date.isBefore(earliest)) {
            return earliest.equals(today)
                    ? label + " không được trước hôm nay"
                    : label + " không được trước ngày bắt đầu đi học (" + DAY.format(earliest) + ")";
        }
        if (target != null && target.yearEnd() != null && date.isAfter(target.yearEnd())) {
            return label + " không được sau ngày kết thúc năm học (" + DAY.format(target.yearEnd()) + ")";
        }
        if (!schoolYearService.isSchoolDay(date)) {
            return label + " phải là ngày học (thứ 2 tới thứ 6, không trùng ngày nghỉ của trường)";
        }
        return null;
    }

    private boolean hasDailyOperationalData(Long childId, LocalDate date) {
        return dayUsageChecks.stream().anyMatch(check -> check.hasData(childId, date));
    }

    @Override
    @Transactional(readOnly = true)
    public ChildEditDraft prepareEdit(CurrentUser actor, Long childId, UpdateChildRequest request) {
        String logPrefix = LOG_PREFIX + "|prepareEdit|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Child child = loadEditable(actor, childId, today);
            if (child.getStatus() == ChildStatus.LEFT) {
                throw GlobalException.of(ApiCode.CHILD_ALREADY_LEFT);
            }
            Long campusId = campusOf(child, today);

            Map<String, String> errors = new LinkedHashMap<>();
            EnrollmentDraft draft =
                    EnrollmentFormValidator.validateEdit(request == null ? null : request.child(), today, errors);
            String reason = request == null || request.reason() == null
                    ? ""
                    : request.reason().strip();
            if (reason.isEmpty()) {
                errors.put("reason", "Nhập lý do chỉnh sửa hồ sơ");
            } else if (reason.length() > MAX_REASON) {
                errors.put("reason", "Lý do tối đa " + MAX_REASON + " ký tự");
            }
            // Health dựa vào điều này: ngày đo/khám không kiểm ngày sinh (module card child, Known pitfalls)
            LocalDate firstDay = firstStudyDay(child);
            if (draft != null && draft.dateOfBirth().isAfter(firstDay)) {
                errors.put("dateOfBirth", "Ngày sinh không được sau ngày tiếp nhận (" + DAY.format(firstDay) + ")");
            }

            List<ChildGuardian> existing = childGuardianRepository.findAllByChildIdOrderByPositionAsc(childId);
            Set<Long> linkedAccounts = guardianChildLinkService.findLinksOfChild(childId).stream()
                    .map(GuardianLinkResponse::userId)
                    .collect(Collectors.toSet());
            List<GuardianInput> inputs = request == null
                            || request.child() == null
                            || request.child().guardians() == null
                    ? List.of()
                    : request.child().guardians();
            List<GuardianEdit> guardians = new ArrayList<>();
            if (draft != null) {
                guardians = matchGuardians(draft.guardians(), inputs, existing, linkedAccounts, errors);
            }
            if (!errors.isEmpty()) {
                throw GlobalException.withFieldErrors(
                        ApiCode.INVALID_REQUEST_DATA, EnrollmentFormValidator.toList(errors));
            }

            Set<String> citizenIds =
                    draft.guardians().stream().map(GuardianDraft::citizenId).collect(Collectors.toSet());
            String nameKey = EnrollmentFormValidator.nameKey(draft.fullName());
            boolean duplicate =
                    childRepository.findDuplicateCandidates(draft.dateOfBirth(), citizenIds, childId).stream()
                            .filter(c -> EnrollmentFormValidator.nameKey(c.getFullName())
                                    .equals(nameKey))
                            .anyMatch(c -> c.getStatus() != ChildStatus.LEFT);
            if (duplicate) {
                throw GlobalException.of(ApiCode.CHILD_DUPLICATE);
            }
            ChildEditDraft result = new ChildEditDraft(
                    childId,
                    campusId,
                    reason,
                    draft.fullName(),
                    draft.dateOfBirth(),
                    draft.gender(),
                    draft.profile(),
                    guardians);
            log.info("{}|campusId={}|guardians={}|END", logPrefix, campusId, guardians.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void applyEdit(CurrentUser actor, ChildEditDraft draft) {
        String logPrefix = LOG_PREFIX + "|applyEdit|userId=" + actor.userId() + "|childId=" + draft.childId();
        log.info("{}|START", logPrefix);
        try {
            Long childId = draft.childId();
            Child child =
                    childRepository.findById(childId).orElseThrow(() -> GlobalException.of(ApiCode.CHILD_NOT_FOUND));
            List<ChildFieldChange> changes = new ArrayList<>();
            change(changes, "fullName", "Họ tên", child.getFullName(), draft.fullName());
            change(changes, "dateOfBirth", "Ngày sinh", day(child.getDateOfBirth()), day(draft.dateOfBirth()));
            change(changes, "gender", "Giới tính", genderLabel(child.getGender()), genderLabel(draft.gender()));
            child.updateBasics(draft.fullName(), draft.dateOfBirth(), draft.gender());
            childRepository.save(child);

            ChildProfileDraft p = draft.profile();
            Optional<ChildProfile> current = childProfileRepository.findById(childId);
            current.ifPresent(old -> profileChanges(changes, old, p));
            ChildProfile profile = current.orElseGet(() -> ChildProfile.create(
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
            profile.update(
                    p.idNumber(),
                    p.premature(),
                    p.gestationWeeks(),
                    p.ethnicity(),
                    p.beneficiary(),
                    p.bloodType(),
                    p.addressProvince(),
                    p.addressWard(),
                    p.addressStreet());
            childProfileRepository.save(profile);

            int removed = applyGuardians(childId, draft.guardians(), changes);

            String changesJson = changes.isEmpty() ? null : objectMapper.writeValueAsString(changes);
            childHistoryRepository.save(ChildHistory.record(
                    childId, ChildHistoryAction.UPDATED, actor.userId(), draft.reason(), changesJson, clock.instant()));
            log.info("{}|changes={}|removedGuardians={}|END", logPrefix, changes.size(), removed);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void syncGuardianAccountInfo(Long accountId, String fullName, String email, String citizenId) {
        String logPrefix = LOG_PREFIX + "|syncGuardianAccountInfo|accountId=" + accountId;
        log.info("{}|START", logPrefix);
        try {
            List<ChildGuardian> rows = childGuardianRepository.findAllByUserId(accountId);
            for (ChildGuardian g : rows) {
                g.update(g.getPosition(), fullName, g.getRelationship(), citizenId, email);
            }
            childGuardianRepository.saveAll(rows);
            log.info("{}|rows={}|END", logPrefix, rows.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void changeGuardianPhone(Long accountId, String newPhone) {
        String logPrefix = LOG_PREFIX + "|changeGuardianPhone|accountId=" + accountId;
        log.info("{}|START", logPrefix);
        try {
            List<ChildGuardian> rows = childGuardianRepository.findAllByUserId(accountId);
            rows.forEach(g -> g.changePhone(newPhone));
            childGuardianRepository.saveAll(rows);
            log.info("{}|rows={}|END", logPrefix, rows.size());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GuardianPhoneTarget findPhoneChangeTarget(CurrentUser actor, Long childId, Long guardianId) {
        String logPrefix = LOG_PREFIX + "|findPhoneChangeTarget|userId=" + actor.userId() + "|childId=" + childId
                + "|guardianId=" + guardianId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Child child = loadEditable(actor, childId, today);
            if (child.getStatus() == ChildStatus.LEFT) {
                throw GlobalException.of(ApiCode.CHILD_ALREADY_LEFT);
            }
            GuardianPhoneTarget target = phoneTarget(child, guardianId, today)
                    .orElseThrow(() -> GlobalException.of(ApiCode.CHILD_GUARDIAN_NOT_FOUND));
            log.info("{}|END", logPrefix);
            return target;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GuardianPhoneTarget> findGuardian(Long guardianId) {
        return childGuardianRepository
                .findById(guardianId)
                .flatMap(g -> childRepository.findById(g.getChildId()))
                .flatMap(child -> phoneTarget(child, guardianId, LocalDate.now(clock)));
    }

    @Override
    @Transactional
    public int changeGuardianRecordPhone(Long guardianId, String oldPhone, String newPhone) {
        String logPrefix = LOG_PREFIX + "|changeGuardianRecordPhone|guardianId=" + guardianId;
        log.info("{}|START", logPrefix);
        try {
            Optional<ChildGuardian> row = childGuardianRepository
                    .findById(guardianId)
                    .filter(g -> g.getPhone().equals(oldPhone));
            row.ifPresent(g -> {
                g.changePhone(newPhone);
                childGuardianRepository.save(g);
            });
            log.info("{}|changed={}|END", logPrefix, row.isPresent());
            return row.isPresent() ? 1 : 0;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findGuardianNames(Collection<Long> guardianIds) {
        return guardianIds.isEmpty()
                ? Map.of()
                : childGuardianRepository.findAllById(guardianIds).stream()
                        .collect(Collectors.toMap(ChildGuardian::getId, ChildGuardian::getFullName));
    }

    // Phụ huynh của đúng trẻ này; tài khoản chỉ tính khi còn liên kết với trẻ (như Sửa hồ sơ)
    private Optional<GuardianPhoneTarget> phoneTarget(Child child, Long guardianId, LocalDate today) {
        List<ChildGuardian> guardians = childGuardianRepository.findAllByChildIdOrderByPositionAsc(child.getId());
        Optional<ChildGuardian> found =
                guardians.stream().filter(g -> g.getId().equals(guardianId)).findFirst();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ChildGuardian g = found.get();
        Long accountId = g.getUserId() != null && guardianChildLinkService.isGuardianOf(g.getUserId(), child.getId())
                ? g.getUserId()
                : null;
        List<String> others = guardians.stream()
                .filter(o -> !o.getId().equals(guardianId))
                .map(ChildGuardian::getPhone)
                .toList();
        return Optional.of(new GuardianPhoneTarget(
                child.getId(),
                child.getFullName(),
                campusOf(child, today),
                g.getId(),
                g.getFullName(),
                g.getCitizenId(),
                g.getPhone(),
                accountId,
                others));
    }

    /**
     * Ghép phụ huynh gửi lên với dòng có sẵn: theo {@code id}, rồi CCCD, rồi cùng vị trí (đổi CCCD của người đã có — mục
     * 5.1). Dòng còn lại mà đang liên kết tài khoản với trẻ ⇒ không bỏ ở hồ sơ (gỡ ở màn Tài khoản phụ huynh — AUTH-19).
     */
    private static List<GuardianEdit> matchGuardians(
            List<GuardianDraft> drafts,
            List<GuardianInput> inputs,
            List<ChildGuardian> existing,
            Set<Long> linkedAccounts,
            Map<String, String> errors) {
        Map<Long, ChildGuardian> byId = existing.stream()
                .collect(Collectors.toMap(ChildGuardian::getId, g -> g, (a, b) -> a, LinkedHashMap::new));
        Set<Long> used = new HashSet<>();
        ChildGuardian[] matched = new ChildGuardian[drafts.size()];
        for (int i = 0; i < drafts.size(); i++) {
            Long id = i < inputs.size() && inputs.get(i) != null ? inputs.get(i).id() : null;
            if (id == null) continue;
            ChildGuardian g = byId.get(id);
            if (g == null || !used.add(id)) {
                errors.putIfAbsent("guardians", "Phụ huynh không thuộc hồ sơ này. Tải lại hồ sơ rồi sửa lại.");
                continue;
            }
            matched[i] = g;
        }
        for (int i = 0; i < drafts.size(); i++) {
            if (matched[i] != null
                    || (i < inputs.size()
                            && inputs.get(i) != null
                            && inputs.get(i).id() != null)) continue;
            String citizenId = drafts.get(i).citizenId();
            for (ChildGuardian g : existing) {
                if (!used.contains(g.getId()) && citizenId != null && citizenId.equals(g.getCitizenId())) {
                    matched[i] = g;
                    used.add(g.getId());
                    break;
                }
            }
        }
        for (int i = 0; i < drafts.size(); i++) {
            if (matched[i] != null
                    || (i < inputs.size()
                            && inputs.get(i) != null
                            && inputs.get(i).id() != null)) continue;
            int position = i;
            for (ChildGuardian g : existing) {
                if (!used.contains(g.getId()) && g.getPosition() == position) {
                    matched[i] = g;
                    used.add(g.getId());
                    break;
                }
            }
        }
        List<String> dropped = existing.stream()
                .filter(g -> !used.contains(g.getId()))
                .filter(g -> g.getUserId() != null && linkedAccounts.contains(g.getUserId()))
                .map(ChildGuardian::getFullName)
                .toList();
        if (!dropped.isEmpty()) {
            errors.putIfAbsent(
                    "guardians",
                    String.join(", ", dropped)
                            + " đã có tài khoản liên kết với trẻ nên không xóa ở hồ sơ. Gỡ liên kết ở màn Tài khoản"
                            + " phụ huynh.");
        }
        List<GuardianEdit> result = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            ChildGuardian g = matched[i];
            Long accountId =
                    g != null && g.getUserId() != null && linkedAccounts.contains(g.getUserId()) ? g.getUserId() : null;
            result.add(new GuardianEdit(
                    drafts.get(i),
                    g == null ? null : g.getId(),
                    accountId,
                    g == null ? null : g.getPhone(),
                    g == null ? null : g.getCitizenId()));
        }
        return result;
    }

    /** Xóa dòng bị bỏ trước (UNIQUE vị trí/SĐT/CCCD DEFERRABLE, người nhận tài khoản thì không), rồi sửa, rồi thêm. */
    private int applyGuardians(Long childId, List<GuardianEdit> edits, List<ChildFieldChange> changes) {
        List<ChildGuardian> existing = childGuardianRepository.lockByChildId(childId);
        Map<Long, ChildGuardian> byId = existing.stream().collect(Collectors.toMap(ChildGuardian::getId, g -> g));
        Set<Long> kept = edits.stream()
                .map(GuardianEdit::guardianId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<ChildGuardian> removed =
                existing.stream().filter(g -> !kept.contains(g.getId())).toList();
        for (ChildGuardian g : removed) {
            changes.add(new ChildFieldChange(
                    "guardians." + g.getPosition(), "Phụ huynh " + (g.getPosition() + 1), g.getFullName(), null));
        }
        childGuardianRepository.deleteAll(removed);
        childGuardianRepository.flush();

        boolean hasRecipient = edits.stream()
                .map(GuardianEdit::guardianId)
                .filter(Objects::nonNull)
                .map(byId::get)
                .anyMatch(ChildGuardian::isAccountRecipient);
        for (int i = 0; i < edits.size(); i++) {
            GuardianEdit edit = edits.get(i);
            GuardianDraft d = edit.draft();
            String prefix = "guardians." + i + ".";
            String label = "Phụ huynh " + (i + 1) + " — ";
            if (edit.guardianId() == null) {
                ChildGuardian created = ChildGuardian.create(
                        childId, i, d.fullName(), d.relationship(), d.phone(), d.citizenId(), d.email());
                // Spec013: hồ sơ còn một phụ huynh mà chưa có người nhận tài khoản ⇒ chọn người đó
                created.selectAccountRecipient(edits.size() == 1 && !hasRecipient);
                childGuardianRepository.save(created);
                changes.add(new ChildFieldChange("guardians." + i, "Phụ huynh " + (i + 1), null, d.fullName()));
                continue;
            }
            ChildGuardian g = byId.get(edit.guardianId());
            change(changes, prefix + "fullName", label + "Họ tên", g.getFullName(), d.fullName());
            change(
                    changes,
                    prefix + "relation",
                    label + "Quan hệ",
                    relationLabel(g.getRelationship()),
                    relationLabel(d.relationship()));
            change(changes, prefix + "phone", label + "Số điện thoại", g.getPhone(), d.phone());
            change(changes, prefix + "idNumber", label + "Số CCCD", mask(g.getCitizenId()), mask(d.citizenId()));
            change(changes, prefix + "email", label + "Email", g.getEmail(), d.email());
            g.update(i, d.fullName(), d.relationship(), d.citizenId(), d.email());
            g.changePhone(d.phone());
            if (edits.size() == 1 && !hasRecipient) {
                g.selectAccountRecipient(true);
            }
            childGuardianRepository.save(g);
        }
        return removed.size();
    }

    private static void profileChanges(List<ChildFieldChange> changes, ChildProfile old, ChildProfileDraft p) {
        change(changes, "profile.idNumber", "Số định danh", mask(old.getIdNumber()), mask(p.idNumber()));
        change(changes, "profile.premature", "Sinh non", text(old.getPremature()), text(p.premature()));
        change(changes, "profile.gestationWeeks", "Tuần thai", text(old.getGestationWeeks()), text(p.gestationWeeks()));
        change(changes, "profile.ethnicity", "Dân tộc", old.getEthnicity(), p.ethnicity());
        change(changes, "profile.beneficiary", "Đối tượng", old.getBeneficiary(), p.beneficiary());
        change(changes, "profile.bloodType", "Nhóm máu", old.getBloodType(), p.bloodType());
        change(changes, "profile.address.province", "Tỉnh/thành phố", old.getAddressProvince(), p.addressProvince());
        change(changes, "profile.address.ward", "Phường/xã", old.getAddressWard(), p.addressWard());
        change(changes, "profile.address.street", "Địa chỉ", old.getAddressStreet(), p.addressStreet());
    }

    private static void change(List<ChildFieldChange> changes, String field, String label, String from, String to) {
        if (!Objects.equals(from, to)) {
            changes.add(new ChildFieldChange(field, label, from, to));
        }
    }

    /** Số định danh/CCCD trong lịch sử chỉ 3 số cuối (mục 3.7 plan tiếp nhận). */
    private static String mask(String value) {
        if (value == null) return null;
        return value.length() <= 3 ? "***" : "***" + value.substring(value.length() - 3);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static String yesNo(boolean value) {
        return value ? "Có" : "Không";
    }

    private static String day(LocalDate date) {
        return date == null ? null : DAY.format(date);
    }

    private static String genderLabel(Gender gender) {
        if (gender == null) return null;
        return gender == Gender.MALE ? "Nam" : "Nữ";
    }

    private static String relationLabel(GuardianRelationship relationship) {
        if (relationship == null) return null;
        return switch (relationship) {
            case FATHER -> "Bố";
            case MOTHER -> "Mẹ";
            case GRANDFATHER -> "Ông";
            case GRANDMOTHER -> "Bà";
            case SIBLING -> "Anh/chị";
            case GUARDIAN -> "Người giám hộ";
            default -> relationship.name();
        };
    }

    @Override
    @Transactional(readOnly = true)
    public void checkDeclaration(CurrentUser actor, Long childId, HealthDeclarationRequest request) {
        String logPrefix = LOG_PREFIX + "|checkDeclaration|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            validateDeclaration(actor, childId, request, LocalDate.now(clock));
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private record ValidDeclaration(
            Child child, HealthDeclarationDraft draft, Optional<ChildHealthDeclaration> existing, String reason) {}

    // Phạm vi + hợp lệ của khai báo (CHD-06); dùng chung khi lưu thẳng và khi GV gửi yêu cầu (CHD-11)
    private ValidDeclaration validateDeclaration(
            CurrentUser actor, Long childId, HealthDeclarationRequest request, LocalDate today) {
        Child child = loadEditable(actor, childId, today);
        if (child.getStatus() == ChildStatus.LEFT) {
            throw GlobalException.of(ApiCode.CHILD_ALREADY_LEFT);
        }
        Map<String, String> errors = new LinkedHashMap<>();
        HealthDeclarationDraft d = EnrollmentFormValidator.validateDeclarationOnly(
                request == null ? null : request.declaration(), child.getDateOfBirth(), today, errors);
        Optional<ChildHealthDeclaration> existing = declarationRepository.findById(childId);
        String reason = request == null || request.reason() == null
                ? ""
                : request.reason().strip();
        if (existing.isPresent() && reason.isEmpty()) {
            errors.put("reason", "Nhập lý do thay đổi khai báo");
        } else if (reason.length() > MAX_REASON) {
            errors.put("reason", "Lý do tối đa " + MAX_REASON + " ký tự");
        }
        if (!errors.isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, EnrollmentFormValidator.toList(errors));
        }
        return new ValidDeclaration(child, d, existing, reason);
    }

    /**
     * Phạm vi sửa hồ sơ/khai báo sức khỏe. GV được HT cấp thêm CHILD_PROFILE_EDIT (AUTH-10) chỉ sửa trẻ lớp đang được
     * phân công (AUTH-03, AUTH-13); trẻ ngoài lớp và trẻ không tồn tại cùng 403. BGH theo {@link #loadInScope}.
     */
    private Child loadEditable(CurrentUser actor, Long childId, LocalDate today) {
        if (ChildEditRoles.teacherOnly(actor)) {
            if (!childAccessPolicy.canViewChild(actor, childId)) {
                throw GlobalException.of(ApiCode.CHILD_ACCESS_DENIED);
            }
            return childRepository.findById(childId).orElseThrow(() -> GlobalException.of(ApiCode.CHILD_ACCESS_DENIED));
        }
        return loadInScope(actor, childId, today);
    }

    /** Như mở lại hồ sơ: với PHT, trẻ không tồn tại và trẻ ngoài điểm trường cùng 403 (không dò được id). */
    private Child loadInScope(CurrentUser actor, Long childId, LocalDate today) {
        boolean wholeSchool = staffAccessPolicy.managesWholeSchool(actor);
        Child child = childRepository
                .findById(childId)
                .orElseThrow(
                        () -> GlobalException.of(wholeSchool ? ApiCode.CHILD_NOT_FOUND : ApiCode.CHILD_ACCESS_DENIED));
        Long campusId = campusOf(child, today);
        boolean inScope = wholeSchool
                || (campusId != null
                        && staffAccessPolicy.managedCampusIds(actor).contains(campusId));
        if (!inScope) {
            throw GlobalException.of(ApiCode.CHILD_ACCESS_DENIED);
        }
        return child;
    }

    /** Điểm trường hiện tại: theo lớp đang học, không có lớp thì điểm trường tiếp nhận, rồi lớp cuối cùng. */
    private Long campusOf(Child child, LocalDate today) {
        return enrollmentRepository.findActiveByChildIdIn(List.of(child.getId()), today).stream()
                .findFirst()
                .flatMap(e -> classroomService.findCampusId(e.getClassroomId()))
                .orElseGet(() -> child.getIntakeCampusId() != null
                        ? child.getIntakeCampusId()
                        : enrollmentRepository
                                .findFirstByChildIdOrderByValidFromDescIdDesc(child.getId())
                                .flatMap(e -> classroomService.findCampusId(e.getClassroomId()))
                                .orElse(null));
    }

    private LocalDate firstStudyDay(Child child) {
        LocalDate first = child.getEnrolledOn();
        Optional<Enrollment> earliest = enrollmentRepository.findFirstByChildIdOrderByValidFromAscIdAsc(child.getId());
        return earliest.map(Enrollment::getValidFrom)
                .filter(from -> from.isBefore(first))
                .orElse(first);
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
