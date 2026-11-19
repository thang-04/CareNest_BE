# Kiểm vị trí các mục thiếu trước khi ghi dữ liệu

User gọi implement sau khi kế hoạch bổ sung 73 cặp đã được trình. Checklist yêu cầu 16/16; tiếp tục theo kế hoạch đã trình, không chạy harness.

`prepare-addition-review.py` xác định 73 cặp: 66 cặp có một dòng nguồn cùng chữ ở tuổi khác; bảy cặp có không hoặc nhiều dòng nguồn. Sau khi đối chiếu trang gốc và nhóm trong seed:

- Năm mục tuổi 24–36 tháng không có dòng cùng chữ ở tuổi khác: hai câu thuộc mục C, I.b.3 (PDF16); hai câu thuộc III.1 và một câu thuộc III.2 (PDF20).
- “Kính yêu Bác Hồ.” có hai dòng cùng chữ: dùng ngữ cảnh mục C, IV.1, không chọn chỉ dựa vào chữ.
- Mục D V.3.4 có hai dòng cùng chữ khác số: dòng mục 3.4 ở tuổi 4–5 là nguồn quan hệ đúng; dòng 3.3 ở tuổi 3–4 là mục khác.
- Bốn mục tiêu 5–6 tuổi có nhóm cùng lĩnh vực/con và groupNo tương ứng trong seed tuổi đích. Cần kiểm lại bằng truy vấn DB để bảo đảm nhóm đích duy nhất trước INSERT.

`plan-addition-placement.py` dùng giao các câu chung trong cùng mục để tìm dòng kề; 59 mục chưa có điểm tựa theo cách này. `check-pdf-placement.py` mở rộng xem vị trí text trong ô tuổi trên cùng trang: 63/69 nội dung có ít nhất một dòng hiện hữu làm điểm tựa, không có xung đột thứ tự được phát hiện; **sáu mục còn lại không có điểm tựa trên cùng ô trang**. Kết quả chỉ là ứng viên theo text extraction, chưa phải 63 thứ tự được phê chuẩn hay bằng chứng toàn văn.

Sáu mục chưa có điểm tựa cùng trang gồm bốn mục tuổi 5–6 trên PDF46 về nước/không khí/đất và hai mục tuổi 5–6 trên PDF49 về nghề/di tích. Ảnh PDF45–46 và PDF48–49 cho thấy chúng nằm tương ứng sau phần hiện tượng ngày/đêm và sau các dòng về bản thân, trường lớp. Tuy nhiên cần xây và kiểm tra quy tắc chèn cho cả nhóm, kể cả các mục mới cùng khoảng, đối chiếu thứ tự thật với ảnh, bảo đảm unique `(source_id, age_group_id, sort_order)` khi cập nhật và không thay ID. Không được gán cuối catalog chỉ vì thiếu điểm tựa.

V95 và mock đã được tạo sau lượt chuẩn bị này; kiểm thử PostgreSQL/FE/E2E và giới hạn ghi trong `docs/quality/VERIFICATION.md`. T032/T035 vẫn mở do audit ngược chưa bao phủ toàn bộ văn bản.
