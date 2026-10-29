# Module — education-plan

Feature: `eduplan` (PROPOSED — tên nhánh G94-226; trong controller/, service/, repository/, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế (làm lại trên `dev` — ADR-0019)

## Mục đích
Kế hoạch giáo dục soạn và duyệt trong CareNest: mục tiêu năm học → chủ đề nhánh → chủ đề tuần → kế hoạch ngày (EDU-01). Kế hoạch ngày là ngữ cảnh hoạt động cho đánh giá trẻ (OBS-07).

## Owns
Mục tiêu năm học (theo năm học, nhóm tuổi), chủ đề nhánh và kế hoạch theo chủ đề nhánh (theo lớp), chủ đề tuần, kế hoạch ngày (các mục, mục tiêu từng hoạt động, đồ dùng — EDU-07), lịch sử duyệt (người, thời điểm, lý do trả lại), phiên bản nhân bản (EDU-08), file Word đính kèm (qua `StorageService`).

## KHÔNG owns
Năm học, nhóm tuổi, lớp, phân công tổ trưởng (school-structure); tài khoản, mã quyền (identity-access); đánh giá trẻ (learning-observation — chỉ đọc mục tiêu hoạt động).

## Rules
EDU-01..10, OBS-07, PAR-01 (tên hoạt động — EDU-09), AI-07 (gợi ý nội dung hoạt động), AUTH-23. ADR-0019, ADR-0017. Flow: `docs/business/flows/education-plan.md`.

## Phụ thuộc
- Dùng: service `organization` (năm học, nhóm tuổi, lớp, phân công tổ trưởng), identity (mã quyền, scope), `StorageService`, `AiClient` (`integration/`).
- Được dùng bởi: learning-observation (mục tiêu hoạt động của kế hoạch ngày), reporting.
- Event: gửi duyệt, trả lại, duyệt/ban hành ⇒ notification (thiết kế khi code).

## API & bảng
Chưa có trên `dev`. Bảng `edu_*` và API `/education-plans/goals` của nhánh G94-226 chỉ để tham khảo (ADR-0019). Migration đánh số theo thứ tự merge vào `dev`.

## PENDING
P-29 (thời lượng chủ đề, cách chia).

## Đối chiếu rule nhánh G94-226
| G94-226 | Trên `dev` |
| --- | --- |
| EDU-01 (1 kế hoạch/năm học/nhóm tuổi), EDU-02 (người lập), EDU-04 (gửi PHT), EDU-05 (ban hành, trả lại có lý do) | EDU-02 |
| EDU-03 (khóa sửa khi đã ban hành) | EDU-08 (nhân bản để sửa) |
| EDU-08 (map tạm mã quyền theo role) | EDU-10 (phân quyền động, AUTH-23) |
| EDU-06 (ai xem bản nào), EDU-07 (ai xóa nháp), EDU-09 (đánh số MT, tích nội dung vào chủ đề) | Chưa đưa lên `dev` — xác nhận lại với người phụ trách giáo án khi làm code |

## Known pitfalls
- Không merge code G94-226 nguyên trạng; không dùng lại số ADR-0017 và migration V7–V10 của nhánh đó (ADR-0019).
- Tổ trưởng không phải role: quyền theo phân công tổ trưởng còn hiệu lực (AUTH-23), không map cứng.
- Chủ đề tuần và kế hoạch ngày không có bước duyệt (EDU-04); không thêm.
- Bản đã duyệt khóa sửa; sửa = nhân bản (EDU-08). Module khác tham chiếu theo id, không theo số thứ tự mục tiêu.
- Status do BE quyết định; không nhận status từ client.
- Phụ huynh chỉ thấy tên hoạt động, qua DTO phụ huynh riêng (EDU-09, ADR-0010).

## Related issues
Chưa có.

## Đọc thêm khi
Đổi quyền hoặc phân công tổ trưởng ⇒ `docs/business/USER_ROLES.md`, ADR-0017. Đụng AI gợi ý ⇒ card ai-assistance.
