package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một lần hồ sơ chủ đề của lớp được ban hành; giữ nội dung + chuỗi ký để xem lại bản cũ (spec 002 FR-018). */
@Getter
@Entity
@Table(name = "edu_class_doc_releases")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduClassDocRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_doc_id", nullable = false, updatable = false)
    private Long classDocId;

    @Column(name = "dossier_version", nullable = false, updatable = false)
    private Long dossierVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "print_snapshot", columnDefinition = "jsonb", nullable = false, updatable = false)
    private String printSnapshot;

    @Column(name = "approved_by", nullable = false, updatable = false)
    private Long approvedBy;

    @Column(name = "released_at", nullable = false, updatable = false)
    private Instant releasedAt;

    public static EduClassDocRelease create(
            Long classDocId, Long dossierVersion, String printSnapshot, Long approvedBy, Instant releasedAt) {
        EduClassDocRelease release = new EduClassDocRelease();
        release.classDocId = classDocId;
        release.dossierVersion = dossierVersion;
        release.printSnapshot = printSnapshot;
        release.approvedBy = approvedBy;
        release.releasedAt = releasedAt;
        return release;
    }
}
