package com.carenest.service.observation;

import com.carenest.dto.observation.ChildSummaryResponse;
import com.carenest.dto.observation.CreateSummaryDraftRequest;
import com.carenest.dto.observation.UpdateSummaryContentRequest;
import com.carenest.security.CurrentUser;
import java.util.List;

/** Nhận xét tổng hợp theo tuần/kỳ: AI soạn nháp (hoặc template khi AI tắt/lỗi), GV sửa và duyệt. */
public interface ChildSummaryService {

    ChildSummaryResponse createDraft(CurrentUser currentUser, Long childId, CreateSummaryDraftRequest request);

    List<ChildSummaryResponse> listSummaries(CurrentUser currentUser, Long childId);

    ChildSummaryResponse updateContent(CurrentUser currentUser, Long summaryId, UpdateSummaryContentRequest request);

    ChildSummaryResponse approve(CurrentUser currentUser, Long summaryId);
}
