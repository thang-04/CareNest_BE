package com.carenest.controller.meals;

import com.carenest.dto.meals.CatalogStatusRequest;
import com.carenest.dto.meals.DishCopyRequest;
import com.carenest.dto.meals.DishDetailResponse;
import com.carenest.dto.meals.DishRequest;
import com.carenest.dto.meals.DishSummaryResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.DishService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.DishType;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Danh mục & thiết lập › Món ăn và định lượng theo nhóm tuổi (NUT-18). Danh mục nhỏ, FE lập thực đơn cần cả danh sách. */
@RestController
@RequestMapping("/nutrition/dishes")
@RequiredArgsConstructor
@Slf4j
public class NutritionDishController {
    private static final String LOG_PREFIX = "[NutritionDishController]";

    private final DishService dishService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<DishSummaryResponse>>> getDishes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DishType dishType,
            @RequestParam(required = false) Boolean reference,
            @RequestParam(required = false) CatalogStatus status,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getDishes|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<DishSummaryResponse> dishes = dishService.getDishes(keyword, dishType, reference, status);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get dishes success", dishes);
    }

    @GetMapping("/{dishId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<DishDetailResponse>> getDish(
            @PathVariable Long dishId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getDish|userId=" + currentUser.userId() + "|dishId=" + dishId;
        log.info("{}|START", logPrefix);
        DishDetailResponse dish = dishService.getDish(dishId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get dish success", dish);
    }

    @PostMapping
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<DishDetailResponse>> createDish(
            @Valid @RequestBody DishRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createDish|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        DishDetailResponse dish = dishService.createDish(currentUser, request);
        log.info("{}|dishId={}|END", logPrefix, dish.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create dish success", dish);
    }

    @PutMapping("/{dishId}")
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    public ResponseEntity<ResponseJson<DishDetailResponse>> updateDish(
            @PathVariable Long dishId, @Valid @RequestBody DishRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateDish|userId=" + currentUser.userId() + "|dishId=" + dishId;
        log.info("{}|START", logPrefix);
        DishDetailResponse dish = dishService.updateDish(currentUser, dishId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update dish success", dish);
    }

    @PatchMapping("/{dishId}/status")
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    public ResponseEntity<ResponseJson<DishDetailResponse>> changeDishStatus(
            @PathVariable Long dishId, @Valid @RequestBody CatalogStatusRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|changeDishStatus|userId=" + currentUser.userId() + "|dishId=" + dishId;
        log.info("{}|START", logPrefix);
        DishDetailResponse dish = dishService.changeDishStatus(currentUser, dishId, request.status());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Change dish status success", dish);
    }

    @PostMapping("/{dishId}/copy")
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<DishDetailResponse>> copyDish(
            @PathVariable Long dishId, @Valid @RequestBody DishCopyRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|copyDish|userId=" + currentUser.userId() + "|dishId=" + dishId;
        log.info("{}|START", logPrefix);
        DishDetailResponse dish = dishService.copyDish(currentUser, dishId, request);
        log.info("{}|newDishId={}|END", logPrefix, dish.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Copy dish success", dish);
    }
}
