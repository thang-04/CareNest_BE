package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Cấu hình kế hoạch giáo dục của trường (1 dòng, EDU-11): chuỗi người ký và dòng hoạt động tuần dạng JSON. */
@Getter
@Entity
@Table(name = "edu_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduSettings {
    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private String signers;

    @Column(name = "week_rows", nullable = false)
    private String weekRows;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void update(String signers, String weekRows, Long userId, Instant now) {
        this.signers = signers;
        this.weekRows = weekRows;
        this.updatedBy = userId;
        this.updatedAt = now;
    }
}
