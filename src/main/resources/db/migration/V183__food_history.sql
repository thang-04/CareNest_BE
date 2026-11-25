-- Lịch sử thay đổi thông tin thực phẩm: mỗi dòng là một trường đổi (giá trị trước/sau); chỉ thêm, không sửa.
-- Thay đổi giá và nhà cung cấp nằm ở food_prices, được gộp khi đọc lịch sử.
CREATE TABLE food_history
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    food_id    BIGINT       NOT NULL REFERENCES foods (id) ON DELETE CASCADE,
    user_id    BIGINT REFERENCES user_accounts (id),
    changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    action     VARCHAR(30)  NOT NULL,
    field      VARCHAR(60),
    old_value  TEXT,
    new_value  TEXT
);

CREATE INDEX idx_food_history_food ON food_history (food_id, changed_at);
