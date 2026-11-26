---
title: API bếp trả cảnh báo dị ứng theo món và số trẻ dị ứng theo lớp
status: in-progress
owner: Thắng
jira: G94-494 / BE-FEAT-184
branch: feature/G94-494-BE-FEAT-184-kitchen-allergy-warning
modules: [nutrition, child]
rules: [NUT-23, AUTH-05, HLT-06, CHD-13, NUT-08, NUT-13, NUT-17]
risk: high
created: 2026-11-26
updated: 2026-11-26
---
# API bếp trả cảnh báo dị ứng theo món và số trẻ dị ứng theo lớp

## Context
NUT-23 yêu cầu bếp thấy cảnh báo và số trẻ dị ứng theo món, lớp. AC-4 của plan `2026-11-25-allergy-warning-only.md` (G94-483) mới làm phần bỏ suất thay thế; BE chưa trả cảnh báo. Nguồn yêu cầu App: đề xuất 25/11, mục 3.
- `dto/meals/KitchenDayResponse.java:30`: `MenuDish(id, code, name)`, không có chất gây dị ứng.
- `service/meals/impl/KitchenDayServiceImpl.java:96,115,120`: chỉ trả món khi tuần đã công bố và ngày có học.
- `child_allergies` (V63) chỉ có `child_id`, `name` chữ tự do; không có mã chất, không có mức.
- `foods.allergen_codes` dùng enum `utils/enums/Allergen.java` (9 chất).

## Quyết định đã chốt
1. Khớp tên dị ứng trẻ khai với 9 chất khi đọc; "Cá biển" tính là Cá (17/11). Không migration, không sửa form — Thắng, 2026-11-26 (agent hiểu câu "xem rule bây giờ chỉ cảnh báo thôi" là phương án khớp tên; Thắng đã đọc bản tóm tắt rồi yêu cầu tạo task và plan).
2. Tên không khớp: bếp thấy chữ đã khai và số trẻ theo lớp — Thắng, 2026-11-26.
3. Đếm mọi trẻ báo ăn đang học trong lớp vào ngày xem; không dựa điểm danh — Thắng, 2026-11-26.
4. Không tách mức nhẹ/nặng (dữ liệu chưa có; NUT-23 chỉ giữ làm tham khảo).

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** Actor bếp (HT/PHT gọi được cùng API) · outcome: biết món nào có chất gì, lớp nào có bao nhiêu trẻ dị ứng · NUT-23, AUTH-05, HLT-06, CHD-13, NUT-08 (CONFIRMED) · flow: `[B]` mở Thực đơn bếp, `[S]` trả món + cảnh báo.
- **Đối chiếu thiết kế ban đầu:** HLT-06 yêu cầu danh mục chọn + mức nhẹ/nặng, code chưa làm; plan này không giải quyết. Đề xuất App muốn đếm trẻ có mặt và `severe`/`mild`: khác theo quyết định 3, 4.
- **Ảnh hưởng:** nutrition (Thắng), child (Giáp, thêm 1 hàm đọc) · quyền không đổi · không đổi dữ liệu · `APP:` tab Thực đơn bếp · `FE:` `src/services/kitchenRecordsApi.js` gọi cùng API, chỉ thêm trường.

**Bản tác động**
```text
Thay đổi: thêm trường cảnh báo vào GET /nutrition/kitchen/day; thêm ChildService.findAllergyNames
Ảnh hưởng: KitchenRecordsController#getKitchenDay · MealPhotoWeekServiceImpl:93 (gọi lại service) · Web kitchenRecordsApi.js
Luồng người dùng: bếp/HT/PHT nhận thêm dữ liệu; client cũ không đổi
Module của: nutrition Thắng · child Giáp
Phương án: A thêm hàm đọc ở ChildService · B meals đọc thẳng ChildAllergyRepository (phá ranh giới module) — đề xuất A
Cách kiểm: test khớp tên + test tích hợp bếp + snapshot OpenAPI
(Dùng grep; chưa chạy impact.mjs theo chỉ đạo 07/11 không tự chạy script harness.)
```

### Câu hỏi mở
- [x] Đã báo Giáp và được đồng ý thêm 1 hàm đọc ở module child (Thắng xác nhận 2026-11-26).

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Cách đối chiếu | Khớp tên khi đọc | Thắng | 2026-11-26 | NUT-23 |
| Tên không khớp | Chữ đã khai + số trẻ | Thắng | 2026-11-26 | NUT-23 |
| Tập trẻ đếm | Trẻ báo ăn của lớp | Thắng | 2026-11-26 | NUT-23 |

