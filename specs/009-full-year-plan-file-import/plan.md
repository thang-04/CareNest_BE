# Implementation plan — Feature009

Status: kế hoạch gốc được user duyệt02/11/2026; bổ sung hai chế độ thay nội dung được user duyệt trong cùng chat02/11/2026. Phase1 được user review bằng “duyệt”; logic hai chế độ FE đã implement/verify phase2, đang chờ review trước phase3/converge. Quản trị phase và review tại [plan gốc](../../docs/plans/active/2026-10-22-muc-tieu-nam-hoc.md). BE branch hiện tại G94-226; FE branch hiện có G94-178; không tạo branch.

## Technical context và constitution check
Java21/SpringBoot/POI hiện có; React18/Vite/JS/CSS token. Module education-plan; không DB/dependency/hạ tầng mới. Trả DTO không entity, giữ quyền/giới hạn, không log source. Constitution1.0.0 hợp lệ, không sửa. API additive được user duyệt; snapshot bắt buộc. Gates trước code: domain14/spec/clarify/plan/data-model/tasks/analyze hoàn tất; user đã duyệt.

## Design
- GoalFileGrid giữ API read hiện có cho caller branch/tests. Thêm đọc phần nhận diện văn bản đầu file an toàn dùng decoder hiện có, không lộ người ký; một interpreter mới phối hợp metadata/lịch với GoalFileTable.
- GoalFileTable sửa fidelity theo research, tests RED trước fix. GoalFilePreviewResponse thêm optional metadata và danh sách themes/breaks; constructor3tham số giữ tương thích Java tests/caller cũ.
- EduGoalFileServiceImpl dùng interpreter mới, giữ permission/transaction/log count và limits. Không thêm public service method hoặc endpoint.
- FE delta mới: helper pure cho full/present, dựng kết quả trước khi set form; reuse decoder/preview và ID/merge primitives khi phù hợp. Chế độ full dùng baseline phần editable rỗng; present chỉ thay các phần preview có dữ liệu. Remap tick theo mã nếu giữ cây nhưng thay chủ đề; không sửa flow lấy từ kế hoạch khác. Modal có radio mode và preview phần thay/xóa/giữ, dùng guard đọc file/apply hiện có. GoalFormPage truyền mode và yearRange hệ thống thật vào preview/apply, chưa có range thì báo và khóa apply; save/lease/recovery không đổi.

## Phases
1. Design: các artifact tại feature009, analyze và duyệt đã nhận.
2. Backend: synthetic regression + parser/DTO/service + snapshot/docs + full verify + review độc lập; dừng user review.
3. Frontend: tests merge/age/year/race + modal/layout + browser thật/real-BE E2E + verify, implement/converge đến hết gap; dừng review.

## Validation
Mapping AC/tests trong tasks. Không dùng số đếm parser cũ làm oracle. Mẫu thật chỉ ở máy user; fixtures tổng hợp. Không mở rộng các file nội dung khác hoặc giảm kiểm tra để pass. Tasks phase3 để mở khi phase2 bàn giao.

## Delta hai chế độ — user approved02/11/2026
- Main plan nghiệp vụ nằm ở đây cùng plan quản trị BE; client plan: FE:docs/plans/active/2026-11-02-year-plan-file-replacement.md. Permission mới chỉ sửa tài liệu BE, không production BE.
- Constitution1.0.0 được đọc/kiểm lại, không cần sửa. Feature path đã trỏ spec009, resolve-template/check-prerequisites/setup-plan/setup-tasks thành công; không tạo branch, setup-plan không ghi đè plan cũ. Không có extensions.yml nên không hook mở branch/commit.
- Phase delta1: Domain14/spec/clarify/research/plan/data-model/contract/tasks, EDU-46/module/impact, analyze; check-ai-layer BE/FE, dừng review theo plan đã duyệt.
- Phase delta2 sau review: RED model/modal tests theo FR06–09/12; implement mode, protected-fields, yearRange, remap; update old additive tests theo spec mới, không xóa hồi quy lỗi; browser cả hai mode/race/cancel/save/discard và full FE verify, dừng review.
- Phase delta3: implement/converge theo tasks append-only, bàn giao evidence/giới hạn. Không gọi converge trước implementation hoặc coi report659test backend cũ chứng minh mode Web mới.
- Không schema/dependency/endpoint/DTO mới; public payload preview/save giữ nguyên. Description vắng trong DTO, full clear/present keep. Null/empty arrays coi thiếu trong present; file không có nội dung hợp lệ khóa apply.
- NFR/stack giống plan gốc; scope chỉ education-plan Web. Tests/trace active delta trong T012..T020, AC1..10. Các task cũ giữ nguyên lịch sử, phần preservation cũ đã superseded rõ ở cuối tasks.

## Progress — delta phase2 sẵn sàng review02/11/2026
User duyệt phase1; triển khai T013..17. Evidence/cases/limits ở quickstart/tasks; full FE369test đạt sau source cuối, browser fake đạt hai mode desktop/mobile. Môi trường E2E BE thiếu E2E_DEV_PASSWORD nên chưa kiểm save/reopen thật, dù listener có sẵn. T018..20 còn mở và không tự chạy convergence trước review phase2. Giữ API/DB/production BE và task history.
