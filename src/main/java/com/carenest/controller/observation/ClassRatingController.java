package com.carenest.controller.observation;

import com.carenest.dto.observation.ClassRatingDtos.ClassRatingResponse;
import com.carenest.dto.observation.ClassRatingDtos.ClassRatingSaveRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.ClassRatingService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.ClassRatingPeriod;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Xếp loại nhóm lớp học kỳ I, II, cuối năm (sổ chỉ đạo chuyên môn mục 7): HT, PHT chấm; GV xem lớp mình. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ClassRatingController {
    private static final String LOG_PREFIX = "[ClassRatingController]";

    private final ClassRatingService classRatingService;

    @GetMapping("/assessment/class-ratings")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
    public ResponseEntity<ResponseJson<ClassRatingResponse>> listRatings(
            @RequestParam ClassRatingPeriod period,
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(required = false) Long campusId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|listRatings|userId=" + currentUser.userId() + "|period=" + period;
        log.info("{}|START", logPrefix);
        ClassRatingResponse response = classRatingService.list(currentUser, schoolYearId, period, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get class ratings success", response);
    }

    @PutMapping("/assessment/class-ratings")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<ClassRatingResponse>> saveRatings(
            @Valid @RequestBody ClassRatingSaveRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveRatings|userId=" + currentUser.userId() + "|period=" + request.period();
        log.info("{}|START", logPrefix);
        ClassRatingResponse response = classRatingService.save(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save class ratings success", response);
    }
}
