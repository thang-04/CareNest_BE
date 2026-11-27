# Luồng đầy đủ: cấu hình trường, điểm danh & suất ăn, đánh giá trẻ (cập nhật 26/11/2026)

Tài liệu mô tả luồng đang chạy sau đợt sửa nhánh `feature/G94-236-BE-FEAT-29-daily-attendance`, kèm các trường hợp, mã lỗi và các thay đổi của đợt này. Rule gốc ở `docs/business/BUSINESS_RULES.md`. Ma trận kịch bản kiểm thử: `docs/qa/attendance-meal-leave-evaluation-scenarios.md`. Rà soát lỗi: `docs/qa/audit-2026-11-26-three-flows.md`. Đối chiếu quy định Bộ: `docs/qa/moet-regulation-check-2026-11-26.md`.

Ký hiệu: HT hiệu trưởng, PHT phó hiệu trưởng, GV giáo viên, `[B]` việc ngoài hệ thống, `[S]` việc của hệ thống.

---

## 1. Cấu hình trường

### 1.1 Cấu trúc
- Năm học có ba trạng thái: PLANNED (sắp diễn ra), ACTIVE (đang hoạt động, chỉ một năm), CLOSED (chỉ đọc ở mọi module).
- Mỗi năm có điểm trường, phòng, nhóm tuổi, lớp và phân công riêng. Dữ liệu dùng chung chỉ xóa mềm khi năm cũ đã dùng.
- Sáu nhóm tuổi chuẩn theo Điều 15 Điều lệ trường mầm non (đã đối chiếu văn bản hợp nhất 09/2026/VBHN-BGDĐT):

| Nhóm | Khoảng tháng | Sĩ số tối đa |
|---|---|---|
| Nhóm trẻ 3–12 tháng | 3 đến dưới 13 | 15 |
| Nhóm trẻ 13–24 tháng | 13 đến dưới 25 | 20 |
| Nhóm trẻ 25–36 tháng | 25 đến dưới 36 | 25 |
| Mẫu giáo 3–4 tuổi | 36 đến dưới 48 | 25 |
| Mẫu giáo 4–5 tuổi | 48 đến dưới 60 | 30 |
| Mẫu giáo 5–6 tuổi | 60 đến dưới 72 | 35 |

- Sĩ số của lớp không vượt nhỏ hơn của (sĩ số nhóm tuổi, sức chứa phòng). Xếp lớp vượt sĩ số báo `2408`.
- Mỗi lớp có 2 đến 3 GV đứng lớp, đúng 1 GV phụ trách chính (SCH-04, AUTH-24). Năm đang hoạt động thiếu ⇒ `2328`, không chọn chính ⇒ `2329`, quá 3 ⇒ `2335`. Năm sắp tới được lưu tạm 0–3 GV. GV dạy thay không tính vào 3 người, bắt buộc có ngày kết thúc và chọn người được thay.

### 1.2 Thời gian trong ngày (HT cấu hình theo năm học, có lịch sử hiệu lực)
Thứ tự bắt buộc (PCK-04): mở đón trẻ ≤ bắt đầu sáng < kết thúc sáng < bắt đầu chiều < mốc tính đủ ngày ≤ bắt đầu trả trẻ < kết thúc trả trẻ ≤ kết thúc chiều. Giờ chốt điểm danh từ giờ mở đón trẻ và trước kết thúc sáng. Giờ đến muộn nhất buổi chiều sau giờ chốt, từ kết thúc sáng, trước bắt đầu trả trẻ.

Thay đổi trong đợt này:
- Giờ chốt đầu tiên của năm phải áp dụng từ ngày bắt đầu năm học (`4040`).
- Không đổi giờ chốt có hiệu lực hôm nay khi hôm nay đã qua giờ chốt hiện hành hoặc giờ chốt mới (`4039`). Giờ chốt mới chỉ áp dụng từ ngày mai.

### 1.3 Nhóm tuổi qua API cũ (không kèm năm học)
Thay đổi: không tạo nhóm có sĩ số trên 35; không xóa được nhóm chuẩn của Bộ.

