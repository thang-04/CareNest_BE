---
title: Service interface + Impl, shared enums and constants in utils
status: in-progress
owner: CareNest team
jira: G94-25 / chưa có mã công việc
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access, school-structure, child]
rules: []
risk: high
created: 2026-10-13
updated: 2026-10-14
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
4. ~~Controller catch + mapper chung + request filter + END trong `finally`~~ — thay bởi mục 6 (user, 2026-10-14).
5. ~~`System.currentTimeMillis()`/`durationMs`, log private helper~~ — thay bởi mục 6.
6. Controller gán kết quả service vào biến, log field kết quả an toàn rồi mới tạo `ResponseJson`; không đặt service call lồng trong `return ResponseJson...` — user, 2026-10-14.
6. Log đơn giản (ADR-0016): `LOG_PREFIX` theo class, method public log `|tênMethod|id=...|START/END`; controller không `try-catch`; service `try-catch (RuntimeException)` log `ERROR|type` rồi `throw ex`; bỏ `ExceptionResponseMapper`, `RequestTraceFilter`, `ExecutionLog`, `SafeExceptionLog`, `finally`/`durationMs`; `GlobalExceptionHandler` log `ex` như cũ — user, 2026-10-14.

## Làm rõ nghiệp vụ
Không đổi nghiệp vụ, API, DB, quyền. Refactor cấu trúc thuần ⇒ không tác động FE/APP.

### Câu hỏi mở
(không)

## Acceptance criteria
- **AC-1**: Given code sau refactor When chạy `ArchitectureRulesTest` (thêm 3 luật ADR-0014) Then pass.
- **AC-2**: Given snapshot `docs/api/openapi.yaml` When chạy `OpenApiSnapshotTest` Then không đổi.
- **AC-3**: Given toàn bộ test hiện có When `node scripts/verify.mjs` Then `VERIFY PASS full`, skip 0.
- **AC-4**: Given lỗi service trong endpoint When request chạy Then endpoint trả cùng HTTP status và `{code, desc, data}` như trước; lỗi trước endpoint vẫn qua advice/security handler.
- **AC-5**: Given method thành công hoặc ném exception When xem log Then có `START`, đúng một `END`, `outcome` và `durationMs` ở mức INFO.
- **AC-6**: Given request chứa mật khẩu/token hoặc giá trị path khôi phục mật khẩu When xem log Then không có dữ liệu đó; request bị 401/403 vẫn có log request.
- **AC-7**: Given thành viên/AI agent dùng `AGENTS.md`, `CLAUDE.md`, rule theo file hoặc workflow When sửa controller/service Then cùng một format `LOG_PREFIX`, `System.currentTimeMillis()`, allowlist `req`, mapper lỗi; `check-ai-layer` phát hiện rule lệch guide.
- **AC-8**: Given endpoint nhận kết quả service When trả response thành công Then gán kết quả vào biến, log ID/role/status hoặc trạng thái an toàn, rồi mới tạo `ResponseJson`; auth/token/OTP/PII không xuất hiện trong log.

## Phases
### Phase 1 — Refactor code + đồng bộ module card
- Files: C `service/**/impl/*Impl.java`, `integration/keycloak/WebAuthClient.java`, `utils/enums/*.java`, `utils/Constants.java` · M 9 interface service, caller enum/hằng, test liên quan, `ArchitectureRulesTest` (giống bản main) · D `WebAuthKeycloakClient.java`, enum cũ.
- Steps: 1. service ⇒ interface + Impl (nested type chuyển vào interface). 2. `WebAuthClient`/`KeycloakWebAuthClient`. 3. enum/hằng về `utils/`. 4. test `new Xxx(` ⇒ `impl/XxxImplTest`. 5. module card nhắc tên class.
- Tests: AC-1..3 bằng test hiện có + ArchUnit.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0.
- **Dừng chờ user review.**

### Phase 2 — Xử lý lỗi cục bộ và execution log (bổ sung 2026-10-14)
- Files: controller/service logic, mapper lỗi dùng chung, request filter, security handler log, test và ADR-0016/guide/contract.
- Steps: 1. Test đỏ cho controller catch, END khi lỗi và redaction. 2. Mapper chung + filter request. 3. Bọc method, giữ bù trừ/cookie/rollback. 4. Đồng bộ tài liệu, rà log nhạy cảm.
- Tests: AC-4..6 bằng `AccountControllerTest`, `ChildServiceImplTest`, `ResponseContractTest`, auth regression và OpenAPI snapshot.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0; `node scripts/check-ai-layer.mjs` → 0 lỗi.

