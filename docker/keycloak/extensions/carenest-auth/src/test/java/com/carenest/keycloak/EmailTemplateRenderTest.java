package com.carenest.keycloak;

import static org.junit.jupiter.api.Assertions.*;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class EmailTemplateRenderTest {
    private static final String THEME = "/theme/carenest-auth/email";

    @Test
    void setupHtmlShowsAccountDetailsLogoAndButton() throws Exception {
        String html = render("html/carenest-setup.ftl", setupAttributes());
        assertTrue(html.contains("Chào <strong>Nguyễn Thị Lan</strong>"));
        assertTrue(html.contains("Trường Mầm Non Thượng Hồng"));
        assertTrue(html.contains("<strong>Giáo viên</strong>"));
        assertTrue(html.contains("0369666456"));
        assertTrue(html.contains("href=\"https://web.example.test/forgot-password\""));
        assertTrue(html.contains("src=\"https://web.example.test/email/logo-mark.png\""));
        assertTrue(html.contains("Thiết lập mật khẩu"));
        assertTrue(html.contains("Email tự động, vui lòng không trả lời"));
        assertEmailSafeHtml(html);
    }

    @Test
    void setupHtmlEscapesNameAndHandlesMissingOptionalFields() throws Exception {
        Map<String, Object> attributes = setupAttributes();
        attributes.put("fullName", "<script>alert(1)</script>");
        attributes.remove("roleLabel");
        attributes.remove("logoUrl");
        String html = render("html/carenest-setup.ftl", attributes);
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("đã tạo cho bạn tài khoản trên CareNest"));
        assertFalse(html.contains("<img"));

        attributes.put("fullName", "");
        assertTrue(render("html/carenest-setup.ftl", attributes).contains("Xin chào,"));
    }

    @Test
    void recoveryHtmlShowsCodeAndValidity() throws Exception {
        String html = render("html/carenest-recovery-code.ftl", recoveryAttributes());
        assertTrue(html.contains("Mã xác thực của bạn"));
        assertTrue(html.contains(">321658</td>"));
        assertTrue(html.contains("<strong>5 phút</strong>"));
        assertTrue(html.contains("kể cả người tự xưng là nhân viên nhà trường"));
        assertEmailSafeHtml(html);
    }

    @Test
    void textVersionsCarrySameContentWithDiacritics() throws Exception {
        String setup = render("text/carenest-setup.ftl", setupAttributes());
        assertTrue(setup.contains("Chào Nguyễn Thị Lan,"));
        assertTrue(setup.contains("tài khoản Giáo viên trên CareNest"));
        assertTrue(setup.contains("Tên đăng nhập: 0369666456"));
        assertTrue(setup.contains("https://web.example.test/forgot-password"));

        String recovery = render("text/carenest-recovery-code.ftl", recoveryAttributes());
        assertTrue(recovery.contains("321658"));
        assertTrue(recovery.contains("5 phút"));
    }

    @Test
    void subjectsHaveDiacriticsInEveryBundle() throws Exception {
        for (String bundle : new String[] {"messages_en.properties", "messages_vi.properties"}) {
            Properties messages = new Properties();
            try (InputStream in = getClass().getResourceAsStream(THEME + "/messages/" + bundle)) {
                messages.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
            assertEquals("CareNest – Thiết lập tài khoản", messages.getProperty("carenestSetupSubject"));
            assertEquals("CareNest – Mã xác thực đặt lại mật khẩu", messages.getProperty("carenestRecoverySubject"));
        }
    }

    private static void assertEmailSafeHtml(String html) {
        assertFalse(html.contains("display:flex"));
        assertFalse(html.contains("display:grid"));
        assertFalse(html.contains("data:image"));
        assertFalse(html.contains("<svg"));
        assertFalse(html.contains("@import"));
    }

    private static Map<String, Object> setupAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("fullName", "Nguyễn Thị Lan");
        attributes.put("username", "0369666456");
        attributes.put("schoolName", AuthOperations.DEFAULT_SCHOOL_NAME);
        attributes.put("roleLabel", "Giáo viên");
        attributes.put("link", "https://web.example.test/forgot-password");
        attributes.put("logoUrl", "https://web.example.test/email/logo-mark.png");
        return attributes;
    }

    private static Map<String, Object> recoveryAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("fullName", "Nguyễn Thị Lan");
        attributes.put("username", "0369666456");
        attributes.put("schoolName", AuthOperations.DEFAULT_SCHOOL_NAME);
        attributes.put("logoUrl", "https://web.example.test/email/logo-mark.png");
        attributes.put("code", "321658");
        attributes.put("minutes", 5);
        return attributes;
    }

    private String render(String template, Map<String, Object> attributes) throws Exception {
        Configuration config = new Configuration(Configuration.VERSION_2_3_32);
        config.setTemplateLoader(new ClassTemplateLoader(getClass(), THEME));
        config.setDefaultEncoding("UTF-8");
        StringWriter out = new StringWriter();
        config.getTemplate(template).process(attributes, out);
        return out.toString();
    }
}
