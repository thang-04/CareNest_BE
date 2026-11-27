package com.carenest.service.notification.impl;

import com.carenest.utils.enums.NotificationType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Danh mục quy tắc thông báo tự động (PAR-09, clarify Q1): loại nào bắt buộc, sửa được câu chữ, biến được phép và câu
 * chữ mặc định. Loại không khai báo ở đây là bắt buộc, chỉ xem (câu chữ do code nghiệp vụ tạo).
 */
final class NotificationRuleCatalog {
    private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z]+)}");

    record Rule(
            NotificationType type,
            String ruleCode,
            String label,
            boolean mandatory,
            boolean editable,
            List<String> variables,
            String defaultTitle,
            String defaultBody) {}

    private static final Map<NotificationType, Rule> RULES = new EnumMap<>(NotificationType.class);

    static {
        editable(
                NotificationType.CHILD_PICKED_UP,
                "PCK-04",
                "Trẻ đã được đón",
                true,
                List.of("tenTre", "gio", "ngay", "nguoiDon"),
                "{tenTre} đã được đón",
                "{tenTre} đã được đón lúc {gio} ngày {ngay}. {nguoiDon}");
        editable(
                NotificationType.DAILY_HEALTH_NOTED,
                "HLT-14",
                "Sức khỏe trong ngày",
                true,
                List.of("tenTre", "ngay"),
                "Thông tin sức khỏe trong ngày",
                "{tenTre} có ghi nhận sức khỏe trong ngày {ngay}. Mở Sổ sức khỏe để xem chi tiết.");
        editable(
                NotificationType.MEAL_COUNT_RECONFIRM,
                "NUT-03",
                "Sổ báo ăn cần xác nhận lại",
                true,
                List.of("ngay"),
                "Sổ báo ăn cần xác nhận lại",
                "Số suất ngày {ngay} đã thay đổi sau khi xác nhận (giáo viên sửa điểm danh). Vào Sổ báo ăn để xác"
                        + " nhận lại; bếp vẫn dùng số đã xác nhận trước đó.");
        editable(
                NotificationType.MENU_PUBLISHED,
                "NUT-08",
                "Công bố thực đơn tuần",
                false,
                List.of("tuan"),
                "Thực đơn tuần mới",
                "Thực đơn tuần {tuan} đã được công bố. Mở mục Thực đơn để xem.");
        editable(
                NotificationType.MENU_CHANGED,
                "NUT-08",
                "Sửa thực đơn đã công bố",
                true,
                List.of("ngay", "lyDo"),
                "Thực đơn ngày {ngay} thay đổi",
                "Thực đơn ngày {ngay} đã được điều chỉnh. Lý do: {lyDo}. Mở mục Thực đơn để xem.");
        editable(
                NotificationType.HEALTH_RESULT_PUBLISHED,
                "PAR-03",
                "Công bố kết quả cân đo, khám sức khỏe",
                false,
                List.of("tenTre"),
                "Kết quả sức khỏe mới",
                "Đã có kết quả cân đo, khám sức khỏe mới của {tenTre}. Mở Sổ sức khỏe để xem.");
        editable(
                NotificationType.CHILD_EVALUATION_CONFIRMED,
                "PAR-03",
                "Công bố đánh giá trẻ",
                false,
                List.of("tenTre"),
                "Đánh giá mới của bé",
                "Đã có đánh giá mới của {tenTre}. Mở ứng dụng để xem.");
        editable(
                NotificationType.THEME_EVALUATION_CONFIRMED,
                "PAR-03",
                "Công bố đánh giá chủ đề",
                false,
                List.of("tenTre", "lop"),
                "Đánh giá chủ đề mới",
                "Lớp {lop} đã có đánh giá chủ đề của {tenTre}. Mở ứng dụng để xem.");
        editable(
                NotificationType.LEAVE_REQUEST_CREATED,
                "ATT-05",
                "Đơn nghỉ của phụ huynh",
                false,
                List.of("tenTre", "tuNgay", "denNgay"),
                "Đơn xin nghỉ mới",
                "Phụ huynh gửi đơn xin nghỉ cho {tenTre} từ {tuNgay} đến {denNgay}. Vào Điểm danh để xem.");
    }

    private NotificationRuleCatalog() {}

    private static void editable(
            NotificationType type,
            String code,
            String label,
            boolean mandatory,
            List<String> variables,
            String title,
            String body) {
        RULES.put(type, new Rule(type, code, label, mandatory, true, variables, title, body));
    }

    /** Quy tắc của loại; loại có sẵn khác ⇒ bắt buộc, chỉ xem. */
    static Rule of(NotificationType type) {
        Rule rule = RULES.get(type);
        return rule != null ? rule : new Rule(type, null, label(type), true, false, List.of(), null, null);
    }

    /** Thay {bien} bằng giá trị; biến thiếu giá trị ⇒ chuỗi rỗng. */
    static String render(String template, Map<String, String> values) {
        Matcher m = VARIABLE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.getOrDefault(m.group(1), "");
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(out);
        return out.toString().trim();
    }

    /** Biến dùng trong câu chữ mà không thuộc danh sách được phép. */
    static List<String> unknownVariables(String template, List<String> allowed) {
        if (template == null) {
            return List.of();
        }
        Matcher m = VARIABLE.matcher(template);
        java.util.ArrayList<String> unknown = new java.util.ArrayList<>();
        while (m.find()) {
            if (!allowed.contains(m.group(1))) {
                unknown.add(m.group(1));
            }
        }
        return unknown;
    }

    private static String label(NotificationType type) {
        return switch (type) {
            case MEAL_COUNT_RECONFIRM -> "Sổ báo ăn cần xác nhận lại";
            case CHILD_WITHDRAWN -> "Trẻ thôi học";
            case GUARDIAN_PHONE_CHANGE_REQUESTED -> "Yêu cầu đổi số điện thoại phụ huynh";
            case GUARDIAN_PHONE_CHANGE_DECIDED -> "Kết quả duyệt đổi số điện thoại";
            case CHILD_TB_CONTACT -> "Trẻ tiếp xúc người bệnh lao";
            case CHILD_TB_CONTACT_DATE_CHANGED -> "Đổi ngày tiếp xúc người bệnh lao";
            case HEALTH_RESULT_UPDATED -> "Kết quả sức khỏe được cập nhật";
            case HEALTH_CHECK_OPENED -> "Mở đợt khám / kỳ cân đo";
            case CHILD_PLACED -> "Trẻ được xếp vào lớp";
            case ATTENDANCE_DAY_UNLOCKED -> "Mở lại ngày điểm danh";
            case EDU_SUBMITTED -> "Kế hoạch giáo dục gửi duyệt";
            case EDU_STEP_APPROVED -> "Kế hoạch giáo dục được duyệt bước";
            case EDU_RELEASED -> "Kế hoạch giáo dục ban hành";
            case EDU_RETURNED -> "Kế hoạch giáo dục bị trả lại";
            case EDU_DEADLINE_EXTENDED -> "Gia hạn kế hoạch giáo dục";
            case EDU_DEADLINE_NEAR -> "Kế hoạch giáo dục gần đến hạn";
            case EDU_OVERDUE_DIGEST -> "Kế hoạch giáo dục quá hạn (báo Hiệu trưởng)";
            case SCHOOL_ANNOUNCEMENT -> "Thông báo của trường";
            default -> type.name();
        };
    }
}
