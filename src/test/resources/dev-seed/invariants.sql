-- Kiểm nhất quán bộ dữ liệu dev: mỗi dòng (tên kiểm, số vi phạm), kỳ vọng 0. DevSeedIntegrationTest chạy với search_path = schema thử.
-- Ngày học = thứ 2–6 từ 03/09/2026 tới hôm qua, trừ school_holidays.
WITH school_days AS (
    SELECT d::date AS day FROM generate_series(DATE '2026-09-03', CURRENT_DATE - 1, INTERVAL '1 day') d
    WHERE EXTRACT(ISODOW FROM d) < 6 AND NOT EXISTS (SELECT 1 FROM school_holidays h WHERE d::date BETWEEN h.start_date AND h.end_date)
)
SELECT 'class_without_two_active_teachers', count(*) FROM classrooms cl
 WHERE cl.school_year_id = (SELECT id FROM school_years WHERE status = 'ACTIVE') AND ((SELECT count(*) FROM staff_assignments a WHERE a.classroom_id = cl.id AND a.staff_role = 'TEACHER' AND a.valid_to IS NULL) <> 2
    OR (SELECT count(*) FROM staff_assignments a WHERE a.classroom_id = cl.id AND a.is_homeroom AND a.valid_to IS NULL) <> 1)
UNION ALL SELECT 'class_missing_age_group_room_or_leader', count(*) FROM classrooms cl
 WHERE cl.school_year_id = (SELECT id FROM school_years WHERE status = 'ACTIVE') AND cl.age_group_id IS NULL OR cl.room_id IS NULL OR cl.capacity IS NULL
    OR cl.school_year_id = (SELECT id FROM school_years WHERE status = 'ACTIVE') AND NOT EXISTS (SELECT 1 FROM staff_assignments a
       WHERE a.staff_role = 'AGE_GROUP_TEAM_LEADER' AND a.age_group_id = cl.age_group_id AND a.school_year_id = cl.school_year_id)
UNION ALL SELECT 'team_leader_not_teaching_own_age_group', count(*) FROM staff_assignments l
 WHERE l.staff_role = 'AGE_GROUP_TEAM_LEADER' AND l.valid_to IS NULL AND NOT EXISTS (SELECT 1 FROM staff_assignments t JOIN classrooms cl ON cl.id = t.classroom_id
       WHERE t.user_id = l.user_id AND t.staff_role = 'TEACHER' AND t.valid_to IS NULL AND cl.age_group_id = l.age_group_id)
UNION ALL SELECT 'child_without_exactly_one_recipient', count(*) FROM (SELECT child_id FROM child_guardians GROUP BY child_id HAVING count(*) FILTER (WHERE account_recipient) <> 1) x
UNION ALL SELECT 'active_child_without_class', count(*) FROM children ch WHERE ch.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = ch.id AND e.valid_to IS NULL)
UNION ALL SELECT 'inactive_account_with_open_assignment', count(*) FROM user_accounts u JOIN staff_assignments a ON a.user_id = u.id WHERE u.status = 'INACTIVE' AND a.valid_to IS NULL
UNION ALL SELECT 'attendance_missing_past_day', count(*) FROM school_days s JOIN enrollments e ON e.valid_from <= s.day AND (e.valid_to IS NULL OR e.valid_to >= s.day)
 WHERE NOT EXISTS (SELECT 1 FROM attendance_records a WHERE a.child_id = e.child_id AND a.date = s.day)
UNION ALL SELECT 'attendance_status_off_formula', count(*) FROM attendance_records a JOIN children ch ON ch.id = a.child_id
 WHERE a.date < CURRENT_DATE AND a.status <> CASE
       WHEN abs(hashtext(ch.code || ':' || a.date::text)::bigint) % 100 < 3
            AND EXISTS (SELECT 1 FROM child_guardians g WHERE g.child_id = ch.id AND g.user_id IS NOT NULL) THEN 'EXCUSED'
       WHEN abs(hashtext(ch.code || ':' || a.date::text)::bigint) % 100 < 5 THEN 'UNEXCUSED' ELSE 'PRESENT' END
