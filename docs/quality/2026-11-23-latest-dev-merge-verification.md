# Merge dev mới nhất — 23/11/2026

## Phạm vi và kết quả xử lý xung đột

- BE dev6b393294 (nhận xét kỳ bằng AI), FE devf2d65234 được tích hợp vào hai nhánh hiện tại; cập nhật PR90/114, không push trực tiếp dev.
- EvaluationRow giữ cả historyStatus của nhánh và aiEnabled của dev; mapper truyền đủ hai trường. Giữ nguyên quyền, phạm vi theo năm/phân công và guard trẻ rời lớp. Không đổi nghiệp vụ nhận xét AI của dev.
- FE tự merge phần đánh giá AI; giữ nhãn lịch sử, trạng thái chốt và kiểm tra quyền từ API.
- User xác nhận đổi riêng V171__weekly_comments_per_class.sql thành V174; SHA256 trước/sau giống nhau. V171 nhận xét AI của dev, V172 bản in cuối năm và V173 danh mục nhóm tuổi giữ nguyên SQL.
- Review độc lập: PASS_WITH_RISK, không phát hiện lỗi tích hợp qua kiểm tra tĩnh; giới hạn là cần xác minh runtime bằng test bên dưới.

## Kiểm chứng

- BE compile và spotless:check đạt. Native Maven test sau merge:155/155 đạt,0fail,0error,0skip,16 lớp test (điểm danh, đánh giá ngày/chủ đề, cấu hình lớp/theo năm, phạm vi lịch sử, nhận xét AI, API snapshot và kiến trúc). Log local latest-dev-merge-tests.log; không chạy wrapper verify thủ công. OpenAPI tái sinh giữ cả historyStatus và aiEnabled, không có thay đổi thêm ngoài snapshot merge.
- FE toàn bộ Node tests1661/1661 đạt,0skip; npm run lint và npm run build đạt.
- GitHub FE run45205718254 trên commit18d44f41 đã thành công. CI BE trên bản merge mới được theo dõi sau push; không suy test local thành CI toàn bộ đã đạt.
- Đã thử migration trên carenest_ui_20261122 trước, sau đó sao lưu và đồng bộ đúng script/checksum của lịch sử tuần171→174 ở DB local carenest_test_20261121, chạy migration171 AI. Validate189 migrations ở DB local thành công; số trẻ/điểm danh/đánh giá ngày trước/sau giữ nguyên. Không tắt validate, không sửa checksum hoặc migration dev, không tác động production.
- Giữ nguyên cấu hình local, verification notes và launcher kiểm thử không thuộc commit. Chưa khẳng định bao phủ toàn bộ UI sau merge.
