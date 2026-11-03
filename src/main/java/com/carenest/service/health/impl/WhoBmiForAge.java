package com.carenest.service.health.impl;

import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.HealthBmiStatus;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * Mức BMI theo tuổi theo chuẩn WHO (HLT-03, HLT-18) từ bảng L, M, S theo tháng tuổi tròn trong
 * {@code health/who-bmi-for-age.csv}: đến 60 tháng WHO 2006, từ 61 tháng WHO 2007. Z-score theo phương pháp LMS; ngoài
 * ±3SD tính theo cách giới hạn đuôi của WHO.
 */
final class WhoBmiForAge {

    private static final int MAX_MONTHS = 84;
    // Từ 61 tháng theo bảng WHO 2007: thừa cân > +1SD, béo phì > +2SD (đến 60 tháng: > +2SD, > +3SD)
    private static final int REFERENCE_2007_FROM_MONTHS = 61;
    private static final String TABLE = "/health/who-bmi-for-age.csv";
    private static final Map<Gender, double[][]> LMS = load();

    /** Mức và z-score làm tròn 2 chữ số của một lần đo. */
    record Result(HealthBmiStatus status, BigDecimal zScore) {}

    private WhoBmiForAge() {}

    /** Null khi thiếu giới tính, chiều cao, cân nặng hoặc tuổi ngoài 0–84 tháng. */
    static Result classify(Gender gender, int ageMonths, BigDecimal heightCm, BigDecimal weightKg) {
        if (gender == null
                || heightCm == null
                || weightKg == null
                || heightCm.signum() <= 0
                || weightKg.signum() <= 0
                || ageMonths < 0
                || ageMonths > MAX_MONTHS) {
            return null;
        }
        double heightM = heightCm.doubleValue() / 100;
        double[] lms = LMS.get(gender)[ageMonths];
        // Xếp mức theo z đã làm tròn 2 chữ số như phần mềm WHO, để mức luôn khớp z-score hiển thị
        BigDecimal z = BigDecimal.valueOf(zScore(weightKg.doubleValue() / (heightM * heightM), lms[0], lms[1], lms[2]))
                .setScale(2, RoundingMode.HALF_UP);
        return new Result(status(z.doubleValue(), ageMonths), z);
    }

    /** L, M, S của một giới tính tại tháng tuổi (để test đối chiếu bảng). */
    static double[] lms(Gender gender, int ageMonths) {
        return LMS.get(gender)[ageMonths].clone();
    }

    static double zScore(double bmi, double l, double m, double s) {
        double z = (Math.pow(bmi / m, l) - 1) / (l * s);
        if (z > 3) {
            double sd3 = valueAt(3, l, m, s);
            return 3 + (bmi - sd3) / (sd3 - valueAt(2, l, m, s));
        }
        if (z < -3) {
            double sd3 = valueAt(-3, l, m, s);
            return -3 + (bmi - sd3) / (valueAt(-2, l, m, s) - sd3);
        }
        return z;
    }

    /** BMI tại z-score {@code z} (công thức LMS, không giới hạn đuôi). */
    static double valueAt(double z, double l, double m, double s) {
        return m * Math.pow(1 + l * s * z, 1 / l);
    }

    private static HealthBmiStatus status(double z, int ageMonths) {
        if (z < -3) {
            return HealthBmiStatus.SEVERE_THINNESS;
        }
        if (z < -2) {
            return HealthBmiStatus.THINNESS;
        }
        if (z <= 1) {
            return HealthBmiStatus.NORMAL;
        }
        if (ageMonths >= REFERENCE_2007_FROM_MONTHS) {
            return z <= 2 ? HealthBmiStatus.OVERWEIGHT : HealthBmiStatus.OBESE;
        }
        if (z <= 2) {
            return HealthBmiStatus.OVERWEIGHT_RISK;
        }
        return z <= 3 ? HealthBmiStatus.OVERWEIGHT : HealthBmiStatus.OBESE;
    }

    // Bảng phải đủ từng tháng 0–84 cho cả hai giới tính, L khác 0 (công thức LMS dùng L làm số chia)
    private static Map<Gender, double[][]> load() {
        Map<Gender, double[][]> table = new EnumMap<>(Gender.class);
        try (InputStream in = WhoBmiForAge.class.getResourceAsStream(TABLE)) {
            if (in == null) {
                throw new IllegalStateException("Thiếu bảng BMI theo tuổi " + TABLE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                // Bỏ BOM nếu trình soạn thảo thêm vào đầu tệp
                line = line.replace("﻿", "");
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                String[] cells = line.split(",");
                int month = Integer.parseInt(cells[1].trim());
                double l = Double.parseDouble(cells[2].trim());
                if (l == 0) {
                    throw new IllegalStateException("L bằng 0 ở dòng " + line);
                }
                table.computeIfAbsent(Gender.valueOf(cells[0].trim()), g -> new double[MAX_MONTHS + 1][])[month] =
                        new double[] {l, Double.parseDouble(cells[3].trim()), Double.parseDouble(cells[4].trim())};
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        for (Gender gender : Gender.values()) {
            double[][] rows = table.get(gender);
            for (int month = 0; month <= MAX_MONTHS; month++) {
                if (rows == null || rows[month] == null) {
                    throw new IllegalStateException("Bảng BMI theo tuổi thiếu " + gender + " tháng " + month);
                }
            }
        }
        return table;
    }
}
