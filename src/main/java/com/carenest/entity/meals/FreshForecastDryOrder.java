package com.carenest.entity.meals;

import java.math.BigDecimal;

/** Lượng hàng khô đặt cho kỳ của bản dự trù (NUT-12), đơn vị gốc g/ml; chụp lúc lưu, không tính lại. */
public record FreshForecastDryOrder(Long foodId, BigDecimal quantity) {}