### 1.4 Phân quyền động (AUTH-20)
HT cấp mã quyền theo mảng kèm phạm vi một điểm trường hoặc cả trường. Quyền liên quan: `ATTENDANCE_VIEW/RECORD/UNLOCK`, `ASSESSMENT_VIEW/RECORD/UNLOCK`, `MEAL_COUNT_CONFIRM`, `YEAR_PLACEMENT_MANAGE`, `STAFF_ASSIGNMENT_MANAGE`, `ROOM_MANAGE`, `REWARD_REVIEW`. Quyền cố định theo vai trò không thu hồi được. Không ai đổi quyền của chính mình.

### 1.5 Kích hoạt năm học và xếp lớp năm mới
Chỉ kích hoạt năm sắp diễn ra sớm nhất, sau khi qua các điều kiện (giờ chốt, phòng, GV, PHT, trẻ chưa xếp, vượt sĩ số). Một giao dịch: đóng năm cũ, kích hoạt năm mới, chụp snapshot, chuyển trẻ theo bản xếp lớp. Trẻ xếp Nghỉ học/Ra trường không vào năm mới.

---

## 2. Điểm danh, đơn nghỉ, mở lại, suất ăn

### 2.1 Dòng thời gian một ngày học
1. **Mở đón trẻ → giờ chốt (ví dụ 08:45):** GV ghi Có mặt / Vắng cho cả lớp. Có mặt có nút "Tham gia ăn" bật sẵn.
2. **Giờ chốt:** điểm danh khóa, chốt suất cả ngày. Trẻ đến sau giờ chốt buổi sáng: trường không nhận, không có suất (ATT-03).
3. **Sau giờ chốt:** hệ thống tổng hợp số suất theo lớp, cơ sở. PHT (hoặc HT) xem và xác nhận **chỉ sau giờ chốt** (trước giờ chốt báo `6078`).
4. **Đã xác nhận:** bếp làm theo số đã xác nhận. Từ đây không luồng nào đổi được suất (xem 2.6).
5. **Buổi chiều:** trẻ nghỉ sáng đến lớp → GV bấm "Đã đến" từ giờ bắt đầu nhận trẻ chiều đến giờ đến muộn nhất. Quá giờ đó chưa đến ⇒ hệ thống tự ghi Vắng cả ngày (sáng có phép, chiều không phép), báo GV và phụ huynh.
6. **Cuối ngày:** GV ghi trả trẻ (người đón, giờ). Trả trước mốc tính đủ ngày ⇒ tính 0,5 ngày, giữ suất đã chốt.

### 2.2 Ghi điểm danh trong ngày (trước giờ chốt)
| Trường hợp | Kết quả |
|---|---|
| Có mặt, ăn bình thường | Suất = mọi bữa của nhóm tuổi |
| Có mặt, gạt tắt "Tham gia ăn" | Không suất cả ngày |
| Vắng không phép | Không suất (nếu gửi kèm bữa ⇒ `6002`) |
| Gửi bữa ngoài nhóm tuổi | `6001` |
| Trẻ không báo ăn (CHD-13) | Không có nút ăn, không suất |
| GV chọn Vắng có phép khi chưa có đơn | `ATTENDANCE_EXCUSED_NOT_ALLOWED` |
| Không phải GV lớp, ngày chưa tới, ngày nghỉ, năm không hoạt động | Từ chối theo mã tương ứng |
| Danh sách trẻ thừa, trùng, sai lớp | `CHILD_LIST_MISMATCH` |
| Hai người lưu cùng lúc | Tuần tự hóa bằng khóa lớp |

### 2.3 Đơn xin nghỉ (phụ huynh)
Thông báo, không có bước duyệt. Trạng thái: gửi rồi hủy. Chỉ phụ huynh liên kết với trẻ gửi, hủy.

