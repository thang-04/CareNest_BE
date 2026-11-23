# Flow — Kế hoạch giáo dục và giáo án

> **Tiền chốt 23/11/2026 (EDU-107):** Các trang Kế hoạch giáo dục dùng chung bộ chọn năm trên Web và lọc dữ liệu theo ID năm học, gồm cả Kho học liệu. Cấu hình kế hoạch và Nguồn chương trình của Bộ dùng dữ liệu chung. Các thao tác chủ động lấy nguồn năm khác áp dụng điều kiện đã chốt của từng nghiệp vụ.

> **Quyết định mới user 10/11/2026 — EDU-72:** Bỏ thao tác lập kế hoạch tổ lần đầu. Khi kế hoạch năm được ban hành, hệ thống tự tạo một TEAM DRAFT cùng năm học/nhóm tuổi, gắn phiên bản năm đã ban hành mới nhất và giao tổ trưởng duy nhất đang phụ trách tuổi đó soạn. User làm rõ “chỉ có một tổ trưởng của một nhóm tuổi, gửi cho người đó soạn”. Quyết định này thay phần “không tự lập tổ/giữ nút lập tổ/chọn nguồn thủ công” ở EDU-62 và tài liệu09/11; các quy tắc giữ nội dung đang soạn, rà soát và giữ tổ ban hành cũ làm nguồn lớp vẫn áp dụng. Đây là chỉ đạo user, không suy là xác nhận mới từ giáo viên trường.


> **Quyết định user10/11/2026 — duyệt hồ sơ lớp:** Theo chuỗi HT cấu hình, không bắt buộc có bước TT; chỉ PHT duyệt cũng hợp lệ. Giữ cấp trên duyệt cấp dưới, chống tự duyệt, phạm vi phân công và REVIEW cấm sửa. Quyết định này thay yêu cầu TT bắt buộc trước PHT/HT trong tài liệu09/11; chưa phải bằng chứng triển khai/nghiệm thu.

> **Cập nhật ngày 09/11/2026** bám sát 4 chỉ đạo nghiệp vụ:
> 1. Phân cấp phê duyệt theo thứ bậc: HT cao nhất; PHT làm → chỉ HT duyệt; TT làm → PHT hoặc HT duyệt; kế hoạch giáo dục ngày lớp do GV làm → duyệt theo cấu hình, có thể chỉ PHT duyệt; không bắt buộc bước TT (user10/11).
> 2. Không ai được xóa kế hoạch tổ, kể cả xóa mềm/ẩn khỏi danh sách; quyền xóa kế hoạch năm theo EDU-07.
> 3. Bỏ status "Đang sửa" và quy tắc khóa 90s/20s -> Cấu hình phân công chỉ định đúng 1 thành viên tạo độc quyền.
> 4. Cả Tổ trưởng và GV đều được sửa nội dung giáo dục; GV ở lớp được tick chọn lại mục tiêu theo chủ đề.

## 1. Phạm vi và chuỗi nguồn

```text
Kế hoạch năm học (nhóm tuổi, toàn trường)
  → Kế hoạch giáo dục tổ (nhóm tuổi, kế thừa cây năm, TT được sửa nội dung, tick vào chủ đề)
    → Chủ đề nhánh của từng lớp (Lịch lớp v2, GV chỉ sửa nhánh riêng; không đổi chủ đề lớn)
      → Kế hoạch chủ đề (GV được sửa nội dung, được tick lại mục tiêu theo chủ đề)
        → Giáo án từng tuần học + 5 kế hoạch ngày
          → Đánh giá trẻ theo MT từ giáo án đã duyệt
```

Mỗi loại năm/tổ có một chuỗi cho một năm học/nhóm tuổi, tối đa một bản DRAFT/REVIEW trong chuỗi.

Khi gửi kế hoạch năm, mọi chủ đề và mẫu nhánh đã khai báo phải đủ ngày bắt đầu/kết thúc để kiểm số tuần theo khoảng áp dụng; nháp vẫn được lưu thiếu ngày. EDU-66 CONFIRMED (Tiền 10/11/2026); EDU-52 đổi: **mọi điều kiện bắt buộc khi ban hành chặn ngay từ bước gửi duyệt** — gửi và ban hành dùng chung một bộ kiểm (`requireReadyToIssue`).

