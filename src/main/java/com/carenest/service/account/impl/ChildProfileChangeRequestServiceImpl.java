package com.carenest.service.account.impl;

import com.carenest.dto.child.ChildProfileChangeCreateRequest;
import com.carenest.dto.child.ChildProfileChangeResponse;
import com.carenest.dto.child.GuardianChildSummary;
import com.carenest.dto.child.HealthDeclarationRequest;
import com.carenest.dto.child.UpdateChildRequest;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.entity.account.ChildProfileChangeRequest;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.ChildProfileChangeRequestRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.account.ChildProfileChangeRequestService;
import com.carenest.service.account.ChildProfileEditService;
import com.carenest.service.account.PermissionService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildRecordService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ChildEditRoles;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.ProfileChangeKind;
import com.carenest.utils.enums.ProfileChangeStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import tools.jackson.databind.ObjectMapper;

/**
 * CHD-11 (user chốt 2026-11-15): GV được HT cấp thêm CHILD_PROFILE_EDIT gửi yêu cầu sửa hồ sơ/khai báo sức khỏe trẻ lớp
 * mình; HT hoặc PHT điểm trường của trẻ có CHILD_PROFILE_EDIT duyệt thì áp đúng luồng sửa hiện có với tư cách người
 * duyệt. Mỗi trẻ một yêu cầu chờ mỗi loại; gửi lại thì hủy yêu cầu cũ (như đổi SĐT phụ huynh, AUTH-21).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildProfileChangeRequestServiceImpl implements ChildProfileChangeRequestService {
    private static final String LOG_PREFIX = "[ChildProfileChangeRequestServiceImpl]";
    private static final int MAX_NOTE = 500;

    private final ChildProfileChangeRequestRepository requestRepository;
    private final ChildRecordService childRecordService;
    private final ChildProfileEditService childProfileEditService;
    private final ChildIntakeService childIntakeService;
    private final StaffAccessPolicy staffAccessPolicy;
    private final StaffAssignmentService staffAssignmentService;
    private final PermissionService permissionService;
    private final AccountService accountService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional
    public ChildProfileChangeResponse submit(CurrentUser actor, Long childId, ChildProfileChangeCreateRequest request) {
        String logPrefix = LOG_PREFIX + "|submit|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            if (!ChildEditRoles.teacherOnly(actor)) {
                // BGH sửa trực tiếp, không qua duyệt
                throw GlobalException.of(ApiCode.FORBIDDEN);
            }
            ProfileChangeKind kind = request.kind();
            Object body;
            if (kind == ProfileChangeKind.PROFILE) {
                body = requireBody(request.profile(), "profile");
                childRecordService.prepareEdit(actor, childId, request.profile());
            } else {
                body = requireBody(request.declaration(), "declaration");
                childRecordService.checkDeclaration(actor, childId, request.declaration());
            }
            requestRepository
                    .findByChildIdAndKindAndStatus(childId, kind, ProfileChangeStatus.PENDING)
                    .ifPresent(pending -> {
                        pending.cancel();
                        requestRepository.saveAndFlush(pending);
                    });
            ChildProfileChangeRequest saved = requestRepository.save(ChildProfileChangeRequest.open(
                    childId, kind, objectMapper.writeValueAsString(body), actor.userId(), clock.instant()));

            GuardianChildSummary child = summaries(List.of(childId)).get(childId);
            Set<Long> approvers = approverIds(child == null ? null : child.campusId());
            approvers.remove(actor.userId());
            String what = kind == ProfileChangeKind.PROFILE ? "sửa hồ sơ" : "sửa khai báo sức khỏe";
            notificationService.notifyUsers(
                    approvers,
                    NotificationType.CHILD_PROFILE_CHANGE_REQUESTED,
                    "Yêu cầu sửa hồ sơ trẻ chờ duyệt",
                    actor.fullName() + " đề nghị " + what + " của " + (child == null ? "trẻ" : child.fullName())
                            + ". Duyệt ở màn Chờ duyệt.",
                    childId);
            ChildProfileChangeResponse response =
                    toResponses(actor, List.of(saved)).getFirst();
            log.info("{}|requestId={}|kind={}|END", logPrefix, saved.getId(), kind);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChildProfileChangeResponse> list(CurrentUser actor, ProfileChangeStatus status) {
        String logPrefix = LOG_PREFIX + "|list|userId=" + actor.userId() + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            List<ChildProfileChangeRequest> all = status == null
                    ? requestRepository.findAllByOrderByRequestedAtDescIdDesc()
                    : requestRepository.findAllByStatusOrderByRequestedAtDescIdDesc(status);
            Map<Long, GuardianChildSummary> children = summaries(
                    all.stream().map(ChildProfileChangeRequest::getChildId).toList());
            List<ChildProfileChangeRequest> visible = all.stream()
                    .filter(r -> canReview(actor, children.get(r.getChildId())))
                    .toList();
            List<ChildProfileChangeResponse> result = toResponses(actor, visible);
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ChildProfileChangeResponse get(CurrentUser actor, Long requestId) {
        String logPrefix = LOG_PREFIX + "|get|userId=" + actor.userId() + "|requestId=" + requestId;
        log.info("{}|START", logPrefix);
        try {
            ChildProfileChangeRequest request = requestRepository
                    .findById(requestId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.CHILD_PROFILE_CHANGE_NOT_FOUND));
            GuardianChildSummary child =
                    summaries(List.of(request.getChildId())).get(request.getChildId());
            // Không dò được id: người ngoài phạm vi thấy như không tồn tại
            if (!actor.userId().equals(request.getRequestedBy()) && !canReview(actor, child)) {
                throw GlobalException.of(ApiCode.CHILD_PROFILE_CHANGE_NOT_FOUND);
            }
            ChildProfileChangeResponse response =
                    toResponses(actor, List.of(request)).getFirst();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChildProfileChangeResponse> latestOfChild(CurrentUser actor, Long childId) {
        String logPrefix = LOG_PREFIX + "|latestOfChild|userId=" + actor.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            GuardianChildSummary child = summaries(List.of(childId)).get(childId);
            boolean reviewer = canReview(actor, child);
            Map<ProfileChangeKind, ChildProfileChangeRequest> latest = new LinkedHashMap<>();
            for (ChildProfileChangeRequest r :
                    requestRepository.findAllByChildIdOrderByRequestedAtDescIdDesc(childId)) {
                if (r.getStatus() != ProfileChangeStatus.CANCELLED
                        && (reviewer || actor.userId().equals(r.getRequestedBy()))) {
                    latest.putIfAbsent(r.getKind(), r);
                }
            }
            List<ChildProfileChangeResponse> result = toResponses(actor, List.copyOf(latest.values()));
            log.info("{}|count={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ChildProfileChangeResponse approve(CurrentUser actor, Long requestId) {
        String logPrefix = LOG_PREFIX + "|approve|userId=" + actor.userId() + "|requestId=" + requestId;
        log.info("{}|START", logPrefix);
        try {
            ChildProfileChangeRequest request = loadForDecision(actor, requestId);
            // Áp đúng luồng sửa hiện có với tư cách người duyệt: hợp lệ, lịch sử hồ sơ, yêu cầu đổi SĐT như HT/PHT sửa
            if (request.getKind() == ProfileChangeKind.PROFILE) {
                childProfileEditService.update(
                        actor,
                        request.getChildId(),
                        objectMapper.readValue(request.getPayload(), UpdateChildRequest.class));
            } else {
                childRecordService.saveDeclaration(
                        actor,
                        request.getChildId(),
                        objectMapper.readValue(request.getPayload(), HealthDeclarationRequest.class));
            }
            request.approve(actor.userId(), clock.instant());
            requestRepository.save(request);
            notifyRequester(actor, request, "Yêu cầu sửa hồ sơ trẻ đã được duyệt", "Hồ sơ đã cập nhật theo đề nghị.");
            ChildProfileChangeResponse response =
                    toResponses(actor, List.of(request)).getFirst();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ChildProfileChangeResponse reject(CurrentUser actor, Long requestId, String note) {
        String logPrefix = LOG_PREFIX + "|reject|userId=" + actor.userId() + "|requestId=" + requestId;
        log.info("{}|START", logPrefix);
        try {
            String trimmed = note == null ? "" : note.strip();
            if (trimmed.isEmpty() || trimmed.length() > MAX_NOTE) {
                throw GlobalException.withFieldErrors(
                        ApiCode.INVALID_REQUEST_DATA,
                        List.of(new FieldErrorResponse(
                                "note",
                                trimmed.isEmpty() ? "Nhập lý do từ chối" : "Lý do tối đa " + MAX_NOTE + " ký tự")));
            }
            ChildProfileChangeRequest request = loadForDecision(actor, requestId);
            request.reject(actor.userId(), trimmed, clock.instant());
            requestRepository.save(request);
            notifyRequester(actor, request, "Yêu cầu sửa hồ sơ trẻ bị từ chối", "Lý do: " + trimmed);
            ChildProfileChangeResponse response =
                    toResponses(actor, List.of(request)).getFirst();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private ChildProfileChangeRequest loadForDecision(CurrentUser actor, Long requestId) {
        ChildProfileChangeRequest request = requestRepository
                .lockById(requestId)
                .orElseThrow(() -> GlobalException.of(ApiCode.CHILD_PROFILE_CHANGE_NOT_FOUND));
        GuardianChildSummary child = summaries(List.of(request.getChildId())).get(request.getChildId());
        // Người gửi không tự duyệt yêu cầu của mình (AUTH-12)
        if (!canReview(actor, child) || actor.userId().equals(request.getRequestedBy())) {
            throw GlobalException.of(ApiCode.CHILD_PROFILE_CHANGE_DECIDE_DENIED);
        }
        if (!request.isPending()) {
            throw GlobalException.of(ApiCode.CHILD_PROFILE_CHANGE_NOT_PENDING);
        }
        if (child != null && child.status() == ChildStatus.LEFT) {
            throw GlobalException.of(ApiCode.CHILD_ALREADY_LEFT);
        }
        return request;
    }

    // HT toàn trường hoặc PHT điểm trường của trẻ, có CHILD_PROFILE_EDIT; GV không duyệt (CHD-11)
    private boolean canReview(CurrentUser actor, GuardianChildSummary child) {
        if (ChildEditRoles.teacherOnly(actor) || !actor.hasPermission(Permission.CHILD_PROFILE_EDIT)) {
            return false;
        }
        if (staffAccessPolicy.managesWholeSchool(actor)) {
            return true;
        }
        return child != null
                && child.campusId() != null
                && staffAccessPolicy.managedCampusIds(actor).contains(child.campusId());
    }

    // Người được báo: HT đang phân công, PHT đang phân công ở điểm trường của trẻ có CHILD_PROFILE_EDIT
    private Set<Long> approverIds(Long campusId) {
        LocalDate today = LocalDate.now(clock);
        Set<Long> ids = new HashSet<>(staffAssignmentService.findPrincipalIds(today));
        if (campusId != null) {
            for (Long userId : staffAssignmentService.findCampusVicePrincipalIds(campusId, today)) {
                if (permissionService.hasEffectivePermission(
                        userId, CareNestRole.VICE_PRINCIPAL, Permission.CHILD_PROFILE_EDIT)) {
                    ids.add(userId);
                }
            }
        }
        return ids;
    }

    private void notifyRequester(CurrentUser actor, ChildProfileChangeRequest request, String title, String body) {
        if (actor.userId().equals(request.getRequestedBy())) {
            return;
        }
        notificationService.notifyUsers(
                Set.of(request.getRequestedBy()),
                NotificationType.CHILD_PROFILE_CHANGE_DECIDED,
                title,
                body,
                request.getChildId());
    }

    private static Object requireBody(Object body, String field) {
        if (body == null) {
            throw GlobalException.withFieldErrors(
                    ApiCode.INVALID_REQUEST_DATA, List.of(new FieldErrorResponse(field, "Thiếu nội dung sửa")));
        }
        return body;
    }

    private Map<Long, GuardianChildSummary> summaries(Collection<Long> childIds) {
        List<Long> ids = childIds.stream().distinct().toList();
        return ids.isEmpty()
                ? Map.of()
                : childIntakeService.findChildSummaries(ids).stream()
                        .collect(Collectors.toMap(GuardianChildSummary::id, Function.identity()));
    }

    private List<ChildProfileChangeResponse> toResponses(CurrentUser actor, List<ChildProfileChangeRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<Long, GuardianChildSummary> children = summaries(
                requests.stream().map(ChildProfileChangeRequest::getChildId).toList());
        Map<Long, String> names = accountService.findFullNames(requests.stream()
                .flatMap(r -> Stream.of(r.getRequestedBy(), r.getDecidedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return requests.stream()
                .map(r -> {
                    GuardianChildSummary child = children.get(r.getChildId());
                    boolean profile = r.getKind() == ProfileChangeKind.PROFILE;
                    return new ChildProfileChangeResponse(
                            r.getId(),
                            r.getChildId(),
                            child == null ? null : child.code(),
                            child == null ? null : child.fullName(),
                            child == null ? null : child.className(),
                            child == null ? null : child.campusId(),
                            r.getKind(),
                            r.getStatus(),
                            profile ? objectMapper.readValue(r.getPayload(), UpdateChildRequest.class) : null,
                            profile ? null : objectMapper.readValue(r.getPayload(), HealthDeclarationRequest.class),
                            r.getRequestedBy(),
                            names.get(r.getRequestedBy()),
                            r.getRequestedAt(),
                            r.getDecidedBy(),
                            r.getDecidedBy() == null ? null : names.get(r.getDecidedBy()),
                            r.getDecidedAt(),
                            r.getDecisionNote(),
                            r.isPending() && !actor.userId().equals(r.getRequestedBy()) && canReview(actor, child));
                })
                .toList();
    }
}
