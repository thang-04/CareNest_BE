---
id: BUG-261102-curriculum-reimport-duplicate-headings
type: bug
modules: [education-plan]
rules: [EDU-24, EDU-28]
status: fixed
date: 2026-11-02
keywords: [curriculum, reimport, duplicate subdomain, lấy từ Bộ, xóa mục tiêu, lĩnh vực con]
similar_to: [CASE-261028-year-plan-import-first-sub-reparent]
---

# Nhập lại mục tiêu của Bộ tạo trùng lĩnh vực con và nhóm

## Symptom
User báo: xóa một mục tiêu lấy từ Bộ rồi thêm lại thì tự sinh thêm lĩnh vực con.

## Điều kiện tái hiện
Fixture giả gồm một mục tiêu có curriculumSubDomainId=10 và curriculumExpectedResultId=20. Gọi curriculumAsSourcePlan rồi mergeImportedGoals vào lĩnh vực rỗng; xóa riêng row goal như GoalTables.removeRow; nhập lại cùng mục tiêu.
Mong đợi theo báo lỗi: dùng lại cha còn tồn tại. Thực tế rows từ [sub, group] thành [sub, group, sub, group, goal].

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Tra ISSUE_INDEX và incident first-sub-reparent; đọc luồng GoalFormPage → curriculumAsSourcePlan → mergeImportedGoals và GoalTables.removeRow | Luồng nhập/xóa chạy ở Web; xóa goal giữ cha | Không sửa backend theo suy đoán từ ảnh |
| 2 | Node stdin, loader test hiện có của FE, fixture giả; không ghi file FE | subCount=2, groupCount=2, report.added=1, duplicates=0, unavailable=[] | Tái hiện trực tiếp helper, chưa kiểm UI/lưu-mở lại |
| 3 | User cho sửa FE; 4 regression trước sửa source | RED: curriculum 12 pass/4 fail, skip0 | Cha mới vẫn được tạo dù cha cũ còn |
| 4 | Tái sử dụng cha nguồn/chèn đúng đoạn, review ca chép nguồn hỗn hợp Bộ/tự gõ | Ca hỗn hợp RED added1 thay vì2; chuyển tìm vị trí chèn sau cả hai nhánh → GREEN39/39 targeted | Vị trí chèn phải giữ cho mục tiêu tự gõ đi sau trong cùng nhóm |
| 5 | FE full verify và review cuối trước commit | 375 tests, fail0/skip0, VERIFY PASS full; fix commit994002c đã push | Chưa thay kiểm persistence BE thật |

## Root cause
CareNest_FE/src/models/education-plan/importGoals.mjs: trạng thái mỗi lần gọi khởi tạo sub/group/subId/groupId=null. Khi có incoming.sub/group, helper addHeading rồi append cuối bảng; không tìm cha nguồn Bộ đã tồn tại ở đích. Liên kết curriculumSubDomainId/curriculumExpectedResultId được giữ nhưng chưa dùng để tái sử dụng cha.
Lỗi lộ khi nhập nhiều lượt hoặc xóa riêng goal rồi nhập lại; đường nhập đầu tiên chưa có cha để trùng.

## Fix
Đã sửa CareNest_FE/src/models/education-plan/importGoals.mjs, commit994002c (G94-178: Fix LVC). Dùng lại cha theo ID nguồn trong đúng lĩnh vực/sub; chỉ fallback so câu chuẩn hóa khoảng trắng khi cả hai cha thiếu ID nguồn (mock). Giữ tên đã sửa nếu cùng ID, không gộp hai ID khác nhau vì cùng tên. Chèn đúng đoạn cha và giữ guard normalize; mục tiêu tự gõ đi sau mục tiêu Bộ vẫn nằm trong nhóm đó. Không mutate dữ liệu cũ hoặc tự xóa các tiêu đề trùng đã tồn tại.
Chi tiết [incident FE](../../../../CareNest_FE/docs/knowledge/incidents/FE-BUG-261102-curriculum-reimport-headings.md). User cho đồng bộ/commit/push hồ sơ BE trong lượt triển khai UI02/11; không đổi source backend.

## Regression test
CareNest_FE/tests/education-plan/curriculum.test.mjs có6ca mới: xóa/nhập lại3chu kỳ sau normalize; nhóm mới/sub cũ; cha sửa tên/cùng tên khác ID; mock thiếu ID cha; nhóm không-sub; chép nguồn hỗn hợp. GREEN39/39 cùng import-goals/import-goal-file, full FE375/375; chưa xác nhận save/reopen BE thật.

## Ảnh hưởng
Web: helper dùng chung cho nhập mục tiêu và nhập file, các caller đã được kiểm hồi quy. Lỗi nằm ở ghép FE, không đổi BE/API/APP. Bản nháp có thể đã giữ tiêu đề trùng; chưa thay dữ liệu có sẵn. Giao diện chọn từ Bộ được chỉnh riêng sau commit fix, giữ tính năng/nguồn/quyền hiện có; xem VERIFICATION của FE.

## Lesson
Chống trùng mục tiêu bằng nội dung không ngăn tạo trùng các cấp cha qua nhiều lượt nhập. Cần đối chiếu định danh nguồn và vị trí trong cây.
