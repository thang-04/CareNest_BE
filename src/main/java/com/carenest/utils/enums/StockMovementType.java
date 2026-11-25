package com.carenest.utils.enums;

/**
 * Bút toán kho khô (NUT-11, P-38): nhập lô, trừ theo suất đã xác nhận, điều chỉnh khi xác nhận lại, trừ phần thiếu
 * khi có phiếu bổ sung, đảo khi hủy phiếu, trả NCC lô còn hạn và xuất hủy lô hết hạn (NUT-29, NUT-30).
 */
public enum StockMovementType {
    RECEIPT,
    DEDUCT,
    ADJUST,
    SHORTFALL_DEDUCT,
    RECEIPT_CANCEL,
    SUPPLIER_RETURN,
    WRITE_OFF
}
