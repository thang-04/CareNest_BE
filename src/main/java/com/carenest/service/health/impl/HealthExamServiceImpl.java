package com.carenest.service.health.impl;

import com.carenest.dto.child.ChildGuardianResponse;
import com.carenest.dto.child.ChildMedicalHistory;
import com.carenest.dto.child.ChildProfileDetailResponse;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.health.ChildHealthRecordResponse;
import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthExamReference;
import com.carenest.dto.health.HealthExamRequest;
import com.carenest.dto.health.HealthExamResponse;
import com.carenest.dto.health.HealthExamReviewResponse;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthFollowUp;
import com.carenest.entity.health.HealthFollowUpEvent;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.entity.health.HealthMeasurementEdit;
import com.carenest.exception.GlobalException;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.repository.health.HealthMeasurementEditRepository;
import com.carenest.repository.health.HealthMeasurementRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.health.HealthExamService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.DeclarationState;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthConclusionMode;
import com.carenest.utils.enums.HealthDeclarationAction;
import com.carenest.utils.enums.HealthExamStatus;
import java.math.BigDecimal;
import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HealthExamServiceImpl implements HealthExamService {
    private static final String LOG_PREFIX = "[HealthExamServiceImpl]";
    private static final Collator VI = Collator.getInstance(Locale.forLanguageTag("vi"));
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String YES = "YES";
    private static final String NO = "NO";
    // Bước cuối của phiếu: kết luận lưu bước này
    private static final int LAST_STEP = 4;

    private final HealthCheckRules rules;
    private final HealthRecords records;
    private final HealthFollowUpTracker tracker;
    private final HealthCheckCampaignRepository campaignRepository;
    private final HealthMeasurementRepository measurementRepository;
    private final HealthMeasurementEditRepository editRepository;
    private final ChildAccessPolicy childAccessPolicy;
    private final ChildIntakeService childIntakeService;
    private final EnrollmentService enrollmentService;
    private final ClassroomService classroomService;

    /** Đợt khám, hồ sơ trẻ, lớp trẻ đang học (cùng điểm trường với đợt). */
    private record Target(HealthCheckCampaign campaign, ChildViewResponse child, ClassroomInfoResponse cls) {}

    @Override
    @Transactional(readOnly = true)
    public HealthExamResponse getExam(CurrentUser currentUser, Long campaignId, Long childId) {
        String logPrefix = LOG_PREFIX + "|getExam|userId=" + currentUser.userId() + "|campaignId=" + campaignId
                + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            Target target = target(currentUser, campaignId, childId);
            HealthRecords.Snapshot s = records.load(List.of(childId));
            HealthExamResponse view = view(currentUser, target, s, examOf(s, childId, campaignId));
            log.info("{}|END", logPrefix);
            return view;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public HealthExamResponse saveExam(
            CurrentUser currentUser, Long campaignId, Long childId, HealthExamRequest request) {
        String logPrefix = LOG_PREFIX + "|saveExam|userId=" + currentUser.userId() + "|campaignId=" + campaignId
                + "|childId=" + childId + "|conclude=" + request.conclude();
        log.info("{}|START", logPrefix);
        try {
            Target target = target(currentUser, campaignId, childId);
            HealthCheckCampaign campaign = target.campaign();
            ChildViewResponse child = target.child();
            // HLT-09: GV đang phụ trách lớp hiện tại của trẻ nhập phiếu; BGH chỉ xem
            if (!rules.canRecord(currentUser, target.cls())) {
                throw GlobalException.of(ApiCode.HEALTH_EXAM_ACCESS_DENIED);
            }
            if (campaign.statusOn(rules.today()) != HealthCheckStatus.IN_PROGRESS) {
                throw GlobalException.of(ApiCode.HEALTH_EXAM_LOCKED);
            }
            HealthRecords.Snapshot s = records.load(List.of(childId));
            HealthMeasurement m = examOf(s, childId, campaignId);
            boolean concluded = m != null && m.getExamStatus() == HealthExamStatus.CONCLUDED;
            if (concluded && !request.conclude()) {
                throw GlobalException.of(ApiCode.HEALTH_EXAM_CONCLUDED);
            }
            LocalDate date =
                    request.date() != null ? request.date() : m != null ? m.getDate() : clamp(campaign, rules.today());
            HealthExamReference reference = HealthExamReview.reference(child.dateOfBirth(), date);
            HealthConclusionMode mode = conclusionMode(s, childId, m);
            if (request.conclude() && request.conclusionMode() != null && request.conclusionMode() != mode) {
                throw GlobalException.of(ApiCode.HEALTH_EXAM_MODE_CHANGED);
            }
            ChildHealthRecordResponse.HealthLatest latest = s.latestHealth(childId);
            boolean hasKnownCondition = latest != null && latest.knownCondition() != null;
            HealthExamForm cleaned = HealthExamNormalizer.normalize(request.exam(), mode, reference, hasKnownCondition);
            // Sửa phiếu đã kết luận giữ hành chính lúc kết luận; người đi cùng luôn lấy theo danh sách phụ huynh
            HealthExamForm before = m == null ? null : s.exam(m);
            HealthExamForm exam = withProfile(cleaned, concluded ? before.admin() : null, child);
            BigDecimal height = HealthExamNormalizer.round(request.heightCm(), 1);
            BigDecimal weight = HealthExamNormalizer.round(request.weightKg(), 1);
            BigDecimal head =
                    reference.headApplies() ? HealthExamNormalizer.round(request.headCircumferenceCm(), 1) : null;

            List<FieldErrorResponse> errors = new ArrayList<>(
                    request.conclude()
                            ? HealthExamValidator.validate(new HealthExamValidator.Input(
                                    exam,
                                    date,
                                    request.heightCm(),
                                    request.weightKg(),
                                    request.headCircumferenceCm(),
                                    request.reason(),
                                    concluded,
                                    mode,
                                    reference,
                                    rules.today(),
                                    child.dateOfBirth(),
                                    campaign.getStartDate(),
                                    campaign.getEndDate(),
                                    child.guardians().size(),
                                    hasKnownCondition))
                            : draftErrors(height, weight, head));
            // Mỗi trẻ một lần đo một ngày (cả nháp, ràng buộc DB)
            Long currentId = m == null ? null : m.getId();
            if (errors.stream().noneMatch(e -> "date".equals(e.field()))
                    && measurementRepository
                            .findByChildIdAndDate(childId, date)
                            .filter(other -> !other.getId().equals(currentId))
                            .isPresent()) {
                errors.add(new FieldErrorResponse(
                        "date", "Ngày này trẻ đã có kết quả đo khác. Chọn ngày khám khác trong đợt."));
            }
            if (!errors.isEmpty()) {
                throw GlobalException.withFieldErrors(ApiCode.HEALTH_EXAM_INCOMPLETE, errors);
            }

            Instant now = rules.now();
            int storedStep = before == null || before.step() == null ? 0 : before.step();
            exam = withParts(
                    exam,
                    request.conclude() ? LAST_STEP : Math.max(exam.step() == null ? 0 : exam.step(), storedStep),
                    null,
                    null);
            if (concluded) {
                List<HealthMeasurementResponse.HealthMeasurementChange> changes = HealthExamDiff.changes(
                        new HealthExamDiff.Snapshot(
                                m.getDate(), m.getHeightCm(), m.getWeightKg(), m.getHeadCircumferenceCm(), before),
                        new HealthExamDiff.Snapshot(date, height, weight, head, exam));
                if (changes.isEmpty()) {
                    // Không đổi gì: không ghi lịch sử, không công bố lại
                    HealthExamResponse unchanged = view(currentUser, target, s, m);
                    log.info("{}|unchanged|END", logPrefix);
                    return unchanged;
                }
                // HLT-11, HLT-13: sửa phiếu đã công bố ghi lý do, các ô đổi; lưu là công bố lại
                editRepository.save(HealthMeasurementEdit.of(
                        m.getId(),
                        currentUser.userId(),
                        trimmed(request.reason()),
                        records.writeChanges(changes),
                        now));
            }
            boolean historyChanged = !concluded || !Objects.equals(before.history(), exam.history());
            boolean isNew = m == null;
            if (isNew) {
                m = HealthMeasurement.newExam(childId, campaignId, currentUser.userId(), now);
            }
            m.saveExam(
                    target.cls().id(),
                    date,
                    height,
                    weight,
                    head,
                    records.writeExam(exam),
                    request.conclude(),
                    currentUser.userId(),
                    now);
            if (isNew) {
                measurementRepository.save(m);
            }
            if (request.conclude()) {
                tracker.track(
                        m,
                        exam,
                        HealthExamOutcome.of(m, exam).level(),
                        campaign.getCampusId(),
                        currentUser.userId(),
                        now);
                if (historyChanged) {
                    writeBackHistory(childId, exam.history(), date, currentUser, now);
                }
            }
            HealthRecords.Snapshot after = records.load(List.of(childId));
            HealthExamResponse saved = view(currentUser, target, after, examOf(after, childId, campaignId));
            log.info("{}|END", logPrefix);
            return saved;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HealthExamReviewResponse reviewPreview(
            CurrentUser currentUser, Long campaignId, Long childId, HealthExamRequest request) {
        String logPrefix = LOG_PREFIX + "|reviewPreview|userId=" + currentUser.userId() + "|campaignId=" + campaignId
                + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            Target target = target(currentUser, campaignId, childId);
            HealthRecords.Snapshot s = records.load(List.of(childId));
            LocalDate date = request.date() != null ? request.date() : clamp(target.campaign(), rules.today());
            HealthExamReference reference =
                    HealthExamReview.reference(target.child().dateOfBirth(), date);
            HealthExamForm exam = HealthExamNormalizer.normalize(
                    request.exam(), conclusionMode(s, childId, examOf(s, childId, campaignId)), reference, false);
            HealthExamReviewResponse response = new HealthExamReviewResponse(HealthExamReview.points(exam, reference));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /**
     * Ngoài phạm vi xem trẻ, trẻ không còn học ở điểm trường của đợt ⇒ 404 như không tồn tại (không lộ dữ liệu); kỳ cân
     * đo không dùng phiếu khám.
     */
    private Target target(CurrentUser user, Long campaignId, Long childId) {
        HealthCheckCampaign campaign = campaignRepository
                .findById(campaignId)
                .orElseThrow(() -> GlobalException.of(ApiCode.HEALTH_EXAM_NOT_FOUND));
        if (!childAccessPolicy.canViewChild(user, childId)) {
            throw GlobalException.of(ApiCode.HEALTH_EXAM_NOT_FOUND);
        }
        ClassroomInfoResponse cls = enrollmentService.findActiveClassroomIds(childId, rules.today()).stream()
                .findFirst()
                .flatMap(classroomService::findClassroomInfo)
                .filter(c -> c.campusId().equals(campaign.getCampusId()))
                .orElseThrow(() -> GlobalException.of(ApiCode.HEALTH_EXAM_NOT_FOUND));
        if (campaign.getKind() != HealthCheckKind.EXAM) {
            throw GlobalException.of(ApiCode.HEALTH_EXAM_ROUND_REQUIRED);
        }
        return new Target(campaign, childIntakeService.getChildView(childId), cls);
    }

    private static HealthMeasurement examOf(HealthRecords.Snapshot s, Long childId, Long campaignId) {
        return s.measurements(childId).stream()
                .filter(m -> m.getCampaignId().equals(campaignId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Phiếu đã kết luận giữ mẫu lúc kết luận; phiếu mới/nháp dùng mẫu FOLLOW_UP khi trẻ có phiếu theo dõi đang mở chưa
     * chứa lần khám này.
     */
    private static HealthConclusionMode conclusionMode(HealthRecords.Snapshot s, Long childId, HealthMeasurement m) {
        if (m != null && m.getExamStatus() == HealthExamStatus.CONCLUDED) {
            return HealthExamOutcome.followUpMode(s.exam(m))
                    ? HealthConclusionMode.FOLLOW_UP
                    : HealthConclusionMode.STANDARD;
        }
        HealthFollowUp open = s.openFollowUp(childId);
        HealthFollowUp ofExam = m == null ? null : s.followUpOf(m.getId());
        return open != null && (ofExam == null || !ofExam.getId().equals(open.getId()))
                ? HealthConclusionMode.FOLLOW_UP
                : HealthConclusionMode.STANDARD;
    }

    private HealthExamResponse view(CurrentUser user, Target target, HealthRecords.Snapshot s, HealthMeasurement m) {
        HealthCheckCampaign c = target.campaign();
        ChildViewResponse child = target.child();
        Long childId = child.id();
        HealthCheckStatus status = c.statusOn(rules.today());
        ChildHealthRecordResponse.HealthLatest latest = s.latestHealth(childId);
        HealthFollowUp open = s.openFollowUp(childId);
        HealthFollowUp ofExam = m == null ? null : s.followUpOf(m.getId());
        boolean openOther = open != null && (ofExam == null || !ofExam.getId().equals(open.getId()));
        LocalDate refDate = m != null ? m.getDate() : clamp(c, rules.today());
        Optional<ChildMedicalHistory> declared = childIntakeService.findMedicalHistory(childId);
        HealthMeasurementResponse measurement = null;
        HealthExamResponse.HealthExamDraft draft = null;
        if (m != null) {
            if (m.isDraft()) {
                // Nháp: hành chính theo hồ sơ hiện tại, tiền sử còn trống điền từ khai báo (chỉ hiển thị, không lưu)
                s.showExam(m.getId(), withHistory(withProfile(s.exam(m), null, child), declared));
            }
            measurement =
                    records.views(s, List.of(m), child.dateOfBirth(), true).getFirst();
        } else {
            draft = new HealthExamResponse.HealthExamDraft(refDate, null, null, null, prefill(child, declared));
        }
        HealthFollowUpEvent external =
                s.externalEvents(childId).stream().findFirst().orElse(null);
        return new HealthExamResponse(
                conclusionMode(s, childId, m),
                latest == null ? null : latest.knownCondition(),
                openOther ? s.brief(open) : null,
                new HealthExamResponse.HealthExamCampaign(
                        c.getId(), c.getName(), c.getStartDate(), c.getEndDate(), c.getExaminer(), status),
                new HealthExamResponse.HealthExamChild(
                        child.id(),
                        child.code(),
                        child.fullName(),
                        child.gender(),
                        child.dateOfBirth(),
                        target.cls().id(),
                        target.cls().name(),
                        child.guardians().stream()
                                .map(g -> new HealthExamResponse.HealthExamGuardian(
                                        g.fullName(), relationLabel(g.relation()), g.phone(), trimmed(g.idNumber())))
                                .toList()),
                measurement,
                draft,
                HealthExamReview.reference(child.dateOfBirth(), refDate),
                s.previous(childId, m == null ? null : m.getId(), refDate),
                external == null
                        ? null
                        : new HealthExamResponse.HealthExamExternalResult(
                                external.getFollowUpId(),
                                external.getEventDate(),
                                external.getNote(),
                                trimmed(external.getFacility()),
                                external.getNextVisitOn()),
                measurementRepository.findDoctorNames(c.getId()).stream()
                        .filter(n -> n != null && !n.isBlank())
                        .sorted(VI)
                        .toList(),
                status == HealthCheckStatus.IN_PROGRESS && rules.canRecord(user, target.cls()),
                status != HealthCheckStatus.IN_PROGRESS);
    }

    /** Phiếu mới: mẫu trống, người đi cùng mặc định là phụ huynh đầu tiên, hành chính và tiền sử điền sẵn. */
    private static HealthExamForm prefill(ChildViewResponse child, Optional<ChildMedicalHistory> declared) {
        HealthExamForm template = HealthExamCatalog.template();
        boolean hasGuardian = !child.guardians().isEmpty();
        HealthExamForm.HealthExamAdmin a = template.admin();
        HealthExamForm.HealthExamAdmin admin = new HealthExamForm.HealthExamAdmin(
                a.childIdNumber(),
                a.premature(),
                a.gestationWeeks(),
                a.ethnicity(),
                a.beneficiary(),
                a.paymentSource(),
                a.bloodType(),
                a.address(),
                hasGuardian ? YES : "",
                new HealthExamForm.HealthExamCompanion(hasGuardian ? 0 : null, "", "", "", ""));
        return withHistory(withProfile(withParts(template, null, admin, null), null, child), declared);
    }

    /**
     * Hành chính lấy từ hồ sơ trẻ ({@code snapshot}: hành chính lúc kết luận khi sửa phiếu đã kết luận), người nhập không
     * sửa; người đi cùng dựng lại từ danh sách phụ huynh theo vị trí đã chọn.
     */
    private static HealthExamForm withProfile(
            HealthExamForm exam, HealthExamForm.HealthExamAdmin snapshot, ChildViewResponse child) {
        HealthExamForm.HealthExamAdmin current = exam.admin();
        String hasCompanion = current == null ? "" : trimmed(current.hasCompanion());
        Integer index = current == null || current.companion() == null
                ? null
                : current.companion().guardianIndex();
        HealthExamForm.HealthExamAdmin base = snapshot != null ? snapshot : profileAdmin(child.profile());
        HealthExamForm.HealthExamAdmin admin = new HealthExamForm.HealthExamAdmin(
                base.childIdNumber(),
                base.premature(),
                base.gestationWeeks(),
                base.ethnicity(),
                base.beneficiary(),
                base.paymentSource(),
                base.bloodType(),
                base.address(),
                hasCompanion,
                companion(hasCompanion, index, child.guardians()));
        return withParts(exam, null, admin, null);
    }

    private static HealthExamForm.HealthExamAdmin profileAdmin(ChildProfileDetailResponse p) {
        if (p == null) {
            return new HealthExamForm.HealthExamAdmin(
                    "", "", null, "", "", "", "", new HealthExamForm.HealthExamAddress("", "", ""), "", null);
        }
        return new HealthExamForm.HealthExamAdmin(
                trimmed(p.idNumber()),
                p.premature() == null ? "" : p.premature().name(),
                p.gestationWeeks() == null ? null : p.gestationWeeks().intValue(),
                trimmed(p.ethnicity()),
                trimmed(p.beneficiary()),
                trimmed(p.paymentSource()),
                trimmed(p.bloodType()),
                p.address() == null
                        ? new HealthExamForm.HealthExamAddress("", "", "")
                        : new HealthExamForm.HealthExamAddress(
                                trimmed(p.address().province()),
                                trimmed(p.address().ward()),
                                trimmed(p.address().street())),
                "",
                null);
    }

    private static HealthExamForm.HealthExamCompanion companion(
            String hasCompanion, Integer index, List<ChildGuardianResponse> guardians) {
        if (YES.equals(hasCompanion) && index != null && index >= 0 && index < guardians.size()) {
            ChildGuardianResponse g = guardians.get(index);
            return new HealthExamForm.HealthExamCompanion(
                    index,
                    trimmed(g.fullName()),
                    relationLabel(g.relation()),
                    trimmed(g.phone()),
                    trimmed(g.idNumber()));
        }
        return new HealthExamForm.HealthExamCompanion(null, "", "", "", "");
    }

    /** Nhãn quan hệ trên phiếu khám (Phụ lục II): Cha, Mẹ, Ông/bà, Anh/chị, Khác. */
    private static String relationLabel(GuardianRelationship relation) {
        if (relation == null) {
            return "Khác";
        }
        return switch (relation) {
            case FATHER -> "Cha";
            case MOTHER -> "Mẹ";
            case GRANDFATHER, GRANDMOTHER -> "Ông/bà";
            case SIBLING -> "Anh/chị";
            default -> "Khác";
        };
    }

    /** Mục tiền sử còn trống điền từ khai báo (Có ⇒ YES kèm nội dung/ngày tiếp xúc, Không ⇒ NO). */
    private static HealthExamForm withHistory(HealthExamForm exam, Optional<ChildMedicalHistory> declared) {
        if (declared.isEmpty()) {
            return exam;
        }
        ChildMedicalHistory d = declared.get();
        HealthExamForm.HealthExamHistory h = exam.history() != null
                ? exam.history()
                : new HealthExamForm.HealthExamHistory("", "", "", "", "", null);
        boolean selfBlank = trimmed(h.self()).isEmpty();
        boolean familyBlank = trimmed(h.family()).isEmpty();
        boolean tbBlank = trimmed(h.tbContact()).isEmpty();
        HealthExamForm.HealthExamHistory history = new HealthExamForm.HealthExamHistory(
                selfBlank ? answer(d.self()) : h.self(),
                selfBlank ? (d.self() == DeclarationState.REPORTED ? trimmed(d.selfText()) : "") : h.selfText(),
                familyBlank ? answer(d.family()) : h.family(),
                familyBlank ? (d.family() == DeclarationState.REPORTED ? trimmed(d.familyText()) : "") : h.familyText(),
                tbBlank ? answer(d.tbContact()) : h.tbContact(),
                tbBlank ? (d.tbContact() == DeclarationState.REPORTED ? d.tbContactDate() : null) : h.tbContactDate());
        return withParts(exam, null, null, history);
    }

    private static String answer(DeclarationState state) {
        if (state == DeclarationState.REPORTED) {
            return YES;
        }
        return state == DeclarationState.NONE_REPORTED ? NO : "";
    }

    /** Kết luận ghi tiền sử ngược về khai báo sức khỏe khi khác (HLT-06, HLT-08). */
    private void writeBackHistory(
            Long childId, HealthExamForm.HealthExamHistory h, LocalDate examDate, CurrentUser user, Instant now) {
        boolean selfYes = YES.equals(h.self());
        boolean familyYes = YES.equals(h.family());
        boolean tbYes = YES.equals(h.tbContact());
        ChildMedicalHistory next = new ChildMedicalHistory(
                state(selfYes),
                selfYes ? trimmed(h.selfText()) : null,
                state(familyYes),
                familyYes ? trimmed(h.familyText()) : null,
                state(tbYes),
                tbYes ? h.tbContactDate() : null);
        if (childIntakeService.findMedicalHistory(childId).filter(next::equals).isPresent()) {
            return;
        }
        childIntakeService.updateMedicalHistory(
                childId,
                next,
                HealthDeclarationAction.UPDATED_FROM_EXAM,
                "Cập nhật từ phiếu khám ngày " + DMY.format(examDate),
                user.userId(),
                now);
    }

    private static DeclarationState state(boolean yes) {
        return yes ? DeclarationState.REPORTED : DeclarationState.NONE_REPORTED;
    }

    /** Nháp không kiểm đủ phiếu, chỉ kiểm khoảng số đo (ràng buộc DB). */
    private static List<FieldErrorResponse> draftErrors(BigDecimal height, BigDecimal weight, BigDecimal head) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        if (outside(height, 40, 150)) {
            errors.add(new FieldErrorResponse("heightCm", "Nhập chiều cao/chiều dài từ 40 đến 150 cm"));
        }
        if (outside(weight, 3, 60)) {
            errors.add(new FieldErrorResponse("weightKg", "Nhập cân nặng từ 3 đến 60 kg"));
        }
        if (outside(head, 30, 60)) {
            errors.add(new FieldErrorResponse("headCircumferenceCm", "Nhập vòng đầu từ 30 đến 60 cm"));
        }
        return errors;
    }

    private static boolean outside(BigDecimal value, int min, int max) {
        return value != null
                && (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0);
    }

    /** Hôm nay, kẹp trong khoảng ngày của đợt. */
    private static LocalDate clamp(HealthCheckCampaign c, LocalDate today) {
        if (today.isBefore(c.getStartDate())) {
            return c.getStartDate();
        }
        return today.isAfter(c.getEndDate()) ? c.getEndDate() : today;
    }

    /** Bản sao phiếu thay bước, hành chính, tiền sử (null = giữ nguyên). */
    private static HealthExamForm withParts(
            HealthExamForm f,
            Integer step,
            HealthExamForm.HealthExamAdmin admin,
            HealthExamForm.HealthExamHistory history) {
        return new HealthExamForm(
                step != null ? step : f.step(),
                admin != null ? admin : f.admin(),
                history != null ? history : f.history(),
                f.vitals(),
                f.nutrition(),
                f.development(),
                f.immunization(),
                f.clinical(),
                f.conclusion());
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
