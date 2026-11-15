---
title: Deploy an toàn (backup DB, tự rollback, kiểm key) và rút ngắn verify
status: in-progress
owner: thangnd
jira: G94-340   # mã công việc BE-FIX-04
branch: fix/G94-340-BE-FIX-04-deploy-rollback
modules: [deployment]
rules: []
risk: high
created: 2026-11-12
updated: 2026-11-13
---
# Deploy an toàn (backup DB, tự rollback, kiểm key) và rút ngắn verify

Plan liên repo (BE + FE). Gộp theo yêu cầu user 2026-11-12; thay cho bản nháp `2026-11-12-shared-postgres-test-container.md`. Lệnh xử lý sự cố theo hiện trạng: `docs/system/INCIDENT_RUNBOOK.md`.

## Context
**A. Deploy lỗi thì site ngừng cho tới khi merge bản sửa.**
- Test fail ⇒ không deploy (`.github/workflows/deploy.yml:174`, `:290`). Lỗi chỉ gây ngừng khi xảy ra **sau** `docker compose up` (`deploy.yml:231`, `:344`): container cũ đã bị thay, health fail thì job in log rồi `exit 1` (`deploy.yml:241-245`), không quay lại.
- Không có backup DB ở bất kỳ bước nào. `docs/system/DEPLOYMENT.md:80` ghi rõ chỉ rollback tay được khi không có migration mới.
- Sự cố đã ghi (`docs/knowledge/incidents/ENV-261112-dev-seed-staff-username-conflict.md`): deploy dev, seed repeatable lỗi `23505`, api `Restarting`. Flyway chạy migration version trước repeatable, nên schema đã lên bản mới trước khi lỗi; image cũ không còn chắc chạy được.
- CI ghi đè `compose.*.yaml` và file realm trên VPS **trước** khi khởi động bản mới (`deploy.yml:202-210`).
- `main` có 80 migration, `dev` có 122: lần release kế tiếp chạy 42 migration trên DB production trong một lần, không có đường lui.
- Dev dùng chung container Postgres với production (`compose.dev.yaml:58`).
- FE: `docker image prune -f` chạy trước bước kiểm tra (`FE:.github/workflows/deploy.yml:103-106`), source trên VPS bị `rsync --delete` (`FE:.github/workflows/deploy.yml:84`) ⇒ không còn bản cũ.

**B. Key cấu hình.** `.env` trên VPS đặt tay; compose chỉ truyền biến được liệt kê (`compose.vps.yaml:91-113`). Key mới phải sửa 3 chỗ (compose, file mẫu, VPS). Thiếu key không có `:?` ⇒ app nhận chuỗi rỗng. Lệnh compose tay cần `API_IMAGE`/`KEYCLOAK_IMAGE` mà không nơi nào lưu.

**C. Verify chậm.** `target/verify.log` 2026-11-12: 133 class, 523s; 47 class integration chiếm 487s. 60 class tự dựng `PostgreSQLContainer` (vd. `src/test/java/com/carenest/service/attendance/impl/PickupIntegrationTest.java:66-68`); mỗi class tốn khoảng 2s container, 2.5s Flyway, 3s context. 33/54 class `@SpringBootTest` có `@TestConfiguration` lồng nên không tái dùng context.

## Quyết định đã chốt
1. Một plan chung cho rollback, key cấu hình, tốc độ verify — user, 2026-11-12.
2. Lỗi DB lúc deploy phải có rollback — user, 2026-11-12.
3. Giữ nguyên bộ cổng pass (toàn bộ test, ArchUnit, snapshot OpenAPI, Spotless); không tách integration khỏi PR — user, 2026-11-12.
4. Tối ưu verify bằng container Postgres dùng chung — user, 2026-11-12.

## Làm rõ nghiệp vụ
Không đổi nghiệp vụ, API, quyền. Đổi hạ tầng deploy (BE, FE) và hạ tầng test (BE). APP không ảnh hưởng.

