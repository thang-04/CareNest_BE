package com.carenest.controller;

import com.carenest.dto.request.UpdateProfileRequest;
import com.carenest.dto.response.ApiResponse;
import com.carenest.dto.response.ImageResponse;
import com.carenest.dto.response.UserResponse;
import com.carenest.service.UserImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * The signed-in user's own profile and images. Open to every role.
 */
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserImageService userImageService;

    @GetMapping
    public ApiResponse<UserResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Thông tin tài khoản", userImageService.getProfile(userId(jwt)));
    }

    @PutMapping
    public ApiResponse<UserResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success("Đã cập nhật thông tin", userImageService.updateProfile(userId(jwt), request));
    }

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserResponse> updateAvatar(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestPart("file") MultipartFile file) {
        return ApiResponse.success("Đã cập nhật ảnh đại diện", userImageService.updateAvatar(userId(jwt), file));
    }

    @GetMapping("/images")
    public ApiResponse<List<ImageResponse>> listImages(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Danh sách ảnh", userImageService.listImages(userId(jwt)));
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ImageResponse> addImage(@AuthenticationPrincipal Jwt jwt,
                                               @RequestPart("file") MultipartFile file,
                                               @RequestParam(required = false) String title) {
        return ApiResponse.success("Đã thêm ảnh", userImageService.addImage(userId(jwt), file, title));
    }

    @DeleteMapping("/images/{imageId}")
    public ApiResponse<Void> deleteImage(@AuthenticationPrincipal Jwt jwt, @PathVariable Long imageId) {
        userImageService.deleteImage(userId(jwt), imageId);
        return ApiResponse.success("Đã xoá ảnh");
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
