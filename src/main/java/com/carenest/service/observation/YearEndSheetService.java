package com.carenest.service.observation;

import com.carenest.dto.observation.YearEndSheetDtos.YearEndSheetRequest;
import com.carenest.dto.observation.YearEndSheetDtos.YearEndSheetResponse;
import com.carenest.entity.observation.ChildEvaluation;
import com.carenest.security.CurrentUser;

/** Phiếu đánh giá sự phát triển của trẻ cuối năm (OBS-28), gắn với đánh giá cuối năm của trẻ. */
public interface YearEndSheetService {

    /** Xem: GV lớp; PHT / HT điểm trường khi đánh giá đã xác nhận (như đánh giá cuối năm). */
    YearEndSheetResponse get(CurrentUser currentUser, Long evaluationId);

    /** GV lớp, năm đang hoạt động, chưa xác nhận: lưu ô sửa tay và 4 lưu ý. */
    YearEndSheetResponse save(CurrentUser currentUser, Long evaluationId, YearEndSheetRequest request);

    /** Gọi khi xác nhận đánh giá cuối năm: chốt cả phiếu (kết quả tổng hợp + ô sửa tay) vào đánh giá. */
    void freeze(ChildEvaluation evaluation);
}
