package com.carenest.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    @DisplayName("AUTH-18: SĐT di động chuẩn hóa thành 10 số đầu 03/05/07/08/09 để làm tên đăng nhập")
    void normalizesVietnamesePhoneNumbers() {
        assertThat(PhoneNumbers.normalize("0912 345 678")).contains("0912345678");
        assertThat(PhoneNumbers.normalize("+84 912.345.678")).contains("0912345678");
        assertThat(PhoneNumbers.normalize("84912345678")).contains("0912345678");
        assertThat(PhoneNumbers.normalize("(091) 234-5678")).contains("0912345678");
        assertThat(PhoneNumbers.normalize("0355123456")).contains("0355123456");
        assertThat(PhoneNumbers.normalize("0868123456")).contains("0868123456");
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThat(PhoneNumbers.normalize("912345678")).isEmpty();
        assertThat(PhoneNumbers.normalize("09123456789")).isEmpty();
        assertThat(PhoneNumbers.normalize("09689041301")).isEmpty();
        // Đầu số không phải di động: máy bàn 02x, đầu 01/04/06 không còn dùng
        assertThat(PhoneNumbers.normalize("0212345678")).isEmpty();
        assertThat(PhoneNumbers.normalize("0123456789")).isEmpty();
        assertThat(PhoneNumbers.normalize("0612345678")).isEmpty();
        assertThat(PhoneNumbers.normalize("09123abc78")).isEmpty();
        assertThat(PhoneNumbers.normalize(null)).isEmpty();
    }
}
