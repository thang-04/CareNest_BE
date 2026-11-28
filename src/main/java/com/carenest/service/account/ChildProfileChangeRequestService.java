package com.carenest.service.account;

import com.carenest.dto.child.ChildProfileChangeCreateRequest;
import com.carenest.dto.child.ChildProfileChangeResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.ProfileChangeStatus;
import java.util.List;

/** GV sửa hồ sơ, khai báo sức khỏe trẻ ⇒ yêu cầu chờ HT/PHT điểm trường duyệt (CHD-11). */
public interface ChildProfileChangeRequestService {

    ChildProfileChangeResponse submit(CurrentUser actor, Long childId, ChildProfileChangeCreateRequest request);

    /** Yêu cầu trong phạm vi duyệt của người xem; {@code status} null ⇒ mọi trạng thái. */
    List<ChildProfileChangeResponse> list(CurrentUser actor, ProfileChangeStatus status);

    ChildProfileChangeResponse get(CurrentUser actor, Long requestId);

    /** Yêu cầu mới nhất từng loại của trẻ: GV (người gửi) hoặc người duyệt xem trạng thái, lý do từ chối. */
    List<ChildProfileChangeResponse> latestOfChild(CurrentUser actor, Long childId);

    ChildProfileChangeResponse approve(CurrentUser actor, Long requestId);

    ChildProfileChangeResponse reject(CurrentUser actor, Long requestId, String note);
}