Theo EDU-71 PROPOSED (user trực tiếp 09/11), nếu giới hạn bị mất hoặc chưa đủ bằng chứng phân biệt với legacy thật, chặn gửi và báo kiểm tra/khôi phục. Khôi phục theo bằng chứng, giữ trạng thái; legacy thật có bằng chứng không tự bị áp khoảng tuần mới nhưng vẫn kiểm đủ ngày. STT hệ thống tự sinh; nhãn trống về mặc định theo EDU-09/58.

Theo EDU-67/68 PROPOSED (user trực tiếp 09/11), nếu năm mới và tổ cùng sửa một nội dung thì bản cập nhật tổ giữ bản tổ, báo tổ trưởng rà soát; phiên bản năm điều chỉnh dùng cấu hình giới hạn tại lúc tạo. Khôi phục giới hạn đã mất do lỗi chỉ khi có bằng chứng và giữ trạng thái hồ sơ.

## 2. Cấu hình và duyệt — Strict Hierarchy Approval

Chỉ HT có quyền cấu hình người soạn/duyệt theo loại hồ sơ. Phân cấp duyệt tuân thủ tuyệt đối:

| Loại | Soạn (Đúng 1 người tạo độc quyền) | Chuỗi duyệt hợp lệ (Cấp trên duyệt cấp dưới) |
|---|---|---|
| Kế hoạch năm học | PHT (hoặc TT) | **Nếu PHT soạn: Bắt buộc CHỈ CÓ Hiệu trưởng duyệt**<br>Nếu TT soạn: PHT hoặc HT duyệt |
| Kế hoạch tổ | Tổ trưởng đúng nhóm tuổi | PHT hoặc HT duyệt |
| Kế hoạch giáo dục chủ đề lớp (tên nhánh + kế hoạch chủ đề + kế hoạch tuần của mọi chủ đề trong kỳ; EDU-74/75) | GV chủ nhiệm | Theo chuỗi cấu hình "chủ đề nhánh"; hạn = ngày bắt đầu học kỳ |
| Kế hoạch giáo dục ngày lớp (chỉ giáo án ngày của chủ đề; EDU-74/75) | GV chủ nhiệm | Theo cấu hình HT: ví dụ chỉ PHT hoặc TT → PHT; không bắt buộc bước TT |

- **Chống tự duyệt:** Người tham gia soạn thảo bị loại khỏi ứng viên duyệt. Cấp dưới không bao giờ được duyệt bài cấp trên.
- **Trạng thái duyệt:**
  ```text
  DRAFT (Nháp) → gửi → REVIEW (Chờ duyệt - Cấm sửa) → duyệt bước cuối → APPROVED (Ban hành)
                           └── trả lại + lý do → DRAFT (UI: Trả lại) → sửa/gửi lại
  ```
- `REVIEW` cấm sửa đối với tất cả mọi người (kể cả PHT). Muốn sửa phải Trả lại về `DRAFT` kèm lý do.
- **Chuỗi nhiều người duyệt (EDU-93, Tiền 18/11/2026):** người đúng lượt thấy "Chờ duyệt" và có nút Duyệt/Ban hành + Trả lại;
  người khác thấy "Đang chờ <vai trò bước hiện tại> duyệt" và chỉ xem. Người duyệt bước khác của chuỗi vẫn thấy hồ sơ ở trang Duyệt
  (nút Xem, mở chỉ đọc). Kế hoạch giáo dục ngày duyệt ở trang xem giáo án ngày của chủ đề; thẻ chủ đề ở trang danh sách không có nút duyệt.

## 3. Cấu hình người tạo độc quyền & Bỏ khóa 90s

- Bỏ hoàn toàn trạng thái `EDITING` ("Đang sửa") và cơ chế lease 90s / heartbeat 20s.
- Hệ thống áp dụng cấu hình phân công: **Mỗi kế hoạch chỉ cho đúng 1 thành viên được chỉ định tạo và soạn thảo**. Người khác không được quyền tạo trùng hay vào sửa đè.

