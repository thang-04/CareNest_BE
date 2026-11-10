package com.carenest.service.observation;

import com.carenest.dto.observation.ClassMonthlyCommentDtos.ClassMonthlyCommentRequest;
import com.carenest.dto.observation.ClassMonthlyCommentDtos.ClassMonthlyCommentResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

/** Nhận xét chung cả lớp theo tháng (OBS-26): GV lớp soạn và xác nhận; HT, PHT xem bản đã xác nhận (AUTH-20). */
public interface ClassMonthlyCommentService {

    /** Tháng đã kết thúc chưa có thì tạo nháp từ số liệu tháng của lớp. */
    ClassMonthlyCommentResponse get(CurrentUser currentUser, Long classId, LocalDate month);

    ClassMonthlyCommentResponse save(CurrentUser currentUser, ClassMonthlyCommentRequest request);

    ClassMonthlyCommentResponse confirm(CurrentUser currentUser, ClassMonthlyCommentRequest request);
}
