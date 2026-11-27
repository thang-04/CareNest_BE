# Kịch bản: điểm danh, suất ăn, đơn nghỉ, mở lại điểm danh, đánh giá trẻ

Nguồn: code nhánh `feature/G94-236-BE-FEAT-29-daily-attendance` (commit `b06f1a27`), rule ATT-02a..ATT-15, NUT-02, PCK-04, OBS-12.
Cột **KQ**: OK = code đã xử lý đúng rule; GAP = chưa đủ/mâu thuẫn, xem mục "Lỗ hổng" cuối file.

## A. Điểm danh trong ngày (trước giờ chốt)

| # | Tình huống | Kỳ vọng | KQ |
|---|-----------|---------|----|
| A1 | GV lớp lưu Có mặt cả lớp | Bản ghi PRESENT, suất = mọi bữa nhóm tuổi, bắn `AttendanceChangedEvent` | OK |
| A2 | Trẻ Vắng không phép | Không có suất (6002 nếu gửi kèm bữa) | OK |
| A3 | Trẻ Có mặt, GV gạt tắt "Tham gia ăn" | Không suất cả ngày | OK |
| A4 | Trẻ không báo ăn (CHD-13) | Bỏ qua `meals` gửi lên, không suất | OK |
| A5 | Gửi bữa không thuộc nhóm tuổi | 6001 `MEAL_SESSION_NOT_SERVED` | OK |
| A6 | GV chọn Vắng có phép khi chưa có đơn | `ATTENDANCE_EXCUSED_NOT_ALLOWED` | OK |
| A7 | GV không phải GV lớp / người dạy thay đúng ngày | `CLASS_ACCESS_DENIED` | OK |
| A8 | Danh sách trẻ gửi lên thừa/trùng/không thuộc lớp | `CHILD_LIST_MISMATCH` | OK |
| A9 | Ngày nghỉ lễ/cuối tuần | `NOT_SCHOOL_DAY` | OK |
| A10 | Năm học chưa kích hoạt/đã kết thúc | `ATTENDANCE_YEAR_NOT_ACTIVE` (chỉ xem) | OK |
| A11 | Ngày khác hôm nay, chưa được mở lại | `ATTENDANCE_TODAY_ONLY` | OK |
| A12 | Trẻ chuyển lớp giữa năm | Điểm danh theo lớp trẻ học ngày đó; bản ghi đơn cũ `moveToClass` khi lưu | OK |
| A13 | Lưu lại không đổi gì | `changed = 0`, không event, không history | OK |
| A14 | Hai GV lưu cùng lúc | `lockForUpdate(classId)` tuần tự hóa | OK |

## B. Đơn xin nghỉ (phụ huynh)

| # | Tình huống | Kỳ vọng | KQ |
|---|-----------|---------|----|
| B1 | Nghỉ cả ngày, nhiều ngày, trước giờ chốt | Ghi EXCUSED các ngày học, không suất, báo GV từng ngày | OK |
| B2 | Nghỉ buổi sáng (1 ngày) | Nhãn "Nghỉ sáng (có phép)", Xế tính theo công tắc ăn của đơn | OK |
| B3 | Nghỉ buổi chiều (1 ngày) | GV điểm danh sáng bình thường, Xế tắt; nhóm tuổi không có Xế vẫn nhận, chuyên cần 0,5 | OK |
| B4 | Nửa buổi nhiều ngày (from ≠ to) | Nhận, áp từng ngày học (chốt 27/11/2026) | Chưa chạy |
| B5 | Quá `leaveMaxDays` | `LEAVE_TOO_LONG` | OK |
| B6 | Ngày bắt đầu ở quá khứ | `LEAVE_DATE_IN_PAST` | OK |
| B7 | Ngoài năm học | `LEAVE_OUTSIDE_SCHOOL_YEAR` | OK |
| B8 | Trùng đơn khác | `LEAVE_OVERLAP` (kiểm theo ngày thực áp) | OK |
| B9 | Không phải phụ huynh liên kết | `LEAVE_NOT_GUARDIAN` | OK |
| B10 | Trẻ sắp nghỉ học (leftAt) | `LEAVE_CHILD_LEAVING` | OK |
| B11 | Đơn gồm cả ngày đã chốt + ngày chưa chốt | Ngày đã chốt vào `skipped`, ngày chưa chốt áp | OK |
| B12 | Toàn bộ ngày đã chốt, không phải chiều hôm nay | `LEAVE_NO_SCHOOL_DAY` | OK |
| B13 | Báo nghỉ chiều hôm nay sau chốt, trước giờ bắt đầu chiều | `noticeOnly`, không đổi điểm danh/suất, báo GV | OK |
| B14 | Như B13 nhưng đã qua giờ bắt đầu chiều | `LEAVE_AFTERNOON_NOTICE_CLOSED` (4032) | OK |
| B15 | Như B13 nhưng trẻ đã bị ghi vắng hôm nay | `LEAVE_CHILD_ABSENT_TODAY` (4033) | OK |
| B16 | Hủy đơn trước chốt, bản ghi do đơn tạo | Xóa/bỏ bản ghi, event cho meals | OK |
| B17 | Hủy đơn trước chốt, GV đã ghi trước đó | Trả trạng thái + bữa cũ từ `leave_attendance_effects` | OK |
| B18 | Hủy đơn trước chốt, GV đã sửa sau đơn | Giữ dữ liệu GV, chỉ bỏ dấu nửa buổi | OK |
| B19 | Hủy đơn nhiều ngày, một phần ngày đã chốt | `cancelFrom` ngày chưa chốt đầu tiên; ngày đã chốt giữ | OK |
| B20 | Hủy khi mọi ngày đã chốt | `LEAVE_CANNOT_CANCEL` | OK |
| B21 | Hủy `noticeOnly` (sau chốt) | Chỉ hủy thông báo, báo GV, không đụng điểm danh | OK |
| B22 | Đơn tạo trước V196 (không có effect) | `revertLegacy` | OK |
| B23 | Xem trước đơn (preview) | Trả bữa hủy/còn ăn, `noticeOnly`, không ghi DB | OK |
| B24 | GV tạo đơn thay phụ huynh | Không có đường tạo (đúng khảo sát) | OK |
| B25 | Trẻ có đơn nghỉ cả ngày nhưng sáng vẫn tới, **trước giờ chốt**, phụ huynh không mở App | GV ghi Có mặt ngay, có suất, báo phụ huynh; Vắng không phép vẫn bị `LEAVE_ATTENDANCE_LOCKED`; đổi lại Vắng có phép được | OK (đã vá 26/11) |

