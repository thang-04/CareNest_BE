# Research — Feature010

## Bảng WHO BMI theo tuổi
- Nguồn: who.int, bảng z-score Excel. WHO Child Growth Standards 2006: `bmi_boys_0-to-2-years_zcores.xlsx`, `bmi_boys_2-to-5-years_zscores.xlsx` (và girls); WHO Growth Reference 2007: `bmi-boys-z-who-2007-exp.xlsx` (và girls). Tải 04/11/2026; cột Month, L, M, S, SD.
- Ghép: tháng 0–23 bảng 0–2 tuổi (đo nằm), 24–60 bảng 2–5 tuổi (đo đứng — tháng 24 có ở cả hai bảng, chọn bảng đo đứng), 61–84 bảng 2007. Kết quả `src/main/resources/health/who-bmi-for-age.csv` (170 dòng L, M, S), không sửa tay.
- Kiểm: tính lại SD từ L, M, S khớp cột SD của WHO (vd. bé trai 61 tháng +2SD 18,259; bé trai 24 tháng +2SD 18,9) — `WhoBmiForAgeTest`.

## Z-score và ngưỡng
- LMS: z = ((BMI/M)^L − 1)/(L·S). Ngoài ±3SD dùng cách giới hạn đuôi của WHO (khoảng SD2–SD3). Z làm tròn 2 chữ số rồi mới so ngưỡng (như phần mềm WHO), để mức khớp z hiển thị (review 04/11: z thô 2,004 hiện "2,00" mà xếp béo phì).
- Tuổi theo tháng tròn như bảng WHO theo tháng (thực địa); phần mềm WHO nội suy theo ngày. Sai lệch ≤ 0,04 SD từ 24 tháng, ~0,09 SD ở 6–13 tháng, lớn ở 0–1 tháng (trường không nhận trẻ tuổi này).
- Ngưỡng WHO: 0–5 tuổi: > +1SD nguy cơ thừa cân, > +2SD thừa cân, > +3SD béo phì, < −2SD gầy còm, < −3SD gầy còm nặng; 5–19 tuổi: > +1SD thừa cân, > +2SD béo phì, < −2SD gầy còm, < −3SD gầy còm nặng.

## Phương án đã loại
- Bảng `health_alerts` ghi khi lưu: lệch khi sửa số đo/ngày sinh, thêm migration; không cần vì tính khi đọc rẻ.
- Thư viện tính z-score ngoài: thêm dependency (phải hỏi), không cần cho 6 mức.
- Hard-code ngưỡng BMI cố định theo tuổi năm: sai với bảng theo tháng.
