package com.carenest.service.child;

import com.carenest.dto.child.ChildListFilter;
import com.carenest.dto.child.ChildListItemResponse;
import com.carenest.dto.child.ParentAccountRowResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.GuardianAccountStatus;
import java.util.List;

/** Danh sách trẻ phân trang (D-4, G94-180/CHD-09) cho màn Danh sách trẻ, Xếp lớp, Tài khoản phụ huynh. */
public interface ChildListService {

    /**
     * HT toàn trường; PHT điểm trường được giao; GV lớp đang dạy (AUTH-07, AUTH-08). Lọc lớp / điểm trường ngoài phạm vi
     * ⇒ 403. Sắp theo tên lớp (trẻ chưa có lớp trước) rồi họ tên.
     */
    PageResponse<ChildListItemResponse> searchChildren(CurrentUser actor, ChildListFilter filter);

    /**
     * Mỗi phụ huynh trên hồ sơ trẻ Đang học, Chờ xếp lớp mà người xem quản lý được tài khoản phụ huynh (AUTH-19; phạm vi
     * như {@link #searchChildren}); lọc trạng thái tài khoản và từ khóa (họ tên, mã trẻ, họ tên, SĐT phụ huynh; không
     * phân biệt hoa thường, dấu). Bảng tổng quan PHT đếm phụ huynh chưa kích hoạt (đặc tả FE mục 4.8).
     */
    List<ParentAccountRowResponse> listParentAccounts(CurrentUser actor, GuardianAccountStatus status, String keyword);
}
