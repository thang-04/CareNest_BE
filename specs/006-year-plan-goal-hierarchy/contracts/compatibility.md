# Compatibility contract
Giữ endpoint, DTO, mã lỗi, header edit-session/revision/request ID.
YearPlanRequest.Domain.rows[] và response rows[] giữ id/kind/text/goalNo/contents/curriculum fields.
ID duy nhất xuyên ba loại và không đổi sau migration; kind là thuộc tính truyền tải, không là discriminator lưu trong một bảng.
Consumer Web: src/services/education-plan/api/yearPlanMapper.js, educationPlanApi.js, useEducationPlan.jsx; không cần sửa client nếu snapshot và regression đạt. APP chưa dùng màn theo CROSS_REPO_MAP.
Input IDs phải thuộc đúng plan và loại, không được lặp; mục tiêu/nội dung được chuyển cha phải giữ định danh trong transaction.
