---
title: Tối ưu DB cho ba phân hệ CareNest
status: draft
owner: Nhữ Đình Cần
jira: chưa có task / DB-OPT
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [organization, attendance, meals, observation]
rules: []
risk: high
created: 2026-11-19
updated: 2026-11-19
---
# Kế hoạch tối ưu DB cho 3 phần CareNest

Status: proposed — chưa triển khai migration tối ưu mới
Phạm vi: Cấu hình trường; Điểm danh & suất ăn; Đánh giá trẻ
Ngoài phạm vi: tài khoản nhân sự, vai trò/quyền, kế hoạch giáo dục và module của thành viên khác (chỉ đọc khóa tham chiếu khi cần)

## Nguyên tắc

- Đúng dữ liệu và đúng phạm vi năm học/campus/lớp trước, nhanh sau.
- Chỉ thêm index khi `EXPLAIN (ANALYZE, BUFFERS)` chứng minh truy vấn thật cần; không đánh index mọi cột.
- Mỗi thay đổi schema có migration riêng, chạy được từ DB trống và DB có dữ liệu; không sửa migration đã phát hành.
- Giữ dữ liệu lịch sử khi trẻ chuyển lớp/thôi học và khi năm học đóng.
- Không dùng cache để che truy vấn sai; cache phải có khóa gồm năm học và phạm vi.
- Các thao tác ghi đồng thời phải được bảo vệ bằng unique/FK/lock ở DB, không chỉ kiểm tra ở Java.

## Bước 0 — Đo hiện trạng

1. Tạo bộ dữ liệu hiệu năng 3 năm học, 3 campus, 60 lớp, 2.000 trẻ, 12 tháng điểm danh/đánh giá; chỉ dùng local/test.
2. Bật `pg_stat_statements` ở môi trường đo; lấy top truy vấn theo tổng thời gian, trung bình, số lần gọi và rows.
3. Với từng màn GV/Tổ trưởng/PHT/HT, lưu `EXPLAIN (ANALYZE, BUFFERS, WAL)` cho truy vấn danh sách, chi tiết, lưu và xuất Excel.
4. Ghi baseline: số query/request, thời gian DB, số buffer hit/read, lượng row quét và kích thước index.
5. Kiểm tra N+1 bằng log Hibernate/statistics; không tối ưu theo cảm giác.

Mục tiêu đề xuất: truy vấn danh sách thông thường p95 dưới 300 ms ở bộ dữ liệu đo; thao tác lưu p95 dưới 500 ms; không có sequential scan lớn hoặc N+1 theo số trẻ. Export lớn đo riêng, xử lý streaming.

## 1 — Cấu hình trường

### Đã có trong V154

- PK theo năm cho nhóm tuổi, điểm trường, phòng.
- Unique không phân biệt hoa thường theo đúng năm/phạm vi.
- FK bảo đảm phòng thuộc điểm trường của chính năm học; FK người phụ trách; CHECK loại/trạng thái/sức chứa.
- Index tra cứu ngược theo bản ghi gốc và người phụ trách.

### Cần đo và tối ưu tiếp

1. `staff_assignments`: đo các truy vấn phân công theo năm, vai trò và phạm vi. Candidate sau khi đo:
   - `(user_id, school_year_id, staff_role)`;
   - `(school_year_id, campus_id, staff_role)` với dòng campus;
   - `(school_year_id, age_group_id, staff_role)` với tổ trưởng;
   - `(school_year_id, classroom_id, staff_role)` với giáo viên lớp.
   Ưu tiên partial index theo `staff_role` nếu nhỏ hơn index tổng quát.
2. `classrooms`: đo màn Nhóm tuổi & lớp/wizard. Candidate `(school_year_id, campus_id, age_group_id, status)`; giữ index phòng riêng cho kiểm tra trùng.
3. Gộp các lần lấy số lớp, sĩ số, giáo viên và phòng thành projection/group-by theo năm thay vì lặp query từng campus/lớp.
4. Rà `YearSetupServiceImpl`, `ClassManagementServiceImpl`, `TeacherAssignmentServiceImpl` để loại N+1; đo trước/sau bằng query count.
5. Xóa/đổi trạng thái phải dùng bảng membership theo năm; không đọc nhầm bảng snapshot hoặc bảng chung.
6. Không partition các bảng cấu hình: số dòng nhỏ, PK theo năm đã đủ; partition làm tăng độ phức tạp mà không có lợi.

