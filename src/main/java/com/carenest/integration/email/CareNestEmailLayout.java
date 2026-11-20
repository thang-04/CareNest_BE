package com.carenest.integration.email;

import java.util.ArrayList;
import java.util.List;

/**
 * Khung chung cho mọi email BE gửi (G94-386): cùng thiết kế với mail Keycloak
 * {@code docker/keycloak/extensions/carenest-auth/.../email/html/carenest-layout.ftl} (table + CSS inline, màu, header,
 * nút, footer). Đổi thiết kế ở một bên ⇒ đổi bên kia. Mọi giá trị đưa vào đều được escape HTML.
 */
public final class CareNestEmailLayout {

    static final String PAGE_BG = "#eef4f9";
    static final String ACCENT = "#4fb0f5";
    static final String HEADING = "#0e3f63";
    static final String TEXT = "#1f2d3d";
    static final String MUTED = "#6b7c8f";
    static final String BOX_BG = "#f3f8fd";
    static final String LINK = "#2b8fd6";
    static final String FONT = "Arial,Helvetica,sans-serif";
    static final String FOOTER = "© CareNest · Email tự động, vui lòng không trả lời";
    /** Logo Web phục vụ cho email (FE {@code public/email/logo-mark.png}), như {@code logoUrl} của mail Keycloak. */
    public static final String LOGO_PATH = "/email/logo-mark.png";

    private final String heading;
    private String preheader = "";
    private String logoUrl;
    private String greetingName;
    private final List<String> paragraphs = new ArrayList<>();
    private final List<String[]> facts = new ArrayList<>();
    private String actionLabel;
    private String actionUrl;
    private String note;

    private CareNestEmailLayout(String heading) {
        this.heading = heading;
    }

    public static CareNestEmailLayout titled(String heading) {
        return new CareNestEmailLayout(heading);
    }

    /** Logo cạnh chữ CareNest ở header; trống ⇒ chỉ chữ (giống mail Keycloak khi thiếu origin). */
    public CareNestEmailLayout logo(String url) {
        this.logoUrl = url;
        return this;
    }

    /** Dòng xem trước hiện ở hộp thư (ẩn trong thân email). */
    public CareNestEmailLayout preheader(String text) {
        this.preheader = text == null ? "" : text;
        return this;
    }

    /** "Chào &lt;tên&gt;,"; không có tên ⇒ "Xin chào,". */
    public CareNestEmailLayout greeting(String fullName) {
        this.greetingName = fullName == null ? "" : fullName;
        return this;
    }

    public CareNestEmailLayout paragraph(String text) {
        if (EmailMessage.hasText(text)) paragraphs.add(text);
        return this;
    }

    /** Một dòng trong hộp thông tin; giá trị trống thì bỏ dòng. */
    public CareNestEmailLayout fact(String label, String value) {
        if (EmailMessage.hasText(value)) facts.add(new String[] {label, value});
        return this;
    }

    /** Nút chính + đường dẫn dự phòng; url trống thì không có nút. */
    public CareNestEmailLayout action(String label, String url) {
        this.actionLabel = label;
        this.actionUrl = url;
        return this;
    }

    /** Dòng chú thích nhỏ cuối thân email. */
    public CareNestEmailLayout note(String text) {
        this.note = text;
        return this;
    }

