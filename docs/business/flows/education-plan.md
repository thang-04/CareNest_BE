# Flow — Kế hoạch giáo dục và giáo án

Nghiệp vụ đích theo [bộ 12 câu trả lời ghi nhận09/11/2026](../education-plan-confirmation-2026-11-09.md), user xác nhận đã được giáo viên phía trường xác nhận. Rule: EDU trong [BUSINESS_RULES](../BUSINESS_RULES.md), OBS-07. Trạng thái source/gap xem [module card](../../modules/education-plan.md); flow này không chứng nhận code đã đáp ứng toàn bộ.

## 1. Phạm vi và chuỗi nguồn

```text
Kế hoạch năm học (nhóm tuổi, toàn trường)
  → Kế hoạch giáo dục tổ (nhóm tuổi, dùng chung hai điểm trường)
    → Chủ đề nhánh của từng lớp
      → Kế hoạch chủ đề
        → Giáo án từng tuần học + 5 kế hoạch ngày
          → Đánh giá trẻ theo MT từ giáo án đã duyệt
```

Mỗi loại năm/tổ có một chuỗi cho một năm học/nhóm tuổi, tối đa một bản DRAFT/REVIEW trong chuỗi. Tên riêng không tạo thêm chuỗi. MT tham chiếu bằng ID/lineage; số MT và nhãn hiển thị không là khóa liên kết.

## 2. Cấu hình và duyệt — EDU-54/55

Chỉ HT có phân công hiệu lực cấu hình người soạn/duyệt riêng theo loại. Mặc định:

| Loại | Soạn | Chuỗi duyệt |
|---|---|---|
| Kế hoạch năm học | PHT | HT |
| Kế hoạch tổ | Tổ trưởng đúng nhóm tuổi | PHT |
| Nhánh lớp / hồ sơ chủ đề | GV lớp | Tổ trưởng → PHT |

HT được cấu hình Tổ trưởng duyệt năm/tổ: đúng nhóm tuổi được phân công, không duyệt hồ sơ đã soạn. Người có vai trò duyệt hiệu lực không soạn cùng loại; người tham gia soạn bị loại khỏi ứng viên duyệt. Server kiểm policy, phạm vi, phân công và lượt duyệt; ẩn nút không thay phân quyền.

```text
DRAFT (Nháp) → gửi → REVIEW (Chờ duyệt) → duyệt bước cuối → APPROVED (Ban hành)
                         └─ trả lại + lý do → DRAFT (UI: Trả lại) → sửa/gửi vòng mới
```

REVIEW cấm sửa/cấm author lease, kể cả PHT. Không ban hành thẳng từ nháp. Thiếu ứng viên bước giữa thì bỏ nếu còn bước sau; thiếu bước cuối giữ chờ. Policy thay đổi reconcile hồ sơ đang chờ, giữ duyệt hợp lệ; bản hoàn tất không mở lại. Bản in chụp ô ký/vai trò/tên lúc ban hành, khác với policy đang dùng để xử lý hồ sơ chờ.

Cấu hình lĩnh vực/dòng tuần giữ quyền riêng EDU-11; không đồng nhất mọi cấu hình với HT-only workflow/thời lượng. Giới hạn thời lượng theo EDU-56, snapshot cho hồ sơ tạo mới; thông số chi tiết giữ mức nguồn riêng của rule đó.

## 3. Soạn kế hoạch năm học — EDU-01..10/22..25/28/46

Người có quyền soạn annual chọn năm/tuổi, nhập tên riêng, cây lĩnh vực → lĩnh vực con → nhóm kết quả mong đợi → MT → nội dung; dự kiến chủ đề/sự kiện, đợt nghỉ, mẫu nhánh. Năm không tick phân bổ nội dung vào chủ đề. Nhãn hiển thị MT theo EDU-58, không đổi ID/số thứ tự MT.

