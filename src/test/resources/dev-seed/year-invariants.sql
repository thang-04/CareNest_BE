-- Kiểm nhất quán lịch sử năm học (file R__dev_06_year_history.sql): mỗi dòng (tên kiểm, số vi phạm), kỳ vọng 0.
SELECT 'year_exactly_one_active', abs((SELECT count(*) FROM school_years WHERE status = 'ACTIVE') - 1)
UNION ALL SELECT 'year_closed_without_closer_or_snapshot', count(*) FROM school_years y WHERE y.status = 'CLOSED'
   AND (y.closed_at IS NULL OR y.closed_by IS NULL
        OR (SELECT count(*) FROM school_year_campuses s WHERE s.school_year_id = y.id) <> (SELECT count(*) FROM campuses)
        OR (SELECT count(*) FROM school_year_age_groups s WHERE s.school_year_id = y.id) <> (SELECT count(*) FROM age_groups)
        OR NOT EXISTS (SELECT 1 FROM school_year_rooms s WHERE s.school_year_id = y.id))
UNION ALL SELECT 'year_closed_class_not_closed', count(*) FROM classrooms c JOIN school_years y ON y.id = c.school_year_id
 WHERE y.status = 'CLOSED' AND c.status <> 'CLOSED'
UNION ALL SELECT 'year_open_class_closed', count(*) FROM classrooms c JOIN school_years y ON y.id = c.school_year_id
 WHERE y.status <> 'CLOSED' AND c.status = 'CLOSED'
UNION ALL SELECT 'year_planned_prep_out_of_range', count(*) FROM school_years y WHERE y.status = 'PLANNED'
   AND (y.prep_date > y.start_date OR y.prep_date <= (SELECT max(p.end_date) FROM school_years p WHERE p.end_date < y.start_date))
UNION ALL SELECT 'year_enrollment_outside_class_year', count(*) FROM enrollments e JOIN classrooms c ON c.id = e.classroom_id
   JOIN school_years y ON y.id = c.school_year_id
 WHERE y.status = 'CLOSED' AND (e.valid_to IS NULL OR e.valid_to >= (SELECT min(n.start_date) FROM school_years n WHERE n.start_date > y.end_date))
UNION ALL SELECT 'year_child_overlapping_enrollments', count(*) FROM enrollments a JOIN enrollments b ON b.child_id = a.child_id AND b.id > a.id
 WHERE a.valid_from <= coalesce(b.valid_to, DATE '9999-12-31') AND b.valid_from <= coalesce(a.valid_to, DATE '9999-12-31')
UNION ALL SELECT 'year_left_child_with_open_class', count(*) FROM children ch WHERE ch.status = 'LEFT'
   AND EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = ch.id AND (e.valid_to IS NULL OR e.valid_to >= ch.left_on))
UNION ALL SELECT 'year_closed_assignment_open_or_past_year', count(*) FROM staff_assignments a JOIN classrooms c ON c.id = a.classroom_id
   JOIN school_years y ON y.id = c.school_year_id
 WHERE y.status = 'CLOSED' AND (a.valid_to IS NULL OR a.valid_to > y.end_date)
UNION ALL SELECT 'year_closed_class_without_one_homeroom', count(*) FROM classrooms c JOIN school_years y ON y.id = c.school_year_id
 WHERE y.status = 'CLOSED' AND (SELECT count(*) FROM staff_assignments a WHERE a.classroom_id = c.id AND a.is_homeroom) <> 1
UNION ALL SELECT 'year_same_role_overlap', count(*) FROM staff_assignments a JOIN staff_assignments b ON b.user_id = a.user_id AND b.id > a.id
   AND b.staff_role = a.staff_role AND b.campus_id IS NOT DISTINCT FROM a.campus_id AND b.classroom_id IS NOT DISTINCT FROM a.classroom_id
   AND b.age_group_id IS NOT DISTINCT FROM a.age_group_id
 WHERE a.staff_role IN ('PRINCIPAL', 'VICE_PRINCIPAL', 'KITCHEN_STAFF') AND a.valid_from <= coalesce(b.valid_to, DATE '9999-12-31')
   AND b.valid_from <= coalesce(a.valid_to, DATE '9999-12-31')
UNION ALL SELECT 'year_leader_not_teaching_group_in_year', count(*) FROM staff_assignments l JOIN school_years y ON y.id = l.school_year_id
 WHERE l.staff_role = 'AGE_GROUP_TEAM_LEADER' AND y.status = 'CLOSED' AND NOT EXISTS (SELECT 1 FROM staff_assignments t
       JOIN classrooms c ON c.id = t.classroom_id WHERE t.user_id = l.user_id AND t.staff_role = 'TEACHER'
       AND c.school_year_id = y.id AND c.age_group_id = l.age_group_id)
