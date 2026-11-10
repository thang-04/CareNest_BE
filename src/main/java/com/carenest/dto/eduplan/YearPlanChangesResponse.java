package com.carenest.dto.eduplan;

import java.util.List;

/**
 * So sánh hai phiên bản cùng chuỗi theo lineage (spec 004, research R6). id = id ở phiên bản planId, fromId = id ở
 * phiên bản fromPlanId; mục bị bỏ chỉ có fromId, mục mới chỉ có id.
 */
public record YearPlanChangesResponse(
        Long planId,
        int versionNo,
        Long fromPlanId,
        int fromVersionNo,
        List<Goal> goals,
        List<Content> contents,
        List<Theme> themes) {

    public enum Change {
        UNCHANGED,
        CHANGED,
        REMOVED,
        ADDED
    }

    public record Goal(
            Long lineageId,
            Long id,
            Long fromId,
            Change change,
            List<String> fields,
            Short goalNo,
            Short fromGoalNo,
            String text,
            String fromText) {}

    public record Content(
            Long lineageId,
            Long id,
            Long fromId,
            Long goalLineageId,
            Change change,
            List<String> fields,
            String text) {}

    public record Theme(
            Long lineageId, Long id, Long fromId, Change change, List<String> fields, String name, String fromName) {}
}
