package com.carenest.utils.enums;

/** Loại thông báo gửi người dùng; App dùng để mở đúng màn hình. */
public enum NotificationType {
    /** Trẻ đã được trả cho người đón (PCK-04). */
    CHILD_PICKED_UP,
    /** Sổ báo ăn đã xác nhận thay đổi (sửa điểm danh sau mở lại), PHT cần xác nhận lại (NUT-03). */
    MEAL_COUNT_RECONFIRM,
    /** Trẻ nghỉ học, báo giáo viên lớp (G94-180/CHD-03). */
    CHILD_WITHDRAWN,
    /** Sửa hồ sơ trẻ đổi SĐT của phụ huynh đã có tài khoản, chờ PHT/HT duyệt (G94-180/AUTH-21). */
    GUARDIAN_PHONE_CHANGE_REQUESTED,
    /** Yêu cầu đổi SĐT phụ huynh đã được duyệt hoặc từ chối, báo người gửi yêu cầu. */
    GUARDIAN_PHONE_CHANGE_DECIDED,
    /** Khai Có tiếp xúc người bệnh lao: HT, PHT điểm trường, GV lớp, người lưu (G94-180/CHD-07). */
    CHILD_TB_CONTACT,
    /** Ngày tiếp xúc người bệnh lao đã có đổi sang ngày khác: HT, PHT điểm trường, người lưu (G94-180/CHD-07). */
    CHILD_TB_CONTACT_DATE_CHANGED,
    /** Kết quả cân đo / phiếu khám mới công bố, báo phụ huynh (HLT-11, PAR-03). */
    HEALTH_RESULT_PUBLISHED,
    /** Kết quả đã công bố được sửa, báo phụ huynh (HLT-11, HLT-13). */
    HEALTH_RESULT_UPDATED,
    /** Mở hoặc mở lại đợt khám / kỳ cân đo, báo GV lớp và PHT điểm trường. */
    HEALTH_CHECK_OPENED,
    /** Trẻ được xếp / chuyển vào lớp (tiếp nhận có lớp, xếp lớp), báo GV lớp mới (CHD-10, plan tiếp nhận mục 6.2). */
    CHILD_PLACED,
    /** Ngày điểm danh của lớp được mở lại (ATT-08, kể cả trả lại từ sổ suất ăn): giáo viên lớp kiểm tra, sửa. */
    ATTENDANCE_DAY_UNLOCKED,
    // Kế hoạch giáo dục (spec 003): gửi duyệt, duyệt bước giữa, ban hành, trả lại, mở hạn, gần đến hạn
    EDU_SUBMITTED,
    EDU_STEP_APPROVED,
    EDU_RELEASED,
    EDU_RETURNED,
    EDU_DEADLINE_EXTENDED,
    EDU_DEADLINE_NEAR,
    /** GV gửi nhận xét tuần của trẻ, báo phụ huynh đã liên kết với trẻ (OBS-29). */
    WEEKLY_COMMENT_SENT
}