## C. Giờ chốt, đến muộn, tự đóng buổi

| # | Tình huống | Kỳ vọng | KQ |
|---|-----------|---------|----|
| C1 | GV lưu sau giờ chốt, ngày chưa mở lại | `ATTENDANCE_LOCKED` | OK |
| C2 | Trẻ đến sau chốt buổi sáng | Trường không nhận, không suất (ATT-03) | OK |
| C3 | Trẻ vắng sáng có phép (đơn nghỉ sáng), đến chiều trong khung nhận chiều → "Đã đến" | PRESENT, giữ bữa Xế, không event | OK |
| C4 | Trẻ vắng sáng **không phép**, đến chiều trong khung → "Đã đến" | 0,5 có mặt + 0,5 vắng không phép, nhãn riêng, bữa giữ nguyên | OK |
| C5 | "Đã đến" trước giờ bắt đầu chiều / sau giờ đến muộn nhất / trẻ đã có mặt / sai lớp | 4036 `ATTENDANCE_ARRIVAL_NOT_ALLOWED` | OK |
| C6 | Quá giờ đến muộn nhất, trẻ nghỉ sáng chưa đến | Job `closeMorningLeaves` (5 phút/lần, T2–T6) khóa "Vắng cả ngày", báo GV + phụ huynh, Xế đã chốt vẫn tính | OK |
| C7 | Đổi giờ chốt giữa năm | Cutoff có hiệu lực theo ngày, ngày cũ dùng giờ cũ | OK |
| C8 | Trẻ đã giao trước mốc tính đủ ngày rồi GV lưu lại | Giữ 0,5 ngày (`pickedUpEarly`), bữa không đổi | OK |

## D. Mở lại điểm danh (ATT-08)

### D-1. Tạo / thu hồi lượt mở

| # | Tình huống | Kỳ vọng | KQ |
|---|-----------|---------|----|
| D1 | HT/PHT có `ATTENDANCE_UNLOCK` đúng phạm vi, ngày đã chốt, lý do + hạn hợp lệ | Tạo lượt mở, báo GV dạy chính + dạy thay đúng ngày | OK |
| D2 | Người không có quyền / ngoài phạm vi cơ sở | `CLASS_ACCESS_DENIED` / `UNLOCK_ACCESS_DENIED` | OK |
| D3 | Năm học không ACTIVE | `UNLOCK_YEAR_NOT_ACTIVE` | OK |
| D4 | Ngày tương lai, cuối tuần, ngoài năm học | `UNLOCK_DATE_OUT_OF_RANGE` | OK |
| D5 | Ngày chưa qua giờ chốt | `DAY_NOT_LOCKED_YET` | OK |
| D6 | Hạn ≤ hiện tại hoặc quá hết năm học | `UNLOCK_DEADLINE_INVALID` | OK |
| D7 | Đã có lượt mở còn hiệu lực cùng lớp/ngày | `UNLOCK_ALREADY_ACTIVE` | OK |
| D8 | Thu hồi lượt đang hiệu lực | Ghi người + giờ thu hồi, GV không lưu được nữa | OK |
| D9 | Thu hồi lượt đã hết hạn/đã thu hồi | `UNLOCK_ALREADY_ENDED` | OK |
| D10 | Lượt mở hết hạn trong lúc GV đang sửa | Lưu bị `ATTENDANCE_LOCKED` | OK |
| D11 | Mở lại điểm danh ngày đã khóa đánh giá | Tự mở lại đánh giá cùng lớp–ngày, cùng hạn (event `AttendanceDayUnlockedEvent`); đã có lượt mở thì giữ | OK (đã vá 26/11) |

