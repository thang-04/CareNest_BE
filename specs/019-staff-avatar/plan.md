# Plan kỹ thuật — Ảnh đại diện nhân sự (019)

Plan quản trị (duyệt, phase, Progress log): [docs/plans/active/2026-11-14-staff-avatar.md](../../docs/plans/active/2026-11-14-staff-avatar.md). Spec: [spec.md](spec.md) · data model: [data-model.md](data-model.md) · contract: [contracts/account-photo-api.md](contracts/account-photo-api.md) · tasks: [tasks.md](tasks.md).

- Không migration, không dependency mới; lưu tệp qua `FileStorage` loại `USER_AVATAR` (spec 017).
- Luật xem gom một chỗ (`AccountPhotoAccessPolicy`) để App phase sau dùng lại.
- Lấy link hàng loạt thay vì gắn `photoUrl` vào từng DTO: không đổi contract các API danh sách, link 15 phút do client tự xin lại.
- Kiểm chứng: `mvnw.cmd -q -Dtest='AccountPhoto*Test,ParentPhoto*Test' test` rồi `mvnw.cmd -q test` toàn bộ trước PR.
