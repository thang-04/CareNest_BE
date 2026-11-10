package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.GoalFilePreviewResponse;
import com.carenest.dto.eduplan.GoalFilePreviewResponse.Break;
import com.carenest.dto.eduplan.GoalFilePreviewResponse.Metadata;
import com.carenest.dto.eduplan.GoalFilePreviewResponse.Theme;
import com.carenest.dto.eduplan.GoalFilePreviewResponse.Warning;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class YearPlanFileTable {
    private static final int CASE = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern YEAR = Pattern.compile("năm\\s*học\\s*:?\\s*(20\\d{2})\\s*[-–—/]\\s*(20\\d{2})", CASE);
    private static final Pattern AGE = Pattern.compile("(\\d{1,3})\\s*[-–—]\\s*(\\d{1,3})\\s*(tuổi|tháng)", CASE);
    private static final Pattern TITLE =
            Pattern.compile("kế\\s*hoạch[^\\n]{0,150}?năm\\s*học\\s*:?\\s*20\\d{2}\\s*[-–—/]\\s*20\\d{2}", CASE);
    private static final Pattern DATE =
            Pattern.compile("(?<!\\d)(?:\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[/.]\\d{1,2}[/.]\\d{4})(?!\\d)");

    private record Schedule(
            GoalFileGrid.Table table,
            int header,
            int offset,
            int no,
            int name,
            int abbr,
            int festivals,
            int period,
            int start,
            int end,
            int note) {}

    private record Entry(
            int row, String no, String name, String abbr, String festivals, String start, String end, String note) {}

    private YearPlanFileTable() {}

    static GoalFilePreviewResponse interpret(List<GoalFileGrid.Table> tables, String head, List<String> domains) {
        var goals = GoalFileTable.interpret(tables, domains);
        List<Warning> warnings = new ArrayList<>(goals.warnings());
        List<Theme> themes = new ArrayList<>();
        List<Break> breaks = new ArrayList<>();
        List<Schedule> schedules = schedules(tables);
        Set<String> abbrs = new HashSet<>();
        for (var schedule : schedules) {
            List<Entry> entries = entries(schedule, breaks, warnings);
            boolean ordinal = schedules.size() == 1
                    && entries.size() == goals.themeAbbrs().size();
            for (int i = 0; i < entries.size(); i++)
                ordinal &= entries.get(i).no().equals(String.valueOf(i + 1));
            for (int i = 0; i < entries.size(); i++) {
                var e = entries.get(i);
                String abbr = e.abbr().isBlank() && ordinal ? goals.themeAbbrs().get(i) : e.abbr();
                if (abbr.isBlank()) {
                    warnings.add(new Warning(
                            e.row(), "Không xác định được viết tắt chủ đề; kiểm tra thứ tự bảng lịch và cột chủ đề"));
                    continue;
                }
                if (!abbrs.add(normalized(abbr).replace(" ", ""))) {
                    warnings.add(new Warning(e.row(), "Viết tắt chủ đề trùng trong file; dòng sau chưa được thêm"));
                    continue;
                }
                themes.add(new Theme(
                        e.name(),
                        abbr,
                        e.festivals(),
                        date(e.start(), e.row(), warnings),
                        date(e.end(), e.row(), warnings),
                        e.note()));
            }
        }
        Metadata metadata = metadata(head, warnings);
        return new GoalFilePreviewResponse(
                goals.domains(),
                goals.themeAbbrs(),
                List.copyOf(warnings),
                metadata,
                List.copyOf(themes),
                List.copyOf(breaks));
    }

    private static Metadata metadata(String head, List<Warning> warnings) {
        String text = Normalizer.normalize(head, Normalizer.Form.NFC).replace('\u00a0', ' ');
        var year = YEAR.matcher(text);
        String schoolYear = null;
        if (year.find()) {
            if (Integer.parseInt(year.group(2)) == Integer.parseInt(year.group(1)) + 1) {
                schoolYear = year.group(1) + "-" + year.group(2);
            } else
                warnings.add(
                        new Warning(0, "Năm học trong file không phải hai năm liên tiếp; kiểm tra thông tin chung"));
        }
        var title = TITLE.matcher(text);
        String name = title.find() ? title.group().replaceAll("\\s+", " ").strip() : null;
        var age = AGE.matcher(text);
        Integer from = null;
        Integer to = null;
        if (age.find()) {
            int factor = normalized(age.group(3)).equals("tuổi") ? 12 : 1;
            int lo = Integer.parseInt(age.group(1)) * factor;
            int hi = Integer.parseInt(age.group(2)) * factor;
            if (lo >= 0 && hi > lo && hi <= 120) {
                from = lo;
                to = hi;
            } else warnings.add(new Warning(0, "Không nhận diện được khoảng nhóm tuổi hợp lệ trong file"));
        }
        return name == null && schoolYear == null && from == null ? null : new Metadata(name, schoolYear, from, to);
    }

    private static List<Schedule> schedules(List<GoalFileGrid.Table> tables) {
        List<Schedule> found = new ArrayList<>();
        int offset = 0;
        for (var t : tables) {
            for (int r = 0; r < Math.min(15, t.rows().size()); r++) {
                int no = -1, name = -1, abbr = -1, festivals = -1, period = -1, start = -1, end = -1, note = -1;
                for (int c = 0; c < t.rows().get(r).size(); c++) {
                    String h = normalized(t.at(r, c).text());
                    if (h.equals("stt")) no = c;
                    else if (h.equals("tên chủ đề") || h.equals("chủ đề")) name = c;
                    else if (h.equals("viết tắt") || h.equals("tên viết tắt")) abbr = c;
                    else if (h.contains("lễ hội")) festivals = c;
                    else if (h.equals("thời gian")) period = c;
                    else if (h.equals("từ ngày") || h.equals("bắt đầu")) start = c;
                    else if (h.equals("đến ngày") || h.equals("kết thúc")) end = c;
                    else if (h.equals("ghi chú")) note = c;
                }
                if (name >= 0 && (period >= 0 || start >= 0)) {
                    found.add(new Schedule(t, r, offset, no, name, abbr, festivals, period, start, end, note));
                    break;
                }
            }
            offset += t.rows().size();
        }
        return found;
    }

    private static List<Entry> entries(Schedule s, List<Break> breaks, List<Warning> warnings) {
        List<Entry> entries = new ArrayList<>();
        for (int r = s.header() + 1; r < s.table().rows().size(); r++) {
            String name = cell(s, r, s.name());
            if (name.isBlank()) continue;
            int line = s.offset() + r + 1;
            String period = cell(s, r, s.period());
            List<String> dates = dateTexts(period);
            String start = s.start() >= 0 ? cell(s, r, s.start()) : dates.isEmpty() ? "" : dates.getFirst();
            String end = s.end() >= 0 ? cell(s, r, s.end()) : dates.size() > 1 ? dates.get(1) : "";
            if (s.start() < 0 && dates.size() > 2) {
                warnings.add(new Warning(line, "Chủ đề có nhiều khoảng thời gian; chưa điền ngày, cần kiểm tra lại"));
                start = "";
                end = "";
            }
            String note = cell(s, r, s.note());
            String lower = normalized(name);
            if (lower.startsWith("nghỉ") || lower.startsWith("ôn tập")) {
                addBreak(name, start, end, note, line, breaks, warnings);
                continue;
            }
            String no = cell(s, r, s.no());
            String abbr = cell(s, r, s.abbr());
            if (s.no() >= 0 && !no.matches("\\d+") && abbr.isBlank()) continue;
            entries.add(new Entry(line, no, name, abbr, cell(s, r, s.festivals()), start, end, note));
            String n = normalized(note);
            if (n.contains("nghỉ") || n.contains("ôn tập")) {
                List<String> rest = dateTexts(note);
                if (rest.size() > 2) {
                    warnings.add(
                            new Warning(line, "Ghi chú có nhiều khoảng nghỉ; chưa thêm đợt nghỉ, cần kiểm tra lại"));
                } else if (!rest.isEmpty()) {
                    String label = DATE.matcher(note)
                            .replaceAll("")
                            .replaceAll("(?iu)^từ\\s+", "")
                            .replaceAll("[–—→-]", " ")
                            .replaceAll("\\s+", " ")
                            .strip();
                    addBreak(label, rest.getFirst(), rest.size() > 1 ? rest.get(1) : "", note, line, breaks, warnings);
                }
            }
        }
        return entries;
    }

    private static void addBreak(
            String name, String start, String end, String note, int row, List<Break> breaks, List<Warning> warnings) {
        LocalDate from = date(start, row, warnings);
        LocalDate until = date(end, row, warnings);
        if (from == null) {
            warnings.add(new Warning(row, "Đợt nghỉ chưa có ngày bắt đầu hợp lệ; chưa thêm"));
            return;
        }
        if (until != null && until.isBefore(from))
            warnings.add(new Warning(row, "Đợt nghỉ có ngày kết thúc trước ngày bắt đầu"));
        var entry = new Break(name, from, until, note);
        if (!breaks.contains(entry)) breaks.add(entry);
    }

    private static List<String> dateTexts(String text) {
        var matcher = DATE.matcher(text);
        List<String> found = new ArrayList<>();
        while (matcher.find()) found.add(matcher.group());
        return found;
    }

    private static LocalDate date(String text, int row, List<Warning> warnings) {
        if (text.isBlank()) return null;
        var parts = dateTexts(text);
        if (parts.size() != 1) {
            warnings.add(new Warning(row, "Ngày trong file không rõ; chưa điền ngày"));
            return null;
        }
        String raw = parts.getFirst();
        try {
            String[] p = raw.split("[-/.]");
            return p[0].length() == 4
                    ? LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]))
                    : LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
        } catch (DateTimeException | NumberFormatException ex) {
            warnings.add(new Warning(row, "Ngày trong file không hợp lệ; chưa điền ngày"));
            return null;
        }
    }

    private static String cell(Schedule s, int row, int col) {
        return col < 0
                ? ""
                : s.table().at(row, col).text().replaceAll("\\s+", " ").strip();
    }

    private static String normalized(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace('\u00a0', ' ')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
