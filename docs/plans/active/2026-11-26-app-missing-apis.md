---
title: API còn thiếu cho App phụ huynh và bếp — đợt 1 (tên điểm trường, chặn ghi hủy mẫu năm đã đóng)
status: in-progress
owner: Nguyen Duy Thang
jira: G94-495 (G94-496, G94-499) / mã công việc chưa chốt
branch: chưa tạo (worktree detached từ origin/dev)
modules: [identity-access, nutrition]
rules: [AUTH-22, CFG-YR-01, NUT-21]
risk: medium
created: 2026-11-26
updated: 2026-11-26
---
# API còn thiếu cho App — đợt 1

## Context
- G94-496: App bếp cần tên điểm trường; `GET /accounts/me` chỉ có `campusId`, `GET /campuses` chỉ HT/PHT gọi được.
- G94-499: `FoodSampleServiceImpl#disposeSample` không kiểm năm học của mẫu.
- Đợt sau, chưa làm: G94-497 (thực đơn và ảnh cho phụ huynh), G94-498 (yêu cầu cấp lại mật khẩu — chưa có rule).

## Quyết định đã chốt
1. Làm G94-499 và G94-496 trước — user, 26/11/2026.
2. G94-496 theo phương án (b): `campusName` trong `assignments` của `/accounts/me` — đề xuất được user chọn làm, 26/11/2026.
3. G94-499 dùng lại mã 2003 `SCHOOL_YEAR_CLOSED`, không thêm `ApiCode`.

## Làm rõ nghiệp vụ

### Câu hỏi mở
Không còn câu hỏi chặn đợt 1.

## Acceptance criteria
- **AC-1** (`AUTH-22`): tài khoản bếp gọi `/accounts/me` nhận tên điểm trường đang phân công hôm nay; phân công hết hạn không hiện.
- **AC-2** (`CFG-YR-01`): ghi hủy mẫu thuộc năm đã kết thúc ⇒ 2003, mẫu giữ nguyên; năm đang chạy như cũ.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Chỗ đặt `campusName` | DTO riêng `MyAssignmentResponse` | thêm field vào `StaffAssignmentSummaryResponse` | DTO chung dùng ở nhiều API khác; chỉ đổi response tài khoản hiện tại và đăng nhập |

## Test matrix
| AC | Test |
| --- | --- |
| AC-1 | `AccountServiceImplTest#assignmentsCarryCampusName`, `KitchenRecordsIntegrationTest#kitchenAccountCarriesTodayCampusName` |
| AC-2 | `KitchenRecordsIntegrationTest#disposeRejectedWhenSchoolYearClosed`, `#disposeAfter24Hours` |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu |
| --- | --- | --- |
| Mẫu lấy ngày cuối năm, năm bị kết thúc trước khi đủ 24 giờ | Bếp không ghi hủy được | Đúng yêu cầu CFG-YR-01; cần nhóm xác nhận nếu muốn ngoại lệ |
| `approve` / duyệt hủy của PHT chưa kiểm năm | Vẫn ghi được dữ liệu năm đã đóng | Ngoài phạm vi G94-499; đề xuất task riêng |

## Progress log
- 26/11/2026: code + test đợt 1 xong; test mới đã fail trước khi sửa. `node scripts/verify.mjs` PASS full (2747 test, 0 fail, 0 error, 3 skip là AI live eval thiếu biến môi trường). Chưa commit, chưa thử trên Web/App.
