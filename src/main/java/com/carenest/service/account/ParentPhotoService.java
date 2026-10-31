package com.carenest.service.account;

import com.carenest.dto.account.AccountPhotoResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.security.CurrentUser;
import java.util.Collection;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ảnh chân dung của tài khoản phụ huynh (PCK-02, đóng P-33 — user chốt 2026-10-31): phụ huynh tải khi đăng nhập App lần
 * đầu (bắt buộc), GV đối chiếu người đón khi trả trẻ; nhân sự trong phạm vi trẻ và phụ huynh của trẻ xem được.
 */
public interface ParentPhotoService {

    AccountPhotoResponse updateMyPhoto(CurrentUser currentUser, MultipartFile file);

    ImageContent getMyPhoto(CurrentUser currentUser);

    /** Ảnh của tài khoản phụ huynh {@code guardianUserId} trên hồ sơ trẻ; ngoài phạm vi hoặc chưa có ảnh ⇒ 404. */
    ImageContent getGuardianPhoto(CurrentUser currentUser, Long childId, Long guardianUserId);

    /** Tài khoản đã có ảnh trong danh sách. */
    Set<Long> findUserIdsWithPhoto(Collection<Long> userIds);
}
