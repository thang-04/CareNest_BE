# Contract — POST /education-plans/goals/file-preview (additive)

User approved 02/11/2026. Request giữ multipart file, không thêm header/param bắt buộc. Response giữ envelope và domains/themeAbbrs/warnings. Thêm:

- metadata: null hoặc {title:string|null, schoolYear:string|null (yyyy-yyyy), ageFromMonths:integer|null, ageToMonths:integer|null}.
- themes: [{name:string, abbr:string, festivals:string, start:ISO-date|null, end:ISO-date|null, note:string}].
- breaks: [{name:string, start:ISO-date, end:ISO-date|null, note:string}].

Không trả người ký/tài khoản/file raw; không ghi DB. Giữ mã7033/7034/7035 và7005 theo service/ApiCode cũ, không thêm mã. Metadata tuổi không tự cấp ID/quyền; BE save giữ kiểm scope.

Consumers: Web educationPlanApi.js → educationPlanService.js → ImportGoalFileModal.jsx → GoalFormPage.jsx/importGoalFile.mjs. Fields mới optional với client cũ; old CSV chỉ metadata null/themes[]/breaks[]. Mobile chưa có consumer, không sửa APP. Snapshot docs/api/openapi.yaml phải phản ánh field mới. FE đối chiếu target tuổi với metadata trước xác nhận, giữ target year, dời ngày bằng helper hiện có, có cảnh báo lịch.