### Câu hỏi mở
- [x] Mã công việc và nhánh cho BE: `BE-FIX-04`, nhánh `fix/G94-340-BE-FIX-04-deploy-rollback` — user, 2026-11-13. Phase 2 (FE) nằm ngoài nhánh này: chưa được phép sửa repo FE, chưa có task FE (giả định khi tự chạy, chờ user).
- [x] Sự cố "ngày 11/11": giả định là `ENV-261112` (deploy dev, seed lỗi); user chưa xác nhận.
- [x] Thư mục và số bản backup: dùng đề xuất `/data/thangnd/CAPSTONE/backups/<env>/`, giữ 5 bản (giả định khi tự chạy, chờ user soát). Dung lượng đĩa VPS vẫn chưa biết; script kiểm đĩa trước khi dump.
- [x] Diễn tập rollback trên VPS: chưa có người nhận; AC-1…AC-5 chưa kiểm chứng trên VPS, theo dõi ở Progress log mục "Còn lại".
- [x] Phase 5: không làm, vì Phase 4 đã bỏ — user, 2026-11-13.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Mã Jira cho việc này | G94-340 | user | 2026-11-12 | frontmatter `jira` |

## Luồng deploy BE mới (production và dev dùng chung một script)
1. **Kiểm trước, chưa đụng gì:** có `.env`; mọi key trong file mẫu có trong `.env`; `docker compose config -q` đạt; Postgres chạy; `docker pull` hai image. Lỗi ⇒ dừng, bản cũ vẫn phục vụ.
2. **Ghi trạng thái cũ:** tag image đang chạy; chép compose và file mount hiện tại vào `.rollback/`. CI tải file mới vào `.incoming/` thay vì ghi đè trực tiếp.
3. Tag api và Keycloak đều không đổi ⇒ bỏ qua bước 4–6.
4. **Dừng api, `pg_dump -Fc` DB ứng dụng**, kiểm file đọc được (`pg_restore -l`). Dump lỗi ⇒ bật lại api cũ, dừng deploy. Tag Keycloak đổi ⇒ dump thêm DB Keycloak. Ghi `max(installed_rank)` của `flyway_schema_history`.
5. Đưa file mới vào chỗ, `up -d --no-build`, chờ health tối đa 3 phút.
6. **Health fail ⇒ rollback:** dừng api mới; trả file từ `.rollback/`; nếu `installed_rank` đã đổi thì khôi phục DB ứng dụng từ dump; `up -d` với tag cũ; chờ health. Job luôn đỏ, ghi rõ `ROLLED BACK` hoặc `ROLLBACK FAILED`.
7. **Health đạt:** ghi tag vào `.deploy.env`; dọn image nhưng giữ lại bản liền trước; giữ 5 dump mới nhất. Caddy và smoke test như cũ; smoke fail ⇒ chạy bước 6.

