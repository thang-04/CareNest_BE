package com.carenest.controller.child;

import com.carenest.dto.child.AutoPlaceRequest;
import com.carenest.dto.child.AutoPlaceResponse;
import com.carenest.dto.child.PlacementBoardResponse;
import com.carenest.dto.child.SavePlacementsRequest;
import com.carenest.dto.child.YearReadinessResponse;
import com.carenest.dto.organization.ActivateSchoolYearRequest;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.YearTransitionService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chuyển năm học: xếp lớp năm mới, kiểm tra chuẩn bị, kích hoạt năm (chuyển trẻ theo bản xếp lớp). */
@RestController
@RequestMapping("/school-years/{schoolYearId}")
@RequiredArgsConstructor
@Slf4j
public class YearTransitionController {
    private static final String LOG_PREFIX = "[YearTransitionController]";

    private final YearTransitionService yearTransitionService;

    @GetMapping("/placements")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<PlacementBoardResponse>> getPlacements(
            @PathVariable Long schoolYearId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getPlacements|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        PlacementBoardResponse board = yearTransitionService.getPlacements(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get placements success", board);
    }

    @PutMapping("/placements")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<Map<String, Integer>>> savePlacements(
            @PathVariable Long schoolYearId,
            @Valid @RequestBody SavePlacementsRequest request,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|savePlacements|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        int saved = yearTransitionService.savePlacements(currentUser, schoolYearId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save placements success", Map.of("saved", saved));
    }

    @PostMapping("/placements/auto")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<AutoPlaceResponse>> autoPlace(
            @PathVariable Long schoolYearId,
            @RequestBody(required = false) AutoPlaceRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|autoPlace|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        AutoPlaceResponse result = yearTransitionService.autoPlace(
                currentUser, schoolYearId, request == null ? new AutoPlaceRequest(null, null) : request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Auto place success", result);
    }

    @GetMapping("/readiness")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<YearReadinessResponse>> getReadiness(
            @PathVariable Long schoolYearId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getReadiness|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        YearReadinessResponse readiness = yearTransitionService.getReadiness(currentUser, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get readiness success", readiness);
    }

    @PostMapping("/activate")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<SchoolYearDetailResponse>> activateSchoolYear(
            @PathVariable Long schoolYearId,
            @Valid @RequestBody ActivateSchoolYearRequest request,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|activateSchoolYear|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        SchoolYearDetailResponse schoolYear =
                yearTransitionService.activateSchoolYear(currentUser, schoolYearId, request.confirmName());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Activate school year success", schoolYear);
    }
}
