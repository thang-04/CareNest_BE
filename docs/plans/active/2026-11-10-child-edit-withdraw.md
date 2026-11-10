---
title: Sửa hồ sơ trẻ (đổi SĐT phụ huynh qua duyệt), cho trẻ nghỉ học
status: in-progress
owner: G94 team
jira: G94-180 / BE-FEAT-43
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [child, identity-access, notification]
rules: [G94-180/AUTH-21, G94-180/CHD-03, G94-180/CHD-04, G94-180/CHD-08, AUTH-18, CHD-04]
risk: high
created: 2026-11-10
updated: 2026-11-11
---
# Sửa hồ sơ trẻ (đổi SĐT phụ huynh qua duyệt), cho trẻ nghỉ học

Làn L (migration V82, `integration/keycloak`, API mới, 2 module). Chạy theo chế độ tự chạy của `CLAUDE.md`: agent tự chọn phương án khuyến nghị, ghi giả định ở "Key decisions" để user soát; `status` không tự đặt `approved`.

## Context
User yêu cầu 2026-11-10 hoàn thiện BE nhánh G94-180: (1) Sửa hồ sơ trẻ — cho sửa các trường, nhưng đổi SĐT phụ huynh phải gửi yêu cầu tới PHT có đủ quyền ở cơ sở đó, duyệt thì gửi SMS tài khoản + mật khẩu mới về SĐT mới; (2) cho trẻ nghỉ học; (3) nhập Excel. Hiện trạng trước khi làm: không có `PUT /children/{id}`, `/withdraw` (`ChildEnrollmentController`); `UserAccount.username` `updatable=false`, `IdentityProviderClient` không đổi được username; nhập Excel đã có (`ChildEnrollmentImportController`, Spec013).

## Quyết định đã chốt
1. Đổi SĐT phụ huynh đã có tài khoản phải qua duyệt của PHT có quyền ở điểm trường đó; duyệt ⇒ SMS tên đăng nhập + mật khẩu mới tới số mới — user, 2026-11-10 (sửa G94-180/AUTH-21 chốt 2026-10-28 là đổi ngay).
2. Nhập Excel thuộc tiếp nhận trẻ và đã làm (Spec013) — không viết lại.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** HT/PHT (`CHILD_PROFILE_EDIT`) sửa hồ sơ; PHT có `PARENT_ACCOUNT_MANAGE` ở điểm trường của trẻ hoặc HT duyệt đổi SĐT; HT/PHT (`CHILD_PLACEMENT`) cho nghỉ học. Rule: G94-180/AUTH-21 (CONFIRMED, sửa 2026-11-10), G94-180/CHD-03, CHD-04 (CONFIRMED). Flow: `[B]` phụ huynh báo số mới → `[S]` người sửa lưu hồ sơ ⇒ yêu cầu PENDING + thông báo người duyệt → `[S]` người duyệt duyệt ⇒ đổi tên đăng nhập, mật khẩu tạm, SMS → `[B]` phụ huynh đăng nhập bằng số mới.
- **Đối chiếu thiết kế ban đầu:** plan `2026-10-28-child-enrollment-guardian-accounts.md` mục 4.2, 5.4 ghi đổi SĐT ⇒ đổi tên đăng nhập ngay; yêu cầu mới: qua duyệt. Đã sửa `BUSINESS_RULES.md` G94-180/AUTH-21.
- **Ảnh hưởng:** module child (ghi hồ sơ, nghỉ học), identity-access (tài khoản, Keycloak, SMS, yêu cầu đổi SĐT), notification (3 loại mới). FE: `EditChildPage` (body `PUT /children/{id}` giữ như BE giả, response đổi thành `{child, accountChanges, phoneChangeRequests}`; nên gửi `guardians[].id`), màn duyệt đổi SĐT mới (chưa có ở FE), `withdrawChild` khớp BE giả. APP: không đổi API; phụ huynh bị đăng xuất khi duyệt, đăng nhập bằng số mới.

### Câu hỏi mở
Không còn câu hỏi chặn; các điểm agent tự chọn ở "Key decisions" chờ user soát.

