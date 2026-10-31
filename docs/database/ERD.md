# ERD

> Status: **ĐANG ĐIỀN** — cập nhật cùng thay đổi với migration. Chi tiết cột: `DATA_DICTIONARY.md`. Mô hình khái niệm đầy đủ: `docs/business/DOMAIN_MODEL.md`.

## identity-access, school-structure, child (V1–V3)

```mermaid
erDiagram
    user_accounts ||--o{ staff_assignments : "được phân công"
    user_accounts ||--o{ guardian_child_links : "là phụ huynh"
    campuses ||--o{ classrooms : "có"
    school_years ||--o{ classrooms : "có"
    campuses |o--o{ staff_assignments : "VP/bếp"
    classrooms |o--o{ staff_assignments : "GV"
    classrooms ||--o{ enrollments : "có"
    children ||--o{ enrollments : "học"
    children ||--o{ guardian_child_links : "có"

    user_accounts {
        BIGINT id PK
        VARCHAR keycloak_user_id UK "JWT sub"
        VARCHAR username UK
        VARCHAR status "ACTIVE|INACTIVE"
    }
    campuses {
        BIGINT id PK
        VARCHAR code UK
        VARCHAR name
    }
    school_years {
        BIGINT id PK
        VARCHAR name UK
        DATE start_date
        DATE end_date
    }
    classrooms {
        BIGINT id PK
        BIGINT campus_id FK
        BIGINT school_year_id FK
        VARCHAR name
    }
    staff_assignments {
        BIGINT id PK
        BIGINT user_id FK
        VARCHAR staff_role "PRINCIPAL|VICE_PRINCIPAL|TEACHER|KITCHEN_STAFF"
        BIGINT campus_id FK "VP/bếp"
        BIGINT classroom_id FK "GV"
        DATE valid_from
        DATE valid_to "NULL = đang hiệu lực"
    }
    children {
        BIGINT id PK
        VARCHAR full_name
        DATE date_of_birth
    }
    enrollments {
        BIGINT id PK
        BIGINT child_id FK
        BIGINT classroom_id FK
        DATE valid_from
        DATE valid_to
    }
    guardian_child_links {
        BIGINT id PK
        BIGINT user_id FK
        BIGINT child_id FK
        VARCHAR relationship
    }
```

## education-plan — Ba bảng mục tiêu (V35)

```mermaid
erDiagram
    edu_year_plans ||--o{ edu_plan_sub_domains : owns
    edu_year_plans ||--o{ edu_plan_expected_results : owns
    edu_year_plans ||--o{ edu_plan_goals : owns
    edu_domains ||--o{ edu_plan_sub_domains : categorizes
    edu_domains ||--o{ edu_plan_expected_results : categorizes
    edu_domains ||--o{ edu_plan_goals : categorizes
    edu_plan_sub_domains |o--o{ edu_plan_expected_results : groups
    edu_plan_sub_domains |o--o{ edu_plan_goals : goals
    edu_plan_expected_results |o--o{ edu_plan_goals : goals
    edu_plan_goals ||--o{ edu_goal_contents : contents
    edu_curriculum_sub_domains |o--o{ edu_curriculum_expected_results : groups
    edu_curriculum_expected_results ||--o{ edu_curriculum_goals : goals
    edu_curriculum_sub_domains |o--o{ edu_plan_sub_domains : source
    edu_curriculum_expected_results |o--o{ edu_plan_expected_results : source
    edu_curriculum_goals |o--o{ edu_plan_goals : source
    edu_goal_contents ||--o{ edu_content_theme_ticks : ticks
    edu_themes ||--o{ edu_content_theme_ticks : ticks
```

Ba bảng có ID từ sequence chung; FK cha ghép với year_plan_id/domain_id. FK goal→group kiểm sub_domain_key để sub của goal và group khớp kể cả NULL. Cấp cha tùy chọn theo user 29/10/2026; không đổi rows/kind API. Chi tiết: [ADR-0021](../decisions/ADR-0021-year-plan-goal-hierarchy.md).

## identity-access — phân quyền (V7, ADR-0017)

```mermaid
erDiagram
    user_accounts ||--o{ user_permission_overrides : "có ngoại lệ"
    user_accounts |o--o{ role_permissions : "updated_by"
    user_accounts ||--o{ permission_change_log : "actor / target_user"

    role_permissions {
        BIGINT id PK
        VARCHAR role "VICE_PRINCIPAL|TEACHER|KITCHEN_STAFF"
        VARCHAR permission_code
    }
    user_permission_overrides {
        BIGINT id PK
        BIGINT user_id FK
        VARCHAR permission_code
        VARCHAR effect "GRANT|REVOKE"
        VARCHAR scope "CAMPUS|SCHOOL, chỉ GRANT"
        BIGINT created_by FK "khác user_id"
    }
    permission_change_log {
        BIGINT id PK
        UUID batch_id
        VARCHAR target_role "hoặc target_user_id"
        VARCHAR before_state
        VARCHAR after_state
        VARCHAR before_scope
        VARCHAR after_scope
    }
```
