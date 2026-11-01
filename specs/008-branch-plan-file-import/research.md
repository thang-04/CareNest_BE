# Research — Feature 008

## Quyết định

1. Dùng `poi-scratchpad:6.0.0` cho HWPF/OLE2; giữ GoalFileGrid.Table/Cell làm giao diện chung. User duyệt thư viện cho cả hai màn ngày 01/11.
   Nguồn xác minh 01/11/2026: [Apache downloads](https://poi.apache.org/download.html), [HWPF](https://poi.apache.org/components/document/), [Maven metadata](https://repo.maven.apache.org/maven2/org/apache/poi/poi-scratchpad/maven-metadata.xml): release 6.0.0 ngày 22/10/2026. Kiểm dependency tree với Spring BOM khi phase 1, không tự nâng lib khác.
2. GoalFileGrid.read:70 hiện từ chối OLE2 trước parser; thêm nhánh OLE2 trả cùng Table/Cell, bảo toàn continued của ô gộp. GoalFileImportTest đang có các định dạng cũ; cần hồi quy và .doc tổng hợp.
3. Thêm preview trong EduClassDocServiceImpl: dùng cùng scope/phân công/kế hoạch hiện hành và editable như saveClassDoc:243–289; backend phân tên vào teachingWeeks:622. Tránh endpoint chỉ kiểm role rồi tin classId từ client.
4. Parser BranchFileTable tách diễn giải bảng khỏi đọc định dạng. Chỉ đọc cột Chủ đề/Chủ đề nhánh, bỏ nhãn Nhánh n và dòng nghỉ; không dùng ngày/số tuần/ghi chú/người ký để sửa lịch.
5. Ghép chủ đề chuẩn Unicode, hoa/thường và khoảng trắng, giữ dấu; không fuzzy. Đích trùng tên chuẩn hóa thì cảnh báo, không đoán; tên thừa không tràn chủ đề, thiếu không xóa dữ liệu đích.
6. Response trả matches có weekStart do server tính, không FE tự tạo lịch business. Client áp dụng đúng matches sau xác nhận, giữ mọi key không được ghép, clear pending AI ở key được thay để payloadOf không khôi phục tên cũ.
7. BranchPlanPage:304 ẩn Lưu theo editable; ReviewActions:63 ẩn gửi theo can.send. Dùng tùy chọn mặc định false cho luôn hiện gửi chỉ ở branch, không đổi caller khác. Giữ docReview và chuỗi EDU-18.
8. Sửa lời GOAL_FILE_UNSUPPORTED 7033 đang yêu cầu chuyển .doc cũ; bổ sung lỗi nhập nhánh 71xx riêng, không dùng thông điệp Mục tiêu/Nội dung cho bảng nhánh. Số ID chọn slot trống sau kiểm ApiCode; semantic được chốt trong contract draft.

## Phương án đã cân nhắc

- Bắt user đổi .docx: ít code hơn nhưng không đạt user yêu cầu đọc .doc trực tiếp và mở rộng bước 2.
- Word COM/LibreOffice/service chuyển đổi ở server: thêm runtime/hạ tầng không cần thiết, không chọn.
- Tự viết OLE2/Word binary parser: nhiều code/rủi ro, không chọn.
- Tách service/policy mới: không cần khi service hiện tại đã sở hữu scope và lịch.

## Chưa kiểm chứng ở giai đoạn thiết kế

Chưa resolve Maven dependency tree hoặc chạy parser POI với fixture; đây là task phase 1, chưa khẳng định tương thích/đọc đúng.
Không copy file thật có tên người ký vào fixture/repo; tạo dữ liệu tổng hợp tương đương.

## Bổ sung thực nghiệm phase 1 — 01/11/2026

Đã resolve dependency tree, không đổi dependency khác: scratchpad/poi 6.0.0; Boot quản lý codec 1.21.0, log4j-api 2.25.5; io 2.22.0, math3 3.6.1. Parser và service liên quan: 13/13 đạt, không fail/error/skip. Fixture Word nhị phân gộp ngang/dọc có preview bằng .docx tương đương. Phần chưa kiểm chứng ở trên là trạng thái trước triển khai; full gate/browser được ghi riêng tại quickstart/plan.
