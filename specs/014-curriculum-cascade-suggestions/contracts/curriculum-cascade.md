# Interface contract — feature014

## API hiện có, giữ nguyên

| Interface | Producer | Consumer |
| --- | --- | --- |
| GET /education-plans/curriculum/sources | EduCurriculumController | educationPlanApi → educationPlanService → useCurriculum |
| GET /education-plans/curriculum/goals?ageGroupId= | EduCurriculumService getGoals | CurriculumGoalsModal và GoalTable editable; source nhóm/mục tiêu/nội dung D |
| GET /education-plans/curriculum/contents?ageGroupId= | EduCurriculumService getContents | Modal mục C hiện có; không có group relation |
| GET/PUT /education-plans/goals/{planId} | EduYearPlanController | yearPlanMapper / GoalFormPage; payload nguồn và lưu tay giữ nguyên |

Không đổi method/path/query/response envelope/DTO/ApiCode hoặc auth. UI ageGroupCode chuyển qua refs hiện có trước gọi ageGroupId. Service facade giữ shape; mock adapter phải enrich source identity còn thiếu theo research R7, không sửa raw seed để ép test. IDs mock-only ổn định, không được gửi API thật; test parity nguồn/cha và mock save/reload.

## Contract state và thao tác modal

- Thứ tự điều khiển: lĩnh vực → lĩnh vực con → kết quả mong đợi → mục tiêu đích. ALL/NONE phân biệt đúng dữ liệu (xem [data model](../data-model.md)).
- Lọc đích bằng actual local tree. Lọc nguồn bằng source relation; không coi target từng chọn là bộ lọc ngầm ưu tiên hơn lựa chọn cha hiện tại.
- Đổi bộ lọc chỉ reconcile selection tạm; mọi nội dung đã thêm, nhãn và dấu tích giữ nguyên.
- Nút thêm enabled chỉ khi target còn eligible và có nội dung eligible. Confirm kiểm lại cùng predicate trên catalog/form mới nhất trước gọi callback, kể cả target bị xóa/chuyển trong form.
- Mục C độc lập: lọc nguồn theo domain/sub; group filter chỉ thu hẹp mục tiêu đích, không giả ánh xạ C→group. Tick bị ẩn vì scope nguồn đổi không được gửi.
- Chỉ xác nhận mới áp vào bản soạn; callback không bypass editable/lease/revision/canEditCurriculum.

## Contract inline gợi ý

| Ô đang focus | Cấp nguồn cần có | Danh sách | Khi chọn |
| --- | --- | --- | --- |
| Kết quả mong đợi | domain/sub nguồn hợp lệ, có thể NONE | Nhóm source đúng nhánh | Điền text + curriculumExpectedResultId/curriculumText của dòng nhóm |
| Mục tiêu | source expectedResultId hợp lệ | Goal thuộc nhóm | Điền text + curriculumGoalId/goalText/sectionRef theo mapper hiện có |
| Nội dung | source expectedResultId hợp lệ | Contents của mọi goal trong nhóm | Điền/thêm đúng ô, không tự đổi mục tiêu hoặc tick |

- Chọn group/goal không tự tạo hàng con; dùng lại thao tác thêm/xóa/chỉnh hiện có.
- Dữ liệu nhập tay chưa linked: hướng dẫn và cho chọn source context tường minh trong vùng gợi ý; state tạm không tự đổi cha hoặc ghi provenance lên dòng chưa chọn gợi ý.
- Focus mở ngay dù query rỗng, gõ chỉ lọc trong scope; đổi query không mở rộng nhánh. Query không mutate dòng khác.
- Không có kết quả/thiếu cha/đang tải/lỗi hiển thị tách biệt; retry không mất bản soạn.
- Gợi ý object có key ổn định; truy cập được toàn bộ kết quả khớp, không cắt12 vĩnh viễn.
- Bàn phím: focus bằng Tab, Up/Down chọn option, Enter chỉ chọn suggestion khi option active và không đồng thời chèn dòng; Escape đóng và giữ text; Enter thường/Shift+Enter/IME giữ convention bảng khi không chọn suggestion. Touch/pointer chọn được, blur portal không làm mất lựa chọn trước commit.
- Combobox/listbox có nhãn, aria-expanded/controls/active-descendant phù hợp; focus ring rõ. Tái dùng token/FloatingPanel, không thêm thư viện.

## Quyền và consumer impact

GoalTable readOnly/fixedCells hoặc canEditCurriculum=false không có mutation và không load catalog chỉ vì render bảng. TEAM source tree vẫn fixed; tick chủ đề theo quyền cũ giữ nguyên. Backend vẫn kiểm quyền/session/revision/immutable source.

Consumer Web cụ thể: CurriculumGoalsModal, GoalTables, GoalFormPage, useCurriculum, curriculum.mjs; mapper/API/mock giữ payload. Các source page vẫn dùng hook với default enabled cũ.

APP sibling không có trong workspace; chưa kiểm source consumer Mobile. Không có transport delta buộc Mobile đổi; không khẳng định Mobile không dùng API. Nếu có yêu cầu UI Mobile, phải khảo sát và có quyền riêng.

## Acceptance mapping

US1/FR001–005 → AC01–02; US2/FR006–008/010 → AC03–06; US3/FR009/011–012 → AC06–08. Các ca kiểm cụ thể tại [quickstart](../quickstart.md).