| Trường hợp | Kết quả |
|---|---|
| Nghỉ cả ngày, một hoặc nhiều ngày, trước giờ chốt | Ghi Vắng có phép, không suất, báo GV |
| Nghỉ buổi sáng (1 ngày) | Ghi "Nghỉ sáng", bữa xế theo công tắc ăn của đơn |
| Nghỉ buổi chiều (1 ngày) | GV điểm danh sáng bình thường, bữa xế tắt, chuyên cần 0,5 |
| Nửa buổi nhiều ngày | Nhận, áp buổi đó cho từng ngày học (chốt 27/11/2026) |
| Quá 30 ngày lịch | `LEAVE_TOO_LONG` |
| Ngày bắt đầu đã qua | `LEAVE_DATE_IN_PAST` |
| Ngoài năm học, qua hai năm học | `LEAVE_OUTSIDE_SCHOOL_YEAR` |
| Trùng đơn khác | `LEAVE_OVERLAP` |
| Trẻ sắp nghỉ học từ ngày D, đơn chạm tới D | `LEAVE_CHILD_LEAVING` |
| Ngày đã qua giờ chốt trong đơn nhiều ngày | Ngày đó bỏ qua, ngày sau áp dụng |
| Chỉ gồm ngày đã qua giờ chốt, không phải chiều hôm nay | `LEAVE_NO_SCHOOL_DAY` |
| Báo nghỉ **chiều hôm nay** sau giờ chốt, trước giờ bắt đầu chiều | Chỉ lưu thông báo, không đổi điểm danh và suất, báo GV |
| Như trên nhưng đã qua giờ bắt đầu chiều | `4032` |
| Như trên nhưng trẻ đã bị ghi vắng hôm nay | `4033` |
| Hủy đơn trước giờ chốt | Hoàn lại đúng phần đơn đã tạo; dữ liệu GV sửa sau đơn giữ nguyên |
| Hủy khi mọi ngày đã chốt | `LEAVE_CANNOT_CANCEL` |
| Hủy thông báo nghỉ chiều sau giờ chốt | Chỉ hủy thông báo |

Đơn tạo hoặc hủy qua `MealChangeGuard`: nếu sổ suất ngày đó đã xác nhận hoặc phiếu bếp đã ký thì bị chặn.

**Trẻ có đơn nghỉ cả ngày nhưng vẫn đến lớp (mới):** GV ghi Có mặt ngay, kể cả trước giờ chốt, trẻ có suất như bình thường, hệ thống báo phụ huynh, đơn vẫn lưu trong lịch sử. GV không đổi được thành Vắng không phép (`LEAVE_ATTENDANCE_LOCKED`), đổi lại "Nghỉ có phép" được. Đơn nghỉ sáng trước giờ chốt: GV chỉ đổi công tắc ăn bữa xế.

### 2.4 Mở lại điểm danh (ATT-08)
- HT hoặc PHT có quyền `ATTENDANCE_UNLOCK` đúng phạm vi mở lại lớp/ngày đã qua giờ chốt, bắt buộc có lý do, hạn mở không quá hết năm học. Chỉ lớp của năm đang hoạt động, lùi xa nhất tới ngày bắt đầu năm.
- Từ chối khi: không quyền, năm không hoạt động, ngày tương lai hoặc ngoài năm, ngày chưa qua giờ chốt, hạn không hợp lệ, đã có lượt mở còn hiệu lực. Thu hồi lượt đã hết hoặc đã thu hồi báo `UNLOCK_ALREADY_ENDED`.
- GV được phân công lớp (đang dạy chính, hoặc đã dạy chính đúng ngày được ghi, hoặc người dạy thay có đợt chứa ngày đó) ghi được và nhận thông báo (mới: không chỉ GV hiện tại).
- Khi ngày đã khóa cả đánh giá, **đánh giá cùng lớp–ngày được mở lại cùng hạn** (mới).
- Khi mở lại, GV được:
  - chọn Cả ngày / Chỉ sáng / Chỉ chiều cho trẻ Có mặt (không đổi suất; ngoài trường hợp này ⇒ `4038`);
  - sửa nhầm "vắng sáng không phép" bằng nút "Sửa: có mặt cả ngày" (`4037` nếu không đúng ca);
  - ghi Có mặt cho trẻ có đơn nghỉ cả ngày.
- Sửa suất khi mở lại: đổi Có mặt ↔ Vắng làm đổi suất chỉ được khi **sổ chưa xác nhận**. Đã xác nhận hoặc phiếu bếp đã ký ⇒ `6005` hoặc `INSPECTION_SIGNED`.

