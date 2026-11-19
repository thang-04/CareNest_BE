---
id: BUG-261107-edu-calendar-convergence
type: bug
modules: [education-plan, learning-observation]
status: fixed
date: 2026-11-07
keywords: [lịch, chủ đề nhánh, kế hoạch giáo dục]
---

# BUG-261107-edu-calendar-convergence

Ngày 07/11/2026, feature012. Nguồn: review code theo spec/plan/contracts, không suy từ checkbox đã đánh.

- Preview calendar thiếu quyền soạn/version/state/snapshot. Bổ sung server guards; rejection không ghi DB.
- Save branch chụp lại policy trên mỗi lần ghi. Dùng snapshot service lúc tạo; hồ sơ cũ giữ LEGACY_UNBOUNDED/snapshot cũ; không tin snapshot client.
- Lịch submit/week/dossier/import vẫn dùng TEAM. Resolve đúng class + exact TEAM; chỉ dùng branch APPROVED cho theme/week; không đổi key/di chuyển tài liệu cũ.
- Đánh giá chưa đọc calendar query, V92 mới ghi origin. Nối projection lớp, capture full presentation khi confirm, frozen-first list/get/results; migration chưa commit backfill goals/children của baseline.
- TEAM guard mới khóa rows/content. Khóa thêm theme/break/template và domain; create có cây khác nguồn phải từ chối.
- Full native Maven 1148 tests phát hiện 1 fixture clone version thiếu legacy flag. Sửa fixture theo nguồn, không nới constraint.

Attempts: migration regression mới thiếu required child fields (code/gender/status/enrolled_on), đã bổ sung; calendar observation regression ban đầu thiếu cast jsonb, rồi trỏ branch của version cũ (9001 thay vì current TEAM9002), đã sửa fixture. Native targeted run3:35 tests,0 failure/error/skip. Broad run sau các sửa tiếp đang ghi target/feature012-final-native.log; kết quả cuối cập nhật ở VERIFICATION.md.

Không chạy harness; không dùng dữ liệu thật. E2E headless thử chạy nhưng thiếu E2E_DEV_PASSWORD:1 failed ở setup,2 did not run. Chưa chứng minh Q01–Q19.
