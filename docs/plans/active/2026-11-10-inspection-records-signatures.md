---
title: Biên bản kiểm thực ba bước (M1–M3), sổ lưu mẫu (M5) và chữ ký ảnh — phần BE
status: approved
owner: carenestg94@gmail.com
jira: G94-192 / BE-FEAT-27   # user chốt 2026-11-10: luồng vẫn BE-FEAT-27 (nhánh bán trú), 192 là số task Jira → commit "G94-192: …"; prefix G94 suy từ các mã Jira khác, chưa user xác nhận
branch: feature/G94-254-BE-FEAT-27-day-boarding   # user chọn làm ở nhánh nutrition (day-boarding), 2026-11-10; worktree CareNest_BE-day-boarding
modules: [nutrition, identity-access]
rules: [NUT-21, AUTH-20, AUTH-21, AUTH-22, INS-01, INS-02, INS-03, INS-04, SIG-01]
risk: high
created: 2026-11-10
updated: 2026-11-10
---

# Biên bản kiểm thực ba bước (M1–M3), sổ lưu mẫu (M5) và chữ ký ảnh — phần BE

Plan liên repo: BE là chủ contract. Domain spec: `specs/014-inspection-records-signatures/domain-spec.md`. Phần Web: `CareNest_FE:docs/plans/active/2026-11-09-food-sample-records-signatures.md` (đã làm màn chạy bằng dữ liệu mẫu theo contract bên dưới). Phần App bếp: plan APP riêng (thêm 3 trường lưu mẫu) — chưa lập.

Trạng thái: plan đã được user duyệt 2026-11-10 (các câu hỏi mở đã trả lời). Phase 0 (tài liệu, rule) đã làm; **chưa có code**, chưa tạo migration.

## Context

- Yêu cầu user 2026-11-09: làm các màn văn bản lưu mẫu như PMS để xử lý biên bản và xuất văn bản; ký bằng ảnh chữ ký của người ký, lưu kèm văn bản; PHT điền M1–M3, bếp ghi lưu mẫu. Quyết định thêm 2026-11-10: biên bản đã ký bị khóa, sửa lại mọi ngày kèm lý do và lịch sử; ký cần chọn đủ Đạt / Không đạt.
- Hiện trạng BE: bản ghi lưu mẫu chỉ có món, giờ lấy, ghi chú, trạng thái duyệt/hủy (`src/main/resources/db/migration/V61__create_kitchen_records.sql:3-35`); duyệt/hủy ở `FoodSampleServiceImpl`; kiểm nhận hàng tươi (`V60`), phiếu nhập kho và lô (`V56`); ảnh bữa ăn dùng kho tệp qua `StorageService` và nhận diện loại ảnh theo byte đầu `ImageFiles.sniff` (`src/main/java/com/carenest/utils/ImageFiles.java:8`). Không có chữ ký ảnh, kiểm thực M1–M3, hay các trường Mẫu 5.
- Rule hiện hành xung đột: NUT-21 giữ chữ ký trên giấy ngoài hệ thống (`docs/business/BUSINESS_RULES.md:124`); P-26, P-27 còn mở (`:369-370`).
- Nhánh làm việc `feature/G94-254-BE-FEAT-27-day-boarding` đã gộp bảng bán trú ở `V83__consolidate_boarding_tables.sql` (ERD gọn, chấp nhận jsonb/mảng thay bảng con nhỏ — `docs/plans/active/2026-11-09-boarding-table-consolidation.md`): `food_sample_dishes` đã thành `food_samples.dish_ids bigint[]`, lô kho khô là `stock_receipt_lines`, ngày của thực đơn tuần nằm trong `meal_plan_weeks.days` jsonb. Migration của việc này bắt đầu từ **V84**; nhánh auth đang ở V82 và phải merge vào `dev` trước (Flyway không chạy V82 sau V83), kiểm lại số ngay trước khi tạo.

## Quyết định đã chốt

