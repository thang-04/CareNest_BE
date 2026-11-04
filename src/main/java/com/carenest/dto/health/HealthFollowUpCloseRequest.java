package com.carenest.dto.health;

import com.carenest.utils.enums.HealthFollowUpOutcome;

/**
 * Đóng phiếu theo dõi theo lời bác sĩ. {@code conditionText} (tối đa 100 ký tự) bắt buộc khi {@code KNOWN_CONDITION}:
 * thêm vào tiền sử bản thân của khai báo sức khỏe.
 */
public record HealthFollowUpCloseRequest(HealthFollowUpOutcome outcome, String conditionText, String reason) {}
