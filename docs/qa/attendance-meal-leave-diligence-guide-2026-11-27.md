# Điểm danh, suất ăn, đơn nghỉ và Bé chăm: toàn bộ tình huống (27/11/2026)

Nguồn: đọc code backend trên máy, gồm các sửa ngày 27/11/2026 chưa commit. Test không cần container đã đạt (1440 test, 0 lỗi). **Integration test chưa chạy** vì Docker không phản hồi, nên chưa kịch bản nào được kiểm chứng trên database thật trong lượt này.

## 1. Khái niệm

| Khái niệm | Ý nghĩa |
|---|---|
| Giờ chốt | Một giờ chung toàn trường, Hiệu trưởng cấu hình. Từ giờ này ngày hôm đó bị khóa |
| Buổi sáng | Bữa Phụ sáng (nếu nhóm tuổi có) và bữa Trưa |
| Buổi chiều | Bữa Xế |
| Giờ bắt đầu buổi chiều | Mặc định 13:30 |
| Giờ đến muộn nhất buổi chiều | Mặc định 14:00 |
| Ngày học | Thứ Hai đến thứ Sáu, trừ ngày nghỉ Hiệu trưởng khai |

Trạng thái điểm danh: Có mặt, Vắng có phép (chỉ đơn nghỉ đặt), Vắng không phép.

## 2. Ai làm được gì

| Vai trò | Được | Không được |
|---|---|---|
| Phụ huynh | Gửi, hủy đơn nghỉ của con mình | Sửa điểm danh |
| Giáo viên lớp, người dạy thay | Điểm danh, bật tắt ăn, "Đã đến", trả trẻ | Tạo, hủy đơn; tự chọn Vắng có phép |
| Phó hiệu trưởng được phân quyền | Xác nhận suất, mở lại ngày | Sửa tay số suất |
| Hiệu trưởng | Cấu hình giờ, mở lại ngày, xác nhận thay | Tạo, hủy đơn thay phụ huynh |

## 3. Giáo viên điểm danh

### 3.1 Trước giờ chốt

| Đơn của trẻ | Giáo viên được | Giáo viên không được |
|---|---|---|
| Không có đơn | Có mặt hoặc Vắng không phép; bật tắt "Tham gia ăn" | Chọn Vắng có phép |
| Cả ngày | Ghi Có mặt nếu trẻ vẫn đến (báo phụ huynh); đổi lại Vắng có phép | Ghi Vắng không phép |
| Nghỉ sáng | Bật tắt ăn bữa Xế | Đổi trạng thái |
| Nghỉ chiều | Điểm danh buổi sáng | Bật bữa Xế |

### 3.2 Sau giờ chốt, chưa mở lại

| Việc | Điều kiện | Suất |
|---|---|---|
| Bấm "Đã đến" | Trẻ nghỉ sáng theo đơn hoặc vắng sáng không phép; trong khung 13:30 đến trước 14:00 | Giữ nguyên |
| Trả trẻ | Trẻ có mặt hôm nay, mỗi trẻ một lần; giờ trả là giờ bấm ghi nhận | Giữ nguyên |
| Sửa giờ trả | Trong ngày; chỉ giáo viên phụ trách lớp; bắt ghi lý do; tính lại chuyên cần | Giữ nguyên |
| Sửa điểm danh hoặc suất | Không được | |

Quá giờ đến muộn nhất mà trẻ nghỉ sáng chưa đến: hệ thống tự ghi Vắng cả ngày (sáng có phép, chiều không phép), báo giáo viên và phụ huynh. Suất Xế đã tính vẫn tính.

### 3.3 Khi ngày được mở lại

| Việc | Suất |
|---|---|
| Chọn Cả ngày, Chỉ sáng, Chỉ chiều cho trẻ Có mặt | Giữ nguyên |
| Đổi giữa Có mặt và Vắng | Đổi theo, chỉ khi suất chưa xác nhận và phiếu bếp chưa ký |
| Sửa nhầm nút ăn của hôm nay | Chỉ khi chưa xác nhận và chưa ký |
| "Sửa: có mặt cả ngày" khi ghi nhầm vắng sáng | Giữ nguyên |

