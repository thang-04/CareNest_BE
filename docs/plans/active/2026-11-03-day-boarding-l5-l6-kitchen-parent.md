---
title: Bán trú L5 + L6 — bếp hôm nay, lưu/hủy mẫu, ảnh bữa, phụ huynh xem thực đơn (BE + Web)
status: in-progress
owner: thangnd
jira: G94-192 / BE-FEAT-27   # FE: G94-191 / FE-FEAT-48
branch: feature/G94-254-BE-FEAT-27-day-boarding
modules: [nutrition, identity-access, child]
rules: [AUTH-05, AUTH-21, AUTH-22, NUT-03, NUT-16, NUT-21, NUT-23, PAR-01, PAR-02, P-26]
risk: high
created: 2026-11-03
updated: 2026-11-03
---
# Bán trú L5 + L6 — bếp hôm nay, lưu/hủy mẫu, ảnh bữa, phụ huynh xem thực đơn (BE + Web)

Plan con của `2026-11-02-day-boarding-redesign.md` (lát L5, L6), sau L4. User 03/11: làm BE + Web và API cho App; màn App Flutter để đợt sau (máy chưa có Flutter SDK); L7 AI để sau. Chế độ tự chạy: giả định ghi ở đây.

## Context
- Bếp chỉ dùng App (AUTH-21), tài khoản chung mỗi cơ sở (AUTH-22) — điểm trường của bếp lấy từ phân công `KITCHEN_STAFF` còn hiệu lực. Bếp chỉ thấy số đã xác nhận (NUT-03), không tên trẻ (AUTH-05).
- Ảnh: `integration/storage/StorageService` (S3, có endpoint/path-style ⇒ chạy được với MinIO). Mẫu upload + dọn khi rollback: `LearningResourceServiceImpl`.
- Phụ huynh: `GuardianChildLinkService.isGuardianOf`, `EnrollmentService.findActiveClassroomIds`; DTO phụ huynh riêng (ADR-0010), mặc định không hiện (PAR-02).
- FE demo: `/kitchen/published-menu`, `/kitchen/food-samples`; hub `foodSampleStatus = NOT_AVAILABLE`.

## Làm rõ nghiệp vụ
- **Bếp (App):** xem ngày; ghi lưu mẫu (ngày, bữa, món điền sẵn từ thực đơn đã công bố), sửa khi chưa duyệt; ghi hủy sau ≥ 24 giờ kể từ lấy mẫu; đăng/thay ảnh mỗi bữa trong khung giờ.
- **PHT có `FOOD_SAFETY_APPROVE` (điểm trường), HT:** duyệt lưu mẫu, duyệt hủy; in nhãn/biểu mẫu từ bản đã duyệt (mẫu sổ chưa chốt — P-26 ⇒ biểu mẫu chung, trường cấu hình sau).
- **HT:** cấu hình khung giờ ảnh theo bữa, chung toàn trường (NUT-16, phần cố định của HT).
- **Phụ huynh (App):** thực đơn tuần đã công bố của khối con học + ảnh bữa của điểm trường con (PAR-01); tuần chưa công bố ⇒ không thấy món.
- **Giả định (chờ soát):** ghi hủy không đòi bản lưu đã được duyệt; một ảnh mỗi điểm trường × ngày × bữa (đăng lại thì thay); chưa cấu hình khung giờ ⇒ chặn đăng ảnh; ảnh ≤ 10 MB, JPEG/PNG/WebP; số suất thay thế hiện theo lớp × bữa (chưa có món thay thế dị ứng).

### Câu hỏi mở
Không có câu hỏi chặn. P-26 còn mở ⇒ biểu mẫu in để chung.

## Thiết kế
**V61__create_kitchen_records.sql** (V58/V59 thuộc phiên định mức gam, V60 thuộc L4): `food_samples(campus_id, sample_date, session, sampled_at, recorded_by, status RECORDED|APPROVED, approved_by/at, disposed_at, disposal_recorded_by, disposal_status NONE|RECORDED|APPROVED, disposal_approved_by/at, note, audit)` · `food_sample_dishes(sample_id, dish_id, PK)` · `meal_photo_windows(session PK, start_time, end_time, updated_by, updated_at, CHECK start < end)` · `meal_photos(campus_id, photo_date, session, storage_key, content_type, size_bytes, uploaded_by, uploaded_at, UK(campus, date, session))` · seed `role_permissions ('KITCHEN_STAFF', 'KITCHEN_RECORD')`.

