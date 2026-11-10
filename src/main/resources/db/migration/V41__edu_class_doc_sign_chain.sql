-- EDU-18: chuỗi người duyệt chụp lúc gửi hồ sơ lớp (mảng role theo thứ tự). Đổi cấu hình chuỗi giữa chừng không làm
-- hồ sơ đang chờ nhảy bước hoặc tự duyệt. NULL = hồ sơ gửi trước khi có cột (service dựng lại từ cấu hình + người đã duyệt).
ALTER TABLE edu_class_docs ADD COLUMN sign_chain JSONB;
ALTER TABLE edu_class_docs
    ADD CONSTRAINT ck_edu_class_docs_sign_chain CHECK (sign_chain IS NULL OR jsonb_typeof(sign_chain) = 'array');
