package com.carenest.entity.meals;

import com.carenest.utils.enums.MealSession;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Khung giờ đăng ảnh bữa ăn (NUT-16), lưu ở dòng cấu hình chung của trường ({@code edu_settings}, id = 1) thay cho
 * bảng riêng chỉ có 3 dòng. Entity chỉ map cột của bán trú; cột kế hoạch giáo dục do {@code EduSettings} quản lý.
 */
@Getter
@Entity
@Table(name = "edu_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealPhotoSettings {
    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Convert(converter = JsonListConverter.PhotoWindows.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meal_photo_windows", nullable = false, columnDefinition = "jsonb")
    private List<MealPhotoWindow> windows = List.of();

    public Optional<MealPhotoWindow> window(MealSession session) {
        return windows.stream().filter(w -> w.session() == session).findFirst();
    }

    /** Thay khung giờ các bữa có trong yêu cầu, giữ nguyên bữa không gửi lên. */
    public void replaceWindows(List<MealPhotoWindow> changed) {
        List<MealPhotoWindow> merged = new ArrayList<>(changed);
        windows.stream()
                .filter(old -> changed.stream().noneMatch(w -> w.session() == old.session()))
                .forEach(merged::add);
        merged.sort(Comparator.comparing(MealPhotoWindow::session));
        this.windows = List.copyOf(merged);
    }
}