UNION ALL SELECT 'excused_without_parent_leave', count(*) FROM attendance_records a WHERE a.status = 'EXCUSED' AND NOT EXISTS (
       SELECT 1 FROM leave_requests l WHERE l.child_id = a.child_id AND a.date BETWEEN l.from_date AND l.to_date
         AND (l.cancelled_from IS NULL OR l.cancelled_from > a.date) AND l.created_by = a.recorded_by)
UNION ALL SELECT 'present_past_without_handover', count(*) FROM attendance_records a WHERE a.status = 'PRESENT' AND a.date < CURRENT_DATE
   AND NOT EXISTS (SELECT 1 FROM pickup_records p WHERE p.child_id = a.child_id AND p.date = a.date AND p.outcome = 'HANDED_OVER')
UNION ALL SELECT 'pickup_without_presence', count(*) FROM pickup_records p
 WHERE NOT EXISTS (SELECT 1 FROM attendance_records a WHERE a.child_id = p.child_id AND a.date = p.date AND a.status = 'PRESENT')
UNION ALL SELECT 'observation_or_health_without_presence',
       (SELECT count(*) FROM daily_observations o WHERE NOT EXISTS (SELECT 1 FROM attendance_records a WHERE a.child_id = o.child_id AND a.date = o.date AND a.status = 'PRESENT'))
     + (SELECT count(*) FROM daily_health_entries h WHERE NOT EXISTS (SELECT 1 FROM attendance_records a WHERE a.child_id = h.child_id AND a.date = h.date AND a.status = 'PRESENT'))
UNION ALL SELECT 'attendance_by_unassigned_teacher', count(*) FROM attendance_records a JOIN user_accounts u ON u.id = a.recorded_by
 WHERE u.primary_role = 'TEACHER' AND NOT EXISTS (SELECT 1 FROM staff_assignments s WHERE s.user_id = u.id AND s.classroom_id = a.classroom_id
       AND s.valid_from <= a.date AND (s.valid_to IS NULL OR s.valid_to >= a.date))
