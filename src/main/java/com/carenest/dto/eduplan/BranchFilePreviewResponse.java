package com.carenest.dto.eduplan;

import java.time.LocalDate;
import java.util.List;

public record BranchFilePreviewResponse(List<Match> matches, List<Warning> warnings) {
    public record Match(int row, Long themeId, String themeName, LocalDate weekStart, String name) {}

    public record Warning(int row, String message) {}
}
