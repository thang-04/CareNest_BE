package com.carenest.service.eduplan;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.*;
import com.carenest.entity.eduplan.EduResourceItem;
import com.carenest.integration.storage.FileStorage;
import com.carenest.security.CurrentUser;
import java.util.List;
import java.util.UUID;

/** Kho học liệu cá nhân (spec 900/901, EDU-82…88): nhãn, gợi ý, chủ đề đã dạy, khóa xóa/đổi tên. */
public interface ResourceLibraryService {
    PageResponse<ResourceItemResponse> list(CurrentUser user, Query query);

    ResourceItemResponse update(CurrentUser user, Long id, ResourceItemUpdateRequest request);

    void delete(CurrentUser user, Long id);

    ResourceUsagesResponse usages(CurrentUser user, Long id);

    FileUrlResponse contentUrl(CurrentUser user, Long id);

    /** Dùng cho giáo án ngày: tạo học liệu tệp trong kho của người dùng (trong giao dịch của người gọi). */
    EduResourceItem createFileItem(
            CurrentUser user, Long ageGroupId, FileStorage.StoredFile stored, String name, UUID requestId);

    /** Dùng cho giáo án ngày: học liệu link của chính người dùng (dùng lại nếu đã có cùng url) — EDU-83. */
    EduResourceItem findOrCreateLink(CurrentUser user, String rawUrl, String title, Long ageGroupId);

    /** EDU-87: nhãn tự thêm của một dòng gắn (chủ đề của ngày + MT của mục), kèm năm học của lớp. */
    void tagAttachment(Long itemId, Long attachmentId, Long schoolYearId, ResourceTagsRequest tags);

    /** Học liệu của chính người dùng (EDU-84); của người khác hoặc không có thì 7128. */
    EduResourceItem requireOwned(CurrentUser user, Long id);

    record Query(
            String q,
            String kind,
            Long ageGroupId,
            Long themeId,
            String themeName,
            List<Long> goalIds,
            String docKey,
            String activityKey,
            String teachThemeName,
            Long schoolYearId,
            int page,
            int size) {}
}
