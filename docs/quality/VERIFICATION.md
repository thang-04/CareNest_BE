# Verification — bằng chứng trước khi báo xong

## Feature009 — phase backend nhập đầy đủ kế hoạch năm (02/11/2026)

- User duyệt plan/ba quyết định nghiệp vụ và bổ sung phần1dạngbảng; [spec/phase evidence](../../specs/009-full-year-plan-file-import/quickstart.md). Preview additive metadata/themes/breaks; không đổi request/endpoint/schema lưu, dependency hoặc migration.
- Source/test cuối: `VERIFY PASS full | tests 659 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 278s | log target/verify.log`; log lưu target/feature009-final-full.log. Snapshot53dòng chỉ thêm3schema/3field preview, giữ schema Theme/Break request bằng tên mới riêng. Review cuối không còn lỗi chặn.
- RED/GREEN domain4ca và preview/boundary/ranges, full cuối bao phủ11test full-preview mới +4domainvariants; sample cục bộ5lĩnh vực83MT203nội dung389ticks/10chủđề đúngngày+mã/2đợt nghỉ/metadata/0warning. Oracle độc lập cho sốMT/ngày/mã; không coi countscontent/ticks là oracle độc lập. Không copy file gốc/người ký.
- Chưa phaseWeb: preview/merge/chặn tuổi/dời năm trên UI, hàng nút/phần1dạngbảng/browser/real-BE E2E/save-reopen/converge; không gọi toàn feature xong. Không commit/push/deploy. Cổng AI-layer và verify hồ sơ cuối chạy sau ghi bằng chứng; output ở bàn giao.

## Feature008 hoàn tất — bàn giao 02/11/2026

- 19/19 task, 11 FR/5 SC được kiểm; implement/converge vòng1 append T019 sửa giới hạn lưới, vòng2 sạch, không mất lịch sử task. [Progress log](../plans/completed/2026-11-01-branch-plan-file-import.md), [bằng chứng/lượt thử/giới hạn](../../specs/008-branch-plan-file-import/quickstart.md).
- BE sau source cuối: `VERIFY PASS full | tests 644 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 248s | log target/verify.log`. FE full verify342 tests, 0 fail/skip, lint/format/design/build đạt.
- Real-BE jar cuối: branch3/3 (gồm mobile375, overwrite, lịch hệ thống, chuỗi duyệt, quyền và lỗi); goal/import17/17 (gồm .doc bước2). Hồi quy edu-flow19/19 đạt lượt trước decoderguard; đây là39 kịch bản có kết quả đạt qua nhiều lượt, không phải một lượt39/39 xanh.
- File mẫu gốc đọc được35 tên,0cảnh báo parser; chỉ probe cục bộ, không copy file thật/người ký vào repo. Fixtures đều tổng hợp. Chốt3 incident fixed về guard/parser mock và E2E ID domain; review cuối không P1/P2.
- Không migration/commit/push/deploy. E2E dùng runtime/DB riêng đã dọn; BE8080/Keycloak8180 đang dùng giữ nguyên. Mock vẫn chỉ nhập CSV; Word/Excel dùng BE thật.

## Feature008 phase 1 — Word .doc mục tiêu, 01/11/2026

- User duyệt plan và bắt đầu phase 1; T001–T006 done, còn phase 2/3. [Progress log](../plans/completed/2026-11-01-branch-plan-file-import.md), [chi tiết RED/GREEN và giới hạn](../../specs/008-branch-plan-file-import/quickstart.md).
- Sau sửa source cuối: `node scripts/verify.mjs` → `VERIFY PASS full | tests 633 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 260s | log target/verify.log`; OpenAPI diff exit 0. Targeted parser/service: 13 test, 0 fail/error/skip; .doc ô gộp tương đương .docx, malformed/encrypted/no-table/limits, quyền và privacy fixture.
- FE full verify đạt sau định dạng câu lỗi mock; check-ai-layer BE 0 lỗi/12 cảnh báo cũ, FE 0 lỗi/14 cảnh báo cũ. Review độc lập không có P1/P2 chặn.
- Chưa browser E2E, chưa hỗ trợ .doc bằng mock parser Web (CSV như cũ), chưa API nhánh/hai nút luôn hiện; không commit/push. Dừng review phase 1 theo plan, không đánh dấu toàn feature hoàn tất.

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
