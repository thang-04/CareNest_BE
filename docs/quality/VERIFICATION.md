# Verification — bằng chứng trước khi báo xong

## Chọn một phụ huynh nhận tài khoản — 05/11/2026

- User đã duyệt Spec013/plan liên repo; triển khai flag recipient, V70/index/backfill, form chỉ tạo/liên kết người được chọn, import CSV/XLSX/XLS lưu hồ sơ trước, API recipient/pending/activation có scope và chặn raw bypass/stale selection. Giữ liên hệ/ảnh/người đón và mọi historical link cũ.
- Sau clean và source/test cuối: VERIFY PASS full | tests1087 fail0 err0 skip0 | archunit ok | snapshot ok | spotless ok | 586s | log target/verify.log. Intentional OpenAPI snapshot + FE route cache đồng bộ; không thêm dependency. Test PostgreSQL gồm selected-only account, deferred import, idempotency, claim CCCD atomic, batch CHD-04, pending/left-campus scopes, projection theo userId, migration legacy và partial unique.
- FE full808tests đạt; Chromium app/mock1440/375 đạt form/chọn người thứ2/ảnh/save/reload, CSV selection, import zero accounts và kích hoạt sau. 0 lỗi JS, 12/12 URL ảnh thu hồi mỗi viewport. Chưa kiểm browser nối BE, Keycloak/SMS/S3 thật; module source hiện có giữ nguyên.
- Review độc lập và kiểm lại blocker cuối đã hoàn tất, không còn blocker trực tiếp. Incident CHECK NOT VALID được ghi và có regression. Check-ai-layer cuối 0 lỗi, các cảnh báo cũ không đổi. Không commit/push/deploy; kế hoạch và evidence tại Spec013 quickstart.

## Ảnh tiếp nhận trẻ và phụ huynh — 05/11/2026

- User duyệt plan 2026-11-05-child-enrollment-photos, Spec012. Migration V68; private S3 metadata trên trẻ và từng phụ huynh; JSON/multipart tiếp nhận tương thích; đọc ảnh/hồ sơ theo scope, metadata quản trị tài khoản được chiếu riêng; pickup dùng ảnh hồ sơ rồi fallback ảnh APP.
- Targeted clean build 95/95 đạt với PostgreSQL Testcontainers, S3/Keycloak/SMS mock; thêm regressions review và OpenAPI snapshot đã đạt sau sửa. Full gate sau source/test cuối: VERIFY PASS full | tests 1073 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 516s | log target/verify.log. Không tuyên bố đã kiểm S3 thật hoặc browser nối BE thật.
- FE full gate đạt 799 tests; actual Chromium mock 1440/375 chọn/bỏ/chọn lại/đổi người/chuyển bước/lưu/mở lại/reload đúng ảnh, 0 lỗi JS, không tràn ngang, 9/9 object URL được giải phóng mỗi viewport. Review độc lập và review lại không còn blocker; chi tiết Specs012 quickstart.
- Trước commit theo yêu cầu user: đã sửa liên kết incident FE bị hỏng sang URL blob tại commit994002c (tệp được xác minh trong Git); check-ai-layer BE đạt 0 lỗi/12 cảnh báo. Không sửa mã giáo dục hoặc nội dung lỗi cũ.


## Báo cáo tổng hợp tăng trưởng theo tháng, mẫu 5.1 (HLT-20) — 2026-11-04

- Nguồn user 2026-11-04: HT (mọi điểm trường), PHT (điểm trường được phân công) xem bảng 5.1 theo tháng; quy tắc đếm theo brief người điều phối cùng ngày. Plan `docs/plans/active/2026-11-04-health-growth-report.md`, bằng chứng chi tiết `specs/011-health-growth-report/quickstart.md`.
- Sau sửa source/test cuối: `VERIFY PASS full | tests 1055 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 477s | log target/verify.log`. Snapshot OpenAPI chỉ thêm path `/api/health-checks/growth-report` và schema `HealthGrowth*`; không đổi API cũ, không migration. Consumer FE: agent FE làm màn báo cáo với BE giả; APP không gọi.
- Testcontainers chạy thật (skip 0): phạm vi HT/PHT, 403/404, tháng không có đợt/kỳ, phiếu khám kết luận tính, nháp/vắng không tính, `unclassified`, thứ tự lớp, tổng MG/NT/điểm trường/toàn trường, trẻ chuyển lớp, điểm trường ngừng hoạt động. Review độc lập 1 lượt (subagent, chỉ đọc): không P1/P2, P3 đã sửa trừ hiệu năng (chấp nhận). Chưa chạy Web nối BE thật; không commit/push.

