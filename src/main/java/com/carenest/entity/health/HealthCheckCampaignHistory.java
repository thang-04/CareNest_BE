package com.carenest.entity.health;

import com.carenest.utils.enums.HealthCheckAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một thao tác trên đợt/kỳ (tạo, sửa, đóng, mở lại, hủy); chỉ thêm, không sửa. */
@Getter
@Entity
@Table(name = "health_check_campaign_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthCheckCampaignHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HealthCheckAction action;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 300)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static HealthCheckCampaignHistory of(
            Long campaignId, HealthCheckAction action, Long userId, String reason, Instant at) {
        HealthCheckCampaignHistory history = new HealthCheckCampaignHistory();
        history.campaignId = campaignId;
        history.action = action;
        history.userId = userId;
        history.reason = reason;
        history.createdAt = at;
        return history;
    }
}
