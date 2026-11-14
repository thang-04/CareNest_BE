-- Kiểm nhất quán bán trú (R__dev_03_kitchen.sql): mỗi dòng trả số vi phạm, đúng phải bằng 0.
WITH ctx AS (SELECT CURRENT_DATE AS today, CURRENT_DATE - (EXTRACT(ISODOW FROM CURRENT_DATE)::int - 1) AS monday),
school_days AS (
    SELECT d::date AS day FROM generate_series(DATE '2026-09-03', CURRENT_DATE - 1, INTERVAL '1 day') d
    WHERE EXTRACT(ISODOW FROM d) < 6
      AND NOT EXISTS (SELECT 1 FROM school_holidays h WHERE d::date BETWEEN h.start_date AND h.end_date)),
served AS (
    SELECT DISTINCT cl.campus_id, s.session
    FROM classrooms cl JOIN age_groups g ON g.id = cl.age_group_id
    CROSS JOIN LATERAL unnest(string_to_array(g.meal_sessions, ',')) s (session)
    WHERE cl.school_year_id = (SELECT id FROM school_years WHERE name = '2026-2027')),
-- Nhu cầu kho khô tính lại độc lập theo MealDemandCalculator: suất đã chốt × gam ÷ (1 − thải bỏ%), làm tròn 2
recomputed AS (
    SELECT p.campus_id, p.date, f.id AS food_id,
           CASE WHEN f.waste_pct <= 0 THEN round(sum(di.grams_per_child * p.portions), 2)
                ELSE round(round(sum(di.grams_per_child * p.portions) / (1 - f.waste_pct / 100), 10), 2) END AS qty
    FROM (SELECT mc.campus_id, mc.date, mc.session, cl.age_group_id, sum(l.normal + l.substitute) AS portions
          FROM meal_counts mc JOIN meal_count_lines l ON l.meal_count_id = mc.id AND NOT l.pending
          JOIN classrooms cl ON cl.id = l.classroom_id
          WHERE mc.status = 'CONFIRMED' GROUP BY 1, 2, 3, 4) p
    JOIN age_groups g ON g.id = p.age_group_id
    JOIN meal_plan_weeks w ON w.week_start = p.date - (EXTRACT(ISODOW FROM p.date)::int - 1) AND w.status = 'PUBLISHED'
    JOIN meal_plan_items i ON i.week_id = w.id AND i.menu_date = p.date AND i.session = p.session
        AND i.menu_block = CASE WHEN g.age_to_months <= 36 THEN 'NURSERY' ELSE 'KINDERGARTEN' END
    JOIN dish_ingredients di ON di.dish_id = i.dish_id AND di.age_group_id = p.age_group_id
    JOIN foods f ON f.id = di.food_id AND f.storage = 'DRY'
    WHERE p.portions > 0
    GROUP BY p.campus_id, p.date, f.id)
SELECT 'lot_balance_negative_at_any_time', count(*) FROM (
    SELECT sum(quantity) OVER (PARTITION BY lot_id ORDER BY created_at, id) AS bal FROM stock_movements) x WHERE bal < 0
UNION ALL
SELECT 'stock_shortfall_open', count(*) FROM stock_daily_demands WHERE shortfall_qty > 0
UNION ALL
SELECT 'demand_not_equal_deducted', count(*) FROM stock_daily_demands d
WHERE d.required_qty - d.shortfall_qty <> -coalesce((SELECT sum(m.quantity) FROM stock_movements m
        WHERE m.campus_id = d.campus_id AND m.food_id = d.food_id AND m.meal_date = d.meal_date), 0)
UNION ALL
SELECT 'demand_out_of_sync_with_counts_and_menu', count(*) FROM recomputed r
FULL JOIN stock_daily_demands d ON d.campus_id = r.campus_id AND d.meal_date = r.date AND d.food_id = r.food_id
WHERE r.qty IS DISTINCT FROM d.required_qty
UNION ALL
SELECT 'receipt_line_without_receipt_movement', count(*) FROM stock_receipt_lines l
JOIN stock_receipts r ON r.id = l.receipt_id AND r.status = 'CONFIRMED'
WHERE NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.lot_id = l.id AND m.movement_type = 'RECEIPT' AND m.quantity = l.quantity)
UNION ALL
SELECT 'deduct_from_draft_or_expired_lot', count(*) FROM stock_movements m
JOIN stock_receipt_lines l ON l.id = m.lot_id JOIN stock_receipts r ON r.id = l.receipt_id
WHERE m.movement_type = 'DEDUCT' AND (r.status <> 'CONFIRMED' OR l.expiry_date < m.meal_date OR r.campus_id <> m.campus_id)
UNION ALL
SELECT 'week_not_published_until_next_week', count(*) FROM ctx
CROSS JOIN LATERAL generate_series(DATE '2026-09-07', ctx.monday + 7, INTERVAL '7 days') g
WHERE NOT EXISTS (SELECT 1 FROM meal_plan_weeks w WHERE w.week_start = g::date AND w.status = 'PUBLISHED'
                    AND EXISTS (SELECT 1 FROM meal_plan_items i WHERE i.week_id = w.id))