UNION ALL SELECT 'year_closed_leaders_without_principal', count(*) FROM school_years y WHERE y.status = 'CLOSED' AND NOT EXISTS (
       SELECT 1 FROM staff_assignments a WHERE a.staff_role = 'PRINCIPAL' AND a.valid_from <= y.end_date
       AND (a.valid_to IS NULL OR a.valid_to >= y.start_date))
UNION ALL SELECT 'placement_wrong_age_group', count(*) FROM class_placements p JOIN classrooms f ON f.id = p.from_classroom_id
   JOIN age_groups fg ON fg.id = f.age_group_id LEFT JOIN classrooms t ON t.id = p.to_classroom_id LEFT JOIN age_groups tg ON tg.id = t.age_group_id
 WHERE (p.action = 'PROMOTE' AND tg.sort_order <> fg.sort_order + 1) OR (p.action = 'STAY' AND tg.id <> fg.id)
    OR (p.action = 'GRADUATE' AND fg.sort_order <> (SELECT max(sort_order) FROM age_groups))
    OR (p.action = 'PROMOTE' AND fg.sort_order = (SELECT max(sort_order) FROM age_groups))
    OR t.school_year_id <> p.school_year_id
UNION ALL SELECT 'placement_applied_mismatch', count(*) FROM class_placements p JOIN school_years y ON y.id = p.school_year_id
 WHERE y.status <> 'PLANNED' AND ((p.action IN ('PROMOTE', 'STAY') AND NOT EXISTS (SELECT 1 FROM enrollments e
          WHERE e.child_id = p.child_id AND e.classroom_id = p.to_classroom_id AND e.valid_from = y.start_date))
       OR (p.action IN ('LEAVE', 'GRADUATE') AND EXISTS (SELECT 1 FROM enrollments e JOIN classrooms c ON c.id = e.classroom_id
          WHERE e.child_id = p.child_id AND c.school_year_id = y.id))
       OR NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = p.child_id AND e.classroom_id = p.from_classroom_id
          AND e.valid_to = y.start_date - CASE WHEN p.action IN ('PROMOTE', 'STAY') THEN 1 ELSE 93 END))
UNION ALL SELECT 'placement_planned_stale_source', count(*) FROM class_placements p JOIN school_years y ON y.id = p.school_year_id
 WHERE y.status = 'PLANNED' AND NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = p.child_id
       AND e.classroom_id = p.from_classroom_id AND e.valid_from <= CURRENT_DATE AND e.valid_to IS NULL)
UNION ALL SELECT 'placement_planned_over_capacity', count(*) FROM classrooms c WHERE c.capacity < (SELECT count(*) FROM class_placements p
       WHERE p.to_classroom_id = c.id AND p.action IN ('PROMOTE', 'STAY'))
UNION ALL SELECT 'graduate_not_left', count(*) FROM class_placements p JOIN children ch ON ch.id = p.child_id
   JOIN school_years y ON y.id = p.school_year_id WHERE y.status <> 'PLANNED' AND p.action IN ('LEAVE', 'GRADUATE') AND ch.status <> 'LEFT'
UNION ALL SELECT 'closed_year_child_without_confirmed_evals', count(*) FROM enrollments e JOIN classrooms c ON c.id = e.classroom_id
   JOIN school_years y ON y.id = c.school_year_id
 WHERE y.status = 'CLOSED' AND e.valid_from <= y.end_date AND (e.valid_to IS NULL OR e.valid_to >= y.end_date)
   AND (SELECT count(*) FROM child_evaluations v WHERE v.child_id = e.child_id AND v.classroom_id = c.id AND v.status = 'CONFIRMED'
        AND v.period_type IN ('SEMESTER', 'YEAR')) <> 3
UNION ALL SELECT 'year_eval_sheet_missing', count(*) FROM child_evaluations v JOIN school_years y ON y.id = v.school_year_id
 WHERE y.status = 'CLOSED' AND v.period_type = 'YEAR' AND (v.goal_results IS NULL OR v.domain_comments IS NULL)
UNION ALL SELECT 'program_flag_mismatch', count(*) FROM child_evaluations v JOIN classrooms c ON c.id = v.classroom_id
   JOIN age_groups g ON g.id = c.age_group_id
 WHERE v.status = 'CONFIRMED' AND v.period_type = 'YEAR' AND ((g.age_from_months >= 60) <> (v.program_completed IS NOT NULL))
UNION ALL SELECT 'semester_eval_period_mismatch', count(*) FROM child_evaluations v JOIN school_years y ON y.id = v.school_year_id
 WHERE v.period_type = 'SEMESTER' AND NOT ((v.period_start = y.semester1_start AND v.period_end = y.semester1_end)
       OR (v.period_start = y.semester2_start AND v.period_end = y.semester2_end))
