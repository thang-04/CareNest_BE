# Data model — Feature014

Migration mới `V82__create_menu_ai_drafts.sql` (số lấy sau V81 của `dev` lúc viết; trước khi commit kiểm lại số lớn nhất trên `dev`).

## `menu_ai_drafts`
| Cột | Kiểu | Ràng buộc / ý nghĩa |
| --- | --- | --- |
| `id` | bigserial | PK |
| `version` | bigint | khóa lạc quan |
| `kind` | varchar(20) | `MENU_WEEKLY`, `MENU_DAILY`, `MENU_DAY_FIX` |
| `menu_block` | varchar(20) | khối |
| `week_start` | date | bắt buộc với `MENU_WEEKLY`, `MENU_DAY_FIX` (thứ Hai) |
| `target_date`, `target_session`, `from_dish_id` | date, varchar, bigint | chỉ `MENU_DAY_FIX` |
| `status` | varchar(20) | `PENDING_REVIEW`, `APPLIED`, `DISMISSED`, `NO_FEASIBLE` |
| `source` | varchar(20) | `AI`, `RULE_BASED`; null khi `NO_FEASIBLE` |
| `fallback_reason` | varchar(30) | null khi `source=AI` |
| `provider`, `model`, `prompt_version` | varchar | null khi không gọi được model |
| `repair_rounds`, `latency_ms` | int | metadata |
| `request_json` | jsonb | mùa, mong muốn đã cắt độ dài |
| `output_json` | jsonb | ô → `dishId` thật, lý do đã làm sạch; không đổi sau khi tạo |
| `score_json` | jsonb | `{ai, baseline, breakdown}` |
| `issues_json` | jsonb | issue evaluator của phương án được chọn hoặc nguyên nhân `NO_FEASIBLE` |
| `input_fingerprint` | varchar(64) | SHA-256 của món ứng viên (id + `updated_at`), định mức, cấu hình bữa dùng lúc tạo |
| `created_by`, `created_at` | bigint, timestamptz | |
| `decided_by`, `decided_at` | bigint, timestamptz | khi apply/dismiss |

Index: (`kind`, `menu_block`, `week_start`, `created_at desc`). CHECK theo `kind` cho cột bắt buộc. Không FK tới trẻ. Không xóa nháp (lịch sử đo giá trị).

## Không đổi
`meal_plan_weeks/days/items`, `menu_templates`, món, thực phẩm, định mức: chỉ đọc; apply ghi qua `MealPlanService.saveMealPlan`.
