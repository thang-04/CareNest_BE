package com.carenest.service.child.impl;

import com.carenest.dto.child.ChildEditDraft;
import com.carenest.dto.child.ChildEditDraft.GuardianEdit;
import com.carenest.dto.child.ChildFieldChange;
import com.carenest.dto.child.ChildProfileDraft;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.EnrollmentDraft;
import com.carenest.dto.child.GuardianDraft;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.child.HealthDeclarationDraft;
import com.carenest.dto.child.HealthDeclarationRequest;
import com.carenest.dto.child.HealthDeclarationResponse;
import com.carenest.dto.child.UpdateChildRequest;
import com.carenest.dto.child.WithdrawChildRequest;
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
import com.carenest.service.child.ChildRecordService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.child.YearTransitionService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
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
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ChildHealthDeclarationRepository declarationRepository;
    private final ChildAllergyRepository allergyRepository;
    private final ChildHealthDeclarationHistoryRepository declarationHistoryRepository;
    private final TbContactNotifier tbContactNotifier;

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
            LocalDate today = LocalDate.now(clock);
            Instant now = clock.instant();
            Child child = loadInScope(actor, childId, today);
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
                throw GlobalException.withFieldErrors(
                        ApiCode.INVALID_REQUEST_DATA, EnrollmentFormValidator.toList(errors));
            }

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

            ChildHealthDeclaration.Answer diet = answer(d.diet());
            ChildHealthDeclaration.Answer otherNotes = answer(d.otherNotes());
            ChildHealthDeclaration.Answer self = answer(d.selfHistory());
            ChildHealthDeclaration.Answer family = answer(d.familyHistory());
            if (existing.isPresent()) {
                existing.get()
                        .update(
                                d.allergiesState(),
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
                        d.allergiesState(),
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
            allergyRepository.deleteByChildId(childId);
            allergyRepository.saveAll(d.allergies().stream()
                    .map(name -> ChildAllergy.of(childId, name))
                    .toList());

            HealthDeclarationResponse.DeclarationAllergies afterAllergies =
                    new HealthDeclarationResponse.DeclarationAllergies(d.allergiesState(), d.allergies());
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
                        "sửa khai báo sức khỏe",
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
    public ChildViewResponse withdraw(CurrentUser actor, Long childId, WithdrawChildRequest request) {
        String logPrefix = LOG_PREFIX + "|withdraw|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Child child = loadInScope(actor, childId, today);
            if (child.getStatus() == ChildStatus.LEFT) {
                throw GlobalException.of(ApiCode.CHILD_ALREADY_LEFT);
            }
            Optional<Enrollment> open = enrollmentRepository.findOpenByChildId(childId, today).stream()
                    .filter(e -> e.getValidTo() == null)
                    .findFirst();

            Map<String, String> errors = new LinkedHashMap<>();
            LocalDate leftAt = parseDate(request == null ? null : request.leftAt());
            if (leftAt == null) {
                errors.put("leftAt", "Chọn ngày nghỉ học");
            } else if (leftAt.isAfter(today)) {
                errors.put("leftAt", "Ngày nghỉ học không được ở tương lai");
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
            if (!errors.isEmpty()) {
                throw GlobalException.withFieldErrors(
                        ApiCode.INVALID_REQUEST_DATA, EnrollmentFormValidator.toList(errors));
            }

            Long classroomId = open.map(Enrollment::getClassroomId).orElse(null);
            open.ifPresent(enrollment -> {
                // Nghỉ từ leftAt ⇒ học tới hết ngày trước đó; nghỉ ngay ngày vào lớp thì bỏ lớp đó (như chuyển năm học)
                if (enrollment.getValidFrom().isBefore(leftAt)) {
                    enrollment.endOn(leftAt.minusDays(1));
                    enrollmentRepository.save(enrollment);
                } else {
                    enrollmentRepository.delete(enrollment);
                }
            });
            child.withdraw(leftAt, reason);
            childRepository.save(child);
            childHistoryRepository.save(ChildHistory.record(
                    childId, ChildHistoryAction.WITHDRAWN, actor.userId(), reason, clock.instant()));
            if (classroomId != null) {
                Set<Long> teacherIds = new HashSet<>(staffAssignmentService
                        .findCurrentTeacherIds(List.of(classroomId), today)
                        .getOrDefault(classroomId, List.of()));
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

    @Override
    @Transactional(readOnly = true)
    public ChildEditDraft prepareEdit(CurrentUser actor, Long childId, UpdateChildRequest request) {
        String logPrefix = LOG_PREFIX + "|prepareEdit|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Child child = loadInScope(actor, childId, today);
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
                    drafts.get(i), g == null ? null : g.getId(), accountId, g == null ? null : g.getPhone()));
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
                        childId, i, d.fullName(), d.relationship(), d.phone(), d.citizenId(), d.email(), null);
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
