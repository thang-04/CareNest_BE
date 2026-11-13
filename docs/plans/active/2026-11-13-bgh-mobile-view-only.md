---
title: Ban giám hiệu dùng App chỉ xem
status: in-progress
owner: chưa gán
jira: TBD-1 / BE-FEAT-0   # mã tạm (chưa có task Jira); sửa khi user cung cấp mã thật
branch: feature/TBD-1-BE-FEAT-0-bgh-mobile-view-only   # tên tạm (user 13/11), đổi khi có mã Jira; tạo từ origin/dev
modules: [identity-access]
rules: [AUTH-21, AUTH-07, AUTH-08, ATT-01, ATT-05]
risk: high
created: 2026-11-13
updated: 2026-11-13
---
# Ban giám hiệu dùng App chỉ xem

## Context
Figma page App có 16 màn PHT/HT. User chốt ngày 13/11/2026: BGH vào App để xem, không có thao tác nào. Hiện trạng trái với điều này:
- AUTH-21 (CONFIRMED) ghi "Ban giám hiệu không dùng App" (`docs/business/BUSINESS_RULES.md:35`). ADR-0022 cùng nội dung.
- `ClientPlatform.MOBILE` chỉ có TEACHER, KITCHEN_STAFF, PARENT (`src/main/java/com/carenest/utils/enums/ClientPlatform.java:12`).
- Converter bỏ role ngoài nền tảng; hết role thì gán `PLATFORM_DENIED` (`src/main/java/com/carenest/security/KeycloakRealmRoleConverter.java:33-43`). `SecurityConfig` trả 403 cho authority này (`src/main/java/com/carenest/config/SecurityConfig.java:103-106`).
- Role của `CurrentUser` lấy từ authorities của `Authentication` (`src/main/java/com/carenest/security/CurrentUserService.java:48,98-99`). `@PreAuthorize` (306 chỗ) và `PermissionGuard` đọc từ đó ⇒ đổi authorities theo request là đủ cho mọi kiểm tra quyền.
- Có GET ghi DB: `ChildEvaluationServiceImpl.java:102`, `ClassMonthlyCommentServiceImpl.java:51` tạo bản nháp, chỉ khi `canRecord` = TEACHER đang dạy lớp (`ObservationDayRules.java:114`).
- `REQUIRES_NEW` có ở 3 listener chạy sau commit của lệnh ghi: `MealCountAttendanceListener`, `StockDeductionListener`, `PushDispatchListener`.

## Quyết định đã chốt
Mọi quyết định dưới đây do user chốt ngày 13/11/2026.
1. BGH (PRINCIPAL, VICE_PRINCIPAL) được dùng App, chỉ xem.
2. Chặn tuyệt đối thao tác của role BGH trên App, kể cả đổi mật khẩu, ảnh đại diện, chữ ký của mình. Ngoại lệ duy nhất: 3 thao tác thông báo của chính tài khoản (QĐ 8).
3. Tài khoản nhiều role: chỉ chặn phần BGH. Phần GV/phụ huynh (điểm danh, đơn nghỉ…) vẫn hoạt động.
4. Chặn 3 lớp: (1) theo method; (2) transaction chỉ đọc ở DB; (3) danh sách GET cho phép.
5. Danh sách cho phép khai báo **từng endpoint**, trong `application.yml`.
6. Mã lỗi khối mới **13xx** (quyền theo nền tảng; 14xx dự phòng): 1301 cho lớp 1 và 3, 1302 cho lớp 2.
7. Nhánh tạm `feature/TBD-1-BE-FEAT-0-bgh-mobile-view-only` (đổi từ `feature/G94-TBD-BE-FEAT-TBD-…` vì hook commit cần mã dạng `ABC-123`), sửa tên khi có mã Jira.
8. BGH trên App được: `PUT /me/devices`, `DELETE /me/devices` (đăng ký/hủy thiết bị nhận push), `POST /me/notifications/{notificationId}/read`, `POST /me/notifications/read-all`. Các endpoint này khai báo trong danh sách ngoại lệ ghi của cấu hình.
9. GET `/meal-counts` được miễn lớp 2 (lập sổ suất là việc hệ thống, NUT-01).

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** actor là HT, PHT · outcome: xem trên App, mọi việc ghi làm trên Web · rule AUTH-21 (sẽ sửa) · flow `[B]` mở App ⇒ xem số liệu, việc chờ xử lý ⇒ thao tác trên Web.
- **Đối chiếu thiết kế ban đầu:**
  - APP 13/10: BGH có App và có duyệt. AUTH-21 27–30/10: bỏ BGH khỏi App. Quyết định 13/11: có App, không ghi.
  - Ghi chú "chưa vào `dev`" trong AUTH-21 đã cũ.
