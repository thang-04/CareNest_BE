-- EDU-54: sau cutover, writer phải dùng đúng trạng thái và giữ chứng cứ vòng duyệt cũ.
DROP TRIGGER IF EXISTS trg_normalize_edu_year_plan_status ON edu_year_plans;
DROP TRIGGER IF EXISTS trg_normalize_edu_class_doc_status ON edu_class_docs;
DROP FUNCTION IF EXISTS fn_normalize_edu_year_plan_status();
DROP FUNCTION IF EXISTS fn_normalize_edu_class_doc_status();

UPDATE edu_class_docs d
SET approvals = (SELECT COALESCE(jsonb_agg(
        CASE WHEN a ? 'round' THEN a ELSE a || jsonb_build_object('round', d.review_round, 'tracking', 'LEGACY_BASELINE') END
        ORDER BY ord), '[]'::jsonb)
    FROM jsonb_array_elements(d.approvals) WITH ORDINALITY AS e(a, ord))
WHERE EXISTS (SELECT 1 FROM jsonb_array_elements(d.approvals) a WHERE NOT a ? 'round');

-- Bổ sung ô ký vào baseline rỗng từ dữ liệu đã biết; không sửa snapshot PUBLICATION.
CREATE FUNCTION edu_legacy_print_slots(creators jsonb, chain jsonb, approvals jsonb) RETURNS jsonb
LANGUAGE sql AS $$
    SELECT jsonb_build_array(jsonb_build_object('roleKey','CREATOR','label','Người soạn','names',creators))
        || COALESCE(jsonb_agg(jsonb_build_object('roleKey', role, 'label',
            CASE role WHEN 'PRINCIPAL' THEN 'Hiệu trưởng' WHEN 'VICE_PRINCIPAL' THEN 'Phó hiệu trưởng'
                WHEN 'TEAM_LEADER' THEN 'Tổ trưởng' ELSE role END,
            'names', COALESCE((SELECT jsonb_agg(DISTINCT a->>'name') FROM jsonb_array_elements(approvals) a
                WHERE a->>'roleKey'=role AND COALESCE(a->>'name','')<>''), '[]'::jsonb)) ORDER BY ord), '[]'::jsonb)
    FROM jsonb_array_elements_text(COALESCE(chain,'[]'::jsonb)) WITH ORDINALITY AS r(role,ord)
$$;

UPDATE edu_year_plans p SET print_snapshot = jsonb_set(p.print_snapshot, '{slots}',
    edu_legacy_print_slots(
        COALESCE((SELECT jsonb_agg(u.full_name ORDER BY a.user_id) FROM edu_document_authors a
            JOIN user_accounts u ON u.id=a.user_id WHERE a.year_plan_id=p.id), '[]'::jsonb),
        COALESCE((SELECT legacy_signers::jsonb->'year' FROM edu_settings WHERE id=1),'["VICE_PRINCIPAL"]'::jsonb),
        CASE WHEN p.issued_by IS NOT NULL THEN jsonb_build_array(jsonb_build_object('roleKey','VICE_PRINCIPAL',
            'name',(SELECT full_name FROM user_accounts WHERE id=p.issued_by))) ELSE p.approvals END))
WHERE p.print_snapshot->>'origin'='LEGACY_BASELINE' AND p.print_snapshot->'slots'='[]'::jsonb;

UPDATE edu_class_docs d SET print_snapshot = jsonb_set(d.print_snapshot, '{slots}',
    edu_legacy_print_slots(
        COALESCE((SELECT jsonb_agg(DISTINCT u.full_name) FROM edu_document_authors a
            JOIN user_accounts u ON u.id=a.user_id JOIN edu_class_docs member ON member.id=a.class_doc_id
            WHERE member.id=d.id OR (d.kind IN ('theme','week') AND member.kind IN ('theme','week')
                AND member.classroom_id=d.classroom_id AND member.year_plan_id=d.year_plan_id AND member.theme_id=d.theme_id)), '[]'::jsonb),
        COALESCE(d.sign_chain,(SELECT legacy_signers::jsonb->CASE WHEN d.kind='branch' THEN 'branch' ELSE 'week' END
            FROM edu_settings WHERE id=1),'[]'::jsonb), d.approvals))
WHERE d.print_snapshot->>'origin'='LEGACY_BASELINE' AND d.print_snapshot->'slots'='[]'::jsonb;

DROP FUNCTION edu_legacy_print_slots(jsonb,jsonb,jsonb);
