package com.carenest.controller.meals;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.dto.meals.InspectionDayResponse;
import com.carenest.dto.meals.InspectionReopenRequest;
import com.carenest.dto.meals.InspectionSaveRequest;
import com.carenest.dto.meals.InspectionSheetResponse;
import com.carenest.dto.meals.InspectionSignRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.InspectionService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.InspectionTab;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Biên bản kiểm thực ba bước và soát sổ lưu mẫu (INS-01..04) cho HT/PHT trên Web. Bếp và giáo viên không dùng; quyền ghi
 * theo điểm trường kiểm ở service.
 */
@RestController
@RequestMapping("/inspections")
@RequiredArgsConstructor
@Slf4j
public class InspectionController {
    private static final String LOG_PREFIX = "[InspectionController]";
    private static final String MANAGERS = "hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')";

    private final InspectionService inspectionService;

    @GetMapping("/day")
    @PreAuthorize(MANAGERS)
    public ResponseEntity<ResponseJson<InspectionDayResponse>> getDay(
            @RequestParam Long campusId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getDay|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        InspectionDayResponse day = inspectionService.getDay(currentUser, campusId, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get inspections success", day);
    }

    @PutMapping("/day/{tab}")
    @PreAuthorize(MANAGERS)
    public ResponseEntity<ResponseJson<InspectionSheetResponse>> saveSheet(
            @PathVariable InspectionTab tab,
            @RequestParam Long campusId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody InspectionSaveRequest request,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|saveSheet|userId=" + currentUser.userId() + "|campusId=" + campusId + "|tab=" + tab;
        log.info("{}|START", logPrefix);
        InspectionSheetResponse sheet = inspectionService.save(currentUser, campusId, date, tab, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save inspection success", sheet);
    }

    @PostMapping("/day/{tab}/sign")
    @PreAuthorize(MANAGERS)
    public ResponseEntity<ResponseJson<InspectionSheetResponse>> signSheet(
            @PathVariable InspectionTab tab,
            @RequestParam Long campusId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody(required = false) InspectionSignRequest request,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|signSheet|userId=" + currentUser.userId() + "|campusId=" + campusId + "|tab=" + tab;
        log.info("{}|START", logPrefix);
        InspectionSheetResponse sheet = inspectionService.sign(currentUser, campusId, date, tab, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Sign inspection success", sheet);
    }

    @PostMapping("/day/{tab}/reopen")
    @PreAuthorize(MANAGERS)
    public ResponseEntity<ResponseJson<InspectionSheetResponse>> reopenSheet(
            @PathVariable InspectionTab tab,
            @RequestParam Long campusId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody InspectionReopenRequest request,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|reopenSheet|userId=" + currentUser.userId() + "|campusId=" + campusId + "|tab=" + tab;
        log.info("{}|START", logPrefix);
        InspectionSheetResponse sheet = inspectionService.reopen(currentUser, campusId, date, tab, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Reopen inspection success", sheet);
    }

    @GetMapping("/signatures/{imageKey}/url")
    @PreAuthorize(MANAGERS)
    public ResponseEntity<ResponseJson<FileUrlResponse>> getSignatureImageUrl(
            @PathVariable String imageKey, @RequestParam Long campusId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getSignatureImageUrl|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        FileUrlResponse url = inspectionService.getSignatureImageUrl(currentUser, campusId, imageKey);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get signature image url success", url);
    }

    @GetMapping("/signatures/{imageKey}")
    @PreAuthorize(MANAGERS)
    public ResponseEntity<byte[]> getSignatureImage(
            @PathVariable String imageKey, @RequestParam Long campusId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getSignatureImage|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        ImageContent image = inspectionService.getSignatureImage(currentUser, campusId, imageKey);
        log.info("{}|END", logPrefix);
        // Ảnh riêng tư: không lưu cache ở trình duyệt hay proxy
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }
}
