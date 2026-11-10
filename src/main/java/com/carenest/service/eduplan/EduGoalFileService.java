package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.GoalFilePreviewResponse;
import com.carenest.security.CurrentUser;
import org.springframework.web.multipart.MultipartFile;

/** Đọc file bảng mục tiêu năm học (Word/Excel/CSV) thành bản xem trước để Web thêm vào bản đang soạn (EDU-46). */
public interface EduGoalFileService {

    GoalFilePreviewResponse preview(CurrentUser user, MultipartFile file);
}
