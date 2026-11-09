# Xác nhận nghiệp vụ kế hoạch giáo dục — 09/11/2026

> **Quyết định mới user 10/11/2026 — EDU-72:** Bỏ thao tác lập kế hoạch tổ lần đầu. Khi kế hoạch năm được ban hành, hệ thống tự tạo một TEAM DRAFT cùng năm học/nhóm tuổi, gắn phiên bản năm đã ban hành mới nhất và giao tổ trưởng duy nhất đang phụ trách tuổi đó soạn. User làm rõ “chỉ có một tổ trưởng của một nhóm tuổi, gửi cho người đó soạn”. Quyết định này thay phần “không tự lập tổ/giữ nút lập tổ/chọn nguồn thủ công” ở EDU-62 và tài liệu09/11; các quy tắc giữ nội dung đang soạn, rà soát và giữ tổ ban hành cũ làm nguồn lớp vẫn áp dụng. Đây là chỉ đạo user, không suy là xác nhận mới từ giáo viên trường.


> **Quyết định user10/11/2026 — duyệt hồ sơ lớp:** Theo chuỗi HT cấu hình, không bắt buộc có bước TT; chỉ PHT duyệt cũng hợp lệ. Giữ cấp trên duyệt cấp dưới, chống tự duyệt, phạm vi phân công và REVIEW cấm sửa. Quyết định này thay yêu cầu TT bắt buộc trước PHT/HT trong tài liệu09/11; chưa phải bằng chứng triển khai/nghiệm thu.

## Nguồn và phạm vi

User trả lời bộ 12 câu hỏi trong chat ngày 09/11/2026 và xác nhận: **“đã được xác nhận bởi giáo viên bên trường”**. Mức nguồn: **CONFIRMED theo thông tin user cung cấp về xác nhận của giáo viên phía trường**. Chưa cung cấp tên giáo viên hoặc ngày giáo viên xác nhận; 09/11 là ngày ghi nhận trong dự án, không suy thành ngày khảo sát/xác nhận trực tiếp với trường.

Tài liệu mô tả **nghiệp vụ đích**. Chỗ code chưa đáp ứng là gap triển khai, không đổi nghiệp vụ theo code và không đánh dấu hoàn tất tính năng. Các quyết định này ưu tiên hơn mô tả cũ tại những điểm trùng phạm vi; không tự nâng mức xác nhận các yêu cầu khác ngoài bộ câu hỏi.

**Cập nhật sau bộ câu hỏi:** user trực tiếp làm rõ 09/11 theo EDU-65 PROPOSED: tổ trưởng được thêm/sửa nội dung riêng của tổ, không sửa nguồn năm; giáo viên phía dưới thấy nội dung tổ theo quyền/trạng thái hiện hành. Cập nhật này thay phần cấm thêm/sửa nội dung trong bảng và phần giữ quyết định dưới đây, không thay đổi lịch sử câu trả lời hoặc ngụ ý giáo viên trường đã xác nhận delta mới.

**Cập nhật tiếp theo:** user trực tiếp chọn 09/11 theo EDU-70 PROPOSED: áp dụng duyệt theo thứ bậc cố định và bỏ lease 90 giây/heartbeat 20 giây như flow. Phần chuỗi tùy cấu hình và lease trong các câu trả lời lịch sử dưới đây được thay trong phạm vi kế hoạch giáo dục; không suy delta mới đã được giáo viên trường xác nhận.

**Cập nhật tiếp theo:** user trực tiếp chọn 09/11 theo EDU-71 PROPOSED: hồ sơ mất giới hạn không đủ bằng chứng khôi phục bị chặn gửi và báo kiểm tra; trường hợp chưa phân biệt được legacy thật cũng cần xác minh trước khi gửi. Không suy delta này đã được giáo viên trường xác nhận; không tự đổi dữ liệu/trạng thái.

## Quyết định đã trả lời

