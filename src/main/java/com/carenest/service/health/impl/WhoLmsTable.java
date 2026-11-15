package com.carenest.service.health.impl;

import com.carenest.utils.enums.Gender;
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
 * Bảng L, M, S của WHO theo tháng tuổi tròn 0–84 cho hai giới tính (CSV {@code sex,month,l,m,s}, dòng {@code #} là ghi
 * chú) và z-score theo phương pháp LMS (HLT-03). Dùng chung cho BMI, cân nặng, chiều cao theo tuổi.
 */
final class WhoLmsTable {

    static final int MAX_MONTHS = 84;

    private final Map<Gender, double[][]> rows;

    private WhoLmsTable(Map<Gender, double[][]> rows) {
        this.rows = rows;
    }

    /** L, M, S của một giới tính tại tháng tuổi. */
    double[] lms(Gender gender, int ageMonths) {
        return rows.get(gender)[ageMonths].clone();
    }

    /**
     * Z-score làm tròn 2 chữ số (như phần mềm WHO, để mức khớp số hiển thị); null khi thiếu giới tính, giá trị không
     * dương hoặc tuổi ngoài 0–84 tháng. {@code restrictedTails}: ngoài ±3SD tính theo khoảng SD2–SD3 (WHO áp cho chỉ số
     * theo cân nặng).
     */
    BigDecimal zScore(Gender gender, int ageMonths, double value, boolean restrictedTails) {
        if (gender == null || !(value > 0) || Double.isInfinite(value) || ageMonths < 0 || ageMonths > MAX_MONTHS) {
            return null;
        }
        double[] lms = rows.get(gender)[ageMonths];
        return BigDecimal.valueOf(zScore(value, lms[0], lms[1], lms[2], restrictedTails))
                .setScale(2, RoundingMode.HALF_UP);
    }

    static double zScore(double value, double l, double m, double s, boolean restrictedTails) {
        double z = (Math.pow(value / m, l) - 1) / (l * s);
        if (restrictedTails && z > 3) {
            double sd3 = valueAt(3, l, m, s);
            return 3 + (value - sd3) / (sd3 - valueAt(2, l, m, s));
        }
        if (restrictedTails && z < -3) {
            double sd3 = valueAt(-3, l, m, s);
            return -3 + (value - sd3) / (valueAt(-2, l, m, s) - sd3);
        }
        return z;
    }

    /** Giá trị tại z-score {@code z} (công thức LMS, không giới hạn đuôi). */
    static double valueAt(double z, double l, double m, double s) {
        return m * Math.pow(1 + l * s * z, 1 / l);
    }

    // Bảng phải đủ từng tháng 0–84 cho cả hai giới tính, L khác 0 (công thức LMS dùng L làm số chia)
    static WhoLmsTable load(String resource, String label) {
        Map<Gender, double[][]> table = new EnumMap<>(Gender.class);
        try (InputStream in = WhoLmsTable.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Thiếu bảng " + label + " " + resource);
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
            double[][] months = table.get(gender);
            for (int month = 0; month <= MAX_MONTHS; month++) {
                if (months == null || months[month] == null) {
                    throw new IllegalStateException("Bảng " + label + " thiếu " + gender + " tháng " + month);
                }
            }
        }
        return new WhoLmsTable(table);
    }
}
