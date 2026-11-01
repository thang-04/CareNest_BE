package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.entity.eduplan.EduDomain;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduDomainRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.utils.ApiCode;
import com.carenest.utils.Constants.EduPermission;
import com.carenest.utils.enums.CareNestRole;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/** EDU-46: quyền, giới hạn dung lượng và lĩnh vực theo cấu hình khi đọc file mục tiêu. */
@ExtendWith(MockitoExtension.class)
class EduGoalFileServiceImplTest {

    private static final CurrentUser TEACHER =
            new CurrentUser(4L, "kc-4", "teacher1", "Giáo viên", null, Set.of(CareNestRole.TEACHER));
    private static final String CSV = "Lĩnh vực,Loại dòng,STT,Mục tiêu,Nội dung giáo dục,Chủ đề\n"
            + "Phát triển thể chất,Mục tiêu,MT1,Trẻ khỏe,Ăn uống,TMN\n";

    @Mock
    private EduDomainRepository domainRepository;

    @Mock
    private EduPlanAccessPolicy accessPolicy;

    @InjectMocks
    private EduGoalFileServiceImpl service;

    @Test
    @DisplayName("Không có quyền sửa kế hoạch năm ⇒ 7005, không đọc file")
    void rejectsWithoutEditPermission() {
        given(accessPolicy.hasPermission(any(), eq(EduPermission.GOALS_EDIT))).willReturn(false);

        assertThatThrownBy(() -> service.preview(TEACHER, file("a.csv", CSV.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.YEAR_PLAN_EDIT_FORBIDDEN));
        verifyNoInteractions(domainRepository);
    }

    @Test
    @DisplayName("File trên 2 MB ⇒ 7035")
    void rejectsLargeFile() {
        given(accessPolicy.hasPermission(any(), eq(EduPermission.GOALS_EDIT))).willReturn(true);

        assertThatThrownBy(() -> service.preview(TEACHER, file("a.csv", new byte[2 * 1024 * 1024 + 1])))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.GOAL_FILE_TOO_LARGE));
    }

    @Test
    @DisplayName("Chỉ dùng lĩnh vực đang bật trong cấu hình")
    void usesActiveConfiguredDomains() {
        given(accessPolicy.hasPermission(any(), eq(EduPermission.GOALS_EDIT))).willReturn(true);
        EduDomain active = Mockito.mock(EduDomain.class);
        given(active.isActive()).willReturn(true);
        given(active.getName()).willReturn("Phát triển thể chất");
        EduDomain off = Mockito.mock(EduDomain.class);
        given(off.isActive()).willReturn(false);
        given(domainRepository.findAllByOrderBySortOrderAsc()).willReturn(List.of(active, off));

        var preview = service.preview(TEACHER, file("a.csv", CSV.getBytes(StandardCharsets.UTF_8)));

        assertThat(preview.domains())
                .singleElement()
                .satisfies(d -> assertThat(d.name()).isEqualTo("Phát triển thể chất"));
    }

    private static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "text/csv", bytes);
    }
}
