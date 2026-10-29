package com.carenest.controller.child;

import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.dto.child.ChildSummaryResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/children")
@RequiredArgsConstructor
@Slf4j
public class ChildController {
    private static final String LOG_PREFIX = "[ChildController]";

    private final ChildService childService;

    // Danh sách để chọn trẻ khi cấp tài khoản phụ huynh (AUTH-18); phạm vi campus lọc ở service (AUTH-08)
    @GetMapping
    @PreAuthorize("@permissionGuard.has('PARENT_ACCOUNT_MANAGE')")
    public ResponseEntity<ResponseJson<PageResponse<ChildSummaryResponse>>> searchChildren(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) Long classroomId,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|searchChildren|userId=" + currentUser.userId() + "|campusId=" + campusId
                + "|classroomId=" + classroomId;
        log.info("{}|START", logPrefix);
        PageResponse<ChildSummaryResponse> children =
                childService.searchChildren(currentUser, campusId, classroomId, q, page, size);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Search children success", children);
    }

    // Quyền chỉ là lớp 1; ChildService kiểm phạm vi lớp/liên kết của đúng trẻ này (lớp 2)
    @GetMapping("/{childId}")
    @PreAuthorize("@permissionGuard.has('CHILD_PROFILE_VIEW')")
    public ResponseEntity<ResponseJson<ChildProfileResponse>> getChildProfile(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getChildProfile|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        ChildProfileResponse profile = childService.getChildProfile(currentUser, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child profile success", profile);
    }
}
