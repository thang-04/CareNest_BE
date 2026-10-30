-- NUT-02 (Cần chốt 27/10/2026): HT cũng xác nhận được sổ báo ăn; lưu vai trò người xác nhận (HT hay PHT) lúc xác nhận.
ALTER TABLE meal_counts ADD COLUMN confirmed_by_role VARCHAR(20);

ALTER TABLE meal_counts
    ADD CONSTRAINT ck_meal_counts_confirmed_by_role
        CHECK (confirmed_by_role IS NULL OR confirmed_by_role IN ('PRINCIPAL', 'VICE_PRINCIPAL'));

-- Sổ đã xác nhận trước đây chỉ do PHT điểm trường xác nhận
UPDATE meal_counts SET confirmed_by_role = 'VICE_PRINCIPAL' WHERE confirmed_by IS NOT NULL;