UNION ALL SELECT 'eval_confirmer_not_teacher', count(*) FROM child_evaluations e WHERE e.status='CONFIRMED' AND NOT EXISTS (SELECT 1 FROM staff_assignments a WHERE a.staff_role='TEACHER' AND a.classroom_id=e.classroom_id AND a.user_id=e.confirmed_by AND a.valid_from<=(e.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AND (a.valid_to IS NULL OR a.valid_to>=(e.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date))
UNION ALL SELECT 'eval_child_not_in_class', count(*) FROM child_evaluations e WHERE NOT EXISTS (SELECT 1 FROM enrollments n WHERE n.child_id=e.child_id AND n.classroom_id=e.classroom_id AND n.valid_from<=e.period_end AND (n.valid_to IS NULL OR n.valid_to>=e.period_start))
UNION ALL SELECT 'eval_period_not_ended', count(*) FROM child_evaluations WHERE period_end >= CURRENT_DATE
UNION ALL SELECT 'eval_events_bad_order', count(*) FROM child_evaluations e JOIN child_evaluation_events v ON v.evaluation_id=e.id AND v.action='CONFIRMED' WHERE v.at < e.draft_at OR v.at <> e.confirmed_at
UNION ALL SELECT 'cmc_confirmed_without_content', count(*) FROM class_monthly_comments WHERE confirmed_by IS NOT NULL AND content IS NULL
UNION ALL SELECT 'ticket_weekly_lt3_flags', count(*) FROM behaviour_tickets WHERE type='WEEKLY' AND (flag_count<3 OR period_end>=CURRENT_DATE)
UNION ALL SELECT 'ticket_monthly_count_mismatch', count(*) FROM behaviour_tickets m WHERE m.type='MONTHLY' AND m.weekly_tickets <> (SELECT count(*) FROM behaviour_tickets w WHERE w.child_id=m.child_id AND w.type='WEEKLY' AND date_trunc('month',w.period_start)=m.period_start)
UNION ALL SELECT 'ticket_issued_before_period_end', count(*) FROM behaviour_tickets WHERE (issued_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date <= period_end
UNION ALL SELECT 'meas_outside_campaign', count(*) FROM health_measurements m JOIN health_check_campaigns c ON c.id=m.campaign_id WHERE m.date NOT BETWEEN c.start_date AND c.end_date OR m.date > CURRENT_DATE
UNION ALL SELECT 'meas_class_mismatch', count(*) FROM health_measurements m WHERE NOT EXISTS (SELECT 1 FROM enrollments n WHERE n.child_id=m.child_id AND n.classroom_id=m.classroom_id AND n.valid_from<=m.date AND (n.valid_to IS NULL OR n.valid_to>=m.date))
UNION ALL SELECT 'meas_on_absent_day', count(*) FROM health_measurements m JOIN children ch ON ch.id=m.child_id WHERE abs(hashtext(ch.code||':'||m.date::text)::bigint)%100 < 5
UNION ALL SELECT 'absent_and_measured', count(*) FROM health_check_absences a JOIN health_measurements m ON m.child_id=a.child_id AND m.campaign_id=a.campaign_id
UNION ALL SELECT 'absence_in_open_campaign', count(*) FROM health_check_absences a JOIN health_check_campaigns c ON c.id=a.campaign_id WHERE c.closed_at IS NULL
UNION ALL SELECT 'exam_kind_mismatch', count(*) FROM health_measurements m JOIN health_check_campaigns c ON c.id=m.campaign_id WHERE (c.kind='EXAM') <> (m.exam IS NOT NULL)
UNION ALL SELECT 'concluded_exam_incomplete', count(*) FROM health_measurements WHERE exam_status='CONCLUDED' AND ((SELECT count(*) FROM jsonb_object_keys(exam->'clinical'))<>25 OR coalesce(exam->'conclusion'->>'doctorName','')='' OR exam->'nutrition'->'statuses'='[]'::jsonb)
UNION ALL SELECT 'meas_head_in_measurement_round', count(*) FROM health_measurements WHERE exam IS NULL AND head_circumference_cm IS NOT NULL
UNION ALL SELECT 'interp_bad_points', count(*) FROM health_interpretations i WHERE EXISTS (SELECT 1 FROM jsonb_array_elements_text(i.measurement_ids) x LEFT JOIN health_measurements m ON m.id=x::bigint AND m.child_id=i.child_id AND m.published_at IS NOT NULL WHERE m.id IS NULL)
UNION ALL SELECT 'interp_generated_before_last_point', count(*) FROM health_interpretations i JOIN health_measurements m ON m.id=(i.measurement_ids->>-1)::bigint WHERE i.generated_at < m.published_at
UNION ALL SELECT 'exam_history_vs_declaration', count(*) FROM health_measurements m JOIN child_health_declarations d ON d.child_id=m.child_id WHERE m.exam_status='CONCLUDED' AND ((m.exam->'history'->>'self'='YES') <> (d.self_history_state='REPORTED') OR (m.exam->'history'->>'tbContact'='YES') <> (d.tb_contact_state='REPORTED'))
UNION ALL SELECT 'future_timestamps', (SELECT count(*) FROM child_evaluations WHERE greatest(updated_at, confirmed_at, draft_at) > now()) + (SELECT count(*) FROM health_measurements WHERE updated_at > now()) + (SELECT count(*) FROM behaviour_tickets WHERE issued_at > now()) + (SELECT count(*) FROM health_interpretations WHERE updated_at > now())
UNION ALL SELECT 'unlock_active_per_class_day_gt1', count(*) FROM (SELECT classroom_id, date FROM assessment_day_unlocks WHERE valid_until > now() GROUP BY 1,2 HAVING count(*)>1) x
UNION ALL SELECT 'edu_i1_thanh_vien_khac_trang_thai_goc', (SELECT count(*) FROM (
SELECT m.doc_key, m.status, r.doc_key, r.status
FROM edu_class_docs m
JOIN edu_themes t ON t.id = m.theme_id
JOIN school_years sy ON sy.name = '2026-2027'
JOIN edu_class_docs r ON r.classroom_id = m.classroom_id AND r.team_plan_id = m.team_plan_id
 AND ((m.kind = 'day' AND r.kind = 'dossier' AND r.theme_id = m.theme_id)
   OR (m.kind IN ('theme', 'week') AND r.kind = 'semester'
       AND r.semester_no = CASE WHEN t.start_date <= sy.semester1_end THEN 1 ELSE 2 END))
WHERE m.status <> r.status OR m.review_round <> r.review_round OR m.approvals <> r.approvals
) x)
UNION ALL SELECT 'edu_i2_goc_da_ban_hanh_phai_co_ban_ban_hanh_va_print_snapshot', (SELECT count(*) FROM (
SELECT r.doc_key FROM edu_class_docs r
WHERE r.kind IN ('semester', 'dossier') AND r.status = 'APPROVED'
  AND (r.print_snapshot IS NULL OR NOT EXISTS (SELECT 1 FROM edu_class_doc_releases x WHERE x.class_doc_id = r.id))
) x)
UNION ALL SELECT 'edu_i3_ho_so_cho_duyet_phai_ap_dung_policy_revision_hien_hanh_neu_khong_startup_reconcile_ghi_lai', (SELECT count(*) FROM (
SELECT d.doc_key FROM edu_class_docs d, edu_settings s
WHERE s.id = 1 AND d.status = 'REVIEW' AND d.applied_policy_revision IS DISTINCT FROM s.workflow_revision
) x)
UNION ALL SELECT 'edu_i5_goi_hoc_ky_da_ban_hanh_phai_du_ten_nhanh_ke_hoach_chu_de_ke_hoach_tuan_moi_tuan_hoc_cua_ky', (SELECT count(*) FROM (
SELECT sp.doc_key, t.id AS theme_id, b.start_date
FROM edu_class_docs sp
JOIN school_years sy ON sy.name = '2026-2027'
JOIN edu_themes t ON t.team_plan_id = sp.team_plan_id
 AND sp.semester_no = CASE WHEN t.start_date <= sy.semester1_end THEN 1 ELSE 2 END
JOIN edu_plan_branch_templates b ON b.theme_id = t.id
JOIN edu_class_docs bp ON bp.kind = 'branch' AND bp.classroom_id = sp.classroom_id AND bp.team_plan_id = sp.team_plan_id
WHERE sp.kind = 'semester' AND sp.status = 'APPROVED'
  AND (COALESCE(bp.content -> 'names' ->> b.start_date::text, '') = ''
       OR NOT EXISTS (SELECT 1 FROM edu_class_docs w WHERE w.kind = 'week' AND w.classroom_id = sp.classroom_id
                      AND w.team_plan_id = sp.team_plan_id AND w.week_start = b.start_date AND w.theme_id = t.id)
       OR NOT EXISTS (SELECT 1 FROM edu_class_docs tp WHERE tp.kind = 'theme' AND tp.classroom_id = sp.classroom_id
                      AND tp.team_plan_id = sp.team_plan_id AND tp.theme_id = t.id))
) x)
UNION ALL SELECT 'edu_i6_tai_lieu_lop_chi_thuoc_ke_hoach_to_da_ban_hanh_cung_nhom_tuoi_tuan_thuoc_dung_chu_de', (SELECT count(*) FROM (
SELECT d.doc_key FROM edu_class_docs d
JOIN classrooms c ON c.id = d.classroom_id JOIN edu_team_plans p ON p.id = d.team_plan_id
WHERE p.status <> 'APPROVED' OR p.age_group_id <> c.age_group_id
   OR (d.week_start IS NOT NULL AND NOT EXISTS (SELECT 1 FROM edu_plan_branch_templates b
        WHERE b.team_plan_id = d.team_plan_id AND b.theme_id = d.theme_id AND b.start_date = d.week_start))
) x)
UNION ALL SELECT 'edu_i7_the_hoat_dong_mt_thuoc_chu_de_co_noi_dung_duoc_tich_loai_gio_thuoc_cau_hinh_contentrefs_thuoc_mt_cua_the', (SELECT count(*) FROM (
SELECT d.doc_key, card ->> 'id'
FROM edu_class_docs d CROSS JOIN LATERAL jsonb_array_elements(d.content -> 'activities') card, edu_settings s
WHERE d.kind = 'theme' AND s.id = 1
  AND (NOT EXISTS (SELECT 1 FROM jsonb_array_elements(s.week_rows::jsonb) r WHERE r ->> 'key' = card ->> 'slot')
       OR EXISTS (SELECT 1 FROM jsonb_array_elements_text(card -> 'goalIds') g
                  WHERE NOT EXISTS (SELECT 1 FROM edu_goal_contents gc JOIN edu_content_theme_ticks k ON k.content_id = gc.id
                                    WHERE gc.goal_id = g::bigint AND k.theme_id = d.theme_id))
       OR EXISTS (SELECT 1 FROM jsonb_array_elements_text(card -> 'contentRefs') ref
                  WHERE NOT (card -> 'goalIds') ? split_part(ref, ':', 1)))
) x)
UNION ALL SELECT 'edu_i8_luot_xep_o_ke_hoach_tuan_tro_toi_the_cua_ke_hoach_chu_de_cung_lop_chu_de', (SELECT count(*) FROM (
SELECT w.doc_key, p ->> 'cardId'
FROM edu_class_docs w
CROSS JOIN LATERAL jsonb_each(w.content -> 'picks') cell CROSS JOIN LATERAL jsonb_array_elements(cell.value) p
WHERE w.kind = 'week'
  AND NOT EXISTS (SELECT 1 FROM edu_class_docs t CROSS JOIN LATERAL jsonb_array_elements(t.content -> 'activities') c
                  WHERE t.kind = 'theme' AND t.classroom_id = w.classroom_id AND t.theme_id = w.theme_id
                    AND c ->> 'id' = p ->> 'cardId')
) x)
UNION ALL SELECT 'edu_i9_ho_so_chu_de_da_gui_moi_ngay_hoc_co_du_4_muc_co_chu_7390', (SELECT count(*) FROM (
SELECT d.doc_key, day ->> 'date', s.k
FROM edu_class_docs d CROSS JOIN LATERAL jsonb_array_elements(d.content -> 'days') day
CROSS JOIN (VALUES ('hoc'), ('ngoai_troi'), ('goc'), ('chieu')) s(k)
WHERE d.kind = 'day' AND d.review_round > 0 AND day ? 'sections'
  AND NOT EXISTS (SELECT 1 FROM school_holidays h WHERE (day ->> 'date')::date BETWEEN h.start_date AND h.end_date)
  AND COALESCE(day -> 'sections' -> s.k ->> 'aims', '') = '' AND COALESCE(day -> 'sections' -> s.k ->> 'prep', '') = ''
  AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(day -> 'sections' -> s.k -> 'rows') r
                  WHERE COALESCE(r ->> 'teacher', '') <> '' OR COALESCE(r ->> 'child', '') <> '' OR COALESCE(r ->> 'note', '') <> '')
) x)
UNION ALL SELECT 'edu_i10_ho_so_chu_de_da_gui_ban_hanh_moi_mt_cua_chu_de_duoc_day_trong_ke_hoach_tuan_7396', (SELECT count(*) FROM (
SELECT ds.doc_key, g.id AS goal_id
FROM edu_class_docs ds
JOIN edu_goal_contents gc ON true
JOIN edu_content_theme_ticks k ON k.content_id = gc.id AND k.theme_id = ds.theme_id
JOIN edu_plan_goals g ON g.id = gc.goal_id
WHERE ds.kind = 'dossier' AND ds.review_round > 0
  AND NOT EXISTS (SELECT 1 FROM edu_class_docs w
                  CROSS JOIN LATERAL jsonb_each(w.content -> 'picks') cell CROSS JOIN LATERAL jsonb_array_elements(cell.value) p
                  WHERE w.kind = 'week' AND w.classroom_id = ds.classroom_id AND w.theme_id = ds.theme_id
                    AND (p -> 'goalIds') ? g.id::text)
GROUP BY ds.doc_key, g.id
) x)
UNION ALL SELECT 'edu_i11_ke_hoach_to_cay_giong_nguon_nam_hien_hanh_theo_lineage_so_dong_va_nguon_la_ban_nam_da_ban_hanh_moi_nhat', (SELECT count(*) FROM (
SELECT t.code FROM edu_team_plans t
WHERE t.curriculum_source_plan_id IS DISTINCT FROM (SELECT y.id FROM edu_year_plans y WHERE y.school_year_id = t.school_year_id
        AND y.age_group_id = t.age_group_id AND y.status = 'APPROVED' AND NOT y.disabled ORDER BY y.version_no DESC LIMIT 1)
   OR (SELECT count(*) FROM edu_plan_goals g WHERE g.team_plan_id = t.id)
      <> (SELECT count(*) FROM edu_plan_goals g WHERE g.school_year_plan_id = t.curriculum_source_plan_id)
   OR (SELECT count(*) FROM edu_goal_contents c JOIN edu_plan_goals g ON g.id = c.goal_id WHERE g.team_plan_id = t.id)
      <> (SELECT count(*) FROM edu_goal_contents c JOIN edu_plan_goals g ON g.id = c.goal_id WHERE g.school_year_plan_id = t.curriculum_source_plan_id)
) x)
UNION ALL SELECT 'edu_i12_ke_hoach_nam_9_chu_de_chu_de_t2_t6_khong_chong_lan_moi_noi_dung_duoc_tich_it_nhat_1_chu_de', (SELECT count(*) FROM (
SELECT p.code, 'themes' FROM edu_year_plans p WHERE (SELECT count(*) FROM edu_themes t WHERE t.school_year_plan_id = p.id) < 9
UNION ALL
SELECT p.code, t.abbr FROM edu_year_plans p JOIN edu_themes t ON t.school_year_plan_id = p.id
WHERE extract(isodow FROM t.start_date) <> 1 OR extract(isodow FROM t.end_date) <> 5
   OR EXISTS (SELECT 1 FROM edu_themes o WHERE o.school_year_plan_id = p.id AND o.id <> t.id
              AND o.start_date <= t.end_date AND t.start_date <= o.end_date)
UNION ALL
SELECT p.code, 'content ' || gc.id FROM edu_year_plans p JOIN edu_plan_goals g ON g.school_year_plan_id = p.id
JOIN edu_goal_contents gc ON gc.goal_id = g.id
WHERE NOT EXISTS (SELECT 1 FROM edu_content_theme_ticks k WHERE k.content_id = gc.id)
) x)
UNION ALL SELECT 'edu_i13_lich_nhanh_v2_themeschedules_khop_chu_de_cua_to_ten_tuan_ten_nhanh_nhanh_tro_mau_cua_to', (SELECT count(*) FROM (
SELECT bp.doc_key FROM edu_class_docs bp
WHERE bp.kind = 'branch'
  AND (jsonb_array_length(bp.content -> 'themeSchedules') <> (SELECT count(*) FROM edu_themes t WHERE t.team_plan_id = bp.team_plan_id)
       OR EXISTS (SELECT 1 FROM jsonb_array_elements(bp.content -> 'themeSchedules') ts
                  WHERE NOT EXISTS (SELECT 1 FROM edu_themes t WHERE t.team_plan_id = bp.team_plan_id AND t.id = (ts ->> 'themeId')::bigint
                                    AND t.start_date = (ts ->> 'startDate')::date AND t.end_date = (ts ->> 'endDate')::date))
       OR EXISTS (SELECT 1 FROM jsonb_array_elements(bp.content -> 'branches') b
                  WHERE bp.content -> 'names' ->> (b ->> 'startDate') IS DISTINCT FROM b ->> 'name'
                     OR NOT EXISTS (SELECT 1 FROM edu_plan_branch_templates x WHERE x.team_plan_id = bp.team_plan_id
                                    AND x.id = (b ->> 'sourceTemplateId')::bigint AND x.theme_id = (b ->> 'themeId')::bigint)))
) x)
UNION ALL SELECT 'edu_i14_hoc_lieu_chi_link_nhan_tay_khong_gan_giao_an_chu_so_huu_la_gv', (SELECT count(*) FROM (
SELECT i.id FROM edu_resource_items i
WHERE i.kind <> 'LINK' OR i.storage_key IS NOT NULL
   OR NOT EXISTS (SELECT 1 FROM staff_assignments sa WHERE sa.user_id = i.owner_id AND sa.staff_role = 'TEACHER')
UNION ALL SELECT t.item_id FROM edu_resource_item_tags t WHERE t.attachment_id IS NOT NULL
) x)
UNION ALL SELECT 'dar_child_not_present', count(*) FROM daily_activity_results r WHERE NOT EXISTS (SELECT 1 FROM attendance_records a WHERE a.child_id=r.child_id AND a.date=r.date AND a.classroom_id=r.classroom_id AND a.status='PRESENT')
UNION ALL SELECT 'dar_doc_not_released_week', count(*) FROM daily_activity_results r LEFT JOIN edu_class_docs d ON d.id=r.class_doc_id WHERE d.kind IS DISTINCT FROM 'week' OR d.classroom_id<>r.classroom_id OR r.date NOT BETWEEN d.week_start AND d.week_start+4 OR (d.status<>'APPROVED' AND d.history::text NOT LIKE '%"revise_future"%')
UNION ALL SELECT 'dar_goal_not_in_theme', count(*) FROM daily_activity_results r JOIN edu_class_docs d ON d.id=r.class_doc_id CROSS JOIN LATERAL unnest(string_to_array(nullif(concat_ws(',', nullif(r.goals_met,''), nullif(r.goals_not_met,'')),''),',')) g(id) WHERE NOT EXISTS (SELECT 1 FROM edu_goal_contents c JOIN edu_content_theme_ticks k ON k.content_id=c.id WHERE c.goal_id=g.id::bigint AND k.theme_id=d.theme_id)
UNION ALL SELECT 'dar_met_and_not_met_overlap', count(*) FROM daily_activity_results r WHERE string_to_array(nullif(r.goals_met,''),',') && string_to_array(nullif(r.goals_not_met,''),',') OR (r.goals_met='' AND r.goals_not_met='' AND r.participation IS NULL)
UNION ALL SELECT 'dar_recorder_not_teacher', count(*) FROM daily_activity_results r WHERE NOT EXISTS (SELECT 1 FROM staff_assignments a WHERE a.staff_role='TEACHER' AND a.classroom_id=r.classroom_id AND a.user_id=r.recorded_by AND a.valid_from<=r.date AND (a.valid_to IS NULL OR a.valid_to>=r.date)) OR (r.recorded_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date<>r.date
UNION ALL SELECT 'theme_eval_confirmed_before_end', count(*) FROM theme_evaluations e WHERE e.confirmed_at IS NOT NULL AND (e.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date < e.end_date
UNION ALL SELECT 'theme_eval_confirmer_not_teacher', count(*) FROM theme_evaluations e WHERE e.confirmed_by IS NOT NULL AND NOT EXISTS (SELECT 1 FROM staff_assignments a WHERE a.staff_role='TEACHER' AND a.classroom_id=e.classroom_id AND a.user_id=e.confirmed_by AND a.valid_from<=(e.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AND (a.valid_to IS NULL OR a.valid_to>=(e.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date))
UNION ALL SELECT 'theme_eval_cell_vs_activity', count(*) FROM theme_evaluations e CROSS JOIN LATERAL jsonb_array_elements(e.cells::jsonb) c WHERE e.confirmed_at IS NOT NULL AND (c->>'x')::boolean = FALSE AND (c->>'m')::boolean <> EXISTS (SELECT 1 FROM daily_activity_results r JOIN attendance_records a ON a.child_id=r.child_id AND a.date=r.date AND a.status='PRESENT' CROSS JOIN LATERAL unnest(string_to_array(nullif(r.goals_met,''),',')) m(gid) JOIN edu_plan_goals g ON g.id=m.gid::bigint WHERE r.child_id=(c->>'c')::bigint AND r.classroom_id=e.classroom_id AND r.date BETWEEN e.start_date AND e.end_date AND coalesce(g.lineage_id,g.id)=(c->>'g')::bigint)
