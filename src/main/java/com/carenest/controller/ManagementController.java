package com.carenest.controller;

import com.carenest.dto.request.ChangeChildParentRequest;
import com.carenest.dto.request.ChangeRoleRequest;
import com.carenest.dto.request.CreateChildRequest;
import com.carenest.dto.request.CreateStaffRequest;
import com.carenest.dto.request.ManagerResetPasswordRequest;
import com.carenest.dto.request.RejectPickupPersonRequest;
import com.carenest.dto.request.UpdateChildRequest;
import com.carenest.dto.request.UpdateUserRequest;
import com.carenest.dto.request.UpdateUserStatusRequest;
import com.carenest.dto.response.ApiResponse;
import com.carenest.dto.response.ChildDetailResponse;
import com.carenest.dto.response.ChildResponse;
import com.carenest.dto.response.ImageResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.response.PickupPersonResponse;
import com.carenest.dto.response.UserDetailResponse;
import com.carenest.dto.response.UserResponse;
import com.carenest.entity.PickupStatus;
import com.carenest.entity.Role;
import com.carenest.entity.UserStatus;
import com.carenest.service.ChildService;
import com.carenest.service.PickupPersonService;
import com.carenest.service.UserImageService;
import com.carenest.service.UserManagementService;
import com.carenest.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * School management: principal and vice principals.
 */
@RestController
@RequestMapping("/management")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
public class ManagementController {

    private final UserService userService;
    private final UserManagementService userManagementService;
    private final ChildService childService;
    private final UserImageService userImageService;
    private final PickupPersonService pickupPersonService;

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createStaff(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody CreateStaffRequest request) {
        return ApiResponse.success("Tạo tài khoản thành công",
                userService.createStaff(Long.valueOf(jwt.getSubject()), request));
    }

    // ---- Users ----

    @GetMapping("/users")
    public ApiResponse<PageResponse<UserResponse>> listUsers(@RequestParam(required = false) Role role,
                                                            @RequestParam(required = false) UserStatus status,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Danh sách tài khoản", userManagementService.list(role, status, keyword, page, size));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<UserDetailResponse> getUser(@PathVariable Long userId) {
        return ApiResponse.success("Thông tin tài khoản", userManagementService.detail(userId));
    }

    @PutMapping("/users/{userId}")
    public ApiResponse<UserResponse> updateUser(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
                                                @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("Đã cập nhật tài khoản",
                userManagementService.update(actorId(jwt), userId, request));
    }

