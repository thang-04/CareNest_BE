---
title: Chọn một phụ huynh nhận tài khoản khi tiếp nhận trẻ
status: done
owner: thang-04
jira: G94-180 / BE-FEAT-43
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [child, identity-access]
rules: [AUTH-04, AUTH-18, AUTH-19, PCK-02]
risk: high
created: 2026-11-05
updated: 2026-11-05
---
# Chọn một phụ huynh nhận tài khoản

## Context
User yêu cầu thiết kế ngày 05/11/2026: một phụ huynh nhận tài khoản, màn tài khoản chỉ hiện người được chọn. Đã làm rõ: form kích hoạt ngay khi lưu, Excel kích hoạt sau. User đã duyệt triển khai thiết kế; quyền sửa FE/BE/schema/contract ghi trong Progress log.
Hiện trạng: `ChildEnrollmentServiceImpl.java:200` lấy toàn bộ guardian làm ứng viên tài khoản; `ChildGuardian.java:31` có position, chưa có recipient. FE `parentAccountMock.js:154` kích hoạt mọi pending guardian; `enrollmentImport.js:113` chỉ tạo một guardian mỗi dòng. FE có adapter import/pending/activate nhưng BE chưa có các endpoint tương ứng.
Các plan tiếp nhận 28/10 và guardian wiring 01/11 ghi luồng cũ; plan này là delta chọn một người, không viết lại lịch sử/ownership của các plan đó.

## Quyết định đã chốt
1. Một người nhận tài khoản được chọn trong số phụ huynh của trẻ; màn tài khoản chỉ hiện người này — user 05/11/2026.
2. Form tiếp nhận kích hoạt người được chọn ngay khi lưu; Excel lưu hồ sơ rồi kích hoạt sau — câu trả lời user cùng ngày.
3. Không biến việc được chọn làm tài khoản thành điều kiện được lưu ảnh hoặc đối chiếu đón trẻ.

## Làm rõ nghiệp vụ
Actor: HT/PHT trong quyền hiện có; outcome: lưu đủ phụ huynh nhưng chỉ tạo/liên kết tài khoản cho người được chỉ định.
Rule nguồn: G94-180/AUTH-20 (form/Excel và ngoại lệ quyền), AUTH-04/18/19 và PCK-02; yêu cầu mới thay việc kích hoạt toàn bộ danh sách. P-28 trong docs cũ chưa chốt; domain-spec ghi rõ phạm vi mới và phần dữ liệu cũ còn cần duyệt thiết kế.
Flow [B]/[S], 14 mục domain và AC-1..8 ở [Domain spec](../../../specs/013-selected-parent-account/domain-spec.md). [Draft contract/model](../../../specs/013-selected-parent-account/contracts/selected-parent-account.md) ghi rõ endpoint chưa có và consumer cụ thể.
Ảnh hưởng: child/account; FE form/import/activation; DB metadata lựa chọn; không đổi APP, chính sách mật khẩu/SMS hay tự thu hồi quyền cũ.

### Câu hỏi mở
- [x] User duyệt thiết kế và cho triển khai: tự chọn khi một guardian; nhiều người chọn rõ; Excel chọn ở preview/cột CCCD tùy chọn; hồ sơ cũ không tự thu hồi liên kết.

| Câu hỏi | Trả lời | Ai | Ngày | Nguồn |
| --- | --- | --- | --- | --- |
| Excel kích hoạt lúc nào? | Sau nhập, ở màn Tài khoản phụ huynh | user | 2026-11-05 | Domain mục 8 |
| Form kích hoạt lúc nào? | Ngay khi lưu, chỉ người được chọn | user | 2026-11-05 | Domain mục 8 |

## Acceptance criteria
AC-1..8 của Domain spec: chọn đúng một; form chỉ tạo/link người chọn; Excel nhiều người chọn theo dòng và kích hoạt sau; projection/count đúng recipient; BE ngăn bypass/stale selection; giữ ảnh/người đón; chuyển dữ liệu không thu hồi quyền ngầm.

## Key decisions
| Vấn đề | Đề xuất | Phương án khác | Lý do |
| --- | --- | --- | --- |
| Lưu lựa chọn | Flag trên ChildGuardian + partial unique theo child | Chỉ giữ index ở FE | Gắn đúng người, BE kiểm và không lẫn khi đổi thứ tự |
| Chọn trong Excel | Dropdown preview; CCCD tùy chọn để chọn sẵn | Bắt buộc sửa cột đánh dấu trong mọi file | File cũ một người vẫn dùng, nhiều người chọn rõ |
| Dữ liệu cũ | Backfill khi không mơ hồ, còn lại yêu cầu chọn | Tự chọn người đầu/xóa link dư | Không tự đổi quyền truy cập tài khoản |

