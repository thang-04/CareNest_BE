package com.carenest.controller.meals;

import com.carenest.dto.meals.FoodSampleDisposeRequest;
import com.carenest.dto.meals.FoodSampleRequest;
import com.carenest.dto.meals.FoodSampleResponse;
import com.carenest.dto.meals.KitchenDayResponse;
import com.carenest.dto.meals.MealPhotoResponse;
import com.carenest.dto.meals.MealPhotoWindowResponse;
import com.carenest.dto.meals.MealPhotoWindowsRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.FoodSampleService;
import com.carenest.service.meals.KitchenDayService;
import com.carenest.service.meals.MealPhotoService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.MealSession;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bếp hôm nay, lưu mẫu thức ăn (NUT-21) và ảnh bữa ăn (NUT-16). Bếp chỉ thấy số đã xác nhận, không tên trẻ (AUTH-05);
 * phạm vi điểm trường kiểm ở service.
 */
@RestController
@RequestMapping("/nutrition")
@RequiredArgsConstructor
@Slf4j
public class KitchenRecordsController {
    private static final String LOG_PREFIX = "[KitchenRecordsController]";

    private final KitchenDayService kitchenDayService;
    private final FoodSampleService foodSampleService;
    private final MealPhotoService mealPhotoService;

    @GetMapping("/kitchen/day")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'KITCHEN_STAFF')")
    public ResponseEntity<ResponseJson<KitchenDayResponse>> getKitchenDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long campusId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getKitchenDay|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        KitchenDayResponse day = kitchenDayService.getKitchenDay(currentUser, date, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get kitchen day success", day);
    }

    @GetMapping("/food-samples")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'KITCHEN_STAFF')")
    public ResponseEntity<ResponseJson<List<FoodSampleResponse>>> getSamples(
            @RequestParam(required = false) Long campusId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getSamples|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<FoodSampleResponse> samples = foodSampleService.getSamples(currentUser, campusId, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get food samples success", samples);
    }

    @PostMapping("/food-samples")
    @PreAuthorize("@permissionGuard.has('KITCHEN_RECORD')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<FoodSampleResponse>> createSample(
            @Valid @RequestBody FoodSampleRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createSample|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        FoodSampleResponse sample = foodSampleService.createSample(currentUser, request);
        log.info("{}|sampleId={}|END", logPrefix, sample.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create food sample success", sample);
    }

    @PutMapping("/food-samples/{sampleId}")
    @PreAuthorize("@permissionGuard.has('KITCHEN_RECORD')")
    public ResponseEntity<ResponseJson<FoodSampleResponse>> updateSample(
            @PathVariable Long sampleId, @Valid @RequestBody FoodSampleRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateSample|userId=" + currentUser.userId() + "|sampleId=" + sampleId;
        log.info("{}|START", logPrefix);
        FoodSampleResponse sample = foodSampleService.updateSample(currentUser, sampleId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update food sample success", sample);
    }

    @PostMapping("/food-samples/{sampleId}/dispose")
    @PreAuthorize("@permissionGuard.has('KITCHEN_RECORD')")
    public ResponseEntity<ResponseJson<FoodSampleResponse>> disposeSample(
            @PathVariable Long sampleId,
            @RequestBody(required = false) FoodSampleDisposeRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|disposeSample|userId=" + currentUser.userId() + "|sampleId=" + sampleId;
        log.info("{}|START", logPrefix);
        FoodSampleResponse sample = foodSampleService.disposeSample(currentUser, sampleId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Dispose food sample success", sample);
    }

    @PostMapping("/food-samples/{sampleId}/approve")
    @PreAuthorize("@permissionGuard.has('FOOD_SAFETY_APPROVE')")
    public ResponseEntity<ResponseJson<FoodSampleResponse>> approveSample(
            @PathVariable Long sampleId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|approveSample|userId=" + currentUser.userId() + "|sampleId=" + sampleId;
        log.info("{}|START", logPrefix);
        FoodSampleResponse sample = foodSampleService.approveSample(currentUser, sampleId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Approve food sample success", sample);
    }

    @PostMapping("/food-samples/{sampleId}/approve-disposal")
    @PreAuthorize("@permissionGuard.has('FOOD_SAFETY_APPROVE')")
    public ResponseEntity<ResponseJson<FoodSampleResponse>> approveDisposal(
            @PathVariable Long sampleId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|approveDisposal|userId=" + currentUser.userId() + "|sampleId=" + sampleId;
        log.info("{}|START", logPrefix);
        FoodSampleResponse sample = foodSampleService.approveDisposal(currentUser, sampleId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Approve food sample disposal success", sample);
    }

    @GetMapping("/meal-photo-windows")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'KITCHEN_STAFF')")
    public ResponseEntity<ResponseJson<List<MealPhotoWindowResponse>>> getWindows(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getWindows|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<MealPhotoWindowResponse> windows = mealPhotoService.getWindows(currentUser);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get meal photo windows success", windows);
    }

    @PutMapping("/meal-photo-windows")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<MealPhotoWindowResponse>>> updateWindows(
            @Valid @RequestBody MealPhotoWindowsRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateWindows|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<MealPhotoWindowResponse> windows = mealPhotoService.updateWindows(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update meal photo windows success", windows);
    }

    @PostMapping(value = "/meal-photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.has('KITCHEN_RECORD')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<MealPhotoResponse>> uploadPhoto(
            @RequestPart MultipartFile file, @RequestParam MealSession session, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|uploadPhoto|userId=" + currentUser.userId() + "|session=" + session;
        log.info("{}|START", logPrefix);
        MealPhotoResponse photo = mealPhotoService.uploadPhoto(currentUser, session, file);
        log.info("{}|photoId={}|END", logPrefix, photo.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Upload meal photo success", photo);
    }

    @GetMapping("/meal-photos")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'KITCHEN_STAFF')")
    public ResponseEntity<ResponseJson<List<MealPhotoResponse>>> getPhotos(
            @RequestParam(required = false) Long campusId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getPhotos|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<MealPhotoResponse> photos = mealPhotoService.getPhotos(currentUser, campusId, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get meal photos success", photos);
    }

    @GetMapping("/meal-photos/{photoId}/content")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'KITCHEN_STAFF')")
    public ResponseEntity<Resource> getPhotoContent(@PathVariable Long photoId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getPhotoContent|userId=" + currentUser.userId() + "|photoId=" + photoId;
        log.info("{}|START", logPrefix);
        MealPhotoService.Content content = mealPhotoService.getPhotoContent(currentUser, photoId);
        log.info("{}|END", logPrefix);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.resource());
    }
}
