package com.carenest.controller.child;

import com.carenest.security.CurrentUser;
import com.carenest.service.account.EnrollmentImportAccountService;
import com.carenest.service.child.ChildEnrollmentImportService.*;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("@permissionGuard.has('CHILD_ENROLL')")
public class ChildEnrollmentImportController {
    private static final String LOG_PREFIX = "[ChildEnrollmentImportController]";
    private final EnrollmentImportAccountService service;

    @PostMapping(value = "/children/enrollments/import/preview", consumes = "multipart/form-data")
    public ResponseEntity<ResponseJson<Preview>> preview(@RequestPart("file") MultipartFile file, CurrentUser actor) {
        String logPrefix = LOG_PREFIX + "|preview|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        Preview result = service.preview(actor, file);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Enrollment import preview", result);
    }

    @PostMapping("/children/enrollments/import")
    public ResponseEntity<ResponseJson<ImportResult>> importRows(
            @RequestBody ImportRequest request, CurrentUser actor) {
        String logPrefix = LOG_PREFIX + "|importRows|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        ImportResult result = service.importRows(actor, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Enrollment profiles imported", result);
    }
}
