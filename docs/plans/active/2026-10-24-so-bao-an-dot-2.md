---
title: Điểm danh & báo ăn — Đợt 2 (sổ báo ăn: tổng hợp, xác nhận, điều chỉnh)
status: in-progress
owner: Nhữ Đình Cần
jira: G94-236 (BE-FEAT-29)
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [nutrition, attendance, school-structure]
rules: [NUT-01, NUT-02, NUT-03, ATT-02, ATT-06, ATT-07, ATT-08, CFG-YR-01, AUTH-01, AUTH-02, AUTH-03]
risk: high
created: 2026-10-24
updated: 2026-10-24
---
# Điểm danh & báo ăn — Đợt 2: Sổ báo ăn

## Context
- **UI là chuẩn:** CareNest_FE `pages/attendance/MealCountPage.jsx`, `components/attendance/MealRegisterCard.jsx`; hành vi theo `attendanceMockRepository.js` (`getMealCounts`, `confirmMealCount`, `adjustMealDay`).
- Đợt 1 (`2026-10-23-diem-danh-dot-1.md`) đã có điểm danh + bữa theo trẻ và phát `AttendanceChangedEvent`.

## Phạm vi
1. `GET /meal-counts?date&schoolYearId` — sổ của các điểm trường trong phạm vi; trước giờ chốt: số tạm tính (`OPEN`, chỉ HT/PHT); sau giờ chốt: tổng hợp một lần rồi lưu.
2. `POST /meal-counts/{id}/confirm` — PHT điểm trường xác nhận từng bữa (FE "Xác nhận tất cả" gọi lần lượt).
3. `POST /meal-counts/adjustments` — PHT điểm trường sửa số suất một lớp (một bữa hoặc cả ngày), bắt buộc lý do.
4. Listener `AttendanceChangedEvent` (sau commit): điểm danh lớp đổi sau khi tổng hợp ⇒ sổ đã xác nhận chuyển chờ xác nhận lại.

Ngoài đợt 2: bàn giao suất cho bếp (NUT-14, NUT-15 PROPOSED, phụ thuộc trạng thái chuẩn bị bữa của bếp), thông báo bếp/PHT (chưa có module thông báo), suất thay thế theo dị ứng (BE chưa có dữ liệu dị ứng ⇒ 0), job tổng hợp đúng giờ chốt.

## Quyết định (Cần chốt 20/10/2026, nhắc lại 24/10)
- Sổ có tab Cả ngày (cộng các bữa) và từng bữa; xác nhận từng bữa + "Xác nhận tất cả"; không tự xác nhận.
- Chỉ PHT điểm trường xác nhận và điều chỉnh; HT chỉ xem; bếp chỉ thấy số đã xác nhận (P-05, Cần chốt 24/10).
- Sau giờ chốt GV chỉ xem điểm danh và bữa ăn, không hủy suất (ATT-08); thay đổi qua HT/PHT mở lại ngày ⇒ sổ chờ xác nhận lại. Sức khỏe trong ngày vẫn ghi.
- Điều chỉnh bắt buộc lý do; sổ đã xác nhận ⇒ số mới chờ xác nhận lại, bếp giữ số cũ (NUT-03, ADR-0005).
- Mở lại điểm danh ngày đã qua ⇒ sổ ngày đó chờ xác nhận lại (ATT-07).
- Năm học theo header; năm không hoạt động chỉ xem.

## Giả định (chờ Cần soát)
- Tổng hợp lười khi lần đầu mở sổ / hủy suất sau giờ chốt (chưa có scheduler); ai mở trước cũng tạo cùng một số.
- Điểm danh đổi khi sổ chưa xác nhận: thay số của lớp đó bằng số mới (điều chỉnh tay trước đó của lớp đó bị thay).
- Cần chốt 24/10: giữ các giả định trên; suất thay thế = 0 tới khi có dữ liệu dị ứng; chưa có module thông báo ⇒ bếp thấy nhãn "Mới" khi `confirmedAt` đổi.

## Progress log
- 2026-10-24 (3): Cần chốt lại: chỉ PHT xác nhận/điều chỉnh, HT chỉ xem; bỏ GV hủy suất sau giờ khóa (endpoint, cột lịch sử, `kitchenChangedAt`). V23 chưa commit nên sửa thẳng, DB local gỡ V23 cũ rồi chạy lại (backup `carenest-20261024-0315-before-V23-redo.dump`).
- 2026-10-24 (2): `classes[].ageGroupId` cho FE bếp (bếp FE mock tính định lượng từ số đã xác nhận).
- 2026-10-24: V23, feature `meals` (entity, repository, service, controller, listener), `AttendanceService.tallyMeals` + `cancelMealAfterLock`; `MealCountIntegrationTest` (tổng hợp + bếp chỉ thấy số đã xác nhận, trẻ đến muộn sau xác nhận, điều chỉnh có lý do); snapshot OpenAPI cập nhật.
