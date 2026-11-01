# Research — Lấy mục tiêu từ kế hoạch năm có sẵn

Ngày: 28/10/2026. Chỉ đọc source hiện có, không nghiên cứu web hoặc chọn dependency mới. Hai nhánh nghiên cứu đọc độc lập BE và FE; kết luận dưới đây là cơ sở thiết kế, chưa là kết quả chạy tính năng.

## 1. Giữ contract BE, thêm đường đọc nguồn riêng trong facade Web

- **Decision**: Dùng GET /education-plans/goals với schoolYearId/ageGroupId/page/size; đọc đủ totalPages, loại đích theo ID. Dùng summary cho dropdown và GET /goals/{id} khi chọn/xác nhận. Không đổi getAll() đang phục vụ provider.
- **Evidence**: BE controller EduYearPlanController.java:49–65 nhận filter độc lập, size tối đa 200; dto/common/PageResponse.java:8 có totalPages; dto/eduplan/YearPlanSummaryResponse.java:7 có metadata/revision/count. FE api/educationPlanApi.js:59–64 hiện chỉ tải page0 size200 rồi mọi detail; facade educationPlanService.js:12 chưa có phương thức nguồn riêng.
- **Rationale**: Không bỏ nguồn ngoài trang đầu, không kéo toàn bộ cây của mọi nguồn, không mở rộng tác động sang màn khác.
- **Alternatives considered**: Tăng size >200 bị validation chặn; tải tất cả detail qua getAll mở rộng regression/N+1; thêm endpoint import không cần cho thao tác soạn bản nháp.

Đường dẫn BE bắt đầu từ src/main/java/com/carenest/; đường dẫn FE bắt đầu từ src/services/education-plan/ trừ khi ghi rõ.

## 2. Quyền nguồn và giới hạn server thực tế

- **Decision**: Giữ EDU-06. Server tiếp tục kiểm quyền list/detail. Import chỉ cho actor có quyền sửa đích; Phó HT chọn khác nhóm, Tổ trưởng giới hạn nhóm tuổi đích/nhóm mình phụ trách. Mock đóng vai BE giả, phải lọc list và từ chối detail giả ID ngoài scope theo EDU-24.
- **Evidence**: BE security/authorization/EduPlanAccessPolicy.java:62–76; service/eduplan/impl/EduYearPlanServiceImpl.java:520–526; FE mock/educationPlanMockRepository.js:113–118 hiện trả toàn bộ collection. Source quyền: docs/business/BUSINESS_RULES.md EDU-06/24.
- **Rationale**: Không cấp quyền đọc mới. Filter import chỉ là ràng buộc UX, không tuyên bố thay thế quyền server.
- **Alternatives considered**: Nới role trên BE trái A/A; chỉ lọc mock ở component không tạo fake backend đúng nghiệp vụ.
- **Limit**: Tổ trưởng đồng thời dạy nhóm khác có thể được generic GET cho xem bản đã ban hành của nhóm đó theo vai trò giáo viên (policy:74). Không thu hẹp quyền đọc của vai trò giáo viên; modal chỉ dùng nhóm dẫn dắt/đích. PUT không có provenance nên server không chứng minh được đoạn văn được nhập tay hay chép từ nguồn nào. Thiết kế không hứa một chính sách nguồn độc lập phía server hoặc giao dịch nguyên tử nguồn/đích.

## 3. Chọn bằng row ID, không bằng vị trí hoặc số MT

- **Decision**: Selection dùng sourceId + goal row.id, chỉ chọn kind=goal; đổi bộ lọc/nguồn xóa selection ngay, nguồn rỗng không được xem là đã chọn tất cả. Hiện ngữ cảnh cha và nội dung giáo dục để nhận diện mục.
- **Evidence**: FE components/education-plan/ImportGoalsModal.jsx:21–22 dùng chỉ số; :36–40 trả items bỏ rows; :145–149 chỉ hiện text mục tiêu. Mapper api/yearPlanMapper.js:62–79 giữ rows đầy đủ; số MT là nhãn.
- **Rationale**: Reorder hoặc tải nguồn mới không dẫn tới lấy sai mục tiêu; hierarchy không mất khi chọn tập con.
- **Alternatives considered**: Selection bằng MTn hoặc index không ổn định; chép cả domain để giữ cây lại lấy thừa mục không chọn.

## 4. Ghép cây phẳng, ID mới và không sao chép lịch/dấu tích

