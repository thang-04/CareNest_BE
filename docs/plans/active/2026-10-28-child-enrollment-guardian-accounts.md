---
title: Tiếp nhận trẻ, phụ huynh trên hồ sơ, tài khoản phụ huynh (CCCD, SMS), dữ liệu theo năm học — việc BE
status: approved     # user duyệt 2026-10-28; D-3 thành việc kỹ thuật Phase 4, D-7 chốt A
owner: carenestg94@gmail.com
jira: G94-180 / BE-FEAT-43   # theo nhánh hiện tại; xác nhận task Jira khi commit
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [child, identity-access, health, school-structure, notification]
rules: [AUTH-18, AUTH-19, AUTH-20, AUTH-21, CHD-01, CHD-02, CHD-03, CHD-04, CHD-05, CHD-06, CHD-07, CHD-08, CHD-09, CHD-10, HLT-06, HLT-08, ATT-09]
risk: high
created: 2026-10-28
updated: 2026-10-28
---
# Tiếp nhận trẻ, phụ huynh trên hồ sơ, tài khoản phụ huynh, dữ liệu theo năm học — việc BE

## Context
User chốt ngày 28/10/2026 toàn bộ điểm lệch BE–FE của nhánh G94-180 (`FE:docs/integration/CHILD_ENROLLMENT_BE_GAPS.md`). FE đã sửa BE giả theo các quyết định đó và viết đặc tả việc BE cần làm (`FE:docs/integration/CHILD_ENROLLMENT_BE_SPEC.md`). User cho phép chép đặc tả sang repo BE để bắt đầu làm BE (C-6, 28/10). Plan này là bản làm việc phía BE; đổi contract thì sửa cả bản FE.

Rule đã ghi ở `docs/business/BUSINESS_RULES.md`: AUTH-18..AUTH-21, CHD-01..CHD-10, HLT-06, HLT-08, ATT-09; P-17, P-18 đã đóng. Mã quyền mới ở `docs/business/USER_ROLES.md`. Module card: `docs/modules/child.md` (mục "Đã chốt, BE chưa làm"), `docs/modules/identity-access.md`.

BE hiện có (`aa64480` + thay đổi chưa commit 28/10): bảng `children` (id, họ tên, ngày sinh), `enrollments`, `guardian_child_links`; `GET /children`, `GET /children/{childId}`, `GET|POST /children/{childId}/guardians`, `DELETE /children/{childId}/guardians/{userId}`, `PATCH /parent-accounts/{userId}/status`, `POST /parent-accounts/{userId}/temporary-password` (`docs/api/openapi.yaml`). L-17 (email phụ huynh chỉ để liên hệ), L-18 (tên đăng nhập nhân sự không là SĐT) đã làm 28/10, chưa commit.

## Quyết định đã chốt
1. L-01, L-03, L-06, L-07, L-08, L-09, L-10 chọn A — user, 2026-10-28 (bảng "Kết quả chốt" ở `FE:docs/integration/CHILD_ENROLLMENT_BE_GAPS.md`).
2. L-14: lưu form tiếp nhận ⇒ tạo/liên kết tài khoản phụ huynh và gửi SMS ngay, cùng giao dịch; nhập Excel và Sửa hồ sơ thêm phụ huynh mới ⇒ kích hoạt sau ở màn Tài khoản phụ huynh — user, 2026-10-28 (AUTH-20).
3. P-18 = A: người có quyền tiếp nhận nhưng không có quyền quản lý tài khoản phụ huynh vẫn lưu; phụ huynh mới "Chưa kích hoạt"; phụ huynh trùng CCCD với tài khoản có sẵn vẫn được liên kết — user, 2026-10-28 (C-1). Kèm: người đó không sửa được tài khoản có sẵn (dùng họ tên/SĐT/email của tài khoản, không đổi tên đăng nhập, không cấp mật khẩu mới) — user đồng ý 2026-10-28 (C-2).
4. L-15: mật khẩu tạm gửi SMS, không trả về giao diện, không in phiếu — user, 2026-10-28.
5. L-16: CCCD phụ huynh là khóa nhận diện; thông tin phụ huynh sửa được ở hồ sơ trẻ; đổi SĐT ⇒ tên đăng nhập đổi theo, mật khẩu tạm mới gửi SMS tới SĐT mới — user, 2026-10-28 (AUTH-18, AUTH-21).
6. C-4 A: bỏ `POST /children/{childId}/guardians` (thêm phụ huynh kèm tạo tài khoản ngay); giữ phần xử lý tạo/liên kết tài khoản trong service để dùng chung cho kích hoạt và lưu form tiếp nhận — user, 2026-10-28.
7. C-5 A: lỗi theo ô giữ `400` + `data` danh sách `{ field, message }` (`docs/contracts/ERROR_CONTRACT.md`); FE tự chuyển khi nối API — user, 2026-10-28.
8. Y-1..Y-5 A: dữ liệu theo năm học (mục 9 của Đặc tả) — user, 2026-10-28.
9. Phạm vi đợt sửa BE giả 28/10 là tiếp nhận + tài khoản phụ huynh (C-3); năm học áp cả đợt khám, phiếu theo dõi, đón trẻ.
10. D-1 A: thêm mã quyền `CHILD_ENROLL` (tiếp nhận, nhập Excel, mở lại hồ sơ), `CHILD_PROFILE_EDIT` (sửa hồ sơ, khai báo sức khỏe), `CHILD_PLACEMENT` (xếp/chuyển lớp, cho nghỉ học); giữ `PARENT_ACCOUNT_MANAGE` — user, 2026-10-28.
11. D-2 A: làm cổng SMS giả trước, chọn nhà cung cấp trước khi dùng thật — user, 2026-10-28.
12. D-4 A: một `GET /children` phân trang, trả đủ trường cho danh sách trẻ và màn Tài khoản phụ huynh — user, 2026-10-28.
13. D-5a B: quan hệ 6 loại (Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ) cho cả hồ sơ lẫn liên kết tài khoản; D-5b A: tối đa 3 phụ huynh mỗi trẻ, BE chặn; D-5c B: nhập đầy đủ thông tin trẻ, không có hồ sơ thiếu — user, 2026-10-28.
14. D-6: đồng ý các giả định FE (Excel không sửa tài khoản; không đổi cùng lúc CCCD và SĐT; phụ huynh có tài khoản không xóa ở hồ sơ) — user, 2026-10-28.
15. D-7 A: PHT chỉ làm trong campus được phân công (AUTH-02); "Hồ sơ trẻ" toàn trường chỉ HT có; không giao PHT phạm vi toàn trường — FE bỏ lựa chọn này ở màn giao quyền PHT (nhánh G94-179) — user, 2026-10-28.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** actor HT (toàn trường, cố định), PHT (theo ma trận quyền, trong campus mình), GV (xem trẻ lớp mình) · outcome: BE thay BE giả của FE cho tiếp nhận trẻ, phụ huynh, tài khoản phụ huynh, lọc theo năm học · rule: như frontmatter (CONFIRMED).
- **Ảnh hưởng:** module child, identity-access (Keycloak đổi username, SMS), health (khai báo sức khỏe, đợt khám/phiếu theo dõi lọc năm), school-structure (lớp của năm đang hoạt động), notification (CHD-07) · FE: `/children/*`, `/children/activation`, `/pickup` · dữ liệu cũ: mục 3.9 của Đặc tả.

### Câu hỏi mở
Chi tiết, khuyến nghị: mục 7 của Đặc tả.
- [x] D-1 — Mã quyền ⇒ A (quyết định 10).
- [x] D-2 — Nhà cung cấp SMS ⇒ A, cổng SMS giả trước (quyết định 11).
- [x] D-3 — Đổi username Keycloak khi đổi SĐT ⇒ không phải câu hỏi nghiệp vụ; chuyển thành việc kỹ thuật ở Phase 4 (user duyệt plan 2026-10-28).
- [x] D-4 — `GET /children` ⇒ A (quyết định 12).
- [x] D-5 — a B, b A, c B (quyết định 13).
- [x] D-6 — Đồng ý (quyết định 14).
- [x] D-7 — PHT được giao "Hồ sơ trẻ" toàn trường? ⇒ A: không, PHT chỉ trong campus được phân công (quyết định 15).

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| C-1..C-7 | Như "Quyết định đã chốt" | user | 2026-10-28 | plan này, `FE:docs/integration/CHILD_ENROLLMENT_BE_SPEC.md` |
| D-1, D-2, D-4, D-5, D-6 | Như quyết định 10–14 | user | 2026-10-28 | plan này, `FE:docs/integration/CHILD_ENROLLMENT_BE_SPEC.md` mục 2, 3, 6, 7 |
| D-7 | A (quyết định 15); D-3 thành việc Phase 4 | user | 2026-10-28 | plan này, `docs/business/USER_ROLES.md`, `docs/business/BUSINESS_RULES.md` AUTH-02 |