### D-2. Sửa khi đang mở lại

| # | Tình huống | Kỳ vọng | KQ |
|---|-----------|---------|----|
| D12 | Đổi Cả ngày / Chỉ sáng / Chỉ chiều cho trẻ Có mặt | Chỉ đổi chuyên cần, **giữ nguyên bữa**, không event | OK |
| D13 | Gửi `presentPart` khi chưa mở lại, hoặc trẻ Vắng | `ATTENDANCE_PART_REQUIRES_UNLOCK` (4038) | OK |
| D14 | Chọn "Chỉ chiều" mà sáng không có đơn | Sáng ghi vắng không phép (`absentPartUnexcused`) | OK |
| D15 | Chọn "Chỉ chiều" mà sáng có đơn nghỉ sáng | Sáng vắng có phép, không ghi không phép | OK |
| D16 | Trẻ có đơn nghỉ cả ngày, vẫn đến → GV ghi Có mặt | Cho phép khi mở lại; đơn giữ trong lịch sử | OK |
| D17 | Trẻ có đơn nghỉ cả ngày → GV ghi Vắng không phép | `LEAVE_ATTENDANCE_LOCKED` | OK |
| D18 | Trẻ có đơn nghỉ sáng, mở lại, GV đổi trạng thái | Cho phép (khác lúc chưa mở lại) | OK |
| D19 | Trẻ vắng chưa có bản ghi → GV thêm Có mặt khi mở lại, `presentPart` có | Bản ghi mới **không có suất** (`List.of()`) | OK (cố ý, xem D24) |
| D20 | GV ghi nhầm "vắng sáng không phép", BGH mở lại, GV bấm "Sửa: có mặt cả ngày" | Xóa nhãn, history theo lượt mở, bữa giữ, không event | OK |
| D21 | D20 khi chưa mở lại / sai ca / đơn nghỉ sáng có phép | 4037 `ATTENDANCE_FULL_DAY_FIX_NOT_ALLOWED` | OK |
| D22 | Mở lại, GV đổi Có mặt → Vắng khi sổ suất **chưa** xác nhận | Bỏ suất, event, sổ tính lại | OK |
| D23 | Mở lại, đổi Vắng → Có mặt (không `presentPart`) khi sổ **chưa** xác nhận | Có suất, event, sổ tính lại | OK |
| D24 | Mở lại ngày cũ (không phải hôm nay), trẻ đã đến nhưng bản ghi chưa có suất | Chấp nhận không suất; không sửa được vì `correctMealParticipation` chỉ cho hôm nay | OK (hạn chế đã biết) |

### D-3. Sổ suất đã xác nhận / phiếu bếp đã ký

| # | Tình huống | Kỳ vọng theo rule | Hành vi code | KQ |
|---|-----------|-------------------|--------------|----|
| D25 | Sổ đã CONFIRMED, BGH mở lại, GV đổi Có mặt ↔ Vắng | Khóa cứng (ATT-07, chốt 26/11) | Chặn `MEAL_COUNT_ALREADY_CONFIRMED` | OK |
| D26 | Sổ CONFIRMED, GV chỉ đổi buổi (Cả ngày/Chỉ sáng/Chỉ chiều) | Không đổi suất, không cần xác nhận lại | Cho phép, không event | OK |
| D27 | Phiếu bếp M2/M3 đã ký | Khóa tuyệt đối | `MEAL_COUNT_INSPECTION_SIGNED` | OK |
| D28 | Sửa nhầm "Tham gia ăn" hôm nay sau chốt, sổ chưa xác nhận | `POST .../meal-participation` đổi suất, history, event | OK |
| D29 | D28 khi bữa liên quan đã xác nhận / phiếu đã ký | Từ chối, không đổi gì | OK |
| D30 | D28 cho ngày khác hôm nay | `ATTENDANCE_TODAY_ONLY` | OK |
| D31 | D28 cho trẻ Vắng / không báo ăn | `ABSENT_CHILD_NO_MEAL` | OK |
| D32 | D28 cho trẻ nghỉ nửa buổi | Chỉ các bữa buổi còn học (`absentPart.other()`) | OK |
| D33 | D28 trước giờ chốt | `ATTENDANCE_LOCKED` (dùng lưu điểm danh thường) | OK |
| D34 | Sổ suất bấm "Trả lại GV kiểm tra" | Chỉ khi chưa có bữa nào CONFIRMED; sau xác nhận nút ẩn | FE ẩn nút khi có bữa CONFIRMED | OK |

