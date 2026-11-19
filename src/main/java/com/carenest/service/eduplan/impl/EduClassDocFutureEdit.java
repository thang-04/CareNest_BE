package com.carenest.service.eduplan.impl;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

final class EduClassDocFutureEdit {
    private EduClassDocFutureEdit() {}

    static void requireFuture(LocalDate start, LocalDate today) {
        if (start == null || !start.isAfter(today)) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
    }

    static void validateWeek(ObjectNode before, ObjectNode after, LocalDate start, LocalDate today) {
        validateWeek(before, after, start, today, true);
    }

    /** {@code released}: hồ sơ chủ đề đã từng ban hành ⇒ chỉ sửa phần tương lai (EDU-64); bản chưa ban hành sửa tự do. */
    static void validateWeek(ObjectNode before, ObjectNode after, LocalDate start, LocalDate today, boolean released) {
        if (start == null) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
        if (after.has("days")) {
            if (!after.path("days").isArray() || after.path("days").size() > 5)
                throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
            for (int i = 0; i < after.path("days").size(); i++)
                if (!after.path("days")
                        .path(i)
                        .path("date")
                        .asString("")
                        .equals(start.plusDays(i).toString())) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
        }
        for (String field : Set.of("grid", "picks", "unlinked"))
            if (after.has(field) && !after.path(field).isObject()) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
        if (after.has("dayGoals")
                && (!after.path("dayGoals").isArray() || after.path("dayGoals").size() > 5))
            throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
        if (start.isAfter(today) || !released) return;
        ObjectNode oldShared = before.deepCopy();
        ObjectNode newShared = after.deepCopy();
        oldShared.remove(Set.of("days", "grid", "dayGoals", "picks", "unlinked"));
        newShared.remove(Set.of("days", "grid", "dayGoals", "picks", "unlinked"));
        unchanged(oldShared, newShared);
        for (int i = 0; i < 5; i++) {
            JsonNode oldDay = before.path("days").path(i);
            JsonNode newDay = after.path("days").path(i);
            if (!Objects.equals(oldDay, newDay)) {
                requireFuture(start.plusDays(i), today);
                if (!newDay.path("date").asString("").equals(start.plusDays(i).toString()))
                    throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
            }
        }
        if (after.path("days").size() > 5) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
        if (after.path("dayGoals").size() > 5) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
        for (int i = 0; i < 5; i++)
            if (!sameList(
                    before.path("dayGoals").path(i), after.path("dayGoals").path(i)))
                requireFuture(start.plusDays(i), today);
        for (String field : Set.of("picks", "unlinked"))
            for (String key : keys(before.path(field), after.path(field))) {
                if (sameList(before.path(field).path(key), after.path(field).path(key))) continue;
                String day = key.substring(key.lastIndexOf('.') + 1);
                if (!day.matches("[0-4]")) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
                requireFuture(start.plusDays(Integer.parseInt(day)), today);
            }
        Set<String> rows = keys(before.path("grid"), after.path("grid"));
        for (String row : rows) {
            JsonNode oldRow = before.path("grid").path(row);
            JsonNode newRow = after.path("grid").path(row);
            if (Objects.equals(oldRow, newRow)) continue;
            if (!(oldRow instanceof ObjectNode) || !(newRow instanceof ObjectNode))
                throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
            ObjectNode oldCommon = ((ObjectNode) oldRow).deepCopy();
            ObjectNode newCommon = ((ObjectNode) newRow).deepCopy();
            oldCommon.remove("days");
            newCommon.remove("days");
            unchanged(oldCommon, newCommon);
            if (newRow.path("days").size() > 5) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
            for (int i = 0; i < 5; i++)
                if (!Objects.equals(
                        oldRow.path("days").path(i), newRow.path("days").path(i)))
                    requireFuture(start.plusDays(i), today);
        }
    }

    private static Set<String> keys(JsonNode before, JsonNode after) {
        Set<String> result = new LinkedHashSet<>();
        before.properties().forEach(entry -> result.add(entry.getKey()));
        after.properties().forEach(entry -> result.add(entry.getKey()));
        return result;
    }

    private static void unchanged(JsonNode before, JsonNode after) {
        if (!before.equals(after)) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
    }

    private static boolean sameList(JsonNode before, JsonNode after) {
        return Objects.equals(before, after)
                || (before.isMissingNode() || before.isArray() && before.isEmpty())
                        && (after.isMissingNode() || after.isArray() && after.isEmpty());
    }
}
