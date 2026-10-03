# Workflow — Thay đổi database

1. **Ownership & scope:** bảng thuộc module nào (module card); campus-scoped cần `campus_id`; dữ liệu nhạy cảm; dữ liệu dẫn xuất bị ảnh hưởng. Theo `docs/database/DATABASE.md`.
2. **Entity:** JPA entity trong `<module>.infrastructure`/`domain`; tham chiếu module khác bằng ID; theo `.claude/rules/database.md`.
3. **Migration:** dùng migration tool của project (kiểm tra source; nếu chưa chọn ⇒ hỏi, không tự chọn). Migration mới, không sửa migration đã chạy. Có đường nâng cấp cho dữ liệu cũ.
4. **Data dictionary:** cập nhật `docs/database/DATA_DICTIONARY.md` (và `ERD.md` khi đổi quan hệ) trong cùng thay đổi.
5. **Impact:** API/DTO, query của module khác (qua api), reporting view.
6. **Test:** integration test với PostgreSQL thật (Testcontainers) cho migration + query; scope filter.
7. Báo rủi ro dữ liệu và cách rollback nếu có thay đổi phá vỡ.
