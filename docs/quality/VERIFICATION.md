# Verification — bằng chứng trước khi báo xong

Auth React Phase2 (2026-10-12): `VERIFY PASS full | tests 196 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`. Extension38 tests/0 skip; real-container provider E2E26/26. Phạm vi, RED/GREEN, review và các phần chưa kiểm chứng: báo cáo Phase2 (chưa có trong repo).

## Iron Law
Không nói "xong / pass / đã sửa / API không đổi / migration OK" khi chưa có **output lệnh chạy trong lượt này, sau lần sửa cuối**. Kết quả lượt trước, suy luận, "chắc chạy được" không phải bằng chứng. **Skip (Docker tắt) hoặc chưa chạy = chưa kiểm chứng** — báo đúng như vậy (`Verify: chưa chạy — <lý do>`).

Lệnh chuẩn: `node scripts/verify.mjs` (full = `mvnw verify`: test + ArchUnit + snapshot OpenAPI + Spotless) hoặc `--quick` (test liên quan file đổi + ArchUnit + Spotless). Output ≤10 dòng; log đầy đủ ở `target/verify.log` — chỉ grep khi cần, không đọc cả file.

## Bằng chứng theo làn
| Làn | Lệnh | Bằng chứng tối thiểu |
| --- | --- | --- |
| S | `node scripts/verify.mjs --quick` | Dòng `VERIFY PASS quick …` |
| M | `node scripts/verify.mjs` | `VERIFY PASS full …`, `skip 0` |
| L | như M + bước kiểm trong plan | `VERIFY PASS full`, skip 0 + diff snapshot (nếu đổi API) + e2e `x/y` |
| Bug fix (mọi làn) | regression test | Output **fail trước fix** (khớp triệu chứng) + **pass sau fix**; tên test ghi vào incident (nếu có) |

## Claim → bằng chứng
| Claim | Bằng chứng |
| --- | --- |
| API không đổi | Snapshot pass (không skip) + `git diff --exit-code docs/api/openapi.yaml` |
| API đổi có chủ đích | `mvnw.cmd test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true` + diff trong báo cáo + consumer FE/APP cụ thể |
| Migration OK | Test Testcontainers chạy thật (skip 0) — Flyway + `ddl-auto: validate` |
| Kiến trúc đúng | `ArchitectureRulesTest` pass |
| Format | Spotless pass (`mvnw.cmd spotless:apply` để sửa) |
| AI layer nhất quán | `node scripts/check-ai-layer.mjs` exit 0 |

## Gate fail
Sửa nguyên nhân. Không xóa/`@Disabled`/skip test, không nới rule ArchUnit, không `--no-verify`, không regenerate snapshot cho qua. Rule sai thật ⇒ đề xuất sửa rule + lý do, hỏi user. Snapshot đổi ngoài ý muốn = bug contract.

## Báo cáo cuối task
```text
Làn S (≤3 dòng):  Đã làm: … · Verify: VERIFY PASS quick (12 tests) · Memory: <chỉ khi có trigger>
Làn M (≤8 dòng):  Đã làm · Rule/AC · Verify (dòng VERIFY) · Chưa kiểm chứng: … · Ảnh hưởng còn chờ · Memory: …
Làn L:            ghi đủ vào Progress log của plan; chat chỉ tóm tắt + link plan
```

## Lấy mục tiêu từ kế hoạch năm có sẵn — 28/10/2026

- Feature003: 22/22 tasks, converge không còn gap; giữ lịch sử task. Chỉ Web source thay đổi, Java/OpenAPI không sửa.
- FE gate: VERIFY PASS full | npm run verify | log verify.log; education-plan 59/59. BE gate: VERIFY PASS full | tests 410 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 153s.
- Playwright feature003 trên BE/Keycloak thật: 13 passed (1.0m), zero fail/skip sau hardening fixture. Đúng DB runtime qua E2E_PG_DB; cleanup 0 bản import còn sót. Các fault injection được ghi riêng, không giả thành lỗi phát sinh thật ở server.
- Scope/command/failed attempts/giới hạn: [quickstart](../../specs/003-import-year-plan-goals/quickstart.md); plan hoàn tất tại docs/plans/completed/2026-10-28-import-year-plan-goals.md.

## Ba bảng mục tiêu cha–con — 29/10/2026
- Feature006 / phần DB-1 plan mục tiêu năm đã được user duyệt; thực hiện bằng vòng lặp Spec Kit, 11/11 tasks done, converge không còn gap, review độc lập PASS.
- `node scripts/verify.mjs`: `VERIFY PASS full | tests 433 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 247s | log target/verify.log`.
- PostgreSQL thật: 5/5 test V34→V35, gồm thiếu cấp cha, nhiều lĩnh vực/phiên bản, ID/lineage/nội dung/dấu tích, shared sequence và dữ liệu bất hợp lệ rollback.
- CRUD/copy regression: chuyển cha rồi xóa tiêu đề cũ giữ nội dung/dấu tích; remap parent ID/lineage trên phiên bản mới; ID sai plan/type/lặp bị từ chối. Class-doc/học liệu và snapshot đạt trong full gate.
- `git diff --exit-code -- docs/api/openapi.yaml`: exit 0. `node scripts/check-ai-layer.mjs`: 0 lỗi, 0 cảnh báo; E6 bỏ qua theo output công cụ.
- Lỗi migration deferred DDL có RED/GREEN tại BUG-261029-hierarchy-deferred-ddl. Chi tiết: [quickstart feature006](../../specs/006-year-plan-goal-hierarchy/quickstart.md).
- Chưa kiểm chứng: migration trên DB dev/production đang dùng, browser E2E Web. Không chạy migration DB thật trong task; không commit/push/tạo nhánh.
