-- EDU-54: Migration contract cho cấu hình người tạo/duyệt kế hoạch giáo dục.
-- Chuẩn hóa dữ liệu legacy và siết CHECK constraints chỉ còn 3 trạng thái chính thức:
-- DRAFT (Nháp) -> REVIEW (Duyệt/Đang duyệt) -> APPROVED (Ban hành).

-- 1. Bỏ constraints cũ trước khi chuẩn hóa dữ liệu
ALTER TABLE edu_year_plans DROP CONSTRAINT IF EXISTS ck_edu_year_plan_edit_session;
ALTER TABLE edu_year_plans DROP CONSTRAINT IF EXISTS ck_edu_year_plans_status;

-- Chuẩn hóa dữ liệu và khóa sửa edu_year_plans
UPDATE edu_year_plans SET status = 'REVIEW' WHERE status = 'PENDING_VP';
UPDATE edu_year_plans SET status = 'DRAFT' WHERE status IN ('REJECTED', 'EDITING');

-- Bản không phải DRAFT hoặc không đủ thông tin phiên sửa thì xóa lease
UPDATE edu_year_plans
SET edit_session_id = NULL, editing_by = NULL, editing_since = NULL, editing_at = NULL, editing_from = NULL
WHERE status <> 'DRAFT' OR edit_session_id IS NULL OR editing_by IS NULL;

-- Chuẩn hóa editing_from thành DRAFT cho bản nháp đang giữ khóa
UPDATE edu_year_plans
SET editing_from = 'DRAFT'
WHERE edit_session_id IS NOT NULL;

-- Trigger tự động chuẩn hóa trạng thái legacy cho edu_year_plans nếu có caller cũ ghi
CREATE OR REPLACE FUNCTION fn_normalize_edu_year_plan_status()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'PENDING_VP' THEN
        NEW.status := 'REVIEW';
    ELSIF NEW.status IN ('REJECTED', 'EDITING') THEN
        NEW.status := 'DRAFT';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_normalize_edu_year_plan_status ON edu_year_plans;
CREATE TRIGGER trg_normalize_edu_year_plan_status
    BEFORE INSERT OR UPDATE OF status ON edu_year_plans
    FOR EACH ROW
    EXECUTE FUNCTION fn_normalize_edu_year_plan_status();

-- Siết CHECK constraint cho edu_year_plans
ALTER TABLE edu_year_plans
    ADD CONSTRAINT ck_edu_year_plans_status
        CHECK (status IN ('DRAFT', 'REVIEW', 'APPROVED'));

-- Cập nhật ràng buộc phiên sửa: chỉ DRAFT mới được giữ author lease; REVIEW/APPROVED tuyệt đối không có lease
ALTER TABLE edu_year_plans
    ADD CONSTRAINT ck_edu_year_plan_edit_session CHECK (
        (edit_session_id IS NULL AND editing_by IS NULL AND editing_since IS NULL AND editing_at IS NULL AND editing_from IS NULL)
        OR (edit_session_id IS NOT NULL AND editing_by IS NOT NULL AND editing_since IS NOT NULL AND editing_at IS NOT NULL AND editing_from IS NOT NULL AND editing_from = 'DRAFT' AND status = 'DRAFT')
    );

-- 2. Chuẩn hóa dữ liệu edu_class_docs
ALTER TABLE edu_class_docs DROP CONSTRAINT IF EXISTS ck_edu_class_docs_status;

UPDATE edu_class_docs SET status = 'REVIEW' WHERE status IN ('PENDING_TL', 'PENDING_VP', 'PENDING_HT');
UPDATE edu_class_docs SET status = 'DRAFT' WHERE status = 'REJECTED';

-- Trigger tự động chuẩn hóa trạng thái legacy cho edu_class_docs (bảo đảm tương thích caller module ngoài như observation)
CREATE OR REPLACE FUNCTION fn_normalize_edu_class_doc_status()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('PENDING_TL', 'PENDING_VP', 'PENDING_HT') THEN
        NEW.status := 'REVIEW';
    ELSIF NEW.status IN ('REJECTED', 'EDITING') THEN
        NEW.status := 'DRAFT';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_normalize_edu_class_doc_status ON edu_class_docs;
CREATE TRIGGER trg_normalize_edu_class_doc_status
    BEFORE INSERT OR UPDATE OF status ON edu_class_docs
    FOR EACH ROW
    EXECUTE FUNCTION fn_normalize_edu_class_doc_status();

-- Siết CHECK constraint cho edu_class_docs
ALTER TABLE edu_class_docs
    ADD CONSTRAINT ck_edu_class_docs_status
        CHECK (status IN ('DRAFT', 'REVIEW', 'APPROVED'));
