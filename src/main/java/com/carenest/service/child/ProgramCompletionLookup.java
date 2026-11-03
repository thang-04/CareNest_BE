package com.carenest.service.child;

import java.util.Collection;
import java.util.Map;

/**
 * Kết quả hoàn thành chương trình GDMN của trẻ 5–6 tuổi (đánh giá cuối năm đã xác nhận) để xếp lớp năm mới. Module
 * đánh giá cài đặt interface này để xếp lớp không phụ thuộc ngược vào module đánh giá.
 */
public interface ProgramCompletionLookup {

    /** childId → true (hoàn thành) / false (chưa hoàn thành); trẻ chưa có đánh giá cuối năm xác nhận thì không có. */
    Map<Long, Boolean> programCompletion(Long schoolYearId, Collection<Long> childIds);
}
