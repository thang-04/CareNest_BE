package com.carenest.controller;

import com.carenest.dto.request.PickupPersonRequest;
import com.carenest.dto.response.ApiResponse;
import com.carenest.dto.response.ChildResponse;
import com.carenest.dto.response.PickupPersonResponse;
import com.carenest.service.ChildService;
import com.carenest.service.PickupPersonService;
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

@RestController
@RequestMapping("/parent")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARENT')")
public class ParentController {

    private final ChildService childService;
    private final PickupPersonService pickupPersonService;

    @GetMapping("/children")
    public ApiResponse<List<ChildResponse>> listChildren(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Danh sách trẻ", childService.listForParent(userId(jwt)));
    }

    @PutMapping(value = "/children/{childId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ChildResponse> updateChildPhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable Long childId,
                                                       @RequestPart("file") MultipartFile file) {
        return ApiResponse.success("Đã cập nhật ảnh của trẻ",
                childService.updatePhotoByParent(userId(jwt), childId, file));
    }

    @GetMapping("/children/{childId}/pickup-persons")
    public ApiResponse<List<PickupPersonResponse>> listPickupPersons(@AuthenticationPrincipal Jwt jwt,
                                                                     @PathVariable Long childId) {
        return ApiResponse.success("Danh sách người đón trẻ", pickupPersonService.listForParent(userId(jwt), childId));
    }

    @PostMapping(value = "/children/{childId}/pickup-persons", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PickupPersonResponse> addPickupPerson(@AuthenticationPrincipal Jwt jwt,
                                                             @PathVariable Long childId,
                                                             @Valid @ModelAttribute PickupPersonRequest request,
                                                             @RequestPart("photo") MultipartFile photo) {
        return ApiResponse.success("Đã thêm người đón trẻ, đang chờ nhà trường duyệt",
                pickupPersonService.add(userId(jwt), childId, request, photo));
    }

    @PutMapping(value = "/pickup-persons/{pickupPersonId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PickupPersonResponse> updatePickupPerson(@AuthenticationPrincipal Jwt jwt,
                                                                @PathVariable Long pickupPersonId,
                                                                @Valid @ModelAttribute PickupPersonRequest request,
                                                                @RequestPart(value = "photo", required = false)
                                                                MultipartFile photo) {
        return ApiResponse.success("Đã cập nhật người đón trẻ, đang chờ nhà trường duyệt lại",
                pickupPersonService.update(userId(jwt), pickupPersonId, request, photo));
    }

    @DeleteMapping("/pickup-persons/{pickupPersonId}")
    public ApiResponse<Void> deletePickupPerson(@AuthenticationPrincipal Jwt jwt, @PathVariable Long pickupPersonId) {
        pickupPersonService.delete(userId(jwt), pickupPersonId);
        return ApiResponse.success("Đã xoá người đón trẻ");
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
