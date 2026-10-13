---
title: Service interface + Impl, shared enums and constants in utils
status: in-progress
owner: CareNest team
jira: G94-25 / chưa có mã công việc
branch: feature/authentication
modules: [identity-access, school-structure, child]
rules: []
risk: high
created: 2026-10-13
updated: 2026-10-13
---
# Service interface + Impl, shared enums and constants in utils

## Context
Team chốt quy ước mới (ADR-0014, đưa lên `main` bằng PR riêng chỉ chứa docs + ArchUnit). Code trên nhánh này chưa theo quy ước:
- 9 service là class cụ thể (`service/child/ChildService.java:21`, `service/account/WebAuthService.java:27`, ...).
- `integration/keycloak/WebAuthKeycloakClient.java:22` là `final class`, không interface (lệch mẫu `IdentityProviderClient` ↔ `KeycloakIdentityProviderClient`).
- Enum rải rác: `security/CareNestRole.java:10`, `entity/organization/StaffRole.java:4`, `entity/account/UserStatus.java:4`, lồng `WebAuthChallenge.Purpose` (`entity/account/WebAuthChallenge.java:14`), `IdentityProviderException.Reason` (`integration/keycloak/IdentityProviderException.java:8`).
- Hằng hệ thống trong controller/service: `WebAuthController.java:28` (`SESSION_COOKIE`), `WebAuthService.java:28-33` (TTL, rate limit), `ActiveAccountInterceptor.java:18`.

## Quyết định đã chốt
1. Service = interface giữ tên gốc + `impl/XxxServiceImpl`; WebAuth client = `WebAuthClient` + `KeycloakWebAuthClient` — user, 2026-10-13.
2. Enum dùng chung ⇒ `utils/enums/`; hằng dùng chung/giá trị hệ thống ⇒ 1 file `utils/Constants.java` (nested class); hằng private 1 class giữ tại chỗ — user, 2026-10-13.
3. Làm ngay trên `feature/authentication`, trộn với thay đổi auth chưa commit — user, 2026-10-13.

## Làm rõ nghiệp vụ
Không đổi nghiệp vụ, API, DB, quyền. Refactor cấu trúc thuần ⇒ không tác động FE/APP.

### Câu hỏi mở
(không)

## Acceptance criteria
- **AC-1**: Given code sau refactor When chạy `ArchitectureRulesTest` (thêm 3 luật ADR-0014) Then pass.
- **AC-2**: Given snapshot `docs/api/openapi.yaml` When chạy `OpenApiSnapshotTest` Then không đổi.
- **AC-3**: Given toàn bộ test hiện có When `node scripts/verify.mjs` Then `VERIFY PASS full`, skip 0.

## Phases
### Phase 1 — Refactor code + đồng bộ module card
- Files: C `service/**/impl/*Impl.java`, `integration/keycloak/WebAuthClient.java`, `utils/enums/*.java`, `utils/Constants.java` · M 9 interface service, caller enum/hằng, test liên quan, `ArchitectureRulesTest` (giống bản main) · D `WebAuthKeycloakClient.java`, enum cũ.
- Steps: 1. service ⇒ interface + Impl (nested type chuyển vào interface). 2. `WebAuthClient`/`KeycloakWebAuthClient`. 3. enum/hằng về `utils/`. 4. test `new Xxx(` ⇒ `impl/XxxImplTest`. 5. module card nhắc tên class.
- Tests: AC-1..3 bằng test hiện có + ArchUnit.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0.
- **Dừng chờ user review.**

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Diff trộn với auth chưa commit | Review khó | Commit tách hunk, mã Jira riêng | Bỏ file mới, khôi phục class từ diff |
| Bean thiếu/trùng sau tách | App không khởi động | Verify full có context load (BUG-261012-web-auth-client-bean) | — |
| Docs quy ước khác main tới khi merge | Conflict nhỏ | Không sửa guide ở nhánh này | — |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ không đổi API contract
- [x] Mỗi AC có test; phase có Exit
- [x] >8 file: refactor cơ học 1 quy ước, không tách plan (user chọn làm ngay)
- [x] Không dùng rule PENDING/OPEN
- [x] Tự review

## Progress log
### 2026-10-13 — Approval
- User duyệt plan trong phiên (ExitPlanMode); phần docs/ArchUnit trên worktree `main`: `VERIFY PASS full | tests 27 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`; user cho push thẳng main ⇒ commit `35d7f93` đã push `origin/main`.

### 2026-10-13 — Phase 1 (chờ review)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 201 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 96s | log target/verify.log`; `check-ai-layer` 0 lỗi.
- Khác plan: thêm 3 luật ArchUnit (cùng bản main); `Constants.Security.CURRENT_USER_ATTRIBUTE` đổi giá trị nội bộ sang `"carenest.currentUser"` (khóa request attribute, không phải contract) để `utils` không phụ thuộc `security`; Javadoc mô tả implementation chuyển sang `AccountProvisioningServiceImpl`.
- Tiếp: user review; commit cần mã công việc + cho phép.
