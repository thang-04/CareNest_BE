package com.carenest.controller.child;

import com.carenest.dto.child.ChildListFilter;
import com.carenest.dto.child.ChildListItemResponse;
import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.dto.child.ParentAccountRowResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildListService;
import com.carenest.service.child.ChildService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.GuardianAccountStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
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
    private final ChildListService childListService;

    // Danh sách trẻ (D-4, G94-180/CHD-09) cho Danh sách trẻ, Xếp lớp, Tài khoản phụ huynh; phạm vi ở service (AUTH-08).
    // classroomId, q: tên cũ của classId, keyword (màn Tài khoản phụ huynh đang gửi)
    @GetMapping
    // Phụ huynh có CHILD_PROFILE_VIEW nhưng không dùng danh sách toàn trường: service chặn vai trò (AUTH-04)
    @PreAuthorize("@permissionGuard.hasAny('CHILD_PROFILE_VIEW', 'PARENT_ACCOUNT_MANAGE')")
    public ResponseEntity<ResponseJson<PageResponse<ChildListItemResponse>>> searchChildren(
            @RequestParam(required = false) Long schoolYear,
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long classroomId,
            @RequestParam(required = false) Set<ChildStatus> status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|searchChildren|userId=" + currentUser.userId() + "|schoolYear=" + schoolYear
                + "|campusId=" + campusId + "|classId=" + (classId != null ? classId : classroomId);
        log.info("{}|START", logPrefix);
        PageResponse<ChildListItemResponse> children = childListService.searchChildren(
                currentUser,
                new ChildListFilter(
                        schoolYear,
                        campusId,
                        classId != null ? classId : classroomId,
                        status,
                        keyword != null ? keyword : q,
                        page,
                        size));
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Search children success", children);
    }

    // Bảng tổng quan PHT (đặc tả FE mục 4.8): mỗi phụ huynh trên hồ sơ trẻ chưa nghỉ; không theo năm học (Y-5)
    @GetMapping("/parent-accounts")
    @PreAuthorize("@permissionGuard.has('PARENT_ACCOUNT_MANAGE')")
    public ResponseEntity<ResponseJson<List<ParentAccountRowResponse>>> listParentAccounts(
            @RequestParam(required = false) GuardianAccountStatus status,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listParentAccounts|userId=" + currentUser.userId() + "|status=" + status;
        log.info("{}|START", logPrefix);
        List<ParentAccountRowResponse> rows = childListService.listParentAccounts(currentUser, status, keyword);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "List parent accounts success", rows);
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
