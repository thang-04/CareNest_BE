package com.carenest.integration.storage;

import com.carenest.utils.enums.FileCategory;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Cấu hình kho tệp: thư mục, giới hạn, định dạng từng loại (FR-002) — đổi qua yml/biến môi trường, không sửa code. */
@ConfigurationProperties("carenest.storage")
public record StorageProperties(
        String bucket, Duration presignTtl, String tmpPrefix, Map<String, Category> categories) {

    public record Category(
            String folder,
            String code,
            DataSize maxSize,
            Set<String> types,
            Map<String, DataSize> maxSizeByKind,
            String legacyPrefix) {}

    public StorageProperties {
        bucket = bucket == null ? "" : bucket;
        presignTtl = presignTtl == null ? Duration.ofMinutes(15) : presignTtl;
        tmpPrefix = tmpPrefix == null ? "tmp/" : tmpPrefix;
        categories = categories == null ? Map.of() : categories;
        if (!tmpPrefix.matches("[a-z0-9-]+/")) throw new IllegalStateException("Invalid carenest.storage.tmp-prefix");
    }

    /** Cấu hình theo enum, kiểm đủ và hợp lệ ngay lúc khởi động để sai cấu hình không lọt tới lúc tải tệp. */
    public Map<FileCategory, Category> resolved() {
        Map<FileCategory, Category> result = new EnumMap<>(FileCategory.class);
        for (FileCategory category : FileCategory.values()) {
            Category c = categories.get(category.configKey());
            String where = "carenest.storage.categories." + category.configKey();
            if (c == null) throw new IllegalStateException("Missing " + where);
            if (c.folder() == null || !c.folder().matches("([a-z0-9-]+/)+"))
                throw new IllegalStateException("Invalid " + where + ".folder");
            if (c.code() == null || !c.code().matches("[a-z]+"))
                throw new IllegalStateException("Invalid " + where + ".code");
            if (c.maxSize() == null || c.maxSize().toBytes() <= 0)
                throw new IllegalStateException("Invalid " + where + ".max-size");
            if (c.types() == null || c.types().isEmpty() || !c.types().stream().allMatch(FileTypes::supported))
                throw new IllegalStateException("Invalid " + where + ".types");
            if (c.legacyPrefix() != null
                    && !c.legacyPrefix().isBlank()
                    && !c.legacyPrefix().matches("[a-zA-Z0-9/_-]*"))
                throw new IllegalStateException("Invalid " + where + ".legacy-prefix");
            result.put(category, c);
        }
        return result;
    }
}
