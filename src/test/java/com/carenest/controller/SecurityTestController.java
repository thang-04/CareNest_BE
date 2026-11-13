package com.carenest.controller;

import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.sql.SQLException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/teacher")
    @PreAuthorize("hasRole('TEACHER')")
    ResponseEntity<ResponseJson<Void>> writeTeacherResource() {
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Teacher write");
    }

    @GetMapping("/authenticated")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ResponseJson<Void>> getAuthenticatedResource() {
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Authenticated resource");
    }

    @PostMapping("/authenticated")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ResponseJson<Void>> writeOwnResource() {
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Own write");
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    ResponseEntity<ResponseJson<Void>> getStaffResource() {
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Staff resource");
    }

    @PostMapping("/staff")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    ResponseEntity<ResponseJson<Void>> writeStaffResource() {
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Staff write");
    }

    // Giả lập GET có ghi DB bị PostgreSQL chặn vì transaction chỉ đọc
    @GetMapping("/staff-write-attempt")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    ResponseEntity<ResponseJson<Void>> attemptStaffWrite() {
        throw new DataAccessResourceFailureException(
                "write", new SQLException("cannot execute INSERT in a read-only transaction", "25006"));
    }

    @GetMapping("/staff-system-write")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    ResponseEntity<ResponseJson<Void>> getStaffSystemWrite() {
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "System write");
    }

    @PostMapping("/notification-read")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ResponseJson<Void>> markNotificationRead() {
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Notification read");
    }
}