- **Ảnh hưởng:**
  - identity-access (`security/`, `config/`).
  - Web: không đổi.
  - APP: màn BGH chỉ hiển thị, xử lý 1301/1302; tài liệu APP sửa khi user cho phép.
  - Không có migration.

### Câu hỏi mở
(không còn — đã trả lời 13/11/2026)

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Ngoại lệ thao tác của chính mình | Chặn tuyệt đối | user | 13/11 | AUTH-21 (Phase 1) |
| Tài khoản nhiều role | Chỉ chặn role BGH | user | 13/11 | AUTH-21 (Phase 1) |
| Lớp chặn | 1 + 2 + 3 | user | 13/11 | ADR-0026 (Phase 1) |
| Mã lỗi | 13xx: 1301 + 1302 | user | 13/11 | ERROR_CONTRACT (Phase 1) |
| Danh sách cho phép | Từng endpoint, cấu hình | user | 13/11 | ADR-0026, AUTH_CONTRACT (Phase 1) |
| Nhánh | Tên tạm, sửa sau | user | 13/11 | front matter |
| Thông báo/push của BGH trên App | Cho cả 3 thao tác | user | 13/11 | AUTH-21, ADR-0026 (Phase 1) |

## Acceptance criteria
"BGH thuần" = token App (`azp` = `carenest-mobile`) chỉ mang PRINCIPAL và/hoặc VICE_PRINCIPAL.
- **AC-1** (`AUTH-21`, `AUTH-08`): Given BGH thuần. When GET một endpoint trong danh sách cho phép. Then 200, phạm vi dữ liệu như trên Web.
- **AC-2** (`AUTH-21`): Given BGH thuần. When POST/PUT/PATCH/DELETE bất kỳ, kể cả `PUT /me/password`. Then 403 `errorCode` 1301; controller không chạy.
- **AC-3** (`AUTH-21`): Given BGH thuần. When GET endpoint ngoài danh sách cho phép. Then 403 1301.
- **AC-4** (`AUTH-21`): Given BGH thuần gọi GET trong danh sách. When code bên dưới cố INSERT/UPDATE/DELETE. Then không có dòng nào được ghi, trả 403 1302, log WARN có path.
- **AC-5** (`AUTH-21`, `ATT-01`): Given token App VICE_PRINCIPAL + TEACHER. When ghi đúng quyền GV (lưu điểm danh lớp được phân công). Then thành công như tài khoản chỉ có TEACHER.
- **AC-6** (`AUTH-21`, `AUTH-07`): Given token App VICE_PRINCIPAL + TEACHER/PARENT. When lệnh ghi cần quyền BGH (xác nhận số suất). Then 403 1101.
- **AC-7** (`AUTH-21`): Given token Web của PRINCIPAL/VICE_PRINCIPAL. When gọi lệnh ghi hoặc GET ngoài danh sách. Then hành vi không đổi.
- **AC-8** (`AUTH-21`): Given token App chỉ có ADMIN, hoặc phụ huynh/bếp dùng token Web. When gọi API bất kỳ. Then vẫn 403 1101 như hiện tại.
- **AC-9** (`AUTH-21`): Given BGH thuần. When `PUT /me/devices`, `DELETE /me/devices`, `POST /me/notifications/{id}/read`, `POST /me/notifications/read-all`. Then thành công như trên Web, không bọc transaction chỉ đọc.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Lớp 1 — method | Filter sau `BearerTokenAuthenticationFilter`: token MOBILE + method ≠ GET/HEAD/OPTIONS ⇒ bỏ `ROLE_PRINCIPAL`, `ROLE_VICE_PRINCIPAL` khỏi `JwtAuthenticationToken`; không còn role ⇒ 403 1301 | Rule `authorizeHttpRequests` chặn mọi lệnh ghi khi có role BGH; sửa 306 `@PreAuthorize` | Rule chặn cả phần GV/PH (trái QĐ 3); sửa 306 chỗ dễ sót |
| Lớp 2 — DB chỉ đọc | Với BGH thuần: filter bọc request bằng `TransactionTemplate(readOnly = true)`; `@Transactional` bên trong (REQUIRED) nhập vào; PostgreSQL từ chối ghi (SQLState `25006`) ⇒ `GlobalExceptionHandler` trả 1302 khi request có cờ BGH-chỉ-đọc | User DB chỉ SELECT + datasource thứ hai | Không thêm hạ tầng. Không phủ `REQUIRES_NEW` (3 listener chỉ chạy sau lệnh ghi) |
| Lớp 3 — danh sách cho phép | `carenest.security.platform.mobile-staff-read-paths`: danh sách `GET <pattern>` (PathPattern), khớp mới cho qua | Theo tiền tố module | User chọn mức endpoint: chặt nhất |
| Ngoại lệ ghi (QĐ 8) | `carenest.security.platform.mobile-staff-write-exceptions`: `METHOD <pattern>`; khớp ⇒ không bỏ role, không bọc chỉ đọc | Hard-code trong filter | Đổi danh sách không cần sửa code, cùng chỗ với danh sách đọc |
| Phạm vi lớp 2 và 3 | Chỉ BGH thuần | Áp cả tài khoản nhiều role | GV kiêm BGH cần GET tạo nháp (OBS); bọc chỉ đọc sẽ làm màn GV lỗi |
| Khai báo role chỉ xem | `ClientPlatform` thêm `readOnlyRoles`: MOBILE allowed {TEACHER, KITCHEN_STAFF, PARENT, PRINCIPAL, VICE_PRINCIPAL}, readOnly {PRINCIPAL, VICE_PRINCIPAL} | Hard-code trong filter | Một nguồn cho rule nền tảng |
| Mã lỗi | Khối 13xx mới; 1301 `MOBILE_STAFF_READ_ONLY` "Ban giám hiệu chỉ xem trên App, vui lòng thao tác trên CareNest Web"; 1302 `MOBILE_STAFF_WRITE_BLOCKED` "Thao tác này không thực hiện được trên App" | Thêm vào 11xx (1144) | 11xx nhiều nhánh cùng lấy số ⇒ hay trùng; 13xx trống trên `dev` |
| Tài liệu | ADR-0026 mới, thay một phần ADR-0022 | Sửa thẳng ADR-0022 | Quyết định kiến trúc ⇒ ADR mới |