1. PHT điền M1–M3, bếp ghi lưu mẫu — user, 2026-11-09.
2. Ký bằng ảnh chữ ký người dùng tải lên, lưu kèm văn bản — user, 2026-11-09.
3. Xác nhận đổi NUT-21, đóng P-26 và P-27 — user, 2026-11-09.
4. Khóa sau ký; sửa lại mọi ngày kèm lý do, chữ ký cũ vào lịch sử; ký cần đủ Đạt / Không đạt — user, 2026-11-10.
5. Duyệt bộ rule INS-01..04, SIG-01; đã ghi vào `docs/business/BUSINESS_RULES.md` (CONFIRMED), đổi NUT-21, đóng P-26, P-27 — user, 2026-11-10.
6. M1 tươi: thêm nhà cung cấp vào kiểm nhận hàng tươi ở BE (điền sẵn địa chỉ, điện thoại, người giao) — user, 2026-11-10.
7. Số suất ở M2, M3, M5 tách Nhà trẻ / Mẫu giáo như PMS — user, 2026-11-10.
8. Làm tiếp trên nhánh bán trú `feature/G94-254-BE-FEAT-27-day-boarding` (không tạo nhánh mới) — user, 2026-11-10.
9. Dùng thiết kế bảng gọn theo hướng ERD của nhánh (một bảng biên bản với cột jsonb) thay vì 6 bảng con — agent chọn theo quyết định ERD gọn 09/11, chờ user soát.

## Làm rõ nghiệp vụ

Theo `.ai/workflows/clarify-business.md` và domain spec 14 mục (`specs/014-.../domain-spec.md`).

- **Actor / outcome:** PHT (điền, ký), HT (như PHT mọi điểm trường), bếp (App ghi lưu mẫu) · hồ sơ kiểm thực và lưu mẫu hằng ngày có chữ ký ảnh, khóa, lịch sử, xuất file.
- **Ảnh hưởng:** nutrition (bảng mới, mở rộng `food_samples`/`food_sample_dishes`), identity-access (chữ ký tài khoản) · quyền theo điểm trường `FOOD_SAFETY_APPROVE`, `KITCHEN_RECORD` · FE: `/kitchen/inspections`, `/account/signatures` · APP: màn bếp ghi lưu mẫu thêm 3 trường · dữ liệu cũ: bản ghi lưu mẫu cũ thiếu trường mới ⇒ rỗng.

### Câu hỏi mở
- [x] User xác nhận bộ rule INS-01..04, SIG-01 và cho ghi vào `BUSINESS_RULES.md`.
- [x] `fresh_checks` thêm `supplier_id` (M1 tươi điền sẵn nhà cung cấp).
- [x] Số suất ở M2/M3/M5 tách Nhà trẻ / Mẫu giáo.
- [x] Nhánh làm việc: nhánh bán trú `feature/G94-254-BE-FEAT-27-day-boarding`.
- Ghi chú (không chặn code): mã Jira riêng cho task này cần có trước khi commit; hiện dùng tạm `G94-254` của nhánh, hỏi user khi commit.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Duyệt INS-01..04, SIG-01 | Duyệt | user | 2026-11-10 | `docs/business/BUSINESS_RULES.md` |
| NCC hàng tươi | Thêm vào kiểm nhận ở BE | user | 2026-11-10 | plan này (data model), nutrition.md khi cài đặt |
| Số suất | Tách Nhà trẻ / Mẫu giáo | user | 2026-11-10 | INS-01 |
| Nhánh BE | Nhánh nutrition (day-boarding) | user | 2026-11-10 | plan này |

## Acceptance criteria

AC-1..AC-9 như domain spec mục 12; gắn rule: AC-1, AC-2 → INS-01; AC-3 → INS-02; AC-4, AC-7 → INS-03; AC-5 → INS-04; AC-6 → AUTH-20; AC-8 → SIG-01; AC-9 → NUT-21.

## Key decisions

| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Lưu chữ ký | Ảnh ở kho tệp (khóa tệp, loại nhận diện theo byte đầu), bản sao ảnh riêng cho mỗi lần ký trên biên bản | Lưu data URL trong DB; biên bản tham chiếu chữ ký tài khoản | Chứng từ không đổi sau ký dù tài khoản đổi/xóa chữ ký; không đưa ảnh vào JSON/log |
| Mô hình dòng | **Một bảng** `inspection_sheets`, dòng và lịch sử nằm trong cột jsonb; kiểm kiểu từng ô ở service theo bảng cột của mẫu | Bảng `inspection_rows` chuẩn hóa theo (biên bản, khóa dòng) và bảng chữ ký/lịch sử riêng | Nhánh bán trú vừa gộp bảng cho ERD gọn (V83) và chấp nhận jsonb thay bảng con nhỏ; biên bản luôn đọc/ghi nguyên cả mẫu, không truy vấn theo ô |
| Khung dòng | BE dựng từ nguồn (kiểm nhận, phiếu nhập, thực đơn, suất, lưu mẫu) mỗi lần đọc biên bản chưa ký; **chụp** vào cột `snapshot` khi ký | Client gửi cả dòng | BE là chủ dữ liệu; khóa dòng ổn định `foodId` hoặc `session:dishId` |
| Sửa sau ký | Khóa; "Sửa lại" có lý do, lịch sử bất biến | Sửa tự do làm mất chữ ký | Đã chốt 2026-11-10 |
| Quyền | Dùng `FOOD_SAFETY_APPROVE` theo điểm trường | Mã mới `INSPECTION_RECORD` | Ít thay đổi ma trận quyền; chờ user chốt |

