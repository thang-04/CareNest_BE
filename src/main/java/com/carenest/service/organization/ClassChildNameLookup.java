package com.carenest.service.organization;

import java.time.LocalDate;
import java.util.List;

/**
 * Họ tên trẻ đang học một lớp, chỉ để che tên trước khi gửi chữ giáo viên gõ cho AI (EDU-115, AI-04). Module child cài đặt
 * (đảo phụ thuộc: kế hoạch giáo dục không gọi thẳng child). Người gọi phải tự kiểm quyền với lớp.
 */
public interface ClassChildNameLookup {

    List<String> childNames(Long classroomId, LocalDate date);
}