## Acceptance criteria
- **AC-1** (CHD-01..CHD-07, AUTH-18..AUTH-21): mỗi endpoint ở mục 4 của Đặc tả trả đúng dữ liệu, quyền, lỗi như BE giả FE; ca kiểm thử `FE:tests/children/*.test.mjs` là ví dụ nghiệm thu, viết lại thành test BE (`@DisplayName("RULE-ID: …")`).
- **AC-2** (AUTH-20): lưu form tiếp nhận tạo/liên kết tài khoản cùng giao dịch; lỗi sau khi đã tạo user Keycloak ⇒ bù trừ, không để tài khoản mồ côi.
- **AC-3** (AUTH-21): đổi SĐT ⇒ username mới, mật khẩu tạm mới, hủy phiên, SMS tới số mới.
- **AC-4** (Y-1..Y-5): lọc theo năm học như mục 9 của Đặc tả.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Điểm trường của trẻ chưa có lớp | Lưu `children.intake_campus_id` | Chỉ suy từ enrollment | L-03 A, CHD-02 |
| Phụ huynh trên hồ sơ trước khi có tài khoản | Bảng `child_guardians`, `guardian_child_links` vẫn là nguồn scope | Tạo tài khoản ngay | AUTH-20 |
| Lớp của trẻ theo năm học | Lấy từ `enrollments` | Lưu năm trên hồ sơ | CMR-04 |
| Cột bắt buộc khi đã có dòng cũ (`children.gender`, `child_guardians.citizen_id`) | CHECK `NOT VALID` (áp cho dòng thêm/sửa) | NOT NULL thẳng (migration lỗi trên DB dev cũ), cột null + chỉ kiểm ở service | Không làm hỏng DB dev/demo cũ; dòng mới vẫn bị DB chặn (CHD-08, mục 3.9) |
| Cấp mã trẻ | Bảng `child_code_sequences` + upsert trong giao dịch | `max(code)+1`, advisory lock | Không trùng mã khi lưu đồng thời, không cần khóa thêm |
| Thông tin phụ huynh trùng trong một trẻ | UNIQUE DEFERRABLE INITIALLY DEFERRED | UNIQUE thường | Sửa hồ sơ đổi SĐT/CCCD giữa hai phụ huynh trong một lần lưu |
| Lịch sử thay đổi (`changes`, `allergy_change`, `history_change`) | JSONB (Hibernate 7.4 có `Jackson3JsonFormatMapper`) | Bảng con từng ô | Dữ liệu chỉ ghi/đọc theo trẻ, không truy vấn bên trong |
| Dị ứng | Bảng `child_allergies` | JSON trong khai báo | Bếp, điểm danh truy vấn theo lớp/campus |
Chi tiết dữ liệu: mục 3 của Đặc tả. Quyết định kiến trúc mới (vd. outbox SMS) ⇒ ADR.

## Phases
Theo mục 1 của Đặc tả. Mỗi phase: Files ghi khi bắt đầu; Exit `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0; **dừng chờ user review**.
### Phase 1 — Quyền và migration
- Mục 2, 3 của Đặc tả: 3 mã quyền mới (D-1), CHECK quan hệ 6 mã (D-5a), cột bắt buộc NOT NULL và seed dev đủ thông tin (D-5c).
- Files: `utils/enums/Permission.java`, `utils/enums/GuardianRelationship.java`, `db/migration/V10__child_enrollment_guardian_accounts.sql`, `db/dev/R__dev_seed.sql`, `docs/api/openapi.yaml` (snapshot: thêm giá trị enum); test `PermissionTest`, `CurrentUserServiceTest`, `ScopeQueryIntegrationTest`, `GuardianDirectoryIntegrationTest`, `ChildEnrollmentMigrationIntegrationTest` (mới); docs `DATA_DICTIONARY.md`, `ERD.md`, `USER_ROLES.md`, thẻ module child, identity-access.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0 (Progress log 2026-10-28 — Phase 1).
### Phase 2 — Tra cứu không ghi
- `duplicate-check`, `guardian-check`, `parent-accounts/by-citizen-id`, `placement-classes`.
### Phase 3 — Tiếp nhận bằng form
- `POST /children/enrollments`: trước chưa có tài khoản, sau thêm tạo/liên kết tài khoản + SMS (mục 5.3, 6.1, 6.3).
### Phase 4 — Sửa hồ sơ
- `PUT /children/{id}`, sửa tài khoản theo hồ sơ, đổi SĐT đổi username (mục 5.4).
- Việc kỹ thuật (từ D-3): bật/thử "Edit username" (`editUsernameAllowed`) trên bản Keycloak đang dùng, đổi username qua Admin API, mật khẩu tạm mới, hủy phiên, trả lại username cũ khi lỗi (mục 6.3). Không làm được ⇒ dừng, báo user chọn cách khác (vd. tạo tài khoản mới, chuyển liên kết).
### Phase 5 — Nhập Excel
- Xem trước, nhập (mục 4.3).
### Phase 6 — Xếp lớp, cho nghỉ học, mở lại hồ sơ
- Mục 4.4.
### Phase 7 — Khai báo sức khỏe, xem hồ sơ
- Mục 4.5, 4.6.
### Phase 8 — Màn Tài khoản phụ huynh
- Mục 4.7 (bỏ `POST /children/{childId}/guardians` — C-4 A), mục 4.8.
### Phase 9 — Năm học
- Mục 9.
### Phase 10 — Thông báo, SMS thật
- Mục 6.1, 6.2; cổng SMS giả có từ Phase 3, nhà cung cấp thật chọn trước khi dùng (D-2).

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 (quyền) | CHD-01 | `PermissionTest.childEnrollmentPermissionsArePrincipalFixedAndVicePrincipalConfigurable`, `PermissionSchemaIntegrationTest.seedMatchesEnumDefaults` (seed V10), `CurrentUserServiceTest` (unit + DB) | 1 | pass |
| AC-1 (dữ liệu) | AUTH-18, CHD-02, CHD-03, CHD-05, CHD-06, CHD-08 | `ChildEnrollmentMigrationIntegrationTest` (nâng cấp dữ liệu cũ mục 3.9; quan hệ 6 loại; ≤3 phụ huynh, không chung SĐT/CCCD; ngày nghỉ; hồ sơ đủ; 1 enrollment đang mở; tuần thai; "Có" có nội dung; CCCD tài khoản duy nhất) — PostgreSQL thật | 1 | pass |
| AC-1..AC-4 (API) | như trên | ghi khi làm từng phase | 2–10 | chưa làm |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Keycloak không cho đổi username | Không làm được AUTH-21 như chốt | Thử sớm (D-3); dự phòng: tạo tài khoản mới, chuyển liên kết | Giữ username cũ, báo user |
| SMS lỗi | Phụ huynh không nhận mật khẩu | `smsStatus = FAILED`, "Gửi lại mật khẩu"; gửi sau commit | — |
| DB dev còn trẻ thiếu trường bắt buộc khi thêm cột NOT NULL | Migration lỗi | Seed dev nhập đủ thông tin; DB dev cũ chạy lại seed hoặc tạo lại (D-5c) | Migration ngược |

## Validation log
- [x] Khẳng định về code có đường dẫn (mục Đặc tả)
- [ ] Đổi contract ⇒ consumer FE cụ thể (khi làm từng phase)
- [ ] Mỗi AC có test; mỗi phase có Exit
- [x] Không dùng rule PENDING/OPEN như đã chốt

## Progress log
### 2026-10-28 — Chép từ FE
- Chép đặc tả từ `FE:docs/integration/CHILD_ENROLLMENT_BE_SPEC.md` (user cho phép, C-6). Chưa làm code. Commit: chưa commit.

### 2026-10-28 — User chốt D-1, D-2, D-4, D-5, D-6
- Ghi vào "Quyết định đã chốt" (10–14) và Đặc tả (sinh lại từ bản FE). FE đã làm phần của mình: quan hệ 6 loại, tối đa 3 phụ huynh trong BE giả. Còn mở D-3, D-7. Commit: chưa commit.

### 2026-10-28 — Ghi quyết định vào sổ rule BE
- User: "FE chưa làm, làm BE trước và commit lên git để tôi code BE". `docs/business/BUSINESS_RULES.md`: AUTH-18 (6 loại quan hệ, tối đa 3 phụ huynh), AUTH-20 (bỏ `POST /children/{childId}/guardians`), CHD-01 (mã quyền), thêm CHD-08 (hồ sơ đủ thông tin), CHD-09 (danh sách trẻ theo năm), CHD-10 (xếp lớp năm đang hoạt động; tài khoản phụ huynh không theo năm), HLT-08 (đợt khám, phiếu theo dõi theo năm), ATT-09 (đón trẻ theo năm). `docs/business/USER_ROLES.md` (3 mã `CHILD_*`), `docs/database/DATA_DICTIONARY.md` (6 mã quan hệ), thẻ module child, identity-access, health, attendance, `docs/context/CURRENT_STATE.md`; ghi chú plan `2026-10-18-parent-account-auto-provisioning.md` đã bị thay một phần.

### 2026-10-28 — User duyệt plan
- D-7 A (quyết định 15): PHT chỉ trong campus được phân công; ghi `docs/business/USER_ROLES.md`, `docs/business/BUSINESS_RULES.md` AUTH-02. D-3 chuyển thành việc kỹ thuật Phase 4. Status `approved`; bắt đầu Phase 1.

### 2026-10-28 — Phase 1 (quyền, migration V10, seed dev)
- User: "hãy code BE theo FE hiện tại" ⇒ làm theo plan đã duyệt, bắt đầu Phase 1. Files, Exit: mục Phase 1. Commit: chưa commit.
- Quyết định kỹ thuật (Key decisions): CHECK `NOT VALID` cho cột bắt buộc khi đã có dòng cũ; `child_code_sequences`; UNIQUE DEFERRABLE ở `child_guardians`; JSONB cho lịch sử; bảng `child_allergies`; partial unique 1 enrollment đang mở.
- Giả định (user soát): dòng `children.status` lưu (không suy mỗi lần đọc), dịch vụ giữ đồng bộ với enrollment; `intake_campus_id` chỉ bắt buộc khi Chờ xếp lớp; `OTHER` vẫn nhận ở `child_guardians` cho dữ liệu chuyển từ liên kết cũ; liên kết cũ có tên đăng nhập không phải SĐT không chuyển sang `child_guardians` (chỉ có ở seed dev — seed tự ghi); `parent_sms_logs` chỉ `SENT`/`FAILED` (outbox, nếu cần, quyết ở Phase 3 kèm ADR); `POST /children/{id}/guardians` vẫn nhận `OTHER` tới khi bỏ ở Phase 8.
- Seed dev: 4 trẻ đủ giới tính, hành chính, khai báo (có trẻ dị ứng, trẻ khai tiếp xúc lao, trẻ chờ xếp lớp ở CS-A), phụ huynh có CCCD giả `999…`; `parent.a`, `parent.b` có CCCD trên tài khoản. DB dev cũ: V10 sinh mã/trạng thái, seed bổ sung giới tính — không cần tạo lại DB.
- **Chặn Phase 2 — cần user chọn:** BE chưa có dữ liệu school-structure mà mục 4.1, 4.4, 9 cần: sĩ số lớp (`capacity`), lớp ngừng hoạt động, điểm trường ngừng hoạt động, năm học đang hoạt động (bảng `school_years` chỉ có tên, ngày bắt đầu/kết thúc). FE có ở BE giả school-config (`classes.capacity`, `classes.status`, `campuses.status`, `schoolYears.status`).
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 325 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`; `node scripts/check-ai-layer.mjs` 0 lỗi. Seed dev thử bằng psql trên PostgreSQL 17 tạm (test không chạy profile dev): DB mới (V1–V10 + seed, chạy seed 2 lần) và DB dev cũ (V1–V9 + seed cũ, rồi V10 + seed mới) đều chạy được, ra cùng dữ liệu (4 trẻ, 5 phụ huynh có CCCD, 4 hành chính, 4 khai báo); mã trẻ demo ở DB cũ theo thứ tự id cũ.

