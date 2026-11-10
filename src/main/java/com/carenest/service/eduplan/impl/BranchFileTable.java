package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.BranchFilePreviewResponse.Warning;
import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class BranchFileTable {
    private static final Pattern LABEL =
            Pattern.compile("^[-–•]?\\s*nhánh\\s*\\d+\\s*[:.)-]?\\s*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    record Entry(int row, String themeName, String name) {}

    record Parsed(List<Entry> entries, List<Warning> warnings) {}

    private BranchFileTable() {}

    static Parsed interpret(List<GoalFileGrid.Table> tables) {
        int offset = 0;
        for (var table : tables) {
            if (table.rows().size() > GoalFileTable.MAX_ROWS) {
                throw GlobalException.of(ApiCode.BRANCH_FILE_TOO_LARGE);
            }
            for (int r = 0; r < table.rows().size(); r++) {
                int themeCol = -1;
                int nameCol = -1;
                for (int c = 0; c < table.rows().get(r).size(); c++) {
                    String header = normalized(table.at(r, c).text());
                    if (header.equals("chủ đề")) themeCol = c;
                    if (header.equals("chủ đề nhánh")) nameCol = c;
                }
                if (themeCol < 0 || nameCol < 0) continue;
                List<Entry> entries = new ArrayList<>();
                List<Warning> warnings = new ArrayList<>();
                String theme = "";
                for (int line = r + 1; line < table.rows().size(); line++) {
                    String sourceTheme = table.at(line, themeCol).text().strip();
                    if (!sourceTheme.isBlank()) theme = sourceTheme.replaceAll("\\s+", " ");
                    String text = table.at(line, nameCol).text().strip();
                    String lower = normalized(text);
                    if (text.isBlank()
                            || lower.startsWith("nghỉ")
                            || lower.startsWith("ôn tập")
                            || (lower.matches("^(từ\\s+)?\\d.*")
                                    && (lower.contains("nghỉ") || lower.contains("ôn tập")))) continue;
                    String name = LABEL.matcher(text).replaceFirst("").strip();
                    if (name.isBlank()) continue;
                    int row = offset + line + 1;
                    if (theme.isBlank()) warnings.add(new Warning(row, "Không xác định được chủ đề của tên nhánh"));
                    else entries.add(new Entry(row, theme, name));
                }
                return new Parsed(List.copyOf(entries), List.copyOf(warnings));
            }
            offset += table.rows().size();
        }
        throw GlobalException.of(ApiCode.BRANCH_FILE_NO_TABLE);
    }

    static String normalized(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace('\u00a0', ' ')
                .strip()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
