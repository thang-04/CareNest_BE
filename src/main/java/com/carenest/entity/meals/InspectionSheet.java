package com.carenest.entity.meals;

import com.carenest.utils.enums.InspectionStatus;
import com.carenest.utils.enums.InspectionTab;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Biên bản kiểm thực của một điểm trường–ngày–mẫu (INS-01). Nháp sửa được; ký chép ảnh chữ ký và chụp dòng rồi khóa
 * (INS-03); sửa lại đưa về nháp, chữ ký cũ nằm trong lịch sử (INS-04).
 */
@Getter
@Entity
@Table(name = "inspection_sheets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionSheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "sheet_date", nullable = false)
    private LocalDate sheetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InspectionTab tab;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InspectionStatus status = InspectionStatus.DRAFT;

    // Tăng mỗi lần nội dung đổi hoặc sửa lại; chữ ký ghi phiên bản được ký
    @Column(name = "version", nullable = false)
    private int docVersion;

    @Convert(converter = InspectionJson.Header.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> header = Map.of();

    @Convert(converter = InspectionJson.Rows.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rows", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> sheetRows = List.of();

    @Convert(converter = InspectionJson.Signature.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private InspectionSignature signature;

    @Convert(converter = InspectionJson.Rows.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> snapshot;

    @Convert(converter = InspectionJson.Events.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<InspectionEvent> history = List.of();

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static InspectionSheet draft(Long campusId, LocalDate sheetDate, InspectionTab tab, Instant at) {
        InspectionSheet sheet = new InspectionSheet();
        sheet.campusId = campusId;
        sheet.sheetDate = sheetDate;
        sheet.tab = tab;
        sheet.updatedAt = at;
        return sheet;
    }

    public boolean isSigned() {
        return status == InspectionStatus.SIGNED;
    }

    /** Lưu nháp: thay phần đầu và dòng, tăng phiên bản. Gọi khi chưa ký (service kiểm trước). */
    public void saveDraft(Map<String, Object> header, List<Map<String, Object>> rows, Long userId, Instant at) {
        this.header = Map.copyOf(header);
        this.sheetRows = List.copyOf(rows);
        this.docVersion++;
        this.updatedBy = userId;
        this.updatedAt = at;
    }

    /** Ký: khóa biên bản, chụp {@code snapshotRows} (dòng đầy đủ gồm ô hệ thống lúc ký) và ghi lịch sử. */
    public void sign(
            InspectionSignature signature, InspectionEvent event, List<Map<String, Object>> snapshotRows, Instant at) {
        this.status = InspectionStatus.SIGNED;
        this.signature = signature;
        this.snapshot = List.copyOf(snapshotRows);
        this.history = append(event);
        this.updatedBy = signature.signedBy();
        this.updatedAt = at;
    }

    /** Sửa lại: về nháp, bỏ chữ ký và bản chụp (ảnh cũ giữ trong lịch sử), tăng phiên bản. */
    public void reopen(InspectionEvent event, Long userId, Instant at) {
        this.status = InspectionStatus.DRAFT;
        this.signature = null;
        this.snapshot = null;
        this.history = append(event);
        this.docVersion++;
        this.updatedBy = userId;
        this.updatedAt = at;
    }

    private List<InspectionEvent> append(InspectionEvent event) {
        List<InspectionEvent> next = new ArrayList<>(history);
        next.add(event);
        return List.copyOf(next);
    }
}