## Đặc tả (chép từ FE ngày 28/10/2026)

Nguồn: `FE:docs/integration/CHILD_ENROLLMENT_BE_SPEC.md`. "BE giả" = mock của FE (bản chạy được của từng luật); đường dẫn `FE:` nằm ở repo CareNest_FE.

### 1. Thứ tự làm đề xuất

1. **Quyền** (mục 2) và **migration** (mục 3).
2. Tra cứu không ghi: `GET /children/duplicate-check`, `POST /children/guardian-check`, `GET /children/parent-accounts/by-citizen-id`, `GET /children/placement-classes`.
3. `POST /children/enrollments` chưa có tài khoản (phụ huynh lưu "Chưa kích hoạt"), rồi thêm tạo/liên kết tài khoản + SMS trong cùng lần lưu (mục 5.3).
4. `PUT /children/{id}` + sửa tài khoản theo hồ sơ, đổi SĐT đổi tên đăng nhập (mục 5.4).
5. Nhập Excel: `POST /children/enrollments/import/preview`, `POST /children/enrollments/import`.
6. Xếp/chuyển lớp, cho nghỉ học, mở lại hồ sơ.
7. Khai báo sức khỏe `PUT /children/{id}/health-declaration`; `GET /children/{id}/profile` trả đủ trường mới.
8. Màn Tài khoản phụ huynh (mục 4.7): danh sách chờ kích hoạt, kích hoạt cả nhà, gỡ, khóa/mở, cấp lại mật khẩu qua SMS.
9. Thông báo (mục 6.2) khi có module notification; nhà cung cấp SMS (mục 6.1).
10. Lọc theo năm học cho danh sách trẻ, đợt khám, phiếu theo dõi, đón trẻ (mục 9).

### 2. Quyền và phạm vi (CHD-01, AUTH-18, AUTH-19)

Hiệu trưởng (HT): mọi việc dưới đây, toàn trường, cố định. Phó hiệu trưởng (PHT): theo quyền Hiệu trưởng giao, trong điểm trường được phân công (AUTH-02, AUTH-10, AUTH-13). Giáo viên: chỉ xem trẻ lớp mình, không làm các việc này.

Mã quyền đã chốt 28/10 (D-1 A): thêm `CHILD_ENROLL`, `CHILD_PROFILE_EDIT`, `CHILD_PLACEMENT`; giữ `PARENT_ACCOUNT_MANAGE`. PHT: mặc định bật, HT tắt được (ma trận AUTH-10). Quyền "Thêm" của FE = `CHILD_ENROLL`; quyền "Sửa" của FE = `CHILD_PROFILE_EDIT` + `CHILD_PLACEMENT` + `PARENT_ACCOUNT_MANAGE`.

| Việc | Endpoint | FE đang dùng (mảng "Hồ sơ trẻ" của PHT) | Mã quyền BE |
| --- | --- | --- | --- |
| Kiểm trùng hồ sơ, kiểm phụ huynh, tiếp nhận (form), nhập Excel | `duplicate-check`, `guardian-check`, `POST enrollments`, `import/*` | Thêm | `CHILD_ENROLL` (mới) |
| Mở lại hồ sơ trẻ đã nghỉ | `POST /children/{id}/readmit` | Thêm, ở điểm trường của trẻ | `CHILD_ENROLL` |
| Tra tài khoản phụ huynh theo CCCD | `GET parent-accounts/by-citizen-id` | Thêm hoặc Sửa | `CHILD_ENROLL` hoặc `CHILD_PROFILE_EDIT` |
| Danh sách lớp để xếp | `GET placement-classes` | Thêm hoặc Sửa (tiếp nhận có chọn lớp) | `CHILD_ENROLL` hoặc `CHILD_PLACEMENT` |
| Sửa hồ sơ, khai báo sức khỏe | `PUT /children/{id}`, `PUT .../health-declaration` | Sửa | `CHILD_PROFILE_EDIT` (mới) |
| Xếp/chuyển lớp, cho nghỉ học | `POST .../placement`, `POST .../withdraw` | Sửa | `CHILD_PLACEMENT` (mới) |
| Kích hoạt (cả khi lưu form), gỡ liên kết, khóa/mở, cấp lại mật khẩu; sửa thông tin phụ huynh đã có tài khoản | mục 4.7, `POST enrollments`, `PUT /children/{id}` | Sửa | `PARENT_ACCOUNT_MANAGE` (đã có) |

- Phạm vi trẻ của PHT: trẻ đang học trong lớp thuộc điểm trường mình **và** trẻ chờ xếp lớp có điểm trường tiếp nhận là điểm trường mình (CHD-02). BE hiện lọc theo enrollment hôm nay nên PHT không thấy trẻ chờ xếp lớp — sửa cùng việc này (`docs/modules/child.md` Known pitfalls).
- Trẻ "Đã nghỉ học": không sửa, không xếp lớp, không khai báo, không kích hoạt phụ huynh; chỉ mở lại hồ sơ.
- Ngoài phạm vi ⇒ 403. Riêng điểm trường gửi lên ngoài phạm vi khi tiếp nhận/nhập Excel ⇒ 422 `campusId` (BE giả `enrollCampusOf`).

