package com.carenest.service.eduplan.impl;

import com.carenest.dto.account.UpdateRolePermissionsRequest;
import com.carenest.dto.eduplan.EduWorkflowPermissionRequest;
import com.carenest.dto.eduplan.EduWorkflowPermissionResponse;
import com.carenest.dto.eduplan.EduWorkflowPolicyRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy.DocType;
import com.carenest.service.account.PermissionService;
import com.carenest.service.eduplan.EduWorkflowPermissionService;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tab Soạn/duyệt hồ sơ (EDU-108): người soạn và thứ tự duyệt ghi vào workflow_policy (EDU-54), ô tích ghi vào ma
 * trận quyền của role cấu hình được, có lịch sử như trang Vai trò mặc định. HT là role cố định nên luôn có mã.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduWorkflowPermissionServiceImpl implements EduWorkflowPermissionService {
    private static final String LOG_PREFIX = "[EduWorkflowPermissionServiceImpl]";
    private static final List<CareNestRole> MATRIX_ROLES =
            List.of(CareNestRole.VICE_PRINCIPAL, CareNestRole.TEAM_LEADER, CareNestRole.TEACHER);
    private static final Set<String> COLUMNS = Set.of("PRINCIPAL", "VICE_PRINCIPAL", "TEAM_LEADER", "TEACHER");

    private final EduWorkflowService workflowService;
    private final PermissionService permissionService;

    @Override
    @Transactional(readOnly = true)
    public EduWorkflowPermissionResponse getWorkflowPermissions(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getWorkflowPermissions|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            EduWorkflowPermissionResponse response = buildResponse();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public EduWorkflowPermissionResponse saveWorkflowPermissions(
            CurrentUser currentUser, EduWorkflowPermissionRequest request) {
        String logPrefix = LOG_PREFIX + "|saveWorkflowPermissions|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            Map<DocType, EduWorkflowPermissionRequest.Document> docs = validate(request);

            // EDU-54: savePolicy tự kiểm HT có phân công, revision và ràng buộc soạn/duyệt
            Map<String, EduWorkflowPolicyRequest.PolicyEntry> policies = new LinkedHashMap<>();
            docs.forEach((type, doc) -> policies.put(
                    type.key(), new EduWorkflowPolicyRequest.PolicyEntry(doc.creators(), doc.reviewers())));
            workflowService.savePolicy(currentUser, new EduWorkflowPolicyRequest(request.revision(), policies));

            Set<Permission> eduCodes = EnumSet.noneOf(Permission.class);
            for (DocType type : DocType.values()) {
                eduCodes.add(type.edit());
                eduCodes.add(type.approve());
            }
            for (CareNestRole role : MATRIX_ROLES) {
                Set<Permission> current = EnumSet.noneOf(Permission.class);
                permissionService.getRolePermissions(role).permissions().stream()
                        .filter(p -> p.isConfigurableFor(role))
                        .forEach(current::add);
                Set<Permission> wanted = EnumSet.copyOf(current);
                wanted.removeAll(eduCodes);
                docs.forEach((type, doc) -> {
                    if (doc.editRoles().contains(role.name())) wanted.add(type.edit());
                    if (doc.approveRoles().contains(role.name())) wanted.add(type.approve());
                });
                if (!wanted.equals(current)) {
                    permissionService.updateRolePermissions(
                            currentUser,
                            role,
                            new UpdateRolePermissionsRequest(new ArrayList<>(wanted), request.reason()));
                }
            }

            EduWorkflowPermissionResponse response = buildResponse();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // FR7.4: đủ 4 loại; vai trò hợp lệ; ô tích cấu hình được cho role; người soạn/duyệt (trừ HT) phải có mã tương ứng
    private static Map<DocType, EduWorkflowPermissionRequest.Document> validate(EduWorkflowPermissionRequest request) {
        Map<DocType, EduWorkflowPermissionRequest.Document> docs = new LinkedHashMap<>();
        for (EduWorkflowPermissionRequest.Document doc : request.documents()) {
            DocType type = Arrays.stream(DocType.values())
                    .filter(t -> t.name().equals(doc.docType()))
                    .findFirst()
                    .orElseThrow(() -> GlobalException.of(ApiCode.SIGNERS_INVALID));
            if (docs.put(type, doc) != null) throw GlobalException.of(ApiCode.SIGNERS_INVALID);
            if (doc.creators().isEmpty()
                    || doc.reviewers().isEmpty()
                    || doc.creators().stream().anyMatch(doc.reviewers()::contains)) {
                throw GlobalException.of(ApiCode.SIGNERS_INVALID);
            }
            requireRoles(doc.editRoles(), type.edit());
            requireRoles(doc.approveRoles(), type.approve());
            for (String creator : doc.creators()) {
                if (!"PRINCIPAL".equals(creator) && !doc.editRoles().contains(creator))
                    throw GlobalException.of(ApiCode.SIGNERS_INVALID);
            }
            for (String reviewer : doc.reviewers()) {
                if (!"PRINCIPAL".equals(reviewer) && !doc.approveRoles().contains(reviewer))
                    throw GlobalException.of(ApiCode.SIGNERS_INVALID);
            }
        }
        return docs;
    }

    private static void requireRoles(List<String> roles, Permission permission) {
        for (String role : roles) {
            if (!COLUMNS.contains(role)) throw GlobalException.of(ApiCode.SIGNERS_INVALID);
            if (!"PRINCIPAL".equals(role) && !permission.isConfigurableFor(CareNestRole.valueOf(role))) {
                throw GlobalException.of(ApiCode.PERMISSION_NOT_CONFIGURABLE_FOR_ROLE, permission, role);
            }
        }
    }

    private EduWorkflowPermissionResponse buildResponse() {
        Map<String, EduWorkflowPolicy.RolePolicy> policies = workflowService.loadAllPolicies();
        Map<CareNestRole, List<Permission>> granted = new HashMap<>();
        for (CareNestRole role : MATRIX_ROLES) {
            granted.put(role, permissionService.getRolePermissions(role).permissions());
        }
        List<EduWorkflowPermissionResponse.Document> documents = new ArrayList<>();
        for (DocType type : DocType.values()) {
            EduWorkflowPolicy.RolePolicy policy = policies.get(type.key());
            documents.add(new EduWorkflowPermissionResponse.Document(
                    type.name(),
                    type.key(),
                    type.edit().name(),
                    type.approve().name(),
                    policy.creators().stream().map(Enum::name).toList(),
                    policy.reviewers().stream().map(Enum::name).toList(),
                    rolesWith(type.edit(), granted),
                    rolesWith(type.approve(), granted)));
        }
        return new EduWorkflowPermissionResponse(workflowService.getPolicyRevision(), documents);
    }

    private static List<String> rolesWith(Permission permission, Map<CareNestRole, List<Permission>> granted) {
        List<String> roles = new ArrayList<>(List.of("PRINCIPAL"));
        for (CareNestRole role : MATRIX_ROLES) {
            if (granted.get(role).contains(permission)) roles.add(role.name());
        }
        return roles;
    }
}