Quyết định kiến trúc (lưu chữ ký chứng từ, bất biến sau ký) ⇒ ADR mới `docs/decisions/ADR-0024-inspection-signature-records.md` (đã viết ở Phase 0).

## Data model (đề xuất, chi tiết ở `specs/014-.../data-model.md` sau khi duyệt)

- `user_signatures(id, user_id → user_accounts, storage_key, content_type, size_bytes, is_default, created_at)`; tối đa 5 mỗi tài khoản; một mặc định (chỉ mục duy nhất một phần).
- `inspection_sheets(id, campus_id, sheet_date, tab ∈ FRESH|DRY|PROCESS|SERVE|SAMPLE, status ∈ DRAFT|SIGNED, version, header jsonb {place, checkedAt}, rows jsonb [{key, …ô nhập}], signature jsonb {signedBy, signedByName, signedAt, documentVersion, imageKey, contentType, sizeBytes}, snapshot jsonb [dòng lúc ký], history jsonb [{action ∈ SIGNED|REOPENED, actorId, actorName, at, note}], updated_by, updated_at, UNIQUE(campus_id, sheet_date, tab))`. Ảnh chữ ký trên biên bản là tệp riêng trong kho tệp (`imageKey`), xóa theo sau commit như ảnh bữa ăn; lịch sử giữ `imageKey` của lần ký cũ để vẫn xem lại được.
- `fresh_checks` thêm `supplier_id BIGINT NULL REFERENCES suppliers(id)` (nhà cung cấp của lần giao, điền ở màn kiểm nhận; M1 tươi lấy địa chỉ, điện thoại, người giao từ nhà cung cấp).
- `food_samples` thêm `dish_details jsonb NOT NULL DEFAULT '[]'` = `[{dishId, weight, container, temperature}]` (bản ghi cũ rỗng); `dish_ids` giữ nguyên.
- Số suất tách nhóm: tính từ suất đã xác nhận theo lớp ↔ nhóm tuổi (nguồn hiện có `MealCount`), BE trả `servings {nursery, kindergarten}` cho dòng M2, M3, M5.

## Contract đề xuất

Mọi đường dẫn dưới `/api`. Lỗi theo `ApiCode`; số mã mới dự kiến 6073–6089 (kiểm thực) và 6090–6099 (chữ ký), xác nhận lại khi cài đặt.

- `GET /signatures/me` · `POST /signatures/me` (multipart `file`, `isDefault`) · `PATCH /signatures/me/{id}/default` · `DELETE /signatures/me/{id}` · `GET /signatures/{id}/content` (chủ ảnh hoặc người xem biên bản chứa ảnh — chữ ký trên biên bản có đường riêng `GET /inspections/signatures/{id}/content`).
- `GET /inspections/day?campusId&date` → `{ campusId, date, canEdit, tabs: { fresh, dry, process, serve, sample } }`; mỗi mẫu: `rows[]` (khóa `key`, trường chỉ đọc do BE điền, ô nhập), `status`, `version`, `header {place, checkedAt}`, `signature {signedByName, signedAt, documentVersion, imageUrl}`, `history[]`, `canEdit`, `canReopen`. Đọc: HT, PHT có quyền đọc điểm trường.
- `PUT /inspections/day/{tab}?campusId&date` `{ header, rows[{key, …}] }` — lưu nháp, 409 nếu đã ký.
- `POST /inspections/day/{tab}/sign?campusId&date` `{ signatureId }` — 422 khi thiếu Đạt / Không đạt (M1–M3), 409 khi đã ký/chưa lưu, 422 khi chưa có chữ ký.
- `POST /inspections/day/{tab}/reopen?campusId&date` `{ reason }`.
- `GET /nutrition/food-samples` thêm `dishes[].weight`, `container`, `temperature`; `POST|PUT /nutrition/food-samples` nhận các trường này (App bếp).
- Kiểm nhận hàng tươi: `PUT /nutrition/fresh-checks` nhận thêm `supplierId`; `GET` trả `supplier`.
- Dòng M2, M3, M5: `servings` là `{ nursery, kindergarten }` (FE hiện đang dùng một số tổng, cần đổi theo).

