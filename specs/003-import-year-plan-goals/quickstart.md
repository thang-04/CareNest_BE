# Quickstart validation — Lấy mục tiêu từ kế hoạch năm

Đây là hướng dẫn chạy sau triển khai, không phải bằng chứng tính năng đã được thực hiện. Các test mới nêu dưới đây được tạo ở phase triển khai/tasks; không chạy một test chưa tồn tại rồi báo đạt.

## Điều kiện

- Đọc [spec](spec.md), [contract](contracts/source-import.md), [data model](data-model.md) và plan làn L đã được user duyệt.
- Web đã có node_modules; BE có Java 21/Maven wrapper, Docker nếu chạy full tests. Không cài/nâng dependency.
- Dữ liệu giả: hai năm học, ít nhất hai nhóm tuổi; nguồn có nhiều sub/group, 10 goals và contents/ticks/themes/breaks; đích chứa nội dung cũ. Có Phó HT và Tổ trưởng của nhóm đích. Ca 200 goals dùng fixture tổng hợp, không dữ liệu trẻ thật.
- E2E hiện có dùng BE thật :8080, Keycloak :8180 và Web preview :5181; làm theo [hướng dẫn E2E Web](../../../CareNest_FE/tests/e2e/README.md). Biến E2E_DEV_PASSWORD lấy từ môi trường được phép, không ghi mật khẩu/token vào tài liệu, câu lệnh hoặc log.
- Fixture E2E mới có prefix và cleanup riêng, không sửa/xóa kế hoạch thật hoặc reset DB chung. Các ca nhiều năm/nhóm mở rộng fixture test hiện hành với cleanup đúng ID.

## Lệnh trong CareNest_FE (sau khi có code/test)

```powershell
node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/import-goals.test.mjs tests/education-plan/import-goal-sources.test.mjs tests/education-plan/year-plan-api.test.mjs
node scripts/verify.mjs
node scripts/check-ai-layer.mjs
npm run test:e2e
```

Expected: tests mới và suite cũ không fail; verify in VERIFY PASS full; check-ai-layer exit0; E2E API+system không fail/skip cho ca feature003. Test contract pagination dùng axios adapter fixture hiện có; source change/late response dùng request có kiểm soát, không cần sleep tùy ý.

BE (cổng cuối plan, không sửa source backend):

```powershell
node scripts/check-ai-layer.mjs
node scripts/verify.mjs
```

Expected: lỗi0 và VERIFY PASS full, skip0; lưu output thật vào Progress log. Plan-only hiện tại chỉ chứng minh tài liệu/legacy suite, không chứng minh behavior mới.

## Ma trận thao tác chấp nhận

| Ca | Thao tác | Expected |
| --- | --- | --- |
| AC-1 nguồn khác | Giữ năm header, chọn nguồn khác năm, khác tuổi, khác cả hai | Nguồn đúng filter, không đổi header/đích, không thiếu trang |
| AC-2 quyền | Phó HT chọn khác tuổi; Tổ trưởng chọn/giả ID khác tuổi trong mock, và kiểm GET thật | Import UI/mock đúng EDU-24; BE GET vẫn enforce scope xem hiện hành, không claim provenance |
| AC-3 selection | Tích 1, một tập con, theo lĩnh vực, tất cả, bỏ tất cả, đổi filter/source | Count khớp, zero không xác nhận; reset ngay, không chọn hàng tiêu đề |
| AC-4 nội dung | Chọn goal ở nhiều sub/group, có tên cha trùng; thêm vào đích có dữ liệu | Đúng cây/thứ tự cả sau normalize/mở lại, ID mới, chỉ goal/contents đã chọn, ticks mới rỗng |
| AC-5 giữ dữ liệu | Nguồn có themes/breaks/ticks; đích có hoặc không có; lưu rồi tải lại | Themes/breaks/ticks cũ giữ nguyên, không lấy từ nguồn, nguồn không đổi |
| AC-6 bỏ qua | Goal trùng cả đích lẫn batch; domain thiếu; orphan hoặc first-sub làm đổi cha target cũ | Báo added/duplicates/unavailable đúng; không bịa cha, không silent drop |
| AC-7 nguồn cũ/lỗi | Khi modal mở, thay revision/status, xóa nguồn, giả 403/404/network | Không ghép snapshot cũ, giữ form, báo lỗi và cho retry/xem chọn lại |
| AC-8 race/lease | Đổi filter nhanh, đóng khi request pending, double confirm, mất lease đích | Không gọi import sau đóng/đổi nguồn; một lần ghép; mất quyền không ghép/lưu |
| AC-9 lưu/wizard | Lấy → chưa Lưu → rời; Hủy; Lưu lỗi; Lưu/Ctrl+S → mở lại | Không mutation HTTP trước Save; guard đúng, lỗi giữ form, vẫn wizard 1–2–3 |
| AC-10 usability | Bàn phím, nhãn checkbox, 200 goals, viewport 390/768/1440 | Thao tác selection ≤1s sau tải; không mất focus/nội dung, bảng cuộn trong khung |

