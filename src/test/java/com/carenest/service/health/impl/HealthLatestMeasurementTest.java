package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.carenest.entity.health.HealthMeasurement;
import com.carenest.repository.health.HealthMeasurementRepository;
import com.carenest.utils.enums.HealthExamStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** OBS-28 + HLT-08/HLT-11: số đo trên phiếu cuối năm lấy nguyên một lần đo đã công bố. */
@ExtendWith(MockitoExtension.class)
class HealthLatestMeasurementTest {

    private static final long CHILD = 7L;
    private static final LocalDate D1 = LocalDate.of(2026, 10, 10);
    private static final LocalDate D2 = LocalDate.of(2026, 10, 15);

    @Mock
    private HealthMeasurementRepository measurementRepository;

    @InjectMocks
    private HealthMeasurementServiceImpl service;

    @Test
    void draftExamIsIgnored() {
        given(round(1L, D1, "18.0"), exam(2L, D2, "18.6", HealthExamStatus.DRAFT));

        assertThat(service.findLatestMeasurement(CHILD, D2)).hasValueSatisfying(m -> {
            assertThat(m.date()).isEqualTo(D1);
            assertThat(m.weightKg()).isEqualByComparingTo("18.0");
        });
    }

    @Test
    void sameDayConcludedExamWinsOverLaterRound() {
        given(exam(1L, D2, "17.0", HealthExamStatus.CONCLUDED), round(2L, D2, "18.0"));

        assertThat(service.findLatestMeasurement(CHILD, D2))
                .hasValueSatisfying(m -> assertThat(m.weightKg()).isEqualByComparingTo("17.0"));
    }

    @Test
    void sameDayWithoutExamTakesLaterRow() {
        given(round(1L, D2, "17.4"), round(2L, D2, "17.9"));

        assertThat(service.findLatestMeasurement(CHILD, D2))
                .hasValueSatisfying(m -> assertThat(m.weightKg()).isEqualByComparingTo("17.9"));
    }

    @Test
    void measurementAfterCutoffIsIgnored() {
        given(round(1L, D1, "18.0"), round(2L, D2, "18.5"));

        assertThat(service.findLatestMeasurement(CHILD, D2.minusDays(1))).hasValueSatisfying(m -> {
            assertThat(m.date()).isEqualTo(D1);
            assertThat(m.weightKg()).isEqualByComparingTo("18.0");
        });
    }

    // Repository trả theo ngày giảm dần, id giảm dần như findByChildIdOrderByDateDescIdDesc
    private void given(HealthMeasurement... rows) {
        when(measurementRepository.findByChildIdOrderByDateDescIdDesc(CHILD))
                .thenReturn(Stream.of(rows)
                        .sorted(Comparator.comparing(HealthMeasurement::getDate)
                                .thenComparing(HealthMeasurement::getId)
                                .reversed())
                        .toList());
    }

    private static HealthMeasurement round(long id, LocalDate date, String weight) {
        HealthMeasurement m = HealthMeasurement.builder()
                .childId(CHILD)
                .date(date)
                .heightCm(new BigDecimal("102"))
                .weightKg(new BigDecimal(weight))
                .measuredBy(1L)
                .createdAt(Instant.parse("2026-10-01T00:00:00Z"))
                .build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private static HealthMeasurement exam(long id, LocalDate date, String weight, HealthExamStatus status) {
        HealthMeasurement m = round(id, date, weight);
        ReflectionTestUtils.setField(m, "examStatus", status);
        if (status == HealthExamStatus.DRAFT) {
            ReflectionTestUtils.setField(m, "publishedAt", null);
        }
        return m;
    }
}
