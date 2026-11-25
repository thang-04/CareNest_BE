package com.carenest.utils.enums;

/** Loại phiếu xuất kho khô: trả NCC lô còn hạn (NUT-29) hoặc xuất hủy lô hết hạn (NUT-30). */
public enum StockDisposalKind {
    SUPPLIER_RETURN("XT", StockMovementType.SUPPLIER_RETURN),
    WRITE_OFF("XH", StockMovementType.WRITE_OFF);

    private final String codePrefix;
    private final StockMovementType movementType;

    StockDisposalKind(String codePrefix, StockMovementType movementType) {
        this.codePrefix = codePrefix;
        this.movementType = movementType;
    }

    public String codePrefix() {
        return codePrefix;
    }

    /** Loại bút toán ghi cho từng lô của phiếu. */
    public StockMovementType movementType() {
        return movementType;
    }
}
