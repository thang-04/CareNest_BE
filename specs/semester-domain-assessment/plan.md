# Technical plan
BE dùng PeriodGoalResults chung kỳ/năm, JPA entity SemesterAssessment, bảng additive, repository scope qua service. Kết quả đếm ở BE duy nhất. Board semester trả tên năm và khoảng ngày thực lấy SchoolYearService.
FE dùng facade/useAsync, useThemeClasses cùng scope, bảng hai header theo mẫu, Modal mục tiêu, bản in HTML/XLSX từ cùng response. Giữ route semester detail cũ để xem lịch sử; dashboard đổi link vào bảng mới cùng kỳ.
Rủi ro: scope thay khi đang lưu → request scope guard; năm đổi qua header → guarded action. Chuyển lớp → PeriodGoalResults xét lớp cũ cùng năm/nhóm tuổi và ngày thuộc đoạn học, gắn tên lớp nguồn trên phiếu năm.
Security: role guard controller + service assignment/year/scope. Không thay token/auth. Nối SemesterUsageCheck để cấu hình không đổi ngày bảng đã lưu.
Plan quản trị: docs/plans/active/2026-11-05-semester-domain-assessment.md.