## Acceptance criteria
- **AC-1** (G94-180/CHD-03): Given trẻ đang học lớp X từ 01/09, When cho nghỉ `leftAt` = hôm nay + lý do, Then trẻ LEFT, enrollment kết thúc hôm qua, lịch sử WITHDRAWN, GV lớp X nhận `CHILD_WITHDRAWN`, sự kiện khóa tài khoản phát ra.
- **AC-2** (G94-180/CHD-03): `leftAt` tương lai / trước ngày tiếp nhận / thiếu lý do ⇒ 400 ô `leftAt`/`reason`; trẻ đã nghỉ ⇒ 409 3011; PHT ngoài điểm trường ⇒ 403.
- **AC-3** (G94-180/AUTH-21): đổi SĐT phụ huynh có tài khoản ⇒ hồ sơ giữ số cũ, yêu cầu PENDING, người duyệt được báo; không gọi Keycloak.
- **AC-4** (G94-180/AUTH-21): duyệt ⇒ Keycloak đổi username + mật khẩu tạm, DB đổi username + SĐT mọi hồ sơ, yêu cầu APPROVED, SMS sau commit; người gửi tự duyệt ⇒ 403 1138; số mới đã thuộc người khác ⇒ 409 1139; DB lỗi ⇒ trả username cũ.
- **AC-5** (G94-180/AUTH-21): sửa họ tên phụ huynh có tài khoản ⇒ tài khoản + mọi hồ sơ + Keycloak đổi; không có `PARENT_ACCOUNT_MANAGE` ⇒ 400 ô; đổi cùng lúc CCCD và SĐT ⇒ 400 ô CCCD.
- **AC-6** (mục 4.2, AUTH-19): bỏ phụ huynh đang liên kết tài khoản / ngày sinh sau ngày tiếp nhận ⇒ 400 ô; lịch sử UPDATED ghi từng ô, CCCD 3 số cuối.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Người duyệt | PHT phân công ở điểm trường hiện tại của trẻ có `PARENT_ACCOUNT_MANAGE` + HT | Chỉ PHT | HT có mọi quyền toàn trường (plan tiếp nhận mục 2); trường chỉ 1 PHT/điểm trường vẫn có người duyệt |
| Người gửi tự duyệt | Cấm duyệt (1138); được từ chối (= rút yêu cầu) | Cho tự duyệt khi có quyền | "Yêu cầu đến PHT" ⇒ 2 người; rút yêu cầu không cần người khác |
| Phụ huynh chưa có tài khoản | Đổi SĐT áp dụng ngay | Cũng qua duyệt | Chưa có tên đăng nhập/SMS để bảo vệ; duyệt chỉ ý nghĩa với tài khoản |
| Yêu cầu chờ mà sửa lại sang số khác | Hủy yêu cầu cũ (CANCELLED), tạo mới | Chặn tới khi xử lý | Không cần endpoint hủy riêng |
| Sửa họ tên/email/CCCD người có tài khoản | Cập nhật ngay, cần `PARENT_ACCOUNT_MANAGE` (400 nếu thiếu) | Cũng qua duyệt | User chỉ nêu SĐT; plan tiếp nhận mục 2 gắn sửa tài khoản với quyền này |
| Tài khoản nhân sự kiêm phụ huynh đổi SĐT | Chặn ở Sửa hồ sơ (400 ô phone) | Cho đổi | Tên đăng nhập nhân sự đổi ở màn nhân sự |
| Thứ tự duyệt | Keycloak username → mật khẩu tạm → DB tx → lỗi trả username → hủy phiên → SMS | DB trước | Không giữ tx DB khi gọi HTTP (ADR-0011); mật khẩu tạm không hoàn được, duyệt lại cấp mới |
| Ngày học cuối khi nghỉ | `leftAt - 1`; nghỉ ngay ngày vào lớp ⇒ xóa enrollment đó | `leftAt` là ngày học cuối | BE giả "nghỉ học từ ngày"; giống chuyển năm học (`YearTransitionServiceImpl.applyPlacements`) |
| Khóa tài khoản khi nghỉ | Phát `ChildrenLeftSchoolEvent` (listener sẵn có) | Không khóa | CHD-04 `dev` (user chốt 2026-10-30) |
| Code tách | Feature child `ChildRecordService` (ghi trẻ); feature account `ChildProfileEditService`, `GuardianPhoneChangeService` | Một service | ArchUnit: account → child, child không gọi account |

