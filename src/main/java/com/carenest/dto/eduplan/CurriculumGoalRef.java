package com.carenest.dto.eduplan;

/** Mục tiêu của Bộ dùng nội bộ khi lưu/đọc kế hoạch năm (EDU-28): nhóm tuổi, câu gốc, mục trong văn bản. */
public record CurriculumGoalRef(Long id, Long ageGroupId, String goalText, String sectionRef) {}