### 2.5 Sửa nhầm "Tham gia ăn" sau giờ chốt
Chỉ hôm nay, chỉ khi các bữa liên quan chưa xác nhận và phiếu bếp chưa ký. Ngày khác ⇒ `ATTENDANCE_TODAY_ONLY`. Trẻ vắng hoặc không báo ăn ⇒ `ABSENT_CHILD_NO_MEAL`. Trẻ nghỉ nửa buổi chỉ đổi bữa của buổi còn học.

### 2.6 Khóa suất sau xác nhận (ATT-07) — chốt 26/11/2026
Sổ đã xác nhận (hoặc phiếu bếp đã ký) khóa cứng. Không có bước "xác nhận lại". Mọi luồng chạm suất đều qua `MealChangeGuard`:

| Luồng | Sau xác nhận |
|---|---|
| Lưu điểm danh (kể cả ngày mở lại) | Chặn `6005` |
| Sửa "Tham gia ăn" | Chặn |
| Tạo hoặc hủy đơn nghỉ | Chặn |
| Đổi giờ chốt để mở lại ngày | Chặn `4039` |
| Phiếu bếp đã ký | Chặn `INSPECTION_SIGNED` |
| "Đã đến" chiều, sửa nhầm vắng sáng, trả trẻ, đóng buổi sáng tự động | Không đổi bữa; chỉ cập nhật số có mặt trên sổ chưa xác nhận |

Số liệu trên sổ chưa xác nhận được tính lại lúc PHT bấm xác nhận để không chốt số cũ khi điểm danh vừa lưu. Nút "Trả lại GV kiểm tra" chỉ hiện khi chưa có bữa nào xác nhận. Thiếu hoặc thừa suất sau xác nhận xử lý ngoài hệ thống (NUT-22).

### 2.7 Chuyển lớp, thôi học, ra trường
- Chuyển lớp: điểm danh theo lớp của trẻ ngày đó; bản ghi do đơn tạo trước khi chuyển chuyển sang lớp mới khi lưu.
- Thôi học có hẹn ngày: đơn nghỉ chạm tới ngày nghỉ bị từ chối. Ngày đã có dữ liệu thì chặn đặt ngày nghỉ. Phụ huynh mất quyền xem ngay (CHD-04, xem 3.5).
- **Chưa xử lý (backlog):** đơn nghỉ và bản ghi Vắng có phép đã tạo cho các ngày từ ngày nghỉ trở đi chưa tự dọn (A3).

---

## 3. Đánh giá trẻ

### 3.1 Đánh giá hằng ngày
- Danh sách trẻ **có mặt** của lớp trong ngày (trẻ vắng tự bỏ qua). Mặc định mọi trẻ "bình thường"; GV chỉ sửa trẻ khác thường. Bảy mặt: ăn, ngủ, cảm xúc, hành vi, tham gia, kiến thức–kỹ năng theo mục tiêu của kế hoạch ngày, sức khỏe.
- Mức Đạt / Cần cố gắng / Chưa đạt. Lớp chưa có kế hoạch ngày được duyệt ⇒ khóa phần chấm theo mục tiêu (`ASSESSMENT_NO_APPROVED_LESSON`).
- Trẻ vắng cả ngày: các mục tiêu ngày đó tính Chưa đạt (OBS-01). Trẻ vắng nửa ngày vẫn được đánh giá bình thường.
- Khóa hết ngày (OBS-12). Ngày đã khóa muốn sửa cần được mở lại (xem 3.3).

**Mới (OBS-17, OBS-33):** các dòng chưa ghi lúc mở trang được gửi kèm `createOnly`. Nếu cô khác đã ghi trẻ đó trong lúc đó thì BE bỏ qua dòng này, không đè ghi nhận thật bằng giá trị mặc định. Dòng cô đã sửa vẫn ghi đè như cũ.

### 3.2 Nhận xét tuần, chủ đề, học kỳ, cuối năm
Nháp (mẫu nền, tùy chọn AI) → GV sửa → GV xác nhận là xong; không có bước BGH duyệt. Phụ huynh nhận bản đã xác nhận. Việc theo kỳ chỉ GV chính ghi; người dạy thay chỉ xem. Nhận xét tháng và học kỳ cá nhân đã bỏ (OBS-06). AI luôn có mẫu nền dự phòng, không xếp hạng, không trao thưởng.