## Phases
### Phase 1 — Tài liệu và danh sách endpoint
- Files: M `docs/business/BUSINESS_RULES.md` (AUTH-21) · M `docs/business/USER_ROLES.md` · C `docs/decisions/ADR-0026-staff-mobile-read-only.md` · M `docs/decisions/ADR-0022-platform-access-from-token-client.md` · M `docs/contracts/ERROR_CONTRACT.md` (khối 13xx, 1301, 1302) · M `docs/contracts/AUTH_CONTRACT.md` (dòng 60, 262) · M `docs/architecture/SECURITY.md` (dòng 42)
- Steps:
  1. Sửa AUTH-21 theo QĐ 1–3; bỏ ghi chú "chưa vào `dev`".
  2. Viết ADR-0026 theo Key decisions.
  3. ERROR_CONTRACT: mở khối 13xx, giữ 14xx dự phòng, ghi 1301, 1302.
  4. Lập bảng 16 màn BGH → GET endpoint (đối chiếu `docs/api/API_INDEX.md`). Màn chưa có API thì ghi "thiếu API". Đưa bảng vào ADR-0026 để user duyệt.
- Tests: không (tài liệu).
- Exit: `git grep -n "Ban giám hiệu không dùng App" docs` → chỉ còn phần lịch sử ADR-0022; user duyệt bảng endpoint.
- **Dừng chờ user review.**

### Phase 2 — Code và test
- Files: M `src/main/java/com/carenest/utils/enums/ClientPlatform.java` · M `src/main/java/com/carenest/security/ClientPlatformProperties.java` (danh sách path) · C `src/main/java/com/carenest/security/MobileStaffReadOnlyFilter.java` · M `src/main/java/com/carenest/config/SecurityConfig.java` · M `src/main/java/com/carenest/utils/ApiCode.java` · M `src/main/java/com/carenest/exception/GlobalExceptionHandler.java` (SQLState 25006 ⇒ 1302) · M `src/main/resources/application.yml` · M `src/test/java/com/carenest/security/KeycloakRealmRoleConverterTest.java` · M `src/test/java/com/carenest/config/SecurityConfigTest.java` (sửa test dòng 122–130) · C `src/test/java/com/carenest/security/MobileStaffReadOnlyTransactionIT.java` (Testcontainers)
- Steps:
  1. `ClientPlatform.readOnlyRoles()`; MOBILE nhận thêm PRINCIPAL, VICE_PRINCIPAL.
  2. Filter: lấy nền tảng từ `azp`; lớp 1 cho mọi token MOBILE; với BGH thuần thêm lớp 3 rồi lớp 2.
  3. Log theo convention: path, method, platform; không log token, tên.
  4. Rà đủ `@GetMapping` → service có `save|delete|@Modifying|REQUIRES_NEW`; ghi kết quả vào Progress log; chỗ BGH thuần chạm được thì hỏi user.
  5. Trước commit: kiểm `ApiCode` trên `origin/dev` không có 13xx.
