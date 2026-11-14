package com.carenest.service.observation;

/**
 * Nhắc việc (OBS-30) qua chuông thông báo: chỉ ngày học, chỉ năm đang hoạt động; mỗi lời nhắc một lần cho mỗi người.
 * Các phương thức chạy theo lịch; gọi tay được để kiểm thử.
 */
public interface AssessmentReminderService {

    /** GV lớp: còn ≤ 30 phút tới giờ chốt mà lớp chưa điểm danh hôm nay. */
    int remindAttendanceCutoff();

    /** GV lớp: 20:00 còn trẻ có mặt chưa được đánh giá ngày (ngày khóa 23:59:59). */
    int remindDailyAssessment();

    /**
     * GV lớp: nhận xét tuần, đánh giá cuối chủ đề / học kỳ / cuối năm vừa mở, còn 1 ngày học tới hạn, đã khóa khi còn
     * việc dở.
     */
    int remindEvaluationDeadlines();

    /** GV lớp: lần mở lại (ngày đánh giá, nhận xét tuần, đánh giá kỳ) còn ≤ 1 giờ. */
    int remindUnlocksEnding();

    /** HT / PHT: sáng ngày học, các lớp quá hạn chưa xong trong điểm trường mình phụ trách. */
    int remindLeadersOverdue();
}