## Phases
### Phase 1 — Cho nghỉ học, Sửa hồ sơ, duyệt đổi SĐT (một lượt, chế độ tự chạy)
- Files: C `V82__guardian_phone_change_requests.sql`, `ChildRecordService(+Impl)`, `ChildProfileEditService(+Impl)`, `GuardianPhoneChangeService(+Impl)`, `PhoneChangeSupport`, `GuardianPhoneChangeController`, `GuardianPhoneChangeRequest(+Repository)`, `PhoneChangeStatus`, DTO `WithdrawChildRequest`, `UpdateChildRequest`, `ChildEditDraft`, `ChildUpdateResponse`, `GuardianAccountChangeResponse`, `ChildFieldChange`, `GuardianPhoneChangeResponse`, `PhoneChangeDecisionRequest` · M `ChildEnrollmentController`, `EnrollmentFormValidator`, `Child`, `ChildProfile`, `ChildGuardian`, `ChildHistory`, `UserAccount`, `UserAccountRepository`, `ChildGuardianRepository`, `EnrollmentRepository`, `IdentityProviderClient`, `KeycloakIdentityProviderClient`, `AccountLifecycleOperations`, `ParentSmsSender`, `StaffAssignmentService(+Impl)`, `ApiCode`, `NotificationType`, `GuardianInput`, `ChildHistoryResponse`.
- Tests: AC-1, AC-2 → `ChildRecordServiceImplTest`; AC-3, AC-5 → `ChildProfileEditServiceImplTest`; AC-4 → `GuardianPhoneChangeServiceImplTest`; AC-6 → `ChildRecordServiceImplTest`; HTTP/quyền → `ChildEnrollmentControllerTest`, `GuardianPhoneChangeControllerTest`.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip chỉ do không có Docker (cần chạy lại khi có Docker: integration + snapshot OpenAPI).

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | G94-180/CHD-03 | `ChildRecordServiceImplTest#withdrawClosesClassAndNotifiesTeachers`, `#withdrawOnFirstClassDayDropsEnrollment` (unit) | 1 | pass |
| AC-2 | G94-180/CHD-03 | `#withdrawValidatesFields`, `#withdrawLeftChildConflicts`, `#withdrawOutOfScopeDenied` (unit); `ChildEnrollmentControllerTest#withdrawNeedsPlacementPermission` (WebMvc) | 1 | pass |
| AC-3 | G94-180/AUTH-21 | `ChildProfileEditServiceImplTest#phoneChangeOfAccountHolderBecomesRequest` (unit) | 1 | pass |
| AC-4 | G94-180/AUTH-21 | `GuardianPhoneChangeServiceImplTest` 6 test (unit); `GuardianPhoneChangeControllerTest` (WebMvc) | 1 | pass |
| AC-5 | G94-180/AUTH-21 | `ChildProfileEditServiceImplTest#nameChangeUpdatesAccount`, `#nameChangeNeedsAccountPermission`, `#citizenAndPhoneTogetherRejected`, `#newPhoneOfAnotherAccountConflicts` (unit) | 1 | pass |
| AC-6 | mục 4.2 | `ChildRecordServiceImplTest#prepareEditRejectsDroppedLinkedGuardianAndLateBirthDate`, `#applyEditRecordsMaskedChanges` (unit) | 1 | pass |
| AC-1, AC-3, AC-4, AC-5 | G94-180/CHD-03, AUTH-21 | `ChildRecordIntegrationTest` 5 test (PostgreSQL thật, V82) | 1 | pass |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Keycloak 26.8 chặn đổi username khi realm `editUsernameAllowed=false` (**đã thử 2026-11-10: 400 `error-user-attribute-read-only`**) | Duyệt không đổi được tên đăng nhập | Realm json: `editUsernameAllowed: true` + user profile `username.permissions.edit = ["admin"]` (đã thử trên KC 26.8.0 dựng mới: service account 204, trùng 409, người dùng tự đổi 400); realm đã có (dev cũ, VPS) chỉnh tay theo `docs/system/KEYCLOAK.md` mục 7; chưa chỉnh ⇒ 1140, không đổi gì | Đặt lại 2 cấu hình realm cũ |
| DB lỗi sau khi đổi username + mật khẩu Keycloak | Mật khẩu cũ mất hiệu lực | Trả username cũ; duyệt lại cấp mật khẩu khác | Cấp lại mật khẩu ở màn Tài khoản phụ huynh |
| ~~Trẻ chờ xếp lớp (chưa có enrollment) cho nghỉ~~ — **đã sửa 2026-11-11** | Phụ huynh vẫn xem được (policy dựa enrollment), không bị khóa tài khoản | `findChildIdsLeftSchool` xét `children.status` (Progress log 2026-11-11) | Trả query cũ |
| Điểm danh đã ghi sau ngày nghỉ (nghỉ lùi ngày) | Dữ liệu điểm danh lệch | Không kiểm (child không đọc attendance) | Chọn `leftAt` = hôm nay |
| Xóa phụ huynh có ảnh ở Sửa hồ sơ | Tệp S3 mồ côi | Ghi giới hạn | Dọn tệp theo `photo_key` |
| V82 | Bảng mới + ràng buộc thông báo | Chỉ thêm | Migration mới DROP bảng, đặt lại CHECK |