## 4. Đơn xin nghỉ

### 4.1 Điều kiện chung

- Không cần duyệt. Chỉ phụ huynh đã liên kết với trẻ.
- Không gửi cho ngày đã qua.
- Nằm trong năm học, không quá ngày kết thúc học kỳ II.
- Tối đa 30 ngày lịch.
- Không trùng đơn đang còn hiệu lực.
- Không chạm ngày từ khi trẻ có lịch nghỉ học hẳn.
- Cuối tuần và ngày nghỉ trong khoảng được bỏ qua.
- Cả ngày, buổi sáng, buổi chiều đều chọn được nhiều ngày (chốt 27/11/2026).

### 4.2 Gửi trước giờ chốt

| Loại đơn | Điểm danh | Suất |
|---|---|---|
| Cả ngày | Vắng có phép | 0 bữa |
| Sáng, bé ăn | Nghỉ sáng có phép | Có Xế |
| Sáng, bé không ăn | Nghỉ sáng có phép | 0 bữa |
| Chiều, bé ăn | Giáo viên điểm danh buổi sáng | Bữa buổi sáng |
| Chiều, bé không ăn | Giáo viên điểm danh buổi sáng | 0 bữa |
| Trẻ không đăng ký ăn | Theo loại đơn | Luôn 0 |

### 4.3 Gửi sau giờ chốt

| Tình huống | Kết quả |
|---|---|
| Đơn nhiều ngày tính từ hôm nay | Hôm nay giữ nguyên, áp từ ngày học sau |
| Chỉ hôm nay, cả ngày hoặc sáng | Từ chối: "Đã qua giờ chốt, hôm nay chỉ báo nghỉ được buổi chiều" (4017) |
| Chỉ hôm nay, buổi chiều, trước 13:30 | Nhận dạng thông báo, không đổi suất |
| Như trên, trẻ đã nghỉ sáng theo đơn | Cả ngày tính vắng có phép, suất giữ nguyên |
| Từ 13:30 | Từ chối (4032) |
| Trẻ đã bị ghi vắng không phép, hoặc vắng cả ngày | Từ chối (4033) |

Hôm nay bị bỏ qua trong đơn nhiều ngày vẫn gửi được báo nghỉ chiều riêng.

## 5. Hủy đơn

Chỉ phụ huynh hủy được.

| Tình huống | Kết quả |
|---|---|
| Hủy trước giờ chốt | Hủy từ hôm nay |
| Hủy sau giờ chốt, còn ngày sau | Hủy từ ngày chưa khóa đầu tiên |
| Hủy sau giờ chốt, chỉ còn hôm nay | Không hủy được |
| Đơn đã qua hết | Không hủy được |
| Hủy một ngày giữa đơn dài | Không được; hủy phần còn lại rồi gửi đơn mới |
| Hủy báo nghỉ chiều, trước 13:30 | Được; trẻ nghỉ sáng trở về trạng thái nghỉ sáng |
| Suất của ngày liên quan đã xác nhận hoặc phiếu bếp đã ký | Bị chặn |

Dữ liệu sau khi hủy:

| Trước khi hủy | Sau khi hủy |
|---|---|
| Đơn tự tạo bản ghi, giáo viên chưa sửa | Bỏ bản ghi, điểm danh lại |
| Bản ghi còn Vắng có phép, giáo viên chỉ đổi bữa | Như chưa sửa: bỏ bản ghi hoặc trả về trước đơn |
| Giáo viên điểm danh trước, đơn gửi sau | Trả về trạng thái và bữa cũ |
| Giáo viên hoặc Ban giám hiệu đổi trạng thái sau đơn | Giữ dữ liệu của họ, bỏ dấu nửa buổi |
| Đơn nghỉ chiều gửi trước điểm danh, giáo viên để bé ăn | Trả lại đủ bữa |
| Như trên nhưng giáo viên đã tắt ăn | Giữ không ăn |

Giáo viên của từng ngày bị hủy (dạy chính và dạy thay) nhận thông báo.