### Phase 3 — Đồng bộ rule AI và format log theo quyết định mới
- Files: `AGENTS.md`, guide mục 5/8/10/11/17, `.claude/rules/`, router/workflow, DoD/test strategy, ADR-0016, E7 check; controller/service và request filter chỉnh prefix/time.
- Steps: 1. Test E7 đỏ khi rule lệch, rồi thêm check đồng bộ. 2. Chỉnh source sang `LOG_PREFIX` + `System.currentTimeMillis()`. 3. Đồng bộ tất cả entrypoint/rule/guide, rà nội dung cũ mâu thuẫn. 4. Verify và review diff.
- Tests: AC-7..8 bằng `check-ai-layer.test.mjs`, `AccountControllerTest`, `ChildServiceImplTest`, `ResponseContractTest`; AC-4..6 giữ regression.
- Exit: `node scripts/check-ai-layer.mjs` → 0 lỗi; `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0.

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Diff trộn với auth chưa commit | Review khó | Commit tách hunk, mã Jira riêng | Bỏ file mới, khôi phục class từ diff |
| Bean thiếu/trùng sau tách | App không khởi động | Verify full có context load (BUG-261012-web-auth-client-bean) | — |
| Docs quy ước khác main tới khi merge | Conflict nhỏ | Đồng bộ guide/controller rule theo Phase 2 và chạy check-ai-layer | — |
| Log INFO ở mọi helper | Tăng dung lượng log | Chỉ log allowlist, theo dõi dung lượng khi vận hành | Tắt mức INFO của package liên quan nếu cần |
| Controller tự chuyển lỗi | Lệch mã lỗi so với advice | Dùng một mapper, test response/snapshot | Khôi phục delegate qua advice |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ không đổi API contract
- [x] Mỗi AC có test; phase có Exit
- [x] >8 file: refactor cơ học 1 quy ước, không tách plan (user chọn làm ngay)
- [x] Không dùng rule PENDING/OPEN
- [x] Tự review
- [x] Phase 2: user chốt cùng G94-25; API wire shape giữ nguyên, FE/APP không cần đổi; >8 file do quy ước áp dụng đồng loạt.

## Progress log
### 2026-10-13 — Approval
- User duyệt plan trong phiên (ExitPlanMode); phần docs/ArchUnit trên worktree `main`: `VERIFY PASS full | tests 27 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`; user cho push thẳng main ⇒ commit `35d7f93` đã push `origin/main`.

### 2026-10-13 — Phase 1 (chờ review)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 201 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 96s | log target/verify.log`; `check-ai-layer` 0 lỗi.
- Khác plan: thêm 3 luật ArchUnit (cùng bản main); `Constants.Security.CURRENT_USER_ATTRIBUTE` đổi giá trị nội bộ sang `"carenest.currentUser"` (khóa request attribute, không phải contract) để `utils` không phụ thuộc `security`; Javadoc mô tả implementation chuyển sang `AccountProvisioningServiceImpl`.
- Tiếp: user review; commit cần mã công việc + cho phép.

### 2026-10-14 — Phase 2 (chờ review)
- User duyệt plan bổ sung trong phiên: `try-catch` cả private helper, controller trả `ResponseJson`, service/helper giữ kiểu trả về, `@Slf4j` hiện có, allowlist request, mọi method INFO.
- Test đỏ: `AccountControllerTest`, `ChildServiceImplTest`, `ResponseContractTest` ghi nhận thiếu local catch/END/request log và raw path từ security handler; test trọng tâm sau thay đổi đã xanh.
- Khác plan cũ: Phase 2 cập nhật guide trên nhánh này theo quyết định mới nhất, thay thế rủi ro "không sửa guide" của Phase 1.
- Verify: `node scripts/check-ai-layer.mjs` → `0 lỗi, 0 cảnh báo`; `node scripts/verify.mjs` → `VERIFY PASS full | tests 211 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 45s | log target/verify.log`; `git diff --check` exit 0; OpenAPI snapshot không đổi.
- Review độc lập: không phát hiện lỗi correctness/security/API contract; rủi ro còn lại là dung lượng log INFO khi gọi nhiều private helper và filter chỉ đo luồng request đồng bộ hiện có.
- Commit: chưa commit. Tiếp: user review Phase 2; không tự commit/push.

### 2026-10-14 — Phase 3 (đang thực hiện)
- User yêu cầu đồng bộ đầy đủ rule AI; sau đó chốt `LOG_PREFIX` theo tên class và `System.currentTimeMillis()` theo mẫu trong tin nhắn.
- User chỉnh tiếp: kết quả service phải được gán biến và log field an toàn trước `ResponseJson`; controller và rule theo file đã cập nhật, auth không log token/OTP hoặc toàn bộ response.
- E7 regression: test mới đã fail trước check và pass sau check (11/11 trước lần đổi format cuối). Source đã có `LOG_PREFIX` đúng tên ở 14 class và prefix method ở 69 method; guide/rule/README/workflow đã chuyển sang `System.currentTimeMillis()`. `git diff --check` exit 0 (kiểm tra tĩnh).
- Verify sau sửa cuối: chưa chạy — auto-review từ chối lệnh `node scripts/check-ai-layer.mjs` vì đã chạm hạn mức sử dụng, không phải kết luận lệnh không an toàn. Không dùng kết quả 211 test của Phase 2 làm bằng chứng cho Phase 3.
- Commit: chưa commit/push.

### 2026-10-14 — Phase 3 đảo hướng: log đơn giản
- User yêu cầu bỏ mapper/filter/`finally` vì code controller khó đọc; chốt 4 điểm: service giữ `try-catch`, chỉ log method public, bỏ `durationMs`, 5xx log `ex` trực tiếp.
- Cách làm: dựng lại source/docs từ HEAD (patch bản cũ lưu ngoài repo), thêm log theo ADR-0016 mới; `AuthStateStoreImpl`, `AuthRefreshCoalescer` là helper nội bộ auth nên không log; `finally` xóa cookie/thu hồi token giữ nguyên (nghiệp vụ). E7 kiểm entrypoint/rule cùng nhắc `LOG_PREFIX`.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 204 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`; `check-ai-layer` 0 lỗi; test E7 11/11. Giảm 7 test so với Phase 2 do bỏ test của mapper/END-trong-finally.
- Commit: chưa commit/push.
- Đồng bộ rule log lên main (user cho push thẳng, `CARENEST_ALLOW_MAIN_PUSH=1`): commit `921bc09` trên worktree main; ở main `check-ai-layer` 0 lỗi, E7 11/11, `VERIFY PASS full | tests 27 fail 0 err 0 skip 0`. Guide trên main giữ ví dụ code cũ (chưa có ChildController) với format log mới.