## Contract (chỉ thêm trường)
```text
menu[].dishes[].allergens          [{ id, code, name }]
menu[].dishes[].allergyWarnings    [{ allergen: { id, code, name }, byClass: [{ classroomId, classroomName, count }] }]
unmatchedAllergies                 [{ name, byClass: [{ classroomId, classroomName, count }] }]
```

## Acceptance criteria
- **AC-1** (NUT-23, NUT-13, NUT-17): Given thực đơn công bố có món chứa Tôm và lớp mẫu giáo có 2 trẻ báo ăn khai "Tôm", When bếp xem ngày, Then món trả `allergens` có SHRIMP và cảnh báo lớp đó `count` 2; lớp khác khối hoặc không ăn bữa đó không có trong `byClass`.
- **AC-2** (NUT-23): Given tên khai khác hoa thường/khoảng trắng, "Cá biển", "Mè" hoặc "Vừng", When đối chiếu, Then ra đúng chất; một trẻ chỉ đếm một lần cho một chất.
- **AC-3** (NUT-23): Given trẻ khai "Mực", When xem ngày, Then `unmatchedAllergies` có "Mực" và số trẻ theo lớp; không gắn vào món nào.
- **AC-4** (CHD-13): Given trẻ không báo ăn hoặc không học lớp đó vào ngày xem, Then không được đếm; trẻ vắng theo điểm danh vẫn được đếm.
- **AC-5** (AUTH-05): Response không có tên hay ID trẻ; chỉ có lớp của điểm trường đang xem.
- **AC-6** (NUT-08): Given tuần chưa công bố hoặc ngày nghỉ, Then `menu` và `unmatchedAllergies` rỗng.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Nơi tính cảnh báo | Class mới trong meals; child chỉ trả tên dị ứng | Tính trong child | Cảnh báo theo món là nghiệp vụ meals |
| Bộ khớp tên | Class trong `service/meals/impl` | Thêm vào enum `Allergen` | `utils/**` là vùng dùng chung |
| Luật khớp | Chuẩn hóa Unicode NFC, bỏ khoảng trắng thừa, không phân biệt hoa thường, giữ dấu; bí danh: "cá biển", "mè", "vừng" | Bỏ dấu, khớp một phần | Bỏ dấu làm lẫn chất; tên lạ vẫn hiện nguyên chữ nên không mất thông tin |
| Kiểu chất gây dị ứng | Dùng lại `AllergenResponse` | DTO mới `{code, name}` | Cùng dạng với chi tiết món |
| Lớp xét | `findActiveYearClassrooms()` lọc theo điểm trường và ngày nằm trong năm | Tìm năm bất kỳ theo ngày | Không phải sửa module tổ chức; ngày ngoài năm đang hoạt động trả rỗng |
| Ảnh tuần gọi lại `getKitchenDay` | Chấp nhận thêm truy vấn (5 ngày) | Tách hàm không tính cảnh báo | Ít file; tối ưu khi thấy chậm |

## Phases
### Phase 1 — Tính và trả cảnh báo
- Files: C `service/meals/impl/KitchenAllergyWarnings.java` · M `dto/meals/KitchenDayResponse.java`, `service/meals/impl/KitchenDayServiceImpl.java`, `service/child/ChildService.java`, `service/child/impl/ChildServiceImpl.java` · C `test/.../KitchenAllergyWarningsTest.java` · M `test/.../KitchenRecordsIntegrationTest.java` (7 file)
- Steps:
  1. `ChildService.findAllergyNames(Collection<Long>)` trả `Map<childId, List<String>>`, dùng `ChildAllergyRepository.findAllByChildIdInOrderByIdAsc` (dòng 16).
  2. `KitchenAllergyWarnings`: lớp từ `ClassroomService.findActiveYearClassrooms`; trẻ từ `EnrollmentService.findChildIdsInClassrooms` (dòng 29) trừ `findChildIdsWithoutMeals`; khối từ `MealDemandCalculator.blockOfAgeGroup` (dòng 121); bữa từ `ClassroomInfoResponse.mealSessions`.
  3. Món nạp bằng `DishRepository.findByIdIn` (đã kèm nguyên liệu, thực phẩm); chất của món gộp mọi nguyên liệu.
  4. Log chỉ ghi id và số lượng, không ghi tên dị ứng.
- Tests: AC-2, AC-3 → `KitchenAllergyWarningsTest`; AC-1, AC-4, AC-5, AC-6 → `KitchenRecordsIntegrationTest` (`@DisplayName` mở đầu bằng rule ID).
- Exit: `./mvnw -o test -Dtest='KitchenAllergyWarningsTest,KitchenRecordsIntegrationTest,ArchitectureRulesTest'` → 0 fail, 0 skip (cần Docker).
- **Dừng chờ user review.**

