package com.carenest.utils.enums;

/** Kết quả xếp lớp năm mới của một trẻ: lên lớp, ở lại nhóm tuổi, nghỉ học/chuyển trường, ra trường. */
public enum PlacementAction {
    PROMOTE,
    STAY,
    LEAVE,
    GRADUATE;

    public boolean keepsChildInClass() {
        return this == PROMOTE || this == STAY;
    }
}
