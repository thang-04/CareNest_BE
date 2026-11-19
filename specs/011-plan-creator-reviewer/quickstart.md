# Validation guide — feature011

Đây là hướng dẫn kiểm chứng và ma trận kết quả thực tế sau implement. Các lệnh/case dự kiến trong từng mục không được xem là PASS nếu không có bằng chứng ghi trong “Kết quả kiểm chứng thực tế”.

## Prerequisites

- User duyệt plan làn L, contract và migration; xác nhận trạng thái rule/ADR đúng nguồn. Sau đó generate tasks, analyze không còn lỗi chặn.
- Java 21, Node, Docker/PostgreSQL Testcontainers theo README; không reset DB đang dùng. Dùng synthetic fixture, không dữ liệu trẻ/thông tin đăng nhập thật trong repo hoặc prompt.
- Web được phép sửa riêng và dùng adapter thật/mock cùng contract; không deploy BE mới với Web cũ. APP review nếu có consumer thực tế.
- Đối chiếu [data model](data-model.md), [API](contracts/workflow-api.md), [consumer](contracts/client-impact.md); số migration cấp lúc implement.

## Commands

Chạy từ repo BE, chọn suite tương ứng phase; lệnh chưa có suite không được báo pass:

```powershell
./mvnw.cmd '-Dtest=EduWorkflowPolicyTest,EduWorkflowServiceIntegrationTest' test
./mvnw.cmd '-Dtest=EduConfigServiceIntegrationTest,EduClassDocServiceIntegrationTest,EduYearPlanServiceIntegrationTest,EduWorkflowCompatibilityIntegrationTest' test
node scripts/verify.mjs
node scripts/check-ai-layer.mjs
git diff -- docs/api/openapi.yaml
```

Tên suite year service phải đối chiếu test file thực tế khi tạo tasks, không sửa verify harness để chấp nhận suite thiếu. Phase L kết thúc cần full fail=0/error=0/skip=0, ArchUnit/Spotless/snapshot đạt và diff snapshot review có chủ đích; Docker skip là chưa kiểm chứng, không PASS. Unit/API tests phải RED trước implementation phù hợp. Không cần test mirror serializer/CRUD thuần nếu không chứng minh rule.

## Test matrix và expected outcomes

