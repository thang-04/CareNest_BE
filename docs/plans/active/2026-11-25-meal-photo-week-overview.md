---
title: Ảnh bữa ăn xem theo tuần, kèm người đăng và thông tin đối chiếu
status: in-progress
owner: Thắng
jira: G94-460 / BE-FEAT-02
branch: feature/G94-460-BE-FEAT-02-meal-photo-week
modules: [nutrition]
rules: [NUT-16, NUT-21, INS-01, INS-03]
risk: medium
created: 2026-11-25
updated: 2026-11-25
---
# Ảnh bữa ăn xem theo tuần, kèm người đăng và thông tin đối chiếu

## Context
HT và PHT phụ trách dinh dưỡng mở màn Ảnh bữa ăn để rà bếp có đăng đủ ảnh không và món thực tế có khớp thực đơn không. Màn hiện tại chỉ xem được một ngày một lần (`CareNest_FE: src/pages/kitchen/MealPhotosPage.jsx:43`), `GET /nutrition/meal-photos` chỉ nhận một ngày (`KitchenRecordsController.java:175`), `MealPhotoResponse` chỉ có `uploadedBy` là id. Ngày không có ảnh chỉ hiện "Chưa có ảnh", không gợi ý ngày nào có. Anh Thắng chọn hướng A (dải tuần + 3 bữa của ngày chọn, mockup 25/11/2026) và cách 2 (một endpoint tổng hợp cho cả tuần).

## Quyết định đã chốt
1. Bố trí màn: dải 5 ngày của tuần (chấm xanh/đỏ theo từng bữa) + thẻ 3 bữa của ngày chọn — Thắng, 25/11/2026.
2. Một endpoint tổng hợp mới cho cả tuần, BE tính "thiếu ảnh", "đúng/trễ giờ" — Thắng, 25/11/2026.
3. Hiện tên người đăng — Thắng, 25/11/2026.
4. Làm đủ ngăn chi tiết: thực đơn, số suất, lưu mẫu thức ăn, biên bản kiểm thực — Thắng, 25/11/2026.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** actor HT, PHT · xem để giám sát, không sửa · NUT-16 (ảnh theo khung giờ), NUT-21 (lưu mẫu), INS-01/03 (biên bản kiểm thực) đều CONFIRMED · flow `[B]` dinh dưỡng.
- **Đối chiếu thiết kế ban đầu:** NUT-16 nói bếp đăng ảnh trong khung giờ HT cấu hình; chưa có rule nào định nghĩa "thiếu ảnh" ⇒ đây là câu hỏi mở bên dưới.
- **Ảnh hưởng:** module nutrition (chỉ đọc, thêm 1 endpoint) · quyền: HT/PHT theo `KitchenAccess.resolveReadCampus` (bếp không dùng màn Web này) · `FE: /kitchen/meal-photos` · App không đổi · dữ liệu cũ không đổi, không migration.

### Câu hỏi mở
- [x] "Thiếu ảnh" định nghĩa thế nào? Đề xuất: bữa có trong thực đơn đã công bố của ngày học (không phải ngày nghỉ) và đã hết khung đăng ảnh của bữa đó mà chưa có ảnh. Hôm nay còn trong khung ⇒ "chờ đăng"; ngày tương lai ⇒ không tính. Thắng duyệt nguyên văn đề xuất, 25/11/2026.
- [x] Mã Jira của việc này (BE và FE) là gì? Trả lời: G94-460.

## Acceptance criteria
- **AC-1** (NUT-16): Given tuần có ảnh ở một số bữa When HT/PHT gọi `GET /nutrition/meal-photos/week?weekStart&campusId` Then trả 5 ngày thứ 2–6, mỗi bữa có trạng thái `POSTED | MISSING | PENDING | NOT_EXPECTED`.
- **AC-2** (NUT-16): Given ảnh đã đăng When xem Then có `uploaderName`, `uploadedAt`, và `onTime` (true/false, null khi chưa cấu hình khung giờ của bữa).
- **AC-3** (NUT-16): Given bữa có trong thực đơn công bố, đã hết khung giờ, chưa có ảnh When xem Then `MISSING`; còn trong khung hôm nay ⇒ `PENDING`; ngày nghỉ hoặc bữa không có trong thực đơn ⇒ `NOT_EXPECTED`.
- **AC-4**: Given ngày học When xem Then mỗi bữa có tên các món đã công bố và số suất đã chốt (thường/thay thế).
- **AC-5** (NUT-21): Given bữa có sổ lưu mẫu When xem Then có trạng thái ghi và trạng thái hủy; bữa chưa ghi ⇒ không có mục này.
- **AC-6** (INS-01, INS-03): Given ngày có biên bản When xem Then mỗi mẫu có `NONE | DRAFT | SIGNED`, tên người ký và giờ ký khi đã ký.
- **AC-7**: Given tài khoản bếp When gọi endpoint tuần Then 403 ở controller (`@PreAuthorize` chỉ HT/PHT); Given bếp điểm trường khác hoặc tài khoản ngoài điểm trường When gọi qua service Then bị từ chối như `GET /meal-photos`.
- **AC-8**: Given `weekStart` không phải thứ 2 When gọi Then chuẩn hóa về thứ 2 của tuần đó; bỏ trống ⇒ tuần hiện tại.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Cách lấy thực đơn, số suất, ngày nghỉ | Gọi `KitchenDayService.getKitchenDay` cho từng ngày | Dựng lại truy vấn riêng | Không nhân bản luật ngày nghỉ và công bố (`KitchenDayServiceImpl:96`) |
| Lưu mẫu, biên bản | Thêm hàm đọc theo khoảng ngày ở repository của chính module | Gọi `InspectionService.getDay` mỗi ngày | `getDay` dựng cả khung dòng, nặng và thừa |
| Tên schema OpenAPI | Record lồng đặt tên duy nhất (`Week*`) | Tên ngắn `Day`, `Session`... | `Day` đụng schema thực đơn tuần, snapshot trỏ nhầm | 
| Tên người đăng | `AccountQueryService.getDisplayNames` | Join bảng tài khoản | Đúng ranh giới module (ArchUnit) |
| `onTime` | So giờ đăng với khung giờ hiện hành của bữa | Lưu cờ khi đăng | Không cần migration; nếu HT đổi khung sau này, cờ cũ có thể đổi theo (ghi ở Rủi ro) |
| Vai trò người đăng | Không hiện, chỉ tên | Tra phân công | Tên đủ để HT/PHT nhận ra; giảm phụ thuộc |