### 3. Dữ liệu (migration)

Tên bảng/cột là đề xuất; giữ nguyên ý nghĩa nếu BE đặt tên khác.

#### 3.1 `children` — thêm cột
| Cột | Kiểu | Ý nghĩa | Rule |
| --- | --- | --- | --- |
| `code` | VARCHAR(20) UNIQUE | Mã trẻ `HS` + 2 số năm + 3 số thứ tự (vd. `HS26001`; BE giả `nextCode`) | — |
| `gender` | VARCHAR(10) | `MALE` / `FEMALE` | — |
| `status` | VARCHAR(20) | `ACTIVE` (đang học) / `PENDING_PLACEMENT` (chờ xếp lớp) / `LEFT` (đã nghỉ); lưu hoặc suy từ enrollment + nghỉ học (L-06 A) | CHD-03 |
| `intake_campus_id` | BIGINT FK `campuses`, NULL | Điểm trường của trẻ chưa có lớp; có lớp thì điểm trường theo lớp (enrollment) | CHD-02 |
| `enrolled_on` | DATE | Ngày tiếp nhận | — |
| `left_on`, `left_reason` | DATE, VARCHAR(300), NULL | Ngày, lý do nghỉ học | CHD-03 |
| `readmitted_on` | DATE NULL | Ngày mở lại hồ sơ | CHD-03 |

FE đọc `campusId` của trẻ = điểm trường hiện tại (theo lớp nếu có lớp, không thì `intake_campus_id`).

#### 3.2 `child_profiles` — hành chính, 1–1 với trẻ (mới; dữ liệu nhạy cảm, CHD-05)
`child_id` (PK, FK) · `id_number` VARCHAR(20) NULL (CCCD/mã định danh 12 số hoặc hộ chiếu 6–12 chữ và số) · `premature` (`YES`/`NO`) · `gestation_weeks` SMALLINT NULL (20–42, bắt buộc khi `YES`) · `ethnicity`, `beneficiary` VARCHAR(100) · `payment_source` = `BHYT` (luôn, không nhập) · `blood_type` VARCHAR(100) NULL · `address_province`, `address_ward` VARCHAR(100), `address_street` VARCHAR(300).
Bắt buộc trừ `id_number`, `blood_type` (và `gestation_weeks` khi không sinh non): không có hồ sơ thiếu, nên cột bắt buộc để NOT NULL (mục 3.9). Danh sách trẻ không trả phần này; phiếu khám sức khỏe đọc từ đây.

#### 3.3 `child_guardians` — phụ huynh trên hồ sơ trẻ (mới, AUTH-20)
Phụ huynh có trên hồ sơ trước khi có tài khoản.
`id` · `child_id` FK · `position` (thứ tự) · `full_name` VARCHAR(150) · `relationship` VARCHAR(30) · `phone` VARCHAR(10) chuẩn hóa · `citizen_id` VARCHAR(12) · `email` VARCHAR(255) NULL · `user_id` FK `user_accounts` NULL (tài khoản đã liên kết) · `created_at`, `updated_at`.
- UNIQUE (`child_id`, `phone`), UNIQUE (`child_id`, `citizen_id`): hai phụ huynh của một trẻ không chung SĐT/CCCD. Mỗi trẻ tối đa 3 phụ huynh (D-5b A) ⇒ thêm thứ 4 trả 422 `guardians`.
- **Quan hệ — 6 loại cho cả hồ sơ lẫn tài khoản (D-5a B):** `FATHER` Bố, `MOTHER` Mẹ, `GRANDFATHER` Ông, `GRANDMOTHER` Bà, `SIBLING` Anh/chị, `GUARDIAN` Người giám hộ. `guardian_child_links.relationship` mở rộng CHECK (V9 hiện 4 mã) sang 6 mã, giữ `OTHER` chỉ cho dữ liệu cũ. FE hiện gửi/nhận `relation` là nhãn tiếng Việt trên hồ sơ và `relationship` là mã ở màn Tài khoản phụ huynh; BE có thể dùng mã ở mọi nơi rồi FE đổi khi nối API.
- `accountStatus` trả cho FE: `NOT_ACTIVATED` khi chưa có liên kết `guardian_child_links` với trẻ; có thì `user_accounts.status` (`ACTIVE` / `INACTIVE`).
- `guardian_child_links` giữ vai nguồn scope của phụ huynh (AUTH-04); kích hoạt = tạo liên kết + ghi `user_id`; gỡ liên kết = xóa liên kết, giữ dòng `child_guardians` làm thông tin liên hệ.

#### 3.4 `user_accounts` — sửa
- Thêm `citizen_id` VARCHAR(12) NULL, UNIQUE (chỉ phụ huynh): khóa nhận diện (AUTH-18). Tài khoản cũ chưa có CCCD được bổ sung khi hồ sơ trẻ ghi CCCD cho phụ huynh đó.
- `username` của phụ huynh **đổi được** khi đổi SĐT (AUTH-21) — sửa mô tả "không đổi được" ở `docs/database/DATA_DICTIONARY.md`; cách làm Keycloak ở mục 6.3.
- `email` phụ huynh lưu chữ thường (đã làm, L-17).

#### 3.5 `enrollments` — dùng khi xếp/chuyển lớp
Xếp lớp mở enrollment mới; chuyển lớp đóng enrollment đang mở rồi mở enrollment mới (một trẻ một lớp tại một ngày; dữ liệu cũ theo lớp tại ngày đó — CMR-04). Thêm `reason` VARCHAR(300) NULL (lý do chuyển lớp, bắt buộc khi chuyển) và `created_by`. Ngày kết thúc/bắt đầu khi chuyển trong ngày: BE chốt (gợi ý: lớp mới tính từ hôm nay).

#### 3.6 `child_health_declarations` — khai báo sức khỏe, 1–1 với trẻ (mới, CHD-06, HLT-06)
Trạng thái mỗi mục: `REPORTED` (Có) / `NONE_REPORTED` (Không có) / `NOT_PROVIDED` (Chưa cung cấp).
- Dị ứng: `allergies_state` + danh sách tên (bảng con `child_allergies` hoặc JSON). Bếp và điểm danh đọc danh sách này ngay khi lưu (không chờ duyệt).
- Chế độ ăn: `diet_state`, `diet_text` (≤500). Thông tin khác: `other_notes_state`, `other_notes_text` (≤500).
- Tiền sử bản thân, gia đình: `*_state` chỉ `REPORTED`/`NONE_REPORTED` khi lưu, `*_text` (≤300, bắt buộc khi `REPORTED`).
- Tiếp xúc người bệnh lao: `tb_contact_state` (`REPORTED`/`NONE_REPORTED`), `tb_contact_date` (bắt buộc khi `REPORTED`).
- `declared_by`, `declared_at`.
- Lịch sử `child_health_declaration_history`: `action` (`DECLARED` / `UPDATED` / `UPDATED_FROM_FOLLOW_UP`), `user_id`, `at`, `note` (lý do), `allergy_change`, `history_change` (JSON trước/sau).

#### 3.7 `child_history` — lịch sử hồ sơ (mới)
`child_id`, `action` (`ENROLLED` / `UPDATED` / `WITHDRAWN` / `READMITTED`), `user_id`, `at`, `note` (lý do), `changes` JSON `[{ field, label, from, to }]`. CCCD trẻ trong lịch sử chỉ hiện 3 số cuối (BE giả `profileChanges`).

#### 3.8 `parent_sms_logs` (mới)
`user_id`, `phone`, `purpose` (`ACTIVATION` / `PASSWORD_RESET` / `USERNAME_CHANGED`), `status` (`SENT` / `FAILED`), `sent_at`. Không lưu mật khẩu. FE hiện trạng thái lần gửi gần nhất trên từng phụ huynh (`smsStatus`, `smsAt`, chỉ HT/PHT).

