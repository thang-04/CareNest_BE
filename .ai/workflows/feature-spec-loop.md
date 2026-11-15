# Workflow — Feature mới theo Domain spec và vòng lặp Spec Kit

## Nguồn và phạm vi

- User yêu cầu ngày 01/11/2026: feature mới làm theo `QuyTrinhLamViec.docx` (tài liệu môn SDD-ADD do user giữ).
- Nội dung quy trình được lưu ở đây để không phụ thuộc file ngoài repo. Mẫu Domain spec: [DOMAIN_SPEC_TEMPLATE.md](../../docs/business/DOMAIN_SPEC_TEMPLATE.md).
- User xác nhận ngày 01/11/2026: giữ stack, kiến trúc và convention CareNest; Java 21/Spring Boot/PostgreSQL 17. MySQL và các lựa chọn kiến trúc trong tài liệu nguồn là ví dụ, không phải yêu cầu chuyển stack.
- **Mức áp dụng theo làn (user chốt 11/11/2026):**
  - Làn **L**: đủ 12 bước dưới đây.
  - Làn **M**: một file `specs/<feature>/spec.md` rút gọn (rule ID áp dụng, acceptance Given/When/Then, thay đổi data/contract nếu có) → implement → verify. Không cần constitution/plan/tasks/analyze/converge riêng.
  - Làn **S**: 3–5 dòng acceptance trong chat rồi làm; không tạo file spec.
  - Sửa bug/refactor thuần kỹ thuật theo workflow tương ứng; phát sinh nghiệp vụ mới thì áp mức theo làn cho phần đó.
- Skill `speckit-*` (làn L) không nằm trong repo: cài Spec Kit theo hướng dẫn của công cụ đang dùng; chưa cài ⇒ làm tay theo đúng thứ tự 12 bước và ghi rõ trong báo cáo.
- Giữ các cổng hiện có của [AGENTS.md](../../AGENTS.md): làm rõ nghiệp vụ, plan làn L do user duyệt trước code, quyền sửa liên repo, dependency/contract, verify và không tự commit/push/tạo branch.

## Trình tự bắt buộc (làn L)

1. **Domain spec**: trong `specs/<feature>/domain-spec.md`, đi đủ 14 mục của mẫu; đọc module card, rule/flow hiện có; phân biệt bước người dùng ngoài hệ thống với bước hệ thống. Không dùng ví dụ trong mẫu làm business rule.
2. **speckit-constitution**: kiểm tra `.specify/memory/constitution.md` với nguyên tắc CareNest. Tái sử dụng constitution hợp lệ; nếu còn template hoặc cần sửa thì làm rõ, cập nhật theo skill, không tự thay stack hoặc làm mất nguyên tắc đã chốt.
3. **speckit-specify**: tạo/cập nhật `spec.md`, có user stories, functional requirements, acceptance scenarios, success criteria và scope; ghi đường dẫn feature đang làm theo cơ chế Spec Kit hiện có.
4. **speckit-clarify**: hỏi ngay các điểm mơ hồ, ghi câu trả lời và nguồn vào spec/tri thức; lặp đến khi hết câu hỏi nghiệp vụ chặn triển khai. Không tự chọn một business rule chưa chốt.
5. **speckit-plan**: lập `plan.md`, research, contract và cách kiểm chứng; nếu làn L, tạo/cập nhật plan quản trị tại `docs/plans/active/` theo [plan-change.md](plan-change.md), trình user duyệt trước code.
6. **Thiết kế database**: ghi `data-model.md` sau thiết kế tổng thể; xác định concept, quan hệ, ràng buộc, dữ liệu dẫn xuất, lifecycle và migration/backfill khi cần. Feature không cần đổi DB vẫn ghi rõ dùng lại dữ liệu nào và vì sao không migration; không tạo bảng cho đủ bước.
7. **speckit-tasks**: từ spec/plan/data model/contract tạo `tasks.md` theo thứ tự phụ thuộc, có test cho logic không hiển nhiên, trace về yêu cầu/AC. Giữ lịch sử task đã có khi bổ sung.
8. **speckit-analyze**: kiểm tra chéo domain spec, constitution, spec, plan, data model, contract và tasks trước code. Có lỗi/mâu thuẫn/thiếu coverage thì sửa artifact đúng nguyên nhân, cập nhật tasks khi cần và analyze lại; không sửa code để che lỗi đặc tả. Hết lỗi chặn và đủ các phê duyệt hiện hành mới implement.
9. **speckit-implement**: làm các task đã thiết kế, đánh dấu theo bằng chứng; chạy test và verify theo làn. Với làn L, thực hiện và dừng review từng phase như plan được duyệt.
10. **speckit-converge**: chỉ chạy sau implement; so code hiện tại với spec/plan/tasks để tìm phần chưa làm hoặc còn thiếu. Chỉ append task mới có truy vết vào cuối `tasks.md`; không xóa, viết lại, đổi số hoặc mất trạng thái task cũ.
11. **Lặp implement → converge**: implement các task được append, verify rồi converge lại đến khi không còn gap. Khi sạch, converge không được sửa `tasks.md`, kể cả thêm tiêu đề rỗng. Nếu phát hiện cần đổi quyết định/spec/plan thì hỏi và cập nhật theo workflow trước, không tự mở rộng scope.
12. **Bàn giao**: ghi kết quả analyze/converge, task hoàn thành, output verify thật và giới hạn vào quickstart/Progress log theo [VERIFICATION.md](../../docs/quality/VERIFICATION.md); không tuyên bố sạch khi còn gap hoặc test fail/skip chưa được nêu rõ.

## Bằng chứng cần giữ

- `domain-spec.md`: đủ 14 mục, nguồn, câu hỏi và quyết định nghiệp vụ.
- `spec.md`/`plan.md`/`data-model.md`/`contracts/`/`tasks.md`: khớp nhau, không dùng placeholder hoặc PENDING như đã chốt.
- Analyze: phát hiện, cách xử lý, lần kiểm lại; không gán PASS khi còn lỗi chặn.
- Converge: vòng, gap → task ID mới, kết quả; giữ lịch sử cũ và có vòng cuối không còn gap.
- Verify: lệnh, kết quả thực tế, phần chưa chạy. Kiểm tra tài liệu/AI layer không thay cho test ứng dụng.