Consumer FE cụ thể: `src/services/kitchen/api/inspectionApi.js`, `src/services/signatureService.js`, `src/services/kitchenRecordsApi.js`; APP: màn bếp ghi lưu mẫu.

## Phases

### Phase 0 — Chốt nghiệp vụ và tài liệu (không code) — ĐÃ LÀM, chờ review
- Files: M `docs/business/BUSINESS_RULES.md` (NUT-21, P-26, P-27, thêm INS-01..04, SIG-01) — đã làm; M `docs/modules/nutrition.md`, `docs/modules/identity-access.md`, `docs/business/flows/meal-management-permissions.md`; C `docs/decisions/ADR-0024-inspection-signature-records.md`, `specs/014-.../{data-model,tasks}.md`.
- Steps: duyệt plan; ghi rule; ADR; data model và tasks cho Phase 1–3.
- Exit: "Câu hỏi mở" chỉ còn mã Jira; `status: approved`; `node scripts/check-ai-layer.mjs` không lỗi.
- **Dừng chờ user review.**

### Phase 1 — Chữ ký tài khoản
- Files: C `V84__create_user_signatures.sql`; C entity/repo/service/controller/DTO (`UserSignature*`), mã lỗi trong `ApiCode`, cập nhật `SecurityConfig` nếu cần; C test service + controller; M snapshot OpenAPI.
- Steps: lưu ảnh qua `StorageService`, nhận diện loại bằng `ImageFiles.sniff`, giới hạn 2 MB và 5 chữ ký, mặc định duy nhất, tải nội dung có kiểm quyền.
- Tests: AC-8 (đúng/sai loại, quá cỡ, quá 5, mặc định, xóa, quyền tải).
- Exit: `node scripts/verify.mjs` → `VERIFY PASS` (test, ArchUnit, snapshot OpenAPI, Spotless).
- **Dừng chờ user review.**

### Phase 2 — Biên bản kiểm thực
- Files: C `V85__create_inspection_sheets.sql` (bảng `inspection_sheets`, `fresh_checks.supplier_id`) và entity/repo/DTO/service/controller; C bộ dựng khung dòng cho từng mẫu (tái dùng `MealDemandCalculator`, kiểm nhận, phiếu nhập, thực đơn, suất đã xác nhận theo nhóm tuổi, `FoodSampleRepository`); M màn kiểm nhận nhận `supplierId`; C mã lỗi; M snapshot OpenAPI.
- Steps: dựng khung, lưu nháp, ký (chép ảnh, chụp dòng, lịch sử), sửa lại, quyền theo điểm trường, khóa dòng khi ghi đồng thời.
- Tests: AC-1..AC-7, gồm cả ghi đồng thời, bản chụp không đổi khi nguồn đổi, ảnh trên biên bản không đổi khi tài khoản xóa chữ ký.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS`.
- **Dừng chờ user review.**

### Phase 3 — Mở rộng lưu mẫu (BE + APP)
- Files: C `V86__food_sample_dish_details.sql` (`food_samples.dish_details`); M `FoodSample*`, `FoodSampleRequest/Response`; M App bếp (plan APP riêng).
- Tests: AC-9; ghi/đọc ba trường; bản ghi cũ.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS`.
- **Dừng chờ user review.**

### Phase 4 — Hoàn tất
- Files: M `docs/modules/*`, `docs/contracts/*`, `docs/context/CURRENT_STATE.md`, `docs/knowledge/CROSS_MODULE_ISSUES.md`, cập nhật `FE:integrationModules.js` (`kitchen-inspection`, `signatures` demo → api).
- Exit: converge không còn gap; `node scripts/check-ai-layer.mjs` pass.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-8 | SIG-01 | service + controller | 1 | chưa chạy |
| AC-1..AC-7 | INS-01..04, AUTH-20 | service + controller + Testcontainers | 2 | pass hẹp cho M1–M3, M5 (service, tích hợp Postgres, controller); ghi đồng thời chưa có test; `verify.mjs` đầy đủ xem Progress log |
| AC-9 | NUT-21 | tích hợp Postgres + JSON | 3 | pass hẹp (`sampleDishDetails`, `FoodSampleRequestJsonTest`); `verify.mjs` xem Progress log |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Nhánh `G94-60` đang có thay đổi chưa commit của người khác và migration V83+ có thể trùng nhánh khác | Xung đột khi gộp | Làm trên nhánh riêng từ `dev`, kiểm số migration ngay trước khi tạo | Bỏ migration chưa gộp |
| Ảnh chữ ký là dữ liệu cá nhân | Lộ/giả mạo | Chỉ tải qua API có xác thực, không URL công khai, không log, không fixture thật, bản sao riêng mỗi lần ký | Tắt endpoint tải nội dung; biên bản vẫn có tên người ký |
| Dựng khung dòng chậm khi nguồn lớn | Chậm | Truy vấn theo (điểm trường, ngày), chỉ mục hiện có; đo ở Phase 2 | Cache theo phiên bản nguồn |
| Dữ liệu nguồn đổi sau ký | Biên bản lệch | Bản chụp dòng lúc ký | — |
| Rule NUT-21/P-26/P-27 đổi chưa được user duyệt | Làm sai nghiệp vụ | Phase 0 chặn code | Không có gì để rollback |