Có thể lấy mục tiêu từ kế hoạch được xem hoặc nguồn Bộ theo quyền; lấy chủ đề/đợt nghỉ theo EDU-43. Nhập file năm có hai chế độ: thay toàn bộ hoặc chỉ thay phần file có. Xem trước → xác nhận áp dụng vào nháp → bấm Lưu; giữ năm/khoảng ngày hệ thống và trường cố định, remap liên kết theo phần thay. Quyền preview phải hỗ trợ người đủ quyền soạn annual; preview không tự lưu/gửi.

Nháp cho lưu dở theo EDU-23 nhưng vẫn kiểm quyền/ID và các ràng buộc luôn áp dụng. Trước gửi/ban hành kiểm đủ nội dung tương ứng, tên trùng/cấu trúc, lịch và ngày đầy đủ khi ban hành theo EDU-50..53/59. Bản đã ban hành không sửa trực tiếp; tạo phiên bản điều chỉnh, duyệt lại.

## 4. Lập và phân bổ kế hoạch tổ — EDU-55/57/60/62

Người dùng bấm Lập kế hoạch tổ → mở trang soạn trực tiếp, không modal/không tự tạo tổ lần đầu. Nhập tên riêng, chọn đúng một annual APPROVED hiện hành cùng năm/tuổi và trong quyền. Tên năm học, tên kế hoạch năm, tên tổ độc lập.

Tổ kế thừa cây MT/nội dung, chủ đề/đợt nghỉ/mẫu nhánh; giữ nguồn bất biến. Tổ trưởng chỉ tick từng nội dung vào từng chủ đề, MT tương ứng đi theo; không tự tick cả MT, không sửa/thêm/xóa cây hoặc nhãn nguồn, kể cả nhập file. Hiển thị đủ cây kể cả hàng chưa tick. Phần xem theo chủ đề chỉ đọc kết quả phân bổ.

Gửi/duyệt theo policy tổ. **Không ai được xóa tổ, kể cả nháp**. Chỉ điều chỉnh và giữ lịch sử. Chỉ năm hiện bảng/thao tác phiên bản; tổ giữ lịch sử nội bộ và liên kết lớp.

## 5. Năm mới ban hành → cập nhật tổ — EDU-62

Chỉ kích hoạt khi annual phiên bản mới đã ban hành, không khi mới tạo nháp:

- Tổ đang nháp/chờ: tự chuyển sang nguồn mới, giữ phần đã nhập để đối chiếu/rà soát; bản chờ về nháp và phải gửi lại.
- Tổ đã ban hành: tạo bản cập nhật nháp; bản tổ ban hành cũ tiếp tục làm nguồn cho lớp đến khi cập nhật được duyệt.
- So sánh với nguồn năm tổ đang dùng trước lần chuyển. Đánh dấu nội dung đổi/xóa và liên kết chủ đề bị ảnh hưởng; mục mới hiển thị riêng để phân bổ, không tự bắt xác nhận đỏ mọi mục mới. Chủ đề đã kết thúc không đỏ.
- Tổ rà soát phần ảnh hưởng, tick lại và gửi/duyệt theo policy; bản cũ/hồ sơ lớp/lịch sử không bị ghi đè.

Đây là nghiệp vụ đích đã chốt, chưa có luồng đầy đủ trong source spec015. Tự tạo bản cập nhật cho tổ đã có khác với tự lập tổ lần đầu.

## 6. Nhánh và lịch lớp — EDU-12..14/39..41/55..56

GV dùng tổ đã ban hành hiện hành làm nguồn. Mẫu nhánh tùy chọn; được lấy/sửa tên bản lớp, thêm nhánh, không sửa mẫu nguồn. Chủ đề lớn giữ tên/nội dung, được đổi thứ tự dạy tại lớp; chuyển cả khối kèm nhánh, giữ số tuần học và dời ngày.

Một nhánh có thể nhiều tuần. Trước gửi nhánh, **mọi tuần học của mỗi chủ đề phải thuộc một nhánh có tên**, hết tên AI chưa giữ/bỏ. Không cần lấy đủ mẫu nhưng phải phủ đủ lịch. Giữ Thứ Hai–Thứ Sáu, nhánh trong cha, không overlap; nghỉ phủ trọn tuần mới loại tuần học, nghỉ lẻ vẫn tính tuần. Lịch cũ được đọc tương thích, không dùng giả định một tuần/một nhánh cho lịch mới.