- Tests:
  - AC-1/2/3/5/6/7/9 → `SecurityConfigTest` (WebMvc)
  - AC-4 → `MobileStaffReadOnlyTransactionIT` (PostgreSQL thật)
  - AC-8 → test hiện có + `KeycloakRealmRoleConverterTest#shouldKeepPrincipalRoleOnMobile`
- Exit: `./mvnw -q test -Dtest=SecurityConfigTest,KeycloakRealmRoleConverterTest,CurrentUserServiceTest,ApiCodeTest,MobileStaffReadOnlyTransactionIT` → 0 failure (IT tự skip nếu Docker tắt ⇒ báo rõ); `./mvnw -q -DskipTests compile` → OK.
- **Dừng chờ user review.**

Ước lượng 7 file tài liệu + 10 file code/test (>8). Giữ 1 plan, 2 phase, mỗi phase review riêng.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-2, AC-3 | AUTH-21 | SecurityConfigTest (WebMvc) | 2 | chưa chạy |
| AC-4 | AUTH-21 | MobileStaffReadOnlyTransactionIT (Testcontainers) | 2 | chưa chạy |
| AC-5, AC-6 | AUTH-21, ATT-01, AUTH-07 | SecurityConfigTest (WebMvc) | 2 | chưa chạy |
| AC-7, AC-8 | AUTH-21 | SecurityConfigTest, KeycloakRealmRoleConverterTest | 2 | chưa chạy |
| AC-9 | AUTH-21 | SecurityConfigTest (WebMvc) | 2 | chưa chạy |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Ghi qua `REQUIRES_NEW` khi BGH thuần gọi GET | Lớp 2 không chặn | Lớp 3 chỉ mở GET đã rà; Phase 2 bước 4 tìm `REQUIRES_NEW` | Bỏ path khỏi danh sách |
| Danh sách cho phép thiếu path | Màn BGH lỗi 403 | Bảng màn → endpoint duyệt ở Phase 1 | Thêm path vào `application.yml` |
| Bọc transaction cả request | Giữ kết nối DB lâu hơn (cả lúc ghi JSON) | Chỉ áp BGH thuần, chỉ GET | Tắt lớp 2 bằng cấu hình |
| Nhánh khác dùng 13xx | Trùng mã | `ApiCodeTest` fail khi gộp; ghi giữ chỗ trong ERROR_CONTRACT | Dời số |
| Mở BGH lên App | Token App của BGH đọc dữ liệu trẻ | Chỉ GET trong danh sách, phạm vi như Web | Bỏ PRINCIPAL/VP khỏi MOBILE |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể: APP (màn BGH, 1301/1302); Web không đổi
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [x] >8 file ⇒ đã đề xuất tách theo 2 phase
- [x] Không dùng rule PENDING/OPEN như đã chốt
- [x] Câu hỏi mở đã trả lời hết

## Progress log
### 2026-11-13 — Plan approved
- User duyệt plan ngày 13/11/2026 ("duyệt plan"), cho phép tạo nhánh tạm.
- Nhánh `feature/G94-TBD-BE-FEAT-TBD-bgh-mobile-view-only` tạo từ `origin/dev` (`f578257`), đã bỏ upstream `origin/dev`.
- Tiếp: Phase 1

### 2026-11-13 — Phase 1 (chờ review)
- Commit: chưa commit
- Sửa: AUTH-21 (`BUSINESS_RULES.md`), cột Client của Principal/VP (`USER_ROLES.md`), ADR-0026 mới, ADR-0022 (status), `ERROR_CONTRACT.md` (khối 13xx, 1301, 1302, quy ước giữ chỗ), `AUTH_CONTRACT.md`, `SECURITY.md`, `docs/system/CROSS_REPO_MAP.md` (dòng Principal/VP — ngoài danh sách file của plan, cùng nội dung).
- Verify: `git grep -nE "(Ban giám hiệu|BGH) không dùng App" -- docs ':!docs/plans'` → chỉ còn câu trích dẫn "thay câu…" trong AUTH-21; ADR-0022 dòng 13 giữ làm lịch sử.
- Khác plan: không chạy `check-ai-layer.mjs` (chỉ đạo 07/11/2026).
- Phát hiện: GET `/meal-counts` lập sổ suất lần đầu sau giờ chốt cho mọi người xem (`MealCountServiceImpl.getMealCounts` → `aggregateMissing` → `mealCountRepository.save`, `historyRepository.save`). Lớp 2 sẽ trả 1302 nếu BGH thuần mở trước mọi người. Chờ user chốt (ADR-0026 "Còn chờ").
- `/accounts/me/photo` chỉ cho PARENT ⇒ không đưa vào danh sách; `GET /children` chưa có tham số lọc dị ứng (màn PHT 10).
- Tiếp: user duyệt bảng endpoint trong ADR-0026 và chốt cách xử lý `/meal-counts`.

