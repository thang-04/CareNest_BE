package com.carenest.service.meals.impl;

import com.carenest.dto.meals.InspectionApprovalDecisionRequest;
import com.carenest.dto.meals.InspectionApprovalRequest;
import com.carenest.dto.meals.InspectionApprovalResponse;
import com.carenest.dto.meals.InspectionDayResponse;
import com.carenest.entity.meals.InspectionDayGrant;
import com.carenest.exception.GlobalException;
import com.carenest.repository.meals.FoodSampleRepository;
import com.carenest.repository.meals.InspectionDayGrantRepository;
import com.carenest.repository.meals.InspectionSheetRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.meals.InspectionGrantService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.Constants;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.InspectionGrantStatus;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * INS-05: ngày đã qua 00:00 (đồng hồ BE, múi giờ trường) chỉ ghi biên bản kiểm thực khi có quyền mở còn hạn. PHT gửi
 * yêu cầu cho một mẫu–ngày–điểm trường; HT duyệt, từ chối, thu hồi hoặc tự mở. Mọi thay đổi chạy dưới khóa điểm trường
 * (cùng khóa với lưu, ký, sửa lại biên bản) nên không lệch với thao tác ghi đang chạy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InspectionGrantServiceImpl implements InspectionGrantService {
    private static final String LOG_PREFIX = "[InspectionGrantServiceImpl]";
    private static final List<InspectionGrantStatus> OPEN =
            List.of(InspectionGrantStatus.PENDING, InspectionGrantStatus.APPROVED);

    private final InspectionDayGrantRepository grantRepository;
    private final InspectionSheetRepository sheetRepository;
    private final FoodSampleRepository campusLookup;
    private final CampusAccessPolicy campusAccessPolicy;
    private final Clock clock;

    @Override
    public InspectionApprovalResponse request(
            CurrentUser currentUser,
            Long campusId,
            LocalDate date,
            InspectionTab tab,
            InspectionApprovalRequest request) {
        String logPrefix =
                LOG_PREFIX + "|request|userId=" + currentUser.userId() + "|campusId=" + campusId + "|tab=" + tab;
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            LocalDate today = LocalDate.now(clock);
            requireEditor(currentUser, campusId);
            if (date == null || date.isAfter(today)) {
                throw GlobalException.of(ApiCode.INSPECTION_DATE_FUTURE);
            }
            if (!date.isBefore(today)) {
                throw GlobalException.of(ApiCode.INSPECTION_APPROVAL_NOT_NEEDED);
            }
            String reason = requireText(request == null ? null : request.reason());
            sheetRepository.lockCampus(campusId);
            freeSlot(campusId, date, tab, now);
            InspectionDayGrant grant;
            if (currentUser.hasRole(CareNestRole.PRINCIPAL)) {
                Instant expiresAt = expiryFrom(now, request == null ? null : request.validHours());
                grant = InspectionDayGrant.selfOpen(
                        campusId, date, tab, reason, currentUser.userId(), nameOf(currentUser), now, expiresAt);
            } else {
                grant = InspectionDayGrant.request(
                        campusId, date, tab, reason, currentUser.userId(), nameOf(currentUser), now);
            }
            InspectionApprovalResponse response = toResponse(grantRepository.saveAndFlush(grant));
            log.info("{}|selfOpened={}|END", logPrefix, response.selfOpened());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public InspectionApprovalResponse approve(
            CurrentUser currentUser, Long approvalId, InspectionApprovalDecisionRequest request) {
        String logPrefix = LOG_PREFIX + "|approve|userId=" + currentUser.userId() + "|approvalId=" + approvalId;
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            InspectionDayGrant grant = lockedGrant(currentUser, approvalId);
            if (grant.getStatus() != InspectionGrantStatus.PENDING) {
                throw GlobalException.of(ApiCode.INSPECTION_APPROVAL_STATE_INVALID);
            }
            grant.approve(
                    currentUser.userId(),
                    nameOf(currentUser),
                    optionalNote(request),
                    now,
                    expiryFrom(now, request == null ? null : request.validHours()));
            InspectionApprovalResponse response = toResponse(grantRepository.saveAndFlush(grant));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public InspectionApprovalResponse reject(
            CurrentUser currentUser, Long approvalId, InspectionApprovalDecisionRequest request) {
        String logPrefix = LOG_PREFIX + "|reject|userId=" + currentUser.userId() + "|approvalId=" + approvalId;
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            String note = requireText(request == null ? null : request.note());
            InspectionDayGrant grant = lockedGrant(currentUser, approvalId);
            if (grant.getStatus() != InspectionGrantStatus.PENDING) {
                throw GlobalException.of(ApiCode.INSPECTION_APPROVAL_STATE_INVALID);
            }
            grant.reject(currentUser.userId(), nameOf(currentUser), note, now);
            InspectionApprovalResponse response = toResponse(grantRepository.saveAndFlush(grant));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public InspectionApprovalResponse revoke(
            CurrentUser currentUser, Long approvalId, InspectionApprovalDecisionRequest request) {
        String logPrefix = LOG_PREFIX + "|revoke|userId=" + currentUser.userId() + "|approvalId=" + approvalId;
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            String note = requireText(request == null ? null : request.note());
            InspectionDayGrant grant = lockedGrant(currentUser, approvalId);
            if (grant.getStatus() != InspectionGrantStatus.APPROVED) {
                throw GlobalException.of(ApiCode.INSPECTION_APPROVAL_STATE_INVALID);
            }
            grant.revoke(currentUser.userId(), nameOf(currentUser), note, now);
            InspectionApprovalResponse response = toResponse(grantRepository.saveAndFlush(grant));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionApprovalResponse> queue(
            CurrentUser currentUser, Long campusId, InspectionGrantStatus status) {
        String logPrefix = LOG_PREFIX + "|queue|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            requirePrincipal(currentUser);
            InspectionGrantStatus wanted = status == null ? InspectionGrantStatus.PENDING : status;
            List<InspectionApprovalResponse> queue =
                    grantRepository
                            .findQueue(wanted, campusId, PageRequest.of(0, Constants.Inspection.QUEUE_LIMIT))
                            .stream()
                            .map(InspectionGrantServiceImpl::toResponse)
                            .toList();
            log.info("{}|count={}|END", logPrefix, queue.size());
            return queue;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionDayResponse.TabApproval> dayApprovals(
            CurrentUser currentUser, Long campusId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|dayApprovals|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            boolean past = date.isBefore(LocalDate.now(clock));
            boolean editor = campusAccessPolicy.canEditCampus(currentUser, Permission.FOOD_SAFETY_APPROVE, campusId);
            boolean principal = currentUser.hasRole(CareNestRole.PRINCIPAL);
            // Dòng mới nhất của từng mẫu quyết định trạng thái; ngày chưa qua thì không bao giờ khóa
            Map<InspectionTab, InspectionDayGrant> latest = new EnumMap<>(InspectionTab.class);
            if (past) {
                grantRepository
                        .findByCampusIdAndSheetDateOrderByIdDesc(campusId, date)
                        .forEach(grant -> latest.putIfAbsent(grant.getTab(), grant));
            }
            List<InspectionDayResponse.TabApproval> approvals = new ArrayList<>();
            for (InspectionTab tab : InspectionTab.values()) {
                InspectionDayGrant grant = latest.get(tab);
                boolean usable = grant != null && grant.isUsable(now);
                boolean locked = past && !usable;
                InspectionGrantStatus status = grant == null ? null : shownStatus(grant, usable);
                boolean canRequest = locked && editor && status != InspectionGrantStatus.PENDING;
                approvals.add(new InspectionDayResponse.TabApproval(
                        tab,
                        locked,
                        status,
                        grant == null ? null : grant.getId(),
                        grant == null ? null : grant.getReason(),
                        grant == null ? null : grant.getRequestedByName(),
                        grant == null ? null : grant.getDecisionNote(),
                        grant == null ? null : grant.getExpiresAt(),
                        canRequest,
                        principal));
            }
            log.info("{}|END", logPrefix);
            return approvals;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void requireOpenDay(Long campusId, LocalDate date, InspectionTab tab) {
        String logPrefix = LOG_PREFIX + "|requireOpenDay|campusId=" + campusId + "|tab=" + tab;
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            if (date.isBefore(LocalDate.now(clock))) {
                boolean open = grantRepository
                        .findByCampusIdAndSheetDateAndTabAndStatusIn(campusId, date, tab, OPEN)
                        .filter(grant -> grant.isUsable(now))
                        .isPresent();
                if (!open) {
                    throw GlobalException.of(ApiCode.INSPECTION_APPROVAL_REQUIRED);
                }
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void consume(Long campusId, LocalDate date, InspectionTab tab) {
        String logPrefix = LOG_PREFIX + "|consume|campusId=" + campusId + "|tab=" + tab;
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            if (date.isBefore(LocalDate.now(clock))) {
                grantRepository
                        .findByCampusIdAndSheetDateAndTabAndStatusIn(campusId, date, tab, OPEN)
                        .filter(grant -> grant.getStatus() == InspectionGrantStatus.APPROVED)
                        .ifPresent(grant -> {
                            grant.consume(now);
                            grantRepository.saveAndFlush(grant);
                        });
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Dòng đang mở còn hiệu lực hoặc đang chờ ⇒ từ chối yêu cầu mới; quá hạn thì đóng lại để nhường chỗ (INS-05). */
    private void freeSlot(Long campusId, LocalDate date, InspectionTab tab, Instant now) {
        grantRepository
                .findByCampusIdAndSheetDateAndTabAndStatusIn(campusId, date, tab, OPEN)
                .ifPresent(open -> {
                    if (open.getStatus() == InspectionGrantStatus.PENDING) {
                        throw GlobalException.of(ApiCode.INSPECTION_APPROVAL_PENDING);
                    }
                    if (open.isUsable(now)) {
                        throw GlobalException.of(ApiCode.INSPECTION_APPROVAL_ACTIVE);
                    }
                    open.expire();
                    grantRepository.saveAndFlush(open);
                });
    }

    /** Chỉ HT quyết định; khóa điểm trường rồi mới đọc dòng để thấy trạng thái sau cùng của thao tác ký đang chạy. */
    private InspectionDayGrant lockedGrant(CurrentUser currentUser, Long approvalId) {
        requirePrincipal(currentUser);
        Long campusId = grantRepository
                .findCampusId(approvalId)
                .orElseThrow(() -> GlobalException.of(ApiCode.INSPECTION_APPROVAL_NOT_FOUND));
        requireEditor(currentUser, campusId);
        sheetRepository.lockCampus(campusId);
        return grantRepository
                .findById(approvalId)
                .orElseThrow(() -> GlobalException.of(ApiCode.INSPECTION_APPROVAL_NOT_FOUND));
    }

    private void requirePrincipal(CurrentUser currentUser) {
        if (!currentUser.hasRole(CareNestRole.PRINCIPAL)) {
            throw GlobalException.of(ApiCode.INSPECTION_APPROVAL_DENIED);
        }
    }

    // INS-01, AUTH-20: yêu cầu mở cũng cần mã FOOD_SAFETY_APPROVE ở điểm trường đó
    private void requireEditor(CurrentUser currentUser, Long campusId) {
        if (!campusLookup.campusExists(campusId)) {
            throw GlobalException.of(ApiCode.CAMPUS_NOT_FOUND);
        }
        if (!campusAccessPolicy.canEditCampus(currentUser, Permission.FOOD_SAFETY_APPROVE, campusId)) {
            throw GlobalException.of(ApiCode.INSPECTION_ACCESS_DENIED);
        }
    }

    private static String requireText(String text) {
        String clean = text == null ? "" : text.strip();
        if (clean.length() < Constants.Inspection.REASON_MIN) {
            throw GlobalException.of(ApiCode.INSPECTION_APPROVAL_REASON_REQUIRED);
        }
        return clean;
    }

    private static String optionalNote(InspectionApprovalDecisionRequest request) {
        String note =
                request == null || request.note() == null ? "" : request.note().strip();
        return note.isEmpty() ? null : note;
    }

    private static Instant expiryFrom(Instant now, Integer validHours) {
        int hours = validHours == null
                ? Constants.Inspection.GRANT_DEFAULT_HOURS
                : Math.min(validHours, Constants.Inspection.GRANT_MAX_HOURS);
        return now.plus(hours, ChronoUnit.HOURS);
    }

    private static InspectionGrantStatus shownStatus(InspectionDayGrant grant, boolean usable) {
        // Quá hạn chỉ tính lười ở bảng khi có yêu cầu mới; hiển thị cho người xem luôn là EXPIRED
        return grant.getStatus() == InspectionGrantStatus.APPROVED && !usable
                ? InspectionGrantStatus.EXPIRED
                : grant.getStatus();
    }

    private static String nameOf(CurrentUser currentUser) {
        String name = currentUser.fullName();
        return name == null || name.isBlank() ? currentUser.username() : name;
    }

    private static InspectionApprovalResponse toResponse(InspectionDayGrant grant) {
        return new InspectionApprovalResponse(
                grant.getId(),
                grant.getCampusId(),
                grant.getSheetDate(),
                grant.getTab(),
                grant.getStatus(),
                grant.isSelfOpened(),
                grant.getReason(),
                grant.getRequestedByName(),
                grant.getRequestedAt(),
                grant.getDecidedByName(),
                grant.getDecidedAt(),
                grant.getDecisionNote(),
                grant.getExpiresAt());
    }
}