Nhập file nhánh EDU-47: preview theo lớp/tổ hiện hành → cảnh báo và xác nhận thay tên → áp nháp → lưu tay. Không nhận lịch/người ký từ file, không tự gửi. Hủy không đổi dữ liệu, tuần không ghép giữ nguyên.

## 7. Kế hoạch chủ đề, tuần/ngày và hồ sơ duyệt — EDU-13/15..18

Nhánh cả năm đã duyệt → lưu kế hoạch chủ đề → soạn giáo án từng tuần. MT/nội dung lấy từ nội dung tổ đã tick, không cho GV sửa phần nguồn. GV viết hoạt động theo loại giờ và môi trường trong/ngoài lớp.

Tuần: mục đích, chuẩn bị, dòng hoạt động × Thứ Hai–Thứ Sáu. Chọn hoạt động từ kế hoạch chủ đề gắn MT; mỗi hoạt động chỉ vào một ô ngày trong cả chủ đề, ô gộp cả tuần có ngoại lệ. Kế hoạch ngày gồm MT theo hoạt động đã xếp, tiến trình cô/trẻ/ghi chú.

Hồ sơ chủ đề = kế hoạch chủ đề + đủ giáo án **mọi tuần học**, kể cả các tuần của một nhánh nhiều tuần. Gửi/duyệt cả bộ trong cùng luồng; tuần không gửi/duyệt riêng. Hạn gửi chỉ nhắc; hoạt động chưa xếp tuần cảnh báo theo rule. Trả lại cả hồ sơ về nháp có lý do; duyệt theo cấu hình và chống tự duyệt.

## 8. Đánh giá, học liệu, lưu và lịch sử

Giáo án WEEK APPROVED cung cấp MT/hoạt động cho đánh giá trẻ theo ngày; không có giáo án duyệt thì khóa phần chấm theo MT. Kết quả đánh giá thuộc learning-observation, không ghi ngược vào giáo án. Backend đã có consumer nguồn giáo án/lineage, không còn chỉ mock FE; chưa chứng nhận mọi tình huống runtime.

Lưu tay Lưu/Ctrl+S hoặc lưu trước gửi. Web giữ bản phục hồi cục bộ; không autosave server. Năm/tổ dùng lease 90s, heartbeat20s và session/revision; header chống lặp hiện hành Idempotency-Key. Lưu tuần kiểm version tài liệu và hồ sơ gốc. Bản thay thế/lớp cũ theo EDU-26, không xóa lịch sử. Riêng điều chỉnh/bổ sung từng ngày ghi qua API riêng theo EDU-27 (tối đa4.000 ký tự/ngày), không mở khóa nội dung đã duyệt.

Binary học liệu lưu storage private, metadata gắn hoạt động; kiểm quyền ở BE, fingerprint đổi thì gắn lại. Trình chiếu không đổi trạng thái. AI gợi ý chỉ nháp, người dùng giữ/bỏ, tắt AI vẫn soạn được; API AI BE còn proposed. Xuất Word/in theo mẫu, chừa chỗ ký tay; không ký ảnh/ký số trong hệ thống. Phụ huynh xem hoạt động theo EDU-37 giữ trạng thái triển khai riêng, chưa được xác minh ở lượt chốt này.

## 9. Gap không được diễn giải thành thay đổi nghiệp vụ

Module card và [biên bản xác nhận](../education-plan-confirmation-2026-11-09.md) ghi riêng: nhãn chờ duyệt/phiên bản tổ; cập nhật nguồn/rà soát/tách bảng spec015; cổng TT duyệt; thiếu guard TEAM cho nguồn lớp; preview file annual; kiểm bất biến nội dung lớp ở server. Không đánh dấu các phần này hoàn tất chỉ vì bộ câu hỏi đã CONFIRMED.
