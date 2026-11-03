package com.carenest.service.meals;

import com.carenest.dto.meals.StockReceiptRequest;
import com.carenest.dto.meals.StockReceiptResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.StockReceiptStatus;
import java.time.LocalDate;
import java.util.List;

/** Phiếu nhập kho khô theo điểm trường (NUT-11): nháp → xác nhận (tạo lô) → hủy bằng bút toán đảo. */
public interface StockReceiptService {

    List<StockReceiptResponse> getReceipts(
            CurrentUser currentUser, Long campusId, LocalDate from, LocalDate to, StockReceiptStatus status);

    StockReceiptResponse getReceipt(CurrentUser currentUser, Long receiptId);

    StockReceiptResponse createReceipt(CurrentUser currentUser, StockReceiptRequest request);

    StockReceiptResponse updateReceipt(CurrentUser currentUser, Long receiptId, StockReceiptRequest request);

    void deleteReceipt(CurrentUser currentUser, Long receiptId);

    /** Tạo lô + bút toán nhập, rồi trừ tiếp phần thiếu của các ngày trước (P-38). */
    StockReceiptResponse confirmReceipt(CurrentUser currentUser, Long receiptId);

    /** Chỉ khi mọi lô của phiếu chưa bị trừ; ghi bút toán đảo kèm lý do. */
    StockReceiptResponse cancelReceipt(CurrentUser currentUser, Long receiptId, String reason);
}
