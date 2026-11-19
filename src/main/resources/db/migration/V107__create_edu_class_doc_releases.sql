-- Spec 002 FR-018 (Tiền duyệt 10/11/2026): mỗi lần hồ sơ chủ đề của lớp được ban hành giữ một bản để xem lại;
-- edu_class_docs.print_snapshot chỉ giữ bản ban hành gần nhất và chỉ có chuỗi ký.
CREATE TABLE edu_class_doc_releases (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    class_doc_id    BIGINT      NOT NULL,
    dossier_version BIGINT      NOT NULL,
    print_snapshot  JSONB       NOT NULL,
    approved_by     BIGINT      NOT NULL,
    released_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_edu_class_doc_releases_doc FOREIGN KEY (class_doc_id) REFERENCES edu_class_docs (id),
    CONSTRAINT fk_edu_class_doc_releases_approver FOREIGN KEY (approved_by) REFERENCES user_accounts (id),
    CONSTRAINT uq_edu_class_doc_releases_version UNIQUE (class_doc_id, dossier_version)
);

-- print_snapshot = { signatures: chuỗi ký lúc ban hành, members: nội dung kế hoạch chủ đề + giáo án tuần }
INSERT INTO edu_class_doc_releases (class_doc_id, dossier_version, print_snapshot, approved_by, released_at)
SELECT t.id,
       COALESCE(t.version, 0),
       jsonb_build_object(
           'signatures', t.print_snapshot,
           'members', (SELECT jsonb_agg(jsonb_build_object(
                                  'kind', m.kind, 'docKey', m.doc_key, 'weekStart', m.week_start,
                                  'content', m.content) ORDER BY m.id)
                       FROM edu_class_docs m
                       WHERE m.classroom_id = t.classroom_id
                         AND m.team_plan_id = t.team_plan_id
                         AND m.theme_id = t.theme_id
                         AND m.kind IN ('theme', 'week'))),
       t.updated_by,
       COALESCE(t.completed_at, t.updated_at)
FROM edu_class_docs t
WHERE t.kind = 'theme'
  AND t.status = 'APPROVED'
  AND t.print_snapshot IS NOT NULL
  AND t.updated_by IS NOT NULL;
