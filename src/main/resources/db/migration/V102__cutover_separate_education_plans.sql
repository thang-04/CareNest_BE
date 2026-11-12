-- Chỉ cutover đồng bộ với app mới, sau maintenance/backup và rehearsal trên DB scratch.
LOCK TABLE edu_year_plans, edu_team_plans, edu_plan_goals, edu_plan_sub_domains,
    edu_plan_expected_results, edu_themes, edu_breaks, edu_plan_events,
    edu_document_authors, edu_plan_branch_templates, edu_class_docs IN ACCESS EXCLUSIVE MODE;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint c WHERE c.contype='f' AND c.confrelid='edu_year_plans'::regclass
        AND c.conrelid NOT IN ('edu_year_plans'::regclass,'edu_team_plans'::regclass,'edu_team_review_items'::regclass,
            'edu_plan_goals'::regclass,'edu_plan_sub_domains'::regclass,'edu_plan_expected_results'::regclass,
            'edu_themes'::regclass,'edu_breaks'::regclass,'edu_plan_events'::regclass,
            'edu_document_authors'::regclass,'edu_plan_branch_templates'::regclass,'edu_class_docs'::regclass)) THEN
        RAISE EXCEPTION 'Unexpected header consumer FK; inventory required before cutover';
    END IF;
    IF EXISTS (SELECT 1 FROM edu_team_plans) THEN
        RAISE EXCEPTION 'Expanded team table must remain empty until cutover; inspect IDs/payload before retry';
    END IF;
    IF EXISTS (SELECT 1 FROM edu_class_docs d JOIN edu_year_plans p ON p.id=d.year_plan_id WHERE p.plan_level <> 'TEAM') THEN
        RAISE EXCEPTION 'Class document references annual plan; resolve from confirmed source before cutover';
    END IF;
    IF EXISTS (SELECT 1 FROM edu_year_plans p JOIN edu_year_plans s ON s.id=p.source_plan_id WHERE s.plan_level <> p.plan_level) THEN
        RAISE EXCEPTION 'Cross-level version lineage; manual inventory required';
    END IF;
    IF EXISTS (SELECT 1 FROM edu_year_plans p JOIN edu_year_plans s ON s.id=p.curriculum_source_plan_id
        WHERE s.plan_level <> 'SCHOOL_YEAR' OR s.school_year_id <> p.school_year_id OR s.age_group_id <> p.age_group_id) THEN
        RAISE EXCEPTION 'Invalid annual curriculum source; manual inventory required';
    END IF;
END $$;

CREATE TEMP TABLE split_header_snapshot ON COMMIT DROP AS SELECT id,plan_level,to_jsonb(p)-'plan_level' payload FROM edu_year_plans p;
CREATE TEMP TABLE split_child_snapshot (table_name text, id bigint, payload jsonb) ON COMMIT DROP;

