# Data Model: Danh mục Chương trình GDMN của Bộ

Migration `V30__add_edu_curriculum_catalog.sql` (cấu trúc) và `V31__seed_edu_curriculum_vbhn_01_2021.sql` (dữ liệu, sinh từ văn bản).

## edu_curriculum_sources — văn bản nguồn

| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| id | BIGINT identity PK | |
| code | VARCHAR(40) NOT NULL UNIQUE | `VBHN-01-2021-GDMN` |
| title | VARCHAR(300) NOT NULL | Văn bản hợp nhất Thông tư ban hành Chương trình giáo dục mầm non |
| document_no | VARCHAR(50) NOT NULL | 01/VBHN-BGDĐT |
| issued_date | DATE NOT NULL | 2021-04-13 |
| issuer | VARCHAR(150) NOT NULL | Bộ Giáo dục và Đào tạo |
| official_url | VARCHAR(500) NOT NULL | Cơ sở dữ liệu quốc gia về VBPL |
| retrieved_from_url | VARCHAR(500) NOT NULL | Trang toàn văn đã dùng để tách dữ liệu |
| retrieved_at | DATE NOT NULL | 2026-10-28 |
| scope_note | TEXT NOT NULL | Phạm vi lấy, cách ánh xạ, đoạn bãi bỏ |
| active | BOOLEAN NOT NULL DEFAULT TRUE | |
| created_at, updated_at | TIMESTAMPTZ | |

## edu_curriculum_goals — mục tiêu theo nhóm tuổi (mục D "Kết quả mong đợi")

| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| id | BIGINT identity PK | |
| source_id | FK edu_curriculum_sources NOT NULL | |
| age_group_id | FK age_groups NOT NULL | |
| domain_id | FK edu_domains NOT NULL | lĩnh vực chuẩn theo tên |
| sub_domain | VARCHAR(150) | lĩnh vực con (vận động, dinh dưỡng – sức khỏe, KPKH, toán, KPXH) |
| group_no | VARCHAR(10) | số nhóm của Bộ ("2") |
| group_text | TEXT NOT NULL | câu nhóm kết quả mong đợi |
| goal_no | VARCHAR(10) | số câu ("2.1"); NULL khi ô không đánh số |
| goal_text | TEXT NOT NULL | câu mục tiêu nguyên văn |
| section_ref | VARCHAR(150) NOT NULL | "Phần ba › D › I › a) › 2 › 2.1" |
| sort_order | INT NOT NULL | thứ tự trong văn bản theo nhóm tuổi |
| UNIQUE (source_id, age_group_id, sort_order) | | |

## edu_curriculum_goal_contents — nội dung (gạch đầu dòng) của mục tiêu

| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| id | BIGINT identity PK | |
| curriculum_goal_id | FK edu_curriculum_goals ON DELETE CASCADE | |
| text | TEXT NOT NULL | nguyên văn |
| sort_order | SMALLINT NOT NULL | |

## edu_curriculum_contents — gợi ý nội dung (mục C "Nội dung giáo dục theo độ tuổi")

| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| id | BIGINT identity PK | |
| source_id, age_group_id, domain_id | FK NOT NULL | |
| sub_domain | VARCHAR(150) | |
| category_no | VARCHAR(10) | |
| category | TEXT NOT NULL | tên mục nội dung |
| label | VARCHAR(300) | nhãn "Đi và chạy" (dòng "- X:"); chỉ để hiển thị |
| text | TEXT NOT NULL | câu gợi ý nguyên văn (thành nội dung khi thêm) |
| section_ref | VARCHAR(150) NOT NULL | |
| sort_order | INT NOT NULL | UNIQUE (source_id, age_group_id, sort_order) |

## edu_goal_rows (sửa)

- Thêm `curriculum_goal_id BIGINT NULL` FK edu_curriculum_goals ON DELETE SET NULL; index. Chỉ dòng `goal`.
- `EduGoalRow.copyFor` (004) chép theo.

## Quy tắc

- Danh mục chỉ đọc qua API; không màn sửa.
- `curriculum_goal_id` khi lưu phải thuộc cùng `age_group_id` với kế hoạch ⇒ sai: 400 `CURRICULUM_GOAL_INVALID` (7030).
- Câu trong kế hoạch là bản chép độc lập; đổi/xóa danh mục không đổi câu đã lấy.