| ID / rule | Fixture/action | Expected |
| --- | --- | --- |
| AC-01 / EDU-54 | HT hiệu lực lưu policy; PHT/direct API/HT hết phân công thử lưu | Chỉ HT hiệu lực được lưu; các request bị từ chối không đổi config/document |
| AC-02 / EDU-54 | Role lạ/rỗng/trùng/giao soạn-duyệt/unknown week key/stale policy revision | 400 hoặc 9012 đúng contract; atomic no side effect |
| AC-03 / EDU-54, AUTH-23 | TL vừa TEACHER có quyền review branch; VP/HT có thêm role author | Reviewer không tạo/sửa/gửi cùng loại; TL có reviewer-role vẫn không author cùng loại tại nhóm khác; review không vượt scope |
| AC-04 / EDU-54, EDU-17 | Year chuỗi HT→VP hoặc TL→HT; class theme+weeks/day; chỉ DRAFT/REVIEW/APPROVED | Đúng lượt; REVIEW không content write/author lease, /issue advance trung gian vẫn REVIEW, cuối thành APPROVED; tuần không gửi riêng; dossier thay đồng bộ |
| AC-05 / EDU-54 | Một coauthor week thử duyệt theme; reviewer lấy lease/preview upload ghi thử | Chặn theo union authors/capabilities; preview read-only không làm contributor |
| AC-06 / EDU-54 | Year/TL rồi đổi policy; role đã duyệt giữ/bị bỏ/thêm role đầu | Không duyệt lại cùng round; role cũ chỉ history nếu bị bỏ; mọi role mới đã duyệt hoặc [TL thiếu người được skip, VP đã duyệt] thì completed mode POLICY_CHANGE |
| AC-07 / EDU-54 | Không candidate ở bước giữa/cuối/toàn bộ; account inactive, assignment hết hạn | Bỏ bước giữa nếu còn bước sau; cuối chưa duyệt thiếu người phải chờ, không fake approval |
| AC-08 / EDU-54 | Reject → sửa → submit hoặc version mới | Round mới, duyệt từ đầu; old history còn, active approvals cũ không được tính |
| AC-09 / EDU-54 | Legacy fixtures owner/createdBy và updatedBy của reviewer | Chỉ creator baseline, không suy reviewer thành coauthor; LEGACY_BASELINE/trackingSince rõ; new docs COMPLETE |
| AC-10 / EDU-54 | Policy update song song approve/save/heartbeat; hai approvals/retries | Đúng lock order; expected class member/root version chặn cả hai request stale đến tuần tự sau policy save/weekly edit; GET REPEATABLE_READ không trộn snapshot, policy revoke cả DRAFT-held-leases mất quyền và job nhả lease không restore status cũ; retry không duplicate |
| AC-11 / EDU-51/52/53 | Policy rút gọn sẽ complete year thiếu lịch/mục tiêu/hierarchy invalid | Rollback toàn policy + pending states, lỗi publish đúng, không APPROVED dữ liệu thiếu |
| AC-12 / EDU-21/30/33 | Completed year/class trước đổi policy, HT final và observation print | review.printSnapshot đúng schema giữ role/label/names dù config/tên đổi; producer edu issued_by không nhận HT/TL, published_by là actual publisher; không sửa module khác |
| AC-13 / EDU-54 | PHT PUT config cũ cố đổi signers; echo đúng để sửa field khác | Workflow không đổi qua legacy endpoint; phần khác theo quyền hiện hành, stale signers báo tải lại |
| AC-14 / EDU-22/27 | Manual save, idempotency, adjustment đã duyệt | Giữ manual save; adjustment đúng author policy, không mở lại nội dung đã duyệt |

## E2E runbook

1. Tạo fixture account tổng hợp với HT/VP/GV/TL và active assignments hai campus; không chia sẻ token lên log. Ghi expected scopes trước khi test.
2. Nâng cấp DB test chứa pending/approved/versioned legacy docs; assert counts/content/approvals/issuer trước-sau, policy seed đúng user, completed giữ nguyên; migration lỗi phải rollback được.
3. Login qua auth flow hiện có, HT cấu hình; PHT/teacher gọi thẳng workflow PUT bị 403; old full-config PUT không sửa ngầm workflow.
4. Tạo/gửi/duyệt year và dossier bằng người đúng vai; reviewer thử soạn và author thử duyệt đều bị chặn. Nhiều role trong cùng tài khoản cũng không vượt được.
5. Mở hai tab, lưu policy ở HT trong khi người khác duyệt/đang giữ lease; kiểm request revision conflict, refresh capability, không có approve theo policy cũ sau khi policy PUT thành công.
6. Đổi chuỗi khi có partial approvals, verify completion khi mọi role mới đã duyệt; thêm role mới thì chờ role đó, bỏ cuối thiếu người không đồng nghĩa đã được duyệt.
7. Reject/sửa/gửi lại và version mới: bắt đầu lại; policy-only change không reset. Export year/class và observation, đối chiếu role/name thật + completion mode.
8. Client mock và API thật cùng hành vi; kiểm desktop/mobile viewport, keyboard reorder, no duplicate saves; đóng/tải lại giữ dữ liệu. API-only E2E không được ghi thành browser E2E.
9. Cleanup chỉ IDs fixture do run tạo, không reset/clean DB chung. Ghi lệnh, output test, snapshot diff, counts, fail/skip và môi trường vào Progress log.

## Performance/concurrency evidence

