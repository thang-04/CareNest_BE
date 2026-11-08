---
id: BUG-261108-duration-feedback-late
type: bug
modules: [education-plan]
rules: [EDU-56]
status: fixed
date: 2026-11-08
keywords: [thời lượng, chủ đề, nhánh, tuần học, chờ ban hành]
---

# Thời lượng chủ đề báo lỗi quá muộn

User yêu cầu báo ngay khi người soạn nhập lịch chủ đề hoặc nhánh sai thời lượng, thay vì đợi người duyệt ban hành. Mặc định chủ đề 2–4 tuần, nhánh 1–2 tuần; giới hạn thực tế theo snapshot hồ sơ, tuần nghỉ trọn không tính. Hồ sơ legacy không tự áp giới hạn mới.

Nguyên nhân: FE đã kiểm trùng và thứ trong tuần tại ô, nhưng chưa kiểm giới hạn thời lượng; server kiểm giới hạn ở bước ban hành.

Đã bổ sung tính tuần và cảnh báo gần ô ngày, kiểm lại trước gửi từ form/chi tiết và lịch lớp, giữ lưu nháp và giới hạn snapshot. Test 295/295 education, lint và build đạt; kiểm tra chủ đề, nhánh, snapshot legacy, cấu hình khác mặc định, tuần nghỉ trọn/lẻ và SSR. Lượt đầu fixture SSR dùng Sortable thật không có `document`; chuyển kiểm bảng năm sang fixture component hiện có và chạy lại. Chưa chạy E2E với BE thực.
