package com.carenest.controller.child;

import com.carenest.dto.child.ChildEnrollmentResponse;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.DuplicateCheckResponse;
import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.GuardianCheckRequest;
import com.carenest.dto.child.ParentAccountLookupResponse;
import com.carenest.dto.child.PlacementClassResponse;
import com.carenest.dto.child.ReadmitChildRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.ChildEnrollmentService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tiếp nhận trẻ (CHD-01..CHD-04, CHD-10, AUTH-18, AUTH-20; plan 2026-10-28 Phase 2–3). Quyền ở đây là lớp 1; phạm vi
 * điểm trường kiểm ở service. Không log họ tên, ngày sinh, SĐT, CCCD.
 */
@RestController
@RequestMapping("/children")
@RequiredArgsConstructor
@Slf4j
public class ChildEnrollmentController {
    private static final String LOG_PREFIX = "[ChildEnrollmentController]";

    private final ChildIntakeService childIntakeService;
    private final ChildEnrollmentService childEnrollmentService;

    @GetMapping("/duplicate-check")
    @PreAuthorize("@permissionGuard.has('CHILD_ENROLL')")
    public ResponseEntity<ResponseJson<DuplicateCheckResponse>> checkDuplicate(
            @RequestParam(required = false) @Size(max = 150) String fullName,
            @RequestParam(required = false) @Size(max = 10) String dateOfBirth,
            @RequestParam(required = false) @Size(max = 3) List<String> guardianIdNumbers,
            @RequestParam(required = false) Long excludeId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|checkDuplicate|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        DuplicateCheckResponse duplicate =
                childIntakeService.checkDuplicate(currentUser, fullName, dateOfBirth, guardianIdNumbers, excludeId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Check duplicate child success", duplicate);
    }

    @PostMapping("/guardian-check")
    @PreAuthorize("@permissionGuard.has('CHILD_ENROLL')")
    public ResponseEntity<ResponseJson<Void>> checkGuardians(
            @RequestBody GuardianCheckRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|checkGuardians|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        childEnrollmentService.checkGuardians(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Check guardians success");
    }

    // CCCD trên query theo contract FE (GET); không log tham số
    @GetMapping("/parent-accounts/by-citizen-id")
    @PreAuthorize("@permissionGuard.hasAny('CHILD_ENROLL', 'CHILD_PROFILE_EDIT')")
    public ResponseEntity<ResponseJson<ParentAccountLookupResponse>> findParentAccount(
            @RequestParam @Size(max = 20) String idNumber, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|findParentAccount|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        ParentAccountLookupResponse account = childEnrollmentService.findParentAccount(currentUser, idNumber);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Find parent account success", account);
    }

    @GetMapping("/placement-classes")
    @PreAuthorize("@permissionGuard.hasAny('CHILD_ENROLL', 'CHILD_PLACEMENT')")
    public ResponseEntity<ResponseJson<List<PlacementClassResponse>>> listPlacementClasses(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listPlacementClasses|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<PlacementClassResponse> classes = childIntakeService.listPlacementClasses(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "List placement classes success", classes);
    }

    @PostMapping("/enrollments")
    @PreAuthorize("@permissionGuard.has('CHILD_ENROLL')")
    public ResponseEntity<ResponseJson<ChildEnrollmentResponse>> enroll(
            @RequestBody EnrollChildRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|enroll|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        ChildEnrollmentResponse enrolled = childEnrollmentService.enroll(currentUser, request);
        log.info("{}|childId={}|END", logPrefix, enrolled.child().id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Enroll child success", enrolled);
    }

    @PostMapping("/{childId}/readmit")
    @PreAuthorize("@permissionGuard.has('CHILD_ENROLL')")
    public ResponseEntity<ResponseJson<ChildViewResponse>> readmit(
            @PathVariable Long childId, @RequestBody ReadmitChildRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|readmit|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        ChildViewResponse child = childIntakeService.readmit(currentUser, childId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Readmit child success", child);
    }
}
