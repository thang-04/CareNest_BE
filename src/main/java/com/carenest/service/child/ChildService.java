package com.carenest.service.child;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.security.CurrentUser;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ChildService {

    ChildProfileResponse getChildProfile(CurrentUser currentUser, Long childId);

    boolean childExists(Long childId);

    /** childId → mã, họ tên, ngày sinh; trẻ không tồn tại không có trong map. Không kiểm phạm vi (nơi gọi đã kiểm). */
    Map<Long, ChildBriefResponse> findChildBriefs(Collection<Long> childIds);

    /** Trẻ có khai dị ứng trong hồ sơ (CHD-06): suất ăn tính suất thay thế; chỉ dùng nội bộ, không kiểm quyền. */
    Set<Long> findChildIdsWithAllergies(Collection<Long> childIds);

    /** Trẻ không báo ăn (CHD-13) trong danh sách đã cho: điểm danh không cho suất ăn; chỉ dùng nội bộ, không kiểm quyền. */
    Set<Long> findChildIdsWithoutMeals(Collection<Long> childIds);

    /** childId → tên dị ứng đã khai (chữ tự do, CHD-06); trẻ không khai không có khóa. Chỉ dùng nội bộ, không kiểm quyền. */
    Map<Long, List<String>> findAllergyNames(Collection<Long> childIds);
}