## Phases
### Phase 1 — Hoàn thiện đặc tả và triển khai một lượt BE/FE sau duyệt
Tách tasks theo dependency vì >8 file, giữ một điểm review cuối cho luồng dùng được hoàn chỉnh. Trước source cần hoàn thiện spec/clarify/plan/data-model/tasks/analyze theo feature-spec-loop, tái sử dụng constitution hiện có.
- BE: migration mới, recipient validation/persistence/read model; giới hạn enrollment account side effects; bổ sung import preview/import và pending/activation thật; bảo vệ các đường link cũ.
- FE: radio form/xem lại; nhóm cột nhiều guardian/preview dropdown; selected-only account list/panel/counters; mock parity, API và ảnh giữ đúng người.
- Tests: AC-1..8; JSON/multipart cũ một người, raw API tạo người không chọn, idempotent activation, stale modal, shared account, dữ liệu cũ và file 1/2/3 guardian.
- Exit: BE/FE `node scripts/verify.mjs` → VERIFY PASS full, skip 0; `node scripts/check-ai-layer.mjs` exit 0; browser form/Excel/activation 1440/375, ghi rõ môi trường thật/mock.
- **Dừng chờ user review sau phase.**

## Test matrix
| AC | Kiểm chứng | Trạng thái |
| --- | --- | --- |
| 1/2 | Validator, form state, enrollment account orchestration, thiếu quyền | Đã triển khai; kiểm tra cuối tại quickstart013 |
| 3/4 | File headers/nhiều người, preview selection, import không side effect, activation sau | Đã triển khai; kiểm tra cuối tại quickstart013 |
| 5/6 | Shared account, raw endpoint bypass, ngoài scope, selection đổi khi modal mở | Đã triển khai; kiểm tra cuối tại quickstart013 |
| 7/8 | Ảnh/người đón, rollback, reorder, migration dữ liệu cũ không mất link | Đã triển khai; kiểm tra cuối tại quickstart013 |

## Rủi ro & rollback
Flag recipient khác user account/link: phải bảo vệ cả API và projection, không chỉ ẩn UI. Không sửa migration đã commit; quay lui source có chọn lọc, không drop link/account/ảnh cũ. Thiếu thư viện/parser hoặc endpoint chưa có phải hiện thành task cụ thể trước code, không tuyên bố đã nối BE.

## Validation log
- [x] Đối chiếu source và phân biệt endpoint hiện có/chưa có.
- [x] Thời điểm form/Excel được user trả lời rõ, không hỏi lại.
- [x] AC/consumer/model/rủi ro và kiểm chứng cụ thể; >8 file đã tách nhóm task.
- [x] Chỉ là thiết kế, chưa sửa source/DB/API hoặc tự coi là đã được duyệt triển khai.

## Progress log
### 2026-11-05 — Thiết kế chờ review
- Đọc form, parser, quản lý tài khoản và orchestration BE. Ghi Domain spec, draft contract/model và thiết kế UI FE. Chưa viết source ứng dụng, migration hay thay OpenAPI.
- Quyết định thời điểm kích hoạt ghi theo user; các chi tiết UX/migration chờ duyệt trong plan.
- Kiểm chứng tài liệu: chạy check-ai-layer và diff whitespace sau bản cuối; kết quả ở bàn giao. Không dùng kiểm tài liệu làm bằng chứng test tính năng đã chạy.

### 2026-11-05 — User yêu cầu triển khai
- User: “hãy thực hiện code phần vừa thiết kế về chọn 1 phụ huynh làm tài khoản”. Đây là phê duyệt triển khai thiết kế/contract/migration FE và BE, một phase rồi review; chưa cho commit/push.

### Khác plan — phiên bản migration
- Dùng V70 cho thay đổi mới: dev đã có V69 của tính năng khác. Giữ nguyên các migration đã commit trên nhánh này.

### 2026-11-05 — Triển khai, review và kiểm chứng
- Hoàn thiện spec/tasks, V70 flag/index/backfill không mất historical links, các API thật import/recipient/pending/activation và raw-route enforcement.
- Form/legacy/activation cùng CAS CCCD trong transaction; recipient lock chống stale selection; projection dùng user_id, không suy đoán danh tính; scope gồm trẻ chờ xếp lớp; lựa chọn có lịch sử.
- Hai vòng review độc lập và kiểm lại blocker cuối đã kết luận không còn blocker trực tiếp. Targeted PostgreSQL/unit đã đạt; full gate BE chạy sau clean và source cuối. FE full808tests và browser1440/375 đạt.
- Đã ghi incident backfill CHECK NOT VALID và đường xử lý; giữ stash dự phòng, không commit/push.

### 2026-11-05 — Hoàn tất phase triển khai, sẵn sàng user review
- BE: VERIFY PASS full | tests1087 fail0 err0 skip0 | archunit/snapshot/spotless đạt, sau clean và source/test cuối; log target/verify.log.
- FE: VERIFY PASS full | tests808 fail0 skip0; browser mock1440/375 đạt, 0 lỗi JS, ảnh12/12 URL thu hồi mỗi viewport. Không tuyên bố live FE-to-BE/Keycloak/SMS/S3.
- Review đọc source độc lập đã kiểm lại CAS ở form/legacy/activation, khóa recipient và projection theo user_id; không còn blocker. Bổ sung test scope trẻ đã nghỉ để không mở rộng quyền PHT.
- Check-ai-layer cuối 0 lỗi; không commit/push/deploy, giữ stash dự phòng.
