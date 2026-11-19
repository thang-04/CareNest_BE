---
id: BUG-261118-record-primitive-breaks-stored-json
type: bug
modules: [child, observation]
rules: [CHD-13]
status: fixed
date: 2026-11-18
keywords: [Jackson 3, FAIL_ON_NULL_FOR_PRIMITIVES, record, primitive, LEGACY_BASELINE, convertValue]
similar_to: [BUG-261012-web-auth-oauth-error-mapping]
---

# BUG-261118-record-primitive-breaks-stored-json — Component nguyên thủy mới làm hỏng đọc JSON đã lưu

## Symptom
Final review nhánh Báo ăn: thêm `boolean mealRegistered` vào `ClassChildResponse` làm `ThemeEvaluationServiceImpl` (convertValue snapshot) lỗi 500 với snapshot V97 cũ.
```text
MismatchedInputException: Cannot map `null` into type `boolean`
```

## Điều kiện tái hiện
`[{"id":1,"fullName":"An","dateOfBirth":"2022-04-15"}]` → `List<ClassChildResponse>` với mapper mặc định Boot 4.

## Attempts
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Giữ component, đổi thành `Boolean` | Không chọn | Mở rộng DTO dùng chung cho 1 consumer |
| 2 | Trả DTO về 3 component, điểm danh lấy cờ qua `ChildService.findChildIdsWithoutMeals` | Chọn | JSON đã lưu không thể có thêm key |

## Root cause
Jackson 3 (`tools.jackson`) mặc định `FAIL_ON_NULL_FOR_PRIMITIVES=true`; key thiếu của creator property nguyên thủy bị coi là null ⇒ lỗi.

## Fix
`ClassChildResponse` về 3 component; cờ báo ăn lấy từ module child (commit "Read attendance meal flag from child records").

## Regression test
`ClassChildResponseJsonTest` (src/test/java/com/carenest/dto/child).

## Ảnh hưởng
Theme evaluation / semester assessment / year-end sheet đọc snapshot cũ.

## Lesson
Không thêm component nguyên thủy vào record được đọc lại từ JSON đã lưu (snapshot, JSONB); dùng wrapper (`Boolean`, null = mặc định) hoặc giữ nguyên DTO.
