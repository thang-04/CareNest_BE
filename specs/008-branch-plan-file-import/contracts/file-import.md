# Contract draft — Nhập file

User đã duyệt cùng plan làn L ngày 01/11/2026; API nhánh thuộc phase 2, chưa implementation ở phase 1.

## API mới

`POST /education-plans/class-docs/branch/file-preview` (đường dẫn controller, chịu prefix API chung hiện hành).
Content-Type multipart/form-data; `file` là part bắt buộc; `classId` và `yearPlanId` là tham số bắt buộc (Long).
Role TEACHER và giáo viên đang phân công chính lớp đó; không dùng riêng role để cấp scope.
Kế hoạch đã ban hành, đúng lớp/năm học/nhóm tuổi, phiên bản hiện hành; tài liệu nhánh hiện có phải sửa được. Chưa có tài liệu vẫn preview được khi đủ điều kiện tạo.

Response giữ ResponseJson hiện hành, data:

```json
{
  "matches": [{"row": 2, "themeId": 10, "themeName": "Chủ đề mẫu", "weekStart": "2026-09-07", "name": "Tên nhánh mẫu"}],
  "warnings": [{"row": 7, "message": "Chủ đề không khớp với kế hoạch năm"}]
}
```

`weekStart` luôn từ lịch server. Ngày/số tuần/đợt nghỉ/người ký file không là request dữ liệu cập nhật.
Không lưu upload, không ghi DB, không lấy khóa ghi và không tạo bản nháp trong endpoint này.

## File và diễn giải

.doc nhị phân/.doc HTML/.docx/.xlsx/.csv, <=2 MB/2.000 dòng, yêu cầu bảng có cột Chủ đề/Chủ đề nhánh.
OLE2 phải đọc được bảng/ô gộp; file hỏng, mã hóa hoặc không hỗ trợ bị từ chối; không chạy macro.
Chuẩn hóa tên chủ đề theo Unicode/khoảng trắng/hoa thường, giữ dấu; chủ đề mơ hồ/không khớp và nhánh thừa vào warnings; không fuzzy/cross-theme.
Chỉ các tuần khớp nhận patches; thiếu để trống nếu đang trống, giữ tên sẵn ở key ngoài matches.

## Lỗi

Giữ lỗi phân quyền/phân công/kế hoạch/phiên bản/trạng thái hiện có, xử lý qua GlobalExceptionHandler.
Thiếu part/tham số hoặc không parse được tham số theo validation contract hiện có.
Thêm ApiCode `BRANCH_FILE_UNSUPPORTED` 7124, `BRANCH_FILE_NO_TABLE` 7125, `BRANCH_FILE_TOO_LARGE` 7126, HTTP 400; đã kiểm ApiCode.java hiện kết thúc 71xx tại 7123, recheck trùng trước code.
7124: file không hỗ trợ/không đọc được/mã hóa; 7125: không có bảng Chủ đề/Chủ đề nhánh; 7126: quá 2 MB/2.000 dòng. Không dùng lời lỗi Mục tiêu/Nội dung của 7034. Ghi snapshot/ERROR_CONTRACT cùng test.
Đổi lời 7033 bỏ chỉ dẫn bắt chuyển .doc cũ; giữ mã và status hiện tại. Mục tiêu 7034/7035 giữ nguyên.

## Endpoint mục tiêu và thao tác ghi

`POST /education-plans/goals/file-preview`: không đổi endpoint, request/response hoặc quyền; chỉ mở rộng OLE2 và lời hướng dẫn/lỗi tương ứng.
`PUT /education-plans/class-docs/branch/{docKey}` và actions giữ nguyên contract. Preview không cấp quyền ghi và không tự submit.

## Consumer impact

Web: educationPlanApi.js + educationPlanService.js thêm previewBranchFile; mock repository cùng facade nhận method; BranchPlanPage/ImportBranchFileModal hiển thị và áp dụng matches.
ImportGoalFileModal cập nhật hỗ trợ .doc; importGoalFile.mjs/merge mục tiêu giữ nguyên nghĩa.
ReviewActions thêm tùy chọn showSend mặc định false; chỉ BranchPlanPage bật, các caller khác giữ nguyên hành vi.
Mobile không triển khai tính năng/màn nhập này, không sửa repo App; API cũ không bị đổi shape.