#### 3.9 Chuyển dữ liệu đang có (D-5c B, user chốt 28/10: nhập đầy đủ thông tin trẻ — `docs/business/BUSINESS_RULES.md` CHD-08)
- Không có hồ sơ trẻ thiếu thông tin: trẻ nào cũng phải đủ giới tính, hành chính, khai báo sức khỏe, phụ huynh có CCCD. BE hiện chỉ có trẻ trong seed dev (`src/main/resources/db/dev/R__dev_seed.sql`, họ tên + ngày sinh) ⇒ sửa seed dev cho đủ thông tin (như BE giả đã làm với dữ liệu demo); DB dev cũ thì chạy lại seed hoặc tạo lại DB.
- Khi bắt đầu dùng thật, trường nhập toàn bộ trẻ bằng file Excel đủ thông tin (mục 4.3) trước khi dùng các chức năng khác; không làm chức năng "bổ sung sau".
- Trẻ có sẵn ngoài seed (nếu có): sinh `code`; `status` = `ACTIVE` nếu có enrollment đang mở, không thì `PENDING_PLACEMENT`; `enrolled_on` = ngày enrollment đầu hoặc `created_at`; phải nhập đủ thông tin trước khi dùng như trên.
- Mỗi `guardian_child_links` ⇒ một dòng `child_guardians` (họ tên, SĐT = `username`, email từ `user_accounts`; quan hệ theo mã liên kết; CCCD nhập bổ sung cùng đợt). Tài khoản chưa có CCCD khớp theo SĐT (BE giả `withAccounts`, `attachAccounts`).

### 4. API

Chung:
- Envelope `{ code, desc, data }` như hiện tại. ID theo kiểu BE (Long); FE giữ nguyên giá trị nhận được.
- **Lỗi theo ô:** BE giả trả `422` (thiếu/sai nghiệp vụ) và `409` (trùng) với `details` dạng map `{ "<đường dẫn ô>": "<câu lỗi>" }`; contract BE hiện là `400` + `data` danh sách `{ field, message }` ⇒ mục 7.2.
- Đường dẫn ô FE dùng: `fullName`, `dateOfBirth`, `gender`, `guardians` (lỗi chung của danh sách), `guardians.{i}.fullName|relation|phone|email|idNumber`, `profile.idNumber|premature|gestationWeeks|ethnicity|beneficiary|bloodType|address.province|address.ward|address.street`, `declaration.allergies|diet|otherNotes|medicalHistory.self|medicalHistory.family|medicalHistory.tbContact|medicalHistory.tbContactDate`, `classId`, `campusId`, `reason`, `leftAt`, `file`.

Kiểu dữ liệu dùng lại:
- `Guardian` (gửi lên): `{ fullName, relation, phone, idNumber, email }` — họ tên ≤150; quan hệ bắt buộc, một trong Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ (D-5a B; khác ⇒ 422 `guardians.{i}.relation`); SĐT 10 số bắt đầu 0 (nhận cả `+84`, khoảng trắng, dấu chấm, gạch); CCCD đúng 12 chữ số, bắt buộc; email tùy chọn, đúng định dạng. Mỗi trẻ 1–3 phụ huynh (D-5b A; quá 3 ⇒ 422 `guardians`).
- `Profile`: `{ idNumber, premature, gestationWeeks, ethnicity, beneficiary, bloodType, address: { province, ward, street } }` (mục 3.2; `paymentSource` BE tự đặt `BHYT`).
- `Declaration`: `{ allergies: { state, items[] }, diet: { state, text }, otherNotes: { state, text }, medicalHistory: { self: { state, text }, family: { state, text }, tbContact: { state, contactDate } } }` (mục 3.6). Ngày tiếp xúc lao: không ở tương lai, không trước ngày sinh.
- `ChildView` (trả về): trường hiện có + `code`, `gender`, `status`, `campusId`, `classId`, `className`, `schoolYear`, `enrolledAt`, `leftAt`, `leftReason`, `readmittedAt`, `allergies[]`, `hasDeclaration`, `profile` (chỉ khi xem hồ sơ, không có ở danh sách), `history[]`, `guardians[]` = `{ fullName, relation, phone, idNumber, email, accountStatus, smsStatus, smsAt }` (HT/PHT); vai khác chỉ `{ fullName, relation, phone, idNumber, email }` (CCCD phụ huynh mọi vai xem như SĐT — user chốt 2026-10-21).
- Trẻ: họ tên ≤100; ngày sinh không ở tương lai, từ 2018-01-01; giới tính bắt buộc.

#### 4.1 Tiếp nhận bằng form

**`GET /children/duplicate-check?fullName&dateOfBirth&guardianIdNumbers=a,b&excludeId`** (mới, CHD-04; BE giả `checkDuplicate`)
- Trùng = cùng họ tên (giữ dấu; không phân biệt hoa thường, khoảng trắng thừa), cùng ngày sinh và CCCD của ít nhất một phụ huynh.
- Trả `null`; hoặc `{ kind: "ACTIVE" | "LEFT", sameCampus: false }` khi hồ sơ trùng ở điểm trường người hỏi không quản lý (không lộ hồ sơ); hoặc `{ kind, sameCampus: true, id, code, fullName, leftAt, canReadmit }`. `ACTIVE` (đang học/chờ xếp lớp) ⇒ FE chặn tiếp nhận; `LEFT` ⇒ gợi ý mở lại hồ sơ cũ.

**`POST /children/guardian-check`** body `{ guardians: [Guardian] }` (mới; BE giả `checkGuardianConflicts`)
- Kiểm như khi lưu (mục 5.2), không ghi gì. Hợp lệ ⇒ `null`; lệch ⇒ `409` + `details` `guardians.{i}.phone|idNumber`. Dùng POST để CCCD, SĐT không nằm trên URL.

**`GET /children/parent-accounts/by-citizen-id?idNumber`** (mới; BE giả `findParentAccountByCitizenId`)
- Trả `null` hoặc `{ id, fullName, phone, idNumber, email, status, children: [{ id, code, fullName, className }], otherChildrenCount, relation }`: `children` chỉ trẻ (chưa nghỉ) trong phạm vi người hỏi, trẻ ngoài phạm vi chỉ đếm; `relation` = quan hệ ghi ở hồ sơ anh/chị (gợi ý).

**`GET /children/placement-classes`** (mới; BE giả `getPlacementClasses`) — lớp đang hoạt động **của năm học đang hoạt động** trong phạm vi, kèm `enrolledCount` (số trẻ đang học); không nhận tham số năm (mục 9).

**`POST /children/enrollments`** (mới; BE giả `enrollChild`)
- Body: `{ child: { fullName, dateOfBirth, gender, guardians: [Guardian], profile: Profile }, declaration: Declaration, classId?, campusId? }`.
- Lớp không bắt buộc. Có `classId`: lớp đang hoạt động, thuộc năm học đang hoạt động (mục 9), chưa đủ sĩ số (`capacity`), thuộc phạm vi; trẻ theo điểm trường của lớp; `campusId` khác điểm trường của lớp ⇒ 422 `classId`. Không có lớp: `campusId` bắt buộc với người được giao toàn trường; PHT một điểm trường bỏ trống = điểm trường của mình; điểm trường không tồn tại/ngừng hoạt động/ngoài phạm vi ⇒ 422 `campusId` (L-03, CHD-02).
- Lưu trong một giao dịch: trẻ (`code`, `status` = `ACTIVE` nếu có lớp, không thì `PENDING_PLACEMENT`, `enrolled_on` = hôm nay), hành chính, khai báo sức khỏe (dị ứng có hiệu lực ngay — CHD-06), lịch sử `ENROLLED`, enrollment nếu có lớp, phụ huynh (mục 5.1–5.3, 5.5).
- Trả `ChildView` + `guardianAccounts: [{ fullName, phone, result, phoneChanged, smsStatus }]` theo thứ tự phụ huynh: `result` = `CREATED` (tạo tài khoản, gửi SMS) / `LINKED` (liên kết tài khoản có sẵn) / `NOT_ACTIVATED` (người lưu không có quyền kích hoạt); `phoneChanged` = tài khoản có sẵn đổi tên đăng nhập sang SĐT mới (mục 5.4); `smsStatus` = `SENT` / `FAILED` / `null`.
- Lỗi: 403 · 422 theo ô · 409 trùng hồ sơ (CHD-04; câu lỗi, không `details`) · 409 `guardians.{i}.phone|idNumber` (mục 5.2).
- Tác dụng phụ: thông báo `CHILD_TB_CONTACT` nếu khai Có tiếp xúc lao; `CHILD_PLACED` cho giáo viên lớp nếu có lớp (mục 6.2); SMS (mục 6.1).

#### 4.2 Sửa hồ sơ — `PUT /children/{id}` (mới; BE giả `updateChild`)
- Body: `{ child: { fullName, dateOfBirth, gender, guardians: [Guardian], profile }, reason }`; `reason` bắt buộc.
- Kiểm như tiếp nhận (trừ khai báo sức khỏe) + trùng hồ sơ trừ chính trẻ (409) + mục 5.2 (409), thêm:
  - Phụ huynh đã có tài khoản đổi **cùng lúc** CCCD và SĐT ⇒ 422 `guardians.{i}.idNumber` (đổi từng thông tin, hoặc gỡ ở màn Tài khoản phụ huynh rồi thêm người mới).
  - Bỏ khỏi danh sách một phụ huynh đang liên kết tài khoản với trẻ ⇒ 422 `guardians` (gỡ liên kết ở màn Tài khoản phụ huynh trước — AUTH-19).