**Quyền:** `KITCHEN_RECORD` (module meals; mặc định + cấu hình được: `KITCHEN_STAFF`); `FOOD_SAFETY_APPROVE` (mặc định `PRINCIPAL`, cấu hình được `VICE_PRINCIPAL`, `campusScoped`). Đọc dữ liệu bếp/mẫu/ảnh: HT, PHT (mọi điểm trường), bếp (điểm trường mình).

**API** (`/nutrition`):

| Method · path | Ai | Ghi chú |
| --- | --- | --- |
| `GET /kitchen/day?date&campusId?` | bếp (điểm trường mình), HT/PHT (bắt buộc `campusId`) | `KitchenDayResponse`: tuần đã công bố? món theo bữa × khối; suất đã xác nhận theo lớp × bữa (`normal`, `substitute`); tổng lượng thực phẩm cần (FRESH + DRY, đơn vị gốc, đã cộng thải bỏ); `confirmedAt` |
| `GET /food-samples?campusId&from&to` | HT/PHT, bếp | `[FoodSampleResponse]` |
| `POST /food-samples` · `PUT /food-samples/{id}` | bếp `KITCHEN_RECORD` | `{date, session, dishIds, sampledAt?, note?}`; sửa khi `status = RECORDED` |
| `POST /food-samples/{id}/dispose` | bếp | `{disposedAt?}`; < 24 giờ sau lấy mẫu ⇒ 409 |
| `POST /food-samples/{id}/approve` · `/approve-disposal` | `FOOD_SAFETY_APPROVE` | |
| `GET /meal-photo-windows` · `PUT /meal-photo-windows` | đọc: HT/PHT/bếp; sửa: HT | `[{session, startTime, endTime}]` |
| `POST /meal-photos` (multipart `file`, `session`) | bếp | ngày = hôm nay, trong khung giờ, thay ảnh cũ |
| `GET /meal-photos?campusId&date` · `GET /meal-photos/{id}/content` | HT/PHT, bếp | |
| `GET /children/{childId}/week-menu?weekStart` | phụ huynh của trẻ | `ParentWeekMenuResponse`: chỉ tuần PUBLISHED; bữa theo nhóm tuổi của trẻ; ảnh bữa điểm trường của trẻ |
| `GET /children/{childId}/meal-photos/{photoId}/content` | phụ huynh của trẻ | ảnh phải thuộc điểm trường của trẻ |

Hub: `foodSampleStatus` ∈ `PENDING_APPROVAL` (kèm `foodSamplePendingCount`), `UP_TO_DATE`, `NONE` (chưa có bản ghi ngày đó).
`ApiCode` 6060–6072 (6044–6052 đụng 6049/6050 của phiên định mức gam; thêm 6069 không có quyền duyệt, 6070 sai trạng thái, 6071 món không hợp lệ, 6072 chỉ HT đặt khung giờ): bản lưu mẫu không tìm thấy; bản lưu mẫu đã duyệt, không sửa; chưa đủ 24 giờ để hủy mẫu; không có quyền bếp điểm trường; khung giờ ảnh không hợp lệ; ngoài khung giờ đăng ảnh bữa {0}; chưa cấu hình khung giờ ảnh; ảnh không hợp lệ (loại/kích thước); không tìm thấy ảnh.

