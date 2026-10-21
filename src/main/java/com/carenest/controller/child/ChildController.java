package com.carenest.controller.child;

import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/children")
@RequiredArgsConstructor
@Slf4j
public class ChildController {
    private static final String LOG_PREFIX = "[ChildController]";

    private final ChildService childService;

    // Role chỉ là lớp 1; ChildService kiểm phạm vi lớp/liên kết của đúng trẻ này (lớp 2)
    @GetMapping("/{childId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'VICE_PRINCIPAL', 'PRINCIPAL', 'PARENT')")
    public ResponseEntity<ResponseJson<ChildProfileResponse>> getChildProfile(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getChildProfile|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        ChildProfileResponse profile = childService.getChildProfile(currentUser, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child profile success", profile);
    }
}
