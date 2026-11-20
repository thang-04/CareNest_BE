package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.EduAiRequest;
import com.carenest.dto.eduplan.EduAiResponse;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.AiClientException;
import com.carenest.integration.ai.AiGateway;
import com.carenest.integration.ai.AiProperties;
import com.carenest.integration.ai.AiRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduAiService;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AiErrorReason;
import com.carenest.utils.enums.AiTask;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * EDU-95/96: gọi AI qua {@link AiGateway} (tác vụ EDU_PLAN, JSON), server lọc lại output: chỉ tuần/MT/loại giờ/ô có trong yêu cầu, cắt độ
 * dài. Không ghi DB, không giữ giao dịch trong lúc chờ AI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduAiServiceImpl implements EduAiService {
    private static final String LOG_PREFIX = "[EduAiServiceImpl]";
    private static final int TEXT_MAX = 2000;
    private static final int NAME_MAX = 150;
    private static final String TEACHER =
            "Bạn là giáo viên mầm non Việt Nam giàu kinh nghiệm, soạn kế hoạch giáo dục theo Chương trình Giáo dục mầm non."
                    + " Viết tiếng Việt chuẩn, ngắn gọn, phù hợp lứa tuổi. Không bịa mục tiêu, không nhắc tên trẻ."
                    + " Chỉ trả về JSON đúng khung được yêu cầu, không thêm chữ nào ngoài JSON.";
    private static final List<String> SECTIONS = List.of("hoc", "ngoai_troi", "goc", "chieu");
    private static final Map<String, String> SECTION_LABELS = Map.of(
            "hoc", "Hoạt động học",
            "ngoai_troi", "Hoạt động ngoài trời",
            "goc", "Hoạt động góc",
            "chieu", "Hoạt động chiều");
    private static final Map<String, String> FIELD_LABELS = Map.of(
            "aims", "Mục đích",
            "prep", "Chuẩn bị",
            "title", "Tên hoạt động",
            "teacher", "Hoạt động của cô",
            "child", "Hoạt động của trẻ",
            "note", "Ghi chú");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;
    private final EduConfigService eduConfigService;
    private final EduClassDocService classDocService;

    @Override
    public EduAiResponse.BranchNames suggestBranchNames(CurrentUser currentUser, EduAiRequest.BranchNames request) {
        String logPrefix = LOG_PREFIX + "|suggestBranchNames|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            Set<String> weeks = new HashSet<>();
            StringBuilder p =
                    new StringBuilder("Đặt tên chủ đề nhánh cho từng tuần (mỗi tuần một nhánh, các tên khác nhau):\n");
            for (EduAiRequest.Theme t : request.themes()) {
                p.append("- Chủ đề \"").append(t.name()).append('"');
                if (t.festivals() != null && !t.festivals().isBlank())
                    p.append(" (sự kiện, ngày lễ: ").append(t.festivals()).append(')');
                p.append(", các tuần bắt đầu: ");
                for (EduAiRequest.Week w : t.weeks()) {
                    weeks.add(w.start());
                    p.append(w.start()).append(' ');
                }
                p.append('\n');
            }
            List<String> taken = request.taken() == null ? List.of() : request.taken();
            if (!taken.isEmpty())
                p.append("Tên đã dùng, không lặp lại: ")
                        .append(String.join("; ", taken))
                        .append('\n');
            p.append("Trả về {\"suggestions\":[{\"weekStart\":\"yyyy-MM-dd\",\"name\":\"...\",\"reason\":\"...\"}]}.");
            JsonNode out = ask("edu-branch-names", p.toString());
            Set<String> used = new HashSet<>();
            taken.forEach(n -> used.add(n.trim().toLowerCase(Locale.ROOT)));
            List<EduAiResponse.BranchSuggestion> list = new ArrayList<>();
            for (JsonNode s : out.path("suggestions")) {
                String week = s.path("weekStart").asString("");
                String name = clip(s.path("name").asString(""), NAME_MAX);
                // Chỉ tuần có trong yêu cầu, mỗi tuần một tên, không trùng tên đã dùng
                if (name.isBlank() || !weeks.contains(week) || !used.add(name.toLowerCase(Locale.ROOT))) continue;
                weeks.remove(week);
                list.add(new EduAiResponse.BranchSuggestion(
                        week, name, clip(s.path("reason").asString(""), 300)));
            }
            log.info("{}|END", logPrefix);
            return new EduAiResponse.BranchNames(list);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public EduAiResponse.BranchName rewriteBranchName(CurrentUser currentUser, EduAiRequest.RewriteBranchName request) {
        String logPrefix = LOG_PREFIX + "|rewriteBranchName|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            int attempt = request.attempt() == null ? 1 : request.attempt() + 1;
            String p = "Viết lại tên chủ đề nhánh \"" + request.name() + "\""
                    + (request.themeName() == null ? "" : " của chủ đề \"" + request.themeName() + "\"")
                    + " cho hay, gọn, hợp trẻ mầm non (cách viết thứ " + attempt
                    + ", khác các lần trước). Trả về {\"name\":\"...\"}.";
            String name = clip(ask("edu-branch-rewrite", p).path("name").asString(""), NAME_MAX);
            if (name.isBlank()) throw GlobalException.of(ApiCode.AI_SERVICE_UNAVAILABLE);
            log.info("{}|END", logPrefix);
            return new EduAiResponse.BranchName(name);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public EduAiResponse.LessonSteps suggestLessonSteps(CurrentUser currentUser, EduAiRequest.LessonSteps request) {
        String logPrefix = LOG_PREFIX + "|suggestLessonSteps|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            String p =
                    "Soạn tiến trình giờ học (mẫu Thái Dương): a) Ổn định tổ chức, gây hứng thú; b) Nội dung trọng tâm;"
                            + " c) Kết thúc. Mỗi phần có hoạt động của cô và hoạt động của trẻ, gạch đầu dòng.\n"
                            + "Môn: " + nz(request.subject()) + "\nĐề tài: " + join(request.topics())
                            + "\nMục tiêu của giờ: " + join(request.goals())
                            + "\nTrả về {\"a\":{\"teacher\":\"...\",\"child\":\"...\"},\"b\":{...},\"c\":{...}}.";
            JsonNode out = ask("edu-lesson-steps", p);
            log.info("{}|END", logPrefix);
            return new EduAiResponse.LessonSteps(step(out.path("a")), step(out.path("b")), step(out.path("c")));
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<EduAiResponse.Activity> suggestActivities(CurrentUser currentUser, EduAiRequest.Activities request) {
        String logPrefix = LOG_PREFIX + "|suggestActivities|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            Map<String, String> slots = new LinkedHashMap<>();
            eduConfigService.getEduConfig().weekRows().forEach(r -> slots.put(r.key(), r.label()));
            StringBuilder p = new StringBuilder("Chủ đề: ")
                    .append(nz(request.themeName()))
                    .append("\nVới mỗi mục tiêu dưới đây, đề xuất đúng một hoạt động giáo dục giúp trẻ đạt mục tiêu."
                            + " Chọn loại giờ trong danh sách: ");
            slots.forEach((k, v) -> p.append(k).append(" (").append(v).append("); "));
            p.append("\nMục tiêu:\n");
            Set<String> ids = new HashSet<>();
            for (EduAiRequest.Goal g : request.goals()) {
                ids.add(g.id());
                p.append("- id ").append(g.id()).append(": ").append(nz(g.text()));
                if (g.lines() != null && !g.lines().isEmpty())
                    p.append(" | nội dung: ").append(String.join("; ", g.lines()));
                p.append('\n');
            }
            p.append("Trả về {\"items\":[{\"goalId\":\"...\",\"slot\":\"...\",\"name\":\"...\"}]}.");
            JsonNode out = ask("edu-activities", p.toString());
            List<EduAiResponse.Activity> list = new ArrayList<>();
            Set<String> done = new HashSet<>();
            // EDU-95: chỉ MT và loại giờ có trong yêu cầu, mỗi MT một gợi ý
            for (JsonNode a : out.path("items")) {
                String goal = a.path("goalId").asString("");
                String slot = a.path("slot").asString("");
                String name = clip(a.path("name").asString(""), 300);
                if (!ids.contains(goal) || !slots.containsKey(slot) || name.isBlank() || !done.add(goal)) continue;
                list.add(new EduAiResponse.Activity(goal, slot, name));
            }
            log.info("{}|END", logPrefix);
            return list;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public EduAiResponse.DayEdit editDay(CurrentUser currentUser, String docKey, EduAiRequest.EditDay request) {
        String logPrefix = LOG_PREFIX + "|editDay|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            // Người soạn, giáo án còn sửa được; khóa nhả ngay sau khi kiểm, không giữ trong lúc chờ AI
            EduClassDoc day = classDocService.requireDayAccess(currentUser, docKey, true);
            Map<String, Cell> cells = cells(day);
            StringBuilder p = new StringBuilder("Giáo viên yêu cầu: \"")
                    .append(request.prompt())
                    .append("\"\nSửa giáo án theo yêu cầu, chỉ đổi những ô cần đổi, giữ mục tiêu đã gắn."
                            + " Các ô hiện có (đường dẫn | tên ô | nội dung):\n");
            cells.forEach((path, c) -> p.append(path)
                    .append(" | ")
                    .append(c.label())
                    .append(" | ")
                    .append(c.text())
                    .append('\n'));
            p.append("Trả về {\"changes\":[{\"path\":\"...\",\"after\":\"...\"}]}.");
            JsonNode out = ask("edu-day-edit", p.toString());
            List<EduAiResponse.CellChange> changes = new ArrayList<>();
            Set<String> done = new HashSet<>();
            for (JsonNode c : out.path("changes")) {
                String path = c.path("path").asString("");
                Cell cell = cells.get(path);
                String after = clip(c.path("after").asString(""), TEXT_MAX);
                if (cell == null || after.equals(cell.text()) || !done.add(path)) continue;
                changes.add(new EduAiResponse.CellChange(path, cell.label(), cell.text(), after));
            }
            log.info("{}|END", logPrefix);
            return new EduAiResponse.DayEdit(changes);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public EduAiResponse.DayReview reviewDay(CurrentUser currentUser, String docKey) {
        String logPrefix = LOG_PREFIX + "|reviewDay|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            EduClassDoc day = classDocService.requireDayReviewAccess(currentUser, docKey);
            Map<String, Cell> cells = cells(day);
            StringBuilder p =
                    new StringBuilder("Đọc giáo án ngày dưới đây như người duyệt. Viết 3–5 câu tóm tắt (dạy gì,"
                            + " bám mục tiêu nào, điểm khác thường) rồi nêu các ý cần xem lại, mỗi ý gắn đúng một ô: hoạt động"
                            + " có giúp đạt mục tiêu không; nội dung có hợp lứa tuổi không; hoạt động của cô và của trẻ có khớp"
                            + " từng bước không; mục đích có quan sát được không (tránh \"trẻ hiểu\", \"trẻ biết\")."
                            + " Không kết luận duyệt hay trả lại.\nCác ô (đường dẫn | tên ô | nội dung):\n");
            cells.forEach((path, c) -> {
                if (!c.text().isBlank())
                    p.append(path)
                            .append(" | ")
                            .append(c.label())
                            .append(" | ")
                            .append(c.text())
                            .append('\n');
            });
            p.append("Trả về {\"summary\":[\"...\"],\"notes\":[{\"path\":\"...\",\"text\":\"...\"}]}.");
            JsonNode out = ask("edu-day-review", p.toString());
            List<String> summary = new ArrayList<>();
            for (JsonNode s : out.path("summary"))
                if (summary.size() < 5 && !s.asString("").isBlank()) summary.add(clip(s.asString(""), 500));
            List<EduAiResponse.ReviewNote> notes = new ArrayList<>();
            for (JsonNode n : out.path("notes")) {
                String path = n.path("path").asString("");
                Cell cell = cells.get(path);
                String text = clip(n.path("text").asString(""), 500);
                if (cell == null || text.isBlank() || notes.size() >= 20) continue;
                notes.add(new EduAiResponse.ReviewNote(path, cell.label(), text));
            }
            log.info("{}|END", logPrefix);
            return new EduAiResponse.DayReview(summary, notes);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private record Cell(String label, String text) {}

    /** Ô chữ của giáo án ngày theo đường dẫn: mục đích, chuẩn bị, từng dòng tiến hành của 4 mục mỗi ngày. */
    private Map<String, Cell> cells(EduClassDoc day) {
        Map<String, Cell> cells = new LinkedHashMap<>();
        JsonNode days = objectMapper
                .readTree(day.getContent() == null ? "{}" : day.getContent())
                .path("days");
        for (int i = 0; i < days.size(); i++) {
            JsonNode d = days.get(i);
            if (!d.path("sections").isObject()) continue;
            String when = dayLabel(d.path("date").asString(""));
            for (String s : SECTIONS) {
                JsonNode sec = d.path("sections").path(s);
                String base = "days." + i + ".sections." + s;
                String head = when + " · " + SECTION_LABELS.get(s);
                for (String f : List.of("aims", "prep"))
                    cells.put(
                            base + "." + f,
                            new Cell(
                                    head + " · " + FIELD_LABELS.get(f),
                                    sec.path(f).asString("")));
                JsonNode rows = sec.path("rows");
                for (int j = 0; j < rows.size(); j++)
                    for (String f : List.of("title", "teacher", "child", "note"))
                        cells.put(
                                base + ".rows." + j + "." + f,
                                new Cell(
                                        head + " · dòng " + (j + 1) + " · " + FIELD_LABELS.get(f),
                                        rows.get(j).path(f).asString("")));
            }
        }
        return cells;
    }

    private static String dayLabel(String iso) {
        try {
            LocalDate d = LocalDate.parse(iso);
            return "Thứ " + (d.getDayOfWeek().getValue() + 1) + " " + DAY_MONTH.format(d);
        } catch (DateTimeParseException ex) {
            return iso;
        }
    }

    /** Gọi AI chế độ JSON; tắt/lỗi/hết giờ/JSON hỏng ⇒ 8001/8002 (AI-03), dữ liệu của giáo viên giữ nguyên. */
    JsonNode ask(String task, String userPrompt) {
        if (!aiGateway.isAvailable(AiTask.EDU_PLAN)) throw GlobalException.of(ApiCode.AI_SERVICE_UNAVAILABLE);
        Duration budget = aiGateway
                .taskConfig(AiTask.EDU_PLAN)
                .map(AiProperties.TaskConfig::deadline)
                .orElse(Duration.ofSeconds(30));
        String text;
        try {
            text = aiGateway
                    .complete(new AiRequest(
                            AiTask.EDU_PLAN,
                            "edu/" + task,
                            TEACHER,
                            userPrompt,
                            Instant.now().plus(budget)))
                    .content();
        } catch (AiClientException ex) {
            throw GlobalException.withCause(
                    ex.getReason() == AiErrorReason.TIMEOUT ? ApiCode.AI_TIMEOUT : ApiCode.AI_SERVICE_UNAVAILABLE, ex);
        }
        String json = text == null ? "" : text.trim();
        // Một số model vẫn bọc ```json … ```
        if (json.startsWith("```"))
            json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (RuntimeException ex) {
            throw GlobalException.withCause(ApiCode.AI_SERVICE_UNAVAILABLE, ex);
        }
        if (node == null || !node.isObject()) throw GlobalException.of(ApiCode.AI_SERVICE_UNAVAILABLE);
        return node;
    }

    private static EduAiResponse.Step step(JsonNode n) {
        return new EduAiResponse.Step(
                clip(n.path("teacher").asString(""), TEXT_MAX),
                clip(n.path("child").asString(""), TEXT_MAX));
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String join(List<String> list) {
        return list == null || list.isEmpty() ? "(không có)" : String.join("; ", list);
    }
}