- Phụ huynh mới chưa có tài khoản ⇒ "Chưa kích hoạt", kích hoạt sau (AUTH-20); CCCD trùng tài khoản có sẵn ⇒ liên kết ngay (AUTH-18).
- Thông tin khác tài khoản ⇒ cập nhật tài khoản (mục 5.4).
- Lịch sử `UPDATED` kèm `changes` từng ô (cả hành chính).
- Trả `ChildView` + `accountChanges: [{ fullName, phone, phoneChanged, smsStatus }]` (chỉ phụ huynh có tài khoản vừa đổi theo).

#### 4.3 Nhập Excel
**`POST /children/enrollments/import/preview`** multipart `file` (mới; BE giả `previewImport`, `enrollmentImport.js`)
- Nhận `.xlsx` (BE đọc) và `.csv`; cột theo mẫu `IMPORT_COLUMNS` (`FE:src/models/children/childrenConstants.js`, mỗi dòng 1 trẻ + 1 phụ huynh; cột bắt buộc có "Số CCCD phụ huynh", hành chính, tiền sử, tiếp xúc lao); ngày `dd/mm/yyyy`; "Không" = không có, để trống = chưa cung cấp. Cột quan hệ nhận 6 giá trị, so không dấu, không phân biệt hoa thường, bỏ khoảng trắng/dấu "/" ("anh chị" = "Anh/chị"); giá trị khác báo lỗi ở dòng đó (D-5a B; BE giả `toRelation`). Tối đa 500 dòng.
- Thiếu cột / không có dòng / quá 500 dòng / sai loại file ⇒ 422 `file`.
- Trả `{ supported, fileName, rows: [{ rowNo, raw, payload: { child, declaration }, errors: [string], notes: [string] }] }`. `errors`: lỗi như tiếp nhận + trùng hồ sơ (CHD-04) + trùng dòng khác trong file + CCCD/SĐT lệch dòng trước trong file. `notes`: phụ huynh trùng CCCD tài khoản (dùng thông tin tài khoản, liên kết khi nhập); trẻ trùng hồ sơ đã nghỉ (nên mở lại hồ sơ cũ).

**`POST /children/enrollments/import`** body `{ rows: [{ child, declaration }], campusId }` (mới; BE giả `importChildren`)
- `campusId` như tiếp nhận không có lớp. Kiểm lại mọi dòng; một dòng lỗi ⇒ 422 `file`, không nhập dòng nào.
- Mỗi dòng: trẻ `PENDING_PLACEMENT` ở điểm trường đó, khai báo sức khỏe như nhập, thông báo lao như tiếp nhận. Phụ huynh trùng CCCD tài khoản ⇒ dùng họ tên/SĐT/email của tài khoản (không sửa tài khoản từ file), liên kết ngay; còn lại "Chưa kích hoạt" — **không tạo tài khoản, không gửi SMS** (AUTH-20).
- Trả `{ created, ids }`.

#### 4.4 Xếp lớp, cho nghỉ học, mở lại hồ sơ
**`POST /children/{id}/placement`** body `{ classId, reason }` (mới; BE giả `placeChild`)
- Trẻ chưa nghỉ; `classId` bắt buộc, khác lớp hiện tại; `reason` bắt buộc khi chuyển lớp (trẻ đang có lớp); trẻ phải có khai báo sức khỏe; lớp đang hoạt động, thuộc năm học đang hoạt động (xếp lớp năm sau ở màn Xếp lớp năm học mới), chưa đủ sĩ số, trong phạm vi ⇒ lỗi 422 `classId` / `reason`.
- Đóng/mở enrollment (mục 3.5); lớp ở điểm trường khác = chuyển điểm trường (CHD-02); `status` = `ACTIVE`; thông báo `CHILD_PLACED`. Trả `ChildView`.

**`POST /children/{id}/withdraw`** body `{ leftAt, reason }` (mới; BE giả `withdrawChild`)
- `leftAt` ngày hợp lệ, không ở tương lai, không trước ngày tiếp nhận; `reason` bắt buộc ≤300 (422 `leftAt` / `reason`).
- `status` = `LEFT`, đóng enrollment, lịch sử `WITHDRAWN`, không xóa dữ liệu; phiếu theo dõi sức khỏe đang mở tự đóng (module health); thông báo `CHILD_WITHDRAWN`. Trả `ChildView`.

**`POST /children/{id}/readmit`** body `{ reason }` (mới; BE giả `readmitChild`, quyền `canReadmitChild`)
- Chỉ trẻ `LEFT` (khác ⇒ 409); `reason` bắt buộc ≤300 (422 `reason`); quyền tiếp nhận ở điểm trường của trẻ.
- `status` = `PENDING_PLACEMENT`, bỏ lớp, `readmitted_on` = hôm nay, lịch sử `READMITTED`; giữ mã trẻ, lịch sử, sức khỏe, tài khoản phụ huynh (CHD-03). Trả `ChildView`.

#### 4.5 Khai báo sức khỏe — `PUT /children/{id}/health-declaration` (mới; BE giả `saveDeclaration`)
- Body `{ declaration: Declaration, reason }`; `reason` bắt buộc khi đã có khai báo; lỗi 422 theo ô (không tiền tố `declaration.`).
- Ghi lịch sử kèm `allergyChange` / `historyChange`; dị ứng mới có hiệu lực ngay cho bếp, điểm danh.
- Thông báo: chuyển sang Có tiếp xúc lao ⇒ `CHILD_TB_CONTACT`; đã có ngày mà đổi ngày ⇒ `CHILD_TB_CONTACT_DATE_CHANGED`; lưu lại không đổi thì không báo.
- Trả khai báo đã lưu.

#### 4.6 Xem hồ sơ — `GET /children/{id}/profile`
Trả `{ child: ChildView, cls, declaration (+ declaredByName), placements: [{ fromClassName, toClassName, userName, at, reason }] }` (BE giả `getChildDetail`). `GET /children/{id}` hiện có chỉ trả id, họ tên, ngày sinh, lớp.

#### 4.7 Màn Tài khoản phụ huynh (`/children/activation`)
Tên endpoint mới là đề xuất; FE chưa có file API cho màn này (`FE:src/services/account/guardianService.js` trả 501 khi không chạy BE giả).

| Endpoint | Việc | BE giả |
| --- | --- | --- |
| `GET /children?campusId&page&size` (đã có) | Thêm trẻ chờ xếp lớp của điểm trường (CHD-02), bỏ trẻ đã nghỉ; mỗi trẻ thêm `guardians[]` (như dòng dưới), `canManage` (người xem kích hoạt/gỡ/khóa được) | `searchChildren` |
| `GET /children/{id}/guardians` (đã có) | Phụ huynh đã liên kết: thêm `idNumber`, `relation` (nhãn trên hồ sơ) cạnh `relationship` (mã) | `listGuardians` |
| `GET /children/{id}/guardians/pending` (mới) | Phụ huynh trên hồ sơ chưa có tài khoản liên kết với trẻ: `[{ fullName, relation, phone, idNumber, email, hasAccount, problems[] }]`; `hasAccount` = đã có tài khoản (trẻ khác), kích hoạt chỉ liên kết; `problems` = thông tin còn thiếu/sai, sửa ở hồ sơ trẻ | `listPendingGuardians` |
| `POST /children/{id}/guardians/activate` (mới) | Kích hoạt **mọi** phụ huynh chờ của trẻ một lần; một người sai thì không tạo ai. Trả `[{ userId, username, fullName, idNumber, relationship, relation, status, linkedAt, phone, newAccount, smsStatus }]`. Không có ai chờ ⇒ 409; thông tin trên hồ sơ thiếu/sai ⇒ 400; lệch danh tính ⇒ 409 `details.idNumber` | `activateGuardians` |
| `DELETE /children/{id}/guardians/{userId}` (đã có) | Gỡ liên kết: phụ huynh mất quyền xem trẻ ngay, tài khoản giữ nguyên, phụ huynh vẫn trên hồ sơ trẻ làm liên hệ (`accountStatus` `NOT_ACTIVATED`) — AUTH-19 | `removeGuardian` |
| `PATCH /parent-accounts/{userId}/status` (đã có) | `ACTIVE` / `INACTIVE`; trạng thái phụ huynh trên mọi hồ sơ trẻ đổi theo | `updateParentStatus` |
| `POST /parent-accounts/{userId}/temporary-password` (đã có, **đổi**) | Cấp mật khẩu tạm mới, gửi SMS, bắt đổi lần đầu, hủy mọi phiên; trả `{ userId, username, phone, smsStatus }`, **bỏ `temporaryPassword`** (L-15). Dùng cho "Gửi lại mật khẩu" | `resetParentPassword` |
| `POST /children/{id}/guardians` (đã có) | **Bỏ** (user chốt 28/10, C-4 A): phụ huynh nhập ở hồ sơ trẻ rồi kích hoạt (AUTH-20). Giữ phần xử lý tạo/liên kết tài khoản trong service để dùng chung cho kích hoạt và lưu form tiếp nhận | đã bỏ `addGuardian`; dùng chung `linkGuardian` |

