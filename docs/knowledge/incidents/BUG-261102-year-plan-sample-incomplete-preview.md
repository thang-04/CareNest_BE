---
id: BUG-261102-year-plan-sample-incomplete-preview
type: bug
modules: [education-plan]
rules: [EDU-46]
status: fixed
date: 2026-11-02
keywords: [Word, doc, GoalFileTable, thiếu lĩnh vực, incomplete preview]
similar_to: []
---
# BUG-261102-year-plan-sample-incomplete-preview — bản xem trước thiếu dữ liệu trên file mẫu

## Symptom
Probe trên compiled classes hiện có với file .doc user cung cấp: 3 lĩnh vực, 57 mục tiêu, 171 nội dung, 10 cột chủ đề, 287 dấu tích và 39 cảnh báo. Lưới nguồn nhận diện 81 số STT khác nhau. Chưa xác định bộ số chuẩn cuối; không coi preview hiện tại là đủ dữ liệu. Không đưa bản gốc hoặc người ký vào repo/log/fixture.

## Điều kiện tái hiện
File .doc kế hoạch năm 2025–2026 khối 3–4 tuổi user gửi trong chat 02/11; probe gọi GoalFileGrid.read và GoalFileTable.interpret với danh sách lĩnh vực giống GoalFileImportTest. File giữ nguyên ở máy user. Cần rebuild trước regression chính thức.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | POI đọc cấu trúc file cục bộ | Có tiêu đề/nhóm tuổi ngoài bảng; ba bảng gồm header, mục tiêu, lịch 10 chủ đề | Decoder hiện chỉ trả bảng, metadata chưa được giữ |
| 2 | Gọi interpreter compiled classes và đếm độc lập STT trong grid | 57 goals/39 warnings so với 81 số STT nguồn | Không dùng parser output hoặc chỉ STT làm oracle |
| 3 | So cách viết domain IV/V, đổi tên cấu hình probe kĩ/mĩ | Đủ5lĩnh vực83MT,0warnings | Chuẩn hóa khóa so tên, không sửa text nguồn |
| 4 | RED/GREEN domain4ca và kiểm nguồn goal-number độc lập | 1..83, phân bố18/24/15/14/12 | STT52/53 trống nhưng câu có số; cảnh báo POI0cell không gây mất26MT |
| 5 | Review metadata warnings/ranges/boundary, synthetic tests/probe | Metadata warning được giữ; nhiều khoảng ngày cảnh báo; tuổi trong/sau bảng không thành metadata | Không nối toàn văn ngoài bảng thành phần nhận diện đầu tài liệu |

## Root cause
`GoalFileTable.domainOf` và `exactDomain` chỉ NFC/lowercase, chưa đồng nhất kĩ/kỹ và mĩ/mỹ. Hai lĩnh vực IV/V nguồn dùng cách viết kĩ/mĩ bị đặt domain rỗng; các dòng sau bị bỏ. Experiment chỉ đổi tên cấu hình trong probe đã phục hồi đủ5lĩnh vực/83MT, chứng minh nguyên nhân; không do warning POI0cell.

81 STT ban đầu không phải oracle: nguồn thiếu ô STT52/53 nhưng số đầu câu vẫn có; hợp nguồn độc lập là1..83, phân bố18/24/15/14/12.

## Fix
Chỉ chuẩn hóa khóa so tên lĩnh vực ở GoalFileTable, trả tên cấu hình và giữ nguyên câu nguồn. Feature009 thêm YearPlanFileTable/metadata/lịch; đây là mở rộng EDU-46 riêng, không dùng tính năng mới để che lỗi mất lĩnh vực.

## Regression test
`YearPlanDomainVariantTest`: RED4/4 trước fix, GREEN4/4 sau fix; cùng GoalFileImportTest10ca đạt14/14 (`target/feature009-domain-{red,green}.log`). Probe file gốc trên classes cuối assert5lĩnh vực83MT/203nội dung389ticks/10chủ đề đúng10cặp ngày và mã/2đợt nghỉ/metadata2025-2026/36-48tháng/0cảnh báo. Số mục tiêu và ngày/mã được đối chiếu độc lập; nội dung/ticks là counts reader, không gọi đó là oracle độc lập.

Review thêm metadata boundary: không lấy khoảng tuổi trong mục tiêu hoặc phần giải thích sau bảng. `FullYearPlanFileImportTest.ageInsideGoalIsNotMetadata`, `ageAfterGoalTableIsNotMetadata`; probe after-table RED AssertionError→GREEN trên classes cuối. Full verify cuối feature009 chạy riêng, kết quả/giới hạn tại quickstart.

## Ảnh hưởng
Preview hiện có có thể bỏ dòng và báo cảnh báo; user xác nhận preview vẫn có nguy cơ nhập thiếu. Chưa ghi DB hoặc sửa dữ liệu thật. Parser fix đã có regression; FE nhập đầy đủ còn phase Web, phải giữ nhập tay và hiển thị cảnh báo.

## Lesson
File được hỗ trợ về định dạng không đồng nghĩa mọi cấu trúc mục tiêu đã đọc đủ; cần đối chiếu nguồn độc lập.