## Phases
### Phase 1 — BE endpoint tuần
- Files: C `dto/meals/MealPhotoWeekResponse.java` · C `service/meals/MealPhotoWeekService.java` · C `service/meals/impl/MealPhotoWeekServiceImpl.java` · M `controller/meals/KitchenRecordsController.java` · M `repository/meals/InspectionSheetRepository.java` · M `docs/api/openapi.yaml`, `docs/api/API_INDEX.md` · M `KitchenRecordsIntegrationTest.java` (dùng lại fixture)
- Steps: 1. DTO. 2. Service ghép ảnh, khung giờ, tên, thực đơn, suất, mẫu, biên bản. 3. Controller + log theo quy ước. 4. Test tích hợp AC-1..AC-8. 5. Cập nhật snapshot OpenAPI.
- Tests: AC-1..AC-8 → `KitchenRecordsIntegrationTest` (`@DisplayName("NUT-16: …")`).
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0
- **Dừng chờ user review, rồi PR vào `dev`.**

### Phase 2 — FE màn Ảnh bữa ăn (repo CareNest_FE, plan riêng ở FE)
- Files: M `src/pages/kitchen/MealPhotosPage.jsx` · C `src/components/kitchen/MealPhotoWeekStrip.jsx`, `MealPhotoSessionCard.jsx`, `MealPhotoDetail.jsx` · M `src/services/kitchenRecordsApi.js`, `src/hooks/kitchen/useKitchenRecords.js` · M `src/styles/modules/kitchen-records.css` · test trong `tests/`
- Steps: dải tuần (‹ tuần trước, tuần sau ›, chọn tuần) · thẻ 3 bữa kèm người đăng, đúng/trễ giờ · ngăn chi tiết (thực đơn, suất, lưu mẫu, biên bản) · ảnh chỉ tải cho ngày đang chọn · giữ ô chọn điểm trường · đủ trạng thái loading/empty/error/403.
- Exit: `node scripts/verify.mjs` pass, mở màn kiểm tay.
- **Dừng chờ user review.** Phụ thuộc: Phase 1 đã deploy dev.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1..6, 8 | NUT-16, NUT-21, INS-01/03 | `KitchenRecordsIntegrationTest#weekOverview*`, `#weekStartNormalizedAndScoped` | 1 | pass |
| AC-7 (service) | NUT-16 | `KitchenRecordsIntegrationTest#weekStartNormalizedAndScoped` | 1 | pass |
| AC-7 (403 bếp) | NUT-16 | chưa có test controller | 1 | chưa kiểm |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Gọi `getKitchenDay` 5 lần mỗi request | Chậm với tuần nhiều dữ liệu | Đo thời gian trong test; chỉ gọi cho ngày học đã qua hoặc hôm nay | Bỏ endpoint, FE về màn cũ |
| Đổi khung giờ đăng ảnh sau này | `onTime` của ảnh cũ đổi theo | Ghi chú trên màn "theo khung giờ hiện hành" | Không cần |
| Seed dev khác dữ liệu thật | Màn dev trông đầy đủ hơn thật | Ghi rõ ở báo cáo | Không cần |

## Validation log
- [ ] Khẳng định về code có `file:line`
- [ ] Đổi contract ⇒ consumer cụ thể: `FE: /kitchen/meal-photos` (`MealPhotosPage.jsx`, `kitchenRecordsApi.js`); App không gọi endpoint mới
- [ ] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [ ] Không dùng rule PENDING/OPEN như đã chốt
- [ ] Tự review: không placeholder · không mâu thuẫn · scope vừa 1 plan

## Progress log
### 2026-11-25 — Duyệt plan
- Người duyệt: Thắng. Jira G94-460. Nhánh `feature/G94-460-BE-FEAT-02-meal-photo-week` tạo từ `origin/dev`.
- Mã công việc `BE-FEAT-02` do agent đặt, chờ user xác nhận khi mở PR.

### 2026-11-25 — Phase 1 (chờ review)
- Commit: chưa commit
- Verify: `./mvnw -Dtest=KitchenRecordsIntegrationTest,ArchitectureRulesTest,OpenApiSnapshotTest test` → exit 0; `spotless:check` sạch. Chưa chạy `node scripts/verify.mjs` và cả bộ test.
- Khác plan: AC-7 sửa lời (bếp bị 403 ở controller, chưa có test controller); test dùng lại `KitchenRecordsIntegrationTest` thay vì file mới; record lồng đổi tên `Week*` vì `Day` đụng schema thực đơn tuần.
- Tiếp: chờ user review, rồi PR vào `dev`; Phase 2 (FE) sau khi Phase 1 lên dev.