| Câu | Trả lời | Quyết định | Rule |
|---|---|---|---|
| 1 | A | Context/business rules mô tả nghiệp vụ mong muốn; tình trạng source và việc còn thiếu ghi riêng | EDU-54/55/62 |
| 2 | Đúng | Kế hoạch năm học → Kế hoạch giáo dục tổ → Chủ đề nhánh của lớp → Kế hoạch chủ đề → Giáo án tuần/ngày → Đánh giá trẻ | EDU-12/13/15/17/19/55 |
| 3 | A | Tổ chuyên môn theo nhóm tuổi, dùng chung cả hai điểm trường | EDU-01/55 |
| 4 | A | Mỗi nhóm tuổi/năm học có một chuỗi kế hoạch tổ, giữ lịch sử điều chỉnh, tối đa một bản đang soạn/chờ; tên riêng không cho tạo thêm chuỗi độc lập | EDU-01/62 |
| 5 | Đúng | Mặc định năm: PHT soạn → HT duyệt; tổ: Tổ trưởng soạn → PHT duyệt; hồ sơ lớp: GV soạn → Tổ trưởng → PHT duyệt. HT cấu hình riêng theo loại, người soạn không tự duyệt | EDU-02..05/11/18/54/55 |
| 6 | A | Hỗ trợ Tổ trưởng duyệt kế hoạch năm/tổ nếu được HT cấu hình, đúng nhóm tuổi phân công và không duyệt hồ sơ đã soạn | EDU-54 |
| 7 | A | Năm mới ban hành: tự chuyển nguồn tổ đang nháp/chờ sang bản mới, giữ phần đã nhập và rà soát; tổ chờ về nháp. Tổ đã ban hành có bản cập nhật nháp, bản cũ tiếp tục làm nguồn lớp tới khi cập nhật được duyệt | EDU-62 |
| 8 | A | Đánh dấu cần rà soát nội dung thay đổi/bị xóa và liên kết chủ đề bị ảnh hưởng. Mục mới hiển thị riêng để phân bổ; không ép mọi mục mới thành ô đỏ bắt buộc xác nhận. Chủ đề đã kết thúc không đánh đỏ. So sánh với nguồn năm tổ dùng trước lần chuyển | EDU-62 |
| 9 | A | Mẫu nhánh tùy chọn lấy/không lấy; trước gửi, mọi tuần học của mỗi chủ đề phải thuộc một nhánh có tên. Nhánh có thể nhiều tuần; đủ giáo án mọi tuần khi gửi hồ sơ chủ đề | EDU-14/17/56 |
| 10 | A | Không ai được xóa kế hoạch tổ, kể cả nháp. Chỉ điều chỉnh và giữ lịch sử; thay yêu cầu cũ chỉ cấm PHT xóa tổ | EDU-07/57 |
| 11 | A | Nhập file kế hoạch năm giữ hai chế độ thay toàn bộ hoặc chỉ thay phần file có; xem trước → xác nhận áp dụng nháp → lưu tay. Tổ chỉ kế thừa cây năm và tick nội dung, không dùng nhập file để sửa cây | EDU-46/55 |
| 12 | B | Các quyết định của bộ câu hỏi đã được giáo viên bên trường xác nhận theo thông tin user cung cấp | Nguồn xác nhận |

“Không tự lập tổ” áp dụng lần tạo tổ đầu tiên: người dùng bấm lập, vào form và chọn một nguồn năm. Việc tự sinh **bản cập nhật** cho một tổ đã có sau khi năm mới ban hành là quyết định riêng ở câu 7.

## Giữ các quyết định ngoài delta

- Nguồn tạo tổ: đúng một bản năm APPROVED hiện hành cùng năm/nhóm tuổi và trong quyền; tổ không sửa cây mục tiêu/nội dung nguồn, phân bổ từng nội dung bằng tick.
- DRAFT → REVIEW → APPROVED; UI dùng Chờ duyệt, trả lại về DRAFT kèm lý do; REVIEW cấm soạn/sửa. Ban hành cuối chụp thông tin bản in. Lưu tay, lease/revision và lịch sử giữ theo rule hiện hành.
- Giới hạn thời lượng, nhãn mục tiêu, chi tiết catalog/gợi ý Bộ, scope xem nguồn theo EDU-56/58..61 giữ nguồn và mức xác nhận riêng; câu 12 không mặc nhiên xác nhận mọi chi tiết các rule này.
- Tách hai bảng vật lý năm/tổ giữ theo yêu cầu EDU-62/spec015 đã ghi trước bộ câu hỏi; bộ trả lời này không phải bằng chứng đã chuyển đổi DB hoặc phê duyệt chạy migration.

## Triển khai còn thiếu theo đối chiếu source

| Phần việc | Trạng thái ghi nhận |
|---|---|
| Hai cấp năm/tổ, cây tổ readonly/tick, ba trạng thái, policy, lease/revision, lịch lớp v2, nguồn giáo án duyệt cho đánh giá | Có trong source; không chứng nhận toàn bộ runtime |
| Tên loại/tên riêng tại chờ duyệt, ẩn phiên bản tổ | Còn gap FE theo đối chiếu 09/11 |
| Tự chuyển nguồn, tạo bản cập nhật tổ và kiểm rà soát/giữ bản ban hành cũ | Chưa có luồng đầy đủ theo spec015 |
| Tách bảng vật lý năm/tổ | Hiện entity vẫn dùng chung bảng; V89 local chuẩn bị, chưa đủ cutover |
| Tổ trưởng duyệt năm/tổ | Policy hỗ trợ nhưng controller issue/return còn chặn TEACHER; cần sửa và regression |
| Hồ sơ lớp chỉ nhận nguồn TEAM | Hàm requireIssuedPlan còn thiếu guard cấp; cần server regression |
| Preview nhập file annual | Còn kiểm workflow year (TEAM), cần đồng bộ quyền annual |
| Bất biến MT/nội dung và phân bổ hoạt động lớp | Cần bổ sung/kiểm chứng validation server, không chỉ dựa UI |

Nguồn kiểm chứng và chi tiết gap: báo cáo `huong_dan_nghiep_vu/doi_chieu_ke_hoach_giao_duc_2026-11-09.md` tại root workspace. Bộ 299 test FE đạt ở lượt đối chiếu trước, không phải test chạy lại cho lần cập nhật tài liệu này.
