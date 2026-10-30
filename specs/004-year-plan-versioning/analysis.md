# Analyze — feature 004 (speckit-analyze, 2026-10-28)

## Lượt 1
| ID | Loại | Mức | Vị trí | Phát hiện | Xử lý |
| --- | --- | --- | --- | --- | --- |
| A1 | Coverage | MEDIUM | spec Edge "hai người cùng tạo" | Không task nào nêu xử lý tạo đồng thời | Thêm vào T016 (unique index ⇒ 7028) |
| A2 | Coverage | LOW | US4-9 nhãn "mới" | Không task nêu rõ | Thêm vào T030 |
| A3 | Consistency | LOW | contracts mã 7005 | Sai mã YEAR_PLAN_EXISTS (thực tế 7002) | Đã sửa contracts |

## Lượt 2 (sau sửa)
- Mọi FR-001..028 có ≥1 task (FR→task: 001–004→T010–T015; 010–016→T001–T009,T016–T024; 020–028→T025–T031).
- Mọi SC có ca kiểm ở quickstart.
- Không còn mâu thuẫn thuật ngữ (lineage, phiên bản hiện hành/đã thay thế, versionChecks) giữa spec/research/data-model/contracts.
- Constitution: template chưa ban hành, dùng gate AGENTS.md — không vi phạm.

**Kết quả: sạch (0 CRITICAL/HIGH/MEDIUM còn mở).**
