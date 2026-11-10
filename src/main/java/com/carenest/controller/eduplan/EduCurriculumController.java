package com.carenest.controller.eduplan;

import com.carenest.dto.eduplan.CurriculumContentResponse;
import com.carenest.dto.eduplan.CurriculumGoalResponse;
import com.carenest.dto.eduplan.CurriculumSourceResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduCurriculumService;
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

/** Danh mục Chương trình GDMN của Bộ để lấy mục tiêu và đối chiếu nguồn (EDU-28); danh mục chung, chỉ đọc. */
@RestController
@RequestMapping("/education-plans/curriculum")
@RequiredArgsConstructor
@Slf4j
public class EduCurriculumController {
    private static final String LOG_PREFIX = "[EduCurriculumController]";

    private final EduCurriculumService curriculumService;

    @GetMapping("/sources")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<List<CurriculumSourceResponse>>> getSources(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getSources|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<CurriculumSourceResponse> sources = curriculumService.getSources();
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get curriculum sources success", sources);
    }

    @GetMapping("/goals")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<List<CurriculumGoalResponse>>> getGoals(
            @RequestParam Long ageGroupId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getGoals|userId=" + currentUser.userId() + "|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        List<CurriculumGoalResponse> goals = curriculumService.getGoals(ageGroupId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get curriculum goals success", goals);
    }

    @GetMapping("/contents")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<List<CurriculumContentResponse>>> getContents(
            @RequestParam Long ageGroupId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getContents|userId=" + currentUser.userId() + "|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        List<CurriculumContentResponse> contents = curriculumService.getContents(ageGroupId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get curriculum contents success", contents);
    }
}