UNION ALL SELECT 'closed_class_semester_assessment_missing', count(*) FROM classrooms c JOIN school_years y ON y.id = c.school_year_id
 WHERE y.status = 'CLOSED' AND (SELECT count(*) FROM semester_assessments s WHERE s.classroom_id = c.id AND s.confirmed_at IS NOT NULL
       AND s.confirmed_at >= (s.period_end + 1) AT TIME ZONE 'Asia/Ho_Chi_Minh') <> 2
UNION ALL SELECT 'semester_snapshot_bad_shape', count(*) FROM semester_assessments s WHERE s.result_snapshot IS NOT NULL
   AND ((s.result_snapshot::jsonb ->> 'classId')::bigint <> s.classroom_id OR (s.result_snapshot::jsonb ->> 'semester')::int <> s.semester_no
        OR jsonb_typeof(s.result_snapshot::jsonb -> 'rows') <> 'array')
UNION ALL SELECT 'semester_confirmer_not_teacher', count(*) FROM semester_assessments s WHERE s.confirmed_by IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM staff_assignments a WHERE a.staff_role = 'TEACHER' AND a.classroom_id = s.classroom_id
       AND a.user_id = s.confirmed_by AND a.valid_from <= (s.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
       AND (a.valid_to IS NULL OR a.valid_to >= (s.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date))
UNION ALL SELECT 'closed_class_ratings_missing', count(*) FROM classrooms c JOIN school_years y ON y.id = c.school_year_id
 WHERE y.status = 'CLOSED' AND (SELECT count(*) FROM class_ratings r WHERE r.classroom_id = c.id) <> 3
UNION ALL SELECT 'reward_item_child_not_in_class', count(*) FROM reward_list_items i JOIN reward_lists l ON l.id = i.list_id
   JOIN classrooms c ON c.id = l.classroom_id JOIN school_years y ON y.id = c.school_year_id
 WHERE NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = i.child_id AND e.classroom_id = c.id
       AND e.valid_from <= least(y.end_date, CURRENT_DATE) AND (e.valid_to IS NULL OR e.valid_to >= least(y.end_date, CURRENT_DATE)))
UNION ALL SELECT 'reward_title_rule', count(*) FROM reward_list_items i JOIN reward_lists l ON l.id = i.list_id
 WHERE (SELECT count(*) FROM unnest(string_to_array(i.titles, '|')) t WHERE t IN ('Bé ngoan', 'Bé chăm ngoan', 'Bé khỏe, bé ngoan', 'Bé chăm ngoan xuất sắc')) > 1
    OR (i.titles LIKE '%chương trình giáo dục mầm non%' AND NOT EXISTS (SELECT 1 FROM child_evaluations v WHERE v.child_id = i.child_id
        AND v.classroom_id = l.classroom_id AND v.period_type = 'YEAR' AND v.status = 'CONFIRMED' AND v.program_completed))
UNION ALL SELECT 'reward_status_fields', count(*) FROM reward_lists l
 WHERE (l.status = 'APPROVED' AND (l.decision IS DISTINCT FROM 'APPROVE' OR l.decision_by IS NULL))
    OR (l.status = 'PENDING_PRINCIPAL' AND (l.review_decision IS DISTINCT FROM 'FORWARD' OR l.decision IS NOT NULL))
    OR (l.status = 'PENDING_VP' AND (l.submitted_at IS NULL OR l.decision IS NOT NULL))
    OR (l.status = 'DRAFT' AND l.submitted_at IS NOT NULL)
    OR (l.status = 'RETURNED' AND coalesce(l.decision, l.review_decision) IS DISTINCT FROM 'RETURN')
UNION ALL SELECT 'reward_last_event_vs_status', count(*) FROM reward_lists l JOIN LATERAL (SELECT v.action FROM reward_list_events v
   WHERE v.list_id = l.id ORDER BY v.at DESC, v.id DESC LIMIT 1) e ON true
 WHERE l.status <> CASE e.action WHEN 'CREATED' THEN 'DRAFT' WHEN 'UPDATED' THEN 'DRAFT' WHEN 'SUBMITTED' THEN 'PENDING_VP'
       WHEN 'RESUBMITTED' THEN 'PENDING_VP' WHEN 'FORWARDED' THEN 'PENDING_PRINCIPAL' WHEN 'RETURNED' THEN 'RETURNED'
       ELSE 'APPROVED' END
UNION ALL SELECT 'closed_year_record_after_close', (SELECT count(*) FROM child_evaluations v JOIN school_years y ON y.id = v.school_year_id
       WHERE y.status = 'CLOSED' AND v.updated_at > y.closed_at)
     + (SELECT count(*) FROM reward_lists l JOIN school_years y ON y.id = l.school_year_id WHERE y.status = 'CLOSED' AND l.updated_at > y.closed_at)
     + (SELECT count(*) FROM class_ratings r JOIN school_years y ON y.id = r.school_year_id WHERE y.status = 'CLOSED' AND r.rated_at > y.closed_at)
