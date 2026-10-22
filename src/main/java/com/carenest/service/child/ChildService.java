package com.carenest.service.child;

import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.dto.child.ChildSummaryResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;

public interface ChildService {

    ChildProfileResponse getChildProfile(CurrentUser currentUser, Long childId);

    /** Danh sách trẻ để chọn khi cấp tài khoản phụ huynh: HT toàn trường, PHT trẻ đang học trong campus mình. */
    PageResponse<ChildSummaryResponse> searchChildren(
            CurrentUser actor, Long campusId, Long classroomId, String keyword, int page, int size);

    boolean childExists(Long childId);
}
