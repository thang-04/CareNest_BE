package com.carenest.integration.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Khung email chung: escape dữ liệu, cùng thiết kế với mail Keycloak (carenest-layout.ftl). */
class CareNestEmailLayoutTest {

    private static final Path KEYCLOAK_LAYOUT = Path.of(
            "docker/keycloak/extensions/carenest-auth/src/main/resources/theme/carenest-auth/email/html/carenest-layout.ftl");

    @Test
    void escapesUserContentAndRendersButtonWithFallbackLink() {
        String html = CareNestEmailLayout.titled("Hồ sơ bị trả lại")
                .greeting("Cô <A>")
                .paragraph("Lý do: \"thiếu\" & sai")
                .fact("Hồ sơ", "Chủ đề <script>")
                .fact("Phạm vi", null)
                .action("Mở hồ sơ", "https://web.test/a?x=1&y=2")
                .html();

        assertThat(html)
                .contains("Chào <strong>Cô &lt;A&gt;</strong>,<br>Lý do: &quot;thiếu&quot; &amp; sai")
                .contains("Chủ đề &lt;script&gt;")
                .doesNotContain("Phạm vi")
                .contains("href=\"https://web.test/a?x=1&amp;y=2\"")
                .contains("Nếu nút không hoạt động");
    }

    @Test
    void logoRendersBesideBrandNameLikeKeycloakMail() {
        String withLogo = CareNestEmailLayout.titled("T").logo("https://web.test/email/logo-mark.png").html();
        String withoutLogo = CareNestEmailLayout.titled("T").html();

        assertThat(withLogo).contains("<img src=\"https://web.test/email/logo-mark.png\" width=\"36\" height=\"36\"");
        assertThat(withoutLogo).doesNotContain("<img").contains(">CareNest</td>");
    }

    @Test
    void withoutUrlHasNoButtonAndTextVersionMatches() {
        CareNestEmailLayout layout =
                CareNestEmailLayout.titled("Tiêu đề").fact("Hồ sơ", "X").action("Mở hồ sơ", null);

        assertThat(layout.html()).doesNotContain("Mở hồ sơ");
        assertThat(layout.text()).contains("Tiêu đề").contains("Hồ sơ: X").contains(CareNestEmailLayout.FOOTER);
    }

    @Test
    void sharesDesignTokensWithKeycloakEmailLayout() throws Exception {
        String ftl = Files.readString(KEYCLOAK_LAYOUT);
        for (String token : new String[] {
            CareNestEmailLayout.PAGE_BG,
            CareNestEmailLayout.ACCENT,
            CareNestEmailLayout.HEADING,
            CareNestEmailLayout.TEXT,
            CareNestEmailLayout.FONT,
            CareNestEmailLayout.FOOTER
        }) {
            assertThat(ftl).as("carenest-layout.ftl chứa %s", token).contains(token);
        }
    }
}