    public String html() {
        StringBuilder b = new StringBuilder(4096);
        b.append("<!DOCTYPE html>\n<html lang=\"vi\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>CareNest</title>\n</head>\n")
                .append("<body style=\"margin:0;padding:0;background-color:").append(PAGE_BG).append(";\">\n")
                .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">")
                .append(escape(preheader)).append("</div>\n")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                .append(" style=\"background-color:").append(PAGE_BG).append(";\">\n<tr>\n")
                .append("<td align=\"center\" style=\"padding:32px 12px;\">\n")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                .append(" style=\"max-width:560px;background-color:#ffffff;border-radius:10px;border-top:4px solid ")
                .append(ACCENT).append(";\">\n")
                .append("<tr><td style=\"padding:18px 32px;border-bottom:1px solid #e6eef5;\">\n")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>\n");
        if (EmailMessage.hasText(logoUrl)) {
            b.append("<td style=\"padding-right:10px;vertical-align:middle;\"><img src=\"").append(escape(logoUrl))
                    .append("\" width=\"36\" height=\"36\" alt=\"\" style=\"display:block;border:0;outline:none;")
                    .append("text-decoration:none;\"></td>\n");
        }
        b.append("<td style=\"vertical-align:middle;font-family:").append(FONT)
                .append(";font-size:20px;font-weight:bold;color:").append(HEADING).append(";\">CareNest</td>\n")
                .append("</tr></table>\n</td></tr>\n")
                .append("<tr><td style=\"padding:28px 32px 32px;font-family:").append(FONT)
                .append(";font-size:15px;line-height:1.6;color:").append(TEXT).append(";\">\n");
        appendBody(b);
        b.append("</td></tr>\n</table>\n")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                .append(" style=\"max-width:560px;\">\n<tr><td align=\"center\" style=\"padding:16px 12px;font-family:")
                .append(FONT).append(";font-size:12px;line-height:1.5;color:#8a99a8;\">").append(escape(FOOTER))
                .append("</td></tr>\n</table>\n</td>\n</tr>\n</table>\n</body>\n</html>\n");
        return b.toString();
    }

    /** Bản text cho hộp thư không đọc HTML; cùng nội dung với {@link #html()}. */
    public String text() {
        StringBuilder b = new StringBuilder(1024);
        b.append(heading).append("\n\n");
        if (greetingName != null) b.append(greetingText()).append("\n\n");
        for (String p : paragraphs) b.append(p).append("\n\n");
        for (String[] f : facts) b.append(f[0]).append(": ").append(f[1]).append('\n');
        if (!facts.isEmpty()) b.append('\n');
        if (EmailMessage.hasText(actionUrl)) b.append(actionLabel).append(": ").append(actionUrl).append("\n\n");
        if (EmailMessage.hasText(note)) b.append(note).append("\n\n");
        b.append("--\n").append(FOOTER).append('\n');
        return b.toString();
    }

    private void appendBody(StringBuilder b) {
        b.append("<h1 style=\"margin:0 0 16px;font-size:22px;line-height:1.3;color:").append(HEADING).append(";\">")
                .append(escape(heading)).append("</h1>\n");
        // Như mail Keycloak: lời chào và đoạn đầu chung một <p>, ngắt bằng <br>
        int blocks = Math.max(paragraphs.size(), greetingName != null ? 1 : 0);
        for (int i = 0; i < blocks; i++) {
            b.append("<p style=\"margin:0 0 16px;\">");
            if (i == 0 && greetingName != null) {
                if (greetingName.isBlank()) b.append("Xin chào,");
                else b.append("Chào <strong>").append(escape(greetingName)).append("</strong>,");
                if (!paragraphs.isEmpty()) b.append("<br>");
            }
            if (i < paragraphs.size()) b.append(escape(paragraphs.get(i)));
            b.append("</p>\n");
        }
        if (!facts.isEmpty()) {
            b.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                    .append(" style=\"margin:0 0 20px;\"><tr><td style=\"background-color:").append(BOX_BG)
                    .append(";border-radius:8px;padding:12px 16px;font-size:14px;color:").append(TEXT).append(";\">\n");
            for (int i = 0; i < facts.size(); i++) {
                String[] f = facts.get(i);
                if (i > 0) b.append("<br>\n");
                b.append(escape(f[0])).append(": <strong style=\"color:").append(HEADING).append(";\">")
                        .append(escape(f[1])).append("</strong>");
            }
            b.append("\n</td></tr></table>\n");
        }
        if (EmailMessage.hasText(actionUrl)) {
            String url = escape(actionUrl);
            b.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                    .append(" style=\"margin:0 0 24px;\"><tr><td style=\"border-radius:8px;background-color:")
                    .append(ACCENT).append(";\">")
                    .append("<a href=\"").append(url).append("\" target=\"_blank\" style=\"display:inline-block;")
                    .append("padding:12px 28px;font-family:").append(FONT)
                    .append(";font-size:15px;font-weight:bold;color:").append(HEADING)
                    .append(";text-decoration:none;border-radius:8px;\">").append(escape(actionLabel))
                    .append("</a></td></tr></table>\n")
                    .append("<p style=\"margin:0 0 12px;font-size:13px;color:").append(MUTED)
                    .append(";\">Nếu nút không hoạt động, sao chép đường dẫn sau vào trình duyệt:<br>")
                    .append("<a href=\"").append(url).append("\" target=\"_blank\" style=\"color:").append(LINK)
                    .append(";word-break:break-all;\">").append(url).append("</a></p>\n");
        }
        if (EmailMessage.hasText(note)) {
            b.append("<p style=\"margin:0;font-size:13px;color:").append(MUTED).append(";\">").append(escape(note))
                    .append("</p>\n");
        }
    }

    private String greetingText() {
        return greetingName.isBlank() ? "Xin chào," : "Chào " + greetingName + ",";
    }

    static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