## Acceptance criteria
- **AC-1**: Given bản mới có migration lỗi, When deploy, Then job đỏ, api chạy lại tag cũ và health đạt, `installed_rank` bằng trước deploy.
- **AC-2**: Given bản mới crash lúc khởi động và không có migration, When deploy, Then rollback về tag cũ, DB không bị khôi phục.
- **AC-3**: Given `.env` thiếu một key có trong file mẫu, When deploy, Then job đỏ ở bước kiểm trước, container cũ không bị dừng.
- **AC-4**: Given deploy thành công, Then có file dump mới, `.deploy.env` chứa hai tag, image liền trước còn trên VPS, thư mục backup không quá 5 file.
- **AC-5**: Given rollback trên dev có khôi phục DB, Then DB `carenest` và `keycloak` của production không đổi.
- **AC-6** (FE): Given bản FE mới không trả 200, When deploy, Then container chạy lại image trước và trả 200.
- **AC-7**: `node scripts/verify.mjs` → `VERIFY PASS full`, số test bằng trước thay đổi, skip 0, 3 lần liên tiếp.
- **AC-8**: Máy không có Docker ⇒ test integration SKIP như hiện tại, không ERROR.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Nơi đặt logic deploy | Một script `deploy/remote-deploy.sh` chạy trên VPS, nhận tham số môi trường | Giữ heredoc trong hai job | Hai job đang lặp cùng khối lệnh (`deploy.yml:225-256`, `:337-369`); thêm rollback vào cả hai sẽ lệch nhau |
| Thời điểm dump | Sau khi dừng api | Dump khi api còn chạy | Dump khớp đúng trạng thái cuối của bản cũ; đổi lại thêm vài giây ngừng |
| Khi nào khôi phục DB | Chỉ khi lịch sử Flyway đã đổi | Luôn khôi phục | Không migration thì không cần, tránh mất dữ liệu vô ích |
| Chặn lỗi migration trước khi dừng bản cũ | Chưa làm | Chạy thử migration trên bản sao DB | App khởi động trên bản sao có thể gửi email/SMS, chạy job nền; xét sau |
| DB Keycloak | Dump khi tag đổi, khôi phục tay theo runbook | Tự khôi phục | Image Keycloak ít đổi; tự khôi phục phải dừng cả Keycloak |
| Key ứng dụng mới | Giữ danh sách tường minh trong compose, thêm bước kiểm key | `env_file` truyền cả `.env` | `env_file` đưa cả mật khẩu Postgres/Keycloak admin vào container api |
| Container test dùng chung | Holder `SharedPostgres`, class test trỏ `@ServiceConnection` vào đó | Base class trừu tượng | Không ép kế thừa, mỗi class đổi 2–3 dòng |

## Phases
### Phase 1 — BE: backup DB và tự rollback
- Files: C `deploy/remote-deploy.sh` · M `.github/workflows/deploy.yml` · M `docs/system/DEPLOYMENT.md` · M `docs/system/INCIDENT_RUNBOOK.md`
- Steps: 1. Viết script theo "Luồng deploy BE mới" bước 2, 4–7. 2. Hai job gọi script. 3. Diễn tập trên dev với image lỗi cố ý (tag `api-drill-*`, xóa sau): một lần có migration lỗi, một lần crash không migration.
- Tests: AC-1, AC-2, AC-4, AC-5 bằng diễn tập trên dev, ghi lệnh và kết quả vào Progress log
- Exit: hai lần diễn tập đạt; `node scripts/verify.mjs` → `VERIFY PASS full`
- **Dừng chờ user review.** Chưa áp cho production tới khi user duyệt kết quả diễn tập.

### Phase 2 — FE: giữ image trước và tự rollback
- Files (repo FE): M `.github/workflows/deploy.yml` · M `docs/integration/DEPLOYMENT.md`
- Steps: 1. Gắn tag `previous` cho image đang chạy trước khi build. 2. Kiểm tra xong mới `prune`. 3. Kiểm tra fail ⇒ `up -d --no-build` với image `previous`.
- Tests: AC-6 bằng diễn tập trên dev
- Exit: diễn tập đạt; `node scripts/verify.mjs` (FE) → `VERIFY PASS`
- **Dừng chờ user review.**

### Phase 3 — Kiểm key trước deploy
- Files: M `deploy/remote-deploy.sh` · M `docs/system/INCIDENT_RUNBOOK.md` (mục 0 và 6 dùng `.deploy.env`)
- Steps: 1. Thêm bước 1 của luồng (so tên key file mẫu với `.env`, `compose config -q`). 2. Cập nhật runbook.
- Tests: AC-3 bằng diễn tập trên dev (tạm xóa một key)
- Exit: diễn tập đạt
- **Dừng chờ user review.**

