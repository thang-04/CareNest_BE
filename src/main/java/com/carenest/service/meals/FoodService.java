package com.carenest.service.meals;

import com.carenest.dto.meals.AllergenResponse;
import com.carenest.dto.meals.FoodImportRequest;
import com.carenest.dto.meals.FoodImportResponse;
import com.carenest.dto.meals.FoodRequest;
import com.carenest.dto.meals.FoodResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.FoodGroup;
import com.carenest.utils.enums.FoodStorage;
import java.util.List;

/** Danh mục chất dị ứng và thực phẩm bán trú, dùng chung hai điểm trường (NUT-05, NUT-10, NUT-18). */
public interface FoodService {

    List<AllergenResponse> getAllergens();

    List<FoodResponse> getFoods(String keyword, FoodGroup foodGroup, FoodStorage storage, CatalogStatus status);

    FoodResponse getFood(Long foodId);

    FoodResponse createFood(CurrentUser currentUser, FoodRequest request);

    FoodResponse updateFood(CurrentUser currentUser, Long foodId, FoodRequest request);

    FoodResponse changeFoodStatus(CurrentUser currentUser, Long foodId, CatalogStatus status);

    FoodImportResponse importFoods(CurrentUser currentUser, FoodImportRequest request);
}
