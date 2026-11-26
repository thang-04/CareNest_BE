package com.carenest.controller.account;

import com.carenest.dto.account.ChildTeacherResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.ChildTeacherService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** App phụ huynh: giáo viên đang dạy lớp của con, để gọi điện (PAR-04, AUTH-04). */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ChildTeacherController {
    private static final String LOG_PREFIX = "[ChildTeacherController]";

    private final ChildTeacherService childTeacherService;

    @GetMapping("/children/{childId}/teachers")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ResponseJson<List<ChildTeacherResponse>>> getTeachers(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getTeachers|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        List<ChildTeacherResponse> teachers = childTeacherService.getTeachers(currentUser, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get child teachers success", teachers);
    }
}
