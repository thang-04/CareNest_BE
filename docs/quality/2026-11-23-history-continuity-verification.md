# Lịch sử lớp và chuyển lớp — kiểm chứng 23/11/2026

## Thay đổi thuộc ba module của Cần

- Đọc ngày/khoảng của giáo viên hết phân công theo đúng đợt, cả ACTIVE/CLOSED; không kéo theo quyền ghi. Tổng quan GV thường giữ giới hạn vai trò hiện có.
- Nhãn tên trẻ phân biệt `(Đã chuyển lớp)` và `(Đã nghỉ học)` từ lịch sử enrollment. Áp dụng đánh giá ngày, chủ đề, học kỳ, cuối năm, hồ sơ phát triển, tuần, phiếu, tổng hợp điểm danh và sức khỏe ngày; bản in/Excel liên quan giữ nhãn.
- Quyết định cuối của user: cô lớp cũ vẫn sửa/chốt MT trẻ đã học tại lớp trước chuyển, nếu chưa xác nhận/còn hạn/có quyền. Cell.canEdit và API cùng kiểm mục tiêu có căn cứ trong thời gian ở lớp. Không chấm MT chỉ dạy sau chuyển. Trẻ nghỉ học giữ chỉ xem; phiếu đã xác nhận không đổi kết quả.
- Cô lớp mới đọc timeline của chính trẻ, kể cả khi lọc tháng trước chuyển. Cô lớp cũ chỉ nhận ngày/phiếu của thời gian ở lớp cũ; quyền hết phân công còn xét khoảng phân công.
- API GET /assessment/daily/classes xét ASSESSMENT_VIEW/UNLOCK độc lập với attendance. FE không cần quyền điểm danh để lấy lớp đánh giá. Không sửa role editor/personnel/auth chung.
- Hồ sơ dùng mốc năm/học kỳ thật, không tự lấy 31/12 khi thiếu cấu hình. Tuần/tháng cắt theo năm; bộ lọc vẫn dùng được khi kỳ bị từ chối quyền.

## Kiểm chứng native

- Maven 44 class/3 module + architecture/API: 362 ca, 360 đạt; 2 kỳ vọng cũ về lớp lịch sử không còn đúng. Đã sửa fixture phân công và assertion theo yêu cầu đọc lịch sử, không bỏ test.
- Sau sửa: AttendanceIntegrationTest + DailyAssessmentIntegrationTest + OpenApiSnapshotTest + ArchitectureRulesTest: **64/64 đạt, 0 skip** (`history-fixes-scope-health.log`). Gồm ca cô mới đọc quá khứ, cô cũ không đọc ngày mới, thu hồi VIEW, phân công cũ và ASSESSMENT không có ATTENDANCE permission.
- Contract bình thường (không update) + ca nhãn sức khỏe: **5/5 đạt, 0 skip** (`history-fixes-api-health-final.log`).
- ThemeEvaluationIntegrationTest: **21/21 đạt** trong lượt `history-fixes-latest-regression.log`; MT cũ của trẻ chuyển sửa được, MT chưa học không sửa được; snapshot giữ ô/tỷ lệ khi đổi nhãn.
- Node toàn FE sau đồng bộ route snapshot: **1608/1608 đạt, 0 skip** (`history-final-all-tests.log`). ESLint và Vite build đạt. Trước đồng bộ route có đúng một gate contract fail vì route mới chưa có trong snapshot; đã đồng bộ và chạy lại toàn bộ.
- Spotless native; không chạy verify/harness wrapper, không bỏ hook.

## UI thật trên DB bản sao

- GV lớp cũ: đánh giá ngày 19/11 vẫn có dữ liệu hai trẻ thử, tên kèm trạng thái riêng. Bảng học kỳ I chưa chốt không còn hai trẻ ở lớp cũ tại thời điểm tổng hợp.
- Cô lớp nhận (tài khoản thực local được phân công lớp nhận): mở hồ sơ trẻ, chọn Tháng10/2026 trong khi chuyển ngày20/11. Hiện21 ngày đánh giá,21 cờ,6 phiếu,1 đánh giá xác nhận; không lỗi quyền. Timeline giữ tên lớp ghi nhận cũ.
- Ảnh ngoài Git: FE logs/ui-proof/history-daily-exact-labels.jpg và receiving-teacher-october-history.jpg. Không commit dữ liệu thử/ảnh/log/launcher.
- Ca UI khóa toàn hàng chủ đề được kiểm trước khi user điều chỉnh; kết quả đó không dùng chứng minh quy tắc cuối. Quy tắc cuối đã có regression BE, cần thao tác UI tiếp sau cập nhật Git.

## Git và giới hạn

Đây là checkpoint chức năng theo yêu cầu user. Chưa kết luận PR merge-ready: dev mới trùng migration169/170 với hai migration nhánh này; FE có conflict ISSUE_INDEX. User đã duyệt đổi riêng hai số migration thành172/173, SQL giữ nguyên, migration dev giữ nguyên. Chưa thực hiện trong checkpoint này. Tiếp tục merge/test/CI sau khi push.

Không sửa .env, cấu hình DB riêng đang dirty của user, migration người khác, role editor, tài khoản, giáo án nghiệp vụ hoặc lớp ghép. Không tối ưu DB.