## Acceptance criteria
- **AC-1** (`AUTH-05`, `NUT-03`): `GET /kitchen/day` của bếp trả suất đã xác nhận theo lớp, không có tên/ID trẻ; số chờ xác nhận lại không hiện.
- **AC-2** (`NUT-21`): bếp ghi lưu mẫu ⇒ `RECORDED`; PHT có mã duyệt ⇒ `APPROVED`, bếp sửa ⇒ 409; ghi hủy trước 24 giờ ⇒ 409, sau 24 giờ ⇒ `disposal_status RECORDED`, PHT duyệt ⇒ `APPROVED`.
- **AC-3** (`NUT-16`): ngoài khung giờ ⇒ 409; trong khung giờ ⇒ lưu ảnh, đăng lại thay ảnh cũ; HT sửa khung giờ, PHT ⇒ 403.
- **AC-4** (`PAR-01`, `PAR-02`): phụ huynh xem tuần đã công bố của khối con; tuần nháp ⇒ không có món; con người khác ⇒ 403; ảnh điểm trường khác ⇒ 404.
- **AC-5** (`AUTH-20`): PHT không có `FOOD_SAFETY_APPROVE` ⇒ duyệt 403.

## Phases
### Phase 1 — Bếp hôm nay, lưu/hủy mẫu, khung giờ + ảnh bữa, hub
- Tests: AC-1, AC-2, AC-3, AC-5 → `KitchenRecordsIntegrationTest` (ảnh: `StorageService` thay bằng bản giả trong test)
### Phase 2 — Phụ huynh (HOÃN theo user 03/11, làm cùng App)
- Tests: AC-4 → `ParentWeekMenuIntegrationTest`
### Phase 3 — Web (FE plan `CareNest_FE:docs/plans/active/2026-11-03-day-boarding-l2-l3-fe.md` mục L5): Bếp hôm nay (xem), hàng chờ duyệt + in, ảnh bữa, khung giờ (HT), hub
- Exit mỗi phase: `node scripts/verify.mjs` → `VERIFY PASS full`; kiểm trên stack `carenest_l3` (MinIO cho ảnh).

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| S3 chưa cấu hình ở môi trường | Không đăng được ảnh | Lỗi rõ `SERVICE_UNAVAILABLE`; stack demo dùng MinIO | — |
| Biểu mẫu in sai mẫu trường dùng (P-26) | In lại | Biểu mẫu chung, tách cấu hình khi P-26 chốt | — |

## Progress log
### 2026-11-03 — Plan (chế độ tự chạy)
- Commit: chưa commit
- L6 hoãn theo user 03/11, làm cùng App: không có `/children/{childId}/week-menu`, ảnh phụ huynh, `ParentWeekMenuIntegrationTest`; AC-4 chưa làm. Chỉ làm Phase 1 (L5).
- Phase 1 xong code + docs (V61, `KitchenRecordsController`, `KitchenDayService`, `FoodSampleService`, `MealPhotoService`, hub `foodSampleStatus` + `foodSamplePendingCount`).
- AC → test (`KitchenRecordsIntegrationTest`, 7 test): AC-1 `kitchenSeesConfirmedServingsOnly` + `draftMenuAndScopes`; AC-2 `recordApproveAndLockSample` + `disposeAfter24Hours`; AC-3 `uploadWithinWindowAndReplace` + `photoValidationAndWindowPermissions`; AC-5 `approvalNeedsPermissionAndValidDishes`. AC-4: hoãn.
- Giả định ghi thêm (chờ soát):
  - Bếp ghi/sửa/hủy/đăng ảnh: điểm trường lấy từ đúng một phân công `KITCHEN_STAFF` còn hiệu lực (không nhận `campusId` từ client); nhiều hơn một ⇒ 6063. Đọc: bếp có thể chọn `campusId` trong các điểm trường mình.
  - Hub `PENDING_APPROVAL` đếm việc chờ duyệt của điểm trường ở mọi ngày (lưu mẫu `RECORDED` + hủy mẫu `RECORDED`, mỗi việc 1), không chỉ ngày đang xem; `UP_TO_DATE`/`NONE` theo bản ghi của ngày xem.
  - Sửa bản lưu mẫu chỉ khi `RECORDED` và chưa ghi hủy (giờ lấy mẫu là mốc 24 giờ); ghi hủy một lần; `sampledAt`/`disposedAt` không được ở tương lai; ngày lưu mẫu không ở tương lai; một bữa có thể có nhiều bản lưu mẫu; món chỉ cần tồn tại trong danh mục (không ép thuộc thực đơn đã công bố vì bếp có thể lưu món thực nấu).
  - Khung giờ ảnh tính theo múi giờ trường, hai đầu gồm cả biên; `PUT /meal-photo-windows` nhận `{windows:[...]}` và chỉ ghi đè bữa có trong danh sách; chỉ HT có phân công còn hiệu lực (PHT ⇒ 6072).
  - Ảnh nhận diện theo byte đầu (JPEG/PNG/WebP), ≤ 10 MB, lưu qua `StorageService` dùng chung bucket/prefix `education-media` (khóa UUID); thay ảnh ⇒ tệp cũ xóa sau commit, lỗi xóa chỉ ghi log. Chưa có `MediaAsset` nên metadata nằm ở `meal_photos`.
  - Kitchen day: `countStatus` NONE|PARTIAL|CONFIRMED; chỉ bữa đã xác nhận được tính suất và lượng thực phẩm; tuần nháp không hiện món/lượng.
  - Quyền mới `KITCHEN_RECORD`, `FOOD_SAFETY_APPROVE` làm đổi danh sách quyền cố định của HT: cập nhật `PermissionTest`, `CurrentUserServiceTest`; hai test hub cũ (`FreshGoodsIntegrationTest`, `MealPlanIntegrationTest`) đổi kỳ vọng `NOT_AVAILABLE` ⇒ `NONE`.
