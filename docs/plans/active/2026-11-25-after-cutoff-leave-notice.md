---
title: Báo nghỉ chiều sau giờ chốt không đổi suất ăn
status: approved
owner: Cần
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [attendance, organization]
rules: [ATT-04, ATT-05, ATT-05b, ATT-06, ATT-12, PCK-04]
risk: high
created: 2026-11-25
updated: 2026-11-25
---
# Báo nghỉ chiều sau giờ chốt không đổi suất ăn

## Context

`LeaveRequestServiceImpl#planLeave` hiện bỏ toàn bộ ngày sau giờ chốt; nếu chỉ chọn chiều hôm nay thì API từ chối. `cancelLeaveRequest` còn có thể xóa dấu nghỉ chiều không do đơn tạo. Điều này không giữ được thông báo cho giáo viên và có nguy cơ làm sai lịch sử điểm danh.

## Quyết định đã chốt

1. Đơn nghỉ là thông báo của phụ huynh, không có giáo viên duyệt/xác nhận — user, 25/11/2026.
2. Sau giờ chốt chỉ nhận báo nghỉ chiều của chính hôm nay; lưu lịch sử và báo giáo viên, không tự mở ngày, không đổi điểm danh hay suất ăn đã chốt — user, 25/11/2026.
3. Hủy báo nghỉ sau chốt chỉ hủy thông báo; hủy đơn trước giờ chốt chỉ hoàn tác đúng hiệu ứng do đơn đã tạo — user, 25/11/2026.
4. HT cấu hình lịch sinh hoạt có hiệu lực theo năm học: mở đón trẻ; bắt đầu/kết thúc sáng; bắt đầu/kết thúc chiều; bắt đầu/kết thúc khung trả trẻ; mốc giữa chiều tính đủ ngày — user, 25/11/2026. Giờ chốt điểm danh/suất ăn và giờ đến muộn nhất buổi chiều là hai cấu hình độc lập. Bản này áp dụng chung cho các điểm trường của năm học vì cấu trúc `attendance_cutoffs` hiện có chưa mang `campus_id`.
5. Trẻ đã điểm danh sáng và được trả trước mốc giữa chiều tính 0,5 ngày; từ mốc giữa chiều tính 1 ngày. Không dùng giờ bắt đầu chiều làm mốc đủ ngày — user, 25/11/2026.
6. Việc trả trẻ sau giờ chốt không thay đổi suất đã chốt; trước giờ chốt, thay đổi suất cần tuân theo bữa thực tế được GV ghi nhận — user, 25/11/2026.

## Làm rõ nghiệp vụ

- **Hiểu nghiệp vụ:** phụ huynh báo nghỉ · giáo viên nhận thông báo và tự ghi nhận thực tế · HT đặt lịch sinh hoạt theo năm học · ATT-05 (CONFIRMED), ATT-05b (điều chỉnh theo quyết định 25/11), ATT-12 (điều chỉnh theo quyết định 25/11), PCK-04 (điều chỉnh theo quyết định 25/11).
- **Đối chiếu thiết kế ban đầu:** `BUSINESS_RULES.md:91,98` ghi ngày sau chốt bị bỏ qua; yêu cầu mới chỉ thêm loại thông báo cho nghỉ chiều hôm nay, không làm thay đổi dữ liệu đã chốt.
- **Ảnh hưởng:** attendance, organization; quyền không đổi (PARENT gửi/hủy, GV/BGH chỉ xem theo scope); meal count không bị cập nhật; FE/App cần đọc cờ nhãn mới từ DTO nhưng không đổi endpoint. Nếu từng điểm trường cần giờ khác nhau, đó là thay đổi tiếp theo: thêm `campus_id` vào lịch sử cấu hình và chọn cấu hình theo lớp/điểm trường.

### Câu hỏi mở

Không có. Các mốc giờ là dữ liệu HT cấu hình, không hard-code theo một giờ chung toàn quốc.

## Acceptance criteria

- **AC-1** (`ATT-05`): phụ huynh chỉ gửi/hủy đơn của trẻ được liên kết; không có bước duyệt.
- **AC-2** (`ATT-05b`): sau giờ chốt, đơn nghỉ chiều chỉ của hôm nay được lưu dạng thông báo; không tạo/sửa `AttendanceRecord` và không phát sự kiện thay đổi suất ăn.
- **AC-3** (`ATT-04`, `ATT-12`): hủy đơn trước giờ chốt chỉ khôi phục đúng trạng thái/bữa ăn do đơn tạo; không ghi đè sửa đổi sau đó của giáo viên.
- **AC-4** (`PCK-04`): HT cấu hình lịch sinh hoạt theo năm học, có lịch sử hiệu lực và áp dụng chung cho các điểm trường của năm đó.
- **AC-5** (`PCK-04`): trẻ đã có mặt sáng và trả trước mốc giữa chiều được tính 0,5; từ mốc đó được tính 1 ngày; số suất sau giờ chốt không đổi.

