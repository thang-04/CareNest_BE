# Feature011 — Báo cáo tổng hợp tăng trưởng theo tháng (mẫu 5.1)

Nguồn: `domain-spec.md`, user 04/11/2026. Plan quản trị: `docs/plans/active/2026-11-04-health-growth-report.md`.

## User stories và acceptance
- US1 (P1): HT xem bảng 5.1 của mọi điểm trường theo tháng, lọc một điểm trường. AC-2, AC-3, AC-4.
- US2 (P1): PHT xem bảng 5.1 của điểm trường được phân công, không xem được điểm trường khác. AC-2.
- US3 (P2): Số trẻ suy dinh dưỡng, thừa cân, béo phì theo chuẩn WHO đúng ngưỡng. AC-1, AC-4.

## Functional requirements
- FR-01: `GET /health-checks/growth-report?month=yyyy-MM[&campusId=]` cho HT, PHT; GV và vai trò khác 403.
- FR-02: Mỗi điểm trường trong phạm vi một khối: đợt/kỳ của tháng (chưa hủy, ngày bắt đầu trong tháng), lớp với số đếm, tổng MG, NT, điểm trường; điểm trường không có đợt/kỳ vẫn trả, danh sách rỗng, số 0 (điểm trường ngừng hoạt động không có đợt/kỳ bị ẩn, trừ khi lọc đúng nó).
- FR-03: Đếm trẻ theo danh sách lớp của đợt/kỳ, hợp theo trẻ; được cân đo theo domain spec mục 10; lấy lần mới nhất.
- FR-04: Xếp W/A, H/A, BMI theo bảng WHO (`who-weight-for-age.csv`, `who-height-for-age.csv`, `who-bmi-for-age.csv`); ánh xạ cột theo domain spec mục 10; `unclassified` = được cân đo mà có chỉ số không xếp được.
- FR-05: Thứ tự lớp: MG theo `sortOrder` nhóm tuổi giảm dần rồi tên, NT theo tên, lớp không nhóm tuổi cuối.
- FR-06: Tổng toàn trường = cộng các điểm trường trả về; tỷ lệ % do FE tính.
- FR-07: Sổ sức khỏe, cảnh báo, BMI của sổ không đổi; W/A, H/A chỉ dùng trong báo cáo.

## Clarifications
### Session 2026-11-04
- Q: Phạm vi? → A: user: HT toàn bộ phân hiệu, PHT phân hiệu được phân công.
- Q: Trẻ được cân đo, ánh xạ cột, cấp học, thứ tự? → A: người điều phối (brief): như FR-03..05; nguy cơ thừa cân vào cột BT.
- Q: Tháng của đợt/kỳ? → A: ngày bắt đầu trong tháng (brief).

## Success criteria
- Báo cáo đúng số với dữ liệu test (Testcontainers) cho HT, PHT, tháng có/không có đợt/kỳ.
- Bộ phân loại khớp giá trị SD WHO công bố; snapshot OpenAPI cập nhật có chủ đích; verify full pass.
