package com.carenest.service.meals;

import com.carenest.dto.meals.DishCopyRequest;
import com.carenest.dto.meals.DishDetailResponse;
import com.carenest.dto.meals.DishRequest;
import com.carenest.dto.meals.DishSummaryResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.DishType;
import java.util.List;

/** Món ăn và định lượng gam/trẻ theo nhóm tuổi; dinh dưỡng, giá tính xác định từ dữ liệu đã lưu (NUT-06, NUT-18). */
public interface DishService {

    List<DishSummaryResponse> getDishes(String keyword, DishType dishType, Boolean reference, CatalogStatus status);

    DishDetailResponse getDish(Long dishId);

    DishDetailResponse createDish(CurrentUser currentUser, DishRequest request);

    DishDetailResponse updateDish(CurrentUser currentUser, Long dishId, DishRequest request);

    DishDetailResponse changeDishStatus(CurrentUser currentUser, Long dishId, CatalogStatus status);

    DishDetailResponse copyDish(CurrentUser currentUser, Long dishId, DishCopyRequest request);
}