## Key decisions

| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Báo sau chốt | `noticeOnly` chỉ chiều hôm nay | Mở lại ngày tự động | Giữ số suất và quyền mở ngày của ATT-08 |
| Hoàn tác đơn | Bảng hiệu ứng lưu ảnh chụp trước/sau | Suy từ `recordedBy` | Không thể phân biệt an toàn thao tác giáo viên với đơn phụ huynh |
| Lịch sinh hoạt | HT cấu hình theo năm học, có hiệu lực | Hard-code một giờ toàn trường | Giữ đúng phạm vi dữ liệu hiện có; campus override là hạng mục tách riêng nếu cần |
| Mốc chuyên cần | Mốc giữa chiều độc lập giờ bắt đầu chiều | Tính đủ ngày ngay khi vào chiều | Khớp quyết định user 25/11 |

## Phases

### Phase 1 — Dữ liệu và service đơn nghỉ
- Files: C `V195__leave_notice_and_attendance_effects.sql` · C `LeaveAttendanceEffect` · M `LeaveRequestServiceImpl`, DTO, repository, tests.
- Steps: lưu `noticeOnly`; lưu snapshot trước/sau của bản ghi bị đơn thay đổi; thêm báo nghỉ chiều hôm nay sau giờ chốt; hủy đúng hiệu ứng thuộc đơn.
- Tests: AC-1..3 → `LeaveRequestIntegrationTest`.
- Exit: targeted Maven tests pass, Spotless pass.

### Phase 2 — Nhãn và tài liệu hợp đồng
- Files: M `AttendanceServiceImpl`, OpenAPI/business flow/API mapping khi contract có cờ mới.
- Steps: dòng điểm danh hiện nhãn báo nghỉ sau chốt; không che thông tin điểm danh thực tế; cập nhật rule đã chốt.
- Tests: AC-2 → response integration test.
- Exit: targeted Maven tests pass, Spotless pass.

### Phase 3 — Lịch sinh hoạt, đón trẻ và chuyên cần
- Files: C migration lịch sinh hoạt · M cutoff/schedule service, `PickupServiceImpl`, DTO/OpenAPI · FE cấu hình thời gian và màn đón trẻ.
- Steps: HT cấu hình các mốc giờ theo năm học/điểm trường; điểm trả trẻ áp dụng mốc giữa chiều để ghi `absentPart=AFTERNOON` khi cần; bảo toàn bữa đã chốt; FE hiển thị mốc/nhãn.
- Tests: AC-4, AC-5 → schedule/pickup integration + FE validation tests.
- Exit: BE targeted tests, FE lint/test/build pass.

## Test matrix

| AC | Rule | Test | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | ATT-05 | guardian/role integration | 1 | code complete; Testcontainers pending |
| AC-2 | ATT-05b | after-cutoff afternoon notice | 1–2 | code complete; Testcontainers pending |
| AC-3 | ATT-04, ATT-12 | cancellation restores own effect only | 1 | code complete; Testcontainers pending |
| AC-4 | PCK-04 | cutoff/schedule mapping | 3 | compile + FE validation passed |
| AC-5 | PCK-04 | pickup before/at full-day threshold | 3 | `PickupServiceImplTest` passed |

## Rủi ro & rollback

| Rủi ro | Giảm thiểu | Rollback |
| --- | --- | --- |
| Ghi sai suất sau giờ chốt | Không phát `AttendanceChangedEvent` cho `noticeOnly` | Migration bổ sung, tắt nhánh notice-only |
| Hủy ghi đè thao tác mới | So khớp snapshot sau khi đơn tạo | Không hoàn tác tự động, yêu cầu mở lại ngày |

## Progress log

### 2026-11-25 — Phases 1–3 (implemented, awaiting integration DB)
- User đã duyệt triển khai trong hội thoại ngày 25/11/2026.
- Đã thêm `noticeOnly`, snapshot hiệu ứng của đơn, nhãn FE, migration V196 (đổi từ V183, V190, V195 khi merge dev 25/11); đơn báo nghỉ chiều sau giờ chốt chỉ là thông báo và hủy chỉ hủy thông báo.
- Đã thêm lịch sinh hoạt theo năm học, migration V197 (đổi từ V184, V191, V196 khi merge dev 25/11), API/OpenAPI/FE validation, và mốc tính đủ ngày khi trả trẻ.
- Đã mở rộng V197 thành tám mốc vận hành: mở đón trẻ, sáng bắt đầu/kết thúc, chiều bắt đầu/kết thúc, trả trẻ bắt đầu/kết thúc và mốc tính đủ ngày; giờ chốt/báo ăn và giờ đến muộn vẫn độc lập.
- `PickupServiceImplTest` và compile/Spotless BE đã qua; test tích hợp `LeaveRequestIntegrationTest` bị Testcontainers skip do máy không có Docker. FE lint/build/API contract và test `attendance-half-day` đã qua.
