# Kiểm chứng ba module — 22/11/2026

## Phạm vi

Cấu hình trường, điểm danh/suất ăn, đánh giá trẻ và điểm nối lịch sử lớp/năm học.
Không gồm lớp ghép, tối ưu DB, nghiệp vụ soạn kế hoạch giáo dục, tài khoản hoặc màn phân quyền.

## Thay đổi

- Thông tin lớp lấy cấu hình nhóm tuổi theo năm hoặc snapshot năm đã đóng.
- Báo cáo điểm danh và tổng bữa giữ các bữa có trong bản ghi, kể cả khi bữa không còn trong cấu hình.
- Sổ ăn giữ bữa đã lưu/tạm tính/thực ghi; khi tắt suất cuối cùng sau đổi cấu hình, dòng sổ cũ cập nhật về 0.
- Học kỳ giữ mục tiêu có kết quả ở lớp cũ cùng năm/nhóm tuổi, kể cả mục tiêu bị bỏ khỏi kế hoạch mới; không áp mục tiêu lịch sử của một trẻ cho trẻ khác.
- V169 chốt thông tin trình bày phiếu cuối năm; bản cũ không tự backfill.
- FE đếm trẻ chưa có lĩnh vực là thiếu dữ liệu, nhất quán với BE chặn xác nhận.

## Kiểm chứng tự động

- BE: 506 ca thuộc 64 lớp test, kết quả mới nhất 0 thất bại / 0 lỗi / 0 bỏ qua. Đợt rộng 459 ca phát hiện 4 lỗi; đã sửa và chạy lại nhóm bị ảnh hưởng cùng controller/validation (15 lớp test, lượt cuối đạt). Không cộng trùng các ca chạy lại.
- Bằng chứng BE: danh sách `target/continuity-final-selection.txt` và `target/continuity-rerun-selection.txt`; log `target/three-modules-final-test.log` (đợt phát hiện lỗi) và `target/three-modules-rerun-test.log` (sau sửa). Tổng hợp kết quả mới nhất `target/continuity-final-results.csv` đối chiếu XML Surefire. Spotless check và git diff --check đạt.
- FE sau lần sửa cuối: Node test 652 đạt / 0 lỗi / 0 bỏ qua; ESLint, Prettier file sửa và Vite build đạt. Log `CareNest_FE/logs/continuity-fe-*-final.log`.
- Review độc lập chỉ đọc: hai điểm được phát hiện và sửa (FE đếm thiếu với domains rỗng; metadata MT đã bỏ khỏi bản kế hoạch mới); chưa phát hiện lỗi chặn khác trong diff.
- Review bổ sung sổ ăn phát hiện dòng cũ không về 0 khi bỏ bữa cuối; đã sửa và regression MealCountIntegrationTest đạt 22/22, gồm kiểm tra số về 0. Review lại chưa thấy lỗi chặn khác.
- Chưa chạy lại CI toàn repo hoặc kiểm thử UI với đủ bốn vai trò qua trình duyệt. Không suy kết quả Node/API thành kết quả UI.

## Ma trận bao phủ bằng test hiện có

| Luồng | Bộ kiểm chứng |
| --- | --- |
| Năm cũ/hiện tại/sắp tới, thông tin theo năm, sao chép | YearConfigPerYearIntegrationTest, SchoolYearServiceIntegrationTest, YearCopyServiceImplTest |
| Nhóm tuổi/lớp, phòng và campus | ClassStructureIntegrationTest, CampusServiceIntegrationTest, FlowConsistencyIntegrationTest |
| Phân công, trùng phân công, đồng thời | TeacherAssignmentIntegrationTest, TeacherAssignmentConcurrencyIntegrationTest, StaffAssignmentServiceImplTest, VicePrincipalAssignmentIntegrationTest |
| Kích hoạt/xếp lớp năm mới, tốt nghiệp | YearTransitionIntegrationTest, ChildPlacementListIntegrationTest, PlacementRulesTest |
| Chuyển lớp/nghỉ hẳn và dữ liệu cũ | ChildRecordServiceImplTest, ChildLeftSchoolIntegrationTest, AssessmentContinuityIntegrationTest, AttendanceHistoryIntegrationTest |
| Có mặt/vắng, bật tắt ăn, lưu lại, sức khỏe | AttendanceIntegrationTest, PickupIntegrationTest, LeaveRequestIntegrationTest |
| Chốt/mở lại, nửa buổi, giờ VN | AttendanceCutoffIntegrationTest, AttendanceIntegrationTest, LeaveRequestIntegrationTest |
| Sổ ăn/xác nhận/sửa sau chốt/đồng thời | MealCountIntegrationTest, MealCountConcurrencyIntegrationTest, MealCountListenerIntegrationTest |
| Ngày/tuần/chủ đề/học kỳ/cuối năm | DailyAssessmentIntegrationTest, WeeklyCommentIntegrationTest, ThemeEvaluationIntegrationTest, SemesterDomainResultTest |
| Cờ, phiếu bé ngoan, khen thưởng | BehaviourTicketRulesTest, AssessmentContinuityIntegrationTest, RewardListIntegrationTest, RewardListDiligentTest |
| Khóa kỳ/mở lại, concurrent save và nhắc việc | EvaluationDeadlineIntegrationTest, AssessmentDayUnlockConcurrencyIntegrationTest, ConcurrentDailyAssessmentIntegrationTest, AttendanceReminderWindowTest |
| Quyền, phạm vi truy cập và API | ScopeQueryIntegrationTest, ClassroomAccessPolicyTest, PermissionGuardTest, EffectivePermissionResolverTest, OpenApiSnapshotTest |

