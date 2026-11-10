package com.carenest.service.account.impl;

import com.carenest.dto.account.GuardianPhoneChangeResponse;
import com.carenest.dto.account.PhoneChangeDecisionRequest;
import com.carenest.dto.child.GuardianChildSummary;
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
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.PhoneChangeStatus;
import com.carenest.utils.enums.SmsStatus;
import java.time.Clock;
import java.util.List;
import java.util.Map;
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
    public GuardianPhoneChangeResponse approve(CurrentUser actor, Long requestId) {
        String logPrefix = LOG_PREFIX + "|approve|userId=" + actor.userId() + "|requestId=" + requestId;
        log.info("{}|START", logPrefix);
        try {
            GuardianPhoneChangeRequest request = loadForDecision(actor, requestId);
            if (actor.userId().equals(request.getRequestedBy())) {
                throw GlobalException.of(ApiCode.PHONE_CHANGE_SELF_DECISION);
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
                notificationService.notifyUsers(
                        Set.of(locked.getRequestedBy()),
                        NotificationType.GUARDIAN_PHONE_CHANGE_DECIDED,
                        "Đã duyệt đổi số điện thoại phụ huynh",
                        actor.fullName() + " đã duyệt đổi số điện thoại đăng nhập của " + account.getFullName()
                                + ". Tài khoản mới đã gửi qua SMS tới số mới.",
                        locked.getChildId());
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
            if (!actor.userId().equals(locked.getRequestedBy())) {
                String guardianName = userAccountRepository
                        .findById(locked.getUserId())
                        .map(UserAccount::getFullName)
                        .orElse("phụ huynh");
                notificationService.notifyUsers(
                        Set.of(locked.getRequestedBy()),
                        NotificationType.GUARDIAN_PHONE_CHANGE_DECIDED,
                        "Không duyệt đổi số điện thoại phụ huynh",
                        actor.fullName() + " không duyệt đổi số điện thoại đăng nhập của " + guardianName
                                + (note == null ? "." : ": " + note),
                        locked.getChildId());
            }
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
