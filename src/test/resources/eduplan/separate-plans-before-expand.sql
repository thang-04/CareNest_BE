INSERT INTO user_accounts(id,keycloak_user_id,username,full_name,status) OVERRIDING SYSTEM VALUE
VALUES (910001,'synthetic-015','synthetic-015','Synthetic planner','ACTIVE');
INSERT INTO school_years(id,name,start_date,end_date) OVERRIDING SYSTEM VALUE
VALUES (910001,'2098-2099','2098-08-01','2099-07-31');
INSERT INTO campuses(id,code,name,is_main) OVERRIDING SYSTEM VALUE VALUES (910001,'S015','Synthetic campus',true);
INSERT INTO classrooms(id,campus_id,school_year_id,age_group_id,name) OVERRIDING SYSTEM VALUE
SELECT 910001,910001,910001,id,'Synthetic class' FROM age_groups WHERE code='ag-4';

INSERT INTO edu_year_plans(id,code,school_year_id,age_group_id,title,description,status,owner_id,
    plan_level,curriculum_source_plan_id,legacy_team_without_source,version_no,source_plan_id,
    approvals,print_snapshot,completion_policy,content_revision,created_request_id)
OVERRIDING SYSTEM VALUE
SELECT v.id,v.code,910001,a.id,v.code,'Synthetic payload',v.status,910001,v.level,v.curriculum,v.legacy,v.version,v.source,
    '[{"reviewerId":910001,"slot":2}]'::jsonb,'{"signature":"Synthetic"}'::jsonb,'{"reviewers":["PRINCIPAL"]}'::jsonb,7,
    ('00000000-0000-0000-0000-'||lpad(v.id::text,12,'0'))::uuid
FROM (VALUES
    (910101,'Y-1','ag-4','APPROVED','SCHOOL_YEAR',NULL::bigint,false,1,NULL::bigint),
    (910102,'Y-2','ag-4','DRAFT','SCHOOL_YEAR',NULL,false,2,910101),
    (910201,'T-1','ag-4','APPROVED','TEAM',910101,false,1,NULL),
    (910202,'T-2','ag-4','DRAFT','TEAM',910101,false,2,910201),
    (910301,'LEG-1','ag-3','APPROVED','TEAM',NULL,true,1,NULL),
    (910302,'LEG-2','ag-3','DRAFT','TEAM',NULL,true,2,910301)
) v(id,code,age,status,level,curriculum,legacy,version,source) JOIN age_groups a ON a.code=v.age;

UPDATE edu_year_plans SET editing_by=910001,editing_since='2098-09-01T01:00:00Z',editing_at='2098-09-01T01:00:00Z',
    editing_from='DRAFT',edit_session_id='00000000-0000-0000-0001-000000910202',
    last_save_request_id='00000000-0000-0000-0002-000000910202' WHERE id=910202;

DO $$ DECLARE p bigint; d bigint; BEGIN
    SELECT min(id) INTO d FROM edu_domains;
    FOREACH p IN ARRAY ARRAY[910101,910201,910301] LOOP
        INSERT INTO edu_plan_sub_domains(id,year_plan_id,domain_id,sort_order,text,lineage_id) VALUES (p,p,d,1,'Synthetic subdomain',p);
        INSERT INTO edu_plan_expected_results(id,year_plan_id,domain_id,sort_order,text,lineage_id,sub_domain_id)
            VALUES (p,p,d,2,'Synthetic expected result',p,p);
        INSERT INTO edu_plan_goals(id,year_plan_id,domain_id,sort_order,text,lineage_id,goal_no,sub_domain_id,expected_result_id,display_label)
            VALUES (p,p,d,3,'Synthetic goal',p,1,p,p,'MT 1');
        INSERT INTO edu_goal_contents(id,goal_id,sort_order,text,lineage_id) OVERRIDING SYSTEM VALUE VALUES (p,p,1,'Synthetic content',p);
        INSERT INTO edu_themes(id,year_plan_id,sort_order,name,abbr,start_date,end_date,lineage_id)
            OVERRIDING SYSTEM VALUE VALUES (p,p,1,'Synthetic theme','ST','2098-09-01','2098-09-12',p);
        INSERT INTO edu_content_theme_ticks(content_id,theme_id) VALUES (p,p);
        INSERT INTO edu_breaks(id,year_plan_id,sort_order,label,start_date,end_date)
            OVERRIDING SYSTEM VALUE VALUES (p,p,1,'Synthetic break','2098-09-15','2098-09-16');
        INSERT INTO edu_plan_events(id,year_plan_id,action,action_label,actor_id,actor_name)
            OVERRIDING SYSTEM VALUE VALUES (p,p,'CREATE','Synthetic create',910001,'Synthetic planner');
        INSERT INTO edu_plan_branch_templates(id,year_plan_id,theme_id,lineage_id,name,start_date,end_date,sort_order)
            OVERRIDING SYSTEM VALUE VALUES (p,p,p,p,'Synthetic branch','2098-09-01','2098-09-12',1);
        INSERT INTO edu_document_authors(id,year_plan_id,user_id,origin)
            VALUES (p,p,910001,'CREATOR');
    END LOOP;
END $$;
INSERT INTO edu_class_docs(id,doc_key,kind,classroom_id,year_plan_id,content,approvals,history,created_by,updated_by,print_snapshot)
VALUES (910201,'synthetic-015-class','branch',910001,910201,'{"branches":["Synthetic"]}','[{"actor":910001}]','[{"action":"SAVE"}]',910001,910001,'{"class":"Synthetic"}');
INSERT INTO edu_document_authors(id,class_doc_id,user_id,origin) VALUES (910901,910201,910001,'CONTENT_WRITE');
