package com.carenest.service.health;

import com.carenest.dto.health.HealthTrendAnalysisResponse;
import com.carenest.dto.health.HealthTrendApproveRequest;
import com.carenest.security.CurrentUser;

/**
 * Giải thích xu hướng sức khỏe (HLT-02..05, AI-01..07): nhân sự xem được trẻ (HT, PHT điểm trường, GV lớp) bấm phân
 * tích ⇒ cảnh báo tính xác định + lời giải thích AI dạng nháp; nhân sự đó duyệt ⇒ phụ huynh thấy (PAR-03).
 */
public interface HealthTrendService {

    /** Dưới 2 lần đo đã công bố ⇒ NOT_ENOUGH_DATA; AI tắt/lỗi ⇒ 503 8001, quá giờ ⇒ 504 8002 (AI-03). */
    HealthTrendAnalysisResponse analyze(CurrentUser actor, Long childId);

    /** Bản mới nhất (nháp hoặc đã duyệt); null khi chưa phân tích lần nào. */
    HealthTrendAnalysisResponse latest(CurrentUser actor, Long childId);

    /** Duyệt (AI-02), sửa câu chữ được; đã duyệt thì trả nguyên bản. */
    HealthTrendAnalysisResponse approve(
            CurrentUser actor, Long childId, Long interpretationId, HealthTrendApproveRequest request);
}
