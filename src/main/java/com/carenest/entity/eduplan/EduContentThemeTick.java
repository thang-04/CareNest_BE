package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Nội dung giáo dục được tích vào chủ đề thực hiện. */
@Getter
@Entity
@Table(name = "edu_content_theme_ticks")
@IdClass(EduContentThemeTick.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduContentThemeTick {

    @Id
    @Column(name = "content_id")
    private Long contentId;

    @Id
    @Column(name = "theme_id")
    private Long themeId;

    public static EduContentThemeTick of(Long contentId, Long themeId) {
        EduContentThemeTick tick = new EduContentThemeTick();
        tick.contentId = contentId;
        tick.themeId = themeId;
        return tick;
    }

    public Key key() {
        return new Key(contentId, themeId);
    }

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long contentId;
        private Long themeId;
    }
}