### Phase 4 — Container Postgres dùng chung cho test
- Files: C `src/test/java/com/carenest/support/SharedPostgres.java` · M 33 class `@Transactional` mức class (9 class không config lồng trước, 24 class còn lại theo từng package). Giữ container riêng: 19 class commit dữ liệu, 6 class test migration, `ChildEnrollmentIntegrationTest`, `YearTransitionIntegrationTest`.
- Steps: 1. Đo số test và thời gian gốc. 2. Spike 2 class. 3. Chuyển từng package, verify sau mỗi package. 4. Đặt `max_connections` cho container dùng chung.
- Tests: AC-7, AC-8
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0, 3 lần liên tiếp; ghi thời gian trước/sau
- **Dừng chờ user review.** Spike fail ⇒ dừng, báo lại.

### Phase 5 — Gộp config `Clock` (tùy chọn)
- Chỉ làm nếu user đồng ý ở Câu hỏi mở. Exit: `VERIFY PASS full`, skip 0.

Plan vượt 3 phase và 8 file: user yêu cầu gộp; mỗi phase tự đứng được và có điểm dừng riêng.

## Test matrix
| AC | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- |
| AC-1, AC-2, AC-4, AC-5 | Diễn tập deploy trên dev | 1 | |
| AC-6 | Diễn tập deploy FE trên dev | 2 | |
| AC-3 | Diễn tập thiếu key trên dev | 3 | |
| AC-7, AC-8 | `node scripts/verify.mjs`, đếm trong `target/verify.log` | 4 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Bản mới chạy được vài phút rồi mới fail health, có người kịp ghi dữ liệu | Khôi phục DB làm mất phần ghi đó | Chỉ khôi phục khi Flyway đã đổi; ghi rõ trong log job | Dump trạng thái lỗi trước khi khôi phục |
| Script rollback tự nó lỗi | Site ngừng như hiện tại | Diễn tập trên dev trước; runbook có lệnh tay | Lệnh tay mục 4–5 runbook |
| Lệnh khôi phục chạm DB production khi deploy dev | Mất dữ liệu production | Tên DB là tham số bắt buộc, script từ chối nếu không khớp môi trường; AC-5 | Dump production gần nhất |
| Đĩa đầy vì dump | Postgres dừng | Giữ 5 bản; kiểm dung lượng trống ở bước kiểm trước | Xóa dump cũ |
| Test dùng chung DB phụ thuộc thứ tự hoặc id cố định | Test fail | Chuyển từng package, chạy 3 lần | Trả class về container riêng |

Ước tính, chưa đo: Phase 4 bớt khoảng 140–150s trên 523s. Thời gian ngừng thêm do dump ở Phase 1 chưa đo.

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể (không đổi contract)
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [x] >8 file hoặc >3 phase ⇒ đã đề xuất tách (user yêu cầu gộp, ghi ở cuối mục Phases)
- [x] Không dùng rule PENDING/OPEN như đã chốt
- [x] Tự review: không placeholder · không mâu thuẫn · scope theo yêu cầu user · không câu mơ hồ

## Progress log
### 2026-11-12 — Viết plan (chờ review)
- Commit: chưa commit
- Verify: chưa chạy (chưa sửa code)
- Đã tạo `docs/system/INCIDENT_RUNBOOK.md` theo hiện trạng; lệnh chưa chạy thử trên VPS
- Tiếp: user trả lời Câu hỏi mở và duyệt plan

