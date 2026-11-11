# Tiêu chí chất lượng gợi ý thực đơn AI — căn cứ và cách đảm bảo

Phạm vi: gợi ý kế hoạch thực đơn tuần theo khối (AI-07 (4)), Feature014. Code: `service/meals/impl/Menu*`, `integration/ai/`. Plan: `docs/plans/active/2026-11-10-boarding-menu-ai.md`. Cập nhật 10/11/2026.

## 1. Luận điểm chính

Output AI **không được dùng thẳng**. Mỗi gợi ý đi qua bốn lớp, ba lớp đầu là code tất định:

| Lớp | Việc | AI có thể làm sai ở đây không |
| --- | --- | --- |
| 1. Lọc trước | Code chọn món ứng viên cho từng ô từ danh mục của trường (đúng loại, đủ định lượng mọi nhóm tuổi, thực phẩm đang dùng, có nguồn số liệu) | Không: AI chỉ thấy món đã lọc, dưới bí danh `D1..Dn` |
| 2. Kiểm cứng | Guard cấu trúc + `MealPlanEvaluator` (cùng bộ kiểm chặn công bố tuần, NUT-08) | Không: sai cấu trúc hoặc còn lỗi chặn ⇒ không bao giờ lưu với nhãn AI |
| 3. So với phương án không AI | Cùng dữ liệu, code tự lập phương án theo quy tắc; chấm điểm cả hai bằng một công thức | Không: AI thua điểm ⇒ hệ thống dùng phương án theo quy tắc, ghi `AI_NOT_BETTER` |
| 4. Người duyệt | PHT xem bảng kiểm, dùng hoặc bỏ; công bố vẫn qua cổng cũ | AI không công bố được (AI-02) |

Vì vậy câu hỏi "AI gợi ý vớ vẩn thì sao" có câu trả lời cụ thể: gợi ý vớ vẩn **không qua được lớp 2 hoặc 3**, người dùng nhận phương án theo quy tắc kèm lý do. Mọi con số hiển thị (kcal, P:L:G, giá) do code tính, không lấy từ model (NUT-05, NUT-06); lý do của model bị xóa chữ số và câu có thuật ngữ y khoa.

## 2. Ma trận tiêu chí

Mức ép: **Chặn** = không đạt thì không thể là phương án được chọn; **Điểm** = trừ điểm khi so AI với phương án quy tắc; **Ưu tiên** = xếp món ứng viên/nhãn trong prompt, không bảo đảm; **Hiển thị** = báo cho PHT.

