package com.carenest.dto.eduplan;

/**
 * Dòng danh mục của Bộ (lĩnh vực con, nhóm hoặc mục tiêu) dùng nội bộ khi lưu/đọc kế hoạch năm (EDU-28):
 * nhóm tuổi để kiểm hợp lệ, câu gốc để so "Đã sửa từ Bộ", mục trong văn bản (chỉ mục tiêu có).
 */
public record CurriculumRef(Long id, Long ageGroupId, String text, String sectionRef) {}
