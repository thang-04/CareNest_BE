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
        VARCHAR code UK "V10"
        VARCHAR full_name
        DATE date_of_birth
        VARCHAR status "V10: ACTIVE|PENDING_PLACEMENT|LEFT"
        BIGINT intake_campus_id FK "V10: khi chưa có lớp"
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

## child — tiếp nhận trẻ, phụ huynh trên hồ sơ, khai báo sức khỏe (V10)

```mermaid
erDiagram
    campuses |o--o{ children : "tiếp nhận (chưa có lớp)"
    children ||--o| child_profiles : "hành chính"
    children ||--o{ child_guardians : "phụ huynh trên hồ sơ (≤3)"
    user_accounts |o--o{ child_guardians : "đã kích hoạt"
    children ||--o| child_health_declarations : "khai báo sức khỏe"
    child_health_declarations ||--o{ child_allergies : "dị ứng"
    children ||--o{ child_health_declaration_history : "lịch sử khai báo"
    children ||--o{ child_history : "lịch sử hồ sơ"
    user_accounts ||--o{ parent_sms_logs : "SMS tài khoản"

    child_profiles {
        BIGINT child_id PK "FK children"
        VARCHAR premature "YES|NO"
        VARCHAR payment_source "BHYT"
    }
    child_guardians {
        BIGINT id PK
        BIGINT child_id FK
        SMALLINT position "0-2"
        VARCHAR phone "UK cùng child_id"
        VARCHAR citizen_id "UK cùng child_id"
        BIGINT user_id FK "NULL = chưa kích hoạt"
    }
    child_health_declarations {
        BIGINT child_id PK "FK children"
        VARCHAR allergies_state
        VARCHAR tb_contact_state "REPORTED|NONE_REPORTED"
    }
    child_allergies {
        BIGINT id PK
        BIGINT child_id FK
        VARCHAR name
    }
    child_health_declaration_history {
        BIGINT id PK
        BIGINT child_id FK
        VARCHAR action
        JSONB allergy_change
        JSONB history_change
    }
    child_history {
        BIGINT id PK
        BIGINT child_id FK
        VARCHAR action
        JSONB changes
    }
    parent_sms_logs {
        BIGINT id PK
        BIGINT user_id FK
        VARCHAR purpose
        VARCHAR status "SENT|FAILED"
    }
```
`child_code_sequences` (số thứ tự mã trẻ theo năm) không có quan hệ.

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
        BIGINT created_by FK "khác user_id"
    }
    permission_change_log {
        BIGINT id PK
        UUID batch_id
        VARCHAR target_role "hoặc target_user_id"
        VARCHAR before_state
        VARCHAR after_state
    }
```