| Tiêu chí | Căn cứ | Mức ép | Mã trong bảng kiểm |
| --- | --- | --- | --- |
| Năng lượng, cơ cấu đạm–béo–bột đường (% năng lượng), đạm/béo động vật–thực vật (g) theo định mức nhóm tuổi | NUT-18 (nhu cầu dinh dưỡng của Chương trình GDMN, Bộ GD&ĐT), NUT-24; số định mức do PHT nhập (NUT-17) | Chặn | `NORM_BALANCE` |
| Món có đủ định lượng cho mọi nhóm tuổi, thực phẩm có số liệu dinh dưỡng có nguồn | NUT-05 (cột `nutrient_source` bắt buộc), NUT-18 (thành phần theo Viện Dinh dưỡng), NUT-09 | Chặn (lọc ứng viên) | `CATALOG` |
| Mỗi bữa khối phục vụ đều có món, đúng khung bữa (cơm–mặn–canh–rau…) | NUT-08, NUT-13 | Chặn | `NORM_BALANCE` |
| Món ứng viên có tới được khoảng định mức năng lượng: mỗi ô lấy món nhiều (ít) năng lượng nhất mà tổng ngày vẫn dưới mức tối thiểu (trên mức tối đa) ⇒ lỗi do định lượng/danh mục, đổi món không sửa được. Chỉ có dòng này khi còn ngày chưa đạt và chứng minh được | NUT-18, NUT-24; số tính như evaluator | Hiển thị (`FAIL`) | `KCAL_REACH` |
| Món đang dùng, thực phẩm đang dùng (không gợi ý món trường đã ngừng hoặc nguyên liệu không còn mua) | Danh mục `CatalogStatus` | Chặn (lọc ứng viên) | `CATALOG` |
| Tỷ lệ năng lượng từng bữa so nhu cầu cả ngày | NUT-25 | Điểm + Hiển thị | `SESSION_KCAL_OFF` |
| Vi chất (canxi, sắt, vitamin A, B1, B2, PP, C, photpho) | NUT-26 (Bảng thành phần thực phẩm VN, Viện Dinh dưỡng) | Điểm + Hiển thị | `MICRONUTRIENT_OFF` |
| Chi phí/trẻ trong giá suất | NUT-19 | Điểm + Hiển thị | `OVER_BUDGET` |
| Món chính không lặp trong tuần | NUT-20 | Điểm + Hiển thị | `DISH_REPEATED` |
| Không lặp thực đơn 2 tuần trước (lịch sử) | NUT-20; dữ liệu kế hoạch tuần đã lưu | Điểm + Ưu tiên | `HISTORY` |
| Món trường đã nấu (đã có trong tuần công bố 12 tuần gần đây) | NUT-13; dữ liệu tuần đã công bố | Ưu tiên + Hiển thị | `FAMILIAR` |
| Hợp mùa: món có trong bộ thực đơn mẫu trùng nhãn mùa PHT chọn | NUT-13 (bộ mẫu 4 tuần có nhãn mùa do trường lập) | Ưu tiên + Hiển thị | `SEASON` |
| Thực phẩm kho khô còn hàng còn hạn ở ít nhất một điểm trường | NUT-11 (tồn theo lô, hạn dùng) | Điểm + Ưu tiên + Hiển thị | `DRY_STOCK_OUT` |
| Chất gây dị ứng trong tuần | NUT-09, NUT-23 (dị ứng nặng xử lý bằng món thay thế, không đổi thực đơn chung) | Hiển thị | `ALLERGENS` |
| Không gửi dữ liệu trẻ cho AI | AI-04, NUT-09; task `PUBLIC_CATALOG`, cấu hình sai ⇒ ứng dụng không khởi động | Chặn (kiến trúc) | — |
| Không lưu, không log prompt/response | AI-05 | Chặn (kiến trúc, có test) | — |

Mỗi nháp trả `checks` (bảng kiểm trên) cho đúng phương án được chọn, kèm rule căn cứ, lưu cùng nháp nên xem lại được.

## 3. Căn cứ văn bản — nói đúng những gì có

- **Định mức dinh dưỡng**: NUT-18 lấy nhu cầu dinh dưỡng theo Chương trình giáo dục mầm non của Bộ GD&ĐT; văn bản hợp nhất repo đang dẫn là VBHN 01/VBHN-BGDĐT ngày 13/4/2021 (EDU-28). Hệ thống **không gắn cứng** con số; PHT nhập định mức theo nhóm tuổi (NUT-17, NUT-24), hệ thống ép đúng số đã nhập. Trước khi bảo vệ, nhóm cần mở bản gốc, đối chiếu mục nhu cầu dinh dưỡng (tỷ lệ năng lượng tại trường, cơ cấu P:L:G cho nhà trẻ và mẫu giáo) và nhập đúng vào cấu hình demo.
- **Thành phần thực phẩm, vi chất**: Bảng thành phần thực phẩm Việt Nam, Viện Dinh dưỡng (NUT-18, NUT-26); mỗi thực phẩm bắt buộc ghi nguồn (NUT-05).
- **Lặp món**: NUT-20 ghi "căn cứ quy định của Bộ; văn bản cụ thể chưa ghi" ⇒ **chưa có số hiệu văn bản**. Không nên dẫn một nghị định cụ thể khi chưa tra được.
- **Nghị định về an toàn thực phẩm**: thuộc vận hành bếp (lưu mẫu thức ăn NUT-21, kiểm nhận hàng NUT-12), không quyết định món nào vào thực đơn. Repo chưa ghi số hiệu nghị định nào; AI gợi ý không thay đổi quy trình đó.

## 4. Giới hạn — trả lời trung thực nếu bị hỏi

