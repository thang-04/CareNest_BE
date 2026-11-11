# Data model — Biên bản kiểm thực, chữ ký ảnh, trường lưu mẫu

Nguồn: `docs/plans/active/2026-11-10-inspection-records-signatures.md`, ADR-0024. Migration bắt đầu từ V84 (V83 là gộp bảng bán trú). Kiểm lại số ngay trước khi tạo.

## Dùng lại dữ liệu có sẵn (không tạo bảng)

| Nguồn | Dùng cho |
| --- | --- |
| `meal_plan_weeks` (+ `days` jsonb, `meal_plan_items`) | Món theo bữa, nhóm khối (M2, M3, M5) |
| Suất đã xác nhận (`MealCount`) | Số suất tách Nhà trẻ / Mẫu giáo |
| `fresh_checks`, `fresh_check_lines` | Hàng tươi nhập trong ngày, khối lượng nhận (M1 tươi) |
| `stock_receipts`, `stock_receipt_lines` | Hàng khô: nhà cung cấp, ngày nhập, hạn dùng (M1 khô) |
| `suppliers` | Tên, địa chỉ, điện thoại, người giao |
| `food_samples` (`dish_ids`) | Dòng sổ lưu mẫu (M5), nhãn M4, M3 gộp |

## Bảng mới

### `user_signatures` (V84)
| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| id | BIGINT identity PK | |
| user_id | BIGINT NOT NULL → `user_accounts` | |
| storage_key | VARCHAR(36) NOT NULL UNIQUE | UUID tệp trong kho |
| content_type | VARCHAR(30) NOT NULL | `image/png`, `image/jpeg` (sniff byte đầu) |
| size_bytes | INT NOT NULL | ≤ 2 MB |
| is_default | BOOLEAN NOT NULL DEFAULT false | chỉ mục duy nhất một phần `(user_id) WHERE is_default` |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |

Ràng buộc: tối đa 5 dòng mỗi `user_id` (kiểm ở service dưới khóa dòng tài khoản).

### `inspection_sheets` (V85)
| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| id | BIGINT identity PK | |
| campus_id | BIGINT NOT NULL → `campuses` | |
| sheet_date | DATE NOT NULL | |
| tab | VARCHAR(10) NOT NULL | `FRESH`, `DRY`, `PROCESS`, `SERVE`, `SAMPLE` |
| status | VARCHAR(10) NOT NULL DEFAULT 'DRAFT' | `DRAFT`, `SIGNED` |
| version | INT NOT NULL DEFAULT 0 | tăng khi nội dung đổi hoặc sửa lại |
| header | JSONB NOT NULL DEFAULT '{}' | `{place, checkedAt}` |
| rows | JSONB NOT NULL DEFAULT '[]' | `[{key, …ô nhập}]` theo bảng cột của mẫu |
| signature | JSONB NULL | `{signedBy, signedByName, signedAt, documentVersion, imageKey, contentType, sizeBytes}` |
| snapshot | JSONB NULL | dòng lúc ký (chỉ khi SIGNED) |
| history | JSONB NOT NULL DEFAULT '[]' | `[{action, actorId, actorName, at, note, imageKey?}]` |
| updated_by | BIGINT → `user_accounts` | |
| updated_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |

Ràng buộc: `UNIQUE (campus_id, sheet_date, tab)`; `CHECK tab IN (...)`; `CHECK status IN (...)`; `CHECK (status = 'SIGNED') = (signature IS NOT NULL)`; chỉ mục `(campus_id, sheet_date)`.

## Cột thêm vào bảng có sẵn

| Bảng | Cột | Ghi chú |
| --- | --- | --- |
| `fresh_checks` (V85) | `supplier_id BIGINT NULL → suppliers` | nhà cung cấp của lần giao; null với dữ liệu cũ |
| `food_samples` (V86) | `dish_details JSONB NOT NULL DEFAULT '[]'` | `[{dishId, weight, container, temperature}]`; `dish_ids` giữ nguyên |

## Vòng đời

Biên bản chưa có dòng = chưa nhập gì (khung dựng từ nguồn). Lưu nháp tạo dòng (`DRAFT`). Ký → `SIGNED` (chép ảnh, chụp dòng, ghi `history`). Sửa lại → `DRAFT`, `signature` và `snapshot` về null, `history` thêm `REOPENED` (giữ `imageKey` lần ký cũ). Không xóa biên bản.

## Migration / backfill

Không backfill: bảng mới trống, cột mới null/`[]`. Không dùng lại bảng cũ nào cho chữ ký vì chưa có.
