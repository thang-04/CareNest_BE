# Contract — POST /education-plans/goals/file-preview (additive)

User approved 02/11/2026. Request giữ multipart file, không thêm header/param bắt buộc. Response giữ envelope và domains/themeAbbrs/warnings. Thêm:

- metadata: null hoặc {title:string|null, schoolYear:string|null (yyyy-yyyy), ageFromMonths:integer|null, ageToMonths:integer|null}.
- themes: [{name:string, abbr:string, festivals:string, start:ISO-date|null, end:ISO-date|null, note:string}].
- breaks: [{name:string, start:ISO-date, end:ISO-date|null, note:string}].

Không trả người ký/tài khoản/file raw; không ghi DB. Giữ mã7033/7034/7035 và7005 theo service/ApiCode cũ, không thêm mã. Metadata tuổi không tự cấp ID/quyền; BE save giữ kiểm scope.

Consumers: Web educationPlanApi.js → educationPlanService.js → ImportGoalFileModal.jsx → GoalFormPage.jsx/importGoalFile.mjs. Fields mới optional với client cũ; old CSV chỉ metadata null/themes[]/breaks[]. Mobile chưa có consumer, không sửa APP. Snapshot docs/api/openapi.yaml phải phản ánh field mới. FE đối chiếu target tuổi với metadata trước xác nhận, giữ target year, dời ngày bằng helper hiện có, có cảnh báo lịch.

## Client apply contract mới nhất —02/11/2026
Hai chế độ thay nội dung chỉ ở Web, không thay request/response/schema của endpoint. additive ở tiêu đề contract chỉ mô tả mở rộng DTO tương thích cũ, không còn nghĩa mặc định nối thêm nội dung vào form.

Modal → GoalFormPage callback mang preview và mode đang chọn; page kiểm quyền/recovery/finishing/lease rồi helper tính patch với form mới nhất và yearRange config. Modal preview và helper apply phải dùng cùng mode/phần hiện có. Full replace editable fields, missing clear; present replace nonempty preview sections, missing keep. Không gửi mode vào API.

Năm học/start-end range hệ thống, fixed age, planId/owner/scope/status/history/version/revision/session không được chép từ file. Age trống vẫn theo quyền hiện có, khác tuổi chặn. Mô tả không có trong schema: full để trống, present giữ. Source year dùng dời lịch; range thiếu thì apply bị chặn, ngoài năm cảnh báo/validate như cũ.

Mọi tick phải trỏ ID chủ đề trong patch cuối; themes mới và domains giữ thì remap bằng mã duy nhất và báo tick không còn đích. File chỉ bảng mục tiêu: full không tận dụng chủ đề cũ để ghép tick; present giữ chủ đề thiếu trong file nên được dùng mã hiện có. Hủy/lỗi/mất quyền/late response không tạo patch một phần. Server vẫn chỉ ghi khi Lưu và kiểm các contract hiện hành.