## Kỳ cân đo tự đóng như đợt khám (HLT-17) — 2026-11-04

- Nguồn user 2026-11-04: "Kỳ cân đo cũng sẽ tự đóng khi qua ngày kết thúc". Job tự đóng mọi loại, lý do lịch sử theo loại, mở lại kỳ hết hạn bắt buộc ngày kết thúc mới; plan `docs/plans/active/2026-11-04-intake-weeks-round-dates.md` (phần 2).
- Sau sửa source/test cuối: `VERIFY PASS full | tests 1014 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 557s | log target/verify.log`. Snapshot OpenAPI không đổi; không migration, không đổi mã/câu lỗi.
- Testcontainers chạy thật: 5 test HLT-17 chạy cho cả EXAM và MEASUREMENT (tự đóng một lần, kết thúc hôm nay chưa đóng, không đụng đợt đã hủy/đóng tay, mở lại rồi tự đóng lại, mở lại cần ngày từ hôm nay); kỳ cân đo tự đóng ⇒ vắng, nhập/sửa 5018; kết thúc hôm nay vẫn nhập tới 00:00. Chưa chạy Web nối BE thật; không commit/push.

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


## ChildDay sức khỏe sau trả và liên hệ — 05/11/2026
- RED `PickupIntegrationTest.teacherHandsOverPresentChildAndParentIsNotified`: canRecord true sau trả; GREEN 3/3, POST mới vẫn 5008, nguồn liên hệ hồ sơ và scope chéo lớp được kiểm trên PostgreSQL thật.
- `node scripts/verify.mjs` → `VERIFY PASS full | tests 1039 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 672s | log target/verify.log`.
- Snapshot diff chỉ thêm ChildDay.contacts/handedOverAt và DailyHealthContact; không đổi request/endpoint/API code. FE adapter/panel đã map; App chưa triển khai và không sửa.
- FE full 840 tests fail/skip 0, browser export và chuẩn hóa nhập đạt; chưa browser E2E với runtime BE khởi động lại. User chọn chờ loại nhiệt kế nên giữ dải nhập hiện tại. Không migration, commit hoặc push.

## OBS-27 mục tiêu đạt từ 50% hoạt động — 05/11/2026

- Cần chốt theo quy tắc cô ở trường: mỗi hoạt động cô đã chấm là một lần; đạt ít nhất 50% thì +, chưa chấm/ngày vắng không tính; không lần nào để trống. Không đổi tổng kết chủ đề 70%, sửa tay, khóa xác nhận hoặc API.
- Service gom theo trẻ + dòng gốc MT, khử trùng trong một hoạt động và lọc điểm danh có mặt đúng lớp/ngày. Phiếu năm nhận lại qua luồng sẵn có; bảng đã xác nhận vẫn dùng snapshot.
- Test đầu vướng fixture JDBC sửa đối tượng đã đọc qua JPA trong cùng transaction; đã clear persistence context để kiểm lần đọc tiếp, không đổi logic production vì fixture. Review read-only chỉ ra test freeze cần thêm hai lần chưa đạt để làm kết quả live đổi; đã tăng cường ca này.
- Lượt test tiếp theo phát hiện fixture dùng trạng thái ABSENT không thuộc enum BE (EXCUSED/UNEXCUSED); sửa fixture thành EXCUSED. `node scripts/verify.mjs` → `VERIFY PASS full | tests 1041 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 661s | log target/verify.log`. Nhóm ThemeEvaluationIntegrationTest 8/8 đạt với PostgreSQL thật; FE full verify đạt. Không sửa cấu hình local, không commit/push.

## Bảng đánh giá học kỳ theo lĩnh vực — 05/11/2026

- PostgreSQL tích hợp kiểm kỳ cấu hình, biên 50% hai tầng, dữ liệu thiếu, quyền, snapshot lớp và lũy kế năm có lớp cũ. 11 test tập trung đạt.
- Full verify: 1044 tests, 0 fail/error/skip, ArchUnit/OpenAPI/Spotless đạt. OpenAPI giữ operationId cũ và thêm contract học kỳ mới.
- FE verify full đạt. Local BE :8081 và FE :5190 đã lên, trình duyệt đến màn đăng nhập. Chưa xác nhận thao tác trình duyệt sau đăng nhập trên bản thử riêng.

## Sửa script dữ liệu demo học kỳ — 08/11/2026

