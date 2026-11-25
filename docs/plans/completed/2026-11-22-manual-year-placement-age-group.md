# Xếp thủ công năm mới sang nhóm tuổi khác

## Phạm vi và quyết định

- Cần yêu cầu ngày 22/11/2026: cho trẻ vào lớp năm mới thuộc nhóm tuổi khác; chưa làm lớp ghép. Yêu cầu này thay điều kiện bắt buộc cùng/đúng nhóm tuổi của thao tác xếp thủ công trong CFG-YR-03; không đổi tự động xếp hay chuyển lớp giữa năm.
- Chỉ YearTransitionServiceImpl và FE school-config tương ứng, gồm trẻ đang học và trẻ mới tiếp nhận trong cùng bảng xếp năm mới. Không sửa màn tiếp nhận, chuyển lớp giữa năm, giáo án, vai trò/quyền hoặc DB.
- Giữ năm đích sắp diễn ra sớm nhất, quyền/phạm vi nguồn và đích, campus đăng ký của trẻ mới, sức chứa, tốt nghiệp, trạng thái trẻ, giao dịch và ngày hiệu lực khi kích hoạt. Gợi ý/tự động vẫn theo tuổi.
- Lớp vẫn có một nhóm tuổi. Sau kích hoạt, bữa ăn và mục tiêu áp dụng theo lớp đó; chưa có chế độ riêng theo tuổi từng trẻ trong lớp.

## Thực hiện

1. BE: bỏ chặn lệch nhóm khi lưu thủ công và kiểm tra readiness; giữ kiểm tra lớp đích thuộc năm, có nhóm tuổi và sĩ số.
2. FE: danh sách chọn lớp thủ công hiện đủ lớp trong phạm vi; tên kèm nhóm tuổi, campus, sĩ số. Chọn mặc định vẫn theo nhóm gợi ý, không tự chọn nhóm bất kỳ.
3. Đồng bộ mock; test lưu khác nhóm, kích hoạt, tự động đúng tuổi, sai năm/campus/quyền/sức chứa vẫn bị chặn.
4. Chạy Maven/Node/lint/build gốc; UI local lưu và tải lại bản xếp khác nhóm. Không commit/push trong bước này.

## Tiến độ

- Hoàn tất code và kiểm chứng theo yêu cầu trực tiếp của Cần. Không đổi API contract hoặc thêm migration.
- BE 23 test liên quan đạt (18 YearTransition, 1 PlacementRules, 4 controller), không skip; Spotless check đạt.
- FE 282 test đạt, không skip; ESLint/Prettier và build API đạt. Review độc lập không có góp ý chặn.
- UI DB clone: lưu/reload khác nhóm thành công ở trẻ đang học và trẻ mới tiếp nhận. Sửa thêm lỗi reset lựa chọn trẻ mới và bảng hẹp bị đè chữ; kiểm 390px (cuộn trong bảng) và 1440px (đủ cột), trang không tràn ngang. Không kích hoạt năm mới trên UI khi chưa tới cửa sổ/thiếu bước; ca kích hoạt kiểm bằng integration PostgreSQL.
- Không commit/push trong lượt này. Ma trận UI ba module tổng thể vẫn theo docs/quality/2026-11-22-ui-execution.md, không suy từ việc này thành tất cả luồng đều đạt.
