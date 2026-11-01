# Analysis: 005-ministry-goal-catalog (speckit-analyze)

## Lần 1 (2026-10-28)

| ID | Mức | Phát hiện | Xử lý |
| --- | --- | --- | --- |
| A1 | MEDIUM | Spec Edge "thêm lĩnh vực con mới, không di chuyển mục tiêu cũ" mâu thuẫn hàm ghép EDU-24 dùng lại (báo "chưa thêm được" khi ngữ cảnh đổi) | Sửa spec theo quy tắc EDU-24 (nhất quán, ít rủi ro) |
| A2 | LOW | SC-001 (<1 phút) không có task đo | Kiểm bằng e2e ST (lấy tất cả 1 lần bấm); không cần task riêng |
| A3 | LOW | Link chính thức chưa xác minh (R1 NEEDS VERIFICATION) | Ghi trên trang nguồn + báo Tiền; không chặn code |

Độ phủ: FR-001→T013 · FR-002→T012 · FR-003→T011,T013 · FR-004→T013 · FR-005→T003,T007,T011,T018 · FR-006→T014 · FR-007→T015 · FR-008→T001,T002 · FR-009→T011 · FR-010→T007,T011 · FR-011→T012,T015 · FR-012→T012,T015 · SC-002→T008,T016 · SC-003→T014,T015,T018 · SC-004→T018.
Không còn mục CRITICAL/HIGH ⇒ chạy lại analyze: sạch; giữ tasks.md (không cần sinh lại).