## Bằng chứng cần lưu

Node test names có EDU-24/EDU-06/EDU-22, số pass/fail; ảnh/trace synthetic E2E nếu cần; HTTP chứng minh chỉ GET trong modal và POST/PUT khi Lưu; so source/target trước/sau, headers phiên/revision đúng nhưng không in token. Đo thời gian selection sau khi detail sẵn, không trộn với thời gian tải mạng.

Nếu Docker/Keycloak/credential/E2E không sẵn: ghi rõ chưa kiểm chứng tương ứng, không suy từ mock thành BE thật. Lỗi header mobile đã biết ngoài task không tự sửa layout chung; phân biệt overflow mới của modal với header cũ.

## Execution evidence — 28/10/2026

- test:edu: 59 pass, 0 fail/skip; helper 15 ca, transport/API/mock mới và cũ 16 ca; reviewer chạy độc lập ba file 31/31.
- Browser Chromium mock: chọn nguồn khác năm và nhóm tuổi; chọn một/all/clear/reset; không ghi trước Lưu; source nguyên, đích themes/breaks nguyên, nội dung mới ticks rỗng; Save–reopen vẫn wizard. Edge QA 7/7 cho revision/missing selected ID cùng revision/403/404/network/late close/late filter.
- FE: VERIFY PASS full | npm run verify | log verify.log. BE: VERIFY PASS full | tests 410 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 123s. Đây chưa phải bằng chứng chạy real-BE E2E feature003.
- Suite bền vững thực tế là tests/e2e/system/year-plan-import.spec.mjs (13 ca), tách khỏi chuỗi serial cũ để fixture/cleanup độc lập. Source test đã syntax/format check, chưa chạy do thiếu E2E_DEV_PASSWORD. Chạy riêng sau khi cấu hình biến môi trường:

```powershell
npx playwright test --project=system --no-deps tests/e2e/system/year-plan-import.spec.mjs
```

- Sau khi biến được cung cấp qua môi trường hoặc ignored .env.e2e.local, thực hiện T021/T019, sửa lỗi có bằng chứng nếu có rồi converge lại. Chưa đánh dấu plan done hoặc converged.

### E2E attempt after local credential file — 28/10/2026

- File ignored .env.e2e.local đã có và E2E_DEV_PASSWORD đọc được. Chạy suite real-BE 13 ca: beforeAll không lấy được token Phó HT, 1 failed setup và 12 ca không chạy; chưa kiểm chứng tính năng.
- Keycloak trả invalid_grant / Invalid user credentials cho tài khoản dev Phó HT. Đọc file bằng dotenv chuẩn Node cũng cho cùng lỗi, nên không phải do shell parse.
- Kiểm read-only actual realm: username viceprincipal, email viceprincipal@gmail.com, enabled=true, requiredActions=[]; không có bước thiết lập tài khoản còn thiếu. Không ghi secret/token vào tài liệu hoặc log.
- T019/T021 tiếp tục mở. User cần cập nhật biến bằng mật khẩu khớp tài khoản Phó HT đang chạy; sau đó thử lại, không coi lần chạy setup lỗi là E2E đạt.

