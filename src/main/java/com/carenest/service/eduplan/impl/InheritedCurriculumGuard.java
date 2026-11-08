package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduGoalRow;
import com.carenest.entity.eduplan.EduYearPlan;
import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.EduGoalRowKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class InheritedCurriculumGuard {

    private InheritedCurriculumGuard() {}

    static void validateInheritedTree(
            EduYearPlan plan,
            YearPlanRequest request,
            List<EduGoalRow> existingRows,
            Map<Long, List<EduGoalContent>> existingContentsByRowId) {
        if (plan.getCurriculumSourcePlanId() == null) {
            return;
        }

        List<YearPlanRequest.Domain> requestDomains = request.domains() == null ? List.of() : request.domains();
        List<EduGoalRow> sortedExisting = new ArrayList<>(existingRows);
        sortedExisting.sort(Comparator.comparing(EduGoalRow::getDomainId)
                .thenComparing(EduGoalRow::getSortOrder)
                .thenComparing(EduGoalRow::getId));

        int existingRowIndex = 0;
        for (YearPlanRequest.Domain domain : requestDomains) {
            List<YearPlanRequest.Row> rows = domain.rows() == null ? List.of() : domain.rows();
            for (YearPlanRequest.Row requestRow : rows) {
                if (existingRowIndex >= sortedExisting.size()) {
                    throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
                }
                EduGoalRow expectedRow = sortedExisting.get(existingRowIndex++);
                if (!Objects.equals(domain.domainId(), expectedRow.getDomainId())
                        || !Objects.equals(requestRow.id(), expectedRow.getId())) {
                    throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
                }
                if (!Objects.equals(domain.domainId(), expectedRow.getDomainId())) {
                    throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
                }
                EduGoalRowKind requestKind = parseKind(requestRow.kind());
                if (requestKind != expectedRow.getKind()) {
                    throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
                }
                String requestText =
                        requestRow.text() == null ? "" : requestRow.text().trim();
                String expectedText = expectedRow.getText() == null
                        ? ""
                        : expectedRow.getText().trim();
                if (!requestText.equals(expectedText)) {
                    throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
                }
                Long requestCurriculumId = requestRow.curriculumId(requestKind);
                if (!Objects.equals(requestCurriculumId, expectedRow.getCurriculumId())) {
                    throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
                }

                if (expectedRow.getKind() == EduGoalRowKind.GOAL) {
                    if (requestRow.displayLabel() != null) {
                        String label = requestRow.displayLabel().strip();
                        if (!Objects.equals(label.isEmpty() ? null : label, expectedRow.getDisplayLabel())) {
                            throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
                        }
                    }
                    List<EduGoalContent> expectedContents =
                            existingContentsByRowId.getOrDefault(expectedRow.getId(), List.of());
                    List<YearPlanRequest.Content> requestContents =
                            requestRow.contents() == null ? List.of() : requestRow.contents();
                    if (requestContents.size() != expectedContents.size()) {
                        throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
                    }
                    for (int c = 0; c < requestContents.size(); c++) {
                        YearPlanRequest.Content reqContent = requestContents.get(c);
                        EduGoalContent expContent = expectedContents.get(c);
                        if (!Objects.equals(reqContent.id(), expContent.getId())) {
                            throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
                        }
                        String reqContentText = reqContent.text() == null
                                ? ""
                                : reqContent.text().trim();
                        String expContentText = expContent.getText() == null
                                ? ""
                                : expContent.getText().trim();
                        if (!reqContentText.equals(expContentText)) {
                            throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
                        }
                    }
                }
            }
        }

        if (existingRowIndex != sortedExisting.size()) {
            throw GlobalException.of(ApiCode.INHERITED_CURRICULUM_READ_ONLY);
        }
    }

    private static EduGoalRowKind parseKind(String kind) {
        if (kind == null) return EduGoalRowKind.GOAL;
        return switch (kind.trim().toUpperCase()) {
            case "SUB", "SUB_DOMAIN" -> EduGoalRowKind.SUB;
            case "GROUP", "EXPECTED_RESULT" -> EduGoalRowKind.GROUP;
            default -> EduGoalRowKind.GOAL;
        };
    }
}
