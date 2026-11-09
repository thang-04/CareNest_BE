# T048 — consumer hồ sơ lớp

Đối chiếu EDU-12/19/26/54/62 và bộ xác nhận ghi nhận09/11/2026. Phạm vi: nguồn tổ APPROVED đúng lớp/năm/tuổi; năm mới không thay nguồn lớp trước khi tổ cập nhật ban hành; bảo toàn API `yearPlanId`/`docKey`, lịch sử và quyền duyệt hồ sơ cũ đang chờ.

## Thay đổi

- `EduClassDocServiceImpl`: dùng `EduPlan`/`EduPlanRepository` cho đọc, batch và khóa header; `requireIssuedPlan` và copy-source bắt buộc TEAM. Batch tính bản thay thế chỉ từ TEAM; lookup đơn dùng chuỗi TEAM rõ ràng và chỉ bản APPROVED lớn hơn mới thay nguồn.
- Giữ nguyên định dạng khóa tuần v1/v2, source-doc ID, lease/revision tài liệu, workflow/author/snapshot. So sánh bản chép vẫn gọi `yearPlanService.compareVersions` nội bộ; không mở API phiên bản TEAM.
- `EduLessonQueryServiceImpl` không phụ thuộc header hoặc bảng năm chung, nên giữ nguyên. Truy vấn MT bằng ID vẫn dùng repository đã chuyển owner ở tầng storage.
- `EduClassDocTeamSourceTest` (5 ca): từ chối annual qua preview, nguồn TEAM ban hành/nháp, năm/nháp tổ không supersede, tổ ban hành thay nguồn, copy từ annual bị từ chối, khóa tuần giữ định dạng.
- `EduLessonQueryServiceImplTest` (2 ca): giáo án cũ vẫn dùng khi bản mới REVIEW; bản mới APPROVED thay đúng ngày; lineage MT giữ nguyên.
- `EduClassDocServiceIntegrationTest`: SQL fixture chuyển owner TEAM, fixture lịch sử tổ dùng clone nội bộ thay API phiên bản công khai. Bổ sung guard annual, batch năm version cao không khóa tài liệu, người duyệt tiếp tục duyệt bản cũ đang chờ. Ca copy cũ/ô đỏ giữ lại.

## Lệnh và bằng chứng

- Trước sửa consumer source: `.\mvnw.cmd -Dtest=EduClassDocTeamSourceTest test` → exit1 ở `testCompile`, chưa chạy behavioral assertions. Shared tree đang chuyển đổi; lỗi type/repository ở `EduYearPlanEditTest`, `EduYearPlanSchemaIntegrationTest`, `EduConfigServiceIntegrationTest`, `EduPlanCalendarQueryServiceImplTest`, `EduWorkflowPublicationTest`, `EduWorkflowServiceIntegrationTest`; `EduPlanReviewDiffTest` chờ class implementation. Không coi đây là behavioral RED hoặc PASS.
- `git diff --check -- src/main/java/com/carenest/service/eduplan/impl/EduClassDocServiceImpl.java src/test/java/com/carenest/service/eduplan/impl/EduClassDocServiceIntegrationTest.java` → không có lỗi whitespace.
- Maven tiếp theo do root điều phối sau khi shared source/testCompile ổn định; cần chạy `-Dtest=EduClassDocTeamSourceTest,EduLessonQueryServiceImplTest,EduClassDocServiceIntegrationTest test` và format/check.

## Giới hạn

Chưa chạy lại Maven sau sửa trong nhánh công việc này; không chạy DB live, Docker/scratch E2E hoặc migration. T048 integration phụ thuộc schema cutover do storage/root hoàn thiện. Cần evidence thật của root trước đánh T048 hoàn tất; unit/mock không chứng minh bảo toàn DB/print runtime.