Quyền: kích hoạt, gỡ, khóa/mở, cấp lại mật khẩu khi người làm quản lý điểm trường của ít nhất một trẻ (chưa nghỉ) đang liên kết với phụ huynh đó (AUTH-19).

#### 4.8 Bảng tổng quan PHT — `GET /children/parent-accounts?status&keyword` (mới; BE giả `listParentAccounts`)
Mỗi phụ huynh trên hồ sơ trẻ (chưa nghỉ) trong phạm vi: `{ childId, childCode, childName, campusId, className, guardianIndex, guardian, username }`; FE đếm `status=NOT_ACTIVATED`.

### 5. Luật xử lý phụ huynh (dùng chung)

#### 5.1 Nhận ra tài khoản có sẵn (AUTH-18; BE giả `attachAccounts`, `withAccounts`)
- Theo CCCD của phụ huynh. Không khớp CCCD: ở Sửa hồ sơ lấy tài khoản của phụ huynh cũ cùng vị trí (đổi CCCD của người đã có tài khoản); tài khoản cũ chưa có CCCD khớp theo SĐT.
- Không tin `accountId` client gửi.
- Form tiếp nhận, Sửa hồ sơ: giữ họ tên/SĐT/email client gửi, tài khoản đổi theo (mục 5.4). Nhập Excel, và form tiếp nhận khi người lưu không có quyền kích hoạt (mục 5.5): dùng họ tên/SĐT/email của tài khoản, không sửa tài khoản.
- SĐT so khớp sau chuẩn hóa: bỏ khoảng trắng, dấu chấm, gạch, ngoặc; `+84` ⇒ `0` (tên đăng nhập = SĐT 10 số bắt đầu 0).

#### 5.2 Một người một tài khoản (BE giả `identityConflict`, `guardianConflicts`) ⇒ 409
So với mọi tài khoản phụ huynh và mọi phụ huynh trên hồ sơ trẻ khác (bỏ bản ghi của chính tài khoản đang sửa):
- CCCD đã ghi cho người có SĐT khác ⇒ `guardians.{i}.idNumber` "Số CCCD này đã ghi cho phụ huynh khác (số điện thoại khác)…".
- SĐT đã thuộc người có CCCD khác ⇒ `guardians.{i}.phone` "Số điện thoại này đã thuộc phụ huynh có số CCCD khác…".
- Làm rõ AUTH-18 "CCCD hoặc SĐT đã là phụ huynh ⇒ chỉ thêm liên kết": SĐT trùng chỉ liên kết khi tài khoản đó chưa có CCCD hoặc cùng CCCD; khác CCCD là 409 như trên.
- Trong cùng hồ sơ: hai phụ huynh không chung SĐT/CCCD ⇒ 422 `guardians`.

#### 5.3 Tạo / liên kết tài khoản (kích hoạt; BE giả `activatePendingGuardians`, `linkGuardian`)
- Đầu vào: phụ huynh trên hồ sơ chưa liên kết tài khoản với trẻ. Kiểm lại họ tên, SĐT, CCCD, quan hệ; một người sai ⇒ không tạo ai.
- Có tài khoản (SĐT/CCCD khớp): chỉ thêm liên kết, giữ mật khẩu, không gửi SMS; bổ sung CCCD nếu tài khoản chưa có.
- Chưa có: tạo tài khoản PARENT (`username` = SĐT chuẩn hóa, `citizen_id`, họ tên, email chữ thường), mật khẩu tạm hệ thống sinh, bắt đổi lần đầu, gửi SMS tới SĐT; tạo liên kết (`relationship` theo quan hệ trên hồ sơ, 6 mã như mục 3.3).
- Khi nào: lưu form tiếp nhận (cùng giao dịch với hồ sơ) và nút kích hoạt ở màn Tài khoản phụ huynh. Nhập Excel, Sửa hồ sơ thêm phụ huynh mới: không kích hoạt (AUTH-20).

#### 5.4 Sửa thông tin phụ huynh đã có tài khoản (AUTH-21; BE giả `applyGuardianEdits`, `differsFromAccount`)
- Họ tên, SĐT, email (so không phân biệt hoa thường), CCCD khác tài khoản ⇒ cập nhật tài khoản và bản sao phụ huynh đó ở mọi hồ sơ trẻ khác, cùng giao dịch.
- Đổi SĐT ⇒ `username` = SĐT mới, mật khẩu tạm mới, bắt đổi lần đầu, hủy mọi phiên, SMS tới SĐT mới (mục 6.3). Báo kết quả qua `guardianAccounts[].phoneChanged` (tiếp nhận) hoặc `accountChanges` (Sửa hồ sơ).
- Số mới trùng người khác ⇒ 409 (mục 5.2); đổi cùng lúc CCCD và SĐT ⇒ 422 (mục 4.2).

#### 5.5 Người lưu không có quyền kích hoạt (P-18 = A, AUTH-20)
- Có quyền tiếp nhận nhưng không có `PARENT_ACCOUNT_MANAGE` ở điểm trường nhận trẻ ⇒ vẫn lưu; phụ huynh chưa có tài khoản ⇒ `NOT_ACTIVATED` (người có quyền kích hoạt sau); phụ huynh trùng CCCD tài khoản ⇒ vẫn liên kết.
- **User đồng ý 28/10 (C-2):** trường hợp này không sửa tài khoản có sẵn — dùng họ tên/SĐT/email của tài khoản như nhập Excel, không đổi tên đăng nhập, không cấp mật khẩu mới (BE giả `enrollChild`).

### 6. Tác dụng phụ

#### 6.1 SMS (L-15)
- Chốt 28/10 (D-2 A): làm cổng SMS giả trước (interface gửi SMS trong `integration/`, bản giả chỉ ghi lại như đã gửi, không gửi thật) để làm và test đủ chức năng; chọn nhà cung cấp thật trước khi đưa vào dùng. Tin gồm tên đăng nhập và mật khẩu tạm; mật khẩu không trả về giao diện, không ghi log.
- Gửi khi: tạo tài khoản (mục 5.3), đổi SĐT (mục 5.4), cấp lại mật khẩu (mục 4.7).
- SMS lỗi không hủy hồ sơ hay tài khoản: trả `smsStatus = FAILED`, FE cho "Gửi lại mật khẩu" (= cấp lại mật khẩu). Gợi ý gửi sau khi giao dịch DB thành công (outbox).
- BE giả: SĐT kết thúc `99` lỗi ở lần gửi đầu (chỉ để thử đường gửi lại).

#### 6.2 Thông báo (CHD-07 và thông báo lớp)
| Loại | Khi nào | Người nhận | Nội dung (BE giả) |
| --- | --- | --- | --- |
| `CHILD_TB_CONTACT` | Khai Có tiếp xúc người bệnh lao (tiếp nhận, nhập Excel, sửa khai báo) | HT, PHT điểm trường, giáo viên lớp, người lưu | "Phụ huynh khai {tên trẻ} có tiền sử tiếp xúc với người bệnh lao (gần nhất ngày {ngày}). Nhà trường trao đổi với phụ huynh về việc đưa trẻ đi khám sàng lọc; bác sĩ xem lại ở đợt khám tới." |
| `CHILD_TB_CONTACT_DATE_CHANGED` | Ngày tiếp xúc đã có đổi sang ngày khác (sửa khai báo, kết luận phiếu khám) | HT, PHT điểm trường, người lưu | "Ngày tiếp xúc gần nhất với người bệnh lao của {tên trẻ} đổi từ {ngày cũ} sang {ngày mới} ({nơi đổi})." |
| `CHILD_PLACED` | Tiếp nhận có lớp, xếp/chuyển lớp | Giáo viên lớp mới | "{tên trẻ} đã được xếp vào {lớp}." |
| `CHILD_WITHDRAWN` | Cho nghỉ học | Giáo viên lớp | "{tên trẻ} nghỉ học từ ngày {ngày}." (+ phiếu theo dõi đã tự đóng) |