- Lỗi user gửi là MismatchedInputException START_ARRAY khi đọc snapshot bảng học kỳ đã xác nhận. Script cũ chốt bảng với snapshot=[]; nay script tạo nháp để service chốt nội dung đúng cấu trúc khi giáo viên xác nhận.
- DB local đã sao lưu và trả về nháp 10 bản đúng marker dữ liệu dev, snapshot=[], confirmed_at khác null. Giữ note và tăng version. Backup trong TEMP, chi tiết incident BUG-261108-semester-demo-array-snapshot.
- Regression mới thực thi block SQL seed hai lần rồi GET bảng. Lần chạy đầu thiếu ngày học kỳ trong fixture; đã bổ sung cấu hình. Sau sửa format cuối, `mvnw.cmd -q -Dtest=ThemeEvaluationIntegrationTest,SemesterDomainResultTest test spotless:check` đạt, exit 0. Tổng 12 test, không lỗi hoặc skip; Spotless đạt.
- `node scripts/verify.mjs` full đã dừng có chủ đích sau 957 giây để chuyển sang kiểm tra tập trung. Trước khi dừng đã ghi 1017 test, 0 failure/error/skip; full trả FAIL do JVM test bị dừng, không coi là full pass.

## Trẻ vắng trong phiếu ngày, cuối năm chỉ chủ đề đã xong — 10/11/2026

- Cần chốt 10/11: `DailySheetResponse.absentChildren` (trẻ vắng / chưa điểm danh, chỉ hiện; vẫn chặn lưu ASSESSMENT_CHILD_NOT_PRESENT). `childThemeResults` bỏ chủ đề chưa tới ngày kết thúc. OpenAPI snapshot cập nhật có chủ đích (thêm `AbsentChild`, `absentChildren`); FE đã đọc field mới.
- Test tập trung `DailyAssessmentIntegrationTest, ThemeEvaluationIntegrationTest, OpenApiSnapshotTest, ConcurrentDailyAssessmentIntegrationTest, ClassCommentServiceTest`: 35 test, 0 fail/error/skip, BUILD SUCCESS.
- `node scripts/verify.mjs` full: VERIFY PASS, 1221 test, 0 fail/error/skip, ArchUnit/snapshot/Spotless đạt (2059 giây).

## Chất lượng tháng: trẻ ra lớp, bé chăm khi chuyển lớp/nghỉ hẳn — 10/11/2026

- Cần chốt 10/11 (OBS-23): dòng toàn trường chỉ đếm trẻ nghỉ hẳn; dòng lớp/khối đếm chuyển lớp + nghỉ hẳn kèm %; bé chăm theo thời gian trẻ ở lớp, trẻ rời lớp trong tháng không xét bé chăm ở lớp đó; nghỉ có phép vẫn là nghỉ.
- Test mới `schoolTotalCountsOnlyChildrenWhoQuitWhileClassRowCountsTransfers`; sửa kỳ vọng `AssessmentHistoryIntegrationTest#qualityReportUsesDaysChildBelongsToClassAndIssuedTickets` (trẻ chuyển đi không còn là bé chăm ở lớp cũ).
- Full verify trước khi kéo dev: VERIFY PASS, 1222 test, 0 fail/error/skip. Sau đó fast-forward lên origin/dev 0b17e54 (AUTH-26), gộp 3 chiều BUSINESS_RULES.md và ApiCode.java không conflict; verify lại đang chạy.
## Phân quyền động: phạm vi nhiều cơ sở, 2 GV đứng lớp, dạy thay, HT sửa suất thay thế — 10/11/2026

- Kéo origin/dev 0b17e54 (fast-forward). Test phạm vi STAFF_ASSIGNMENT_MANAGE (SCHOOL làm được mọi cơ sở, CAMPUS chỉ cơ sở mình, thu hồi bị chặn).
- Cần chốt 10/11: SCH-04 năm đang học đúng 2 GV + chủ nhiệm (2328, 2329); dạy thay bắt buộc chọn người được thay (V85 `replaced_user_id`, mã 2330–2333); NUT-02 HT điều chỉnh được suất thay thế. OpenAPI snapshot cập nhật có chủ đích.
- `node scripts/verify.mjs` full: VERIFY PASS, 1224 test, 0 fail/error/skip, ArchUnit/snapshot/Spotless đạt (951 giây).
- Chưa sửa: StaffAccessPolicy (phân công nhân sự vào cơ sở) chưa đọc override SCHOOL — chờ Cần chốt.
