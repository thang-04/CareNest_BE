package com.carenest.service.child;

import com.carenest.dto.child.ChildIdentity;
import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.security.CurrentUser;
import java.util.Collection;
import java.util.List;

public interface ChildService {

    ChildProfileResponse getChildProfile(CurrentUser currentUser, Long childId);

    /**
     * Họ tên và ngày sinh cho service nội bộ (tính tuổi, ẩn danh tên trước khi gọi AI). Không kiểm phạm vi:
     * caller phải kiểm ChildAccessPolicy/ClassroomAccessPolicy trước.
     */
    List<ChildIdentity> findIdentities(Collection<Long> childIds);
}
