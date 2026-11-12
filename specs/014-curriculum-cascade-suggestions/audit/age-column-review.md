# T032 — Đối chiếu cột tuổi toàn bộ catalog seed V34

Nguồn chính thức: https://congbao.chinhphu.vn/van-ban/van-ban-hop-nhat-so-01-vbhn-bgddt-33656/35646.htm
Ngày audit:08/11/2026. Chỉ đọc; không sửa repo/data/migration.
PDF84trang SHA256146c1f23b3515d46f2e2e97afcfc0a1d871ef28b6700c7db4fbacfeb56e8572f.

## Kết quả

| Loại | Toàn bộ | Khớp cột tuổi tự động | Xác nhận render bổ sung | Còn chưa xác định cột tuổi |
| --- | --- | --- | --- | --- |
| Goal mục D |327|323|4|0|
| Content mục D |101|101|0|0|
| Content mục C |423|418|5|0|
| Tổng |851|842|9|0|

Chỉ xác nhận mỗi chuỗi seed hiện có xuất hiện ở cột tuổi tương ứng trong đúng phần C/D. Đây không phải chứng nhận ranh giới câu, cấu trúc mục tiêu–nội dung, hoặc không thiếu dữ liệu gốc.

## Phương pháp tự động

1. PDF tables/header cung cấp bounds/cột tuổi: ag-2 dùng cột24–36tháng; ag-3/4/5 lần lượt cột3–4/4–5/5–6tuổi.
2. Đọc các spantext và borderrectangles vật lý tại y từng dòng. Cell gộp áp dụng cho mọi cột tuổi nó bao phủ, không tùy ý sao chép dữ liệu từ tuổi khác.
3. Chỉ dùng C nhà trẻ PDF14–22, D nhàtrẻPDF23–30; C mẫugiáoPDF40–55, D mẫugiáoPDF56–77.
4. So sánh NFC và bỏ whitespace do dàn trang, bỏ sốchúthích superscript nhưng giữ dấu câu. Đây là normalization của phép so sánh, không thay payload.
5. Mỗi result giữkind/V34line/age/text/ref/page/table, extract giữbbox và cellX. Không dùng equality theo tên nhánh hay age suy từ mục tiêu tương tự.

## Chín ngoại lệ được đọc render

| Field/V34line | Tuổi | PDF/Công báo | Lý do cần đọc render |
| --- | --- | --- | --- |
| Goal9 |24–36tháng|23–24/35–36|Câu đi qua hai trang trong cùng cột cuối|
| Goal305 |4–5tuổi|69/81|Không lặp header tuổi trên trang, finder bỏ hàngđầu|
| Goal331 |5–6tuổi|70/82|Border/extraction làm mất chuỗi mục3.1, render xác nhận đúngcột|
| Goal369 |5–6tuổi|70/82|BảngIV cuối trang có một hàngbody; finder bỏ phầnđầu|
| ContentC727 |5–6tuổi|48/60|Bảngc Khámpháxãhội nửa dưới, bullet1 cột5–6|
| ContentC728 |5–6tuổi|48/60|Bảngc Khámpháxãhội nửa dưới, bullet2 cột5–6|
| ContentC729 |5–6tuổi|48/60|Bảngc Khámpháxãhội nửa dưới, bullet3 cột5–6|
| ContentC730 |5–6tuổi|48/60|Bảngc Khámpháxãhội nửa dưới, bullet4 cột5–6|
| ContentC819 |5–6tuổi|53/65|Bullet riêng cột5–6 ở đoạn border/ngắt dòng khó nhận dạng|

## Phát hiện cấu trúc vẫn phải sửa riêng

V34line31–32, ag-2/sort_order27/goal3.1: range5 -7 bị parser cũ tách thành goal kếtthúc5 và content bắtđầu7. Hai chuỗi đều thuộc đúngcột24–36tháng, nên phép so cột tuổi vẫn match; cấu trúc goal/content là sai. PDF28/Côngbáo40 xác nhận đây là một câu liên tục không cóbullet trước7.

33goal khác bỏ dấu hai chấm cuối khi tách nội dung, có bảng riêng ../source-colon-audit.md. Đó là thay punctuation cóchủđích trước đây, không được gọi nguyên từngký tự.

## Tái lập và review

- Chạy từ CareNest_BE: python specs/014-curriculum-cascade-suggestions/audit/audit-age-cells.py (yêu cầu PyMuPDF và bản PDF chính thức tại %TEMP%/carenest014-source-audit/official.pdf, SHA-256 như trên).
- Output auto age-cell-audit.json và age-cell-extract.json. Script tự đọc V34 hiện có, dùngPDFofficial.pdf; không dựa dữ liệu database.
- File age-column-review-results.json giữ metadata hash, toàn bộ851rows và9manualEvidence. Không đổi các9ngoạilệ thành autoPASS.
- Render các page23/24/28/48/53/69/70 để review lại manual; PNG đã có trong cùngthưmục.
- Khi PDF hoặc V34đổi, rerun auto và đọc lại manual, không tái dùng chữký cũ làm chứng cứ.
- Chưa kiểm runtimeDB/API, chưa xác minh chiều ngược mọi câu/ô của PDF đều có seed, chưa kiểm nhóm/lĩnhvực của tấtcả mục. Không kếtluận catalog hoàn hảo100%; kếtluận chỉ là không phát hiện seed bị đặt nhầm cột tuổi trong851chuỗi đã kiểm.
