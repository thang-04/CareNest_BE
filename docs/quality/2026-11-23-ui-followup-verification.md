# Kiểm chứng sửa lỗi phát hiện khi test UI — 23/11/2026

## Phạm vi và môi trường

Cấu hình trường, điểm danh & suất ăn, đánh giá trẻ. Không sửa source vai trò/tài khoản, tiếp nhận hoặc giáo án/kho. Các test liên module chỉ cập nhật fixture tương thích nhóm tuổi theo năm đã được user cho phép.

UI thực qua Vite5173, BE8080, Keycloak8180, PostgreSQL bản sao carenest_ui_20261122: teacher2 và viceprincipal. Clock nghiệp vụ cố định20/11/2026 16:30; không đổi giờ Windows. Do FE dùng giờ thực, không dùng ca mới này để kết luận giờ chốt của toàn hệ thống đã đồng bộ.

## Kết quả thực tế

| Ca | Kết quả |
| --- | --- |
| GV mở trẻ đã tắt ăn | Trưa và chiều Không ăn, mọi lựa chọn của hai bữa disabled |
| GV lưu19 trẻ rồi reload | Không ăn vẫn đúng, trẻ tham gia khác vẫn Ăn hết |
| PHT xem cùng phiếu | Chỉ đọc, mức ăn đồng bộ với GV |
| PHT lọc cấu hình tất cả/campus1/campus2 | 11/6/5 lớp tương ứng, danh mục có cả hai campus, nhãn Tất cả điểm trường |
| PHT nhập reason rồi đổi năm, chọn Hủy | Giữ reason |
| Chọn Không lưu để đổi năm | Reason trống; quay năm hiện tại chưa nhập không có dialog lặp |
| PHT hồ sơ phát triển hiện tại | Năm học, HKI, tháng11 tải được; không tái hiện9005 trên trẻ mẫu đang thử |

Ảnh ngoài Git: FE:logs/ui-proof/no-meals-after-reload.jpg, pht-campus-filter-fixed.jpg, unlock-discard-fixed.jpg, development-month-current.jpg.

## Công cụ gốc

- Node toàn bộ tests:1602 pass,0 fail/skip; logs/ui-followup-full-node.log. ESLint bốn file sửa đạt; Vite build API đạt.
- Maven lượt liên quan đầu: schema7, tiếp nhận27, attendance19, year plan38 đạt. Curriculum fixture có code quá20 ký tự và hai assertion Daily chưa tính bữa chiều không tham gia thất bại; đã sửa đúng fixture/expectation, không bỏ assertion.
- Maven lượt sau `-Dtest=DailyAssessmentIntegrationTest,LeaveRequestIntegrationTest,EduCurriculumServiceIntegrationTest,OpenApiSnapshotTest,ArchitectureRulesTest test`:61/61 pass,0fail/error/skip. logs/ui-followup-final-regression.log.
- Sau metadata schema DailyAssessmentRow: OpenApiSnapshotTest4/4 pass với chế độ sinh snapshot; Spotless check đạt. Snapshot+hai fixture kho đang kiểm lại bằng chế độ so sánh thường.
- Full Maven all-tests từ đêm22/11 không hoàn tất sau khi các tiến trình/môi trường dừng; không tính thành full pass. Không chạy harness/verify wrapper theo chỉ đạo repo07/11.

## Đối chiếu ảnh bug mới (chỉ phần của user)

- BUG-015: tái hiện bằng regression, sửa ngày trùng theo tập ngày áp dụng;13test đơn nghỉ đạt. Không test UI APP phụ huynh.
- BUG-014: code dashboard ClassTodayPanel vẫn gọi daily-assessment với ngày cụ thể; BE từ chối ngày nghỉ. Đường lỗi còn tồn tại; không đổi dashboard dùng chung trong đợt sửa này.
- BUG-006: canViewDay của attendance/observation có ngoại lệ dạy thay, nhưng GV dạy chính đã hết phân công trong năm ACTIVE còn có thể bị chặn. Cần regression riêng theo đúng khoảng phân công; không mở rộng policy chung chỉ để bỏ403.
- BUG-027: chưa tái hiện trên dữ liệu/UI local đang có (năm/HKI/tháng). Không khẳng định đã sửa được trường hợp trẻ49 trong ảnh khi chưa có cùng dữ liệu/ngày.
- BUG-004/005/025/026/028 liên quan token, admin/menu, tiếp nhận và menu dùng chung: không sửa source ngoài phần user được giao.

## Giới hạn

Chưa kết luận full UI tất cả vai trò, mọi scope CRUD hoặc toàn bộ thông báo/chuyển lớp/nhập học đạt. Không triển khai tối ưu DB, không làm lớp ghép, không sửa production/DB local gốc.

## Bổ sung sửa khởi động local theo yêu cầu mới của user

User gửi log checksum mismatch167/168 và yêu cầu sửa DB local để test cùng. Sau backup local, đổi đúng lịch sử hai migration riêng của nhánh sang170/171, giữ checksum; migrate dev167/168 còn thiếu. Không dùng repair đại trà. CareNestApplication thông thường khởi động8080 với Clock thật; UI5173 tải hồ sơ phát triển. Không sửa .env/application.yml. Dữ liệu giữ289 trẻ,11972 điểm danh,11340 đánh giá ngày; không tác động production.
