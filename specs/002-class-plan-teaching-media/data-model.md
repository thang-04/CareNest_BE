# Database Design

Migration mới V31 tạo edu_learning_resources; không sửa migration cũ/JSONB.

| Column | Type/constraint |
| --- | --- |
| id | BIGINT identity PK |
| class_doc_id | BIGINT not null FK edu_class_docs, index |
| activity_key | VARCHAR180 nullable |
| activity_signature | VARCHAR64 nullable, cùng null/non-null với key (explicit IS NOT NULL) |
| activity_label | VARCHAR1000 not null |
| original_name | VARCHAR255 not null sanitized |
| content_type | VARCHAR120 not null |
| byte_size | BIGINT positive not null |
| storage_key | VARCHAR100 unique not null, không expose |
| upload_request_id | UUID not null, unique(class_doc_id,upload_request_id) |
| uploaded_by | BIGINT not null FK user_accounts |
| created_at | TIMESTAMPTZ not null |

Active tính theo current activity key + SHA256 UTF8 toàn row.activities chuẩn hóa CRLF/LF, không trim. Detach null key/signature, giữ tệp. Parent lock mọi mutation; assignment/status trong transaction. Reader active only, editor thấy unbound. Không BLOB/base64.
