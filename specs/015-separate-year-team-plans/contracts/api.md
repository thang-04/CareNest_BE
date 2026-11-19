# API Contract — spec015 (thiết kế sửa, chưa triển khai)

Giữ prefix `/education-plans/goals`, ResponseJson/ApiCode và convention lỗi hiện có. Thay đổi dưới đây cần duyệt contract/impact theo T001/T047 trước code; lệnh fix chỉ sửa tài liệu.

## Surface và định danh

Giữ list GET (planLevel tương thích), GET/PUT/DELETE /{planId}, POST tạo, /{planId}/submit|issue|return|start-editing|finish-editing|editing-heartbeat|versions|changes, POST /file-preview và method hiện có tương ứng. Không hiểu “giữ surface” là giữ hành vi trái EDU hiện hành.

ID numeric header duy nhất xuyên hai bảng nhờ sequence chung trong data-model §1.3. Router tìm đúng bảng theo ID, không suy từ title và không thử ghi hai bảng. Inventory ID trùng phải fail trước cutover; request không truyền ID tự tạo. Kiểm quyền trên hồ sơ đã tìm trước trả payload. planType/planLevel client không được dùng bỏ qua server authorization. Không đổi semantics ID của nội dung/goal/theme là miền riêng.

## 1. List/detail và tương thích (FR-001/005/006)

- YearPlanSummaryResponse/YearPlanDetailResponse thêm planType: SCHOOL_YEAR|TEAM, planTypeLabel: “Kế hoạch năm học”|“Kế hoạch tổ”, lấy từ bảng vật lý.
- GET list nhận planType; giữ planLevel như alias transition, nếu truyền cả hai khác nhau trả lỗi validation theo convention hiện có. Cả hai vắng giữ lọc mặc định hiện hành; không mở rộng scope.
- title, ageGroup label, owner name/title, status/action theo quyền; sentAt nullable từ sent_at. Giữ sentBy.at cho client cũ, hai trường không lệch nhau; chưa gửi cả hai không có ngày, UI “—”. Không dùng createdAt.
- Chờ duyệt dùng cùng phân loại/title/link; không nhầm năm thành tổ.

## 2. Scope, issue/return và DELETE (FR-012/015/016/017)

- Server kiểm phân công hiệu lực trên list/detail/source/version/chờ duyệt và mọi action. TT/GV chỉ tuổi được giao; kiêm HT/PHT quản lý hiệu lực mới mọi tuổi, không mở trạng thái năm chưa ban hành chỉ nhờ scope tuổi.
- issue/return không từ chối TT chỉ vì role TEACHER: kiểm policy loại hồ sơ, tuổi, phân công, đúng lượt REVIEW; loại owner/coauthor. Return bắt buộc lý do. Mất phân công/sai lượt/ngoài tuổi/tự duyệt bị từ chối, không đổi dữ liệu. Chỉ bước cuối ban hành; REVIEW cấm sửa/lease.
- DELETE TEAM bị từ chối ở mọi trạng thái cho mọi người, dùng lỗi forbidden theo convention hiện có; không xóa children/events/lịch sử. DELETE SCHOOL_YEAR giữ EDU-07 và guard lock hiện có. UI không cung cấp thao tác xóa TEAM, server vẫn chặn gọi trực tiếp.

## 3. Create/update/import (FR-002/003/004)

- Người dùng bấm lập tổ vào form FE trực tiếp, POST chỉ chạy khi lưu/xác nhận tạo; không tạo trước row rỗng chỉ để mở form. Tên riêng + planType=TEAM + curriculumSourcePlanId, đúng một annual APPROVED hiện hành cùng năm/tuổi/trong quyền; server kiểm lại lúc lưu, lỗi giữ nội dung form.
- PUT tổ giữ cây MT/nội dung/nhãn/bố cục nguồn bất biến theo guard hiện có; chỉ cho tick/bỏ tick từng nội dung và trường hợp lệ của tổ. Chặn request giả đổi/add/remove nguồn, kể cả import. Không tự tick cả MT.
- POST /file-preview giữ quyền preview annual; đường apply/save phải chặn thay cây TEAM, không tin planType do client khai khác loại hồ sơ thật. Không thay parser hoặc chế độ import annual ngoài delta phân quyền tương thích.
- Giữ lease/revision/request chống lặp như contract lưu hiện có. Nguồn vừa bị thay thế trả lỗi theo convention, không âm thầm chọn nguồn khác hoặc làm mất phần nhập.

## 4. Review update (FR-008/009/013/014)

Detail tổ bổ sung:

- curriculumSourcePlanId: nguồn năm hiện tại; previousSourcePlanId: baseline lưu trước lần chuyển, nullable với tổ chưa chuyển/legacy.
- contentRevision và thông tin phiên sửa theo contract sẵn có.
- reviewItems[]: `{reviewItemId, itemKind, itemId, sourcePlanId, reason, resolved, resolvedBy?, resolvedAt?}`. reviewItemId là PK edu_team_review_items, itemId là đối tượng tổ, không dùng thay nhau.
- newItems[]: `{itemKind, itemId}` nhận diện dòng mới theo lineage so baseline, UI nhãn mới riêng; không tự tạo review unresolved chỉ vì dòng mới. Tính từ hai nguồn khi đọc, không thêm bảng dẫn xuất.

