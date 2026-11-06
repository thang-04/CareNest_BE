# ADR-0024 — Policy tạo/duyệt riêng cho kế hoạch giáo dục

- Status: IMPLEMENTED (Đã triển khai và kiểm chứng toàn diện BE + FE 06/11/2026)
- Date: 2026-11-06
- Liên quan: EDU-54 (PROPOSED), ADR-0020, ADR-0017, AUTH-20/23; [feature011](../../specs/011-plan-creator-reviewer/plan.md)

## Bối cảnh

User yêu cầu chỉ HT cấu hình vai trò soạn và duyệt theo loại hồ sơ giáo dục, không phân quyền global/từng tài khoản. Year đang hardcode TL/VP, class dùng signers snapshot; thiếu contributor history và chính sách đổi chuỗi pending mới. Quyền global ở ADR-0017 không phải nơi đặt thứ tự duyệt hồ sơ.

## Quyết định đề xuất

- Dùng local education policy/revision trong settings, engine chung year/branch/theme, giữ module/layers hiện có.
- Chủ thể quản lý policy = HT hiệu lực, không chuyển qua role_permissions/overrides để PHT tự cấu hình. Source role gates eduplan giữ ngoại lệ EDU-08/AUTH-20; scope thực tế do server policy/assignment kiểm.
- JSON approvals cùng round + ledger người soạn có FK. Không tái tạo lịch sử không có bằng chứng; legacy creator baseline là lựa chọn user.
- Policy save atomically reconcile pending và giữ completed; không workflow queue/job eventual consistency. Shared/exclusive settings locks cùng deterministic document lock ordering.
- Endpoint workflow riêng, legacy full-config write không được sửa policy. Completion do đổi policy ghi event riêng, không fake human approval.

## Phương án đã cân nhắc

| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Local policy + fields nhỏ + ledger | Khớp ba loại hồ sơ, tái dùng code/data | Phải đồng bộ year/class/clients và concurrency |
| Dùng ma trận global làm chuỗi duyệt | Tái dùng bảng quyền | Ma trận không thể hiện thứ tự/vòng hồ sơ, vượt scope user |
| Workflow instance/step platform tổng quát | Linh hoạt nhiều domain | Nhiều bảng/lifecycle và dependency không cần cho V1 |

## Hệ quả

Migration mới và API/enum semantics phải rollout phối hợp Web; APP consumer chưa xác minh. Year reviewer không còn được sửa pending. Sau chỉ đạo user chỉ giáo dục, không sửa adapter/source observation: publisher thật dùng published_by/completed_at trong edu, issued_by legacy projection chỉ actual VP approval hoặc null; final status APPROVED giữ nguyên. User06/11 sau lệnh fix cho phép riêng đúng helper AccountService.findActiveUserIds chỉ đọc và test, không endpoint/quyền/nghiệp vụ tài khoản; không mở rộng source module khác. Plan >8 file tách phase, review/gates mỗi phase.

## Trạng thái và giới hạn còn lại

User authorized implementation by invoking `$speckit-implement` and subsequent scope decisions. Implementation evidence is tracked in feature011; status IMPLEMENTED does not mean released or deployed. Rollout still requires coordinated BE+Web release and a normal release decision; APP remains outside current scope because user confirmed no education-plan integration yet. EDU-54 remains PROPOSED until the responsible school/team adopts it. Do not create architecture based on examples from another stack.


## Delta sau analyze06/11/2026

User chốt ba trạng thái DRAFT/REVIEW/APPROVED, REVIEW không sửa nội dung/lease, final approve tự Ban hành; return để sửa về DRAFT. Chỉ module giáo dục. Policy update xử lý cả REVIEW và DRAFT-held-leases mất quyền. User chốt print_snapshot gồm slot role/label/names lưu khi Ban hành, không dựng lại từ config mới. Thiết kế vẫn PROPOSED, cần review delta; dependency ACTIVE-account đã có helper read-only user cho phép, implementation vẫn cần duyệt plan.


## Sửa analyze I2/U3/I3 — 06/11/2026
Expand/contract là hai migration mới trong cùng release, không deploy giai đoạn chuyển tiếp. Expand P1 giữ legacy enums/CHECK/caller compatibility và thêm REVIEW, full verify/snapshot T014 thực thi được. T047 sau khi caller/test chuyển xong mới bỏ legacy constants và contract migration normalize/siết ba giá trị; T051 full cuối. User cho phép đúng helper đọc ACTIVE-ID của account dùng repository.findAllById hiện có, không nghiệp vụ/HTTP/permission mới. Bản in duy nhất dùng review.printSnapshot. Giữ PROPOSED, chưa code hoặc approve plan.

Implementation06/11: user đã gọi speckit-implement và cho phép riêng helper StaffAssignmentService.findActiveEducationReviewerAssignments(LocalDate) chỉ đọc cùng test để enumerate HT/PHT/Tổ trưởng; không HTTP hoặc đổi nghiệp vụ/quyền tổ chức. User tiếp tục cho phép triển khai toàn bộ phase kể cả FE. Toàn bộ thiết kế đã được hiện thực hóa và kiểm chứng thành công: Flyway V80 expand + V81 contract siết 3 trạng thái DRAFT/REVIEW/APPROVED; BE 1,104 tests GREEN, FE 199 tests GREEN. Sổ cái edu_document_authors ngăn chặn tự duyệt; print_snapshot lưu trữ ô ký bất biến lúc ban hành.

Final verification 06/11/2026: BE full verify 1129 tests, fail0/error0/skip0, ArchUnit/OpenAPI snapshot/Spotless PASS; FE verify 207/207 education tests plus lint/format/design/API/build PASS; real-browser WF-01/02/03 3/3 PASS. No release/deploy was performed.