Trẻ thôi học hoặc ra trường: đơn nghỉ từ ngày đó tự hủy, bản ghi vắng có phép của các ngày sau hôm nay được bỏ. Chuyển lớp: đơn đi theo trẻ. Việc tự đóng buổi sáng bù cả các ngày trước bị lỡ.

## 6. Suất ăn

- Số suất của một bữa = số trẻ có bản ghi điểm danh chứa bữa đó.
- Trẻ chưa điểm danh không tính suất.
- Trẻ không đăng ký ăn luôn không có suất.
- Không có suất thay thế; dị ứng chỉ cảnh báo.
- Phó hiệu trưởng chỉ xác nhận **sau giờ chốt** và khi mọi trẻ đang học đã được điểm danh.
- Xác nhận một lần cho cả ngày của điểm trường.
- Nếu số đã đổi so với số người xác nhận đang xem, hệ thống yêu cầu xem lại.

**Sau khi xác nhận, hoặc phiếu bếp M2/M3 đã ký, không thao tác nào đổi được suất:** điểm danh, đơn nghỉ, hủy đơn, mở lại ngày. API chỉnh tay luôn từ chối. Thiếu hoặc thừa suất thực tế báo qua điện thoại, ngoài hệ thống. Ngoại lệ lý thuyết: dữ liệu cũ còn dòng chờ xác nhận lại vẫn xác nhận lại được.

## 7. Chuyên cần và Bé chăm

### 7.1 Quy đổi ngày

| Bản ghi | Có mặt | Vắng có phép | Vắng không phép |
|---|---|---|---|
| Có mặt cả ngày | 1 | 0 | 0 |
| Có mặt, nghỉ một buổi theo đơn | 0,5 | 0,5 | 0 |
| Vắng sáng không phép, chiều có mặt | 0,5 | 0 | 0,5 |
| Trả trước mốc tính đủ ngày | 0,5 | 0,5 | 0 |
| Vắng có phép cả ngày | 0 | 1 | 0 |
| Vắng không phép | 0 | 0 | 1 |

Dòng "trả trước mốc" lưu buổi chiều là phần vắng và được tính vào phần có phép theo công thức hiện tại. Bảng tổng hợp của giáo viên và Ban giám hiệu đếm trạng thái theo ngày; cột Ngày đi học mới quy đổi 0,5.

### 7.2 Bé chăm

- Điều kiện: số ngày có mặt quy đổi đạt **từ 90%** số ngày phải đi.
- Ngày phải đi: ngày học của lớp trong thời gian trẻ thuộc lớp.
- Nghỉ có phép vẫn tính là nghỉ.
- Ngày đã qua mà lớp quên điểm danh vẫn tính vào ngày phải đi.
- So tỷ lệ chưa làm tròn: 89,5% chưa đạt.
- Báo cáo chất lượng tháng chỉ tính hôm nay khi lớp đã điểm danh. Trẻ chuyển lớp hoặc nghỉ hẳn trong tháng không xét Bé chăm ở lớp đó.
- Màn khen thưởng cuối năm cũng chỉ tính hôm nay khi lớp đã điểm danh.

### 7.3 Phiếu bé ngoan

| Phiếu | Điều kiện | Phát từ |
|---|---|---|
| Tuần | Từ 3 cờ trong tuần, mỗi ngày tối đa 1 cờ | Thứ Sáu hoặc ngày học cuối tuần |
| Tháng | Từ 3 phiếu tuần trong tháng | Thứ Sáu cuối tháng |

### 7.4 Gợi ý danh hiệu cuối năm

| Gợi ý | Điều kiện |
|---|---|
| Bé chăm ngoan xuất sắc | Bé chăm, và phiếu tháng đạt ít nhất nửa số tháng học |
| Bé chăm ngoan | Bé chăm, và phiếu tuần đạt ít nhất nửa số tuần học |
| Bé ngoan | Phiếu tuần đạt ít nhất nửa số tuần, chưa đạt Bé chăm |

Hệ thống chỉ gợi ý một danh hiệu cao nhất. Giáo viên quyết định.

## 8. Vấn đề còn mở

| Vấn đề | Tình trạng |
|---|---|
| Giờ đến muộn buổi sáng sau giờ chốt: không nhận | Giữ theo quy tắc đã chốt |