## 4. Quy tắc xóa kế hoạch

- Không ai được xóa kế hoạch tổ ở bất kỳ trạng thái nào, kể cả xóa cứng hay xóa mềm/ẩn khỏi danh sách; server từ chối mọi đường xóa. Mỗi năm học/nhóm tuổi chỉ có một chuỗi kế hoạch tổ liên kết kế hoạch năm; giữ nguồn, lịch sử và liên kết lớp theo EDU-01/07/57/62. User xác nhận trực tiếp 09/11/2026.
- Quyền xóa kế hoạch năm giữ riêng theo EDU-07; quyết định bỏ lease tại EDU-70 thay phần điều kiện khóa phiên cũ, không tự mở rộng quyền xóa.

## 5. Quyền sửa Nội dung & Tính linh hoạt của Giáo viên ở lớp

- Dữ liệu cây mục tiêu và nội dung map tuần tự: Năm học $\rightarrow$ Tổ $\rightarrow$ Lớp.
- **Tổ trưởng:** Kế thừa từ năm, được thêm/sửa nội dung giáo dục riêng trong kế hoạch tổ và tick vào chủ đề. Không sửa nguồn năm; giáo viên phía dưới thấy nội dung tổ theo quyền và trạng thái hiện hành (EDU-65 PROPOSED, user trực tiếp 09/11).
- **Giáo viên ở lớp:** Kế thừa từ tổ, **được quyền sửa nội dung giáo dục** và **được quyền tick chọn lại mục tiêu/nội dung theo chủ đề** cho phù hợp với đặc thù học sinh lớp mình.
- **Sau khi dạy:** Giáo viên ghi nhận thực tế vào ô *"Kế hoạch điều chỉnh và bổ sung"* (EDU-27) qua endpoint riêng, lưu độc lập ở mọi trạng thái mà không ảnh hưởng trạng thái `APPROVED` của giáo án.

## Mốc duyệt theo thời gian dạy (EDU-74, Tiền 10/11/2026)

```text
Kế hoạch giáo dục chủ đề (hạn = ngày bắt đầu học kỳ)   ──ban hành──▶ Kế hoạch giáo dục ngày được gửi (hạn = ngày bắt đầu chủ đề)
   tên nhánh + kế hoạch chủ đề + kế hoạch tuần          chỉ giáo án ngày mọi tuần của chủ đề
   của mọi chủ đề trong kỳ (EDU-75)
Hạn − 7 ngày: "Gần đến hạn" + nhắc một lần │ Từ hạn chưa ban hành: "Quá hạn", khóa gửi → HT mở hạn (lý do, hạn mới)
```

- Nghiệp vụ đích (EDU-97, Tiền 19/11/2026): bản Chờ duyệt được xử lý thêm số ngày do Hiệu trưởng cấu hình. Hết hạn, chặn duyệt, ban hành và trả lại kể cả với Hiệu trưởng. Hiệu trưởng mở lại hạn duyệt tại Kế hoạch quá hạn, ghi lý do, hạn mới và lịch sử. Ban hành sau hạn vẫn hiện "Ban hành trễ". Số ngày để người soạn gửi lại sau khi bị trả lại trễ được cấu hình riêng theo EDU-94. Chỉ Hiệu trưởng được thay đổi cả hai giá trị. Hiệu trưởng thấy và mở hạn cho mọi hồ sơ đủ điều kiện dù không thuộc chuỗi duyệt. Màn Kế hoạch quá hạn mặc định hiện Tất cả và lọc được theo Quá hạn gửi (EDU-76) hoặc Quá hạn duyệt (EDU-97).
- Trạng thái duyệt (Nháp/Chờ duyệt/Bị trả lại/Đã ban hành) và trạng thái hạn (Gần đến hạn/Quá hạn/Đúng hạn/Ban hành trễ)
  hiện tách rõ; mở hạn/mở khóa chỉ Hiệu trưởng (Tiền 11/11/2026, spec 005).
