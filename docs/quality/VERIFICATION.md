# Verification — bằng chứng trước khi báo xong

## Tuần thai bắt buộc, kỳ cân đo bỏ vòng đầu, ngày đo/ngày khám do hệ thống ghi — 2026-11-04

- Nguồn user 2026-11-04 (G94-180/CHD-05, HLT-01, HLT-08, HLT-13) và điều chỉnh của người điều phối cùng ngày (kỳ cân đo chưa đóng mà đã qua ngày kết thúc ⇒ ngày đo = ngày kết thúc, chờ user soát). Plan `docs/plans/active/2026-11-04-intake-weeks-round-dates.md`.
- Sau sửa source/test cuối: `VERIFY PASS full | tests 1007 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 461s | log target/verify.log`. Snapshot OpenAPI đổi có chủ đích 3 schema (`HealthMeasurementRequest` bỏ `date`, `headCircumferenceCm`; `HealthExamRequest` bỏ `date`; `ChildHealthRecordResponse` bỏ `headCircumferenceUntil`); bỏ mã 5021, đổi câu 5020 và lỗi ô `date` của phiếu khám (`docs/system/CROSS_REPO_MAP.md` mục 04/11). Consumer FE: agent FE làm song song; APP không gọi.
- Testcontainers chạy thật (skip 0): tuần thai lưu cho trẻ không sinh non, ngày đo kẹp trong kỳ, sửa giữ ngày đo và vòng đầu cũ, ngày khám chốt ở lần lưu đầu, trùng ngày báo HT/PHT; WebMvc: client cũ gửi `date`/`headCircumferenceCm` vẫn được nhận. Review độc lập 2 lượt (subagent, chỉ đọc): không P1/P2. Chưa chạy Web nối BE thật; không migration, không commit/push.

## Cảnh báo ở sổ sức khỏe, mức BMI kỳ cân đo, bỏ phiếu theo dõi — 2026-11-04

- Nguồn user 2026-11-04: đợt khám = kết luận bác sĩ, kỳ cân đo = cảnh báo BMI, bỏ phiếu theo dõi, cảnh báo lưu ở sổ sức khỏe (HLT-16 REMOVED, HLT-18, HLT-19, P-40). Plan `docs/plans/active/2026-11-04-health-warnings-book.md`, bằng chứng chi tiết `specs/010-health-warnings-book/quickstart.md`.
- Sau sửa source cuối: `VERIFY PASS full | tests 999 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 471s | log target/verify.log`. Snapshot OpenAPI đổi có chủ đích (bỏ `/health-follow-ups/**`, thêm trường cảnh báo); consumer FE cập nhật cùng ngày (BE giả), APP chưa gọi API.
- Review độc lập: P1 nguồn "vấn đề đã biết" đã sửa và có test; không migration, không xóa dữ liệu phiếu cũ, không commit/push. Chưa chạy Web nối BE thật.
- Bỏ câu "vấn đề mới hay đã biết" (user yêu cầu cùng ngày): validator không bắt `issueKind`, phiếu cũ đã trả lời giữ khi sửa, bỏ `knownCondition` (snapshot OpenAPI bỏ 2 trường). Sau sửa source cuối: `VERIFY PASS full | tests 999 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 522s | log target/verify.log`.

## Tự đóng đợt khám và mở lại với hạn nhập bù — 2026-11-01

- Nguồn user 2026-11-01 (HLT-17): EXAM tự đóng khi qua ngày kết thúc theo giờ trường, chạy lúc 00:00 và khi ứng dụng khởi động để xử lý thời gian tắt máy. Tái sử dụng đóng tay cho vắng/chưa kết luận/khóa kết quả; phiếu theo dõi vẫn mở; lịch sử đóng tự động ghi actor hệ thống. Kỳ cân đo giữ nguyên.
- User chốt đợt mở lại vẫn tự đóng: request reopen nhận thêm ngày `endDate`; đợt khám đã hết hạn phải chọn ngày mới từ hôm nay. Web đã thêm ô ngày; request cũ chỉ có `reason` vẫn dùng với đợt còn hạn/kỳ cân đo; không đổi endpoint hoặc response. Snapshot OpenAPI chỉ thêm `endDate: string/date` trong `HealthCheckReopenRequest`.
- Flyway V67 nới actor đóng/historical user cho hệ thống, không sửa migration cũ; PostgreSQL Testcontainers đã chạy migration và kiểm closure, reopen, lịch sử, kết quả và phiếu theo dõi thật. 25/25 test health + snapshot đạt, không skip. Snapshot cập nhật có chủ đích và kiểm diff 3 dòng; cổng full sau đó ghi kết quả trong `target/verify.log` và output bàn giao.
- Chrome trên Web mock đã kiểm tự đóng, mở lại với ngày mới `dd/mm/yyyy`, validation và tự đóng lần hai. Chưa chạy UI nối API thật. Không commit/push.
## Delta006 EDU-48 và ellipsis — 02/11/2026

