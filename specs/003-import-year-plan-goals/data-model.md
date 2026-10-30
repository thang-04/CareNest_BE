# Data model — Lấy mục tiêu kế hoạch năm

Thiết kế nội dung bản soạn và trạng thái UI; không tạo entity/table/column/migration. Cấu trúc bền vững tiếp tục là kế hoạch năm hiện hành (EDU-09/24).

## 1. SourceFilters

| Field | Kiểu logic | Ràng buộc |
| --- | --- | --- |
| schoolYear | Nhãn năm theo UI hiện có | Chuyển sang schoolYearId qua refs; không đổi header |
| ageGroupId | Mã UI hoặc rỗng (tất cả) | Phó HT có thể rỗng/khác nhóm; Tổ trưởng dùng nhóm đích mình phụ trách |
| excludeId | ID kế hoạch đích | Không xuất hiện trong nguồn |
| actor | Người hiện tại trong phiên | Mock cần role/nhóm; API thật dựa token/session, không tin actor gửi lên |

Dùng danh mục schoolYears hiện có của provider/config, không suy danh sách năm chỉ từ các kế hoạch đang tải. Không map được filter sang ID thì báo lỗi, không bỏ filter âm thầm.

## 2. SourceSummary và SourceSnapshot

Summary: id chuỗi, code, title, schoolYear nhãn UI, ageGroupId mã UI, status, revision, goalCount. DTO thật được map qua config refs hiện có. Không tải domain/content cho mọi summary.

Snapshot: SourceSummary + domains có id/name/rows theo đúng thứ tự và contents. Snapshot lưu sourceId/status/revision đã xem; selectedIDs chỉ có ý nghĩa với snapshot đó. Đọc nguồn không gọi start-editing và không sửa baseline known của dịch vụ lưu.

## 3. HierarchicalGoalView

Đọc rows tuần tự trong từng lĩnh vực: gặp sub thì đổi sub hiện tại và reset group; gặp group thì đổi group; gặp goal thì ghi row ID, text, source goalNo hiển thị, contents và đường dẫn context cha bằng row IDs. Cùng nhãn nhưng khác row ID/context vẫn là hai nhóm khác nhau.

Legacy items chuyển qua toRows(domain, uid) hiện có trước bước này. Selection giữ ID của bản đã xem; tái tải làm đổi ID tạm hoặc nội dung thì yêu cầu chọn lại, không suy theo số MT.

## 4. SelectionState

- sourceKey = sourceId + status + revision; selectedGoalIds là tập ID goal thuộc nguồn đó.
- selectedCount là số ID hiện có hợp lệ; allSelected chỉ true khi có goal và mọi goal đều chọn; chọn theo domain chỉ xét goal của domain đó.
- Đổi filter/nguồn, đổi revision hoặc mất nguồn thì clear selection; đóng/Hủy không đổi target.
- Không lưu selection vào database hoặc thêm bản giữ cục bộ riêng.

## 5. ImportResult và quy tắc ghép

Input: target form mới nhất, fresh source snapshot đã qua xác minh, selectedGoalIds, hàm sinh ID có prefix chữ.

Output: domains mới và report {added, duplicates, unavailable}; unavailable có nhãn mục tiêu/lĩnh vực và lý do hiển thị, không dùng log nội dung. Chỉ các domain thực sự đổi cần tạo bản mới; không mutate source hoặc target.

1. Duyệt selected goal theo thứ tự source domains/rows; group đích theo domain.name như convention hiện hành, đồng thời bảo đảm domain gửi được qua config. Domain thiếu/inactive không biểu diễn được thì unavailable.
2. Set khóa text.trim() trong từng domain bao gồm goal đích và goal vừa thêm. Không lowercase; trùng thì bỏ cả goal/contents, tăng duplicates.
3. Với goal được thêm, clone context cha tối thiểu và goal/contents bằng IDs mới; preserve text và thứ tự contents. Có thể lặp nhãn heading để giữ ngữ cảnh; không chép goal không chọn hoặc tạo heading không có goal.
4. Mỗi content mới có themes=[]; không giữ source themeIds, themeKeys hoặc id số. Không copy source goalNo/code vào identity đích.
5. Append đoạn context đúng vào cuối domain. Nếu không thể reset context của source thiếu cha trong flat rows hiện hành, goal đó unavailable; không tự bịa heading, gắn vào cha cuối hoặc sửa nestUnderFirstSub dùng chung.
6. Trước khi chấp nhận candidate, kiểm nó qua toRows/nestUnderFirstSub hiện hành như lúc mở lại. Đường dẫn cha theo row ID của mọi row đích cũ và goal nguồn mới phải đúng. Đặc biệt: target đã có rows không-sub + incoming sub đầu tiên có thể kéo sub lên đầu và gán lại cha target; khi không biểu diễn được mà vẫn giữ context thì các incoming goal liên quan là unavailable, không áp candidate. Thử round-trip này dù deep-compare input ngay sau merge không phát hiện mutation.
7. Không thay bất kỳ target themes/breaks/ticks/metadata/status/session/revision/history nào. ID cũ trong domain vẫn giữ; full-document save phải gửi cả nội dung cũ để tránh bị xóa do vắng mặt.
8. added + duplicates + unavailable.length bằng số goal hợp lệ đã chọn. Báo 0 thêm được khi tất cả bị bỏ qua, không báo thành công chung chung.

## 6. Trạng thái modal và ghi dữ liệu

loading-list → list-ready / list-error / list-empty → loading-detail → detail-ready / detail-error → confirming → applied (đóng) hoặc lỗi/nguồn-thay-đổi (giữ modal).

Đây là UI state, không phải status BE mới. During confirming: disable xác nhận lần hai; đổi filter/nguồn hoặc unmount làm invalid generation; late response không được gọi parent import. Khi nguồn đổi version, không tự chọn lại. Retry chủ động, không tự ghi dữ liệu.

Sau applied, form là unsaved theo hook lưu hiện có; Save/Ctrl+S mới POST/PUT với edit headers của đích. Save lỗi giữ form; gửi/ban hành kiểm đầy đủ như trước. Nguồn không có mutation hoặc phiên sửa.
