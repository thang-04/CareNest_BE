package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.GoalFilePreviewResponse;
import com.carenest.dto.eduplan.GoalFilePreviewResponse.Content;
import com.carenest.dto.eduplan.GoalFilePreviewResponse.Domain;
import com.carenest.dto.eduplan.GoalFilePreviewResponse.Row;
import com.carenest.dto.eduplan.GoalFilePreviewResponse.Warning;
import com.carenest.exception.GlobalException;
import com.carenest.service.eduplan.impl.GoalFileGrid.Cell;
import com.carenest.service.eduplan.impl.GoalFileGrid.Table;
import com.carenest.utils.ApiCode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Diễn giải bảng mục tiêu thành bản xem trước (EDU-46). Hai bố cục:
 * <ul>
 *   <li>Bảng mẫu Thái Dương / bảng A CareNest xuất: STT | Mục tiêu | Nội dung | cột chủ đề viết tắt (dưới "Chủ đề thực
 *       hiện"); có thể thêm cột "Nhóm kết quả mong đợi". Dòng dải một ô: "I. …" lĩnh vực, "*…" lĩnh vực con, "- …" nhóm.
 *   <li>Mẫu CSV cũ: Lĩnh vực | Loại dòng | STT | Mục tiêu | Nội dung | Chủ đề (viết tắt, cách nhau bởi ;).
 * </ul>
 */
final class GoalFileTable {

    static final int MAX_ROWS = 2000;

    private static final Pattern ROMAN = Pattern.compile("^[ivxlc]+\\s*[.)]\\s*");
    private static final Pattern GOAL_NO = Pattern.compile("^(mt\\s*)?\\d+\\s*[.):]\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern BULLET = Pattern.compile("^[-+•*–]\\s*");

    private final List<String> domainNames;
    private final Map<String, List<Row>> rowsByDomain = new LinkedHashMap<>();
    private final List<Warning> warnings = new ArrayList<>();

    private GoalFileTable(List<String> domainNames) {
        this.domainNames = domainNames;
    }

    static GoalFilePreviewResponse interpret(List<Table> tables, List<String> domainNames) {
        for (Table t : tables) {
            int header = headerRow(t);
            if (header < 0) {
                continue;
            }
            if (t.rows().size() > MAX_ROWS) {
                throw GlobalException.of(ApiCode.GOAL_FILE_TOO_LARGE);
            }
            GoalFileTable reader = new GoalFileTable(domainNames);
            List<String> abbrs =
                    isCsvLayout(t, header) ? reader.readCsvLayout(t, header) : reader.readSchoolLayout(t, header);
            List<Domain> domains = reader.rowsByDomain.entrySet().stream()
                    .map(e -> new Domain(e.getKey(), e.getValue()))
                    .toList();
            return new GoalFilePreviewResponse(domains, abbrs, List.copyOf(reader.warnings));
        }
        throw GlobalException.of(ApiCode.GOAL_FILE_NO_TABLE);
    }

    /* ---------- bảng mẫu trường ---------- */

    private List<String> readSchoolLayout(Table t, int header) {
        int width = t.width();
        int goalCol = -1;
        int contentCol = -1;
        int groupCol = -1;
        for (int c = 0; c < width; c++) {
            String h = norm(t.at(header, c).text());
            if (h.contains("kết quả mong đợi") && groupCol < 0) {
                groupCol = c;
            } else if (h.contains("mục tiêu") && goalCol < 0) {
                goalCol = c;
            } else if (h.contains("nội dung") && contentCol < 0) {
                contentCol = c;
            }
        }
        if (goalCol < 0 || contentCol < 0) {
            throw GlobalException.of(ApiCode.GOAL_FILE_NO_TABLE);
        }
        // Cột chủ đề: sau cột nội dung; viết tắt ở dòng tiêu đề thứ hai nếu dòng đầu là "Chủ đề thực hiện"
        Map<Integer, String> themeCols = new LinkedHashMap<>();
        int start = header + 1;
        boolean secondHeader = false;
        if (header + 1 < t.rows().size()) {
            for (int c = contentCol + 1; c < width; c++) {
                Cell below = t.at(header + 1, c);
                if (!below.continued()
                        && !below.text().isBlank()
                        && t.at(header + 1, goalCol).continued()) {
                    secondHeader = true;
                    break;
                }
            }
        }
        if (secondHeader) {
            start = header + 2;
        }
        for (int c = contentCol + 1; c < width; c++) {
            String abbr =
                    secondHeader ? t.at(header + 1, c).text() : t.at(header, c).text();
            if (!abbr.isBlank() && !norm(abbr).contains("chủ đề thực hiện")) {
                themeCols.put(c, abbr.strip());
            }
        }

        String domain = null;
        Row goal = null;
        for (int r = start; r < t.rows().size(); r++) {
            int line = r + 1;
            Cell goalCell = t.at(r, goalCol);
            Cell contentCell = t.at(r, contentCol);
            List<String> ticks = new ArrayList<>();
            for (Map.Entry<Integer, String> col : themeCols.entrySet()) {
                Cell tick = t.at(r, col.getKey());
                if (!tick.continued() && !tick.text().isBlank()) {
                    ticks.add(col.getValue());
                }
            }
            String band = bandText(t, r, width, contentCol, themeCols.keySet());
            if (band != null && (marked(band) || exactDomain(band) != null)) {
                String resolved = marked(band) ? band(band, line, domain) : exactDomain(band);
                if (resolved != null) {
                    domain = resolved.isEmpty() ? null : resolved;
                }
                goal = null;
                continue;
            }
            if (goalCell.text().isBlank() && contentCell.text().isBlank()) {
                continue;
            }
            if (domain == null) {
                warnings.add(new Warning(line, "Dòng nằm ngoài lĩnh vực nào (cần dòng \"I. …\" phía trên), đã bỏ qua"));
                continue;
            }
            Cell groupCell = groupCol >= 0 ? t.at(r, groupCol) : Cell.EMPTY;
            if (!groupCell.continued() && !groupCell.text().isBlank()) {
                rows(domain).add(new Row("group", clean(groupCell.text()), null));
                goal = null;
            }
            if (!goalCell.continued() || goal == null) {
                String text = goalText(goalCell.text());
                if (text.isBlank()) {
                    warnings.add(new Warning(line, "Nội dung không thuộc mục tiêu nào, đã bỏ qua"));
                    continue;
                }
                goal = new Row("goal", text, new ArrayList<>());
                rows(domain).add(goal);
            }
            for (String content : contentCell.continued() ? List.<String>of() : contentLines(contentCell.text())) {
                goal.contents().add(new Content(content, List.copyOf(ticks)));
            }
        }
        return List.copyOf(themeCols.values());
    }

    /** Dòng dải: chỉ một chữ (ô gộp ngang), không có nội dung và dấu tích. */
    private static String bandText(Table t, int r, int width, int contentCol, Iterable<Integer> themeCols) {
        for (int c : themeCols) {
            if (!t.at(r, c).text().isBlank() && !t.at(r, c).continued()) {
                return null;
            }
        }
        String found = null;
        for (int c = 0; c < width; c++) {
            Cell cell = t.at(r, c);
            if (cell.continued() || cell.text().isBlank()) {
                continue;
            }
            if (found != null || c == contentCol) {
                return null;
            }
            found = cell.text();
        }
        if (found == null) {
            return null;
        }
        return found.strip();
    }

    /** Dòng dải có dấu: "I. …" lĩnh vực, "*…" lĩnh vực con, "- …" nhóm kết quả. */
    private static boolean marked(String text) {
        return ROMAN.matcher(norm(text)).find() || BULLET.matcher(text.strip()).find();
    }

    /** Dòng dải chỉ ghi đúng tên lĩnh vực (không số La Mã). */
    private String exactDomain(String text) {
        String n = norm(text).replaceFirst("^giáo dục\\s+", "");
        return domainNames.stream().filter(d -> norm(d).equals(n)).findFirst().orElse(null);
    }

    /** Trả tên lĩnh vực mới; "" = lĩnh vực không nhận ra; null = lĩnh vực con/nhóm (đã thêm dòng). */
    private String band(String text, int line, String domain) {
        String n = norm(text);
        if (ROMAN.matcher(n).find()) {
            String name = domainOf(text);
            if (name == null) {
                warnings.add(new Warning(line, "Không nhận ra lĩnh vực \"" + text + "\", các dòng bên dưới đã bỏ qua"));
                return "";
            }
            return name;
        }
        if (domain == null) {
            warnings.add(new Warning(line, "Dòng nằm ngoài lĩnh vực nào (cần dòng \"I. …\" phía trên), đã bỏ qua"));
            return null;
        }
        String kind = text.startsWith("*") ? "sub" : "group";
        rows(domain).add(new Row(kind, clean(text), null));
        return null;
    }

    /* ---------- mẫu CSV cũ ---------- */

    private static boolean isCsvLayout(Table t, int header) {
        boolean domain = false;
        boolean kind = false;
        for (int c = 0; c < t.width(); c++) {
            String h = norm(t.at(header, c).text());
            domain |= h.equals("lĩnh vực");
            kind |= h.equals("loại dòng");
        }
        return domain && kind;
    }

    private List<String> readCsvLayout(Table t, int header) {
        int domainCol = -1;
        int kindCol = -1;
        int goalCol = -1;
        int contentCol = -1;
        int themeCol = -1;
        for (int c = 0; c < t.width(); c++) {
            String h = norm(t.at(header, c).text());
            if (h.equals("lĩnh vực")) {
                domainCol = c;
            } else if (h.equals("loại dòng")) {
                kindCol = c;
            } else if (h.startsWith("mục tiêu")) {
                goalCol = c;
            } else if (h.startsWith("nội dung")) {
                contentCol = c;
            } else if (h.startsWith("chủ đề")) {
                themeCol = c;
            }
        }
        List<String> abbrs = new ArrayList<>();
        Row goal = null;
        String goalDomain = null;
        for (int r = header + 1; r < t.rows().size(); r++) {
            int line = r + 1;
            String domainText = t.at(r, domainCol).text();
            String text = clean(t.at(r, goalCol).text());
            if (domainText.isBlank() && text.isBlank()) {
                continue;
            }
            String domain = domainOf(domainText);
            if (domain == null || text.isBlank()) {
                warnings.add(new Warning(
                        line, domain == null ? "Không nhận ra lĩnh vực, đã bỏ qua" : "Thiếu câu mục tiêu, đã bỏ qua"));
                continue;
            }
            String kindLabel = norm(t.at(r, kindCol).text());
            if (kindLabel.contains("lĩnh vực con")) {
                rows(domain).add(new Row("sub", text, null));
                goal = null;
                continue;
            }
            if (kindLabel.contains("kết quả")) {
                rows(domain).add(new Row("group", text, null));
                goal = null;
                continue;
            }
            text = goalText(text);
            if (goal == null || !domain.equals(goalDomain) || !norm(goal.text()).equals(norm(text))) {
                goal = new Row("goal", text, new ArrayList<>());
                goalDomain = domain;
                rows(domain).add(goal);
            }
            String content = clean(t.at(r, contentCol).text());
            if (!content.isBlank()) {
                List<String> ticks = new ArrayList<>();
                for (String a : t.at(r, themeCol).text().split("[;,]")) {
                    if (!a.isBlank()) {
                        ticks.add(a.strip());
                        if (abbrs.stream().noneMatch(x -> x.equalsIgnoreCase(a.strip()))) {
                            abbrs.add(a.strip());
                        }
                    }
                }
                goal.contents().add(new Content(content, List.copyOf(ticks)));
            }
        }
        return abbrs;
    }

    /* ---------- tiện ích ---------- */

    private static int headerRow(Table t) {
        int limit = Math.min(t.rows().size(), 15);
        for (int r = 0; r < limit; r++) {
            boolean goal = false;
            boolean content = false;
            for (int c = 0; c < t.rows().get(r).size(); c++) {
                String h = norm(t.at(r, c).text());
                if (h.length() > 60) {
                    continue;
                }
                goal |= h.contains("mục tiêu");
                content |= h.contains("nội dung");
            }
            if (goal && content) {
                return r;
            }
        }
        return -1;
    }

    private List<Row> rows(String domain) {
        return rowsByDomain.computeIfAbsent(domain, k -> new ArrayList<>());
    }

    /** "I. Giáo dục phát triển thể chất", "Phát triển thể chất", "Thể chất" ⇒ tên lĩnh vực trong cấu hình. */
    private String domainOf(String text) {
        String n = ROMAN.matcher(norm(text))
                .replaceFirst("")
                .replaceFirst("^giáo dục\\s+", "")
                .strip();
        if (n.isEmpty()) {
            return null;
        }
        for (String name : domainNames) {
            if (norm(name).equals(n)) {
                return name;
            }
        }
        for (String name : domainNames) {
            String d = norm(name);
            if (d.contains(n) || n.contains(d)) {
                return name;
            }
        }
        return null;
    }

    private static String goalText(String text) {
        return GOAL_NO.matcher(clean(text)).replaceFirst("").strip();
    }

    /** Một ô nội dung có thể chứa nhiều dòng "- …": mỗi dòng là một nội dung. */
    private static List<String> contentLines(String text) {
        List<String> lines =
                text.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
        boolean bulleted = lines.size() > 1
                && lines.stream().allMatch(l -> BULLET.matcher(l).find());
        if (!bulleted) {
            String one = clean(String.join(" ", lines));
            return one.isEmpty() ? List.of() : List.of(one);
        }
        return lines.stream()
                .map(GoalFileTable::clean)
                .filter(l -> !l.isEmpty())
                .toList();
    }

    private static String clean(String text) {
        return BULLET.matcher(text.strip())
                .replaceFirst("")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
