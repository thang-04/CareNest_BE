---
id: BUG-261108-duration-feedback-late
type: bug
modules: [education-plan]
rules: [EDU-56]
status: open
date: 2026-11-08
keywords: [thời lượng, chủ đề, nhánh, tuần học, chờ ban hành]
---

# Thời lượng chủ đề báo lỗi quá muộn

User yêu cầu báo ngay khi người soạn nhập lịch chủ đề hoặc nhánh sai thời lượng, thay vì đợi người duyệt ban hành. Mặc định chủ đề 2–4 tuần, nhánh 1–2 tuần; giới hạn thực tế theo snapshot hồ sơ, tuần nghỉ trọn không tính. Hồ sơ legacy không tự áp giới hạn mới.

Nguyên nhân: FE đã kiểm trùng và thứ trong tuần tại ô, nhưng chưa kiểm giới hạn thời lượng; server kiểm giới hạn ở bước ban hành.

Đã bổ sung tính tuần và cảnh báo gần ô ngày, kiểm lại trước gửi từ form/chi tiết và lịch lớp, giữ lưu nháp và giới hạn snapshot. Test 295/295 education, lint và build đạt; kiểm tra chủ đề, nhánh, snapshot legacy, cấu hình khác mặc định, tuần nghỉ trọn/lẻ và SSR. Lượt đầu fixture SSR dùng Sortable thật không có `document`; chuyển kiểm bảng năm sang fixture component hiện có và chạy lại. Chưa chạy E2E với BE thực.

## Tái mở — user báo gửi duyệt vẫn thành công, 09/11/2026

- DB local carenest_merge: kế hoạch năm 345 đang REVIEW; chủ đề 658 từ 2026-09-07 đến 2026-10-09 = 5 tuần học, không có đợt nghỉ của kế hoạch. edu_settings revision1 vẫn theme2–4/branch1–2.
- V97 loại duration_policy_snapshot khỏi edu_year_plans. EduPlan.durationPolicySnapshot là @Transient mặc định LEGACY_UNBOUNDED; EduTeamPlan có mapping riêng nhưng EduYearPlan không có. Snapshot BOUNDED lúc tạo chỉ tồn tại trong memory, mất sau load lại. FE form/chi tiết bỏ kiểm giới hạn khi không có BOUNDED, nên đây là lỗi persistence, không phải chính sách cho phép gửi chủ đề 5 tuần.
- requireReadyForReview chỉ kiểm trùng lịch/tuần/nhánh và nội dung; duration bound hiện ở YearPlanPublicationValidator khi ban hành. Vì vậy sửa persistence vẫn cần bổ sung kiểm server trước submit.
- Chưa sửa code/migration hay dữ liệu thực; delta sửa cần duyệt theo workflow làn L. Kế hoạch sửa bổ sung tại docs/plans/active/2026-11-07-school-year-team-plans.md, delta khôi phục snapshot thời lượng09/11.
- User làm rõ tiếp 09/11 theo EDU-71 PROPOSED: hồ sơ mất snapshot hoặc chưa phân biệt được legacy thật bị chặn gửi và báo kiểm tra/khôi phục; chỉ khôi phục theo bằng chứng, giữ trạng thái. Legacy thật được xác minh giữ ngoại lệ cũ. Chưa chứng minh fix đã triển khai.
