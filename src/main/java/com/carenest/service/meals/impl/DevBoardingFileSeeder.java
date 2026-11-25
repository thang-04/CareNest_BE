package com.carenest.service.meals.impl;

import com.carenest.entity.meals.InspectionEvent;
import com.carenest.entity.meals.InspectionSheet;
import com.carenest.entity.meals.InspectionSignature;
import com.carenest.entity.meals.MealPhoto;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.meals.InspectionSheetRepository;
import com.carenest.repository.meals.MealPhotoRepository;
import com.carenest.utils.enums.FileCategory;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.MealSession;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Dữ liệu dev có tệp: chữ ký PHT, ảnh bữa ăn, biên bản kiểm thực đã ký. Flyway không nạp được vì tệp nằm ở kho S3, nên
 * nạp sau khi seed SQL xong. Chạy lại không nhân đôi; kho tệp chưa cấu hình thì bỏ qua, không làm hỏng khởi động.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
class DevBoardingFileSeeder implements ApplicationRunner {
    private static final String LOG_PREFIX = "[DevBoardingFileSeeder]";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    // 5 ngày học gần nhất đã có sổ báo ăn; các ngày cũ hơn để dành cho yêu cầu mở ghi bù (R__dev_14)
    private static final int SEEDED_DAYS = 5;

    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    private final MealPhotoRepository photoRepository;
    private final InspectionSheetRepository sheetRepository;
    private final InspectionSkeleton skeleton;

    private record Staff(Long campusId, Long vpId, String vpName, Long kitchenId) {}

    @Override
    public void run(ApplicationArguments args) {
        String logPrefix = LOG_PREFIX + "|run";
        log.info("{}|START", logPrefix);
        try {
            int photos = 0;
            int sheets = 0;
            for (Staff staff : staff()) {
                if (staff.vpId() == null) {
                    continue;
                }
                byte[] signature = ensureSignature(staff);
                for (var day : recentDays(staff.campusId())) {
                    for (MealSession session : day.getValue()) {
                        photos += seedMealPhoto(staff, day.getKey(), session) ? 1 : 0;
                    }
                    for (InspectionTab tab : InspectionTab.values()) {
                        sheets += seedSheet(staff, day.getKey(), tab, signature) ? 1 : 0;
                    }
                }
            }
            log.info("{}|photos={}|sheets={}|END", logPrefix, photos, sheets);
        } catch (RuntimeException ex) {
            // Dev không có S3 là bình thường; không chặn khởi động
            log.warn("{}|SKIPPED|type={}", logPrefix, ex.getClass().getSimpleName());
        }
    }

    private List<Staff> staff() {
        return jdbc.query(
                """
                SELECT c.id,
                       (SELECT a.user_id FROM staff_assignments a WHERE a.campus_id = c.id
                          AND a.staff_role = 'VICE_PRINCIPAL' AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE)
                        ORDER BY a.id LIMIT 1) AS vp_id,
                       (SELECT u.full_name FROM staff_assignments a JOIN user_accounts u ON u.id = a.user_id
                        WHERE a.campus_id = c.id AND a.staff_role = 'VICE_PRINCIPAL'
                          AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE) ORDER BY a.id LIMIT 1) AS vp_name,
                       (SELECT a.user_id FROM staff_assignments a WHERE a.campus_id = c.id
                          AND a.staff_role = 'KITCHEN_STAFF' AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE)
                        ORDER BY a.id LIMIT 1) AS kitchen_id
                FROM campuses c ORDER BY c.id
                """,
                (rs, i) -> new Staff(rs.getLong("id"), (Long) rs.getObject("vp_id"), rs.getString("vp_name"), (Long)
                        rs.getObject("kitchen_id")));
    }