## Validation log
- [ ] Khẳng định về code có `file:line`: đã có ở Context
- [ ] Đổi contract ⇒ consumer cụ thể: FE `inspectionApi.js`, `signatureService.js`, `kitchenRecordsApi.js`; APP màn bếp
- [ ] Mỗi AC có test; mỗi phase có Exit
- [x] >8 file hoặc >3 phase ⇒ đã tách 4 phase
- [ ] Không dùng rule PENDING/OPEN như đã chốt: INS-01..04, SIG-01 đang đề xuất
- [ ] Tự review: không placeholder · không mâu thuẫn · scope vừa · không câu mơ hồ

## Việc cho App (repo `CareNest_APP`, chưa làm, không sửa từ repo này)
- Màn bếp ghi/sửa lưu mẫu (`POST|PUT /nutrition/food-samples`): thêm ba ô cho **từng món đã chọn**: khối lượng/thể tích mẫu (gam/ml), dụng cụ chứa mẫu, nhiệt độ bảo quản; gửi trong `dishDetails[{dishId, weight, container, temperature}]` (chuỗi tự do, tối đa 50/100/50 ký tự; bỏ trống ô được). Mỗi món tối đa một dòng, món phải nằm trong `dishIds` (sai ⇒ 400 `INVALID_VALUE`).
- Sửa bản đã ghi mà không gửi `dishDetails` ⇒ BE giữ ghi chú cũ; gửi `[]` ⇒ xóa hết. Nên luôn gửi đủ khi app có ô này.
- Đọc `GET /nutrition/food-samples`: `dishes[].weight|container|temperature` (rỗng với bản cũ). Bản đã duyệt/đã ghi hủy vẫn khóa sửa (409 `FOOD_SAMPLE_LOCKED`) như trước.
- Gợi ý UX: nhớ giá trị lần ghi trước (dụng cụ, nhiệt độ thường giống nhau mỗi ngày) để bếp chạm một lần; trường là chuỗi tự do nên chưa có danh sách chuẩn, chờ user chốt nếu cần danh sách lựa chọn.

## Progress log
### 2026-11-10 — Test ghi đồng thời; user chốt giữ chặn ký (INS-02)
- Commit: chưa commit
- User chốt "giữ" (2026-11-10): ký bị chặn (422, mã 6090) khi còn dòng Không đạt chưa ghi biện pháp xử lý — không nới thành cảnh báo.
- Đóng hạn chế đã biết của lát 2b: thêm `InspectionConcurrencyIntegrationTest` (Postgres thật, không giao dịch test): hai luồng cùng lưu một biên bản ở phiên bản 0 ⇒ đúng một `OK`, một 409 `INSPECTION_STALE` (6082), một dòng `inspection_sheets` với `version = 1`. Chạy hẹp 1 pass; `node scripts/verify.mjs` đầy đủ trên code cuối: `VERIFY PASS full | tests 1259 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 705s` (2026-11-10).

### 2026-11-10 — INS-02 thêm biện pháp xử lý khi Không đạt (theo QĐ 1246)
- Commit: chưa commit
- Nguồn: user yêu cầu "rule chuẩn thực tế theo nghị định" kèm bản scan QĐ 1246/QĐ-BYT. Điều 3.1 (nguyên liệu không đạt "cần ghi rõ biện pháp xử lý … loại bỏ, trả lại, tiêu hủy"), Điều 4.2 (món có biểu hiện lạ: loại bỏ và ghi rõ biện pháp xử lý), Điều 5.5 (món bất thường: biện pháp xử lý kịp thời, ghi cụ thể).
- Đã làm: `ApiCode.INSPECTION_ACTION_MISSING` 6090 (422); `InspectionColumns.actionMissing` (dòng cảm quan hoặc xét nghiệm nhanh `FAIL` mà `note` trống/khoảng trắng); `sign` kiểm sau kiểm cảm quan, chỉ M1–M3; `BUSINESS_RULES.md` INS-02 và module card nutrition cập nhật. Test: `InspectionServiceImplTest.signNeedsActionForFailedRows` (ba trường hợp + ký được khi đủ), cập nhật test ký lại sau sửa.
- Verify: test hẹp `InspectionServiceImplTest` 13 pass, `InspectionIntegrationTest` 7 pass; `node scripts/verify.mjs` đầy đủ trên code cuối: `VERIFY PASS full | tests 1258 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 893s` (2026-11-10).
- Giả định cần user soát: chặn ký (422) thay vì chỉ cảnh báo; lưu nháp vẫn cho thiếu biện pháp xử lý. Nếu user muốn nới thì đổi thành cảnh báo ở FE và bỏ kiểm ở `sign`.

