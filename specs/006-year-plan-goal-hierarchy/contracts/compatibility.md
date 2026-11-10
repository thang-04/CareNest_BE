# Compatibility contract
Giữ endpoint, DTO, mã lỗi, header edit-session/revision/request ID.
YearPlanRequest.Domain.rows[] và response rows[] giữ id/kind/text/goalNo/contents/curriculum fields.
ID duy nhất xuyên ba loại và không đổi sau migration; kind là thuộc tính truyền tải, không là discriminator lưu trong một bảng.
Consumer Web: src/services/education-plan/api/yearPlanMapper.js, educationPlanApi.js, useEducationPlan.jsx; không cần sửa client nếu snapshot và regression đạt. APP chưa dùng màn theo CROSS_REPO_MAP.
Input IDs phải thuộc đúng plan và loại, không được lặp; mục tiêu/nội dung được chuyển cha phải giữ định danh trong transaction.

## UI delta02/11 (EDU-48)
- Không thêm DELETE endpoint cho lĩnh vực con, không đổi DTO/ApiCode/header/PUT. Đây là thay hành vi nút xóa trong bản soạn, không phải sửa contract chuyển cha.
- Mở ConfirmationModal danger: tên nhánh + số nhóm, mục tiêu, nội dung, dấu tích sẽ mất + thông báo chỉ lưu khi bấm Lưu. Hủy giữ nguyên toàn bộ bản soạn; Xóa bỏ đúng đoạn nhánh.
- PUT sau Lưu thiếu các ID thuộc nhánh đã xóa và vẫn chứa mọi row/tick/chủ đề ngoài nhánh như trước. Không tự gửi PUT khi chỉ xác nhận xóa; lease/revision/request ID theo client sẵn có.
- Chỉ read-only/view không hiển thị thùng rác. Không thay contract/bản ban hành/tài liệu lớp hoặc nguồn Bộ.
- Select cur-domain: hiển thị một dòng/ellipsis, title đầy đủ nhãn của giá trị hiện tại, option/value và callback lọc nguyên vẹn; cur-target giữ hành vi hiện có. Không thêm component select hoặc thư viện.
