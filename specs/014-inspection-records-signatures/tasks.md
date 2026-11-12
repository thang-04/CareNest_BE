# Tasks — Biên bản kiểm thực, chữ ký ảnh, trường lưu mẫu

Nguồn: plan `docs/plans/active/2026-11-10-inspection-records-signatures.md`, `data-model.md`, domain spec. Mỗi phase dừng chờ user review. Trạng thái: `[ ]` chưa làm.

## Phase 1 — Chữ ký tài khoản (SIG-01, AC-8)

- [x] T101 Migration `V84__create_user_signatures.sql` theo `data-model.md` (bảng, chỉ mục mặc định duy nhất, ràng buộc).
- [x] T102 Entity `UserSignature`, `UserSignatureRepository` (đếm theo tài khoản, khóa dòng tài khoản khi thêm).
- [x] T103 `ApiCode` mã lỗi chữ ký 1140 (ảnh sai), 1141 (quá 5), 1142 (không tìm thấy); người ngoài chủ ảnh nhận 1142.
- [x] T104 `UserSignatureService`/`Impl`: liệt kê, tải lên (sniff loại, ≤2 MB, tối đa 5, mặc định đầu tiên), đặt mặc định, xóa (xóa tệp sau commit), đọc nội dung (chủ ảnh).
- [x] T105 `UserSignatureController` `GET|POST /signatures/me`, `PATCH /signatures/me/{id}/default`, `DELETE /signatures/me/{id}`, `GET /signatures/me/{id}/content` (đổi đường dẫn so với bản đầu); log theo quy ước controller/service.
- [x] T106 Test service (8) và controller (5): đúng/sai loại, quá cỡ, quá 5, mặc định, xóa, quyền tải — `mvnw -o test` pass 13/13.
- [x] T107 Cập nhật snapshot OpenAPI, ArchUnit, Spotless; `node scripts/verify.mjs` → `VERIFY PASS full | tests 1254 fail 0` (2026-11-10, sau lát 2b).

## Phase 2 — Biên bản kiểm thực (INS-01..04, AC-1..AC-7)

### Lát 2a — biên bản, ký, M2/M3/M5 (xong, chưa commit)

- [x] T201 Migration `V85__create_inspection_sheets.sql`: bảng `inspection_sheets` và cột `fresh_checks.supplier_id` (cột đã có, entity dùng ở T202).
- [x] T203 `InspectionColumns`: bảng cột từng mẫu (khóa ô, kiểu, ô hệ thống điền), kiểm và làm sạch ô nhập, đếm dòng thiếu Đạt/Không đạt.
- [x] T205 Entity `InspectionSheet` + `InspectionJson` (converter jsonb), `InspectionSheetRepository` (khóa dòng điểm trường, tìm ảnh trong biên bản).
- [x] T206 `InspectionService`/`Impl`: `getDay`, `save` (409 khi đã ký, 409 khi lệch phiên bản, kiểm ô), `sign` (đủ cảm quan M1–M3, ảnh từ `UserSignatureService.findForSigning`, chép sang `InspectionSignatureStorage`, chụp dòng, lịch sử), `reopen` (lý do ≥3, lịch sử), `getSignatureImage` (ảnh theo điểm trường, kể cả ảnh trong lịch sử).
- [x] T207 `InspectionController` `GET /inspections/day`, `PUT /inspections/day/{tab}`, `POST /inspections/day/{tab}/sign`, `POST /inspections/day/{tab}/reopen`, `GET /inspections/signatures/{imageKey}`; chỉ PRINCIPAL/VICE_PRINCIPAL. Đường ảnh dùng mã tệp thay `{id}` vì ảnh nằm trong jsonb, không có id riêng.
- [x] T208 `ApiCode` 6080–6089 (không quyền, đã ký, lệch phiên bản, chưa lưu, thiếu cảm quan, thiếu chữ ký, lý do, chưa ký, ngày chưa tới, ô sai).
- [x] T204a `InspectionSkeleton` cho M2/M3 (thực đơn công bố, suất đã xác nhận tách Nhà trẻ / Mẫu giáo theo khối thực đơn của nhóm tuổi) và M5 (`food_samples`).
- [x] T209a Test: `InspectionServiceImplTest` (12), `InspectionIntegrationTest` (5, Postgres thật: khung đúng thực đơn/suất tách khối, jsonb, ký, bản chụp không đổi khi nguồn đổi, sửa lại dựng lại từ nguồn mới, ảnh theo điểm trường, quyền), `InspectionControllerTest` (5). OpenAPI snapshot cập nhật (chỉ thêm).
### Lát 2b — M1 và nhà cung cấp trên kiểm nhận (xong, chưa commit)

- [x] T204b `InspectionIntakeSkeleton`: M1 tươi từ kiểm nhận hàng tươi trong ngày (lần 2 nếu đã ghi, không thì lần 1) kèm nhà cung cấp, giờ kiểm; M1 khô từ các lô đã trừ kho cho bữa ăn của ngày (`stock_movements.meal_date`) kèm nhà cung cấp, ngày nhập, hạn dùng; khối lượng đổi g/ml sang kg/lít.
- [x] T202 Kiểm nhận hàng tươi: `FreshCheck.supplierId`, `FreshCheckRequest.supplierId` (không bắt buộc, không tồn tại ⇒ `SUPPLIER_NOT_FOUND`), `FreshCheckResponse.FreshCheckRound.supplier` (không có CCCD).
- [x] T209b Test M1 tươi và khô (`InspectionIntegrationTest`), nhà cung cấp trên kiểm nhận (`FreshGoodsIntegrationTest`). Test ghi đồng thời hai luồng cho biên bản: `InspectionConcurrencyIntegrationTest` (một OK, một 6082, một dòng, version 1).
- [x] T210 Snapshot OpenAPI, ArchUnit, Spotless; `verify.mjs` → `VERIFY PASS full | tests 1254 fail 0` (2026-11-10).

## Phase 3 — Trường lưu mẫu (NUT-21, AC-9)

- [x] T301 Migration `V86__food_sample_dish_details.sql` (`food_samples.dish_details` jsonb, mặc định `[]`).
- [x] T302 `FoodSample` + `FoodSampleDishDetail` + converter, `FoodSampleRequest.dishDetails[{dishId, weight, container, temperature}]` (không bắt buộc; null khi sửa ⇒ giữ), `FoodSampleResponse.dishes[].weight|container|temperature` (rỗng khi chưa ghi); M5 điền sẵn ba cột từ các trường này.
- [x] T303 Test: `KitchenRecordsIntegrationTest.sampleDishDetails` (ghi/đọc, bản không gửi, giữ khi sửa, xóa bằng mảng rỗng, món lạ/trùng), `InspectionIntegrationTest` (M5 điền sẵn), `FoodSampleRequestJsonTest` (JSON cũ/mới, độ dài). `verify.mjs` xem Progress log.
- [x] T304 Plan APP: ghi ở mục "Việc cho App" của plan BE (không sửa repo APP).

## Phase 4 — Hoàn tất

- [x] T401 Module card nutrition, identity-access, `CURRENT_STATE.md`, `CROSS_MODULE_ISSUES.md` (GAP-261110, FE đổi từ dữ liệu mẫu sang API) cập nhật 10/11; `check-ai-layer` BE 0 lỗi. `docs/contracts` không đổi: `ERROR_CONTRACT.md` không liệt kê mã theo dãy 60xx/11xx, mã mới nằm ở `ApiCode` và snapshot OpenAPI.
- [ ] T402 Converge: so sánh code với spec/plan/tasks, append task mới nếu còn gap.
