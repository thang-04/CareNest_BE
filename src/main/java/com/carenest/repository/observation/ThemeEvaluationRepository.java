package com.carenest.repository.observation;

import com.carenest.entity.observation.ThemeEvaluation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThemeEvaluationRepository extends JpaRepository<ThemeEvaluation, Long> {

    Optional<ThemeEvaluation> findByClassroomIdAndThemeId(Long classroomId, Long themeId);

    List<ThemeEvaluation> findByClassroomId(Long classroomId);

    /** Chủ đề của kế hoạch năm học đã duyệt (bản mới nhất) của nhóm tuổi; chỉ đọc bảng của module kế hoạch giáo dục. */
    interface ThemeRow {
        Long getId();

        /** Dòng gốc của chủ đề (giữ qua các bản kế hoạch): khóa lưu bảng đánh giá. */
        Long getLineage();

        String getName();

        String getAbbr();

        LocalDate getStartDate();

        LocalDate getEndDate();
    }

    /** MT có nội dung được tích cho chủ đề; lineage nối MT qua các bản kế hoạch (giáo án chấm theo id dòng MT). */
    interface GoalRow {
        Long getId();

        Long getLineage();

        Short getGoalNo();

        String getText();
    }

    @Query(
            value =
                    "SELECT t.id AS id, COALESCE(t.lineage_id, t.id) AS lineage, t.name AS name, t.abbr AS abbr, t.start_date AS startDate, t.end_date AS endDate"
                            + " FROM edu_themes t WHERE t.team_plan_id = (SELECT p.id FROM edu_team_plans p"
                            + " WHERE p.school_year_id = :schoolYearId AND p.age_group_id = :ageGroupId AND p.status = 'APPROVED'"
                            + " ORDER BY p.version_no DESC, p.id DESC LIMIT 1)"
                            + " AND t.start_date IS NOT NULL AND t.end_date IS NOT NULL ORDER BY t.start_date, t.sort_order, t.id",
            nativeQuery = true)
    List<ThemeRow> findApprovedThemes(@Param("schoolYearId") Long schoolYearId, @Param("ageGroupId") Long ageGroupId);

    // PHT chuyên môn ban hành kế hoạch năm của nhóm tuổi (EDU-30): người ký "Phó hiệu trưởng" trên bản in
    @Query(
            value =
                    "SELECT p.issued_by FROM edu_team_plans p WHERE p.school_year_id = :schoolYearId"
                            + " AND p.age_group_id = :ageGroupId AND p.status = 'APPROVED' ORDER BY p.version_no DESC, p.id DESC LIMIT 1",
            nativeQuery = true)
    Long findApprovedPlanIssuer(@Param("schoolYearId") Long schoolYearId, @Param("ageGroupId") Long ageGroupId);

    @Query(
            value = "SELECT g.id AS id, COALESCE(g.lineage_id, g.id) AS lineage, g.goal_no AS goalNo, g.text AS text"
                    + " FROM edu_plan_goals g WHERE EXISTS (SELECT 1 FROM edu_goal_contents c"
                    + " JOIN edu_content_theme_ticks k ON k.content_id = c.id WHERE c.goal_id = g.id AND k.theme_id = :themeId)"
                    + " ORDER BY g.goal_no, g.sort_order, g.id",
            nativeQuery = true)
    List<GoalRow> findThemeGoals(@Param("themeId") Long themeId);

    /** MT của kế hoạch năm học đã duyệt (bản mới nhất) kèm lĩnh vực, cho phiếu đánh giá cuối năm (OBS-28). */
    interface YearGoalRow {
        Long getLineage();

        Short getGoalNo();

        String getText();

        Long getDomainId();

        String getDomainName();
    }

    @Query(
            value = "SELECT COALESCE(g.lineage_id, g.id) AS lineage, g.goal_no AS goalNo, g.text AS text,"
                    + " d.id AS domainId, d.name AS domainName FROM edu_plan_goals g"
                    + " LEFT JOIN edu_domains d ON d.id = g.domain_id"
                    + " WHERE g.team_plan_id = (SELECT p.id FROM edu_team_plans p WHERE p.school_year_id = :schoolYearId"
                    + " AND p.age_group_id = :ageGroupId AND p.status = 'APPROVED' ORDER BY p.version_no DESC, p.id DESC LIMIT 1)"
                    + " ORDER BY d.sort_order NULLS LAST, d.id NULLS LAST, g.goal_no, g.sort_order, g.id",
            nativeQuery = true)
    List<YearGoalRow> findYearGoals(@Param("schoolYearId") Long schoolYearId, @Param("ageGroupId") Long ageGroupId);
}
