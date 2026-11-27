package com.carenest.dto.eduplan;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Kết quả AI (nháp, EDU-95/96): server đã lọc MT, loại giờ, ô không có trong yêu cầu. */
public final class EduAiResponse {
    private EduAiResponse() {}

    /** refs: mẩu RAG đã dùng làm tham khảo (bước c); rỗng khi không có kho hoặc AI embedding tắt. */
    public record LessonSteps(Step a, Step b, Step c, List<RagRef> refs, List<Step> rows) {}

    /** Mẩu tham khảo RAG mà AI dùng: id trong kho, tiêu đề ngắn, link nguồn (giáo án web). */
    public record RagRef(String id, String title, String url) {}

    public record Step(String teacher, String child) {}

    /**
     * ref: mẩu RAG AI ghi "from" (bước b, EDU-113); null khi AI không dựa vào mẩu nào. lines: chỉ số các dòng nội dung (theo
     * danh sách gửi lên) mà hoạt động dạy (EDU-114).
     */
    public record Activity(String goalId, String slot, String name, RagRef ref, List<Integer> lines) {}

    /** path: đường dẫn ô trong nội dung giáo án ngày (vd. days.0.sections.hoc.aims). */
    public record DayEdit(List<CellChange> changes) {}

    public record CellChange(String path, String label, String before, String after) {}

    /** EDU-103: cặp chủ đề–nội dung AI gợi ý (đã lọc chống bịa); lĩnh vực lỗi/quá hạn nằm ở failedDomains. */
    @Schema(name = "EduAiThemeContentMapResponse")
    public record ThemeContentMap(List<ThemeContentMatch> matches, List<FailedDomain> failedDomains) {}

    /** from: cụm nguyên văn trong yêu cầu, tên chủ đề, lễ hội hoặc tên nhánh của chính chủ đề đó. */
    @Schema(name = "EduAiThemeContentMatchResponse")
    public record ThemeContentMatch(Long themeId, Long contentId, String from) {}

    @Schema(name = "EduAiFailedDomainResponse")
    public record FailedDomain(Long domainId, String name) {}

    @Schema(name = "EduAiGoalContentMapResponse")
    public record GoalContentMap(List<GoalContentMatch> matches, List<FailedDomain> failedDomains) {}

    /**
     * EDU-106/115: source CURRICULUM_C | CURRICULUM_D | PREVIOUS_YEAR | RAG (chữ chép từ nguồn) | AI (AI tự viết khi không
     * khớp nguồn nào); ref chỉ có với RAG.
     */
    @Schema(name = "EduAiGoalContentMatchResponse")
    public record GoalContentMatch(Long goalId, String text, String source, RagRef ref, String from) {}

    /** Đoạn tham khảo RAG (thử tìm kiếm): sim là điểm đã xếp hạng (WEB ×0,8, cộng thưởng khớp tuổi/mã). */
    @Schema(name = "EduAiRagHitResponse")
    public record RagHit(String id, String type, String source, String content, double sim, RagMeta meta) {}

    @Schema(name = "EduAiRagMetaResponse")
    public record RagMeta(String url, String theme, String age) {}
}
