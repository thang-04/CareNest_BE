---
id: ENV-261030-flyway-renumbered-stale-db
type: env
modules: [deployment]
rules: []
status: workaround
date: 2026-10-30
keywords: [flyway, migration, checksum mismatch, Validate failed, merge dev, đổi số migration, carenest_merge]
similar_to: [ENV-261016-flyway-duplicate-version-stale-target]
---

# ENV-261030-flyway-renumbered-stale-db — Flyway checksum mismatch hàng loạt sau khi merge dev đổi số migration

## Symptom
`mvnw spring-boot:run -Dspring-boot.run.profiles=dev` dừng khi khởi tạo context:
```text
Error creating bean with name 'flywayInitializer' ... Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 7
... (lặp tới version 31)
```

## Điều kiện tái hiện
DB local (`carenest_merge` qua pgproxy cổng 5433, theo `DB_URL` trong `.env`) migrate ngày 2026-10-28 theo số version cũ của nhánh (V7 = create age groups). Sau merge `origin/dev` (commit `0060947`), migration của nhánh dời +3 để nhường V7–V9 (permissions) cho dev ⇒ cùng version nhưng nội dung khác.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | So `flyway_schema_history` với tên file | V7 DB = `create age groups`, file V7 = `create_permissions` | Lỗi ở trạng thái DB, không ở source |
| 2 | Migrate DB rỗng mới với source hiện tại | 38 migration tới V37, app start OK | Xác nhận source đúng |
| 3 | Đổi tên DB cũ thành `carenest_merge_bak_20261030`, đổi DB mới thành `carenest_merge` | App start, `Schema "public" is up to date` | Cách xử lý; không dùng `flyway repair` vì schema cũ thiếu bảng của dev |

## Root cause
Đổi số migration khi merge là hợp lệ với DB chung (chưa áp nhánh), nhưng DB local đã áp số cũ thì lịch sử Flyway không còn khớp.

## Fix
Workaround: dựng lại DB local (giữ bản backup bằng đổi tên). Dữ liệu tự tạo trên DB cũ cần chép tay từ bản backup nếu cần.

## Regression test
Không áp dụng (lỗi môi trường dữ liệu local).

## Ảnh hưởng
Mọi máy dev đã chạy nhánh G94-226 trước commit `0060947`. DB `carenest` (cổng 5432) cũng còn lịch sử cũ (tới V25) — gặp lỗi tương tự nếu trỏ vào.

## Ca ngày 20/11/2026 — V145–V148 của nhánh giáo dục

- DB local đã chạy bốn file giáo dục V145–V148 trước khi merge. Đối chiếu tên file và checksum cho thấy SQL khớp V160–V163; toàn bộ migration có số còn lại đã áp đều khớp. `clean` chỉ xử lý file build cũ, không chỉnh được lịch sử DB.
- Tạo bản sao DB. Chỉ trên bản sao, đổi version và tên file cho đúng bốn dòng giáo dục sau khi kiểm checksum, rồi chạy Flyway một lần với `SPRING_FLYWAY_OUT_OF_ORDER=true` để áp các migration dev V145–V159 còn thiếu. Chạy lại bằng cấu hình Flyway bình thường. DB gốc vẫn dùng được với nhánh cũ.
- Trong lúc nâng cấp, `R__dev_10_planned_admissions.sql` gặp mã trẻ mẫu trùng bản ghi đang học không có cơ sở tiếp nhận. Seed nay chỉ tạo hồ sơ dự tuyển cho bản ghi mẫu vừa thêm hoặc bản ghi khớp dữ liệu mẫu và còn chờ xếp lớp. Trang trống hoặc seed thất bại không phải lý do đổi mã trẻ đã có.
- Kiểm chứng trên bản sao: Flyway validate thành công, `/actuator/health` trả `UP`; số bản kế hoạch năm/tổ/tài liệu lớp khớp DB gốc. Chạy seed hai lần trong giao dịch rollback với mã mẫu mới tạo đúng hai hồ sơ, mã trùng có sẵn không bị đưa vào diện dự tuyển.

## Ca ngày 21/11/2026 — đổi tiếp V160–V163 giáo dục thành V163–V166

- Dev thêm V160–V162 (menu AI, G94-200) ⇒ bốn migration giáo dục đổi số thành V163–V166, giữ nguyên SQL (commit merge `0790df06`, Tiền duyệt). DB local đã chạy bản giáo dục dưới V160–V163 sẽ lỗi Flyway validate: đổi version và tên script của bốn dòng trong `flyway_schema_history` (trên bản sao DB) rồi áp V160–V162 của dev bằng `SPRING_FLYWAY_OUT_OF_ORDER=true`; V162 cần extension `vector` (image `pgvector/pgvector:pg17`).
