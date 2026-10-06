package com.carenest.controller.child;

import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/children")
@RequiredArgsConstructor
public class ChildController {

    private final ChildService childService;

    // Role chỉ là lớp 1; ChildService kiểm phạm vi lớp/liên kết của đúng trẻ này (lớp 2)
    @GetMapping("/{childId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'VICE_PRINCIPAL', 'PRINCIPAL', 'PARENT')")
    public ResponseEntity<ResponseJson<ChildProfileResponse>> getChildProfile(
            @PathVariable Long childId, CurrentUser currentUser) {
        return ResponseJson.toJsonWithData(
                ApiCode.SUCCESSFUL, "Get child profile success", childService.getChildProfile(currentUser, childId));
    }
}
