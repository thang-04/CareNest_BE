package com.carenest.entity.meals;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dòng hàng khô kiểm nhận không đạt (NUT-11): không thành lô kho, giữ lại cùng biện pháp xử lý. */
public record StockRejectedLine(Long foodId, String lotNo, LocalDate expiryDate, BigDecimal quantity, String note) {}
