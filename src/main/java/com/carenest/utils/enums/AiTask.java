package com.carenest.utils.enums;

/** Tác vụ AI; tên enum là khóa cấu hình {@code carenest.ai.tasks.<TASK>} (chuỗi provider, loại dữ liệu, deadline). */
public enum AiTask {
    MENU_WEEKLY,
    MENU_DAILY,
    MENU_DAY_FIX,
    HEALTH_TREND,
    WEEKLY_COMMENT,
    /** Soạn/duyệt kế hoạch giáo dục (EDU-95/96): nội dung chương trình, chủ đề, hoạt động; không dữ liệu trẻ. */
    EDU_PLAN,
    /** Gom nội dung vào chủ đề ở kế hoạch tổ (EDU-103): chỉ nội dung chương trình và chủ đề, không dữ liệu trẻ. */
    EDU_MATCH
}
