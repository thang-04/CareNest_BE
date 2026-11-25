package com.carenest.service.meals;

import com.carenest.dto.meals.StockDisposalRequest;
import com.carenest.dto.meals.StockDisposalResponse;
import com.carenest.security.CurrentUser;

/** Xuất kho khô ngoài trừ theo suất: trả NCC lô còn hạn (NUT-29) và xuất hủy lô hết hạn (NUT-30). */
public interface StockDisposalService {

    /** Trả lại NCC các lô còn hạn do chính NCC đó giao; trừ tồn từng lô, ghi bút toán {@code SUPPLIER_RETURN}. */
    StockDisposalResponse createReturn(CurrentUser currentUser, StockDisposalRequest request);

    /** Xuất hủy các lô đã hết hạn kèm lý do; trừ tồn từng lô, ghi bút toán {@code WRITE_OFF}. */
    StockDisposalResponse createWriteOff(CurrentUser currentUser, StockDisposalRequest request);
}