### 2026-11-10 — Sửa lỗi bản chụp lúc ký (phát hiện khi nối FE)
- Lỗi: `sign()` chụp đúng các dòng **client đã lưu**; client chỉ lưu ô PHT nhập (khóa dòng + ô nhập) nên bản chụp thiếu ô hệ thống (tên món, suất, nhà cung cấp...) ⇒ biên bản đã ký hiện dòng rỗng tên. Test cũ không bắt được vì lưu nguyên dòng khung.
- Sửa: `InspectionServiceImpl.sign` gộp khung nguồn lúc ký với dòng đã lưu (cùng `merge` của đọc nháp) rồi chụp và kiểm cảm quan trên dòng đã gộp; `InspectionSheet.sign` nhận dòng chụp. Test tích hợp `saveSignReopenRoundTrip` giờ chỉ lưu ô PHT nhập và kiểm bản chụp có `dishName`.
- Verify: `node scripts/verify.mjs` đầy đủ trên code cuối (Phase 1–3 + sửa này): `VERIFY PASS full | tests 1257 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 713s` (2026-11-10). Đây là lần chạy hiệu lực cho cả Phase 1, 2, 3.

### 2026-11-10 — Phase 3: trường lưu mẫu Mẫu 5 (chờ review)
- Commit: chưa commit
- Verify: chạy hẹp `KitchenRecordsIntegrationTest` (8), `InspectionIntegrationTest` (7), `InspectionServiceImplTest` (12), `ArchitectureRulesTest` (14), `FoodSampleRequestJsonTest` (2) pass; snapshot OpenAPI cập nhật (chỉ thêm). `node scripts/verify.mjs` đầy đủ chạy nền sau khi hết sửa code, kết quả ghi bên dưới khi có.
- Đã làm: `V86` (`food_samples.dish_details` jsonb `[]`), `FoodSampleDishDetail`, converter `JsonListConverter.SampleDishDetails`, `FoodSampleRequest.dishDetails` (+ constructor 5 tham số cho client cũ), `FoodSampleResponse.SampleDish.weight|container|temperature`, kiểm đầu vào (món thuộc mẫu, không trùng, độ dài), M5 điền sẵn ba cột.
- Quyết định/giả định cần user soát:
  1. Ba trường là **chuỗi tự do** theo từng món (chưa có danh sách chuẩn, đơn vị) — khớp cách PMS/giấy ghi tay; muốn danh sách chọn thì cần user chốt.
  2. `dishDetails` null khi sửa ⇒ giữ ghi chú cũ (an toàn cho App chưa cập nhật); `[]` ⇒ xóa hết.
  3. M5 là một dòng cho mỗi lần lấy mẫu (nhiều món): giá trị các món giống nhau ghi một lần, khác nhau ghi "Tên món: giá trị; …".
  4. Không đổi quy tắc duyệt/hủy NUT-21: bản đã duyệt hoặc đã ghi hủy vẫn khóa sửa, nên chỉ ghi ba trường khi bản còn `RECORDED`.
  5. Việc App ghi ở mục "Việc cho App", chưa sửa repo APP.

