package com.carenest.utils.enums;

/** Mẫu biên bản kiểm thực theo QĐ 1246/QĐ-BYT (INS-01): M1 tươi/khô, M2, M3 và soát sổ lưu mẫu M5. */
public enum InspectionTab {
    FRESH,
    DRY,
    PROCESS,
    SERVE,
    SAMPLE;

    /** M1–M3 phải chọn Đạt / Không đạt mọi dòng mới ký được; M5 do bếp ghi nên không bắt buộc (INS-02). */
    public boolean requiresSensory() {
        return this != SAMPLE;
    }
}