## Các ca cần kiểm thử màn hình với dữ liệu local riêng

1. HT chọn năm đang học và năm sắp tới: cùng nhóm tuổi nhưng bữa khác nhau. Lưu năm tới, quay lại năm đang học, kiểm tra thông tin không đổi.
2. Ghi một ngày có ăn trưa; đổi cấu hình bữa; xem tổng hợp ngày/theo trẻ và Excel: bữa đã ghi vẫn hiện đúng. Đóng năm/kích hoạt năm mới rồi xem lại.
3. GV điểm danh Có mặt mặc định tham gia ăn; tắt ăn và lưu; tải lại. Nghỉ cả ngày không có bữa; nghỉ nửa buổi chỉ cho bữa của buổi còn lại.
4. Phụ huynh tạo/hủy đơn trước giờ chốt; kiểm tra khóa trạng thái và dữ liệu điểm danh cũ không mất. Sau chốt kiểm tra bị chặn theo cấu hình.
5. PHT từng campus xác nhận/mở lại đúng phạm vi; thử ID lớp campus khác qua API và UI. HT quyền toàn trường làm được theo quyền được cấp.
6. Chuyển trẻ trước khi có dữ liệu ngày đó: lớp cũ kết thúc hôm trước, lớp mới nhận từ ngày hiệu lực; xem lịch sử lớp cũ và nhãn rời lớp. Thử chuyển/nghỉ sau khi đã có dữ liệu: phải bị chặn và giữ dữ liệu.
7. Trẻ A chuyển lớp giữa chủ đề, mục tiêu lớp cũ 1/2/3, lớp mới 2/3/4. Kiểm tra kết quả chỉ lấy ngày thuộc lớp; ngày trước khi vào lớp không tự thành Chưa đạt; dữ liệu cũ có nguồn lớp/chủ đề.
8. Một MT có một hoạt động Đạt và nhiều hoạt động Chưa đạt: cuối chủ đề vẫn Đạt. Không có hoạt động Đạt thì Chưa đạt khi MT đã được dạy trong thời gian trẻ thuộc lớp; MT chưa được học để trống.
9. Học kỳ I/II theo ngày HT cấu hình: không lẫn kỳ. MT lớp cũ có kết quả vẫn có trong căn cứ; mục tiêu lịch sử của A không làm trẻ B bị thiếu. Bản kế hoạch mới bỏ MT đã chốt vẫn giữ kết quả lịch sử.
10. Cuối năm: 1/2 chủ đề đạt thì MT đạt; 1/3 thì chưa đạt; không lẫn năm khác. Lưu nhận xét lĩnh vực, tải lại, xác nhận rồi kiểm tra hai cô không sửa lại được.
11. Sau xác nhận phiếu mới, đổi tên giáo viên/HT/nhóm tuổi và nhập cân đo hồi tố: nội dung phiếu/bản in đã chốt giữ nguyên. Phiếu legacy chưa có snapshot cần kiểm tra riêng, không mặc định coi metadata là bản gốc.
12. Chuyển giữa tuần: lớp mới xem cờ lớp cũ, chỉ một phiếu hợp lệ cho trẻ/kỳ; lớp cũ giữ bằng chứng và không phát phiếu mới cho trẻ đã rời. Trẻ nghỉ hẳn vẫn xem được dữ liệu trước ngày nghỉ.
13. Với GV/tổ trưởng/PHT/HT: thu hồi quyền Ghi hoặc Xác nhận, đổi phạm vi campus, đổi năm phân công; đăng nhập lại và kiểm tra cả nút lẫn API.
14. Kiểm tra chuông thông báo chuyển/nghỉ, xác nhận, mở lại và người nhận thực tế; các mock notifier trong integration test không chứng minh việc giao thông báo đầu cuối.

## Giới hạn cần ghi nhận

- Chuyển/nghỉ hiện chỉ hiệu lực ngày thao tác, ngày học và chưa có dữ liệu vận hành. Chưa thêm lịch đặt trước.
- Phiếu cũ không có snapshot trình bày không thể tự khôi phục chính xác metadata tại thời điểm xác nhận.
- Các tỷ lệ đánh giá là nghiệp vụ dự án đã chốt, không gắn nhãn công thức bắt buộc của Bộ.
