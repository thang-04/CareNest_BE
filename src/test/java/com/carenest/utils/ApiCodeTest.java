package com.carenest.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Mã lỗi tập trung: không trùng, đúng 4 số, câu lỗi format được (docs/contracts/ERROR_CONTRACT.md). */
class ApiCodeTest {

    private static final List<ApiCode> ERRORS = Arrays.stream(ApiCode.values())
            .filter(c -> c.getErrorCode() != null)
            .toList();

    @Test
    void errorCodesAreUniqueFourDigitAndNeverOnSuccess() {
        assertThat(ERRORS).extracting(ApiCode::getErrorCode).doesNotHaveDuplicates();
        assertThat(ERRORS).allSatisfy(c -> assertThat(c.getErrorCode()).isBetween(1000, 9999));
        assertThat(ERRORS)
                .allSatisfy(c -> assertThat(c.getHttpStatus().isError()).isTrue());
        assertThat(ApiCode.SUCCESSFUL.getErrorCode()).isNull();
        assertThat(ApiCode.CREATED.getErrorCode()).isNull();
    }

    @Test
    void everyMessageIsAValidPattern() {
        assertThat(ERRORS)
                .allSatisfy(c -> assertThat(new MessageFormat(c.getDefaultDesc()).getFormats())
                        .isNotNull());
        assertThat(ERRORS).map(ApiCode::getDefaultDesc).allMatch(Objects::nonNull);
    }

    @Test
    void formatShowsDatesAndNumbersForUsers() {
        assertThat(ApiCode.SCHOOL_YEAR_CLOSE_TOO_EARLY.format(LocalDate.of(2028, 5, 31)))
                .isEqualTo("Chỉ kết thúc được năm học sau ngày kết thúc 31/05/2028");
        assertThat(ApiCode.CHILD_NOT_IN_ACTIVE_YEAR.format(12345L)).contains("12345");
        assertThat(ApiCode.NOT_SCHOOL_DAY.format()).isEqualTo(ApiCode.NOT_SCHOOL_DAY.getDefaultDesc());
    }
}
