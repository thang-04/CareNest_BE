package com.carenest.entity.meals;

import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuAiDraftKind;
import com.carenest.utils.enums.MenuAiDraftSource;
import com.carenest.utils.enums.MenuAiDraftStatus;
import com.carenest.utils.enums.MenuAiFallbackReason;
import com.carenest.utils.enums.MenuBlock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Nháp gợi ý thực đơn (AI-01, AI-07): lưới món đề xuất cùng nguồn, điểm, issue và metadata gọi model. Lưới, điểm,
 * issue không đổi sau khi tạo; chỉ trạng thái đổi khi PHT dùng hoặc bỏ. Không lưu prompt/response thô (AI-05).
 */
@Getter
@Entity
@Table(name = "menu_ai_drafts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuAiDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuAiDraftKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "menu_block", nullable = false, length = 20)
    private MenuBlock menuBlock;

    @Column(name = "week_start")
    private LocalDate weekStart;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_session", length = 20)
    private MealSession targetSession;

    @Column(name = "from_dish_id")
    private Long fromDishId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuAiDraftStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MenuAiDraftSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "fallback_reason", length = 30)
    private MenuAiFallbackReason fallbackReason;

    @Column(length = 50)
    private String provider;

    @Column(length = 100)
    private String model;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(name = "repair_rounds", nullable = false)
    private int repairRounds;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_json", nullable = false, columnDefinition = "jsonb")
    private String requestJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_json", nullable = false, columnDefinition = "jsonb")
    private String outputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_json", nullable = false, columnDefinition = "jsonb")
    private String scoreJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "issues_json", nullable = false, columnDefinition = "jsonb")
    private String issuesJson;

    @Column(name = "input_fingerprint", nullable = false, length = 64)
    private String inputFingerprint;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Ô cần thay của nháp thay một món; null với loại khác. */
    public record Target(LocalDate date, MealSession session, Long fromDishId) {}

    /**
     * Nháp của một khối: tuần cần {@code weekStart}; thay một món cần thêm {@code target}; mẫu ngày không có tuần.
     * {@code source} null khi {@code NO_FEASIBLE}.
     */
    public static MenuAiDraft create(
            MenuAiDraftKind kind,
            MenuBlock block,
            LocalDate weekStart,
            Target target,
            MenuAiDraftStatus status,
            MenuAiDraftSource source,
            MenuAiFallbackReason fallbackReason,
            Long createdBy,
            Instant createdAt) {
        MenuAiDraft draft = new MenuAiDraft();
        draft.kind = kind;
        draft.menuBlock = block;
        draft.weekStart = weekStart;
        if (target != null) {
            draft.targetDate = target.date();
            draft.targetSession = target.session();
            draft.fromDishId = target.fromDishId();
        }
        draft.status = status;
        draft.source = source;
        draft.fallbackReason = fallbackReason;
        draft.createdBy = createdBy;
        draft.createdAt = createdAt;
        return draft;
    }

    public void recordModelCall(String provider, String model, String promptVersion, int repairRounds, long latencyMs) {
        this.provider = provider;
        this.model = model;
        this.promptVersion = promptVersion;
        this.repairRounds = repairRounds;
        this.latencyMs = (int) Math.min(Integer.MAX_VALUE, latencyMs);
    }

    public void recordContent(
            String requestJson, String outputJson, String scoreJson, String issuesJson, String inputFingerprint) {
        this.requestJson = requestJson;
        this.outputJson = outputJson;
        this.scoreJson = scoreJson;
        this.issuesJson = issuesJson;
        this.inputFingerprint = inputFingerprint;
    }

    public boolean isPending() {
        return status == MenuAiDraftStatus.PENDING_REVIEW;
    }

    public void apply(Long userId, Instant at) {
        decide(MenuAiDraftStatus.APPLIED, userId, at);
    }

    public void dismiss(Long userId, Instant at) {
        decide(MenuAiDraftStatus.DISMISSED, userId, at);
    }

    // Chỉ nháp chờ duyệt mới được dùng/bỏ; service kiểm trước và trả 409, đây là chốt cuối
    private void decide(MenuAiDraftStatus next, Long userId, Instant at) {
        if (!isPending()) {
            throw new IllegalStateException("Draft is not pending review: " + status);
        }
        this.status = next;
        this.decidedBy = userId;
        this.decidedAt = at;
    }
}