## 2 — Điểm danh & suất ăn

### Candidate index cần xác minh bằng EXPLAIN

1. `attendance_records`:
   - `(date, absent_part, status)` cho truy vấn theo ngày/phần buổi/trạng thái;
   - `(campus_id, date)` nếu tổng hợp PHT/HT thực sự đọc trực tiếp theo campus;
   - index hiện có `(classroom_id, date)` và unique `(child_id, date)` tiếp tục phục vụ màn lớp/trẻ.
2. `attendance_day_unlocks`: partial index bản mở còn hiệu lực theo `(classroom_id, date, valid_until)` nơi `revoked_at IS NULL` nếu query plan cần.
3. `daily_health_entries`:
   - đo `(child_id, date, recorded_at DESC, id DESC)` cho lịch sử trẻ;
   - đo `(classroom_id, date, recorded_at DESC, id DESC)` cho màn nhận trẻ;
   - chỉ thêm `(campus_id, date)` nếu màn PHT/HT truy vấn theo campus.
4. `meal_counts`: unique `(campus_id, date, session)` đã đúng. Cân nhắc partial index hàng chờ xác nhận theo campus/ngày khi số năm lớn; không thêm nếu planner dùng unique index hiệu quả.
5. `meal_count_lines`: unique `(meal_count_id, classroom_id, pending)` đã bao phủ truy vấn chính; tránh index trùng.
6. Kho/bếp: kiểm tra lại các index FIFO, campus-ngày và shortfall hiện có; loại các phép tổng hợp bằng nhiều vòng Java nếu có thể chuyển thành một `GROUP BY`/projection.

### Tối ưu luồng

- Lấy danh sách trẻ + điểm danh + đơn nghỉ + lần sức khỏe mới nhất theo batch, không query từng trẻ.
- Upsert điểm danh theo unique `(child_id,date)` và giữ history trong cùng transaction.
- Tổng hợp suất ăn theo một truy vấn nhóm lớp/bữa; khóa theo campus-ngày theo thứ tự cố định để tránh deadlock.
- Export Excel dùng streaming/cursor, không nạp toàn bộ lịch sử của cả năm vào RAM.
- Không index cột chuỗi `meals`; chỉ cân nhắc chuẩn hóa khi có truy vấn thống kê theo từng bữa chứng minh cần thiết.

## 3 — Đánh giá trẻ

### Candidate index cần xác minh bằng EXPLAIN

1. `child_evaluations`:
   - `(school_year_id, period_type, status, child_id)` cho danh sách học kỳ/cuối năm theo phạm vi;
   - `(school_year_id, period_type, period_start)` cho kiểm tra/tạo kỳ;
   - `(child_id, status, period_start, period_end)` cho hồ sơ phát triển và dữ liệu cũ khi chuyển lớp.
2. `semester_assessments`:
   - `(school_year_id, period_start)` cho kiểm tra kỳ;
   - đo `(campus_id, school_year_id, semester_no)` cho màn PHT/HT toàn campus.
3. `theme_evaluations`: unique `(classroom_id, theme_id)` đủ cho màn lớp; chỉ thêm `(school_year_id, campus_id, confirmed_at)` nếu truy vấn tổng hợp quản lý cần.
4. `daily_observations` và `daily_activity_results`: unique/index child-date và classroom-date hiện có; đo index có thứ tự ngày cho tổng hợp khoảng thời gian.
5. `class_ratings`: giữ `(school_year_id, period)` và unique lớp-kỳ; không thêm index trùng.

### Thay đổi cấu trúc quan trọng nhất

`daily_activity_results` hiện lưu `goals_met/goals_effort/goals_not_met` dưới dạng chuỗi danh sách. Đây là điểm khó tối ưu và khó bảo đảm dữ liệu khi tính cuối chủ đề/cuối năm. Đề xuất theo hai giai đoạn:

