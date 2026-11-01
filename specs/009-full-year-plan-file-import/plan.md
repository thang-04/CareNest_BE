# Implementation plan — Feature009

Status: approved by user 02/11/2026 ("chốt"). Quản trị phase và review tại [plan gốc](../../docs/plans/active/2026-10-22-muc-tieu-nam-hoc.md). BE branch hiện tại G94-226; FE branch hiện có G94-178; không tạo branch.

## Technical context và constitution check
Java21/SpringBoot/POI hiện có; React18/Vite/JS/CSS token. Module education-plan; không DB/dependency/hạ tầng mới. Trả DTO không entity, giữ quyền/giới hạn, không log source. Constitution1.0.0 hợp lệ, không sửa. API additive được user duyệt; snapshot bắt buộc. Gates trước code: domain14/spec/clarify/plan/data-model/tasks/analyze hoàn tất; user đã duyệt.

## Design
- GoalFileGrid giữ API read hiện có cho caller branch/tests. Thêm đọc phần nhận diện văn bản đầu file an toàn dùng decoder hiện có, không lộ người ký; một interpreter mới phối hợp metadata/lịch với GoalFileTable.
- GoalFileTable sửa fidelity theo research, tests RED trước fix. GoalFilePreviewResponse thêm optional metadata và danh sách themes/breaks; constructor3tham số giữ tương thích Java tests/caller cũ.
- EduGoalFileServiceImpl dùng interpreter mới, giữ permission/transaction/log count và limits. Không thêm public service method hoặc endpoint.
- FE phase sau review BE: merge đầy đủ qua helper pure, dùng lại mergeImportedThemes/mergeFileGoals; modal giữ async guard và hiển thị metadata/lịch trước xác nhận; GoalFormPage đổi thanh nút và phần1 thành bảng nhập trực tiếp như phần2/3 (user bổ sung sau duyệt), không đổi save protocol.

## Phases
1. Design: các artifact tại feature009, analyze và duyệt đã nhận.
2. Backend: synthetic regression + parser/DTO/service + snapshot/docs + full verify + review độc lập; dừng user review.
3. Frontend: tests merge/age/year/race + modal/layout + browser thật/real-BE E2E + verify, implement/converge đến hết gap; dừng review.

## Validation
Mapping AC/tests trong tasks. Không dùng số đếm parser cũ làm oracle. Mẫu thật chỉ ở máy user; fixtures tổng hợp. Không mở rộng các file nội dung khác hoặc giảm kiểm tra để pass. Tasks phase3 để mở khi phase2 bàn giao.
