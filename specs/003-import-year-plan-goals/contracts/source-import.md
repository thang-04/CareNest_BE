# Contracts — Đọc nguồn và lấy mục tiêu vào bản soạn

Không thêm/chỉnh endpoint, DTO, error code, envelope hoặc OpenAPI. Tài liệu này mô tả cách dùng contract hiện hành và giao diện nội bộ Web dự kiến; chỉ áp dụng sau khi plan được duyệt.

## External HTTP contract đang có

| Call | Dữ liệu | Cách dùng trong tính năng |
| --- | --- | --- |
| GET /education-plans/config | Danh mục năm/nhóm tuổi/lĩnh vực | Tái dùng refs hiện có; không đổi config hoặc thêm fetch nền riêng |
| GET /education-plans/goals | schoolYearId, ageGroupId, page>=0, size 1..200; PageResponse items/page/size/totalElements/totalPages | Lấy summary theo filter, đọc đủ trang; bỏ excludeId ở service/modal |
| GET /education-plans/goals/{planId} | Detail domains/rows/contents/status/revision/permissions | Đọc nguồn được chọn và đọc lại khi xác nhận; không dùng headers/lock ghi |
| POST /education-plans/goals hoặc PUT /education-plans/goals/{planId} | Full YearPlanRequest; edit session/revision/request ID hiện hành | Chỉ qua nút Lưu/Ctrl+S/gửi/ban hành của form như trước; không có HTTP import |

BE: controller/eduplan/EduYearPlanController.java:49/70/100; dto/common/PageResponse.java:8; dto/eduplan/YearPlanRequest.java. Prefix và axios envelope tiếp tục theo axiosClient hiện tại, không tạo HTTP client mới.

403/404 và lỗi mạng phải được giữ là lỗi, không biến thành nguồn rỗng. Backend là nơi quyết định scope xem thực tế. Filter nhóm cho Tổ trưởng trong import là UX; không hứa generic GET kiểm một permission import mới hoặc PUT kiểm provenance nguồn. Xem research §2.

## Giao diện facade nội bộ dự kiến

```text
listGoalSources({ schoolYear, ageGroupId, excludeId }, actor, options?)
  -> Promise<SourceSummary[]>
getGoalSource(sourceId, actor, options?)
  -> Promise<SourceSnapshot>
```

- API repository chuyển year/age UI sang ID qua refs, đọc pages với limit hợp lệ, dedupe summary theo id, không gọi getAll/provider và không fetch cây cho từng summary. Filter không map được phải lỗi rõ, không vô tình lấy toàn bộ trường.
- Detail map bằng toUiGoal thuần; không dùng remember/fetchGoal có side effect known của writer. options có thể chuyển signal tới axios hiện có nếu dùng hủy request; không đổi HTTP contract.
- Mock dùng cùng shape, seed giả hiện có, lọc year/age/exclude và role/scope theo EDU-06/24; detail bị giả ID ngoài nhóm của Tổ trưởng phải từ chối. Mock đọc nguồn không được sửa source/đổi status/giữ khóa.
- actor chỉ cho fake backend/UX; không gửi actor từ client làm bằng chứng quyền của API thật. Vai trò kép giữ quyền đọc teacher hiện hành nhưng import chỉ nhóm phụ trách/đích.

## Modal → form

```text
onImport({ source, selectedGoalIds }) -> boolean
```

Modal truyền fresh snapshot đã được đọc lại và đối chiếu, selectedGoalIds là ID goal còn tồn tại trong snapshot đã xem. Parent trả false nếu không còn quyền/leaseReady hoặc dữ liệu không hợp lệ; modal chỉ đóng sau khi ghép thành công. Empty selection không gọi callback.

Parent callback phải dùng form/quyền hiện tại khi lời gọi thực sự diễn ra (ref hoặc cập nhật chức năng đúng convention), không dùng form closure đã giữ trước await. Khi mất quyền, fieldset disabled vẫn chưa đủ vì modal portal có thể còn mở: parent kiểm lại trước ghép và BE vẫn kiểm ở Save.

## Selection và xác nhận

- Chọn một, theo lĩnh vực hoặc tất cả chỉ cập nhật selection; count đúng, allSelected của nguồn rỗng=false. Header năm học không đổi.
- Filter/source change clear selection và invalid confirm generation ngay; query/source key chặn dữ liệu phản hồi cũ hiển thị như nguồn mới. Tái dùng useAsync cho đọc theo deps, thêm generation cho thao tác confirm.
- Confirm disable double click; tái đọc detail. id/status/revision khác snapshot hoặc thiếu selected ID: không ghép, thông báo nguồn đã đổi và mời xem/chọn lại. Lỗi không đóng modal và không mất form.
- Source thay đổi sau confirm thành công không tự cập nhật bản chép ở đích; không có giao dịch nguyên tử qua hai document.

## Helper ghép dự kiến

```text
mergeImportedGoals(target, source, selectedGoalIds, makeId)
  -> { domains, report: { added, duplicates, unavailable } }
```

Theo [data model](../data-model.md) §5: không mutate inputs, clone context tổ tiên tối thiểu, IDs mới, bỏ ticks, không đổi themes/breaks, không lấy thừa mục tiêu. Candidate phải giữ context của row đích cũ và goal mới cả sau normalize/mở lại; nếu incoming sub đầu tiên làm đổi cha dữ liệu cũ thì báo unavailable và không áp candidate. Báo đầy đủ skip trùng/unavailable; chỉ ghép vào form, không gọi saveGoal. Bản mới tiếp tục serialize qua mapper/save queue hiện có, không sửa hook lưu.

## UI contract

Giữ bước 1–2–3, nút Lấy từ kế hoạch có sẵn và UI kit Modal/States/field/checkbox/alert sẵn có. Hiện tên/năm/nhóm tuổi/trạng thái nguồn, cha của goal, text và contents để người dùng phân biệt. Nội dung helper/report render dạng text React; không HTML thô. Không thêm toolbar/menu/preview redesign của task đã hoàn tác.
