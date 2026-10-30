# Contract: Danh mục Chương trình GDMN

Quyền: PRINCIPAL, VICE_PRINCIPAL, TEACHER (như `GET /education-plans/config`). Envelope `{code, errorCode, desc, data}`.

## GET /api/education-plans/curriculum/sources

`data`: `[{ id, code, title, documentNo, issuedDate, issuer, officialUrl, retrievedFromUrl, retrievedAt, scopeNote }]` (chỉ nguồn active).

## GET /api/education-plans/curriculum/goals?ageGroupId={id}

`data`: `[{ id, sourceId, domainId, domainName, subDomain, groupNo, groupText, goalNo, goalText, sectionRef, sortOrder, contents: [string] }]` theo `sortOrder`.
Nhóm tuổi không tồn tại ⇒ 404 `AGE_GROUP_NOT_FOUND` (2202). Không có dữ liệu ⇒ `[]`.

## GET /api/education-plans/curriculum/contents?ageGroupId={id}

`data`: `[{ id, sourceId, domainId, domainName, subDomain, categoryNo, category, label, text, sectionRef, sortOrder }]`.

## Kế hoạch năm (thay đổi tương thích)

- `YearPlanRequest.Row` thêm `curriculumGoalId` (Long, tùy chọn; chỉ dùng cho `kind=goal`).
- `YearPlanDetailResponse.Row` thêm `curriculumGoalId`, `curriculumGoalText` (câu gốc của Bộ), `curriculumSectionRef`.
- Lưu với `curriculumGoalId` không thuộc nhóm tuổi của kế hoạch ⇒ 400 `CURRICULUM_GOAL_INVALID` (7030) "Mục tiêu của Bộ không thuộc nhóm tuổi của kế hoạch".