## Validation log
- [x] Khẳng định về code có `file:line` (trong báo cáo bàn giao và module card)
- [x] Đổi contract ⇒ consumer cụ thể: FE `childrenApi.updateChild`/`withdrawChild`, màn duyệt đổi SĐT (mới)
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] >8 file: một phase do chế độ tự chạy; đã liệt kê file
- [x] Không dùng rule PENDING/OPEN như đã chốt (chi tiết duyệt ghi PROPOSED ở `BUSINESS_RULES`)
- [x] Tự review: không placeholder · không mâu thuẫn

## Progress log
### 2026-11-10 — Phase 1 (chờ user soát)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 1187 fail 0 err 0 skip 391 | archunit ok | snapshot SKIP | spotless ok`
- Khác plan: snapshot OpenAPI (`docs/api/openapi.yaml`) và test tích hợp Testcontainers chưa chạy (không có Docker) ⇒ phải chạy `mvnw.cmd test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true` và verify lại khi có Docker.
- Tiếp: user soát Key decisions; thử đổi username trên Keycloak thật; FE nối API + màn duyệt.

### 2026-11-10 — Phase 1 bổ sung (Docker, Keycloak thật)
- User cho chạy Docker. Snapshot OpenAPI cập nhật (`OpenApiSnapshotTest -Dopenapi.snapshot.update=true`): thêm 5 operation (`update`, `withdraw`, `listPhoneChangeRequests`, `approve`, `reject`) + schema mới; đặt tên method `listPhoneChangeRequests` (không `list`) để operationId `list_1` của `/api/assessment/evaluations` không đổi. Snapshot mất schema `RecordView` (attendance): không endpoint nào tham chiếu vì `ChildRow` trùng tên giữa attendance và assessment (lỗi có từ trước, pitfall tên record lồng) — springdoc ghi schema mồ côi tùy thứ tự quét bean.
- Keycloak 26.8.0 (container thử, đã xóa): realm cũ ⇒ Admin API đổi username 400 read-only. Sửa `docker/keycloak/import/carenest-realm.json` (`editUsernameAllowed: true`, username chỉ admin sửa) và kiểm lại trên realm import mới: service account đổi 204 + đọc lại đúng, trùng 409, đăng nhập số mới được, người dùng tự đổi 400; `updateProfile` đổi họ tên/email, email `""` ⇒ xóa email. Test `docker/keycloak/auth/*.test.mjs` pass (4 file).
- Thêm `USERNAME_CHANGE_NOT_ALLOWED` (1140): realm chưa chỉnh ⇒ báo rõ thay vì `IDENTITY_DATA_REJECTED` ("kiểm tra mật khẩu và họ tên"); test `GuardianPhoneChangeServiceImplTest#approveFailsClearlyWhenRealmForbidsUsernameEdit`.
- Thêm test tích hợp PostgreSQL thật `ChildRecordIntegrationTest` (5 test: nghỉ học ghi CHECK V63/thông báo V82; Sửa hồ sơ giữ SĐT tài khoản, đổi ngay SĐT chưa có tài khoản, lịch sử JSONB; PHT duyệt đổi username + SĐT mọi hồ sơ + SMS, người gửi tự duyệt 1138; yêu cầu mới CANCELLED yêu cầu cũ, từ chối giữ số; đổi họ tên đồng bộ tài khoản + hồ sơ anh/chị em) → 5/5 pass. Hai lần fail đầu do test `@Transactional` không flush trước khi đọc JDBC (không phải lỗi code) ⇒ thêm `entityManager.flush()`.
- Verify có Docker lần 1: 8 error `FileNotFoundException ... SchoolDayUsageCheck.class` / resource null — VS Code Java build lại `target/` khi đang sửa code giữa lúc verify (memory máy này); chạy lại không sửa gì trong lúc verify ⇒ hết.
- **Fail có sẵn, không do task này:** `FoodMicronutrientsMigrationTest` (target V73 mong có cột vi chất) fail vì nhánh còn thứ tự migration cũ (V68 = `child_enrollment_photos`, đẩy V68–V79 của `dev` lên +1). `origin/dev` đã sửa ở `e89f30d` "Restore production migration numbers V68-V79" (V80 = `child_enrollment_photos`, V81 = `selected_parent_account`), nhánh chưa gộp. V82 của task này vẫn đúng sau V81 của `dev`. Cần user cho gộp `dev` vào nhánh (đổi tên migration đã commit — hỏi trước theo AGENTS).
- Verify cuối (có Docker, sau lần sửa cuối): `node scripts/verify.mjs` → `VERIFY FAIL full | tests 1198 fail 1 err 0 skip 0 | archunit ok | snapshot ok | spotless -` — fail duy nhất là `FoodMicronutrientsMigrationTest` có sẵn (trên); Spotless không chạy trong verify vì Maven dừng ở pha test ⇒ chạy riêng `mvnw spotless:check` → `BUILD SUCCESS`.
- Tiếp: user soát Key decisions; gộp `dev` vào nhánh (hết fail migration); chỉnh realm Keycloak đã có ở dev/VPS (`docs/system/KEYCLOAK.md` mục 7); FE nối API + màn duyệt đổi SĐT.
- 2026-11-11: gộp `origin/dev` (có `V83__consolidate_boarding_tables`, không có V82) ⇒ đổi số migration của nhánh: V82 `guardian_phone_change_requests` → V84. Nội dung migration không đổi.
- 2026-11-11: sửa giới hạn "trẻ chờ xếp lớp cho nghỉ". `EnrollmentRepository.findChildIdsLeftSchool` = `status = LEFT` và `left_on ≤ D`, hoặc ra trường (từng có lớp, không còn lớp chưa kết thúc tới D), bỏ trẻ Chờ xếp lớp. Sửa luôn lỗi ngược phát hiện khi soát: trẻ mở lại hồ sơ (Chờ xếp lớp, lớp cũ đã đóng) bị coi là đã nghỉ ⇒ phụ huynh bị chặn xem (user đồng ý phụ huynh xem lại được như trẻ mới tiếp nhận). Cả 4 nơi dùng (`ChildAccessPolicy`, `GET /accounts/me`, `ParentAccountLeftSchoolListener`, `revokeStaffRole`) theo cùng query; không migration, không đổi API. Test `ChildLeftSchoolIntegrationTest` (10; 5 fail trước khi sửa). Giới hạn còn: mở lại hồ sơ không tự mở lại tài khoản phụ huynh đã bị khóa.