### Phase 2 — Contract và tài liệu
- Files: M `docs/api/openapi.yaml`, `docs/modules/nutrition.md`, `docs/modules/child.md`
- Steps: 1. cập nhật snapshot bằng `-Dopenapi.snapshot.update=true`, xem diff chỉ thêm trường. 2. ghi API và hàm đọc mới. 3. nhắn Giáp contract và việc App không đọc `substitute`.
- Exit: `./mvnw -o test -Dtest='OpenApiSnapshotTest'` → 0 fail.
- **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | NUT-23, NUT-13, NUT-17 | `KitchenRecordsIntegrationTest` (khối), `KitchenAllergyWarningsTest` (bữa) | 1 | pass |
| AC-2, AC-3 | NUT-23 | `KitchenAllergyWarningsTest` (unit) | 1 | pass |
| AC-4..6 | CHD-13, AUTH-05, NUT-08 | `KitchenRecordsIntegrationTest` (tích hợp) | 1 | pass |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Thực phẩm chưa gắn chất, hoặc tên khai sai | Thiếu cảnh báo dù trẻ dị ứng | Trả `unmatchedAllergies`; App không ghi "an toàn" khi rỗng | — |
| Chữ tự do chứa ghi chú riêng; lớp ít trẻ dễ đoán ra trẻ | Lộ thông tin sức khỏe cho bếp | Không trả tên/ID; user chấp nhận 26/11 | Revert commit |
| Khai báo và cờ báo ăn là giá trị hiện tại | Xem ngày cũ không phản ánh lúc đó | Ghi trong tài liệu API | — |
| App đếm khác kỳ vọng (gồm trẻ vắng) | Bếp thấy nhiều hơn thực tế | Nhãn "trẻ báo ăn" | — |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể (APP tab Thực đơn bếp; FE `kitchenRecordsApi.js`; `MealPhotoWeekServiceImpl`)
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] Phase 1: 7 file, 2 phase
- [x] Rule dùng đều CONFIRMED
- [x] Tự review: không placeholder, không mâu thuẫn

Khác quy trình: không lập Domain spec/Spec Kit vì đây là phần còn thiếu của AC-4 đã duyệt ở G94-483.

## Progress log
### 2026-11-26 — Soạn plan (chờ duyệt)
- Commit: chưa commit
- Verify: chưa chạy (chỉ tài liệu)
- Tiếp: user trả lời câu hỏi mở và duyệt plan.

### 2026-11-26 — Plan approved
- Thắng duyệt plan, xác nhận Giáp đồng ý; bắt đầu Phase 1.

### 2026-11-26 — Phase 1 (chờ review)
- Commit: chưa commit
- Test trước code: `./mvnw -o -q test -Dtest='KitchenAllergyWarningsTest,KitchenRecordsIntegrationTest'` → 34 test, 14 fail, 0 error (đúng dự kiến). Test AC-6 và 5 ca "không tự khớp" đã pass với khung rỗng.
- Verify (Maven trực tiếp theo chỉ đạo 07/11, không chạy `scripts/verify.mjs`): `./mvnw -o spotless:apply` rồi `./mvnw -o test -Dtest='KitchenAllergyWarningsTest,KitchenRecordsIntegrationTest,ArchitectureRulesTest'` → `Tests run: 48, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Chưa chạy: toàn bộ suite; `OpenApiSnapshotTest` (contract đã đổi, snapshot cập nhật ở Phase 2). Chưa có review độc lập.
- Khác plan: không.
- Tiếp: Thắng xem Phase 1; duyệt thì làm Phase 2.

### 2026-11-26 — Phase 2 và kiểm toàn bộ (chờ commit)
- Thắng duyệt làm hết các phase, yêu cầu kiểm ảnh hưởng Web (2026-11-26).
- Commit: commit `G94-494` của nhánh (Thắng cho commit và tạo PR 2026-11-26)
- Verify: `./mvnw -o test -Dtest='OpenApiSnapshotTest' -Dopenapi.snapshot.update=true` → 4 test pass; diff `openapi.yaml` 41 dòng thêm, 0 dòng xóa. `./mvnw -o spotless:check test` → `Tests run: 2690, Failures: 0, Errors: 0, Skipped: 3`, `BUILD SUCCESS` (3 test bỏ qua là LiveEval AI; Surefire báo kill JVM do không thoát sau 30 giây).
- Web (đọc code `CareNest_FE` `origin/dev` `2ea75267`, chưa mở trình duyệt): chỉ `kitchenRecordsApi.getKitchenDay` gọi API (màn `/kitchen/published-menu`); đọc `dish.id`, `dish.name`, không dùng trường mới, không đổi trường cũ ⇒ không cần sửa FE.
- Khác plan: tin nhắn contract cho Giáp soạn để Thắng gửi, agent không gửi.
- Còn lại: commit, PR vào `dev`, review của thành viên khác.
