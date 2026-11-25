package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.meals.InspectionSheetRepository;
import com.carenest.repository.meals.MealPhotoRepository;
import com.carenest.utils.enums.FileCategory;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Runner nạp ảnh dev (chữ ký, ảnh bữa ăn, biên bản kiểm thực đã ký) trên bộ seed dev thật, kho tệp giả. */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/dev")
@Testcontainers(disabledWithoutDocker = true)
class DevBoardingFileSeederIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @MockitoBean
    private FileStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MealPhotoRepository photoRepository;

    @Autowired
    private InspectionSheetRepository sheetRepository;

    @Autowired
    private InspectionSkeleton skeleton;

    private DevBoardingFileSeeder seeder;

    @BeforeEach
    void setUp() {
        AtomicInteger seq = new AtomicInteger();
        when(storage.store(any(FileCategory.class), any(), any(byte[].class))).thenAnswer(call -> {
            byte[] content = call.getArgument(2);
            String key = "files/f_" + seq.incrementAndGet() + ".png";
            return new FileStorage.StoredFile(key, "image/png", content.length);
        });
        when(storage.loadBytes(any(FileCategory.class), any())).thenReturn(new byte[] {1, 2, 3});
        seeder = new DevBoardingFileSeeder(jdbc, storage, photoRepository, sheetRepository, skeleton);
    }

    @Test
    @DisplayName("Nạp chữ ký, ảnh bữa ăn, biên bản đã ký cho các ngày gần nhất; chạy lại không nhân đôi")
    void seedsFilesOnceAndIsIdempotent() {
        seeder.run(null);

        long photos = count("meal_photos");
        long signatures = count("user_signatures");
        long signed = jdbc.queryForObject("SELECT count(*) FROM inspection_sheets WHERE status = 'SIGNED'", Long.class);
        assertThat(photos).isPositive();
        assertThat(signatures).isEqualTo(2);
        assertThat(signed).isPositive();
        assertThat(jdbc.queryForObject(
                        "SELECT count(DISTINCT tab) FROM inspection_sheets WHERE status = 'SIGNED'", Long.class))
                .isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM inspection_sheets WHERE status = 'SIGNED' AND signature IS NULL",
                        Long.class))
                .isZero();
        // Ngày dành cho yêu cầu mở ghi bù không được ký sẵn
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM inspection_sheets s JOIN inspection_day_grants g "
                                + "ON g.campus_id = s.campus_id AND g.sheet_date = s.sheet_date AND g.tab = s.tab",
                        Long.class))
                .isZero();

        long sheets = count("inspection_sheets");
        seeder.run(null);

        assertThat(count("meal_photos")).isEqualTo(photos);
        assertThat(count("user_signatures")).isEqualTo(signatures);
        assertThat(count("inspection_sheets")).isEqualTo(sheets);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
