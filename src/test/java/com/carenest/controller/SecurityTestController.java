package com.carenest.controller;

import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller chỉ dùng trong test để kiểm cấu hình security (role mapping, 401/403). */
@RestController
@RequestMapping("/security-test")
public class SecurityTestController {

    @GetMapping("/teacher")
    @PreAuthorize("hasRole('TEACHER')")
    ResponseEntity<ResponseJson<Void>> getTeacherResource() {
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Teacher resource");
    }
}
