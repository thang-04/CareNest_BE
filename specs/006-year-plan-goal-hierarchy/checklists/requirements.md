# Specification Quality Checklist
**Created**: 2026-10-29 | **Feature**: [spec.md](../spec.md)
- [x] Phạm vi và nguồn user rõ; ba bảng là yêu cầu rõ ràng từ user.
- [x] User stories có acceptance và kiểm độc lập.
- [x] FR-001..008 và SC-001..004 kiểm chứng được.
- [x] Thiếu cấp cha, chuyển cha, ID, lineage và dữ liệu cũ được mô tả.
- [x] Không còn câu hỏi nghiệp vụ mở; không tự thêm tiêu đề giả.
- [x] Không mở rộng quyền, trạng thái, API hay repo client.

## Delta02/11
- [x] Domain14 và nguồn user/EDU-48 đầy đủ.
- [x] US4/US5,7FR/4SC mới có AC và task riêng; không dùng evidence29/10 thay delta.
- [x] Giữ cha tùy chọn/PUT chuyển cha có chủ đích; không API/schema/dependency mới.
- [x] Hủy/counts/read-only/ticks/nhánh rỗng/cùng tên khác ID/ellipsis/value/filter có kiểm.
- [x] User đã chọn “Duyệt kế hoạch và triển khai tới khi sạch”02/11 trước code.
- [x] Implement/verify/converge delta hoàn thành; real-BE API-only E2E1/1 và cleanup0 có evidence, không còn gap.
