# Data model — feature014

## Dữ liệu lưu trữ dùng lại

Không thêm/sửa bảng, migration, backfill hoặc payload.

| Concept | Field hiện có | Quan hệ / mục đích |
| --- | --- | --- |
| Source goal mục D | id, sourceId, domainId, subDomainId, expectedResultId, groupNo/groupText, goalText, contents[] | Theo nhóm tuổi request; contents thuộc goal; nhóm source lấy từ expectedResultId |
| Source content mục C | id, sourceId, domainId, subDomainId, category/label/text | Không có expectedResultId, không ép vào nhóm |
| Plan domain | id/name, rows[] | Cây bản soạn là hàng có thứ tự |
| Plan row | id, kind, text; source IDs theo kind | sub mở nhánh và reset group; group mở nhóm; goal dùng context đang hiệu lực |
| Plan content | id, text, themes[] | Giữ contents và ticks khi lọc; nội dung chọn mới không tự tick |
| Provenance | curriculumSubDomainId / curriculumExpectedResultId / curriculumGoalId và text metadata hiện có | Liên kết nguồn, không là local parent ID |

Source domain liên hệ domain kế hoạch theo mapping cấu hình hiện hành của EDU-28; không tạo domain hoặc suy diễn thêm alias. Nếu cấu hình/source không khớp thì báo không có ngữ cảnh hợp lệ, không fallback nhánh khác.

## Chuẩn hóa nguồn mock

Raw seed Web hiện không có source parent IDs như API. curriculumMockRepository enrich source-only ID ổn định, namespace mock, tuple age/source/domain/sub/group. ID dùng lại nhất quán giữa goals/contents cùng source sub, khác nhánh/tuổi không trùng, sống ổn định qua reload và roundtrip mock save. Chỉ source adapter đọc hierarchy seed để tạo identity; không tự ghép dòng kế hoạch nhập tay theo tên. API thật giữ numeric IDs và mapper cũ, không gửi mock ID sang server hoặc nới validation BE. Giữ numeric goal.id của raw seed; không mutate raw seed. Kiểm mock save/reload/clone bảo toàn generated provenance và content C dùng cùng sub key với goal D, không tạo group từ category. Mock source context/provenance chỉ tồn tại trong chế độ mock.

## Dữ liệu dẫn xuất trong bộ nhớ Web

- TargetDescriptor: goalRowId, domain local identity/name, subRowId hoặc null, groupRowId hoặc null, text; provenance của từng ancestor/goal nếu có.
- SourceScope: ageGroupCode, sourceId, domainId, sourceSubDomainId hoặc null, sourceExpectedResultId hoặc null. Source tuple phải thuộc catalog đang tải.
- TargetFilter: domain local key, sub local key, group local key; mỗi cấp phân biệt ALL, NONE (nhánh trực tiếp), VALUE(id). ALL là chưa lọc, NONE là không có cấp đó; không dùng chuỗi rỗng đại diện cả hai.
- Selection: targetRowId, selected suggestion keys, query và focused row. Modal mục C dùng content ID; mục D dùng sourceId/goalId/contentIndex làm key trong một snapshot, không chỉ dùng text.
- Inline source context cho nhánh nhập tay: state tạm theo row, scope nhóm tuổi và phiên form; chỉ đổi khi người dùng chọn nguồn rõ ràng. Không serialize state bộ lọc hoặc thêm field vào request.

## Thuật toán và validation

1. Quét từng domain.rows theo thứ tự: gặp sub reset group; gặp group cập nhật group hiện hành; goal nhận actual context. Nhánh đầu không tiêu đề giữ null.
2. Dựng index nguồn từ catalog theo source/domain/sub/group IDs, giữ thứ tự nguồn. Source result options phân biệt bằng source ID; mục D content pool là toàn bộ nội dung goal cùng source group.
3. Lọc target theo local tree; nguồn được lọc bằng source context tương ứng hoặc source context người dùng chọn rõ ràng. Không lấy source ID so bằng local ID, không nối sub/group theo tên.
4. Mỗi lần cha/form/age/scope/catalog đổi: tính eligible targets/suggestions; giữ giao selection với tập hợp lệ, không tự chọn target thay thế. Thao tác này không mutate form.domains/rows/contents/themes.
5. Confirm tính lại bằng snapshot mới nhất; target phải còn tồn tại/đúng filter và mọi nội dung đưa vào đều thuộc nguồn hợp lệ. Lựa chọn hết hiệu lực thì không gọi mutation callback; thông báo chọn lại.
6. Chọn gợi ý nhóm/goal chỉ điền ô đích và provenance đúng kind, giữ ID dòng. Chọn nội dung chỉ điền/thêm vị trí đích và theo bỏ trùng hiện có. Không tự lấy các con hoặc tích chủ đề.
7. Nếu edit nhóm/goal đã linked, giữ nhãn/provenance theo convention hiện tại; gợi ý dựa source context rõ ràng, actual target context vẫn theo cây. Không tự đổi nguồn theo câu gõ.
8. Không giữ suggestion từ scope cũ trong khi request scope mới chưa hợp lệ; retry/error không phục hồi stale selection.

## Lifecycle

UNSCOPED → SCOPED → SELECTED → APPLIED_LOCAL → SAVED theo lưu tay hiện có. Đổi cha có thể đưa SELECTED về SCOPED; APPLIED_LOCAL/SAVED không bị đảo bởi lọc. LOADING/EMPTY/ERROR là trạng thái gợi ý độc lập, không đổi DRAFT/REVIEW/APPROVED.

## Giới hạn

Không migration dữ liệu cũ; dòng không source vẫn dùng nhập tay, chọn nguồn tường minh để nhận gợi ý. Không tự thêm liên kết mục C→nhóm. Không lưu bộ lọc vào database/local draft mới chỉ để triển khai014.
