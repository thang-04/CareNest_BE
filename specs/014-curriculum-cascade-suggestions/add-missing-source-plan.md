# Đề xuất bổ sung các mục nguồn Bộ bị thiếu — 08/11/2026

## Mục tiêu và bằng chứng

Sau V94, đối chiếu từ văn bản Bộ sang catalog phát hiện tối thiểu 69 cặp nội dung C–tuổi và 4 cặp mục tiêu D–tuổi bị thiếu do ô gộp. [Báo cáo, trang và câu gốc](audit/reverse-coverage-review.md); [73 cặp và các nguồn cha ứng viên](audit/addition-impact-review.json). Chưa khẳng định đã kiểm hết văn bản, do đó chỉ bổ sung những cặp đã được kiểm ảnh trang gốc.

## Phạm vi và tác động

1. Tạo migration Flyway **mới sau V94**, không sửa migration đã áp dụng. Thêm đúng 73 cặp, không cập nhật hoặc xóa text/ID catalog hiện có, không ghi đè kế hoạch, tick hay phiên bản đã chép. Ngôn ngữ từng mục giữ đúng câu nguồn, không diễn giải.
2. Kiểm cha lĩnh vực, lĩnh vực con, nhóm kết quả và mục C theo trang và quan hệ thật. Danh sách hiện có 66 trường hợp có một nguồn cùng câu ở tuổi khác; còn **7 trường hợp không có hoặc có nhiều nguồn tương ứng** cần đối chiếu trực tiếp trước khi xây SQL, không tự đoán. Mục D trỏ đúng nhóm nguồn cùng tuổi. Bảo toàn thứ tự văn bản, có preflight kiểm duy nhất nguồn/tuổi/cha và nội dung cũ; abort transaction nếu dữ liệu thực khác dự kiến.
3. Đồng bộ dữ liệu mock FE; mục mới có ID riêng, không thay ID cũ. Thay đổi dữ liệu response catalog cho Web, Mobile và client khác: mục được bổ sung sẽ xuất hiện trong gợi ý theo tuổi/nhóm; không đổi endpoint, DTO hay quyền. Mobile chưa được kiểm thực tế.
4. Có nguy cơ thứ tự `sort_order` bị ràng buộc unique theo nguồn/tuổi. Thiết kế renumber phải tránh va chạm tức thời và giữ thứ tự cũ tương đối, kiểm giới hạn kiểu số và không dùng thứ tự/ID làm liên kết. Kiểm truy vấn/consumer trước khi chốt thuật toán, so kết quả với trang gốc từng nhóm.
5. Test PostgreSQL trên DB cô lập: V94→mới, đủ 69+4 đúng câu/tuổi/cha/thứ tự, ID và dữ liệu kế hoạch cũ bất biến, thử điều kiện sai phải rollback; so từng mục giữa DB và mock. Chạy FE education suite, build, browser và E2E catalog/API cần thiết. Không chạy migration trên DB user.
6. Rollback vận hành qua backup/restore hoặc migration đảo được review theo ID nguồn và kiểm không xóa dữ liệu đã liên kết; không sửa migration đã chạy. Không tự xóa nội dung kế hoạch người dùng khi thu hồi dữ liệu catalog.

## Giới hạn

T032/T035 vẫn mở: cần đối chiếu hết các đoạn gãy trang, các ô gộp chưa được nhận diện, mục D và toàn bộ quan hệ cha. Không coi 73 là tổng đầy đủ của Bộ. Triển khai chỉ sau khi delta này được user duyệt theo quy trình làn L; không cần xin lại quyền sửa FE đã cấp.

## Thực hiện và kiểm chứng

Theo yêu cầu implement sau khi trình kế hoạch, V95 bổ sung 69 nội dung C và 4 mục tiêu D đã xác nhận, cộng 2 nội dung hô hấp được phát hiện khi rà tiếp. Giữ ID cũ và bản chép; SQL abort nếu baseline V94 không đúng. FE test 288/288; PostgreSQL migration/rollback và nguồn khác 6/6, hierarchy 5/5, service 7/7, controller 3/3; E2E 3/3. DB thử nghiệm và mock khớp toàn bộ 494 nội dung C và 331 mục tiêu D về câu, quan hệ, thứ tự, section và nội dung con. Không chạy V95 trên DB user. Nội dung và giới hạn kiểm từng lượt ghi tại `docs/quality/VERIFICATION.md`.
