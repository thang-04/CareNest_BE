package com.carenest.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    @DisplayName("AUTH-18: SĐT chuẩn hóa thành 10 số bắt đầu 0 để làm tên đăng nhập")
    void normalizesVietnamesePhoneNumbers() {
        assertThat(PhoneNumbers.normalize("0912 345 678")).contains("0912345678");
        assertThat(PhoneNumbers.normalize("+84 912.345.678")).contains("0912345678");
        assertThat(PhoneNumbers.normalize("84912345678")).contains("0912345678");
        assertThat(PhoneNumbers.normalize("(091) 234-5678")).contains("0912345678");
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThat(PhoneNumbers.normalize("912345678")).isEmpty();
        assertThat(PhoneNumbers.normalize("09123456789")).isEmpty();
        assertThat(PhoneNumbers.normalize("09123abc78")).isEmpty();
        assertThat(PhoneNumbers.normalize(null)).isEmpty();
    }
}