**Mới (OBS-17):** chữ GV nhập ở ghi chú chủ đề, học kỳ, cuối năm (kể cả nhận xét lĩnh vực) và các chỗ khác đều qua bộ lọc thuật ngữ chẩn đoán. Bộ lọc chuẩn hóa chữ (bỏ dấu, hạ chữ, "tự kỷ" và "tự kĩ", "autism", "adhd"…), nên gõ biến thể vẫn bị bắt. Câu mô tả hành vi quan sát được không bị chặn.

### 3.3 Mở lại đánh giá
HT hoặc PHT có `ASSESSMENT_UNLOCK` đúng phạm vi mở lại ngày đã khóa: ngày học trong quá khứ trong năm, hạn sau hiện tại, mỗi lớp/ngày một lượt còn hiệu lực. **Mới:** mở lại điểm danh tự mở lại đánh giá cùng lớp–ngày (cùng hạn) khi ngày đánh giá đã khóa; lượt đã có thì giữ. Nếu việc mở tự động lỗi, hệ thống ghi log lỗi để HT mở bằng tay.

### 3.4 Khen thưởng
Phiếu bé ngoan tuần: ≥ 3 cờ trong tuần, mỗi ngày tối đa 1 cờ; GV tự chọn trẻ nhận (hệ thống không gợi ý). 3 phiếu trong tháng là "xuất sắc". Cuối năm: GV đề xuất → PHT xét → HT duyệt.

### 3.5 Phụ huynh xem (mới, chốt 26/11/2026)
Phụ huynh chỉ xem trẻ liên kết với mình. **Trẻ đã thôi học hoặc ra trường thì phụ huynh mất quyền xem ngay** (đánh giá, nhận xét tuần, khen thưởng, điểm danh). Phụ huynh chỉ thấy bản đã xác nhận (chủ đề, học kỳ, cuối năm) và danh sách khen thưởng đã duyệt. Ngày đang sửa sau khi mở lại thì không hiện bản đang sửa.

### 3.6 Điểm danh sửa sau khi đã đánh giá
- Có mặt → Vắng: dữ liệu giữ, phụ huynh thấy ngày đó là vắng.
- Vắng → Có mặt khi ngày đánh giá đã khóa: đánh giá được mở lại cùng điểm danh (xem 3.3).

---

## 4. Danh sách thay đổi của đợt này

**BE**
- `MealChangeGuard` / `MealChangeGuardImpl` (meals): điểm danh và đơn nghỉ hỏi khóa suất qua interface, không dùng repository module khác (sửa ArchUnit).
- `MealCountServiceImpl`: chỉ xác nhận sau giờ chốt (`6078`); tính lại số sổ chưa xác nhận lúc xác nhận; bỏ qua sổ đã xác nhận khi điểm danh đổi.
- `AttendanceServiceImpl`: Có mặt cho trẻ có đơn nghỉ cả ngày; phát event cho mọi thay đổi điểm danh; "Đã đến" và sửa vắng sáng cũng phát event; đóng buổi sáng không vỡ khi một lớp lỗi.
- `LeaveRequestServiceImpl`: tạo hoặc hủy đơn qua `MealChangeGuard`.
- `AttendanceDayUnlockServiceImpl` + `AssessmentReopenOnAttendanceUnlockListener`: mở lại điểm danh tự mở lại đánh giá.
- `AttendanceCutoffServiceImpl`: `4039`, `4040`.
- `AgeGroupServiceImpl`: trần sĩ số 35 và không xóa nhóm chuẩn qua API cũ.
- `AttendanceDayRules` / `ObservationDayRules`: GV phân công lớp ghi được và được báo khi mở lại.
- `ParentAssessmentServiceImpl`, `WeeklyCommentServiceImpl`, `ParentAttendanceServiceImpl`: trẻ đã thôi học thì phụ huynh mất quyền xem.
- `DailyAssessmentServiceImpl` + `SaveDailyGeneralRequest.Entry.createOnly`: không đè ghi nhận của cô khác.
- `DiagnosticTerms` và ghi chú chủ đề, học kỳ, cuối năm.
- Mã lỗi mới: `4039`, `4040`, `6078`. Snapshot OpenAPI đã cập nhật.
- Test viết lại theo ATT-07 (MealCount, Listener, Stock, Concurrency, LeavePreview) và test mới (`DiagnosticTermsTest`, kịch bản đơn nghỉ cả ngày mà trẻ vẫn đến, xác nhận trước giờ chốt).