- OpenAPI snapshot sinh lại (`-Dopenapi.snapshot.update=true`); diff chung với các phiên khác đang sửa cùng worktree.

### 2026-11-03 — Kiểm L5 trên stack `carenest_l3` (dữ liệu thật; S3 = SeaweedFS 3.97)
- Commit: chưa commit
- Verify BE: `VERIFY PASS full | tests 804 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 494s`; FE: `VERIFY PASS full`.
- Stack migrate tới V62 (V62 của phiên định mức kcal cả ngày). HT đặt khung giờ trên Web (trưa 10:00–16:00, chiều 16:30–17:30) ⇒ DB đúng.
- Bếp (`kitchen`, token `carenest-dev`, giả lập App): `GET /kitchen/day` 03/11 ⇒ tuần PUBLISHED, sổ CONFIRMED, suất lớp A1 2/2, không tên trẻ. Ghi lưu mẫu trưa 03/11 ⇒ 201 `RECORDED`; hủy ngay ⇒ 409; mẫu chiều 01/11 hủy ⇒ `disposalStatus RECORDED`. Ảnh trưa (trong khung) ⇒ 201, đăng lại thay ảnh cũ (1 dòng, S3 còn 1 object); ảnh chiều (ngoài khung) ⇒ 409. Bếp sửa bản đã duyệt ⇒ 409. PHT (không có `FOOD_SAFETY_APPROVE`) duyệt ⇒ 403.
- Web HT: hàng chờ duyệt ⇒ duyệt lưu mẫu, duyệt hủy mẫu 01/11 ⇒ "Đã duyệt"; hub CS-A "1 chờ duyệt", CS-B "Chưa có bản lưu mẫu"; Ảnh bữa tải blob đúng (800×499); Bếp hôm nay hiện món, suất theo lớp, thực phẩm theo đơn vị mua.
- Lỗi môi trường đã gặp (không phải lỗi app): SeaweedFS 3.97 lưu nguyên khung `aws-chunked` (`chunk-signature=`) khi AWS SDK gửi checksum trailer mặc định ⇒ ảnh tải về hỏng. Stack test đặt `AWS_REQUEST_CHECKSUM_CALCULATION=when_required`, `AWS_RESPONSE_CHECKSUM_VALIDATION=when_required` cho api ⇒ byte ảnh tải về trùng tệp gốc. S3 thật/MinIO không cần.
- Ghi nhận để soát: ảnh bữa dùng chung prefix `education/learning-resources/` của `StorageService` (chưa tách prefix theo loại media).
- Ảnh: `day-boarding-screens/l5-kitchen-today.jpg`, `l5-food-sample-queue.jpg`, `l5-meal-photos.jpg`, `l5-photo-windows.jpg`, `l5-hub-food-sample-pending.jpg`; L4: `l4-forecast-detail.jpg`, `l4-fresh-check-round2.jpg`.
