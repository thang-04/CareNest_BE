# Quickstart validation

## Delta02/11 — xóa nhánh và ellipsis (đã implement/converge, chốt cổng cuối)
- Prerequisite: user duyệt delta plan L; FE npm dependencies đã có. Browser dùng fixture tổng hợp, không lấy dữ liệu thật hoặc secret. Test save/reopen server chỉ dùng runtime/credential kiểm thử hợp lệ khi có sẵn.
- RED/GREEN: `node --import ./scripts/register-auth-test-loader.mjs --test tests/education-plan/goal-subdomain-delete.test.mjs` từ FE; 11ca đã được tạo và chạy RED/GREEN như evidence bên dưới.
- AC-D1..D4:1nhánh có2nhóm/3goal/4content/5tick; xác nhận đủ số liệu; Hủy deep-equal form; Xóa chỉ mất ID nhánh. Lặp nhánh đầu/giữa/cuối/duy nhất/rỗng/ID không tồn tại và hai nhánh cùng tên. Giữ goal trực tiếp/nhóm rỗng trong semantics nhánh.
- AC-D5/D6: normalize/numberGoals/themeGoalIds không nhận lại con đã xóa, chủ đề và nhánh khác giữ nguyên; read-only không có nút xóa; trước Lưu không có write request. Khi có môi trường server: Lưu/mở lại bản thử, kiểm ID còn/mất và bản nguồn không đổi.
- AC-U1/U2: browser360/390/768/1440, chọn lĩnh vực Phát triển tình cảm và kỹ năng xã hội; ghi white-space/overflow/text-overflow/chiều cao một dòng, title đầy đủ, option/value/filter/keyboard và ảnh; kiểm lại ô target có câu dài.
- Cổng sau source cuối: FE `node scripts/verify.mjs`; BE `node scripts/verify.mjs --quick` cho tài liệu; hai repo `node scripts/check-ai-layer.mjs`. Check docs không thay test xóa và kiểm UI.
- Converge sau implement: trace7FR/4SC mới vàUS4/US5; append task cho gap, không viết lại T001..T011; vòng cuối hash tasks trước/sau phải trùng. Chưa gọi sạch nếu T019 persistence hoặc task delta khác còn mở.
- Tình trạng: user đã duyệt plan L; source/test delta đã triển khai. Evidence29/10 bên dưới chỉ cho phần DB cũ.

