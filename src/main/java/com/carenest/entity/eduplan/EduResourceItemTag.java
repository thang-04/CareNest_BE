package com.carenest.entity.eduplan;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** EDU-87: nhãn chủ đề (kèm năm học) hoặc MT của học liệu; MT giữ mã mục tiêu Chương trình Bộ để khớp sang năm học sau. */
@Entity
@Table(name = "edu_resource_item_tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduResourceItemTag {
    public static final String THEME = "THEME";
    public static final String GOAL = "GOAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 10)
    private String kind;

    private Long refId;
    private Long curriculumGoalId;

    @Column(length = 500)
    private String name;

    private Long schoolYearId;

    /** NULL = nhãn thêm tay; có giá trị = nhãn tự thêm, DB xóa theo dòng gắn (EDU-87). */
    private Long attachmentId;

    public static EduResourceItemTag of(
            Long itemId,
            String kind,
            Long refId,
            Long curriculumGoalId,
            String name,
            Long schoolYearId,
            Long attachmentId) {
        EduResourceItemTag t = new EduResourceItemTag();
        t.schoolYearId = schoolYearId;
        t.attachmentId = attachmentId;
        t.itemId = itemId;
        t.kind = kind;
        t.refId = refId;
        t.curriculumGoalId = curriculumGoalId;
        t.name = name;
        return t;
    }
}
