# Flow — Đơn xin nghỉ

Module: attendance (→ nutrition qua attendance). Rule: ATT-04, ATT-05, ATT-07.

> **PENDING P-15:** chưa xác nhận ai tạo đơn (phụ huynh qua app hay GV nhập) và có bước duyệt hay không. Implement state machine tối thiểu, actor tạo/duyệt cấu hình qua permission (`leave:create`, `leave:approve`).

## Luồng (đề xuất, chờ xác nhận actor)

```text
[S] Tạo LeaveRequest (child, từ ngày, đến ngày, lý do)       actor: PENDING
[S] (nếu có duyệt) PENDING → APPROVED / REJECTED              actor: PENDING
      ↓ APPROVED
[S] attendance đánh dấu vắng có phép các ngày tương ứng (ATT-04, PROPOSED)
[S] MealParticipation các ngày đó mặc định = không (GV chỉnh được)
      ↓
    tiếp tục flow attendance.md
    Nếu ngày đó số suất đã xác nhận → đánh dấu thay đổi, yêu cầu xác nhận lại (ATT-07, NUT-03)
```

## Trạng thái

`SUBMITTED → APPROVED | REJECTED`, `SUBMITTED/APPROVED → CANCELLED`. Nếu khảo sát cho thấy không cần duyệt: tạo = APPROVED ngay.
