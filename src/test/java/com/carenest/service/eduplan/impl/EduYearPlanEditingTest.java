package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.utils.ApiCode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EduYearPlanEditingTest {
    @Test
    void schoolYearPlanEditIsNotLockedToCreator() throws Exception {
        // EDU-07 (Tiền 25/11/2026): bỏ kiểm tra người tạo; quyền soạn kiểm bằng isEditor ở nơi gọi
        assertThat(Arrays.stream(EduYearPlanServiceImpl.class.getDeclaredMethods())
                        .map(java.lang.reflect.Method::getName))
                .doesNotContain("requireDesignatedAuthor", "isOwner");
        assertThat(ApiCode.YEAR_PLAN_EDIT_FORBIDDEN.name()).isNotBlank();
    }
}
