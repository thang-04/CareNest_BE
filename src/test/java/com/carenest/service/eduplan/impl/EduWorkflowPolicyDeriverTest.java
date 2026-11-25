package com.carenest.service.eduplan.impl;

import static com.carenest.utils.enums.CareNestRole.PRINCIPAL;
import static com.carenest.utils.enums.CareNestRole.TEACHER;
import static com.carenest.utils.enums.CareNestRole.TEAM_LEADER;
import static com.carenest.utils.enums.CareNestRole.VICE_PRINCIPAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.service.eduplan.impl.EduWorkflowPolicy.Role;
import com.carenest.service.eduplan.impl.EduWorkflowPolicyDeriver.Issue;
import com.carenest.service.eduplan.impl.EduWorkflowPolicyDeriver.Result;
import com.carenest.service.eduplan.impl.EduWorkflowPolicyDeriver.Rule;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EduWorkflowPolicyDeriverTest {

    /** Mặc định EDU-54: năm học PHT soạn/HT duyệt; tổ TT soạn, PHT+HT duyệt; gói và chủ đề GV soạn, TT+PHT+HT duyệt. */
    private static Map<CareNestRole, Set<Permission>> defaultMatrix() {
        Map<CareNestRole, Set<Permission>> m = new EnumMap<>(CareNestRole.class);
        m.put(
                PRINCIPAL,
                EnumSet.of(
                        Permission.EDU_SCHOOL_YEAR_PLAN_APPROVE,
                        Permission.EDU_TEAM_PLAN_APPROVE,
                        Permission.EDU_SEMESTER_PACKAGE_APPROVE,
                        Permission.EDU_THEME_PLAN_APPROVE));
        m.put(
                VICE_PRINCIPAL,
                EnumSet.of(
                        Permission.EDU_SCHOOL_YEAR_PLAN_EDIT,
                        Permission.EDU_TEAM_PLAN_APPROVE,
                        Permission.EDU_SEMESTER_PACKAGE_APPROVE,
                        Permission.EDU_THEME_PLAN_APPROVE));
        m.put(
                TEAM_LEADER,
                EnumSet.of(
                        Permission.EDU_TEAM_PLAN_EDIT,
                        Permission.EDU_SEMESTER_PACKAGE_APPROVE,
                        Permission.EDU_THEME_PLAN_APPROVE));
        m.put(TEACHER, EnumSet.of(Permission.EDU_SEMESTER_PACKAGE_EDIT, Permission.EDU_THEME_PLAN_EDIT));
        return m;
    }

    private static List<Rule> rulesFor(Result result, String docType) {
        return result.issues().stream()
                .filter(i -> i.docType().equals(docType))
                .map(Issue::rule)
                .toList();
    }

    @Test
    @DisplayName("BR3.1: ma trận mặc định hợp lệ, chuỗi xếp TT → PHT → HT")
    void defaultMatrixIsValid() {
        Result result = EduWorkflowPolicyDeriver.derive(defaultMatrix());
        assertThat(result.valid()).isTrue();
        assertThat(result.policies()).hasSize(4);
        assertThat(result.policies().get("theme").creators()).containsExactly(Role.TEACHER);
        assertThat(result.policies().get("theme").reviewers())
                .containsExactly(Role.TEAM_LEADER, Role.VICE_PRINCIPAL, Role.PRINCIPAL);
        assertThat(result.policies().get("schoolYear").reviewers()).containsExactly(Role.PRINCIPAL);
    }

    @Test
    @DisplayName("BR1.1: hai vai trò soạn cùng loại ⇒ CREATOR_NOT_EXACTLY_ONE")
    void twoCreators() {
        var m = defaultMatrix();
        m.get(TEAM_LEADER).add(Permission.EDU_THEME_PLAN_EDIT);
        m.get(TEAM_LEADER).remove(Permission.EDU_THEME_PLAN_APPROVE);
        Result result = EduWorkflowPolicyDeriver.derive(m);
        assertThat(rulesFor(result, "theme")).containsExactly(Rule.CREATOR_NOT_EXACTLY_ONE);
        assertThat(result.policies()).doesNotContainKey("theme").containsKey("branch");
    }

    @Test
    @DisplayName("BR1.1: không ai soạn ⇒ CREATOR_NOT_EXACTLY_ONE")
    void noCreator() {
        var m = defaultMatrix();
        m.get(VICE_PRINCIPAL).remove(Permission.EDU_SCHOOL_YEAR_PLAN_EDIT);
        assertThat(rulesFor(EduWorkflowPolicyDeriver.derive(m), "schoolYear"))
                .containsExactly(Rule.CREATOR_NOT_EXACTLY_ONE);
    }

    @Test
    @DisplayName("BR1.2: vai trò soạn đồng thời duyệt ⇒ CREATOR_IS_REVIEWER")
    void creatorIsReviewer() {
        var m = defaultMatrix();
        m.get(TEAM_LEADER).add(Permission.EDU_TEAM_PLAN_APPROVE);
        assertThat(rulesFor(EduWorkflowPolicyDeriver.derive(m), "year")).containsExactly(Rule.CREATOR_IS_REVIEWER);
    }

    @Test
    @DisplayName("BR1.3: GV duyệt ⇒ TEACHER_REVIEWER")
    void teacherReviewer() {
        var m = defaultMatrix();
        m.get(TEACHER).add(Permission.EDU_TEAM_PLAN_APPROVE);
        assertThat(rulesFor(EduWorkflowPolicyDeriver.derive(m), "year")).contains(Rule.TEACHER_REVIEWER);
    }

    @Test
    @DisplayName("BR1.5: không ai duyệt khi người soạn không phải HT ⇒ NO_REVIEWER")
    void noReviewer() {
        var m = defaultMatrix();
        m.get(PRINCIPAL).remove(Permission.EDU_SCHOOL_YEAR_PLAN_APPROVE);
        assertThat(rulesFor(EduWorkflowPolicyDeriver.derive(m), "schoolYear")).containsExactly(Rule.NO_REVIEWER);
    }

    @Test
    @DisplayName("BR1.4: người duyệt cấp thấp hơn người soạn ⇒ REVIEWER_NOT_ABOVE_CREATOR")
    void reviewerBelowCreator() {
        var m = defaultMatrix();
        m.get(TEAM_LEADER).add(Permission.EDU_SCHOOL_YEAR_PLAN_APPROVE);
        Result result = EduWorkflowPolicyDeriver.derive(m);
        assertThat(rulesFor(result, "schoolYear")).containsExactly(Rule.REVIEWER_NOT_ABOVE_CREATOR);
        assertThat(result.issues().getFirst().roles()).containsExactly(TEAM_LEADER);
    }

    @Test
    @DisplayName("BR1.5: HT soạn mà còn người duyệt ⇒ lỗi; HT soạn, dòng duyệt trống ⇒ hợp lệ, chuỗi rỗng")
    void principalCreator() {
        var m = defaultMatrix();
        m.get(VICE_PRINCIPAL).remove(Permission.EDU_SCHOOL_YEAR_PLAN_EDIT);
        m.get(PRINCIPAL).add(Permission.EDU_SCHOOL_YEAR_PLAN_EDIT);
        assertThat(rulesFor(EduWorkflowPolicyDeriver.derive(m), "schoolYear")).contains(Rule.CREATOR_IS_REVIEWER);

        m.get(PRINCIPAL).remove(Permission.EDU_SCHOOL_YEAR_PLAN_APPROVE);
        Result ok = EduWorkflowPolicyDeriver.derive(m);
        assertThat(ok.valid()).isTrue();
        assertThat(ok.policies().get("schoolYear").creators()).containsExactly(Role.PRINCIPAL);
        assertThat(ok.policies().get("schoolYear").reviewers()).isEmpty();

        m.get(VICE_PRINCIPAL).add(Permission.EDU_SCHOOL_YEAR_PLAN_APPROVE);
        assertThat(rulesFor(EduWorkflowPolicyDeriver.derive(m), "schoolYear"))
                .containsExactly(Rule.REVIEWER_NOT_ABOVE_CREATOR);
    }

    @Test
    @DisplayName("BR3.1: thứ tự tick không ảnh hưởng thứ tự chuỗi; vai trò ngoài 4 vai trò bị bỏ qua")
    void orderIndependent() {
        var m = new EnumMap<CareNestRole, Set<Permission>>(CareNestRole.class);
        defaultMatrix().forEach((k, v) -> m.put(k, Set.copyOf(v)));
        m.put(CareNestRole.KITCHEN_STAFF, Set.of(Permission.EDU_THEME_PLAN_EDIT));
        Result result = EduWorkflowPolicyDeriver.derive(m);
        assertThat(result.valid()).isTrue();
        assertThat(result.policies().get("branch").reviewers())
                .containsExactly(Role.TEAM_LEADER, Role.VICE_PRINCIPAL, Role.PRINCIPAL);
    }
}
