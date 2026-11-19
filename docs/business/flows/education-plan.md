# Flow — Kế hoạch giáo dục và giáo án

> **Quyết định mới user 10/11/2026 — EDU-72:** Bỏ thao tác lập kế hoạch tổ lần đầu. Khi kế hoạch năm được ban hành, hệ thống tự tạo một TEAM DRAFT cùng năm học/nhóm tuổi, gắn phiên bản năm đã ban hành mới nhất và giao tổ trưởng duy nhất đang phụ trách tuổi đó soạn. User làm rõ “chỉ có một tổ trưởng của một nhóm tuổi, gửi cho người đó soạn”. Quyết định này thay phần “không tự lập tổ/giữ nút lập tổ/chọn nguồn thủ công” ở EDU-62 và tài liệu09/11; các quy tắc giữ nội dung đang soạn, rà soát và giữ tổ ban hành cũ làm nguồn lớp vẫn áp dụng. Đây là chỉ đạo user, không suy là xác nhận mới từ giáo viên trường.


> **Quyết định user10/11/2026 — duyệt hồ sơ lớp:** Theo chuỗi HT cấu hình, không bắt buộc có bước TT; chỉ PHT duyệt cũng hợp lệ. Giữ cấp trên duyệt cấp dưới, chống tự duyệt, phạm vi phân công và REVIEW cấm sửa. Quyết định này thay yêu cầu TT bắt buộc trước PHT/HT trong tài liệu09/11; chưa phải bằng chứng triển khai/nghiệm thu.

> **Cập nhật ngày 09/11/2026** bám sát 4 chỉ đạo nghiệp vụ:
> 1. Phân cấp phê duyệt theo thứ bậc: HT cao nhất; PHT làm → chỉ HT duyệt; TT làm → PHT hoặc HT duyệt; hồ sơ chủ đề lớp do GV làm → duyệt theo cấu hình, có thể chỉ PHT duyệt; không bắt buộc bước TT (user10/11).
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
| Gói học kỳ lớp (tên nhánh + kế hoạch chủ đề + kế hoạch tuần của mọi chủ đề trong kỳ; EDU-74/75) | GV chủ nhiệm | Theo chuỗi cấu hình "chủ đề nhánh"; hạn = ngày bắt đầu học kỳ |
| Hồ sơ chủ đề lớp (chỉ giáo án ngày của chủ đề; EDU-74/75) | GV chủ nhiệm | Theo cấu hình HT: ví dụ chỉ PHT hoặc TT → PHT; không bắt buộc bước TT |

- **Chống tự duyệt:** Người tham gia soạn thảo bị loại khỏi ứng viên duyệt. Cấp dưới không bao giờ được duyệt bài cấp trên.
- **Trạng thái duyệt:**
  ```text
  DRAFT (Nháp) → gửi → REVIEW (Chờ duyệt - Cấm sửa) → duyệt bước cuối → APPROVED (Ban hành)
                           └── trả lại + lý do → DRAFT (UI: Trả lại) → sửa/gửi lại
  ```
- `REVIEW` cấm sửa đối với tất cả mọi người (kể cả PHT). Muốn sửa phải Trả lại về `DRAFT` kèm lý do.

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
Gói học kỳ (hạn = ngày bắt đầu học kỳ)   ──ban hành──▶ Hồ sơ chủ đề được gửi (hạn = ngày bắt đầu chủ đề)
   tên nhánh + kế hoạch chủ đề + kế hoạch tuần          chỉ giáo án ngày mọi tuần của chủ đề
   của mọi chủ đề trong kỳ (EDU-75)
Hạn − 7 ngày: "Gần đến hạn" + nhắc một lần │ Từ hạn chưa ban hành: "Quá hạn", khóa gửi → HT mở hạn (lý do, hạn mới)
```

- Bản đang Chờ duyệt khi tới hạn vẫn được duyệt; ban hành sau hạn hiện "Ban hành trễ".
- Trạng thái duyệt (Nháp/Chờ duyệt/Bị trả lại/Đã ban hành) và trạng thái hạn (Gần đến hạn/Quá hạn/Đúng hạn/Ban hành trễ)
  hiện tách rõ; mở hạn/mở khóa chỉ Hiệu trưởng (Tiền 11/11/2026, spec 005).
- Người chỉ xem chỉ thấy lịch nhánh của học kỳ đã ban hành.

## Cách duyệt cố định, phiên bản và khóa theo thời gian (spec 004 — Tiền 11/11/2026, EDU-75/76/77)

- Không còn lựa chọn "theo học kỳ"/"theo chủ đề". Gửi gói học kỳ thiếu tên nhánh, kế hoạch chủ đề hoặc kế hoạch tuần ⇒ chặn
  (7387), hiện danh sách mục thiếu. Gói học kỳ chưa ban hành vẫn soạn/lưu được giáo án ngày, chỉ chưa gửi hồ sơ chủ đề.
- Màn hình: "Chủ đề / Chủ đề nhánh" và kế hoạch chủ đề chỉ để sửa (không nút gửi). Gói học kỳ gửi/duyệt ở **Kế hoạch giáo
  dục chủ đề** (tab học kỳ 1/2); hồ sơ chủ đề ở **Kế hoạch giáo dục ngày** (kế hoạch tuần chỉ xem ở trên + giáo án 5 ngày).

```text
Đã ban hành ──(trước ngày bắt đầu)── Tạo phiên bản điều chỉnh → Nháp → gửi → duyệt → ban hành (bản cũ hiệu lực tới lúc này)
            ──(từ ngày bắt đầu)───── Khóa (7388 gói/hồ sơ; 7389 năm/tổ) → Mở khóa (lý do + hạn mới) → làm tiếp tới hạn mới
```

| Tài liệu | Ngày bắt đầu (mốc khóa) | Mở khóa |
|---|---|---|
| Kế hoạch năm học, kế hoạch tổ | ngày bắt đầu năm học (khóa gửi bản chưa ban hành, tạo phiên bản năm, cập nhật tổ) | Hiệu trưởng |
| Gói học kỳ | ngày bắt đầu học kỳ | HT/PHT |
| Hồ sơ chủ đề (giáo án ngày) | ngày bắt đầu chủ đề; sau đó chỉ còn ô điều chỉnh sau dạy (EDU-27) | HT/PHT |

- Phiên bản dở khi tới ngày bắt đầu: Chờ duyệt vẫn duyệt/trả lại cho xong (trả lại thì không gửi lại được); Nháp bị khóa.
- Phiên bản mới của gói học kỳ ban hành mà đổi kế hoạch tuần ⇒ chỉ giáo án ngày của tuần đó về Nháp (sự kiện `week_changed`,
  báo GVCN); tuần khác giữ ban hành.

