package com.carenest.controller.eduplan;

import com.carenest.dto.eduplan.EduDurationPolicyRequest;
import com.carenest.dto.eduplan.EduDurationPolicyResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduDurationPolicyService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/education-plans/duration-policy")
@RequiredArgsConstructor
@Slf4j
public class EduDurationPolicyController {
    private static final String LOG_PREFIX = "[EduDurationPolicyController]";
    private final EduDurationPolicyService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<EduDurationPolicyResponse>> get(CurrentUser user) {
        String logPrefix = LOG_PREFIX + "|get|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        var result = service.getDurationPolicy(user);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get education duration policy success", result);
    }

    @PutMapping
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<EduDurationPolicyResponse>> save(
            @Valid @RequestBody EduDurationPolicyRequest request, CurrentUser user) {
        String logPrefix = LOG_PREFIX + "|save|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        var result = service.saveDurationPolicy(user, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save education duration policy success", result);
    }
}
