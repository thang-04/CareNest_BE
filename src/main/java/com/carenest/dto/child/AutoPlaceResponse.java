package com.carenest.dto.child;

import java.util.List;

/** unplaced = số trẻ chưa xếp được theo lớp cũ (lớp năm mới đúng nhóm tuổi đã hết chỗ hoặc chưa có). */
/** undecided: trẻ lớp ra trường chưa hoàn thành chương trình hoặc chưa đánh giá, để Hiệu trưởng chọn ra trường / ở lại. */
public record AutoPlaceResponse(int promoted, int graduated, int undecided, List<Unplaced> unplaced) {

    public record Unplaced(String className, int count) {}
}