### 2026-11-13 — Phase 1 review xong
- User duyệt bảng endpoint, chốt GET `/meal-counts` là ngoại lệ lớp 2 (`mobile-staff-system-write-paths`), cho làm Phase 2.
- Tiếp: Phase 2

### 2026-11-13 — Phase 2 (chờ review)
- Commit: chưa commit
- Code: `ClientPlatform` (`readOnlyRoles`, MOBILE thêm PRINCIPAL/VICE_PRINCIPAL), `ClientPlatformProperties` (3 danh sách), `MobileStaffReadOnlyFilter` mới, `SecurityConfig` (đăng ký sau `BearerTokenAuthenticationFilter`), `ApiCode` (1301, 1302), `GlobalExceptionHandler` (SQLState 25006 + cờ request ⇒ 1302), `application.yml` (17 GET + `/meal-counts`, 4 ngoại lệ ghi, 1 ngoại lệ lớp 2), comment `KeycloakRealmRoleConverter`.
- Test: `SecurityConfigTest` (26, thêm 11 ca), `KeycloakRealmRoleConverterTest` (10), `MobileStaffReadOnlyFilterTest` mới (4), `MobileStaffReadOnlyTransactionIntegrationTest` mới (2, PostgreSQL thật: `ERROR: cannot execute INSERT in a read-only transaction`), `CurrentUserServiceTest` (10), `ApiCodeTest` (3).
- Verify: `./mvnw -q test -Dtest=SecurityConfigTest,KeycloakRealmRoleConverterTest,CurrentUserServiceTest,ApiCodeTest,MobileStaffReadOnlyFilterTest,MobileStaffReadOnlyTransactionIntegrationTest` → exit 0, 55 test, 0 fail, 0 skip (Docker bật).
- Khác plan: tên IT đổi `*IntegrationTest` (surefire không chạy `*IT`); thêm `MobileStaffReadOnlyFilterTest`; lớp 2 thiếu transaction manager ⇒ từ chối 1302 (fail closed); `/accounts/me/photo` không vào danh sách (chỉ PARENT).
- Lỗi gặp: `FACTOR_BEARER` của Spring Security 7 làm filter không nhận ra BGH thuần ⇒ lọc `ROLE_*` (BUG-261113-factor-bearer-authority, đã ghi ISSUE_INDEX + Known pitfalls `identity-access.md`).
- Bước 4 (rà GET): 17 GET trong danh sách không ghi DB với BGH (đối chiếu service → helper ≥3 tầng; không publish event). Riêng `/meal-counts` ghi (đã miễn lớp 2).
- Verify toàn bộ: `./mvnw test` → `Tests run: 1643, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Tiếp: user review Phase 2; mã Jira thật ⇒ đổi tên nhánh, commit khi user cho phép; APP (màn BGH chỉ hiển thị, xử lý 1301/1302) là việc riêng.

### 2026-11-13 — Commit và push (user cho phép)
- Đổi tên nhánh tạm thành `feature/TBD-1-BE-FEAT-0-bgh-mobile-view-only`: hook `commit-msg` chỉ nhận mã `[A-Z][A-Z0-9]+-[0-9]+`, `G94-TBD` không qua. Mã tạm `TBD-1` (BGH chỉ xem), `TBD-2` (PCK-02/AUTH-19); user sẽ cung cấp mã thật để sửa tên.
- Commit riêng `TBD-2` (ngoài plan này, user chốt 13/11): `profileUpdateRequired` luôn `false` (PCK-02, ảnh lấy từ hồ sơ tiếp nhận); ghi AUTH-19 phụ huynh quên mật khẩu nhận OTP qua SMS (chưa có API).
- Verify: test liên quan `PickupIntegrationTest`, `AccountServiceImplTest`, `AccountControllerTest`, `ChildLeftSchoolIntegrationTest`, `AuthServiceImplTest` → 0 fail; toàn bộ `./mvnw test` chạy lại trước push.
- Tiếp: user review nhánh; mã Jira thật ⇒ đổi tên nhánh/commit; API OTP SMS cho phụ huynh là task sau.
