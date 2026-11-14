package com.carenest.service.account;

import com.carenest.dto.account.AccountPhotoResponse;
import com.carenest.dto.account.AccountPhotoUrlResponse;
import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.security.CurrentUser;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ảnh chân dung của tài khoản. Phụ huynh (PCK-02, đóng P-33 — user chốt 2026-10-31): tải khi đăng nhập App lần đầu (bắt
 * buộc), GV đối chiếu người đón; nhân sự trong phạm vi trẻ và phụ huynh của trẻ xem được. Nhân sự (AUTH-27): tự đổi/xóa
 * ảnh của mình, ai xem được theo {@code AccountPhotoAccessPolicy}.
 */
public interface AccountPhotoService {

    AccountPhotoResponse updateMyPhoto(CurrentUser currentUser, MultipartFile file);

    /** AUTH-27: xóa ảnh của mình; tài khoản có role phụ huynh không xóa được (ảnh phụ huynh bắt buộc). */
    AccountPhotoResponse deleteMyPhoto(CurrentUser currentUser);

    ImageContent getMyPhoto(CurrentUser currentUser);

    /** Ảnh của tài khoản phụ huynh {@code guardianUserId} trên hồ sơ trẻ; ngoài phạm vi hoặc chưa có ảnh ⇒ 404. */
    ImageContent getGuardianPhoto(CurrentUser currentUser, Long childId, Long guardianUserId);

    /** Liên kết xem có hạn của ảnh mình, cùng điều kiện với {@link #getMyPhoto}. */
    FileUrlResponse getMyPhotoUrl(CurrentUser currentUser);

    /** Liên kết xem có hạn, cùng điều kiện quyền với {@link #getGuardianPhoto}. */
    FileUrlResponse getGuardianPhotoUrl(CurrentUser currentUser, Long childId, Long guardianUserId);

    /**
     * AUTH-27: link xem có hạn ảnh của những tài khoản trong danh sách mà người gọi được xem, có ảnh và là nhân sự (ảnh
     * phụ huynh chỉ xem qua trẻ, trừ ảnh của chính mình). Người không thỏa bị bỏ qua, không báo lỗi.
     */
    List<AccountPhotoUrlResponse> findPhotoUrls(CurrentUser currentUser, Collection<Long> userIds);

    /** Tài khoản đã có ảnh trong danh sách. */
    Set<Long> findUserIdsWithPhoto(Collection<Long> userIds);
}
