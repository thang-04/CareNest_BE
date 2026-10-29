# ADR-0019 — Module kế hoạch giáo dục làm lại trên `dev`

- Status: ACCEPTED
- Date: 2026-10-29
- Liên quan: EDU-01..10, OBS-07, OBS-08 (REMOVED), AUTH-23, ADR-0006 (SUPERSEDED), ADR-0017; P-01 (đóng), P-29

## Bối cảnh
- Trước 05/10: CareNest không làm giáo án; GoKids/eDoc giữ việc soạn và duyệt (OBS-08, ADR-0006 OPEN chờ P-01).
- Report 1 v2 (19/10) đưa kế hoạch giáo dục vào phạm vi (FE-04).
- Nhánh `feature/G94-226-BE-FEAT-09-school-year-goals` đã làm mục tiêu năm học: rule EDU-01..09 (Tiền 22/10), migration V7–V10 và `ADR-0017-education-plan-module.md`. Nhánh này tách trước code phân quyền, trùng số migration và trùng số ADR-0017 với `dev`.
- Khảo sát team 27/10:
  - EDU-09: chuỗi mục tiêu năm → chủ đề nhánh → chủ đề tuần → kế hoạch ngày, duyệt không ký.
  - EDU-02: chỉ duyệt một lần đầu năm.
  - INT-02: làm lại trên `dev`.
- User chốt 29/10: plan khảo sát viết nhóm EDU lên `dev`.

## Quyết định
- Thêm module `education-plan` trên `dev`: rule là nhóm EDU trong `BUSINESS_RULES.md`; card `docs/modules/education-plan.md`, flow `docs/business/flows/education-plan.md` (có bảng đối chiếu rule G94-226 → `dev`).
- Kế hoạch giáo dục soạn và duyệt trong CareNest, không tích hợp GoKids/eDoc.
- Ngữ cảnh hoạt động cho đánh giá trẻ lấy từ kế hoạch ngày (OBS-07). Thay ADR-0006.
- Quyền theo mã quyền của ADR-0017 (`dev`). Tổ trưởng là cột ma trận theo phân công (AUTH-23), không map cứng theo role.
- Code của nhánh G94-226 không merge nguyên trạng. Khi làm lại:
  - migration đánh số theo thứ tự merge vào `dev` (khảo sát INT-01);
  - không dùng lại số ADR-0017.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Làm lại trên `dev` theo rule khảo sát (chọn) | Không trùng migration/ADR; dùng phân quyền động có sẵn | Phải viết lại phần đã làm ở G94-226 |
| Rebase G94-226 lên `dev` rồi merge | Giữ code đã viết | Diff lớn (xóa code phân quyền), trùng V7–V10 và ADR-0017 |
| Giữ ngoài phạm vi, dùng GoKids/eDoc | Ít việc | Trái Report 1 v2 và khảo sát (SCO-01, EDU-09) |

## Hệ quả
- OBS-08 REMOVED; OBS-07 CONFIRMED; P-01 đóng.
- Bảng của G94-226 (`edu_year_plans`, `edu_goal_rows`, `edu_themes`…) chỉ là tham khảo khi thiết kế lại. Mô hình mới thêm chủ đề nhánh, chủ đề tuần, kế hoạch ngày và nhân bản phiên bản (EDU-08).
- FE và APP có mock giáo án riêng. Khi nối API phải theo contract BE.

## Còn chờ
- P-29: thời lượng và cách chia chủ đề.
- Thiết kế bảng và API của module: làm trong task code, kèm plan làn L.