### Evidence và Attempts delta02/11
1. Unit RED11ca: chưa có helper chọn nhánh. Browser baseline dùng GoalTables gốc từ Git HEAD (không sửa file ứng dụng để quay lui): xóa sub-a vẫn còn direct/group-a/goal-a/group-a2/goal-a2 bị normalize sang sub-b; assertion đúng nhánh thất bại.
2. GREEN:11unit mới và44targeted đạt; full FE sau source đạt386test fail0/skip0, lint/format/design/build đạt. Helper không mutate, không đổi normalizer. Counts UI2group/3goal/4content/5tick; Hủy (nút Quay lại của component chung) giữ form, read-only không nút xóa, chỉ bỏ đúng nhánh/ticks.
3. Browser source/CSS thật với fixture tổng hợp: PASS360/390/768/1440,0lỗiJS; select nowrap/ellipsis/title/value/target/filter/keyboard đúng. Artifacts FE node_modules/.cache/subdomain-delete-review.mjs và subdomain-delete-shots. Kỳ vọng nút Hủy ban đầu sai nhãn thực tế Quay lại, đã sửa test, không sửa component chung.
4. E2E dự kiến nối vào suite nhập file cũ bị chặn trước fixture vì thiếu E2E_PG_DB. Không đoán DB hoặc bỏ guard. Chuyển sang file độc lập tests/e2e/system/year-plan-subdomain-delete.spec.mjs: chỉ API đã xác thực, tìm slot trống, tạo plan với marker ngẫu nhiên, cleanup kiểm prefix/ID rồi lấy lease và DELETE đúng plan qua API. Suite cũ được giữ byte như HEAD, không SQL hoặc sửa cấu hình môi trường/secret.
5. Lượt API-only đi qua mọi assertion lưu/mở lại/select nhưng lỗi test ở Đóng vì trùng close-icon/footer; sửa selector vào footer. Lượt cuối: node --env-file=.env.e2e.local node_modules/@playwright/test/cli.js test tests/e2e/system/year-plan-subdomain-delete.spec.mjs --project=system --no-deps --reporter=line →1passed(15.7s). Xác nhận không PUT trước Lưu, sau Lưu chỉ3hàng nhánh giữ, cùng ID/content/tick, goalNo=1, themes/breaks giữ, reopen không còn nhánh xóa và select4viewport đúng. Kiểm API sau cleanup: remainingOwnedTestPlans=0. Log FE node_modules/.cache/subdomain-real-e2e.log; ảnh domain-360/390/768/1440 trong test-results của test.
6. Review độc lập code+test mới: không blocker; reviewer chạy11unit đạt. Review API-only xác nhận cleanup chỉ marker-owned ID, giữ guard suite SQL cũ; review tĩnh không thay runtime15.7s.
7. Lệnh PowerShell cập nhật approval trước code từng làm hỏng chuỗi vài docs; đã khôi phục lịch sử từ HEAD+delta, kiểm prefix4artifact/14mục/22task duy nhất trước code; xem incident ENV-261102-powershell-replacement-pairs. Đây là lỗi thao tác tài liệu, không phải code ứng dụng.

### Cổng và convergence cuối
Cổng cuối chạy sau hồ sơ tại VERIFICATION hai repo. Converge đánh giá riêng FR-009..015/SC-005..008/US4..5; task lịch sử DB không thay evidence mới. Hash và kết quả vòng cuối ghi bên dưới; không nhận sạch từ evidence lịch sử DB.

- Converge1 read-only độc lập:7FR/4SC/US4..5/8AC và constitution không có missing/partial/contradicts/unrequested. Không append task giả hoặc viết lại lịch sử. Tasks trước/sau assessment đều AEBC454612DB482F9F49AA49C63461D0357C19DACAC6C3ECFBDAE91B309AA7A3; T022 lúc đó là metadata chờ đóng.
- Ngoài convergence: đóng metadataT022 sau assessment, sửa câu trạng thái test dự kiến trong quickstart và ghi evidence hoàn tất; không đổi intent hoặc code.
- Kiểm convergence cuối:22/22task đã xong (11lịch sử+11delta), prefix T001..T011 như HEAD. Tasks byte-for-byte trước/sau lượt kiểm cuối:11D489CE9602E79869F2D93064E41DABE4A416427EB70175BF0D3CFBECC25E7C. Không gap triển khai còn lại trong delta; không thay đổi tasks trong lượt sạch.
- Giới hạn: không re-run migration cũ/433testDB29/10 hoặc toàn suite E2E liên quan khác; delta đã có11unit mới/fullFE386/localbrowser/realBE1case và backendquick14. Không commit/push/deploy. Cổng cuối sau hồ sơ xem output bàn giao.

## Lịch sử kiểm chứng DB29/10
Prerequisite: Java 21, Maven wrapper, Docker đang chạy; chỉ dùng dữ liệu giả.
1. Chạy regression nâng cấp V34→V35: `./mvnw.cmd test -Dtest=EduGoalHierarchyMigrationTest`.
2. Chạy schema/service/class-doc/resource liên quan; xem tên test trong tasks.
3. Chạy `node scripts/verify.mjs`; yêu cầu VERIFY PASS full, skip 0, ArchUnit/snapshot/Spotless ok.
4. Chạy `node scripts/check-ai-layer.mjs`; xem E6 theo output thực tế.
5. Kiểm `git diff --exit-code docs/api/openapi.yaml` và đối chiếu sơ đồ/tài liệu.
6. Converge dựa trên spec/plan/tasks và source; append task nếu còn thiếu, chạy implement và verify sau sửa.
Không chạy ứng dụng để tự migrate DB dev/production đang dùng.

