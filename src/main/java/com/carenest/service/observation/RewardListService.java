package com.carenest.service.observation;

import com.carenest.dto.observation.RewardDtos.RewardClassDetail;
import com.carenest.dto.observation.RewardDtos.RewardClassRow;
import com.carenest.dto.observation.RewardDtos.RewardDecisionRequest;
import com.carenest.dto.observation.RewardDtos.RewardListRequest;
import com.carenest.security.CurrentUser;
import java.util.List;

/**
 * Đề xuất khen thưởng cuối năm theo danh sách lớp: GV lớp chọn danh hiệu cho trẻ đã có đánh giá cuối năm xác nhận
 * (hệ thống gợi ý từ chuyên cần, phiếu bé ngoan, hoàn thành chương trình) và gửi; PHT điểm trường chuyển HT hoặc trả
 * lại; HT phê duyệt hoặc trả lại (HT xử lý được cả danh sách đang chờ PHT).
 */
public interface RewardListService {

    /** Mỗi lớp trong phạm vi một dòng (kể cả lớp chưa lập danh sách). */
    List<RewardClassRow> classes(CurrentUser currentUser, Long schoolYearId);

    RewardClassDetail detail(CurrentUser currentUser, Long classId);

    /** Lưu cả danh sách của lớp (tạo nếu chưa có); submit: gửi PHT ngay sau khi lưu. */
    RewardClassDetail save(CurrentUser currentUser, Long classId, RewardListRequest request, boolean submit);

    RewardClassDetail review(CurrentUser currentUser, Long listId, RewardDecisionRequest request);

    RewardClassDetail decide(CurrentUser currentUser, Long listId, RewardDecisionRequest request);
}
