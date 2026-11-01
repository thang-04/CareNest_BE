package com.carenest.dto.child;

import java.util.List;

/** unplaced = số trẻ chưa xếp được theo lớp cũ (lớp năm mới đúng nhóm tuổi đã hết chỗ hoặc chưa có). */
public record AutoPlaceResponse(int promoted, int graduated, List<Unplaced> unplaced) {

    public record Unplaced(String className, int count) {}
}
