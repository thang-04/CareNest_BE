package com.carenest.service.meals;

import com.carenest.dto.meals.AgeGroupMealConfigRequest;
import com.carenest.dto.meals.AgeGroupMealConfigResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/** Giá suất và chuẩn dinh dưỡng theo nhóm tuổi, có ngày hiệu lực (NUT-17, NUT-18, NUT-19). */
public interface AgeGroupMealConfigService {

    /** Bản đang áp dụng tại {@code date} (mặc định hôm nay) của từng nhóm tuổi đã có cấu hình. */
    List<AgeGroupMealConfigResponse> getEffectiveConfigs(LocalDate date);

    List<AgeGroupMealConfigResponse> getConfigVersions(Long ageGroupId);

    AgeGroupMealConfigResponse createConfig(CurrentUser currentUser, AgeGroupMealConfigRequest request);

    AgeGroupMealConfigResponse updateConfig(CurrentUser currentUser, Long configId, AgeGroupMealConfigRequest request);

    /** Mỗi dòng: đã có bản cùng nhóm tuổi, cùng ngày ⇒ sửa (bản chưa hiệu lực); chưa có ⇒ tạo. Tất cả hoặc không. */
    List<AgeGroupMealConfigResponse> saveConfigs(CurrentUser currentUser, List<AgeGroupMealConfigRequest> requests);
}
