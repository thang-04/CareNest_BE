package com.carenest.dto.knowledge;

import com.carenest.utils.enums.AiDocumentType;
import java.time.LocalDate;
import java.util.Set;

/**
 * Truy vấn RAG. text phải được dựng bằng code từ dữ liệu không định danh (nhóm tuổi, tên tiêu chí), không chứa
 * ghi chú hay tên trẻ, vì text được gửi tới embedding API bên ngoài.
 */
public record RetrievalQuery(String text, Set<AiDocumentType> types, String region, LocalDate asOf) {}