## E. Suất ăn ↔ điểm danh

| # | Tình huống | Kỳ vọng | KQ |
|---|-----------|---------|----|
| E1 | Suất = trẻ PRESENT × bữa nhóm tuổi, theo lớp/cơ sở | `MealCountAttendanceListener` → `markAttendanceChanged` | OK |
| E2 | PHT xác nhận khi còn lớp chưa điểm danh | Bị chặn (NUT-02) | OK |
| E3 | Điểm danh đổi sau chốt, sổ chưa xác nhận | Tính lại bình thường | OK |
| E4 | Không còn suất thay thế (NUT-23) | Dị ứng chỉ cảnh báo, `MealSubstituteLookup` đã bỏ | OK |
| E5 | Thiếu/thừa suất thực tế | Bếp báo PHT ngoài hệ thống (NUT-22) | Ngoài hệ thống |

## F. Đánh giá trẻ ↔ điểm danh

| # | Tình huống | Kỳ vọng | KQ |
|---|-----------|---------|----|
| F1 | Đánh giá ngày, trẻ PRESENT | Lưu được; trẻ vắng tự bỏ qua | OK |
| F2 | Đánh giá trẻ không Có mặt | `ASSESSMENT_CHILD_NOT_PRESENT` | OK |
| F3 | Ngày tương lai / không phải ngày học / ngoài năm | `ASSESSMENT_FUTURE_DATE` / `NOT_SCHOOL_DAY` / `DATE_OUT_OF_YEAR` | OK |
| F4 | Ngày đã khóa, chưa có lượt mở đánh giá | `ASSESSMENT_DAY_LOCKED` | OK |
| F5 | Lớp chưa có giáo án duyệt, chấm theo mục tiêu | `ASSESSMENT_NO_APPROVED_LESSON` | OK |
| F6 | Đánh giá xong rồi BGH mở lại điểm danh, đổi trẻ Có mặt → Vắng | Giữ dữ liệu, ẩn khỏi phụ huynh (`ABSENT`) | OK (`ParentAssessmentServiceImpl` đã ẩn ngày vắng) |
| F7 | Mở lại điểm danh, đổi Vắng → Có mặt, ngày đánh giá đã khóa | Đánh giá được mở lại cùng điểm danh | OK (đã vá 26/11) |
| F8 | Trẻ nghỉ nửa buổi / chuyên cần 0,5 | Đánh giá theo PRESENT; chuyên cần tính 0,5 (ATT-14) cho khen thưởng, chất lượng tháng | OK |
| F9 | Khen thưởng tuần: trẻ đi học ≥ 4 ngày, ≥ 3 cờ | Đọc chuyên cần (0,5 ngày tính theo ATT-14); đổi chuyên cần sau khi mở lại phải làm lại số liệu | Cần test chéo | GAP-2 |

## G. Trả trẻ ↔ điểm danh

| # | Tình huống | Kỳ vọng | KQ |
|---|-----------|---------|----|
| G1 | Trả trước mốc tính đủ ngày | 0,5 ngày, giữ suất đã chốt | OK |
| G2 | Trả từ mốc đủ ngày | 1 ngày | OK |
| G3 | GV lưu lại điểm danh sau khi trả sớm | Không mất 0,5 | OK |
| G4 | Trả trẻ sau giờ chốt | Không đổi suất | OK |

## Lỗ hổng cần quyết (trả lời Cần/PO)

- **GAP-1 (đã đóng 26/11, phương án a):** khóa cứng sau xác nhận; đã sửa ATT-07, NUT-02/03/04, flow attendance, ẩn nút "Trả lại GV" khi có bữa đã xác nhận.
- **GAP-2 (đã đóng 26/11):** mở lại điểm danh tự mở lại đánh giá cùng lớp–ngày; trẻ sửa thành Vắng thì đánh giá ẩn khỏi phụ huynh.
- **GAP-3 (đã đóng 26/11, phương án A):** trẻ có đơn nghỉ cả ngày mà đến lớp, GV ghi Có mặt ngay trước giờ chốt, báo phụ huynh.
- **Ghi chú:** trẻ Vắng → Có mặt trong ngày mở lại bằng `presentPart` không sinh suất (D19/D24); nếu nhà bếp đã nấu cho trẻ, chỉ sửa được bằng `meal-participation` trong ngày hôm nay và sổ chưa xác nhận.