-- Làm mới owner sau giai đoạn expand, gồm cả bản ghi do app cũ vừa tạo.
DO $$ DECLARE tbl text; BEGIN
    FOREACH tbl IN ARRAY ARRAY['edu_plan_goals','edu_plan_sub_domains','edu_plan_expected_results','edu_themes',
        'edu_breaks','edu_plan_events','edu_document_authors','edu_plan_branch_templates','edu_class_docs'] LOOP
        EXECUTE format('UPDATE %I SET school_year_plan_id=NULL,team_plan_id=NULL',tbl);
        EXECUTE format('UPDATE %I c SET school_year_plan_id=CASE WHEN p.plan_level=''SCHOOL_YEAR'' THEN p.id END,
            team_plan_id=CASE WHEN p.plan_level=''TEAM'' THEN p.id END FROM edu_year_plans p WHERE c.year_plan_id=p.id',tbl);
        EXECUTE format('INSERT INTO split_child_snapshot SELECT %L,id,to_jsonb(c)-''year_plan_id'' FROM %I c',tbl,tbl);
    END LOOP;
END $$;

DO $$ DECLARE cols text; BEGIN
    SELECT string_agg(quote_ident(column_name),',' ORDER BY ordinal_position) INTO cols
    FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='edu_year_plans' AND column_name <> 'plan_level';
    EXECUTE format('INSERT INTO edu_team_plans (%s) OVERRIDING SYSTEM VALUE SELECT %s FROM edu_year_plans WHERE plan_level=''TEAM''',cols,cols);
    IF EXISTS ((SELECT id,payload FROM split_header_snapshot WHERE plan_level='TEAM'
        EXCEPT SELECT id,to_jsonb(t)-'previous_source_plan_id' FROM edu_team_plans t)
        UNION ALL (SELECT id,to_jsonb(t)-'previous_source_plan_id' FROM edu_team_plans t
        EXCEPT SELECT id,payload FROM split_header_snapshot WHERE plan_level='TEAM')) THEN
        RAISE EXCEPTION 'Team header payload mismatch before deletion';
    END IF;
END $$;
SET CONSTRAINTS ALL IMMEDIATE;

-- Inventory tự lấy định nghĩa cũ, giữ deferred/unique/index và thay cả hai đầu FK ghép.
CREATE TEMP TABLE split_constraints ON COMMIT DROP AS
SELECT c.conrelid::regclass::text tbl,c.conname,c.contype,pg_get_constraintdef(c.oid) definition
FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=ANY(c.conkey)
WHERE c.connamespace=(SELECT oid FROM pg_namespace WHERE nspname=current_schema()) AND a.attname='year_plan_id';
CREATE TEMP TABLE split_indexes ON COMMIT DROP AS
SELECT tablename,indexname,indexdef FROM pg_indexes i WHERE schemaname=current_schema() AND indexdef LIKE '%year_plan_id%'
AND NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_class x ON x.oid=c.conindid WHERE x.relname=i.indexname AND c.connamespace=(SELECT oid FROM pg_namespace WHERE nspname=current_schema()));
DO $$ DECLARE r record; BEGIN
    FOR r IN SELECT * FROM split_constraints ORDER BY CASE WHEN contype='f' THEN 0 ELSE 1 END LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',r.tbl,r.conname);
    END LOOP;
    FOR r IN SELECT * FROM split_indexes LOOP EXECUTE format('DROP INDEX %I',r.indexname); END LOOP;
END $$;

DO $$ DECLARE r record; owner_col text; suffix text; definition text; BEGIN
    FOREACH owner_col IN ARRAY ARRAY['school_year_plan_id','team_plan_id'] LOOP
        suffix := CASE WHEN owner_col='school_year_plan_id' THEN '_sy' ELSE '_team' END;
        FOR r IN SELECT * FROM split_constraints WHERE contype='u' LOOP
            EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s',r.tbl,left(r.conname,55)||suffix,replace(r.definition,'year_plan_id',owner_col));
        END LOOP;
        FOR r IN SELECT * FROM split_constraints WHERE contype='f' LOOP
            definition := replace(r.definition,'year_plan_id',owner_col);
            IF owner_col='team_plan_id' THEN definition := replace(definition,'REFERENCES edu_year_plans','REFERENCES edu_team_plans'); END IF;
            EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s',r.tbl,left(r.conname,55)||suffix,definition);
        END LOOP;
        FOR r IN SELECT * FROM split_indexes LOOP
            EXECUTE replace(replace(r.indexdef,r.indexname,left(r.indexname,55)||suffix),'year_plan_id',owner_col);
        END LOOP;
    END LOOP;
END $$;

DO $$ DECLARE tbl text; owner_check text; BEGIN
    FOREACH tbl IN ARRAY ARRAY['edu_plan_goals','edu_plan_sub_domains','edu_plan_expected_results','edu_themes',
        'edu_breaks','edu_plan_events','edu_document_authors','edu_plan_branch_templates','edu_class_docs'] LOOP
        owner_check := CASE WHEN tbl='edu_document_authors' THEN 'num_nonnulls(school_year_plan_id,team_plan_id,class_doc_id)=1'
            WHEN tbl='edu_class_docs' THEN 'school_year_plan_id IS NULL AND team_plan_id IS NOT NULL'
            ELSE 'num_nonnulls(school_year_plan_id,team_plan_id)=1' END;
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I CHECK (%s)',tbl,'ck_'||tbl||'_owner',owner_check);
        EXECUTE format('ALTER TABLE %I DROP COLUMN year_plan_id',tbl);
        EXECUTE format('DO $check$ BEGIN IF EXISTS ((SELECT id,payload FROM split_child_snapshot WHERE table_name=%L EXCEPT SELECT id,to_jsonb(c) FROM %I c)
            UNION ALL (SELECT id,to_jsonb(c) FROM %I c EXCEPT SELECT id,payload FROM split_child_snapshot WHERE table_name=%L))
            THEN RAISE EXCEPTION ''Child payload mismatch: %%'', %L; END IF; END $check$',tbl,tbl,tbl,tbl,tbl);
    END LOOP;
