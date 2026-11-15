package com.carenest.service.account;

import com.carenest.dto.account.UserSignatureResponse;
import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.ImageContent;
import com.carenest.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

/** Chữ ký ảnh của chính tài khoản đang đăng nhập (SIG-01): tối đa 5, một mặc định; chỉ chủ tài khoản xem ảnh. */
public interface UserSignatureService {

    List<UserSignatureResponse> listMine(CurrentUser currentUser);

    /** Ảnh đầu tiên của tài khoản luôn là mặc định; {@code makeDefault} đặt ảnh mới làm mặc định. */
    UserSignatureResponse upload(CurrentUser currentUser, MultipartFile file, boolean makeDefault);

    UserSignatureResponse setDefault(CurrentUser currentUser, Long signatureId);

    /** Xóa ảnh của mình; xóa ảnh mặc định thì ảnh mới nhất còn lại thành mặc định. */
    void delete(CurrentUser currentUser, Long signatureId);

    ImageContent getContent(CurrentUser currentUser, Long signatureId);

    /** Liên kết xem có hạn ảnh chữ ký của chính mình. */
    FileUrlResponse getContentUrl(CurrentUser currentUser, Long signatureId);

    /**
     * Ảnh dùng để ký chứng từ (INS-03): ảnh {@code signatureId} của chính người ký, bỏ trống ⇒ ảnh mặc định. Chưa có ảnh
     * nào (và không chỉ định) ⇒ rỗng; chỉ định ảnh không phải của mình ⇒ không tìm thấy.
     */
    Optional<ImageContent> findForSigning(CurrentUser currentUser, Long signatureId);
}
