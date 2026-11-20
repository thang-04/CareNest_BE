package com.carenest.service.notification.impl;

import com.carenest.entity.notification.Notification;
import com.carenest.integration.email.CareNestEmailLayout;
import com.carenest.integration.email.EmailMessage;
import com.carenest.utils.enums.NotificationType;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dựng email cho thông báo kế hoạch giáo dục (G94-386) trên khung chung {@link CareNestEmailLayout}. */
final class EduEmailComposer {

    private static final DateTimeFormatter AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    // Câu nhắc hạn của remindNearDeadlines: "…, còn N ngày tới hạn dd/MM/yyyy"
    private static final Pattern DAYS_LEFT = Pattern.compile("còn (\\d+) ngày");

    private EduEmailComposer() {}

    static EmailMessage compose(
            Notification n, String scope, String recipientEmail, String fullName, String webBaseUrl, ZoneId zone) {
        String title = n.getTitle();
        String subject = "[CareNest] " + title;
        if (n.getType() == NotificationType.EDU_DEADLINE_NEAR) {
            Matcher m = DAYS_LEFT.matcher(n.getBody());
            if (m.find()) subject += " – còn " + m.group(1) + " ngày";
        }
        CareNestEmailLayout layout = CareNestEmailLayout.titled(heading(n.getType()))
                .logo(url(webBaseUrl, CareNestEmailLayout.LOGO_PATH))
                .preheader(title)
                .greeting(fullName)
                .paragraph(n.getBody())
                .fact("Hồ sơ", documentName(title))
                .fact("Phạm vi", scope)
                .fact("Trạng thái", status(n.getType()))
                .fact("Thời điểm", AT_FORMAT.format(n.getCreatedAt().atZone(zone)))
                .action("Mở hồ sơ", url(webBaseUrl, n.getLink()))
                .note("Bạn cần đăng nhập CareNest để mở hồ sơ; hệ thống vẫn kiểm tra quyền xem, duyệt như trên web.");
        return new EmailMessage(List.of(recipientEmail), subject, layout.text(), layout.html());
    }

    static String heading(NotificationType type) {
        return switch (type) {
            case EDU_SUBMITTED -> "Hồ sơ chờ bạn duyệt";
            case EDU_STEP_APPROVED -> "Hồ sơ chuyển bạn duyệt tiếp";
            case EDU_RELEASED -> "Hồ sơ đã ban hành";
            case EDU_RETURNED -> "Hồ sơ bị trả lại";
            case EDU_DEADLINE_EXTENDED -> "Hồ sơ đã được mở hạn";
            case EDU_DEADLINE_NEAR -> "Hồ sơ gần đến hạn";
            default -> "Thông báo kế hoạch giáo dục";
        };
    }

    // Trạng thái sau thao tác; nhắc hạn đã nêu trạng thái trong nội dung
    private static String status(NotificationType type) {
        return switch (type) {
            case EDU_SUBMITTED, EDU_STEP_APPROVED -> "Chờ duyệt";
            case EDU_RELEASED -> "Đã ban hành";
            case EDU_RETURNED -> "Nháp – cần chỉnh sửa";
            case EDU_DEADLINE_EXTENDED -> "Đã mở hạn";
            default -> null;
        };
    }

    // Tiêu đề thông báo dạng "<sự kiện>: <tên hồ sơ>"
    private static String documentName(String title) {
        int i = title.indexOf(": ");
        return i < 0 ? title : title.substring(i + 2);
    }

    static String url(String webBaseUrl, String link) {
        if (!hasText(webBaseUrl) || !hasText(link)) return null;
        String base = webBaseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + (link.startsWith("/") ? link : "/" + link);
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }
}