**FE**
- Trang điểm danh: đơn nghỉ cả ngày không khóa Có mặt; nhãn "Nghỉ có phép"; mock cùng quy tắc.
- Sổ suất ăn: ẩn "Trả lại GV kiểm tra" khi đã xác nhận.
- Đánh giá ngày gửi `createOnly` cho dòng chưa ghi.
- Cổng contract: khai pending `POST /nutrition/fresh-checks/dry-receipts` (BE dev tạm tắt endpoint này) và đồng bộ `be-api-routes.json`.

**Tài liệu rule:** ATT-04, ATT-07, ATT-08, NUT-02, NUT-03, NUT-04, ATT-15, OBS-32, OBS-36, flow `attendance.md`.

---

## 5. Đối chiếu quy định Bộ GD&ĐT

Nguồn đọc nguyên văn: văn bản hợp nhất 09/2026/VBHN-BGDĐT ngày 23/07/2026 (Thông tư 52/2020 sửa đổi bởi Thông tư 51/2026).

| Quy định | Tình trạng |
|---|---|
| Điều 15 nhóm trẻ và lớp mẫu giáo, khoảng tháng, sĩ số tối đa | Khớp |
| Điều 15.2 nhóm/lớp ghép khi dưới 50% sĩ số | Chưa hỗ trợ (để sau) |
| Điều 15.3 tối đa 2 trẻ khuyết tật hòa nhập | Chưa hỗ trợ (để sau) |
| Điều 15.4 đủ GV theo quy định | Hệ thống bắt 2–3 GV; định mức theo văn bản chuyên ngành chưa đối chiếu |
| Điều 20.1 kiểm tra sức khỏe tối thiểu 1 lần/năm | Khớp (HLT-08) |
| Điều 20.2 đánh giá dinh dưỡng hằng tháng với trẻ dưới 24 tháng | Cân đo không thuộc phạm vi này, bỏ qua |
| Điều 32 nhận trẻ từ 3 tháng đến 6 tuổi | Ranh giới khớp |
| Đánh giá trẻ theo ngày, chủ đề, cuối độ tuổi; không dùng xếp hạng | Khớp về luồng |
| 35 tuần/năm học, 5 ngày/tuần; định mức GV; cơ cấu bữa ăn | **Chưa đối chiếu được nguyên văn** |

---

## 6. Còn tồn tại (không sửa trong đợt này)

Chi tiết và vị trí file ở `docs/qa/audit-2026-11-26-three-flows.md`.
- Điểm danh: thôi học hoặc chuyển lớp không dọn đơn nghỉ đã tạo (A3); trả trẻ thiếu khóa lớp và đường sửa giờ trả (A6); job đóng buổi sáng chỉ xét hôm nay (A5, một phần).
- Đánh giá: nháp AI giữ khóa lớp lâu (B6); kiểm đầu ra AI tuần yếu hơn kiểm kỳ (B7); cờ khen thưởng phụ huynh thấy chưa lọc theo điểm danh (B8); trẻ chuyển lớp giữa tuần mất nhận xét tuần ở lớp cũ (B9); bản chốt chủ đề không đánh dấu khi dữ liệu nguồn đổi sau (B10); giáo án duyệt muộn làm ngày chưa chấm tính Chưa đạt (B11); gửi AI ngoài còn lọt tên bạn cùng lớp (B15).
- Cấu hình: kiểm sĩ số khi xếp lớp ở module hồ sơ trẻ chưa khóa (C3, **không thuộc phạm vi này**); tuổi ra trường theo lớp lớn nhất (C2, chờ xác nhận thuộc phạm vi nào); lịch sinh hoạt chưa có CHECK ở DB (C8); ngừng điểm trường chưa kiểm quyền ghim (C9).
- Nhóm 25–36 tháng chưa ghép nguồn Chương trình GDMN 24–36 (thuộc module kế hoạch giáo dục).
