package com.carenest.dto.eduplan;

import java.util.Set;
import tools.jackson.databind.node.ObjectNode;

/**
 * Lọc danh sách gọn tài liệu lớp (edu-list-paging, Tiền 25/11/2026). Trường null/rỗng ⇒ không lọc theo trường đó.
 * classId: chỉ dựng tài liệu của một lớp; classIds: lọc trong các lớp (vd. lớp của một nhóm tuổi).
 * myTurn: hàng chờ duyệt — chỉ hồ sơ Chờ duyệt người xem thuộc chuỗi duyệt (đúng lượt hoặc bước khác, EDU-93).
 */
public record ClassDocSummaryFilter(
        Long classId,
        Set<String> classIds,
        Set<String> kinds,
        Integer semesterNo,
        String themeId,
        Set<String> statuses,
        boolean myTurn,
        int page,
        int size,
        boolean currentOnly) {

    public ClassDocSummaryFilter(
            Long classId,
            Set<String> classIds,
            Set<String> kinds,
            Integer semesterNo,
            String themeId,
            Set<String> statuses,
            boolean myTurn,
            int page,
            int size) {
        this(classId, classIds, kinds, semesterNo, themeId, statuses, myTurn, page, size, false);
    }

    public boolean matches(ObjectNode node) {
        return in(classIds, node.path("classId").asString(""))
                && in(kinds, node.path("kind").asString(""))
                && in(statuses, node.path("status").asString(""))
                && (semesterNo == null || node.path("semesterNo").asInt(-1) == semesterNo)
                && (themeId == null || themeId.equals(node.path("themeId").asString("")));
    }

    private static boolean in(Set<String> allowed, String value) {
        return allowed == null || allowed.isEmpty() || allowed.contains(value);
    }
}
