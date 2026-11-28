-- Gỡ luồng AI gợi ý thực đơn: bỏ nháp gợi ý và vector món chỉ luồng này dùng. Giữ extension vector cho RAG kế
-- hoạch giáo dục (V194). Bật lại tính năng phải tạo bảng bằng migration mới, dữ liệu cũ không khôi phục.
DROP TABLE IF EXISTS menu_ai_drafts;
DROP TABLE IF EXISTS dish_embeddings;
