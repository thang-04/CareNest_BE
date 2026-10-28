# ADR-0006 — Nguồn ngữ cảnh hoạt động/học tập

- Status: **ACCEPTED** (chốt 2026-10-20)
- Date: 2026-10-03, cập nhật 2026-10-24
- Liên quan: OBS-07, EDU-16, EDU-19, ADR-0017, module learning-observation, education-plan

## Bối cảnh
Review 24/09: đánh giá trẻ và AI tóm tắt thiếu chiều sâu nếu không biết trẻ đã tham gia hoạt động gì, nhằm mục tiêu nào. Cần một nguồn ngữ cảnh hoạt động có cấu trúc cho đánh giá hằng ngày.

## Quyết định
Ngữ cảnh hoạt động lấy từ **giáo án tuần/ngày đã duyệt** do CareNest soạn (module education-plan, ADR-0017):
- mỗi ô (giờ, ngày) của kế hoạch tuần gắn hoạt động và **MT** của kế hoạch năm (EDU-16);
- đánh giá hằng ngày chấm theo MT của hoạt động trong giáo án đã duyệt; lớp chưa có giáo án duyệt thì khóa phần chấm theo MT (EDU-19);
- không import hay đồng bộ GoKids, không nhập metadata hoạt động riêng.

## Phương án đã cân nhắc
| Phương án | Kết luận |
| --- | --- |
| Lấy từ giáo án tuần/ngày soạn trong CareNest | **Chọn**: dữ liệu có cấu trúc, đã duyệt, nối MT xuyên suốt năm → chủ đề → tuần/ngày → đánh giá |
| Import/sync từ GoKids | Bỏ: trường soạn giáo án trong CareNest, không cần làm 2 nơi |
| GV/admin tự nhập metadata hoạt động | Bỏ: trùng với giáo án, dữ liệu thưa |

## Hệ quả
- learning-observation chỉ đọc giáo án đã duyệt qua service của education-plan, không sở hữu dữ liệu hoạt động.
- Giáo án đã duyệt không sửa được (EDU-18), nên ngữ cảnh của đánh giá đã ghi không bị đổi ngầm.
