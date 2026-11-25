package com.carenest.service.meals;

import com.carenest.dto.meals.FoodHistoryResponse;
import com.carenest.utils.enums.CatalogStatus;
import java.util.List;
import java.util.Map;

/** Lịch sử thay đổi thực phẩm: các trường đã đổi và đổi giá, nhà cung cấp (NUT-27) gộp theo thời gian. */
public interface FoodHistoryService {

    void recordCreated(Long foodId, Long userId);

    /** Ghi mỗi trường có giá trị khác nhau giữa hai bản chụp; không có trường nào đổi thì không ghi gì. */
    void recordChanges(Long foodId, Long userId, Map<String, String> before, Map<String, String> after);

    void recordStatusChange(Long foodId, Long userId, CatalogStatus from, CatalogStatus to);

    /** Theo thời gian tăng dần; gồm sự kiện đã ghi và các lần đổi giá, nhà cung cấp lấy từ lịch sử giá. */
    List<FoodHistoryResponse> getHistory(Long foodId);
}