Endpoint `POST /{teamPlanId}/review/{reviewItemId}/resolve` nhận expectedSourcePlanId + contentRevision + trường lease/session/requestId theo format lưu hiện có. Không nhận resolvedBy/at hoặc resolved flag do client tự đặt. Server khóa item/tổ, kiểm quyền soạn DRAFT/lease/revision; item thuộc tổ và nguồn hiện tại, mapping/tham chiếu đã sửa hợp lệ rồi mới resolve. Item khác tổ trả lỗi not-found/forbidden theo convention không lộ dữ liệu; nguồn/revision cũ trả conflict. Gọi lặp cùng requestId không ghi hai sự kiện.

Submit tính lại tham chiếu và unresolved trên nguồn hiện tại, không cho flag resolve che lỗi; chủ đề end_date < ngày hiện tại Asia/Ho_Chi_Minh miễn đỏ, dùng rule ngoại lệ EDU-26/62. Lưu/mở lại giữ baseline/tiến độ; cập nhật nguồn invalidates lượt resolve cũ nếu đối tượng lại thay đổi.

Khi nguồn thực sự đổi, contentRevision tăng nguyên tử và lease/session cũ hết hiệu lực. Request PUT/submit/resolve/heartbeat/finish-editing từ form cũ trả conflict theo convention; không ghi, không gia hạn phiên, không dùng save-cache cũ vượt check revision. FE giữ input để đối chiếu, tải detail mới và lấy lease mới trước lưu/resolve; không tự gửi lại toàn payload cũ. Replay ban hành đã áp dụng nguồn không tăng revision hoặc vô hiệu phiên thêm lần nữa. Lỗi transaction khôi phục cả nguồn/revision/lease.

Năm ban hành chỉ chọn tổ liên quan bằng curriculumSourcePlanId thuộc chuỗi nguồn năm. Tổ legacy không nguồn giữ NULL/legacy và dữ liệu cũ, không gán nguồn theo tên/tuổi hoặc tạo baseline/review giả; xem FR-011/data-model §4. Không thêm endpoint liên kết nguồn legacy trong feature này.

Năm ban hành gọi cập nhật tổ đồng bộ trong cùng transaction (data-model §4). Nếu chưa có tổ thì không lập; nếu có bản mở thì reuse; chỉ có APPROVED mới sinh n+1. Lỗi generator rollback lần ban hành; request lặp không tạo thêm. Không API tạo phiên bản tổ thủ công.

## 5. Versions/changes (FR-007)

GET /{planId}/versions và /changes chỉ cho SCHOOL_YEAR trong scope; TEAM trả conflict theo convention hiện có, không “rỗng hoặc409” tùy cách cài đặt. UI TEAM ẩn bảng/nút/compare/create-version, giữ nguồn lớp/lịch sử nội bộ. Các đường tạo phiên bản hiện có phải chặn TEAM server-side.

## Impact consumer — cần triển khai và kiểm chứng

- CareNest_FE pages/education-plan: GoalListPage (5 cột, ngày, action), GoalFormPage (form trực tiếp/source readonly/tick/lưu lỗi), GoalDetailPage (ẩn versions, review/newItems), PlanReviewQueuePage (loại/tên/link/duyệt TT).
- Components GoalTables, YearPlanSourceCard, planUi, VersionWidgets/VersionHistory; services educationPlanApi/yearPlanMapper giữ tương thích sentBy.at/planLevel và thêm field mới. Không xây hệ component mới.
- T043–T046 là task FE có gate quyền repo riêng T047; chưa sửa FE trong lượt tài liệu. E2E kiểm UI thật, không dùng test service BE thay bằng chứng SC-002/004.
- APP: T034 kiểm CROSS_REPO_MAP/consumer; nếu có màn chịu tác động phải bổ sung task và quyền trước cutover, không mặc định bỏ qua APP.
- Khi implement cập nhật docs/api/openapi.yaml và chạy API snapshot tests hiện có (T037); chưa chỉnh snapshot thành contract đã triển khai trong lượt docs.

## Tương thích consumer hồ sơ lớp — T048

Các endpoint hồ sơ lớp/branch preview và nguồn giáo án đã duyệt giữ payload/docKey/quyền theo contract hiện có; field legacy yearPlanId trong API lớp vẫn mang ID bản TEAM nguồn, không đổi sang annual hoặc đổi key âm thầm. Server chuyển đường đọc/khóa/source-copy/superseded từ bảng header chung sang TEAM. Hồ sơ mới chặn annual hoặc tổ chưa duyệt/đã thay thế/sai lớp-tuổi-năm; hồ sơ cũ đọc/in từ TEAM lịch sử và chỉ chuyển trạng thái sử dụng khi TEAM cập nhật được duyệt theo EDU-26/62. Rà caller và regression nguồn đánh giá, cập nhật API snapshot chỉ nếu surface thực sự thay đổi được duyệt.
