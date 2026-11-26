# Spec — Thực đơn tuần và ảnh suất ăn cho phụ huynh

Domain: [domain-spec.md](domain-spec.md). Jira G94-497. Ngày 26/11/2026.

## User stories
- **US1 (P1)** xem thực đơn từng ngày của con. **US2 (P1)** xem ảnh bữa. **US3 (P2)** đánh dấu dị ứng.

## Functional requirements
| ID | Yêu cầu | US | AC |
| --- | --- | --- | --- |
| FR-1 | Chỉ phụ huynh liên kết với con đang học được đọc | 1–3 | AC-1 |
| FR-2 | Trả thứ Hai–Sáu của tuần chọn, mặc định tuần hiện tại | 1 | AC-2 |
| FR-3 | Món chỉ khi tuần đã công bố, đúng khối và bữa của lớp tại ngày | 1 | AC-3, AC-4 |
| FR-4 | Ngày nghỉ, ngày chưa học: đánh dấu, trống | 1 | AC-5 |
| FR-5 | Không báo ăn: trống từ tuần hiện tại | 1, 2 | AC-6 |
| FR-6 | Ảnh theo điểm trường của con tại ngày; cùng điều kiện hiển thị với món | 2 | AC-7 |
| FR-7 | Chất của món, chất trùng khai báo, chữ khai không khớp | 3 | AC-8 |
| FR-8 | Không trả dữ liệu dành cho nhân sự, giờ bữa, năng lượng, người đăng | 1, 2 | AC-9 |

## Acceptance scenarios
- **AC-1** (`AUTH-04`, `CHD-04`): Given tài khoản không liên kết, hoặc con đã nghỉ/ra trường, When gọi một trong ba endpoint, Then 403 (4108); không phải role `PARENT` ⇒ 403.
- **AC-2** (`PAR-01`): Given `weekStart` là thứ Năm, Then trả tuần bắt đầu thứ Hai đó, đủ 5 ngày; bỏ trống ⇒ tuần hiện tại.
- **AC-3** (`NUT-08`, `PAR-14`): Given tuần nháp hoặc chưa lập, có ảnh đã đăng, Then `published=false`, mọi ngày không món, không ảnh.
- **AC-4** (`NUT-13`, `NUT-17`): Given tuần đã công bố, Then chỉ món khối của con ở các bữa của lớp; con chuyển lớp giữa tuần ⇒ mỗi ngày theo lớp của ngày đó.
- **AC-5** (`SCH-05`, `PAR-14`): Given ngày nghỉ ⇒ `dayOff=true`; ngày trước nhập học hoặc ngoài năm học ⇒ `enrolled=false`; cả hai không món, không ảnh, mã 200.
- **AC-6** (`CHD-13`, `PAR-14`): Given con không báo ăn, When xem tuần hiện tại hoặc sau, Then `mealsHidden=true`, không món, không ảnh; When xem tuần đã qua, Then có món và ảnh, không đánh dấu dị ứng.
- **AC-7** (`NUT-16`, `PAR-14`): Given ảnh hiển thị được cho con, Then `url` và `content` trả ảnh; ảnh điểm trường khác, ngày chưa học, ngày nghỉ, bữa ngoài lớp, tuần chưa công bố, hoặc tuần bị ẩn do không báo ăn ⇒ 404 (6068).
- **AC-8** (`NUT-23`, `PAR-14`): Given con khai "Cá biển" và "Dâu tây", Then món có cá đánh dấu chất Cá; "Dâu tây" nằm trong `unmatchedAllergies`; không có dữ liệu trẻ khác.
- **AC-9** (`PAR-14`): JSON không có số suất, người đăng, trạng thái đúng giờ, sổ mẫu, biên bản, giờ bữa, năng lượng.

## Success criteria
App dựng được màn "Thực đơn tuần" từ một lần gọi tuần và tải ảnh theo `photoId`; Web không đổi.

## Out of scope
Sửa App/Web; migration; mã lỗi mới; thông báo mới.
