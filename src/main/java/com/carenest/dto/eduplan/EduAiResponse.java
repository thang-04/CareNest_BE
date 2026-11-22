package com.carenest.dto.eduplan;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Kết quả AI (nháp, EDU-95/96): server đã lọc MT, loại giờ, ô không có trong yêu cầu. */
public final class EduAiResponse {
    private EduAiResponse() {}

    public record BranchNames(List<BranchSuggestion> suggestions) {}

    public record BranchSuggestion(String weekStart, String name, String reason) {}

    public record BranchName(String name) {}

    public record LessonSteps(Step a, Step b, Step c) {}

    public record Step(String teacher, String child) {}

    public record Activity(String goalId, String slot, String name) {}

    /** path: đường dẫn ô trong nội dung giáo án ngày (vd. days.0.sections.hoc.aims). */
    public record DayEdit(List<CellChange> changes) {}

    public record CellChange(String path, String label, String before, String after) {}

    /** Tóm tắt cho người duyệt: chỉ gợi ý, không lưu vào hồ sơ. */
    public record DayReview(List<String> summary, List<ReviewNote> notes) {}

    public record ReviewNote(String path, String label, String text) {}

    /** EDU-103: cặp chủ đề–nội dung AI gợi ý (đã lọc chống bịa); lĩnh vực lỗi/quá hạn nằm ở failedDomains. */
    @Schema(name = "EduAiThemeContentMapResponse")
    public record ThemeContentMap(List<ThemeContentMatch> matches, List<FailedDomain> failedDomains) {}

    /** from: cụm nguyên văn trong yêu cầu, tên chủ đề, lễ hội hoặc tên nhánh của chính chủ đề đó. */
    @Schema(name = "EduAiThemeContentMatchResponse")
    public record ThemeContentMatch(Long themeId, Long contentId, String from) {}

    @Schema(name = "EduAiFailedDomainResponse")
    public record FailedDomain(Long domainId, String name) {}
}
