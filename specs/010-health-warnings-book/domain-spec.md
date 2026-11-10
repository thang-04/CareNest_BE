# Domain spec — Cảnh báo sức khỏe ở sổ sức khỏe, mức BMI kỳ cân đo, bỏ phiếu theo dõi

Nguồn mới nhất: user 04/11/2026 — "đợt khám là phần có bác sĩ, có kết luận sức khỏe; kỳ cân đo có cảnh báo BMI; bỏ phiếu theo dõi sức khỏe; cảnh báo lưu vào sổ sức khỏe của trẻ, giảm việc cho GV; thiết kế cảnh báo ở sổ sức khỏe phù hợp". Chế độ tự chạy (CLAUDE.md, user chốt 23/10): agent chọn phương án khuyến nghị, ghi quyết định ở plan `docs/plans/active/2026-11-04-health-warnings-book.md` để user soát.

## 1. Domain Overview
Sổ sức khỏe của trẻ là nơi lưu cảnh báo sức khỏe. Đợt khám (bác sĩ) cho cảnh báo bằng kết luận; kỳ cân đo (GV cân, đo) cho cảnh báo bằng mức BMI theo tuổi chuẩn WHO. Không còn phiếu theo dõi: không ai phải trao đổi phụ huynh, ghi kết quả chuyên khoa, đóng phiếu.

## 2. Scope
Trong phạm vi: mức BMI theo tuổi cho số đo kỳ cân đo (0–84 tháng); cảnh báo hiện tại ở đầu sổ; cảnh báo ở danh sách lớp, chi tiết đợt/kỳ; bỏ API, màn, dữ liệu xử lý của phiếu theo dõi. Ngoài phạm vi: phân loại WHO cân nặng/tuổi, chiều cao/tuổi (hoãn — HLT-10), điều chỉnh tay mức BMI (P-40), thông báo, phụ huynh xem trên APP, xóa bảng phiếu cũ.

## 3. Actors & Roles
GV phụ trách lớp: nhập số đo kỳ cân đo, phiếu khám (HLT-09, HLT-08). HT/PHT trong phạm vi: xem sổ, đợt/kỳ. Phụ huynh: thấy kết quả đã công bố (HLT-11, PAR-03) khi APP nối API. Bác sĩ: ngoài hệ thống, kết luận được GV nhập.

## 4. Domain Concepts
Lần đo (kết quả), đợt khám, kỳ cân đo, kết luận bác sĩ (nhóm kết luận, mức theo dõi), mức BMI theo tuổi (z-score, 6 mức), cảnh báo hiện tại (lần khám gần nhất, lần cân đo gần nhất), trẻ cần chú ý của đợt/kỳ.

## 5. Domain Relationships
Trẻ 1–* lần đo; lần đo thuộc 1 đợt khám hoặc 1 kỳ cân đo. Lần đo đợt khám có phiếu khám → kết luận → mức theo dõi. Lần đo kỳ cân đo + ngày sinh + giới tính → mức BMI. Cảnh báo hiện tại suy từ các lần đo, không phải thực thể riêng.

## 6. User Requirements
- UR-1: GV lưu số đo kỳ cân đo thấy ngay mức BMI của trẻ.
- UR-2: Sổ sức khỏe hiện cảnh báo hiện tại (bác sĩ, BMI) và cảnh báo của từng lần đo; lọc được lần có cảnh báo.
- UR-3: HT/PHT thấy số trẻ theo mức BMI và danh sách trẻ cần chú ý của mỗi đợt/kỳ.
- UR-4: Không còn việc xử lý phiếu theo dõi; kết luận theo dõi sát chỉ là cảnh báo.

## 7. Business Rules
HLT-18 (mới), HLT-19 (mới), HLT-16 REMOVED; sửa HLT-10, HLT-12, HLT-17, G94-180/CHD-03, G94-180/HLT-08. Giữ HLT-03 (chuẩn WHO), HLT-11 (lưu là công bố), HLT-13 (sửa có lý do). P-40 mở.

## 8. Business Processes
Kỳ cân đo: [B] GV cân đo → [S] lưu là công bố → [S] xếp mức BMI khi đọc → hiện ở phản hồi lưu, sổ, danh sách lớp, chi tiết kỳ. Đợt khám: [B] bác sĩ khám, GV nhập phiếu → [S] kết luận → mức theo dõi → cảnh báo ở sổ (không mở phiếu). [B] Sửa số đo kèm lý do → [S] mức tính lại.

## 9. State / Lifecycle
Mức BMI và cảnh báo hiện tại không có trạng thái riêng, tính lại mỗi lần đọc. Phiếu khám: nháp → kết luận (giữ nguyên). Phiếu theo dõi: không còn vòng đời; dữ liệu cũ đóng băng.

## 10. Business Constraints
Tuổi tháng tròn tại ngày đo; 0–23 tháng bảng đo nằm, 24–60 tháng WHO 2006, 61–84 tháng WHO 2007; ngoài khoảng không xếp mức. Ngưỡng: < −3SD gầy còm nặng, < −2SD gầy còm, ≤ +1SD bình thường; đến 60 tháng: ≤ +2SD nguy cơ thừa cân, ≤ +3SD thừa cân, còn lại béo phì; từ 61 tháng: ≤ +2SD thừa cân, còn lại béo phì. Lần đo đợt khám không xếp mức BMI (bác sĩ kết luận dinh dưỡng trên phiếu).

## 11. Non-Functional Requirements
Không dependency mới, không migration. Tính cho cả danh sách đợt/kỳ một lần đọc (không truy vấn theo từng trẻ). Không dữ liệu trẻ thật trong test/log. Ngôn ngữ: "sàng lọc", không chẩn đoán (HLT-04).

## 12. Acceptance Criteria
AC-1..AC-5 trong plan quản trị (mức BMI, tính lại khi sửa, kết luận không mở phiếu, cảnh báo ở đợt/kỳ, bỏ phiếu theo dõi nhưng phiếu cũ mẫu FOLLOW_UP vẫn xem/sửa).

## 13. Domain Terminology
Cảnh báo sức khỏe; mức BMI theo tuổi; z-score; gầy còm (thinness/wasting); nguy cơ thừa cân (possible risk of overweight); trẻ cần chú ý. Xem `docs/context/GLOSSARY.md`.

## 14. Assumptions & Open Questions
- Giả định (agent, chờ user soát): có mức "nguy cơ thừa cân" cho trẻ đến 60 tháng; giữ bảng phiếu cũ thay vì xóa; mức theo dõi hiện tại chỉ từ lần khám gần nhất (bỏ xét phiếu đã đóng); `watchList` kỳ cân đo = trẻ BMI khác bình thường; không còn câu "vấn đề mới hay đã biết" (user bỏ 2026-11-04, phiếu cũ giữ câu trả lời); z-score làm tròn 2 chữ số trước khi xếp mức.
- Mở: P-40 (điều chỉnh tay mức BMI, kèm lý do).
- Constitution: clone này không có `.specify/memory/constitution.md`; áp nguyên tắc AGENTS.md.