END $$;

DELETE FROM edu_year_plans WHERE plan_level='TEAM';
DROP INDEX uq_edu_plan_level_version;
DROP INDEX uq_edu_plan_level_open;
ALTER TABLE edu_year_plans DROP CONSTRAINT ck_edu_plan_level, DROP CONSTRAINT ck_edu_plan_source;
ALTER TABLE edu_year_plans DROP COLUMN plan_level, DROP COLUMN curriculum_source_plan_id,
    DROP COLUMN legacy_team_without_source, DROP COLUMN duration_policy_snapshot;
CREATE UNIQUE INDEX uq_edu_year_plan_version ON edu_year_plans(school_year_id,age_group_id,version_no);
CREATE UNIQUE INDEX uq_edu_year_plan_open ON edu_year_plans(school_year_id,age_group_id) WHERE status <> 'APPROVED';

CREATE SEQUENCE edu_plan_id_seq;
SELECT setval('edu_plan_id_seq',GREATEST(COALESCE((SELECT max(id) FROM edu_year_plans),0),COALESCE((SELECT max(id) FROM edu_team_plans),0))+1,false);
ALTER TABLE edu_year_plans ALTER COLUMN id DROP IDENTITY;
ALTER TABLE edu_team_plans ALTER COLUMN id DROP IDENTITY;
ALTER TABLE edu_year_plans ALTER COLUMN id SET DEFAULT nextval('edu_plan_id_seq');
ALTER TABLE edu_team_plans ALTER COLUMN id SET DEFAULT nextval('edu_plan_id_seq');

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM edu_year_plans y JOIN edu_team_plans t USING(id)) THEN RAISE EXCEPTION 'Duplicate plan IDs'; END IF;
    IF EXISTS ((SELECT id,payload-ARRAY['curriculum_source_plan_id','legacy_team_without_source','duration_policy_snapshot'] FROM split_header_snapshot WHERE plan_level='SCHOOL_YEAR'
        EXCEPT SELECT id,to_jsonb(p) FROM edu_year_plans p)
        UNION ALL (SELECT id,to_jsonb(p) FROM edu_year_plans p EXCEPT
        SELECT id,payload-ARRAY['curriculum_source_plan_id','legacy_team_without_source','duration_policy_snapshot'] FROM split_header_snapshot WHERE plan_level='SCHOOL_YEAR')) THEN
        RAISE EXCEPTION 'Annual header payload mismatch after contract';
    END IF;
END $$;
SET CONSTRAINTS ALL IMMEDIATE;

DO $$ DECLARE tbl text; BEGIN
    FOREACH tbl IN ARRAY ARRAY['edu_plan_goals','edu_plan_sub_domains','edu_plan_expected_results','edu_themes',
        'edu_breaks','edu_plan_events','edu_document_authors','edu_plan_branch_templates','edu_class_docs'] LOOP
        EXECUTE format('DO $check$ BEGIN IF EXISTS ((SELECT id,payload FROM split_child_snapshot WHERE table_name=%L EXCEPT SELECT id,to_jsonb(c) FROM %I c)
            UNION ALL (SELECT id,to_jsonb(c) FROM %I c EXCEPT SELECT id,payload FROM split_child_snapshot WHERE table_name=%L))
            THEN RAISE EXCEPTION ''Child payload mismatch after contract: %%'', %L; END IF; END $check$',tbl,tbl,tbl,tbl,tbl);
    END LOOP;
END $$;