### Retry với credential và DB đúng — 28/10/2026

- Keycloak đã xác thực được sau khi user cập nhật mật khẩu. Fixture SQL phải cùng database với Backend: runtime hiện dùng carenest_merge, helper mặc định carenest. Cấu hình E2E_PG_DB trong process chạy test, không thay global default hoặc source Java.
- Hai assertion E2E được sửa theo hành vi đã có: canEdit=false làm editor không render và hiện thông báo từ chối (không phải textarea disabled); Ctrl+S trên bản pristine sau reopen không ghi hoặc sinh timestamp mới. Vẫn kiểm không có nút import khi bị từ chối, dữ liệu không đổi và không phát sinh lượt ghi dư.
- 12 ca đầu đã đạt, ca lưu cuối đạt khi chạy riêng (1/1). Đang chạy lại đủ 13 ca sau chỉnh sửa assertion để lấy kết quả cuối thống nhất.

```powershell
$env:E2E_PG_DB='carenest_merge'
node --env-file=.env.e2e.local node_modules/@playwright/test/cli.js test --project=system --no-deps tests/e2e/system/year-plan-import.spec.mjs
```

E2E_PG_DB phải theo DB_URL của Backend thực tế; không suy từ database mặc định của helper hoặc sự trùng ID dữ liệu seed. File env vẫn được Git bỏ qua và không ghi mật khẩu vào câu lệnh/tài liệu.

### Final real-BE E2E result — 28/10/2026

- Lệnh Node --env-file cùng E2E_PG_DB=carenest_merge ở trên: **13 passed (1.1m)**; 0 failed, 0 skipped.
- Gồm data fixture thật/API readback và UI thật; các ca lỗi/race/mất quyền có tiêm phản hồi có kiểm soát để kiểm hành vi. Không gọi các phản hồi mô phỏng lỗi là server thật phát sinh lỗi đó.
- Đã chứng minh chọn một/domain/all/reset/cancel/keyboard, quyền và lease, fresh revision/status/selected ID, 403/404/network và retry, late close/filter, 200 goals dưới 1s và 390/768/1440px, double-confirm, không ghi trước Save, save lỗi/thử lại, Save–reopen và giữ hierarchy/themes/breaks/ticks.
- Các ghi chú thiếu credential/failed setup/lần chạy đang tiến hành ở trên là lịch sử; kết quả cuối này thay thế trạng thái chờ của T019/T021. Cổng unit/lint/build/AI layer được chạy lại trước bàn giao cuối.

### Final fixture isolation result — 28/10/2026

- Thêm guard E2E_PG_DB bắt buộc trước SQL fixture. Cleanup dùng ID+marker của lần chạy cho kế hoạch, ID+name cho năm đã tạo; không xóa theo ID đơn thuần trong database có thể cấu hình sai.
- Dọn đúng bản thử còn sót có marker riêng từ lần sai DB. Lần chạy sau hardening: **13 passed (1.0m), zero failed/skipped**; SQL read-only kiểm còn **0** bản [E2E-IMPORT] sau cleanup.
- T022 đã hoàn tất; tổng 22/22 task, lịch sử vẫn giữ. Các trạng thái 19/21/chờ credential ở trên là lịch sử, không phải trạng thái cuối.

### Final handoff gates — 28/10/2026

- FE gate sau lần sửa test cuối: VERIFY PASS full | npm run verify | log verify.log (gồm 59 test education-plan, lint/format/build và kiểm tra dùng chung).
- BE gate cùng lượt: VERIFY PASS full | tests 410 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 153s. Java/OpenAPI không đổi.
- Converge cuối: 22/22 tasks, 15 FR/6 SC/21 AC/7 edge cases/10 design decisions; gaps=0. Task bytes không đổi trong lượt converge cuối; không có header/task rỗng. Plan được lưu ở docs/plans/completed.
- Real-BE E2E target feature003 đạt 13/13; không tuyên bố đã chạy toàn bộ E2E của các feature khác. Dữ liệu giả riêng được dọn, 0 fixture import còn sót; không commit hoặc in secret.
