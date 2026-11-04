-- OBS-15 (Cần chốt 04/11/2026): Kiến thức kỹ năng theo MT chỉ còn Đạt / Chưa đạt. Kết quả "Cần cố gắng" đã ghi chuyển
-- sang Chưa đạt (chưa đạt mục tiêu); cột goals_effort giữ lại, luôn rỗng.
UPDATE daily_activity_results
SET goals_not_met = CASE
                        WHEN goals_not_met IS NULL OR goals_not_met = '' THEN goals_effort
                        ELSE goals_not_met || ',' || goals_effort
                    END,
    goals_effort = ''
WHERE goals_effort IS NOT NULL AND goals_effort <> '';