- **Decision**: Helper thuần nhận nguồn rows và selected IDs; giữ thứ tự và chỉ clone tổ tiên sub/group cần cho goal thực sự thêm. Đặt đoạn nguồn vào cuối lĩnh vực đích với các heading cần reset context; không gộp hai cha chỉ vì cùng nhãn. Sinh ID mới có prefix chữ cho mọi heading/goal/content mới, contents.themes=[]; không truyền source IDs sang dữ liệu mới. Giữ nguyên mọi themes/breaks/metadata/rows/ticks đích.
- **Evidence**: FE pages/education-plan/GoalFormPage.jsx:491–519 hiện copy themes/breaks, map ticks theo abbreviation và append goal trần. GoalTables.jsx:86–110 áp cha tuyến tính. models/education-plan/goalTable.js:17–28 normalize dòng trước sub đầu. Mapper api/yearPlanMapper.js:10 và :125–129 biến ID chữ thành null khi gửi. BE YearPlanRequest là full document; service:708–735 xử lý IDs/ticks trong bản đích.
- **Rationale**: A/A yêu cầu không lấy lịch; không dính nhóm cuối đích; không cập nhật nhầm ID nguồn. Helper chỉ biến đổi bản soạn, không quyết định status/quyền.
- **Alternatives considered**: Dùng items mất cha; reuse source ID có nguy cơ tham chiếu nhầm; chép chủ đề cũ hoặc nối ticks theo viết tắt trái EDU-24.
- **Legacy case**: Chuyển nguồn items cũ qua toRows hiện có. Nếu nguồn không có context cha nhưng append vào đích sẽ gán sai cha và cấu trúc hiện hành không có dòng reset để biểu diễn đúng, không tự tạo nhãn cha hoặc sửa normalizer chung: mục đó thuộc unavailable, báo lý do để người dùng nhập/đặt lại thủ công. Không silently drop. Nguồn có sub thì emit lại sub sẽ reset group; nguồn có group dưới sub phải emit cả sub/group.
- **Target legacy case (review đã tái hiện)**: Đích có goal không-sub, nguồn thêm sub đầu tiên: append [goal T, sub S, goal N] sẽ bị toRows/nestUnderFirstSub đổi thành [sub S, goal T, goal N] khi mở lại. Vì vậy candidate merge phải được kiểm qua normalize/reopen hiện hành và đối chiếu context của mọi row đích cũ lẫn goal nguồn mới. Nếu không giữ được context thì incoming goal gây thay đổi thuộc unavailable, không áp dụng candidate. Không sửa normalizer chung, không tạo sub giả cho đích. Bằng chứng tái hiện và nguồn: [CASE](../../docs/knowledge/incidents/CASE-261028-year-plan-import-first-sub-reparent.md).
- **Duplicates**: So text.trim() trong cùng domain, case-sensitive như handler hiện có (:505); thêm khóa vào set sau mỗi goal mới để loại trùng cả trong cùng batch. Goal trùng không thêm contents của nó. Loại heading rỗng nếu mọi goal dưới nó bị bỏ qua.

## 5. Tái đọc nguồn và ngăn phản hồi cũ

- **Decision**: Tái GET detail khi xác nhận, đối chiếu id/status/revision và toàn bộ selected IDs với snapshot đã xem. Nguồn đổi thì yêu cầu tải/xem/chọn lại, không tự lấy phiên bản mới thay người dùng. 403/404/lỗi mạng không ghép hoặc sửa đích. Không lock/heartbeat nguồn.
- **Evidence**: BE get detail controller:70; service:153–165 là đọc, :520–526 kiểm quyền tồn tại. FE useAsync.js:12–59 dùng latestRequest; api/educationPlanApi.js:23–29 fetchGoal hiện ghi cache known, nên phương thức nguồn phải dùng mapper thuần, không remember.
- **Rationale**: Phù hợp FR-013 và edge cases; giữ baseline writer. Không có giao dịch ràng buộc nguồn/đích: snapshot đã được xem và xác nhận là bản chép độc lập; nguồn có thể thay đổi sau đó.
- **Alternatives considered**: Tin mãi snapshot cache không phát hiện nguồn mất quyền; tự ghi vào đích sau mỗi click trái EDU-22; lấy khóa nguồn không cần và chặn người đang soạn.
- **Concurrency**: Hook dùng useAsync hiện có cho list/detail, kèm query/source key để không render data cũ khi đổi filter. Request confirm có generation token và snapshot key; unmount/đổi lọc hủy hiệu lực callback, khóa double-submit. Callback parent đọc canEdit/leaseReady/form mới nhất trước ghép, không dùng closure form cũ sau await.

## 6. Kiểm thử và phạm vi triển khai

- **Decision**: 3 phase: đọc nguồn (5 file), helper/test (2), UI + hook + E2E (4); cập nhật tài liệu Web theo EDU-24. Không sửa test/backend khi BE không đổi.
- **Evidence**: FE tests/education-plan/year-plan-api.test.mjs:1–90 có axios adapter/mapper fixture; node:test script test:edu đã nạp mọi *.test.mjs. tests/e2e/README.md:3–29 yêu cầu BE thật và E2E_DEV_PASSWORD, có cleanup riêng.
- **Rationale**: Không cần framework test hoặc cache mới; kiểm logic bằng dữ liệu giả và UI/API thật qua hạ tầng sẵn.
- **Alternatives considered**: Chỉ mock UI không chứng minh save/reload/permission thật; chỉ source snapshot test không chứng minh hành vi lựa chọn.

## Kết quả cổng research

Không còn câu hỏi kỹ thuật chặn thiết kế. Không thêm quyết định nghiệp vụ thay A/A. Chưa sửa source hoặc chạy E2E tính năng; tiếp theo tạo tasks sau khi plan được duyệt.
