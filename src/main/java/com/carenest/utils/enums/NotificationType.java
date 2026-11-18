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
    /** GV lớp duyệt nhận định xu hướng sức khỏe, báo phụ huynh; nội dung = tổng quan đã duyệt (HLT-21, PAR-03). */
    HEALTH_TREND_APPROVED,
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
    /** Thông báo của trường do BGH gửi (PAR-05, spec 019). */
    SCHOOL_ANNOUNCEMENT,
    /** Sức khỏe trong ngày có biểu hiện, báo phụ huynh ngay (HLT-14). */
    DAILY_HEALTH_NOTED,
    /** Công bố thực đơn tuần, báo phụ huynh (NUT-08). */
    MENU_PUBLISHED,
    /** Phụ huynh gửi đơn nghỉ, báo GV lớp (ATT-05). */
    LEAVE_REQUEST_CREATED,
    /** Xác nhận đánh giá trẻ, báo phụ huynh (PAR-03). */
    CHILD_EVALUATION_CONFIRMED,
    /** Xác nhận đánh giá chủ đề của lớp, báo phụ huynh (PAR-03). */
    THEME_EVALUATION_CONFIRMED,
    /** GV gửi nhận xét tuần của trẻ, báo phụ huynh đã liên kết với trẻ (OBS-29). */
    WEEKLY_COMMENT_SENT,
    // Nhắc việc (OBS-30): GV lớp — sắp tới giờ chốt điểm danh, chưa đánh giá ngày, đánh giá / nhận xét tuần mở,
    // gần hạn, đã khóa; được mở lại, sắp hết thời gian mở lại. BGH — sổ báo ăn chưa xác nhận, lớp quá hạn đánh giá
    ATTENDANCE_REMINDER,
    ASSESSMENT_REMINDER,
    ASSESSMENT_REOPENED,
    LEADER_REMINDER,
    // Thông báo việc (OBS-31): phân công GV / tổ trưởng, đổi giờ chốt, đơn nghỉ của phụ huynh, sổ báo ăn đã xác nhận
    // (báo bếp), luồng đề xuất khen thưởng
    STAFF_ASSIGNED,
    CUTOFF_CHANGED,
    LEAVE_REQUEST_CHANGED,
    MEAL_COUNT_CONFIRMED,
    REWARD_LIST_UPDATED,
    /** Kích hoạt năm học mới: báo GV, PHT, bếp có phân công năm mới (CFG-YR-02). */
    SCHOOL_YEAR_ACTIVATED;

    public String typeLabel() {
        return switch (this) {
            case SCHOOL_ANNOUNCEMENT -> "Thông báo nhà trường";
            case EDU_SUBMITTED,
                    EDU_STEP_APPROVED,
                    EDU_RELEASED,
                    EDU_RETURNED,
                    EDU_DEADLINE_EXTENDED,
                    EDU_DEADLINE_NEAR -> "Kế hoạch giáo dục";
            case CHILD_PICKED_UP -> "Đón trẻ";
            case DAILY_HEALTH_NOTED -> "Sức khỏe trong ngày";
            case CHILD_TB_CONTACT,
                    CHILD_TB_CONTACT_DATE_CHANGED,
                    HEALTH_RESULT_PUBLISHED,
                    HEALTH_RESULT_UPDATED,
                    HEALTH_CHECK_OPENED,
                    HEALTH_TREND_APPROVED -> "Sức khỏe";
            case MENU_PUBLISHED -> "Thực đơn";
            case MEAL_COUNT_RECONFIRM, MEAL_COUNT_CONFIRMED -> "Suất ăn";
            case LEAVE_REQUEST_CREATED, LEAVE_REQUEST_CHANGED -> "Đơn nghỉ";
            case CHILD_EVALUATION_CONFIRMED, THEME_EVALUATION_CONFIRMED, ASSESSMENT_REMINDER, ASSESSMENT_REOPENED ->
                "Đánh giá trẻ";
            case WEEKLY_COMMENT_SENT -> "Nhận xét tuần";
            case ATTENDANCE_DAY_UNLOCKED, ATTENDANCE_REMINDER -> "Điểm danh";
            case CHILD_WITHDRAWN, CHILD_PLACED -> "Hồ sơ trẻ";
            case GUARDIAN_PHONE_CHANGE_REQUESTED, GUARDIAN_PHONE_CHANGE_DECIDED -> "Tài khoản phụ huynh";
            case STAFF_ASSIGNED -> "Phân công";
            case CUTOFF_CHANGED -> "Thời gian chốt";
            case REWARD_LIST_UPDATED -> "Khen thưởng";
            case SCHOOL_YEAR_ACTIVATED -> "Năm học";
            case LEADER_REMINDER -> "Nhắc việc";
        };
    }
}