## Evidence vòng 1
- Analyze: 8/8 FR có task; 4/4 SC có acceptance/test; không ambiguity/critical; constitution là template nên không dùng placeholder làm luật.
- RED: hierarchy-red.log: bảng mới chưa tồn tại và ambiguous order chưa bị từ chối. Lần setup đầu sai query separator URL container đã được sửa trước khi lấy RED.
- Implement regression: hierarchy-green2.log: 46 tests, 0 failure/error/skip; V34→V35 trên PostgreSQL thật. Lỗi deferred trigger khi CREATE INDEX được ghi vào BUG-261029-hierarchy-deferred-ddl, đã có GREEN.
- Review độc lập vòng 2: PASS sau fix migration; kiểm cha nullable, ownership, cascade, copy và API.
- Converge 1: FR-002/SC-001 còn thiếu fixture nhiều domain/phiên bản và sequence đã vượt max ID; append-only T011, không đổi ý định spec/plan. Đã bổ sung test, chờ gate cuối xác nhận.
- Drawio: thêm tab đầu `V32 - Ba bảng cha con` (migration nay là V35), giữ tab đang được user chỉnh sửa; XML/ID/edge/nhãn văn bản thuần đã kiểm.

## Converge vòng 2 — 29/10/2026
Đã đối chiếu lại 8 FR, 4 SC, 3 user stories và các quyết định ownership/ID/migration/API/copy trong plan với source và test. Không còn gap implementation; T011 bổ sung fixture nhiều domain/phiên bản và sequence cũ đã được viết. Converge không thay đổi tasks.md (SHA256 trước/sau: 411067E55257C896ED9B25895FD99158EF22E4FB8054AD1D27A2FE09EED6B1E6). Cổng cuối sau convergence đang chạy, kết quả được ghi bên dưới khi có output.

## Kết quả cuối — 29/10/2026
- `node scripts/verify.mjs` → `VERIFY PASS full | tests 433 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 247s | log target/verify.log`.
- `EduGoalHierarchyMigrationTest`: 5 tests, failure/error/skip 0; có nâng cấp V34 thật với nhiều domain/phiên bản và sequence cao hơn max ID.
- `git diff --exit-code -- docs/api/openapi.yaml` exit 0.
- `node scripts/check-ai-layer.mjs`: 0 lỗi, 0 cảnh báo; E6 bỏ qua theo công cụ.
- 11/11 tasks done; review độc lập PASS; converge không còn gap.
- Giới hạn: chưa chạy V35 lên DB dev đang dùng hoặc production; chưa browser FE E2E. Plan gốc vẫn in-progress cho phạm vi cũ, riêng DB-1 đã hoàn tất.

### Converge sau visual review T023 — kết quả cuối
- Sau lượt kiểm trước, ảnh xác nhận lộ số thứ tự sai do closure counter; append-only T023, không sửa/xóa task cũ. Local RED nhận2khi chọn1; capture const theo block, bổ sung assertion ordinal. Local GREEN; real-BE E2E cuối1/1passed20.3s, API cleanup0. Review lại không gap.
- Sau implementation T023, đóng task ngoài converge; vòng sạch cuối23/23task, FR009..015/SC005..008 không còn missing/partial/contradicts. Tasks trước/sau lượt kiểm đều C597326B228F71D8EFF03365D62F16786FE8CFB423D53960BF92DEE5C5C7478D. Không thay tasks trong lượt sạch. Các hash22task phía trên là lịch sử trước khi phát hiện T023, không phải hash cuối.
- Cổng verify cuối chạy lại sau sourceT023 và hồ sơ cuối; output bàn giao là bằng chứng sau cùng.
