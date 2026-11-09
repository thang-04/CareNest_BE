package com.carenest.service.eduplan.impl;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Map;
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
        if (start == null) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
        if (after.has("days")) {
            if (!after.path("days").isArray() || after.path("days").size() > 5)
                throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
            for (int i = 0; i < after.path("days").size(); i++)
                if (!after.path("days").path(i).path("date").asString("").equals(start.plusDays(i).toString()))
                    throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
        }
        for (String field : Set.of("grid", "picks", "unlinked"))
            if (after.has(field) && !after.path(field).isObject()) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
        if (after.has("dayGoals") && (!after.path("dayGoals").isArray() || after.path("dayGoals").size() > 5))
            throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
        if (start.isAfter(today)) return;
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
            if (!sameList(before.path("dayGoals").path(i), after.path("dayGoals").path(i)))
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
                if (!Objects.equals(oldRow.path("days").path(i), newRow.path("days").path(i)))
                    requireFuture(start.plusDays(i), today);
        }
    }

    static Set<Long> validateBranch(
            ObjectNode before, ObjectNode after, Map<LocalDate, Long> weeks, LocalDate today) {
        Set<Long> affected = new LinkedHashSet<>();
        for (String field : Set.of("names", "notes")) {
            for (String key : keys(before.path(field), after.path(field))) {
                if (Objects.equals(before.path(field).path(key), after.path(field).path(key))) continue;
                LocalDate date = date(key);
                requireFuture(date, today);
                Long theme = weeks.get(date);
                if (theme == null) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
                affected.add(theme);
            }
        }
        var oldBranches = byKey(before.path("branches"));
        var newBranches = byKey(after.path("branches"));
        Set<String> branchKeys = new LinkedHashSet<>(oldBranches.keySet());
        branchKeys.addAll(newBranches.keySet());
        for (String key : branchKeys) {
            JsonNode oldBranch = oldBranches.get(key);
            JsonNode newBranch = newBranches.get(key);
            if (Objects.equals(oldBranch, newBranch)) continue;
            for (JsonNode branch : new JsonNode[] {oldBranch, newBranch}) {
                if (branch == null) continue;
                long theme = branch.path("themeId").asLong();
                if (!weeks.containsValue(theme)) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
                String start = branch.path("startDate").asString("");
                LocalDate sourceStart = weeks.entrySet().stream().filter(entry -> entry.getValue() == theme)
                        .map(Map.Entry::getKey).min(LocalDate::compareTo).orElse(null);
                requireFuture(start.isBlank() ? sourceStart : date(start), today);
                affected.add(theme);
            }
        }
        ObjectNode oldShared = before.deepCopy();
        ObjectNode newShared = after.deepCopy();
        oldShared.remove(Set.of("names", "notes", "branches"));
        newShared.remove(Set.of("names", "notes", "branches"));
        if (!oldShared.equals(newShared)) {
            if (weeks.isEmpty()) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
            // EDU-64: đổi cấu trúc chung chỉ khi toàn bộ phần được áp dụng còn ở tương lai.
            weeks.keySet().forEach(day -> requireFuture(day, today));
            affected.addAll(weeks.values());
        }
        return affected;
    }

    private static Map<String, JsonNode> byKey(JsonNode branches) {
        Map<String, JsonNode> result = new java.util.LinkedHashMap<>();
        for (JsonNode branch : branches) {
            String key = branch.path("key").asString("");
            if (key.isBlank() || result.put(key, branch) != null) throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
        }
        return result;
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            throw GlobalException.of(ApiCode.CLASS_DOC_LOCKED);
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