### 2026-11-10 — Phase 2 lát 2b: M1 tươi/khô và nhà cung cấp trên kiểm nhận (chờ review)
- Commit: chưa commit
- Verify: chạy hẹp `mvnw -o test -Dtest=InspectionIntegrationTest` (7 pass: thêm M1 tươi, M1 khô), `-Dtest=FreshGoodsIntegrationTest` (11 pass, có nhà cung cấp), snapshot OpenAPI cập nhật lại (chỉ thêm). `node scripts/verify.mjs` đầy đủ chạy nền sau khi hết sửa code: `VERIFY PASS full | tests 1254 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 863s` (gồm cả Phase 1 và lát 2a, 2b; đóng mục "chưa chạy lại verify đầy đủ" của Phase 1). Sau đó chỉ sửa tài liệu.
- Đã làm: `FreshCheck.supplierId` (cột có từ V85), `FreshCheckRequest.supplierId`, `FreshCheckResponse.FreshCheckRound.supplier` (`id, name, phone, address, deliveryPerson`, không CCCD); `InspectionIntakeSkeleton` (M1 tươi/khô), `StockMovementRepository.findByCampusIdAndMealDate`.
- Quyết định/giả định cần user soát:
  1. M1 tươi lấy từ **một** lần kiểm nhận trong ngày: lần 2 (thực nhận theo suất chốt) nếu đã ghi, không thì lần 1; khối lượng = số thực nhận nếu có, không thì lượng cần nhận. Giờ nhập = giờ ghi kiểm nhận (PHT sửa được).
  2. M1 khô lấy từ **lô đã trừ kho cho bữa ăn của ngày** (bút toán `meal_date` = ngày, cộng cả điều chỉnh/trả lô), chứ không phải phiếu nhập trong ngày; kho khô nhập theo đợt nên phiếu của ngày thường rỗng. Ô "Tên cơ sở sản xuất" và "Địa chỉ sản xuất" vẫn do PHT nhập (kho chưa lưu nhà sản xuất).
  3. `supplierId` không bắt buộc trong ghi kiểm nhận (không phá client cũ); bỏ trống khi ghi đè ⇒ bỏ nhà cung cấp đã ghi.
  4. Hạn chế đã biết: chưa có test ghi đồng thời hai luồng cho biên bản.

### 2026-11-10 — Phase 2 lát 2a: biên bản, ký, M2/M3/M5 (chờ review)
- Commit: chưa commit
- Verify: chạy hẹp, **chưa chạy `node scripts/verify.mjs` đầy đủ** (≈12 phút, để cuối Phase 2b): `mvnw -o test -Dtest=InspectionServiceImplTest` 12 pass; `-Dtest=InspectionIntegrationTest` (Postgres thật) 5 pass; `-Dtest=InspectionControllerTest` 5 pass; `-Dtest=UserSignatureServiceImplTest,UserSignatureControllerTest,ArchitectureRulesTest` pass (14 ArchUnit). Snapshot OpenAPI cập nhật có chủ đích bằng `-Dopenapi.snapshot.update=true` (chỉ thêm 459 dòng, không xóa). `spotless:apply` đã chạy.
- Sửa lỗi Phase 1 còn tồn: `ArchitectureRulesTest.apiDoesNotExposeEntities` fail vì `UserSignatureResponse.from(UserSignature)` gọi getter entity trong DTO → chuyển ánh xạ sang `UserSignatureServiceImpl.toResponse`, DTO chỉ còn record.
- Đã làm: V85 (`inspection_sheets` + `fresh_checks.supplier_id`); `InspectionSheet` + `InspectionJson` (jsonb: header, rows, signature, snapshot, history), `InspectionSheetRepository` (khóa dòng `campuses` khi ghi, tìm ảnh trong biên bản); `InspectionColumns` (bảng cột từng mẫu, kiểm ô); `InspectionSkeleton` (M2/M3 từ thực đơn công bố + suất đã xác nhận tách theo khối thực đơn của nhóm tuổi, M5 từ `food_samples`; M1 chưa dựng); `InspectionService`/`Impl`; `InspectionController` (`/inspections/day`, `/day/{tab}`, `/sign`, `/reopen`, `/signatures/{imageKey}`); `InspectionSignatureStorage` (prefix `meals/inspection-signatures/`, cấu hình + `.env.example`); `UserSignatureService.findForSigning`; `ApiCode` 6080–6089.
- Quyết định/giả định cần user soát:
  1. Quyền ghi/ký/sửa lại = `canEditCampus(FOOD_SAFETY_APPROVE)` (đúng INS-01); PHT mặc định chưa có mã này nên phải được HT cấp mới ghi được. Đọc: HT/PHT đọc được điểm trường (`checkCanReadCampus`). Bếp, GV không vào.
  2. Số suất tách Nhà trẻ / Mẫu giáo thực hiện bằng **một dòng cho mỗi (bữa, khối, món)**, `block` + `sessionLabel` ("Bữa trưa · Nhà trẻ") + `servings` của khối đó; không đổi `servings` thành `{nursery, kindergarten}` như contract đề xuất. M5: `servings` = tổng hai khối của bữa.
  3. Đọc nháp = khung nguồn mới nhất gộp với ô PHT đã lưu (ô hệ thống lấy lại từ nguồn, ô nhập giữ, dòng PHT đã lưu mà nguồn không còn vẫn giữ); đã ký trả bản chụp. Nên "sửa lại" không cần dựng lại riêng.
  4. Lưu nháp có `expectedVersion` (0 khi chưa lưu); lệch ⇒ 409 `INSPECTION_STALE` thay vì ghi đè người khác.
  5. Ô hệ thống điền do client gửi lên được lưu như gửi (chỉ kiểm khóa ô và kiểu); giá trị chốt là bản chụp lúc ký.
  6. Đường ảnh trên biên bản dùng mã tệp: `GET /inspections/signatures/{imageKey}?campusId` (ảnh nằm trong jsonb, không có id riêng); ảnh của lần ký đã bị thay vẫn tải được qua lịch sử.
  7. Ngày chưa tới không ghi được; "Sửa lại" cho mọi ngày đã qua.
