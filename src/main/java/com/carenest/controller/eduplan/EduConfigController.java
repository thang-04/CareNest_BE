package com.carenest.controller.eduplan;

import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.dto.eduplan.ExpectedResultResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dữ liệu tham chiếu của form kế hoạch năm (SD-09); danh mục chung, không chứa dữ liệu trẻ. */
@RestController
@RequestMapping("/education-plans")
@RequiredArgsConstructor
@Slf4j
public class EduConfigController {
    private static final String LOG_PREFIX = "[EduConfigController]";

    private final EduConfigService eduConfigService;

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<EduConfigResponse>> getEduConfig(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getEduConfig|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        EduConfigResponse config = eduConfigService.getEduConfig();
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get education plan config success", config);
    }

    @GetMapping("/expected-results")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<List<ExpectedResultResponse>>> getExpectedResults(
            @RequestParam Long ageGroupId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getExpectedResults|userId=" + currentUser.userId() + "|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        List<ExpectedResultResponse> results = eduConfigService.getExpectedResults(ageGroupId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get expected results success", results);
    }
}
