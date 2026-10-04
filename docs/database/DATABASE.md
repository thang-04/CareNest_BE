# Database

Nguồn chuẩn: `docs/backend-coding-guide.md` mục 12. PostgreSQL 17, Spring Data JPA (Hibernate 7, `ddl-auto: validate`), **Flyway** (`src/main/resources/db/migration`, tên `V{n}__{mo_ta_ngan}.sql`).

## Nguyên tắc (guide + bổ sung nghiệp vụ)

1. Mọi thay đổi schema qua migration Flyway mới; không sửa migration đã chạy ở môi trường dùng chung.
2. Bảng/cột `snake_case`. Ràng buộc nghiệp vụ quan trọng có ràng buộc DB (unique, FK, not null) — vd. unique điểm danh theo trẻ/ngày.
3. **Ownership:** mỗi bảng thuộc 1 feature (`docs/modules/`); chỉ service của feature đó ghi.
4. **Campus scope:** dữ liệu campus-scoped có cột campus; không thêm school/tenant vào mọi bảng (1 trường — ADR-0002).
5. Dữ liệu có hiệu lực theo thời gian (phân công, lớp của trẻ): khoảng từ–đến để tra theo ngày.
6. Bản ghi do AI tạo: nguồn (`AI`/thủ công/template), model, trạng thái nháp/đã duyệt, người duyệt, thời điểm.
7. Không lưu binary trong bảng nghiệp vụ; ảnh/tệp qua `StorageService`, bảng giữ metadata (`MediaAsset`).
8. pgvector chỉ khi làm semantic search/RAG, migration bật extension tách riêng.
9. Kiểu ID, cột audit chuẩn, tiền tố bảng: **chưa chốt** — theo migration đầu tiên của nhóm, rồi ghi lại tại đây.

## Tài liệu liên quan

ERD: `ERD.md` (SKELETON) · Data dictionary: `DATA_DICTIONARY.md` (SKELETON) · Entity khái niệm: `docs/business/DOMAIN_MODEL.md` · Quy trình: `.ai/workflows/update-database.md`.