- **EDU-73 (Tiền 23/11/2026):** ở các trang chính, mọi vai trò khi chỉ có quyền xem chỉ thấy bản đã ban hành trong phạm vi mình được xem. Trang Dự kiến chủ đề - chủ đề nhánh trả lịch đã ban hành theo học kỳ cho người chỉ xem. Người được phân công soạn xem được lịch dự kiến và các trạng thái khác trong phạm vi soạn.
- Hồ sơ chưa ban hành đang quá hạn hoặc đã mở hạn chỉ xuất hiện ở **Kế hoạch quá hạn** cho quyền mở hạn theo EDU-76/97. Luồng soạn và hàng chờ duyệt theo EDU-93 vẫn áp dụng theo phân công.

## Cách duyệt cố định, phiên bản và khóa theo thời gian (spec 004 — Tiền 11/11/2026, EDU-75/76/77)

- Không còn lựa chọn "theo học kỳ"/"theo chủ đề". Gửi kế hoạch giáo dục chủ đề thiếu tên nhánh, kế hoạch chủ đề hoặc kế hoạch tuần ⇒ chặn
  (7387), hiện danh sách mục thiếu. Kế hoạch giáo dục chủ đề chưa ban hành vẫn soạn/lưu được giáo án ngày, chỉ chưa gửi kế hoạch giáo dục ngày.
- Màn hình: "Chủ đề / Chủ đề nhánh" và kế hoạch chủ đề chỉ để sửa (không nút gửi). Kế hoạch giáo dục chủ đề gửi/duyệt ở **Kế hoạch giáo
  dục chủ đề** (tab học kỳ 1/2); kế hoạch giáo dục ngày ở **Kế hoạch giáo dục ngày** (kế hoạch tuần chỉ xem ở trên + giáo án 5 ngày).

```text
Đã ban hành ──(trước ngày bắt đầu)── Tạo phiên bản điều chỉnh → Nháp → gửi → duyệt → ban hành (bản cũ hiệu lực tới lúc này)
            ──(từ ngày bắt đầu)───── Khóa (7388 gói/hồ sơ; 7389 năm/tổ) → Mở khóa (lý do + hạn mới) → làm tiếp tới hạn mới
```

| Tài liệu | Ngày bắt đầu (mốc khóa) | Mở khóa |
|---|---|---|
| Kế hoạch năm học, kế hoạch tổ | ngày bắt đầu năm học (khóa gửi bản chưa ban hành, tạo phiên bản năm, cập nhật tổ) | Hiệu trưởng |
| Kế hoạch giáo dục chủ đề | ngày bắt đầu học kỳ | HT/PHT |
| Kế hoạch giáo dục ngày (giáo án ngày) | ngày bắt đầu chủ đề; sau đó chỉ còn ô điều chỉnh sau dạy (EDU-27) | HT/PHT |

- Phiên bản dở khi tới ngày bắt đầu: Chờ duyệt xử lý trong hạn theo EDU-97; Nháp bị khóa theo EDU-76. Bản đã ban hành trước đó tiếp tục hiệu lực.
- Phiên bản mới của kế hoạch giáo dục chủ đề ban hành mà đổi kế hoạch tuần ⇒ chỉ giáo án ngày của tuần đó về Nháp (sự kiện `week_changed`,
  báo GVCN); tuần khác giữ ban hành.


## Thẻ hoạt động, kế hoạch tuần theo kế hoạch giáo dục ngày, kiểm đủ MT (spec 006 — Tiền 12/11/2026, EDU-78/79/80)

```text
Kế hoạch chủ đề: MT (chỉ đọc) → nội dung (chỉ đọc) → thẻ hoạt động {loại giờ, tên, MT[], nội dung[]}   (EDU-78)
  → Kế hoạch tuần theo kỳ (kế hoạch giáo dục chủ đề): ô Thứ × dòng chọn thẻ, một thẻ xếp nhiều lần; gõ tay ⇒ chưa gắn MT
    → [kế hoạch giáo dục chủ đề ban hành, trước ngày bắt đầu chủ đề] sửa tuần ở trang Kế hoạch giáo dục ngày ⇒ day.content.week (EDU-79)
      → Kế hoạch giáo dục ngày = kế hoạch ngày (+ kế hoạch tuần nếu có sửa); gửi chặn nếu MT chủ đề chưa dạy (7396), chủ đề cuối
        hoặc kế hoạch giáo dục chủ đề cuối thiếu MT năm (7397) (EDU-80)
        → ban hành ⇒ bản tuần trong hồ sơ là bản hiệu lực cho trang ngày và đánh giá trẻ
```