Câu trung tính, không chẩn đoán (CHD-07). Đường dẫn: hồ sơ trẻ hoặc trang khai báo sức khỏe.

#### 6.3 Keycloak
- Tạo tài khoản phụ huynh như `POST /children/{id}/guardians` hiện nay (tạo user, gán role, mật khẩu tạm + bắt đổi), nhưng có thể nhiều người trong một lần lưu ⇒ lỗi ở bất kỳ bước nào sau khi đã tạo user Keycloak thì xóa mọi user vừa tạo (bù trừ; `docs/modules/identity-access.md` Known pitfalls).
- Đổi tên đăng nhập (AUTH-21): realm cần cho đổi username (Keycloak "Edit username", `editUsernameAllowed`) để Admin API đổi `username`; sau đó đặt mật khẩu tạm mới, hủy phiên, cập nhật `user_accounts.username`; lỗi giữa chừng thì trả lại username cũ. BE cần thử trên bản Keycloak đang dùng.

### 7. Điểm cần thống nhất và kết quả chốt
Chốt 28/10/2026: C-5 (mục 2), D-1, D-7 (mục 1), D-2 (mục 4), D-4 (mục 5), D-5 (mục 6–8), D-6 (mục 9). Mục 3 là việc kỹ thuật (Phase 4 của plan BE).
1. **Mã quyền — đã chốt (D-1 A).** Bảng ở mục 2. **Phạm vi PHT — đã chốt (D-7 A):** PHT chỉ trong campus được phân công (AUTH-02), "Hồ sơ trẻ" toàn trường chỉ HT có. FE còn phải bỏ lựa chọn "toàn trường" khi HT giao "Hồ sơ trẻ" cho PHT (màn Phân công Phó hiệu trưởng, nhánh G94-179) — làm khi nối API.
2. **Lỗi theo ô — đã chốt 28/10 (C-5 A).** BE: 400 + `data` danh sách `{ field, message }` (`docs/contracts/ERROR_CONTRACT.md`, không có 422). FE (13 file BE giả, các trang tiếp nhận): 422 + `details` map; trang tiếp nhận chỉ đọc lỗi ô khi 422/409. Chốt: BE giữ 400, `field` theo đường dẫn ô ở mục 4 (không tiền tố `child.`, chỉ số dạng `guardians.0.phone`), 409 trùng cũng trả danh sách; FE đổi danh sách thành map ở `axiosClient` và coi 400 như 422 (`FE:docs/knowledge/KNOWN_ISSUES.md` FE-KI-003).
3. **Đổi tên đăng nhập Keycloak** (mục 6.3) — việc kỹ thuật, BE thử khi làm; không làm được thì báo user chọn cách khác.
4. **Nhà cung cấp SMS — đã chốt (D-2 A):** cổng SMS giả trước, chọn nhà cung cấp trước khi dùng thật (mục 6.1).
5. **`GET /children` — đã chốt (D-4 A):** một endpoint phân trang, trả đủ trường cho danh sách trẻ (`ChildView` không có `profile`) và cho màn Tài khoản phụ huynh (`guardians[]`, `canManage`); lọc như hiện có (`schoolYear`, `campusId`, `classId`, `status`, `keyword`, mức sức khỏe). FE: khi nối API, màn Danh sách trẻ chuyển sang phân trang từ BE (hiện lọc, chia trang ở trình duyệt).
6. **Quan hệ — đã chốt (D-5a B):** 6 loại cho cả hồ sơ lẫn tài khoản (mục 3.3); Excel chỉ nhận 6 giá trị (mục 4.3).
7. **Số phụ huynh tối đa — đã chốt (D-5b A):** BE chặn quá 3 (422 `guardians`); BE giả đã kiểm.
8. **Trẻ có trước tính năng — đã chốt (D-5c B):** nhập đầy đủ thông tin trẻ, không có hồ sơ thiếu (mục 3.9).
9. **Giả định đang chạy ở BE giả — user đồng ý 28/10 (D-6, C-2):** Excel không sửa tài khoản có sẵn; không đổi cùng lúc CCCD và SĐT của phụ huynh đã có tài khoản; phụ huynh có tài khoản không xóa ở hồ sơ (gỡ liên kết ở màn Tài khoản phụ huynh); người không có quyền kích hoạt không sửa tài khoản có sẵn (mục 5.5).

### 8. Ngoài phạm vi tài liệu này
Ngoài phần năm học ở mục 9, contract vẫn ở chú thích các file API: danh sách trẻ (`listChildren`, `latestHealth`), sổ sức khỏe, cân đo, phân tích xu hướng AI (`FE:src/services/children/api/childrenApi.js`), đợt khám, phiếu khám, phiếu theo dõi (`FE:src/services/children/api/healthCheckApi.js`), đón trẻ (`FE:src/services/pickup/api/pickupApi.js`). Quyền theo mảng của PHT (nhánh G94-179) chỉ ghi ở mục 7.1.

### 9. Năm học (user chốt 2026-10-28, Y-1..Y-5)

Header có ô chọn năm học (mặc định năm đang hoạt động). Dữ liệu xem theo năm đang chọn; việc trong ngày và việc ghi (tiếp nhận, xếp lớp, đón trẻ) luôn ở năm đang hoạt động. Rule BE: CHD-09 (Y-2), CHD-10 (Y-3, Y-5), HLT-08 (Y-4), ATT-09 (Y-1). Plan: `FE:docs/plans/completed/2026-10-28-school-year-scope.md`; BE giả dùng chung `FE:src/mocks/schoolYearScope.js`.

- **Ngày thuộc năm nào:** năm học gần nhất đã bắt đầu tính tới ngày đó (kỳ hè giữa hai năm tính vào năm vừa qua) — để ngày nào cũng thuộc đúng một năm (BE giả `schoolYearIdOfDate`).
- **Y-2 · Danh sách trẻ `GET /children?schoolYear=X`:** trẻ có lớp thuộc năm X, trả `classId`, `className`, `ageGroupId`, `schoolYear`, `campusId` của lớp cuối cùng trong năm X (BE lấy từ `enrollments`; CMR-04). Trẻ chờ xếp lớp chỉ có ở năm đang hoạt động; trẻ nghỉ khi chưa có lớp ở năm của ngày nghỉ. `status` vẫn là trạng thái hiện tại; phạm vi xem theo điểm trường hiện tại của trẻ. Không có `schoolYear` ⇒ mọi trẻ trong phạm vi. BE giả: `classInYear`, `inSchoolYear` (lấy lịch sử từ `childPlacements` và lịch sử chuyển năm của Xếp lớp năm học mới). Bảng tổng quan HT/PHT đếm số trẻ theo cùng tham số.
- **Y-3 · Tiếp nhận, xếp lớp:** chỉ vào lớp của năm đang hoạt động — `placement-classes` chỉ trả lớp năm đó; `POST /children/enrollments`, `POST /children/{id}/placement` với lớp năm khác ⇒ 422 `classId` (mục 4.1, 4.4). Xếp lớp cho năm sau thuộc màn Xếp lớp năm học mới (module school-structure). Header ở năm đã kết thúc ⇒ FE chỉ hiện thông báo ở màn tiếp nhận, nhập Excel, xếp lớp (không cần BE).
- **Y-4 · Đợt khám, phiếu theo dõi:** `GET /health-checks?schoolYear=X` trả đợt có ngày bắt đầu thuộc năm X. `GET /health-follow-ups?schoolYear=X` trả mọi phiếu đang mở và phiếu đã đóng có khoảng mở–đóng giao năm X; `counts` tính trên cùng tập (BE giả `listHealthChecks`, `listHealthFollowUps`).
- **Y-1 · Đón trẻ:** danh sách lớp chỉ lớp của năm đang hoạt động (`GET /classes?activeYear=true`, như điểm danh); `GET /classes/{classId}/pickups?date` và `GET /pickups?date`: lớp năm khác ⇒ 422 `classId`; `date` ở tương lai, hoặc trước ngày bắt đầu năm đang hoạt động khi năm đã bắt đầu ⇒ 422 `date`. BE giả vẫn lấy trẻ theo lớp hiện tại như điểm danh; BE thật nên lấy theo enrollment tại ngày đó (CMR-04).
- **Y-5 · Tài khoản phụ huynh:** không theo năm (một tài khoản dùng qua nhiều năm); màn Tài khoản phụ huynh, ô "phụ huynh chưa kích hoạt" ở bảng tổng quan không nhận tham số năm.
- Y-2 = A: user xác nhận 28/10 (C-7).
