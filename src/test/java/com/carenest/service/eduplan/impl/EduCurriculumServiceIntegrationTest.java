package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.eduplan.CurriculumContentResponse;
import com.carenest.dto.eduplan.CurriculumGoalResponse;
import com.carenest.exception.GlobalException;
import com.carenest.service.eduplan.EduCurriculumService;
import com.carenest.utils.ApiCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Danh mục Chương trình GDMN của Bộ (EDU-28) nạp bằng V33/V34, đọc qua service thật. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduCurriculumServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private EduCurriculumService service;

    @Autowired
    private JdbcTemplate jdbc;

    private Long ageGroup(String code) {
        return jdbc.queryForObject("SELECT id FROM age_groups WHERE code = ?", Long.class, code);
    }

    @Test
    @DisplayName("EDU-28: nguồn là VBHN 01/VBHN-BGDĐT ngày 13/4/2021, có link và phạm vi")
    void sourceDocument() {
        assertThat(service.getSources()).singleElement().satisfies(s -> {
            assertThat(s.documentNo()).isEqualTo("01/VBHN-BGDĐT");
            assertThat(s.issuedDate()).isEqualTo(LocalDate.of(2021, 4, 13));
            assertThat(s.issuer()).isEqualTo("Bộ Giáo dục và Đào tạo");
            assertThat(s.officialUrl()).startsWith("https://");
            assertThat(s.retrievedFromUrl()).startsWith("https://");
            assertThat(s.scopeNote()).contains("Kết quả mong đợi", "Nội dung giáo dục theo độ tuổi");
        });
    }

    @Test
    @DisplayName("EDU-28: đủ mục tiêu và gợi ý nội dung cho 4 nhóm tuổi")
    void countsPerAgeGroup() {
        assertThat(service.getGoals(ageGroup("ag-2"))).hasSize(41);
        assertThat(service.getGoals(ageGroup("ag-3"))).hasSize(84);
        assertThat(service.getGoals(ageGroup("ag-4"))).hasSize(99);
        assertThat(service.getGoals(ageGroup("ag-5"))).hasSize(103);
        assertThat(service.getContents(ageGroup("ag-2"))).hasSize(77);
        assertThat(service.getContents(ageGroup("ag-3"))).hasSize(119);
        assertThat(service.getContents(ageGroup("ag-4"))).hasSize(121);
        assertThat(service.getContents(ageGroup("ag-5"))).hasSize(106);
    }

    @Test
    @DisplayName("EDU-28: câu nguyên văn theo cách Thái Dương (câu đánh số = MT, gạch đầu dòng = nội dung)")
    void verbatimThaiDuongMapping() {
        List<CurriculumGoalResponse> goals = service.getGoals(ageGroup("ag-3"));
        assertThat(goals.getFirst()).satisfies(g -> {
            assertThat(g.domainName()).isEqualTo("Phát triển thể chất");
            assertThat(g.subDomain()).isEqualTo("Phát triển vận động");
            assertThat(g.groupText()).isEqualTo("Thực hiện được các động tác phát triển các nhóm cơ và hô hấp");
            assertThat(g.goalText()).isEqualTo("Thực hiện đủ các động tác trong bài tập thể dục theo hướng dẫn.");
            assertThat(g.contents()).isEmpty();
        });
        assertThat(goals)
                .filteredOn(g -> "2.1".equals(g.goalNo()) && g.sectionRef().endsWith("› I › a) › 2 › 2.1"))
                .singleElement()
                .satisfies(g -> {
                    assertThat(g.goalText()).isEqualTo("Giữ được thăng bằng cơ thể khi thực hiện vận động");
                    assertThat(g.contents())
                            .containsExactly("Đi hết đoạn đường hẹp (3m x 0,2m).", "Đi kiễng gót liên tục 3m.");
                });
    }

    @Test
    @DisplayName("EDU-28: nhà trẻ tách mục IV: hát, vẽ, nặn vào Phát triển thẩm mỹ")
    void nurseryAestheticsSplit() {
        List<CurriculumGoalResponse> goals = service.getGoals(ageGroup("ag-2"));
        assertThat(goals)
                .filteredOn(g -> g.sectionRef().startsWith("Phần hai › D › IV › 4"))
                .isNotEmpty()
                .allMatch(g -> g.domainName().equals("Phát triển thẩm mỹ"));
        assertThat(goals)
                .filteredOn(g -> g.sectionRef().startsWith("Phần hai › D › IV › 1"))
                .isNotEmpty()
                .allMatch(g -> g.domainName().equals("Phát triển tình cảm và kỹ năng xã hội"));
    }

    @Test
    @DisplayName("EDU-28: gợi ý nội dung mục C có nhãn nhóm và tham chiếu mục")
    void contentSuggestions() {
        List<CurriculumContentResponse> contents = service.getContents(ageGroup("ag-3"));
        assertThat(contents)
                .filteredOn(c -> "Đi kiễng gót.".equals(c.text()))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.label()).isEqualTo("Đi và chạy");
                    assertThat(c.sectionRef()).isEqualTo("Phần ba › C › I › a) › 2");
                    assertThat(c.domainName()).isEqualTo("Phát triển thể chất");
                });
    }

    @Test
    @DisplayName("EDU-28: nhóm tuổi không tồn tại ⇒ 2202")
    void unknownAgeGroup() {
        assertThatThrownBy(() -> service.getGoals(999_999L))
                .isInstanceOf(GlobalException.class)
                .satisfies(
                        ex -> assertThat(((GlobalException) ex).getApiCode()).isEqualTo(ApiCode.AGE_GROUP_NOT_FOUND));
    }
}
