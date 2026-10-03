package com.carenest.controller;

import com.carenest.dto.response.ApiResponse;
import com.carenest.dto.response.PickupPersonResponse;
import com.carenest.service.PickupPersonService;
import com.carenest.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/teacher")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TeacherController {

    private final UserService userService;
    private final PickupPersonService pickupPersonService;

    @PostMapping("/parents/{parentId}/reset-password")
    public ApiResponse<Void> resetParentPassword(@AuthenticationPrincipal Jwt jwt, @PathVariable Long parentId) {
        userService.resetParentPasswordByTeacher(Long.valueOf(jwt.getSubject()), parentId);
        return ApiResponse.success("Đã đặt lại mật khẩu phụ huynh về số điện thoại");
    }

    // No class assignment yet, so every teacher can see the approved pickup persons of any child.
    @GetMapping("/children/{childId}/pickup-persons")
    public ApiResponse<List<PickupPersonResponse>> listPickupPersons(@PathVariable Long childId) {
        return ApiResponse.success("Danh sách người đón trẻ đã duyệt", pickupPersonService.listApproved(childId));
    }
}