- MT của một lượt xếp luôn = MT của thẻ (sửa MT của thẻ khi kế hoạch chủ đề còn sửa được thì các ô đổi theo).
- Dữ liệu cũ dạng "Loại giờ: …" chuyển thành thẻ khi đọc, id giữ `goalId#i` nên ô tuần, học liệu, MT đánh giá cũ không đổi.
- Kế hoạch giáo dục chủ đề thiếu MT chỉ cảnh báo (hộp gửi, dòng tổng, tab "Bảng phủ MT"); chặn theo MT chỉ ở kế hoạch giáo dục ngày và cuối năm.
- Người duyệt kế hoạch giáo dục ngày thấy ô tuần khác bản theo kỳ được đánh dấu; kế hoạch giáo dục chủ đề ra bản mới đổi tuần ⇒ ngày về Nháp, bản tuần
  sửa theo hồ sơ vẫn giữ, có nhãn "bản theo kỳ đã đổi".

## Kế hoạch theo trạng thái năm học (EDU-89 — Cần đề xuất, Tiền chốt 13/11/2026)

| Năm học | Lập / sửa / gửi / duyệt kế hoạch | Ghi chú |
|---|---|---|
| Sắp tới, trước ngày mở chuẩn bị | Không (2040 `SCHOOL_YEAR_NOT_PREPARING`) | HT/PHT thấy năm, chỉ xem (CFG-YR-05) |
| Sắp tới, từ ngày mở chuẩn bị | Có | GV/TT năm mới thấy lớp từ ngày bắt đầu phân công |
| Đang hoạt động | Có | |
| Đã kết thúc (CLOSED) | Không (2003 `SCHOOL_YEAR_CLOSED`) | Chỉ xem/in (CFG-YR-01) |


## Header thích ứng — EDU-92 (Tiền 17/11/2026)

Tiền điều chỉnh tiếp 17/11/2026: thanh thao tác sát dưới header dùng chung, đứng trước thẻ lọc/tab; bỏ khoảng trống do tiêu đề ẩn. Quay lại (nếu có) ở trái, chi tiết/lưu/gửi/duyệt/xuất ở phải; chip/nút phụ vẫn nhóm trái. Giữ một hàng; thiếu chiều rộng thì gom vào menu ba gạch. Giữ quyền EDU-54/90, disabled và hộp xác nhận. Áp dụng năm/tổ, nhánh, chủ đề, tuần/ngày và cấu hình; không thay API/luồng nghiệp vụ. Thứ tự này thay bộ lọc/tab trước hàng nút của quyết định 12/11.

Ngoại lệ Tiền chốt tiếp 17/11/2026: trang Mở hạn/Kế hoạch quá hạn bỏ thanh header thao tác riêng; Mở hạn hàng loạt đặt sát bảng danh sách, giữ chọn nhiều dòng, mở từng dòng, quyền và hộp xác nhận.

Tiền điều chỉnh tiếp 17/11/2026 (ảnh tên Kế hoạch năm học khi cuộn): bỏ mọi tên ngắn của tài liệu/lớp/chủ đề/tuần tự hiện trong thanh thao tác khi cuộn. Tên trang vẫn ở breadcrumb/header chung; giữ nguyên trạng thái, nút, quyền và ghim thanh thao tác. Thay hành vi hiện tên ngắn lúc dính của quyết định 12/11.

EDU-98 (Tiền, 20/11/2026): danh mục, cấu hình, khung giáo án và nhóm tuổi nhận diện khi nhập file do BE cung cấp. BE khởi tạo dữ liệu chuẩn còn thiếu khi chạy lần đầu.