### 2026-11-13 — Phase 1, 3, 4 (chế độ tự chạy, chưa commit, chưa tạo nhánh)
- Giả định (user chọn tự chạy, soát ở báo cáo): nhánh thực hiện chưa tạo, sửa trên nhánh hiện tại; Phase 2 (FE) **chưa làm** vì không được sửa repo FE; Phase 5 **không làm**; backup `/data/thangnd/CAPSTONE/backups/<production|dev>/`, giữ 5 bản; `.env` trên VPS phải đủ mọi key của `deploy/*.env.example` (thiếu ⇒ deploy dừng ở bước kiểm trước, kể cả key có giá trị mặc định như `CARENEST_MOBILE_REDIRECT_URI`).
- Phase 1+3: `deploy/remote-deploy.sh` (kiểm key, `compose config`, dump, rollback, chế độ `rollback` cho smoke fail); `deploy.yml` hai job gọi script, file vào `.incoming/`; `DEPLOYMENT.md` mục Rollback và `INCIDENT_RUNBOOK.md` đã cập nhật.
- Kiểm chứng script: chạy với `docker`/`curl` giả (không có VPS): deploy tốt, crash không migration (không khôi phục DB), migration lỗi (khôi phục DB), tag không đổi (không dump), thiếu key (dừng, không dừng container cũ), giữ 5 dump, chế độ `rollback`, chặn khôi phục DB dev trong deploy production. Lỗi tìm ra và sửa: `ls` không khớp file làm `pipefail` kết thúc script sau deploy thành công. **Chưa diễn tập trên VPS thật** (AC-1…AC-5 chờ người có SSH): lệnh `psql`/`pg_dump` trong container dùng socket không `-h`, theo runbook, chưa kiểm chứng.
- Phase 4: `src/test/java/com/carenest/support/SharedPostgres.java`; 33 class `@Transactional` chuyển sang container dùng chung (trừ `ChildEnrollmentIntegrationTest`, `YearTransitionIntegrationTest`). Spike 2 class (`PickupIntegrationTest`, `DryStockIntegrationTest`): 10 test pass.
- Verify sau Phase 4 (2 lần, cùng máy): `VERIFY PASS full | tests 1222 fail 0 err 0 skip 0`, 622s và 585s. Mốc 523s (2026-11-12) đo trước khi có thay đổi, lúc đó cây làm việc có thể khác ⇒ **chưa chứng minh được tăng tốc**; AC-7 đạt về pass/skip, chưa đạt về thời gian và chưa chạy đủ 3 lần. Cần đo A/B (cùng cây, có/không `SharedPostgres`) trước khi giữ Phase 4.
- Tiếp: đo A/B Phase 4; diễn tập `remote-deploy.sh` trên dev bởi người có SSH; Phase 2 (FE) khi được phép sửa repo FE.
- Đo A/B Phase 4 (2026-11-13 chiều, chạy liền nhau cùng máy, cùng cây làm việc): A (33 class dùng container riêng) 882s; B (`SharedPostgres`) 903s; cả hai `VERIFY PASS full | tests 1222 fail 0 err 0 skip 0`. Máy lúc đo chậm hơn buổi trưa (585–622s cho B), nên dao động giữa các lần lớn hơn chênh lệch A/B. **Kết luận: không đo được tăng tốc từ container dùng chung**; ước tính 140–150s trong plan không đạt. Cây làm việc đang ở bản B. Chờ user quyết giữ hay bỏ Phase 4.
- **Bỏ Phase 4 — user, 2026-11-13** (thay Quyết định đã chốt số 4; AC-7, AC-8 không còn áp dụng): 33 class test trả về container riêng, khớp `HEAD`; xóa `SharedPostgres.java`. Phase 5 theo đó cũng không làm.
- Verify sau khi bỏ Phase 4: `VERIFY PASS full | tests 1222 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 710s` (`--quick` tự nâng lên full).
- Còn lại: diễn tập `remote-deploy.sh` trên dev (AC-1…AC-5, cần SSH); Phase 2 FE (AC-6, cần phép sửa repo FE); nhánh và commit chờ user.
- Nhánh `fix/G94-340-BE-FIX-04-deploy-rollback` (user cho phép 2026-11-13), tạo từ `origin/dev` vì `dev` trên máy chậm 105 commit; chỉ mang file của task, phần Twilio trong `DEPLOYMENT.md` để lại cho luồng authentication.
