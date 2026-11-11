# Đề xuất điều chỉnh danh mục nguồn Bộ — 08/11/2026

**Triển khai đang kiểm chứng.** Sau khi delta và tác động được trình, user gọi implement để thực hiện các task còn lại ngày 08/11/2026. Đây là thay đổi bổ sung so với plan014 ban đầu: dùng migration mới, không đổi endpoint hay DTO.

## Bằng chứng

Đối chiếu bản [01/VBHN-BGDĐT chính thức trên Công báo Chính phủ](https://congbao.chinhphu.vn/van-ban/van-ban-hop-nhat-so-01-vbhn-bgddt-33656/35646.htm), PDF84 trang SHA-256 `146c1f23b3515d46f2e2e97afcfc0a1d871ef28b6700c7db4fbacfeb56e8572f`. [Bảng đối chiếu 33 dấu hai chấm](source-colon-audit.md) chỉ rõ cột tuổi, thứ tự nguồn và trang. Mục D nhóm tuổi24–36 tháng, 3.1 (PDF28/Công báo40): câu chứa “5 - 7 tiếng” bị tách thành goal dừng ở “5” và content bắt đầu “7 tiếng”; đây là một câu mục tiêu, không có gạch đầu dòng.

Audit 327 mục tiêu D, 101 nội dung D và 423 mục C: 842/851 chuỗi khớp tự động đúng cột tuổi, 9/851 được xác nhận bằng xem render, không có chuỗi nào chưa xác định cột. So khớp dùng NFC/khoảng trắng và không áp dụng biến đổi cho payload. [Báo cáo T032](audit/age-column-review.md), [chi tiết 851 dòng](audit/age-column-review-results.json). Kiểm theo chiều seed→PDF chưa chứng minh tất cả nội dung PDF đã được nhập hoặc cấu trúc mọi nhóm là đúng; câu 5 - 7 bị tách sai và 33 dấu hai chấm thiếu vẫn cần sửa.

## Phạm vi và phương án

1. Sau phê duyệt tạo migration Flyway mới, không sửa V34/V37: dùng source code + age-group code + sort_order, xác thực chính xác giá trị cũ trước khi cập nhật 33 dấu `:`; ghép lại đúng câu ag-2/27, loại một content catalog sinh sai; không thay ID goal hoặc sửa dữ liệu kế hoạch đã sao chép.
2. Đồng bộ mock FE từ cùng bảng đối chiếu để real/mock giống nhau, giữ ID mục tiêu hiện có và nguồn mock namespace. Không đổi endpoint, DTO hay dependency. Response của danh mục có 34 goal-text thay đổi và 1 content sai bị loại; đây là thay đổi dữ liệu quan sát được ở cả Web, Mobile và client API khác.
3. Không ghi đè nội dung người dùng đã chép vào kế hoạch, phiên bản cũ hoặc dấu tích. Goal đã liên kết giữ ID; nhãn so sánh nguồn có thể phản ánh khác biệt văn bản cũ, cần kiểm các consumer. Mobile chưa checkout: chưa xác minh UI/logic tiêu thụ, cần thông báo điều chỉnh dữ liệu response.
4. Test native PostgreSQL trên DB cô lập: preflight phải nhận đủ33+1 và dừng nếu khác, đúng nội dung/dấu câu/cột tuổi, 423 mục C/100 content D sau sửa, kế hoạch cũ bất biến; đối chiếu FE seed và API response bằng so sánh chuỗi đầy đủ. Không sửa test để che lỗi.
5. Rollback vận hành bằng backup/restore hay migration đảo có điều kiện đã review; không sửa migration đã áp dụng. Không tự thao tác DB user.

## Artifact để review

[SQL thử nghiệm chỉ đọc hiệu ứng sau rollback](contracts/ministry-source-correction-draft.sql) là bản đề xuất, không nằm trong Flyway. Đã chạy trên DB014 cô lập, kết quả `INSERT 33; DO; UPDATE 33; DELETE 1; UPDATE 1; ROLLBACK`, exit0. Đây là kiểm điều kiện/transaction, chưa phải triển khai. Kiểm đúng cột tuổi được ghi riêng trong báo cáo T032.

Đã tạo V94 và đồng bộ mock theo delta; test PostgreSQL và các kiểm chứng tiếp theo được ghi tại `docs/quality/VERIFICATION.md`. Việc đối chiếu chiều PDF→catalog và nghiệm thu đầy đủ vẫn theo T032/T035.