EDU-98 (20/11/2026). Bộ lọc nhóm tuổi dùng danh mục age_groups; ở Dự kiến chủ đề – chủ đề nhánh, Kế hoạch giáo dục chủ đề và Kế hoạch giáo dục ngày, ô Nhóm tuổi hiện toàn bộ danh mục. Ô Lớp chỉ hiện các lớp được phép xem và trang báo Không tìm thấy lớp nếu nhóm đang chọn không có lớp phù hợp. Các màn chọn lớp khác chỉ hiện nhóm có lớp được phép xem. Mọi bộ lọc nhóm tuổi trong kế hoạch giáo dục gộp theo cặp tháng bắt đầu và kết thúc, khớp đúng cả hai đầu mút. Ô chọn hiển thị tên ngắn của nhóm tuổi đại diện cho khoảng tháng, dùng tên đầy đủ nếu thiếu tên ngắn. Chương trình Bộ lưu khoảng tháng trên dòng danh mục và truy vấn trực tiếp theo cặp tháng này. Nhóm trường tạo lại có cùng khoảng tháng vẫn dùng được chương trình tương ứng. Bảng edu_plan_defaults lưu config_reset và day_template.

EDU-99 (Tiền, 20/11/2026): thông báo lỗi và kiểm tra dữ liệu dùng đỏ. Hướng dẫn, mục mới và kết quả đã xử lý dùng xanh lá. Lý do trả lại hiện chữ đỏ, không có nền hoặc khung đỏ.

## AI ghép nội dung — EDU-103/104/105/106 (Tiền 22/11/2026)

Thiết kế: `UI-giaoan/docs/superpowers/specs/2026-11-22-edu-ai-content-matching-design.md`. Thứ tự làm: (1) EDU-103, (2) EDU-104, (3) EDU-105, (4) EDU-106.

1. **Kế hoạch tổ — AI gom nội dung vào chủ đề (EDU-103):** Thao tác card nội dung → modal chủ đề + ô yêu cầu → (tự Lưu nếu còn thay đổi, EDU-22) → BE đọc chủ đề/lễ/nhánh/nội dung từ DB, hỏi AI theo lĩnh vực → kiểm id, trùng, căn cứ nguyên văn → màn duyệt gom theo chủ đề → căn cứ → Áp dụng (chỉ thêm tick vào bản đang soạn) → tổ trưởng tick nốt bằng ô tìm + lọc "Chưa thuộc chủ đề nào". Không giới hạn số chủ đề mỗi nội dung.
2. **Kế hoạch tổ — lấy tick năm trước (EDU-104):** nguồn là kế hoạch tổ APPROVED mới nhất cùng nhóm tuổi năm học liền trước; xác nhận cặp chủ đề (tự đoán theo tên) → nội dung khớp theo chữ → xem trước → Áp dụng (chỉ thêm).
3. **Ví dụ mẫu (EDU-105):** tick của kế hoạch tổ APPROVED làm ví dụ trong prompt EDU-103, có giới hạn, tắt được bằng cấu hình. BE đã cài selector tối đa 5/chủ đề và 60/toàn yêu cầu, ưu tiên cùng khoảng tháng tuổi, bỏ nguồn disabled; `CARENEST_EDU_AI_EXAMPLES_ENABLED=false` mặc định, thử đối chứng ở lĩnh vực ngôn ngữ chưa cho thấy cải thiện rõ.
4. **Kế hoạch năm — nội dung mục C cho mục tiêu (EDU-106):** mục tiêu chưa có nội dung → AI chọn nội dung mục C cùng nhóm tuổi/lĩnh vực → duyệt → Áp dụng chép chữ. BE đã có API `POST /education-plans/ai/year-plans/{planId}/goal-content-map` chỉ trả nháp đã lọc; chữ nội dung do BE đọc từ catalog. Web tự Lưu trước, duyệt theo mục tiêu, rồi chép vào bản soạn. Đã chạy thử trên dữ liệu năm thật trước khi làm UI.

Đo chất lượng: mỗi lần Áp dụng ghi log số dòng gợi ý/giữ (không lưu DB); tỉ lệ giữ < ~80% ⇒ sửa prompt/ví dụ. AI tắt/lỗi ⇒ báo, soạn tay bình thường (EDU-95).
