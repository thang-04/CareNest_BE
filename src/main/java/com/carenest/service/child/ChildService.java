package com.carenest.service.child;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.dto.child.ChildSummaryResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import java.util.Collection;
import java.util.Map;

public interface ChildService {

    ChildProfileResponse getChildProfile(CurrentUser currentUser, Long childId);

    /** Danh sách trẻ để chọn khi cấp tài khoản phụ huynh: HT toàn trường, PHT trẻ đang học trong campus mình. */
    PageResponse<ChildSummaryResponse> searchChildren(
            CurrentUser actor, Long campusId, Long classroomId, String keyword, int page, int size);

    boolean childExists(Long childId);

    /** childId → mã, họ tên, ngày sinh; trẻ không tồn tại không có trong map. Không kiểm phạm vi (nơi gọi đã kiểm). */
    Map<Long, ChildBriefResponse> findChildBriefs(Collection<Long> childIds);
}
