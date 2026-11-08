---
id: ENV-261106-dev-migration-renumber-checksum
type: env
modules: [child, tooling]
rules: []
status: fixed
date: 2026-11-06
keywords: [flyway, checksum mismatch, migration renumber, đổi số migration, V68, V80, dev, clone prod]
similar_to: [BUG-261105-enrollment-photo-projection]
---

# ENV-261106-dev-migration-renumber-checksum — API không khởi động trên DB có lịch sử migration của production

## Symptom
API dev (DB `carenest_dev` clone từ production) restart liên tục, cổng 18311 từ chối kết nối:
```text
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 68
```
(lặp lại cho mọi version 68–79). Test `FoodMicronutrientsMigrationTest` cũng fail vì `target("73")` không còn là migration vi chất.

## Điều kiện tái hiện
DB đã chạy migration theo số của `main` (production) ⇒ khởi động API build từ `dev` trước bản sửa.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Sửa test sang `target("74")` cho khớp số mới | Test pass | Chỉ che triệu chứng: số trên `dev` lệch production, release vào `main` sẽ làm API production không lên |

## Root cause
Commit `9b89778` chèn `V68__child_enrollment_photos.sql` vào giữa, đẩy V68–V79 đã chạy trên production lên +1 (nội dung không đổi, chỉ đổi số) ⇒ Flyway so checksum theo version thấy lệch. Migration đã có trên `main` là bất biến cả về **số** lẫn nội dung.

## Fix
Trả V68–V79 về đúng số của `main` (nội dung giống hệt, đã so từng file), chuyển `child_enrollment_photos` xuống `V80` (chỉ thêm cột, không phụ thuộc migration khác), giữ `V81__selected_parent_account`. Test vi chất trở lại `target("73")`. DB local/dev đã chạy theo số lệch ⇒ tạo lại DB (dữ liệu dev seed lại được).

## Phòng ngừa
Merge nhánh có migration mới: số mới luôn lớn hơn số lớn nhất trên `main`/`dev`, không chèn giữa. CI giờ verify cả push `dev` nên phát hiện sớm.