- **Địa phương**: dữ liệu không có thuộc tính vùng miền. Lý lẽ: AI chỉ chọn trong danh mục món **do chính trường lập** (NUT-18: danh mục ban đầu team chuẩn bị, trường bổ sung), nên món luôn là món trường nấu được, quen với địa phương. Không có điểm "đặc sản vùng".
- **Mùa**: không có thuộc tính mùa theo thực phẩm. Tín hiệu mùa lấy từ bộ thực đơn mẫu theo mùa do PHT lập; khối chưa có bộ mẫu trùng mùa thì bảng kiểm ghi "chưa đối chiếu được".
- **Hàng tươi**: đặt nhà cung cấp hằng ngày ngoài hệ thống (NUT-12), không có dữ liệu "còn hàng"; chỉ kho khô được xét. Thực phẩm ngừng dùng bị loại hẳn.
- **Kho khô theo điểm trường**: món bị đánh dấu hết hàng khi mọi điểm trường người lập xem được đều hết lô còn hạn tại thứ Hai của tuần; không chặn vì PHT có thể nhập thêm trước ngày ăn (NUT-11), sau khi dùng nháp màn kế hoạch tuần vẫn cảnh báo thiếu kho như cũ.
- **Khẩu vị trẻ**: hệ thống không có dữ liệu trẻ ăn hết hay bỏ thừa; "món trường đã nấu" là tín hiệu gần nhất.
- **Chất lượng AI so với quy tắc chưa đo bằng model thật**: test hiện dùng gateway giả. Phase 3 chạy eval tay 8 tình huống × 3 lần; mốc bật cho demo: 0 lần lọt lỗi chặn và AI thắng phương án quy tắc ≥ 60% case hợp lệ (AC-10). Không đạt thì ghi đúng kết quả.

## 5. Câu hỏi hội đồng hay gặp

- **"Làm sao biết AI không bịa món, bịa số?"** AI chỉ trả bí danh món trong danh sách ứng viên của từng ô; bí danh lạ, sai ô, thiếu ô ⇒ loại cả output (`MenuAiOutputParserTest`). Số liệu hiển thị do evaluator tính; lý do của model bị xóa chữ số.
- **"Nếu AI tệ thì người dùng có bị ảnh hưởng?"** Không: phương án theo quy tắc luôn được tính trước; AI chỉ được chọn khi đạt mọi kiểm tra chặn và điểm không thấp hơn (`MenuSuggestionEngineTest`). AI tắt, hết hạn mức, quá thời gian ⇒ vẫn có phương án hoặc thông báo không có phương án kèm nguyên nhân (AI-03). Danh mục chưa ghép được thực đơn đạt định mức ⇒ vẫn hiện phương án theo quy tắc gắn nhãn chưa đạt kèm lỗi từng ngày; không bao giờ gắn nhãn AI và không công bố được cho tới khi PHT sửa đạt (NUT-09, `unmetPlanStillOffersDishesButCannotBePublished`).
- **"Vì sao tin điểm số?"** Công thức tất định, có unit test (`MenuScoreTest`), áp cho cả AI và phương án quy tắc: sát định mức 45, giá 20, đa dạng 25, cảnh báo 10. Trọng số trong cấu hình, chỉ chỉnh khi có kết quả eval.
- **"AI có thấy dữ liệu trẻ không?"** Không. Task thực đơn khai `PUBLIC_CATALOG`; prompt chỉ có món, định mức nhóm tuổi, giá suất (`AiPropertiesTest`, `MenuSuggestionEngineTest` kiểm prompt không chứa id thật).
- **"Đo giá trị khi dùng thật thế nào?"** Bảng `menu_ai_drafts` lưu nguồn, điểm AI và quy tắc, lý do dự phòng, trạng thái dùng/bỏ: tính tỉ lệ `source=AI`, tỉ lệ PHT dùng, điểm chênh trung bình.

## 6. Việc mở

- Tra số hiệu văn bản cho NUT-20 (lặp món) hoặc ghi rõ là quy định nội bộ trường.
- Đối chiếu định mức demo với bản gốc Chương trình GDMN trước buổi bảo vệ.
- Muốn tiêu chí mùa/địa phương mạnh hơn: cần thêm thuộc tính mùa vụ cho thực phẩm hoặc nhãn món — là quyết định nghiệp vụ, chưa làm.
