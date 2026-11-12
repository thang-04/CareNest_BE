package com.carenest.service.eduplan;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.PresignedUploadResponse;
import com.carenest.dto.eduplan.LearningResourceBinding;
import com.carenest.dto.eduplan.LearningResourceConfirmRequest;
import com.carenest.dto.eduplan.LearningResourceResponse;
import com.carenest.dto.eduplan.LearningResourceUploadUrlRequest;
import com.carenest.dto.eduplan.ResourceAttachRequest;
import com.carenest.dto.eduplan.ResourceLinkRequest;
import com.carenest.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface LearningResourceService {
    List<LearningResourceResponse> list(CurrentUser user, String docKey);

    LearningResourceResponse upload(
            CurrentUser user, String docKey, MultipartFile file, LearningResourceBinding binding, UUID requestId);

    /** EDU-83: dán link vào một mục hoạt động (dùng lại học liệu cùng url của người dán). */
    LearningResourceResponse addLink(CurrentUser user, String docKey, ResourceLinkRequest request);

    /** EDU-82: gắn học liệu có sẵn trong kho vào một mục hoạt động. */
    List<LearningResourceResponse> attach(CurrentUser user, String docKey, ResourceAttachRequest request);

    void detach(CurrentUser user, String docKey, Long id);

    Download content(CurrentUser user, String docKey, Long id);

    /** Liên kết xem có hạn, cùng điều kiện quyền với {@link #content}. */
    FileUrlResponse contentUrl(CurrentUser user, String docKey, Long id);

    /** Liên kết tải học liệu trực tiếp lên kho (tệp lớn như video), cùng quyền với {@link #upload}. */
    PresignedUploadResponse uploadUrl(CurrentUser user, String docKey, LearningResourceUploadUrlRequest request);

    /** Xác nhận tệp đã tải trực tiếp rồi gắn vào hoạt động, như {@link #upload} (idempotent theo requestId). */
    LearningResourceResponse confirmUpload(
            CurrentUser user, String docKey, LearningResourceConfirmRequest request, UUID requestId);

    record Download(Resource resource, String fileName, String contentType) {}
}