1. Tạo bảng chuẩn hóa `daily_activity_goal_results` gồm `daily_activity_result_id`, `child_id`, `classroom_id`, `date`, `activity_key`, `goal_lineage_id`, `result` và unique cho một mục tiêu trong một hoạt động.
2. Index `(child_id, date, goal_lineage_id, result)` và `(classroom_id, date, goal_lineage_id)`.
3. Backfill từ dữ liệu chuỗi, kiểm tra đối soát 100%, sau đó dual-write trong một phiên bản.
4. Chuyển tính cuối chủ đề: trong khoảng ngày chủ đề, một hoạt động đạt thì mục tiêu chủ đề đạt; vắng không sinh kết quả và không tự biến thành chưa đạt.
5. Chuyển tính cuối năm: với từng mục tiêu, số chủ đề đạt / số chủ đề có mục tiêu và có căn cứ, đạt khi >= 50% theo nghiệp vụ đã chốt.
6. Sau một phiên bản ổn định mới bỏ đọc chuỗi cũ. Các snapshot đã xác nhận (`cells`, `goal_results`, `result_snapshot`, nhận xét lĩnh vực) vẫn giữ nguyên để bản in lịch sử không đổi; có thể đổi TEXT sang JSONB ở migration riêng nhưng không tạo GIN nếu không truy vấn nội dung JSON.

## 4 — Tính toàn vẹn, đồng thời và dữ liệu lịch sử

- Mọi unique nghiệp vụ phải nằm ở DB: một trẻ/ngày, một phiếu/lớp-kỳ, một chủ đề/lớp, một cấu hình/năm.
- Tất cả query phạm vi Tổ trưởng/PHT phải có `school_year_id` cùng campus/age-group, không suy từ `valid_to IS NULL`.
- Quy định thứ tự khóa: năm học → campus → lớp → trẻ/bản ghi; thêm test đồng thời cho lưu điểm danh, xác nhận suất ăn, xác nhận đánh giá.
- Không cascade xóa dữ liệu nghiệp vụ lịch sử. Chỉ cascade bảng con thuần túy (line/history thuộc bản ghi cha) khi lịch sử vẫn được bảo toàn theo yêu cầu.
- Chuyển lớp/thôi học không đổi khóa hoặc xóa đánh giá cũ; query hồ sơ theo child và khoảng hiệu lực enrollment.

## 5 — Migration và triển khai an toàn

1. Tách migration index/constraint nhẹ khỏi migration chuẩn hóa đánh giá.
2. Với production lớn, tạo index `CONCURRENTLY` bằng migration không transaction hoặc runbook riêng; kiểm tra Flyway hỗ trợ trước.
3. Backfill theo batch có checkpoint; báo số dòng nguồn/đích và checksum nghiệp vụ.
4. Triển khai theo thứ tự: index + query rewrite → quan sát → bảng goal normalized + dual-write → chuyển read → dọn cột cũ.
5. Có cờ rollback read-path; không rollback bằng xóa dữ liệu mới.

## 6 — Test bắt buộc

- DB trống chạy toàn bộ Flyway; DB copy gần production chạy migrate và validate.
- Test độc lập 3 năm học, 2 campus, chuyển lớp giữa kỳ, thôi học, nhập học giữa năm.
- Test đồng thời 2 giáo viên lưu/xác nhận; idempotency; mở lại ngày; phụ huynh hủy đơn trước/sau chốt.
- Đối soát tổng: điểm danh, suất ăn, sức khỏe, đánh giá chủ đề/học kỳ/cuối năm trước và sau tối ưu phải bằng nhau.
- Full BE/FE test; đo lại EXPLAIN và query count. Không duyệt nếu nhanh hơn nhưng thay đổi kết quả nghiệp vụ.

## Thứ tự ưu tiên đề xuất

1. P0: đo baseline, khóa phạm vi năm học, unique/FK và N+1.
2. P1: index/query cho attendance, health, child evaluations, semester assessments.
3. P1: chuẩn hóa kết quả mục tiêu hằng ngày và chuyển phép tính cuối chủ đề/cuối năm.
4. P2: streaming export, pg_stat_statements dashboard, cảnh báo slow query.
5. P3: chỉ cân nhắc partition theo năm khi bảng điểm danh/hoạt động đạt hàng chục triệu dòng và EXPLAIN chứng minh pruning có lợi.

## Deliverables

- Báo cáo baseline và after kèm query plan.
- Migration + data dictionary + rollback/runbook.
- Test đối soát nghiệp vụ và test đồng thời.
- Danh sách index được thêm/không thêm kèm lý do.
- Kết quả full test và smoke test theo từng role/phạm vi.