    @PutMapping("/users/{userId}/role")
    public ApiResponse<UserResponse> changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
                                                @Valid @RequestBody ChangeRoleRequest request) {
        return ApiResponse.success("Đã đổi vai trò. Người dùng cần đăng nhập lại",
                userManagementService.changeRole(actorId(jwt), userId, request));
    }

    @PostMapping("/users/{userId}/reset-password")
    public ApiResponse<Void> resetUserPassword(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
                                               @Valid @RequestBody ManagerResetPasswordRequest request) {
        userManagementService.resetPassword(actorId(jwt), userId, request);
        return ApiResponse.success("Đã đặt mật khẩu tạm. Người dùng phải đổi mật khẩu khi đăng nhập");
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUser(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        userManagementService.delete(actorId(jwt), userId);
        return ApiResponse.success("Đã xoá tài khoản");
    }

    @PutMapping("/users/{userId}/status")
    public ApiResponse<UserResponse> changeUserStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId,
                                                      @Valid @RequestBody UpdateUserStatusRequest request) {
        UserResponse user = userManagementService.changeStatus(Long.valueOf(jwt.getSubject()), userId, request.status());
        String message = request.status() == UserStatus.LOCKED ? "Đã khoá tài khoản" : "Đã mở khoá tài khoản";
        return ApiResponse.success(message, user);
    }

    @PutMapping("/teachers/{teacherId}/head-teacher")
    public ApiResponse<UserResponse> appointHeadTeacher(@PathVariable Long teacherId) {
        return ApiResponse.success("Đã chỉ định tổ trưởng", userManagementService.setHeadTeacher(teacherId, true));
    }

    @DeleteMapping("/teachers/{teacherId}/head-teacher")
    public ApiResponse<UserResponse> removeHeadTeacher(@PathVariable Long teacherId) {
        return ApiResponse.success("Đã bỏ chức tổ trưởng", userManagementService.setHeadTeacher(teacherId, false));
    }

    // ---- Children ----

    @GetMapping("/children")
    public ApiResponse<PageResponse<ChildResponse>> listChildren(@RequestParam(required = false) String keyword,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Danh sách trẻ", childService.list(keyword, page, size));
    }

    @GetMapping("/children/{childId}")
    public ApiResponse<ChildDetailResponse> getChild(@PathVariable Long childId) {
        return ApiResponse.success("Thông tin trẻ", childService.detail(childId));
    }

    @PutMapping("/children/{childId}")
    public ApiResponse<ChildResponse> updateChild(@PathVariable Long childId,
                                                  @Valid @RequestBody UpdateChildRequest request) {
        return ApiResponse.success("Đã cập nhật thông tin trẻ", childService.update(childId, request));
    }

    @PutMapping("/children/{childId}/parent")
    public ApiResponse<ChildResponse> changeChildParent(@PathVariable Long childId,
                                                        @Valid @RequestBody ChangeChildParentRequest request) {
        return ApiResponse.success("Đã đổi phụ huynh. Người đón trẻ cần được duyệt lại",
                childService.changeParent(childId, request));
    }

    @PostMapping("/children")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChildResponse> createChild(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateChildRequest request) {
        ChildResponse child = childService.createChild(Long.valueOf(jwt.getSubject()), request);
        String message = child.parentAccountCreated()
                ? "Thêm trẻ thành công. Đã tạo tài khoản phụ huynh, mật khẩu mặc định là số điện thoại"
                : "Thêm trẻ thành công và gắn vào tài khoản phụ huynh đã có";
        return ApiResponse.success(message, child);
    }

    @PostMapping("/parents/{parentId}/reset-password")
    public ApiResponse<Void> resetParentPassword(@PathVariable Long parentId) {
        userService.resetParentPassword(parentId);
        return ApiResponse.success("Đã đặt lại mật khẩu phụ huynh về số điện thoại");
    }

    @PutMapping(value = "/children/{childId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ChildResponse> updateChildPhoto(@PathVariable Long childId,
                                                       @RequestPart("file") MultipartFile file) {
        return ApiResponse.success("Đã cập nhật ảnh của trẻ", childService.updatePhoto(childId, file));
    }

    @GetMapping("/users/{userId}/images")
    public ApiResponse<List<ImageResponse>> listUserImages(@PathVariable Long userId) {
        return ApiResponse.success("Danh sách ảnh", userImageService.listImages(userId));
    }

    @GetMapping("/pickup-persons")
    public ApiResponse<List<PickupPersonResponse>> listPickupPersons(
            @RequestParam(defaultValue = "PENDING") PickupStatus status) {
        return ApiResponse.success("Danh sách người đón trẻ", pickupPersonService.listByStatus(status));
    }

    @PostMapping("/pickup-persons/{pickupPersonId}/approve")
    public ApiResponse<PickupPersonResponse> approvePickupPerson(@AuthenticationPrincipal Jwt jwt,
                                                                 @PathVariable Long pickupPersonId) {
        return ApiResponse.success("Đã duyệt người đón trẻ",
                pickupPersonService.review(Long.valueOf(jwt.getSubject()), pickupPersonId, true, null));
    }

    @PostMapping("/pickup-persons/{pickupPersonId}/reject")
    public ApiResponse<PickupPersonResponse> rejectPickupPerson(@AuthenticationPrincipal Jwt jwt,
                                                                @PathVariable Long pickupPersonId,
                                                                @Valid @RequestBody(required = false)
                                                                RejectPickupPersonRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.success("Đã từ chối người đón trẻ",
                pickupPersonService.review(Long.valueOf(jwt.getSubject()), pickupPersonId, false, reason));
    }

    private static Long actorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
