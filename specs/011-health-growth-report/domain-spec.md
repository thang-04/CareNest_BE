# Domain spec — Báo cáo tổng hợp tăng trưởng theo tháng (mẫu 5.1)

Nguồn mới nhất: user 04/11/2026 — HT (toàn bộ phân hiệu), PHT (phân hiệu được phân công) xem bảng tổng hợp theo mẫu "5.1" của trường; "Hãy thiết kế sao cho phù hợp". Quy tắc đếm, ánh xạ cột do người điều phối giao trong brief cùng ngày. Chế độ tự chạy (CLAUDE.md, user chốt 23/10): agent chọn phương án khuyến nghị, ghi ở plan `docs/plans/active/2026-11-04-health-growth-report.md`.

## 1. Domain Overview
Báo cáo tổng hợp tình trạng dinh dưỡng của trẻ theo tháng, theo lớp và nhóm tuổi, từ kết quả cân đo đã có của các đợt khám, kỳ cân đo trong tháng. Ba chỉ số chuẩn WHO: cân nặng theo tuổi, chiều cao theo tuổi, BMI theo tuổi. Không nhập thêm, không lưu bảng báo cáo.

## 2. Scope
Trong phạm vi: API báo cáo theo tháng (lọc điểm trường), phân loại W/A, H/A, BMI cho báo cáo; tổng lớp, MG, NT, điểm trường, toàn trường. Ngoài phạm vi: báo cáo 5.3 (hoãn), xuất Excel/in (FE), cảnh báo W/A, H/A ở sổ sức khỏe, so sánh nhiều tháng, phụ huynh xem.

## 3. Actors & Roles
HT: xem mọi điểm trường. PHT: xem điểm trường được phân công (AUTH-02). GV, phụ huynh, bếp: không xem. GV và bác sĩ tạo dữ liệu nguồn (số đo, phiếu khám) ở chức năng đã có.

## 4. Domain Concepts
Tháng báo cáo; đợt/kỳ của tháng (ngày bắt đầu trong tháng, chưa hủy); trẻ của lớp (danh sách lớp của đợt/kỳ); trẻ được cân đo; chỉ số W/A, H/A, BMI và mức; cấp học MG (mẫu giáo), NT (nhà trẻ); trẻ chưa xếp được mức.

## 5. Domain Relationships
Điểm trường 1–* đợt/kỳ; đợt/kỳ 1–* lớp (lớp của điểm trường trong năm học của ngày bắt đầu); lớp thuộc 0–1 nhóm tuổi; trẻ 0–* lần đo; lần đo + ngày sinh + giới tính ⇒ ba mức. Báo cáo suy ra, không là thực thể.

## 6. User Requirements
- UR-1: HT/PHT chọn tháng (và điểm trường) xem bảng 5.1 của từng điểm trường và toàn trường.
- UR-2: Điểm trường chưa có đợt/kỳ trong tháng vẫn hiện, ghi chưa có kỳ cân đo (điểm trường ngừng hoạt động thì ẩn, trừ khi chọn đúng nó).
- UR-3: Số liệu khớp màn đợt/kỳ: trẻ vắng, phiếu nháp không tính là đã cân đo.

## 7. Business Rules
HLT-20 (mới, PROPOSED); sửa HLT-10 (W/A, H/A hết hoãn, chỉ dùng cho HLT-20). Dùng HLT-03 (chuẩn WHO), HLT-18 (BMI, ngưỡng), HLT-01/HLT-08 (kỳ cân đo, đợt khám), HLT-17 (vắng khi đóng), AUTH-02.

## 8. Business Processes
[B] GV cân đo, bác sĩ khám trong đợt/kỳ → [S] lưu kết quả (đã có) → [B] HT/PHT mở báo cáo tháng → [S] chọn đợt/kỳ của tháng, hợp trẻ theo lớp, xếp mức, cộng tổng → [B] FE hiện/in bảng, tính tỷ lệ.

## 9. State / Lifecycle
Không trạng thái riêng; tính lại mỗi lần đọc. Đợt/kỳ đang diễn ra, đã đóng đều tính; đã hủy không tính.

## 10. Business Constraints
- Tuổi tháng tròn tại ngày đo, 0–84 tháng; < 24 tháng bảng đo nằm. Z làm tròn 2 chữ số trước khi so ngưỡng.
- W/A: < −3 SDD nhẹ cân nặng, < −2 SDD nhẹ cân (SDDNC), còn lại bình thường (PTBT); giới hạn đuôi ngoài ±3SD. H/A: < −3 thấp còi nặng, < −2 thấp còi (SDDTC), còn lại PTBT; công thức thường (L = 1).
- Cột BMI: BT = bình thường + nguy cơ thừa cân; SDD thể gầy còm = gầy còm + gầy còm nặng; thừa cân; béo phì (ngưỡng HLT-18).
- Được cân đo = số đo kỳ cân đo hoặc phiếu khám đã kết luận có chiều cao, cân nặng; nhiều lần ⇒ lần mới nhất.

## 11. Non-Functional Requirements
Không migration, không dependency. Đọc lần đo/vắng của mọi đợt/kỳ trong tháng một lần cho cả báo cáo. Phạm vi kiểm ở server. Không dữ liệu trẻ thật trong test/log; log chỉ id, số lượng. Ngôn ngữ "sàng lọc", không chẩn đoán (HLT-04).

## 12. Acceptance Criteria
AC-1..AC-5 trong plan quản trị.

## 13. Domain Terminology
PTBT (phát triển bình thường), SDDNC (suy dinh dưỡng nhẹ cân — W/A < −2SD), SDDTC (suy dinh dưỡng thấp còi — H/A < −2SD), SDD thể gầy còm (BMI < −2SD), MG (mẫu giáo), NT (nhà trẻ), TS (tổng số).

## 14. Assumptions & Open Questions
- Giả định (agent/người điều phối, chờ user soát): đếm theo danh sách lớp của đợt/kỳ; tổng toàn trường = cộng điểm trường; nhóm NT theo tuổi bắt đầu < 36 tháng; BMI tính cả cho phiếu khám trong báo cáo; trẻ không xếp được mức đếm riêng (`unclassified`); điểm trường ngừng hoạt động không có đợt/kỳ bị ẩn.
- Giả định cần trường xác nhận: cột "cân nặng/chiều cao" của mẫu 5.1 tính bằng BMI/tuổi WHO (theo brief); WHO còn chỉ số cân nặng theo chiều dài/chiều cao cho trẻ dưới 5 tuổi, trẻ sát ngưỡng có thể khác nhóm.
- Constitution: clone không có `.specify/memory/constitution.md`; áp nguyên tắc AGENTS.md.