Synthetic workload có nhiều year pending/dossier với nhiều tuần; đo query count và transaction duration khi save policy. Mỗi role/assignment/account tải batch, không một query mỗi ô hoặc mỗi user; ghi dataset size thực và số query thay vì hứa p95 chưa đo. Test parallel writers khác documents với shared policy lock vẫn tiến triển, exclusive policy save không thấy trạng thái nửa commit. Nếu fail thì sửa batching/lock ordering, không bypass synchronization.

## Evidence status at design time (superseded by results below)

At initial planning time, only the artifacts existed; those statements were superseded by the implementation and verification evidence in the section below. This historical note does not describe current status.

Delta sau analyze: kiểm migration mọi legacy PENDING/EDITING/REJECTED về đúng ba trạng thái; return về DRAFT giữ lý do; draft lease mất quyền bị thu hồi và stale tab không ghi. Không sửa observation source; account chỉ có đúng helper đọc ACTIVE-ID và test tại T009 đã được user cho phép, không mở nghiệp vụ module khác. Coverage FR hiện18 và SC9, cần analyze lại, không tái sử dụng kết luận23yêu cầu cũ.

## Kết quả kiểm chứng thực tế — 06/11/2026

- Backend focused review cho danh sách/class/workflow/access: 37/37 test đạt; sau tối ưu T077, GET 24 hồ sơ lớp dùng 23 câu SQL (log `target/feature011-list-final.log`). Mốc trước tối ưu là 258 câu (`target/feature011-list-batch-red.log`). Regression giữ hồ sơ DRAFT không bị đánh dấu superseded khi chưa có version APPROVED mới hơn.
- FE `npm run verify`: 207/207 test kế hoạch giáo dục; lint, format, design, API contract và production build đạt (log tại `CareNest_FE/node_modules/feature011-fe-final-verify.log`).
- Browser WF-01/02/03 với Keycloak, PostgreSQL và BE build từ source cuối: 3/3 đạt (log `CareNest_FE/node_modules/feature011-browser-final.log`). WF-01: HT lưu policy và đọc lại sau reload; WF-02: PHT bị chặn qua API/form; WF-03: reviewer duyệt cuối, hồ sơ ban hành, không lấy author lease.
- Backend `node scripts/verify.mjs`: `VERIFY PASS full | tests 1129 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 800s` (log `target/verify.log`).
- `node scripts/check-ai-layer.mjs`: 0 lỗi, 12 cảnh báo đường dẫn/plan cũ ngoài phạm vi feature.
- APP: user xác nhận chưa tích hợp kế hoạch giáo dục; không có phạm vi consumer để test hoặc sửa trong task này.
- Không có line-coverage percentage được suy ra; query count đo trên fixture tổng hợp 24 hồ sơ, không phải SLA p95.


## Historical P1/P2 verification design (before implementation)
- P1: chỉ migration expand đã tạo; test Flyway target expand giữ legacy+REVIEW và legacy caller fixtures, thêm REVIEW trong exhaustive switches, full verify/snapshot expand T014 phải đạt; chưa tạo contract migration ở mốc này, không rollout.
- P2: viết/đổi caller/fixtures year/class/lease/round, sau đó T047 tạo contract migration + xóa legacy enum trong một cutover. Kiểm DB nâng cấp thẳng từ legacy qua cả2migration và bước expand→contract; SQL normalize giữ approvals/history/completed, CHECK cuối chỉ DRAFT/REVIEW/APPROVED. Full verify/snapshot final T051 phải đạt; không lấy kết quả P1 thay final.
- Helper test: findActiveUserIds input rỗng không query; ACTIVE trả IDs, inactive/missing bị loại, duplicate IDs dedupe; repository.findAllById một batch, không PII/network. Integration giáo dục vẫn kiểm assignment/scope/author exclusion độc lập.
- Print test/contract chỉ đọc review.printSnapshot; completionPolicy không là nguồn ô ký/name/role. Các hướng dẫn source/verify này là expected, chưa có implementation thực tế.
