package com.carenest.service.child;

import com.carenest.dto.child.ChildListFilter;
import com.carenest.dto.child.ChildListItemResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;

/** Danh sách trẻ phân trang (D-4, G94-180/CHD-09) cho màn Danh sách trẻ, Xếp lớp, Tài khoản phụ huynh. */
public interface ChildListService {

    /**
     * HT toàn trường; PHT điểm trường được giao; GV lớp đang dạy (AUTH-07, AUTH-08). Lọc lớp / điểm trường ngoài phạm vi
     * ⇒ 403. Sắp theo tên lớp (trẻ chưa có lớp trước) rồi họ tên.
     */
    PageResponse<ChildListItemResponse> searchChildren(CurrentUser actor, ChildListFilter filter);
}
