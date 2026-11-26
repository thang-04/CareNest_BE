package com.carenest.service.child;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Kết quả hoàn thành chương trình GDMN của trẻ 5–6 tuổi (đánh giá cuối năm đã xác nhận) để xếp lớp năm mới. Module
 * đánh giá cài đặt interface này để xếp lớp không phụ thuộc ngược vào module đánh giá.
 */
public interface ProgramCompletionLookup {

    /** childId → true (hoàn thành) / false (chưa hoàn thành); trẻ chưa có đánh giá cuối năm xác nhận thì không có. */
    Map<Long, Boolean> programCompletion(Long schoolYearId, Collection<Long> childIds);

    /** Trẻ đã có đánh giá cuối năm được xác nhận trong năm học (bảng xếp lớp hiện để HT tham khảo, CFG-YR-03). */
    Set<Long> yearEndConfirmed(Long schoolYearId, Collection<Long> childIds);
}
