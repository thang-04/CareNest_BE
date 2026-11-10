package com.carenest.controller.meals;

import com.carenest.dto.meals.AllergenResponse;
import com.carenest.dto.meals.CatalogStatusRequest;
import com.carenest.dto.meals.FoodImportRequest;
import com.carenest.dto.meals.FoodImportResponse;
import com.carenest.dto.meals.FoodRequest;
import com.carenest.dto.meals.FoodResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.FoodService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.FoodGroup;
import com.carenest.utils.enums.FoodStorage;
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

/**
 * Danh mục & thiết lập › Thực phẩm, chất dị ứng. Ban giám hiệu xem; sửa cần NUTRITION_MANAGE (AUTH-20). Danh mục vài
 * trăm dòng, FE cần cả danh sách để lập món ⇒ không phân trang.
 */
@RestController
@RequestMapping("/nutrition")
@RequiredArgsConstructor
@Slf4j
public class NutritionFoodController {
    private static final String LOG_PREFIX = "[NutritionFoodController]";

    private final FoodService foodService;

    @GetMapping("/allergens")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<AllergenResponse>>> getAllergens(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getAllergens|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<AllergenResponse> allergens = foodService.getAllergens();
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get allergens success", allergens);
    }

    @GetMapping("/foods")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<FoodResponse>>> getFoods(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) FoodGroup foodGroup,
            @RequestParam(required = false) FoodStorage storage,
            @RequestParam(required = false) CatalogStatus status,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getFoods|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<FoodResponse> foods = foodService.getFoods(keyword, foodGroup, storage, status);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get foods success", foods);
    }

    @GetMapping("/foods/{foodId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<FoodResponse>> getFood(@PathVariable Long foodId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getFood|userId=" + currentUser.userId() + "|foodId=" + foodId;
        log.info("{}|START", logPrefix);
        FoodResponse food = foodService.getFood(foodId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get food success", food);
    }

    @PostMapping("/foods")
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<FoodResponse>> createFood(
            @Valid @RequestBody FoodRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|createFood|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        FoodResponse food = foodService.createFood(currentUser, request);
        log.info("{}|foodId={}|END", logPrefix, food.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create food success", food);
    }

    @PutMapping("/foods/{foodId}")
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    public ResponseEntity<ResponseJson<FoodResponse>> updateFood(
            @PathVariable Long foodId, @Valid @RequestBody FoodRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateFood|userId=" + currentUser.userId() + "|foodId=" + foodId;
        log.info("{}|START", logPrefix);
        FoodResponse food = foodService.updateFood(currentUser, foodId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update food success", food);
    }

    @PatchMapping("/foods/{foodId}/status")
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    public ResponseEntity<ResponseJson<FoodResponse>> changeFoodStatus(
            @PathVariable Long foodId, @Valid @RequestBody CatalogStatusRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|changeFoodStatus|userId=" + currentUser.userId() + "|foodId=" + foodId;
        log.info("{}|START", logPrefix);
        FoodResponse food = foodService.changeFoodStatus(currentUser, foodId, request.status());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Change food status success", food);
    }

    @PostMapping("/foods/import")
    @PreAuthorize("@permissionGuard.has('NUTRITION_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<FoodImportResponse>> importFoods(
            @Valid @RequestBody FoodImportRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|importFoods|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        FoodImportResponse result = foodService.importFoods(currentUser, request);
        log.info("{}|created={}|updated={}|END", logPrefix, result.created(), result.updated());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Import foods success", result);
    }
}