    /** Ngày học đã qua có sổ báo ăn xác nhận (mới nhất trước) kèm các bữa có suất. */
    private List<Map.Entry<LocalDate, List<MealSession>>> recentDays(Long campusId) {
        Map<LocalDate, List<MealSession>> days = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT date, session FROM meal_counts
                WHERE campus_id = ? AND status = 'CONFIRMED' AND date < CURRENT_DATE AND date >= CURRENT_DATE - 30
                ORDER BY date DESC, session
                """,
                rs -> {
                    days.computeIfAbsent(rs.getDate("date").toLocalDate(), d -> new ArrayList<>())
                            .add(MealSession.valueOf(rs.getString("session")));
                },
                campusId);
        return days.entrySet().stream().limit(SEEDED_DAYS).toList();
    }

    // ---------- chữ ký ----------

    /** Chữ ký mặc định của PHT; chưa có thì vẽ một chữ ký mẫu rồi lưu. Trả nội dung ảnh để ký biên bản. */
    private byte[] ensureSignature(Staff staff) {
        // Bảng chữ ký thuộc module tài khoản: đọc/ghi bằng SQL để không phụ thuộc repository của module khác
        List<String> keys = jdbc.queryForList(
                "SELECT storage_key FROM user_signatures WHERE user_id = ? ORDER BY is_default DESC, id LIMIT 1",
                String.class,
                staff.vpId());
        if (!keys.isEmpty()) {
            return storage.loadBytes(FileCategory.USER_SIGNATURE, keys.get(0));
        }
        byte[] content = drawSignature(staff.vpName());
        FileStorage.StoredFile stored = storage.store(FileCategory.USER_SIGNATURE, staff.vpId(), content);
        jdbc.update(
                "INSERT INTO user_signatures (user_id, storage_key, content_type, size_bytes, is_default) "
                        + "VALUES (?, ?, ?, ?, TRUE)",
                staff.vpId(),
                stored.key(),
                stored.contentType(),
                content.length);
        return content;
    }

    private static byte[] drawSignature(String name) {
        BufferedImage image = new BufferedImage(360, 140, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(20, 40, 120));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Nét ký: đường cong và gạch chân, lệch theo tên để mỗi người một chữ ký
        int seed = Math.abs(name == null ? 0 : name.hashCode());
        int lift = 20 + seed % 25;
        g.draw(new CubicCurve2D.Double(20, 95, 80, lift, 130, 130, 190, 60));
        g.draw(new CubicCurve2D.Double(190, 60, 230, 10 + seed % 20, 280, 120, 340, 70));
        g.drawLine(40, 118, 330, 104);
        g.setFont(new Font(Font.SERIF, Font.ITALIC, 22));
        String last = name == null || name.isBlank() ? "" : name.substring(name.lastIndexOf(' ') + 1);
        g.drawString(last, 30, 40);
        g.dispose();
        return encode(image, "png");
    }

    // ---------- ảnh bữa ăn ----------

    private boolean seedMealPhoto(Staff staff, LocalDate day, MealSession session) {
        if (photoRepository
                .findByCampusIdAndPhotoDateAndSession(staff.campusId(), day, session)
                .isPresent()) {
            return false;
        }
        byte[] content = mealImage(session, day);
        FileStorage.StoredFile stored = storage.store(FileCategory.MEAL, staff.campusId(), content);
        LocalTime at =
                switch (session) {
                    case MORNING -> LocalTime.of(9, 20);
                    case LUNCH -> LocalTime.of(10, 50);
                    case AFTERNOON -> LocalTime.of(14, 35);
                };
        MealPhoto photo = MealPhoto.of(staff.campusId(), day, session);
        photo.replaceFile(
                stored.key(),
                stored.contentType(),
                stored.size(),
                staff.kitchenId() != null ? staff.kitchenId() : staff.vpId(),
                day.atTime(at).atZone(ZONE).toInstant());
        photoRepository.saveAndFlush(photo);
        return true;
    }

    /** Ảnh mẫu: lấy tệp {@code dev-files/meals/<bữa>-<n>.jpg} nếu có, không thì vẽ khay ăn minh họa. */
    private static byte[] mealImage(MealSession session, LocalDate day) {
        int variant = day.getDayOfYear() % 3 + 1;
        ClassPathResource resource =
                new ClassPathResource("dev-files/meals/" + session.name().toLowerCase() + "-" + variant + ".jpg");
        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                return in.readAllBytes();
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot read dev meal image", ex);
            }
        }
        return drawTray(session, variant);
    }

    private static byte[] drawTray(MealSession session, int variant) {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(236, 230, 218));
        g.fillRect(0, 0, 800, 600);
        g.setColor(new Color(176, 182, 190));
        g.fillRoundRect(60, 90, 680, 420, 40, 40);
        g.setColor(new Color(205, 210, 216));
        g.fillRoundRect(80, 110, 640, 380, 30, 30);
        Color[] foods =
                switch (session) {
                    case LUNCH ->
                        new Color[] {
                            new Color(250, 248, 240),
                            new Color(150, 78, 40),
                            new Color(96, 150, 70),
                            new Color(214, 170, 60)
                        };
                    case MORNING ->
                        new Color[] {
                            new Color(255, 250, 235),
                            new Color(226, 190, 120),
                            new Color(240, 236, 228),
                            new Color(220, 120, 90)
                        };
                    case AFTERNOON ->
                        new Color[] {
                            new Color(244, 214, 150),
                            new Color(232, 120, 60),
                            new Color(120, 170, 80),
                            new Color(250, 240, 200)
                        };
                };
        int[][] cells = {{110, 140, 250, 330}, {380, 140, 320, 150}, {380, 310, 150, 150}, {550, 310, 150, 150}};
        for (int i = 0; i < cells.length; i++) {
            g.setColor(foods[(i + variant) % foods.length]);
            g.fillRoundRect(cells[i][0], cells[i][1], cells[i][2], cells[i][3], 60, 60);
        }
        g.setColor(new Color(60, 60, 60));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        String label =
                switch (session) {
                    case MORNING -> "Bữa phụ sáng";
                    case LUNCH -> "Bữa trưa";
                    case AFTERNOON -> "Bữa chiều";
                };
        g.drawString(label + " (ảnh mẫu dev)", 60, 560);
        g.dispose();
        return encode(image, "jpg");
    }

    // ---------- biên bản kiểm thực ----------

    /** Biên bản đã ký của PHT: ô hệ thống từ nguồn, mọi dòng cảm quan Đạt, ảnh chữ ký chép sang kho riêng của biên bản. */
    private boolean seedSheet(Staff staff, LocalDate day, InspectionTab tab, byte[] signature) {
        if (sheetRepository
                .findByCampusIdAndSheetDateAndTab(staff.campusId(), day, tab)
                .isPresent()) {
            return false;
        }
        List<Map<String, Object>> source = skeleton.rows(tab, staff.campusId(), day);
        if (source.isEmpty()) {
            return false;
        }
        List<Map<String, Object>> saved = new ArrayList<>();
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Map<String, Object> row : source) {
            Map<String, Object> entered = new LinkedHashMap<>();
            entered.put(InspectionColumns.KEY, row.get(InspectionColumns.KEY));
            if (tab.requiresSensory()) {
                entered.put(InspectionColumns.SENSORY, InspectionColumns.PASS);
            }
            saved.add(entered);
            Map<String, Object> full = new LinkedHashMap<>(row);
            full.putAll(entered);
            snapshot.add(full);
        }
        java.time.Instant now = day.atTime(11, 30).atZone(ZONE).toInstant();
        InspectionSheet sheet = InspectionSheet.draft(staff.campusId(), day, tab, now);
        sheet.saveDraft(Map.of(), saved, staff.vpId(), now);
        String imageKey =
                FileStorage.fileName(storage.store(FileCategory.INSPECTION_SIGNATURE, staff.campusId(), signature)
                        .key());
        java.time.Instant signedAt = day.atTime(11, 45).atZone(ZONE).toInstant();
        sheet.sign(
                new InspectionSignature(
                        staff.vpId(),
                        staff.vpName(),
                        signedAt,
                        sheet.getDocVersion(),
                        imageKey,
                        "image/png",
                        signature.length),
                new InspectionEvent(InspectionEvent.SIGNED, staff.vpId(), staff.vpName(), signedAt, null, imageKey),
                snapshot,
                signedAt);
        sheetRepository.saveAndFlush(sheet);
        return true;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot encode dev image", ex);
        }
    }
}
