package com.carenest.service.child;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.security.CurrentUser;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface ChildService {

    ChildProfileResponse getChildProfile(CurrentUser currentUser, Long childId);

    boolean childExists(Long childId);

    /** childId → mã, họ tên, ngày sinh; trẻ không tồn tại không có trong map. Không kiểm phạm vi (nơi gọi đã kiểm). */
    Map<Long, ChildBriefResponse> findChildBriefs(Collection<Long> childIds);

    /** Trẻ có khai dị ứng trong hồ sơ (CHD-06): suất ăn tính suất thay thế; chỉ dùng nội bộ, không kiểm quyền. */
    Set<Long> findChildIdsWithAllergies(Collection<Long> childIds);
}
