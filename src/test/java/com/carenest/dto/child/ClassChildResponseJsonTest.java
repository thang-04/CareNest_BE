package com.carenest.dto.child;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Snapshot LEGACY_BASELINE (V97) lưu danh sách trẻ chỉ có id, fullName, dateOfBirth và được đọc lại bằng
 * convertValue; thêm component nguyên thủy vào record này sẽ lỗi vì Jackson 3 mặc định FAIL_ON_NULL_FOR_PRIMITIVES.
 */
class ClassChildResponseJsonTest {
    // Mapper mặc định của Boot 4 (Jackson 3), cùng cách dựng với FoodSampleRequestJsonTest
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    @DisplayName("Snapshot V97 không có thêm trường vẫn đọc được thành ClassChildResponse")
    void legacySnapshotChildrenStillDeserialize() {
        var node = MAPPER.readTree("[{\"id\":1,\"fullName\":\"An\",\"dateOfBirth\":\"2022-04-15\"}]");

        List<ClassChildResponse> children = MAPPER.convertValue(node, new TypeReference<>() {});

        assertThat(children).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo(1L);
            assertThat(c.fullName()).isEqualTo("An");
            assertThat(c.dateOfBirth()).isEqualTo(LocalDate.of(2022, 4, 15));
        });
    }
}