- Chưa làm (lát 2b): khung M1 tươi (kiểm nhận + nhà cung cấp, cần entity/DTO `supplierId` ở `FreshCheck*`) và M1 khô (phiếu nhập, hạn dùng) — hiện trả khung rỗng; test ghi đồng thời hai luồng; `verify.mjs` đầy đủ.
- Tiếp: review user → lát 2b.

### 2026-11-10 — Phase 1 chữ ký tài khoản (chờ review)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` chạy đầy đủ một lần (≈12 phút, Testcontainers): mọi test pass **trừ** `OpenApiSnapshotTest` (thiếu 5 endpoint chữ ký) → đã cập nhật `docs/api/openapi.yaml` có chủ đích (chỉ thêm 143 dòng, không đổi operationId có sẵn; đặt tên phương thức controller riêng `listSignatures`, `uploadSignature`... để không làm đổi `upload` → `upload_1` của API khác). Chạy lại hẹp: `mvnw -o test -Dtest=OpenApiSnapshotTest,UserSignatureControllerTest,UserSignatureServiceImplTest` → 16 test pass; `mvnw -o spotless:check` → BUILD SUCCESS. **Chưa chạy lại `verify.mjs` đầy đủ sau khi sửa snapshot** (user ngắt lệnh chạy 10 phút); chạy lại ở cuối Phase 2.
- Đã làm: `V84__create_user_signatures.sql`; `UserSignature` entity, `UserSignatureRepository` (khóa dòng tài khoản, bỏ mặc định cũ), `SignatureStorage` (prefix `accounts/signatures/`, cấu hình `carenest.signature-media.prefix`, `.env.example`), `UserSignatureService`/`Impl`, `UserSignatureController` (`GET|POST /signatures/me`, `PATCH /signatures/me/{id}/default`, `DELETE /signatures/me/{id}`, `GET /signatures/me/{id}/content`), `UserSignatureResponse`, mã lỗi `SIGNATURE_INVALID` 1140, `SIGNATURE_LIMIT_REACHED` 1141, `SIGNATURE_NOT_FOUND` 1142. Chỉ PNG/JPG theo byte đầu (không nhận WEBP), ≤2 MB, tối đa 5, ảnh đầu tiên tự mặc định, xóa ảnh mặc định thì ảnh mới nhất còn lại thành mặc định, tệp xóa sau commit, lưu DB lỗi thì dọn tệp mới. Vai trò dùng: HT, PHT, GV (nhân sự Web).
- Khác plan: đường tải ảnh là `GET /signatures/me/{id}/content` (chỉ chủ ảnh) thay `/signatures/{id}/content`; ảnh trên biên bản đã ký có đường riêng ở Phase 2. Chưa có test tích hợp với Postgres thật cho migration V84 và chỉ mục mặc định duy nhất ngoài `verify.mjs`.
- Tiếp: review user → Phase 2 (biên bản kiểm thực).

### 2026-11-10 — Phase 0 (chờ review)
- Commit: chưa commit
- Verify: `node scripts/check-ai-layer.mjs` (xem kết quả ở lần chạy cuối); chưa chạy `verify.mjs` vì chỉ sửa tài liệu.
- Đã làm: duyệt plan (user trả lời 4 câu), chuyển tài liệu từ checkout G94-60 sang worktree day-boarding, ghi INS-01..04, SIG-01, đổi NUT-21, đóng P-26/P-27 vào `BUSINESS_RULES.md`, đổi data model sang một bảng jsonb theo ERD gọn, đổi số migration V84–V86.
- Khác plan: bảng dữ liệu gộp một bảng jsonb thay 6 bảng (quyết định 9, chờ user soát).
- Tiếp: ADR, data-model, tasks, cập nhật module card; sau review user → Phase 1.
