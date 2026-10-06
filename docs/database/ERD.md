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
