-- Suất thay thế nằm trong số trẻ ăn (Cần chốt 04/11/2026): 20 trẻ có mặt, 1 trẻ ăn thay thế = 20 suất (19 thường + 1
-- thay thế). Trước đây suất thường = số trẻ ăn và suất thay thế cộng thêm; chuyển số cũ về cách tính mới.
UPDATE meal_count_lines
SET substitute = LEAST(substitute, normal),
    normal     = normal - LEAST(substitute, normal)
WHERE substitute > 0;
