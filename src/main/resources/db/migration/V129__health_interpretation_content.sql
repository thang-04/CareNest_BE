-- Giải thích xu hướng sức khỏe có cấu trúc (HLT-21): tổng quan, điểm cần chú ý gắn mã cảnh báo, điểm tích cực,
-- gợi ý theo dõi, giới hạn dữ liệu. summary giữ bản văn ghép từ content; bản ghi trước V129 có content NULL.
ALTER TABLE health_interpretations ADD COLUMN content JSONB;