- User chốt xóa cả nhánh và select một dòng, sau đó duyệt plan L bằng “Duyệt kế hoạch và triển khai tới khi sạch”. Domain14/spec/clarify/plan/research/data-model/contracts/tasks giữ lịch sửT001..T011, thêmT012..T022; constitution1.0.0 không đổi. Analyze/review không blocker, prefix lịch sử4artifact/14mục/22task unique đã kiểm trước code; sự cố sửa trạng thái docs được phục hồi và ghi ENV-261102-powershell-replacement-pairs.
- Delta chỉ production FE và hồ sơ BE; không source/API/DB/dependency/APP backend. FE11unit mới/44targeted/full386fail0skip0 và browser4viewport đạt. Real-BE API-only E2E1/1(15.7s) xác nhận/Quay lại/xóa/lưu/mở lại/ID-content-theme-break preservation/ellipsis; cleanup ownedmarkers0, không SQL. Xem [FE evidence](../../../CareNest_FE/docs/quality/VERIFICATION.md) và [quickstart006](../../specs/006-year-plan-goal-hierarchy/quickstart.md).
- BE verify --quick trong lượt:14tests fail0err0skip0, archunit/spotlessok, snapshot không chạy; không dùng quick để nhận lại toàn bộ migration/contract. Cổng cuối: check-ai-layer hai repo và verify theo scope sau hồ sơ; output bàn giao/target/verify.log.
- Converge chỉ đánh giá deltaFR009..015/SC005..008, không lấy completionDB29/10 thay evidence mới; kết quả/hash ở quickstart006. Không commit/push/deploy hoặc sửa dữ liệu thật, giữ .tmp_doc_edit ngoài phạm vi.

## Hồ sơ sửa LVC và giao diện chọn từ Bộ — 02/11/2026

- User cho commit/push bản fix trước rồi triển khai UI/commit/push FE và BE. Fix FE994002c đã push; RED4ca ban đầu và1ca nguồn hỗn hợp → GREEN39targeted, FEfull375/375fail0/skip0. BE chỉ đồng bộ incident/index và bằng chứng; không thay source backend/API/DB/dependency/APP.
- UI dựa ảnh thứ hai đã duyệt, chỉ trình bày chức năng hiện có EDU-24/28: chọn tất cả mục tiêu đang hiển thị, đếm, tìm/lọc, phân cấp, xem nội dung đi kèm; thẻ nội dung chọn đích/lọc/tick từng dòng, nhắc lưu tay. Xem [bằng chứng FE](../../../CareNest_FE/docs/quality/VERIFICATION.md). Không coi đề xuất đầu hoặc ảnh AI là feature đã code.
- Browser FE source/CSS thật với hook/dữ liệu/callback giả đạt tại360/390/768/1440,0lỗiJS; bước ghép dùng helper thật. Chưa kiểm persistence/API với BE thật. Cổng BE cuối cho delta tài liệu: check-ai-layer và verify --quick, kết quả xem output bàn giao/target/verify.log; không dùng quick để tuyên bố toàn bộ test API đã chạy.

## Pre-PR monthly roster fixes — 02/11/2026

- User approved four findings from FE pre-PR report. Two monthly regressions RED against previous source → GREEN, covering first listing after transfer, leavers/joiners, name/history, repeated listing and teacher/VP scopes. MONTH uses period overlap; YEAR retains current roster.
- After final source edits: `VERIFY PASS full | tests 731 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 427s | log target/verify.log`. DailyAssessmentIntegrationTest: 11/11. FE: full 487 tests and Chrome demo smoke 9/9. No real browser-to-BE/mobile E2E in this follow-up. No commit/push/PR or local database reset.

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
