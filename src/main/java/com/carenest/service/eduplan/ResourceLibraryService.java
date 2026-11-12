package com.carenest.service.eduplan;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.*;
import com.carenest.entity.eduplan.EduResourceItem;
import com.carenest.integration.storage.FileStorage;
import com.carenest.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** Kho học liệu (spec 900, EDU-82…88): phạm vi, nhãn, gợi ý, chia sẻ, duyệt lên kho trường. */
public interface ResourceLibraryService {
    PageResponse<ResourceItemResponse> list(CurrentUser user, Query query);

    ResourceItemResponse upload(CurrentUser user, MultipartFile file, String title, Long ageGroupId, UUID requestId);

    ResourceItemResponse addLink(CurrentUser user, ResourceLibraryLinkRequest request);

    ResourceItemResponse update(CurrentUser user, Long id, ResourceItemUpdateRequest request);

    ResourceItemResponse share(CurrentUser user, Long id, ResourceShareRequest request);

    ResourceItemResponse withdraw(CurrentUser user, Long id);

    ResourceItemResponse review(CurrentUser user, Long id, ResourceReviewRequest request);

    void delete(CurrentUser user, Long id);

    List<ResourceUsageResponse> usages(CurrentUser user, Long id);

    FileUrlResponse contentUrl(CurrentUser user, Long id);

    /** Dùng cho giáo án ngày: tạo học liệu tệp "Của tôi" (trong giao dịch của người gọi) và gắn nhãn. */
    EduResourceItem createFileItem(
            CurrentUser user, Long ageGroupId, FileStorage.StoredFile stored, String name, UUID requestId,
            ResourceTagsRequest tags);

    /** Dùng cho giáo án ngày: học liệu link của chính người dùng (dùng lại nếu đã có cùng url) — EDU-83. */
    EduResourceItem findOrCreateLink(
            CurrentUser user, String rawUrl, String title, Long ageGroupId, ResourceTagsRequest tags);

    /** Học liệu người dùng thấy được để gắn vào giáo án (EDU-84); không thấy thì 7128. */
    EduResourceItem requireVisible(CurrentUser user, Long id);

    record Query(
            String view,
            String q,
            String kind,
            Long ageGroupId,
            Long themeId,
            String themeName,
            List<Long> goalIds,
            String docKey,
            String activityKey,
            int page,
            int size) {}
}