UNION ALL
SELECT 'week_days_json_invalid', count(*) FROM meal_plan_weeks w
WHERE jsonb_array_length(w.days) <> 5
   OR EXISTS (SELECT 1 FROM jsonb_array_elements(w.days) e
              WHERE (e ->> 'date')::date NOT BETWEEN w.week_start AND w.week_start + 4
                 OR NOT (e ? 'off') OR NOT (e ? 'note'))
UNION ALL
SELECT 'published_day_session_without_dish', count(*) FROM meal_plan_weeks w
CROSS JOIN LATERAL jsonb_array_elements(w.days) e
CROSS JOIN (VALUES ('NURSERY', 'MORNING'), ('NURSERY', 'LUNCH'), ('NURSERY', 'AFTERNOON'),
                   ('KINDERGARTEN', 'LUNCH'), ('KINDERGARTEN', 'AFTERNOON')) b (block, session)
WHERE w.status = 'PUBLISHED' AND NOT (e ->> 'off')::boolean
  AND NOT EXISTS (SELECT 1 FROM meal_plan_items i WHERE i.week_id = w.id AND i.menu_date = (e ->> 'date')::date
                    AND i.menu_block = b.block AND i.session = b.session)
UNION ALL
SELECT 'past_school_day_without_confirmed_count', count(*) FROM school_days d CROSS JOIN served s
WHERE NOT EXISTS (SELECT 1 FROM meal_counts mc WHERE mc.campus_id = s.campus_id AND mc.date = d.day
                    AND mc.session = s.session AND mc.status = 'CONFIRMED')
UNION ALL
SELECT 'count_line_not_matching_attendance', count(*) FROM meal_count_lines l
JOIN meal_counts mc ON mc.id = l.meal_count_id
WHERE NOT l.pending AND (l.normal + l.substitute <> l.present
   OR l.present <> (SELECT count(*) FROM attendance_records r
                    WHERE r.classroom_id = l.classroom_id AND r.date = mc.date AND r.status = 'PRESENT'))
UNION ALL
SELECT 'count_confirmed_by_not_campus_vp', count(*) FROM meal_counts mc
WHERE mc.status = 'CONFIRMED' AND NOT EXISTS (
    SELECT 1 FROM staff_assignments a WHERE a.user_id = mc.confirmed_by AND a.staff_role = 'VICE_PRINCIPAL'
      AND a.campus_id = mc.campus_id AND a.valid_from <= mc.date AND (a.valid_to IS NULL OR a.valid_to >= mc.date))
UNION ALL
SELECT 'count_history_missing', count(*) FROM meal_counts mc
WHERE (SELECT count(*) FROM meal_count_history h WHERE h.meal_count_id = mc.id AND h.action IN ('AGGREGATED', 'CONFIRMED')) < 2
UNION ALL
SELECT 'fresh_forecast_missing_next_week', count(*) FROM ctx CROSS JOIN campuses c
WHERE c.code IN ('CS-A', 'CS-B') AND NOT EXISTS (SELECT 1 FROM fresh_forecasts f
        WHERE f.campus_id = c.id AND f.period_from = ctx.monday + 7 AND jsonb_array_length(f.counts) > 0
          AND EXISTS (SELECT 1 FROM fresh_forecast_lines l WHERE l.forecast_id = f.id))
UNION ALL
SELECT 'fresh_check_without_lines', count(*) FROM fresh_checks c
WHERE NOT EXISTS (SELECT 1 FROM fresh_check_lines l WHERE l.check_id = c.id)
UNION ALL
SELECT 'fresh_round2_without_confirmed_count', count(*) FROM fresh_checks c
WHERE c.round = 2 AND NOT EXISTS (SELECT 1 FROM meal_counts mc WHERE mc.campus_id = c.campus_id AND mc.date = c.check_date)
   OR c.round = 2 AND EXISTS (SELECT 1 FROM meal_counts mc WHERE mc.campus_id = c.campus_id AND mc.date = c.check_date
                               AND mc.status <> 'CONFIRMED')
UNION ALL
SELECT 'fresh_round1_line_not_from_forecast', count(*) FROM fresh_check_lines l
JOIN fresh_checks c ON c.id = l.check_id AND c.round = 1
WHERE NOT EXISTS (SELECT 1 FROM fresh_forecast_lines f WHERE f.forecast_id = c.forecast_id
                    AND f.menu_date = c.check_date AND f.food_id = l.food_id AND f.quantity = l.expected_qty)
UNION ALL
SELECT 'food_sample_dish_details_invalid', count(*) FROM food_samples s
WHERE cardinality(s.dish_ids) = 0 OR jsonb_array_length(s.dish_details) <> cardinality(s.dish_ids)
UNION ALL
SELECT 'food_sample_disposed_before_24h', count(*) FROM food_samples s
WHERE s.disposed_at IS NOT NULL AND s.disposed_at < s.sampled_at + INTERVAL '24 hours'
UNION ALL
SELECT 'food_sample_in_future', count(*) FROM food_samples s
WHERE s.sampled_at > now() OR s.disposed_at > now() OR s.approved_at > now() OR s.disposal_approved_at > now()
