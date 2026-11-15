# Research — Feature011

## Bảng WHO cân nặng, chiều cao theo tuổi
- Nguồn: who.int, bảng z-score Excel (cột Month, L, M, S, SD…), tải 04/11/2026.
  - W/A: Child Growth Standards 2006 `wfa_boys_0-to-5-years_zscores.xlsx`, `wfa_girls_…` (0–60 tháng); Growth Reference 2007 cân nặng theo tuổi 5–10 tuổi (61–120 tháng) — trên trang who.int tệp tên `hfa-boys-z-who-2007-exp_0ff9c43c-….xlsx`, `hfa-girls-z-who-2007-exp_7ea58763-….xlsx` dù nằm trong thư mục `weight-for-age-(5-10-years)`; đã kiểm nội dung là cân nặng (bé trai 61 tháng M 18,5057 kg).
  - L/H/A: 2006 `lhfa_boys_0-to-2-years_zscores.xlsx` (đo nằm, 0–24), `lhfa_boys_2-to-5-years_zscores.xlsx` (đo đứng, 24–60), và girls; 2007 `hfa-boys-z-who-2007-exp.xlsx`, `hfa-girls-…` (61–228).
- Ghép: W/A 0–60 bảng 2006, 61–84 bảng 2007; H/A 0–23 đo nằm, 24–60 đo đứng (tháng 24 có ở cả hai, chọn đo đứng như BMI), 61–84 bảng 2007. Kết quả `src/main/resources/health/who-weight-for-age.csv`, `who-height-for-age.csv` (170 dòng mỗi tệp). Số ghi dạng ngắn nhất của cùng giá trị double trong Excel (0.34870000000000001 ⇒ 0.3487), không làm tròn, không sửa tay.
- Kiểm khi ghép: mỗi dòng −2SD tính từ L, M, S khớp cột SD2neg WHO theo số chữ số WHO in (2006: 1 chữ số, 2007: 3 chữ số). Test đối chiếu thêm SD công bố (vd. bé trai 61 tháng W/A −2SD 14,367; H/A −2SD 101,082).

## Z-score
- LMS: z = ((x/M)^L − 1)/(L·S). W/A dùng giới hạn đuôi của WHO ngoài ±3SD như BMI (chỉ số theo cân nặng); H/A có L = 1 ⇒ z = (x/M − 1)/S, không giới hạn đuôi (cách WHO tính chiều dài/chiều cao).
- Z làm tròn 2 chữ số rồi so ngưỡng; tuổi tháng tròn tại ngày đo (như HLT-18). Trẻ < 24 tháng coi là đo nằm (hệ thống không ghi cách đo).

## Phương án đã loại
- Bảng báo cáo lưu sẵn theo tháng: lệch khi sửa số đo, thêm migration.
- Đếm trẻ theo enrollment ngày cuối tháng: lệch với danh sách lớp của đợt/kỳ (vắng, nháp).
- Thư viện tính z-score ngoài: thêm dependency, không cần.
