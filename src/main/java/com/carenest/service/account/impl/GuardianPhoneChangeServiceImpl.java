package com.carenest.service.account.impl;

import com.carenest.dto.account.GuardianPhoneChangeCreateRequest;
import com.carenest.dto.account.GuardianPhoneChangeResponse;
import com.carenest.dto.account.PhoneChangeDecisionRequest;
import com.carenest.dto.child.GuardianChildSummary;
import com.carenest.dto.child.GuardianPhoneTarget;
import com.carenest.dto.child.GuardianRecord;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.entity.account.GuardianPhoneChangeRequest;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.GuardianPhoneChangeRequestRepository;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.GuardianPhoneChangeService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildRecordService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.PhoneNumbers;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.PhoneChangeStatus;
import com.carenest.utils.enums.SmsStatus;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Duyệt/từ chối yêu cầu đổi SĐT phụ huynh (G94-180/AUTH-21, user chốt 2026-11-10). Duyệt: kiểm hết (không ghi) → đổi
 * username + mật khẩu tạm Keycloak, một giao dịch DB (khóa yêu cầu, tên đăng nhập, SĐT trên mọi hồ sơ, thông báo) → lỗi
 * thì trả username cũ → sau commit SMS tới số mới. Không log SĐT, mật khẩu — chỉ id.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardianPhoneChangeServiceImpl implements GuardianPhoneChangeService {
    private static final String LOG_PREFIX = "[GuardianPhoneChangeServiceImpl]";
    private static final int MAX_NOTE = 300;

    private final GuardianPhoneChangeRequestRepository requestRepository;
    private final UserAccountRepository userAccountRepository;
    private final ChildIntakeService childIntakeService;
    private final ChildRecordService childRecordService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final NotificationService notificationService;
    private final AccountLifecycleOperations accountLifecycle;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final ParentSmsSender smsSender;
    private final PhoneChangeSupport phoneChanges;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<GuardianPhoneChangeResponse> list(CurrentUser actor, PhoneChangeStatus status) {
        String logPrefix = LOG_PREFIX + "|list|userId=" + actor.userId() + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            List<GuardianPhoneChangeRequest> all = status == null
                    ? requestRepository.findAllByOrderByRequestedAtDescIdDesc()
                    : requestRepository.findAllByStatusOrderByRequestedAtDescIdDesc(status);
            Map<Long, GuardianChildSummary> children = phoneChanges.childrenOf(all);
            List<GuardianPhoneChangeRequest> visible = all.stream()
                    .filter(r -> {
                        GuardianChildSummary child = children.get(r.getChildId());
                        return phoneChanges.inScope(actor, child == null ? null : child.campusId());
                    })
                    .toList();
            List<GuardianPhoneChangeResponse> result = phoneChanges.toResponses(actor, visible, children);
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public GuardianPhoneChangeResponse request(
            CurrentUser actor, Long childId, Long guardianId, GuardianPhoneChangeCreateRequest body) {
        String logPrefix =
                LOG_PREFIX + "|request|userId=" + actor.userId() + "|childId=" + childId + "|guardianId=" + guardianId;
        log.info("{}|START", logPrefix);
        try {
            GuardianPhoneTarget target = childRecordService.findPhoneChangeTarget(actor, childId, guardianId);
            UserAccount account = target.accountId() == null
                    ? null
                    : userAccountRepository.findById(target.accountId()).orElse(null);

            List<FieldErrorResponse> errors = new ArrayList<>();
            String raw = body == null || body.newPhone() == null
                    ? ""
                    : body.newPhone().strip();
            String newPhone = PhoneNumbers.normalize(raw).orElse(null);
            if (raw.isEmpty()) {
                errors.add(new FieldErrorResponse("newPhone", "Nhập số điện thoại mới"));
            } else if (newPhone == null) {
                errors.add(new FieldErrorResponse("newPhone", "Nhập số điện thoại hợp lệ, ví dụ 0912 345 678"));
            } else if (newPhone.equals(target.phone())) {
                errors.add(new FieldErrorResponse("newPhone", "Số mới trùng số đang dùng"));
            } else if (account != null && account.getPrimaryRole() != CareNestRole.PARENT) {
                errors.add(new FieldErrorResponse("newPhone", ChildProfileEditServiceImpl.STAFF_PHONE));
            }
            String reason =
                    body == null || body.reason() == null ? "" : body.reason().strip();
            if (reason.isEmpty()) {
                errors.add(new FieldErrorResponse("reason", "Nhập lý do đổi số điện thoại"));
            } else if (reason.length() > MAX_NOTE) {
                errors.add(new FieldErrorResponse("reason", "Lý do tối đa " + MAX_NOTE + " ký tự"));
            }
            if (!errors.isEmpty()) {
                throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
            }
            String conflict = phoneConflict(target, account, newPhone);
            if (conflict != null) {
                throw GlobalException.withFieldErrors(
                        ApiCode.GUARDIAN_IDENTITY_CONFLICT, List.of(new FieldErrorResponse("newPhone", conflict)));
            }

            GuardianPhoneChangeRequest opened = phoneChanges.open(actor, target, account, newPhone, reason);
            GuardianPhoneChangeResponse response = toResponse(actor, opened);
            log.info("{}|requestId={}|withAccount={}|END", logPrefix, opened.getId(), account != null);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public GuardianPhoneChangeResponse approve(CurrentUser actor, Long requestId) {
        String logPrefix = LOG_PREFIX + "|approve|userId=" + actor.userId() + "|requestId=" + requestId;
        log.info("{}|START", logPrefix);
        try {
            GuardianPhoneChangeRequest request = loadForDecision(actor, requestId);
            if (request.getUserId() == null) {
                GuardianPhoneChangeResponse response = approveRecordOnly(actor, request);
                log.info("{}|withAccount=false|END", logPrefix);
                return response;
            }
            UserAccount account = userAccountRepository
                    .findById(request.getUserId())
                    .orElseThrow(() -> stale("Không còn tài khoản phụ huynh này. Từ chối yêu cầu."));
            checkStillValid(request, account);

            String temporaryPassword = passwordGenerator.generate(); // carenest:allow-secret
            accountLifecycle.changeUsername(logPrefix, account, request.getNewPhone(), temporaryPassword, () -> {
                GuardianPhoneChangeRequest locked = requestRepository
                        .lockById(requestId)
                        .orElseThrow(() -> GlobalException.of(ApiCode.PHONE_CHANGE_REQUEST_NOT_FOUND));
                if (!locked.isPending()) {
                    throw GlobalException.of(ApiCode.PHONE_CHANGE_REQUEST_NOT_PENDING);
                }
                if (userAccountRepository.updateUsername(account.getId(), locked.getOldPhone(), locked.getNewPhone())
                        != 1) {
                    throw stale("Tài khoản đã đổi số điện thoại sau khi gửi yêu cầu. Từ chối yêu cầu này.");
                }
                childRecordService.changeGuardianPhone(account.getId(), locked.getNewPhone());
                locked.approve(actor.userId(), clock.instant());
                requestRepository.save(locked);
                notifyRequester(
                        actor,
                        locked,
                        "Đã duyệt đổi số điện thoại phụ huynh",
                        actor.fullName() + " đã duyệt đổi số điện thoại đăng nhập của " + account.getFullName()
                                + ". Tài khoản mới đã gửi qua SMS tới số mới.");
                return null;
            });

            // Sau commit: SMS tên đăng nhập mới + mật khẩu tạm tới SĐT mới; lỗi chỉ ghi FAILED (cấp lại mật khẩu sau)
            UserAccount renamed = userAccountRepository
                    .findById(account.getId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.ACCOUNT_NOT_FOUND));
            SmsStatus sms = smsSender.sendUsernameChanged(renamed, temporaryPassword);
            GuardianPhoneChangeRequest approved = transactionTemplate.execute(status -> {
                GuardianPhoneChangeRequest r = requestRepository
                        .findById(requestId)
                        .orElseThrow(() -> GlobalException.of(ApiCode.PHONE_CHANGE_REQUEST_NOT_FOUND));
                r.recordSms(sms);
                return requestRepository.save(r);
            });
            GuardianPhoneChangeResponse response = toResponse(actor, approved);
            log.info("{}|accountId={}|sms={}|END", logPrefix, account.getId(), sms);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public GuardianPhoneChangeResponse reject(CurrentUser actor, Long requestId, PhoneChangeDecisionRequest request) {
        String logPrefix = LOG_PREFIX + "|reject|userId=" + actor.userId() + "|requestId=" + requestId;
        log.info("{}|START", logPrefix);
        try {
            loadForDecision(actor, requestId);
            String note = request == null || request.note() == null
                    ? null
                    : request.note().strip();
            if (note != null && note.isEmpty()) {
                note = null;
            }
            if (note != null && note.length() > MAX_NOTE) {
                throw GlobalException.withFieldErrors(
                        ApiCode.INVALID_REQUEST_DATA,
                        List.of(new FieldErrorResponse("note", "Ghi chú tối đa " + MAX_NOTE + " ký tự")));
            }
            GuardianPhoneChangeRequest locked = requestRepository
                    .lockById(requestId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.PHONE_CHANGE_REQUEST_NOT_FOUND));
            if (!locked.isPending()) {
                throw GlobalException.of(ApiCode.PHONE_CHANGE_REQUEST_NOT_PENDING);
            }
            locked.reject(actor.userId(), note, clock.instant());
            requestRepository.save(locked);
            notifyRequester(
                    actor,
                    locked,
                    "Không duyệt đổi số điện thoại phụ huynh",
                    actor.fullName() + " không duyệt đổi số điện thoại của " + guardianName(locked)
                            + (note == null ? "." : ": " + note));
            GuardianPhoneChangeResponse response = toResponse(actor, locked);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Yêu cầu đang chờ trong phạm vi người quyết định (ngoài phạm vi ⇒ 403 như quản lý phụ huynh của trẻ). */
    private GuardianPhoneChangeRequest loadForDecision(CurrentUser actor, Long requestId) {
        GuardianPhoneChangeRequest request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> GlobalException.of(ApiCode.PHONE_CHANGE_REQUEST_NOT_FOUND));
        GuardianChildSummary child = phoneChanges.childrenOf(List.of(request)).get(request.getChildId());
        if (!phoneChanges.inScope(actor, child == null ? null : child.campusId())) {
            throw GlobalException.of(ApiCode.CHILD_GUARDIAN_MANAGE_DENIED);
        }
        if (!request.isPending()) {
            throw GlobalException.of(ApiCode.PHONE_CHANGE_REQUEST_NOT_PENDING);
        }
        if (child != null && child.status() == ChildStatus.LEFT) {
            throw stale("Trẻ đã nghỉ học. Từ chối yêu cầu này.");
        }
        return request;
    }

    /** Từ lúc gửi tới lúc duyệt: tài khoản còn tên cũ, còn liên kết trẻ, số mới chưa thuộc người khác (AUTH-18). */
    private void checkStillValid(GuardianPhoneChangeRequest request, UserAccount account) {
        if (!account.getUsername().equals(request.getOldPhone())) {
            throw stale("Tài khoản đã đổi số điện thoại sau khi gửi yêu cầu. Từ chối yêu cầu này.");
        }
        if (!guardianChildLinkService.isGuardianOf(account.getId(), request.getChildId())) {
            throw stale("Phụ huynh không còn liên kết với trẻ. Từ chối yêu cầu này.");
        }
        if (userAccountRepository.existsByUsername(request.getNewPhone())) {
            throw stale("Số điện thoại mới đã là tên đăng nhập của tài khoản khác. Từ chối yêu cầu này.");
        }
        List<GuardianRecord> others =
                childIntakeService.findGuardianRecords(List.of(request.getNewPhone()), List.of("-"));
        boolean takenByOther = others.stream()
                .anyMatch(r -> !account.getId().equals(r.userId())
                        && r.citizenId() != null
                        && !r.citizenId().equals(account.getCitizenId()));
        if (takenByOther) {
            throw stale("Số điện thoại mới đã thuộc phụ huynh khác trên hồ sơ trẻ. Từ chối yêu cầu này.");
        }
    }

    /**
     * Phụ huynh chưa có tài khoản: chỉ đổi SĐT dòng phụ huynh trên hồ sơ (không Keycloak, không SMS). Kiểm lại như lúc
     * gửi: phụ huynh còn trên hồ sơ, vẫn chưa có tài khoản, hồ sơ còn số cũ, số mới chưa thuộc người khác.
     */
    private GuardianPhoneChangeResponse approveRecordOnly(CurrentUser actor, GuardianPhoneChangeRequest request) {
        GuardianPhoneTarget target = request.getGuardianId() == null
                ? null
                : childRecordService
                        .findGuardian(request.getGuardianId())
                        .filter(t -> t.childId().equals(request.getChildId()))
                        .orElse(null);
        if (target == null) {
            throw stale("Phụ huynh không còn trên hồ sơ trẻ. Từ chối yêu cầu này.");
        }
        if (target.accountId() != null) {
            throw stale("Phụ huynh đã có tài khoản sau khi gửi yêu cầu. Từ chối rồi gửi lại yêu cầu.");
        }
        if (!target.phone().equals(request.getOldPhone())) {
            throw stale("Số điện thoại trên hồ sơ đã đổi sau khi gửi yêu cầu. Từ chối yêu cầu này.");
        }
        if (phoneConflict(target, null, request.getNewPhone()) != null) {
            throw stale("Số điện thoại mới đã thuộc người khác. Từ chối yêu cầu này.");
        }
        GuardianPhoneChangeRequest approved = transactionTemplate.execute(status -> {
            GuardianPhoneChangeRequest locked = requestRepository
                    .lockById(request.getId())
                    .orElseThrow(() -> GlobalException.of(ApiCode.PHONE_CHANGE_REQUEST_NOT_FOUND));
            if (!locked.isPending()) {
                throw GlobalException.of(ApiCode.PHONE_CHANGE_REQUEST_NOT_PENDING);
            }
            if (childRecordService.changeGuardianRecordPhone(
                            target.guardianId(), locked.getOldPhone(), locked.getNewPhone())
                    != 1) {
                throw stale("Số điện thoại trên hồ sơ đã đổi sau khi gửi yêu cầu. Từ chối yêu cầu này.");
            }
            locked.approve(actor.userId(), clock.instant());
            GuardianPhoneChangeRequest saved = requestRepository.save(locked);
            notifyRequester(
                    actor,
                    saved,
                    "Đã duyệt đổi số điện thoại phụ huynh",
                    actor.fullName() + " đã duyệt đổi số điện thoại của " + target.fullName() + ".");
            return saved;
        });
        return toResponse(actor, approved);
    }

    /**
     * Số mới không được thuộc người khác (AUTH-18): phụ huynh khác của cùng trẻ; tên đăng nhập của tài khoản khác (trừ
     * chính tài khoản của phụ huynh, hoặc tài khoản cùng CCCD khi phụ huynh chưa liên kết); phụ huynh khác CCCD trên hồ sơ
     * trẻ khác. Trả câu lỗi, null khi hợp lệ.
     */
    private String phoneConflict(GuardianPhoneTarget target, UserAccount account, String newPhone) {
        if (target.otherGuardianPhones().contains(newPhone)) {
            return "Số này đang là của phụ huynh khác của trẻ.";
        }
        Optional<UserAccount> owner = userAccountRepository.findByUsername(newPhone);
        if (owner.isPresent()) {
            UserAccount o = owner.get();
            boolean same = account != null
                    ? o.getId().equals(account.getId())
                    : o.getCitizenId() != null && o.getCitizenId().equals(target.citizenId());
            if (!same) {
                return ChildProfileEditServiceImpl.USERNAME_TAKEN;
            }
        }
        boolean takenByOther = childIntakeService.findGuardianRecords(List.of(newPhone), List.of("-")).stream()
                .anyMatch(r -> !target.childId().equals(r.childId())
                        && r.citizenId() != null
                        && !r.citizenId().equals(target.citizenId())
                        && (account == null || !account.getId().equals(r.userId())));
        return takenByOther ? ChildEnrollmentServiceImpl.PHONE_HAS_OTHER_ID : null;
    }

    // Người gửi tự quyết định (AUTH-21 sửa 2026-11-11) thì không tự báo mình
    private void notifyRequester(CurrentUser actor, GuardianPhoneChangeRequest request, String title, String body) {
        if (actor.userId().equals(request.getRequestedBy())) {
            return;
        }
        notificationService.notifyUsers(
                Set.of(request.getRequestedBy()),
                NotificationType.GUARDIAN_PHONE_CHANGE_DECIDED,
                title,
                body,
                request.getChildId());
    }

    private String guardianName(GuardianPhoneChangeRequest request) {
        if (request.getUserId() != null) {
            return userAccountRepository
                    .findById(request.getUserId())
                    .map(UserAccount::getFullName)
                    .orElse("phụ huynh");
        }
        return request.getGuardianId() == null
                ? "phụ huynh"
                : childRecordService
                        .findGuardianNames(List.of(request.getGuardianId()))
                        .getOrDefault(request.getGuardianId(), "phụ huynh");
    }

    private GuardianPhoneChangeResponse toResponse(CurrentUser actor, GuardianPhoneChangeRequest request) {
        List<GuardianPhoneChangeRequest> one = List.of(request);
        return phoneChanges
                .toResponses(actor, one, phoneChanges.childrenOf(one))
                .getFirst();
    }

    private static GlobalException stale(String message) {
        return GlobalException.of(ApiCode.PHONE_CHANGE_REQUEST_STALE, message);
    }
}
