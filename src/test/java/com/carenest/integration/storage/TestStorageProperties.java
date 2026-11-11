package com.carenest.integration.storage;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.util.unit.DataSize;

/** Cấu hình kho cho test, giống giá trị mặc định trong application.yml; {@code root} đặt trước mọi thư mục (smoke). */
final class TestStorageProperties {
    private TestStorageProperties() {}

    static StorageProperties of(String bucket, String root) {
        Map<String, StorageProperties.Category> c = new LinkedHashMap<>();
        Set<String> images = Set.of("jpg", "png", "webp");
        Set<String> plans = Set.of("pdf", "docx");
        c.put("user-avatar", cat(root + "avatars/users/", "user", 2, images, null, "accounts/photos/"));
        c.put("child-avatar", cat(root + "avatars/children/", "child", 2, images, null, "children/photos/"));
        c.put("pickup", cat(root + "pickups/", "pickup", 3, images, null, null));
        c.put("plan-year-goal", cat(root + "plans/year-goals/", "yeargoal", 10, plans, null, null));
        c.put("plan-theme", cat(root + "plans/themes/", "theme", 10, plans, null, null));
        c.put("plan-lesson", cat(root + "plans/lessons/", "lesson", 10, plans, null, null));
        c.put("meal", cat(root + "meals/", "meal", 5, images, null, "education/learning-resources/"));
        c.put("food-sample", cat(root + "food-samples/", "foodsample", 5, images, null, null));
        c.put("import", cat(root + "imports/", "import", 5, Set.of("xlsx"), null, null));
        c.put(
                "learning-resource",
                cat(
                        root + "hoclieu/",
                        "hoclieu",
                        20,
                        Set.of("jpg", "jpeg", "png", "gif", "webp", "mp4", "webm", "pdf", "pptx", "docx", "xlsx", "txt",
                                "csv"),
                        Map.of("image", DataSize.ofMegabytes(5), "video", DataSize.ofMegabytes(100)),
                        "education/learning-resources/"));
        c.put("user-signature", cat(root + "signatures/users/", "sig", 2, Set.of("jpg", "png"), null, "accounts/signatures/"));
        c.put(
                "inspection-signature",
                cat(root + "signatures/inspections/", "inspsig", 2, Set.of("jpg", "png"), null, "meals/inspection-signatures/"));
        return new StorageProperties(bucket, Duration.ofMinutes(15), "tmp/", c);
    }

    private static StorageProperties.Category cat(
            String folder, String code, int mb, Set<String> types, Map<String, DataSize> byKind, String legacy) {
        return new StorageProperties.Category(folder, code, DataSize.ofMegabytes(mb), types, byKind, legacy);
    }
}
