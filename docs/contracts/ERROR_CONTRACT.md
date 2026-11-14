# Response & Error Contract

EDU-53, 05/11/2026: `GOAL_SUB_DOMAIN_MIXED` 7038 (lĩnh vực có lĩnh vực con nhưng còn nhóm/mục tiêu không thuộc lĩnh vực con nào, kiểm khi gửi và ban hành), HTTP 400.

EDU-51/52, 05/11/2026: `GOAL_NAME_DUPLICATE` 7036 (trùng tên lĩnh vực con / nhóm kết quả / mục tiêu / nội dung trong cùng phạm vi, kiểm khi gửi và ban hành) và `THEME_DATES_REQUIRED` 7037 (chủ đề chưa có ngày, chỉ kiểm khi ban hành), đều HTTP 400.

EDU-47, 01/11/2026: preview chủ đề nhánh dùng `BRANCH_FILE_UNSUPPORTED` 7124 (file hỏng/không hỗ trợ/mã hóa), `BRANCH_FILE_NO_TABLE` 7125 (thiếu bảng Chủ đề/Chủ đề nhánh), `BRANCH_FILE_TOO_LARGE` 7126 (2 MB/2.000 dòng), đều HTTP 400. Giữ lỗi quyền/phân công, sai kế hoạch/phiên bản và tài liệu đã gửi theo contract hiện tại; preview không lưu. Chi tiết [contract feature008](../../specs/008-branch-plan-file-import/contracts/file-import.md).

EDU-46, 01/11/2026: `GOAL_FILE_UNSUPPORTED` (7033, HTTP 400) giữ mã/status, đổi lời thành "Không đọc được file. Chỉ nhận Word (.doc, .docx), Excel (.xlsx) hoặc CSV; file không được đặt mật khẩu" khi bổ sung Word .doc nhị phân. .doc hỏng/mã hóa trả 7033, không có bảng mục tiêu 7034, vượt 2 MB/2.000 dòng 7035. Web dùng errorCode, không so chuỗi; mock và hướng dẫn nhập mục tiêu đã đồng bộ.

> Status: **ACCEPTED, đã implement.** Bổ sung 27/10/2026 (Cần): mã lỗi nghiệp vụ tập trung trong `ApiCode` + trường `errorCode`. Nguồn chuẩn: `src/main/java/com/carenest/utils/ApiCode.java`, `ResponseJson.java`, `exception/GlobalException.java`, `exception/GlobalExceptionHandler.java`; test `src/test/java/com/carenest/exception/ResponseContractTest.java`, `src/test/java/com/carenest/utils/ApiCodeTest.java`; quy tắc: `docs/backend-coding-guide.md` mục 8, 11. File này chỉ tóm tắt cho FE/APP và agent.

Mọi response (thành công hay lỗi):

```json
{ "code": 409, "errorCode": 4007, "desc": "Điểm danh và báo ăn đã khóa sau 08:30", "data": null }
```

| Trường | Ý nghĩa |
| --- | --- |
| `code` | Từ `ApiCode`, **trùng HTTP status** |
| `errorCode` | Mã lỗi nghiệp vụ 4 số của `ApiCode`; `null` khi thành công. FE/APP phân biệt lỗi bằng trường này, không so `desc` |
| `desc` | Câu tiếng Việt hiển thị được cho người dùng (lỗi lấy từ `ApiCode`, có giá trị thay vào `{0}`) |
| `data` | Dữ liệu; luôn có mặt, `null` khi không có. Validation 400 (`errorCode` 9001): danh sách `{field, message}`. Danh sách: `PageResponse` (`items`, `page`, `size`, `totalElements`, `totalPages`) |

## Mã lỗi theo phân hệ

Mỗi lỗi là một dòng trong `ApiCode`: mã 4 số + HTTP status + câu tiếng Việt. Service ném `GlobalException.of(ApiCode.X, args...)` (bọc lỗi: `GlobalException.withCause(ApiCode.X, ex, args...)`), không tự viết câu lỗi. Lỗi mới thêm vào cuối dải của phân hệ; mã đã dùng không đổi số, không dùng lại. Danh sách đầy đủ: `ApiCode.java` (bản Word "CareNest_Danh_sach_ma_loi" của Cần).

Bổ sung 29/10/2026 (G94-180, khi gộp G94-236): tài khoản & phân quyền 1112–1127, phân công nhân sự 2308–2320, liên kết phụ huynh – trẻ 3004–3006 (thay các câu lỗi tiếng Anh cũ; bảng theo tình huống: `docs/contracts/AUTH_CONTRACT.md`); tiếp nhận trẻ 3008–3010 (bảng dưới; trước khi gộp `dev` 31/10 là 3007–3009 — 3007 nay là `GUARDIAN_ACCOUNT_CONFIRMATION_REQUIRED` của G94-60), SĐT tài khoản nhân sự ở form tiếp nhận 1130 `PHONE_OF_STAFF_ACCOUNT`. Số 11xx/23xx/30xx theo bảng của `dev` (G94-60 đánh lại). Cần thêm các mã này vào bản Word của Cần.

Bổ sung 31/10/2026 (G94-180, đợt khám và kỳ cân đo): sức khỏe 5009–5026 — đợt/kỳ không tìm thấy 5009, không quản lý được 5010, điểm trường đang có đợt/kỳ chưa đóng 5011, đã đóng/hủy 5012, có kết quả không hủy được 5013, đóng/mở lại sai trạng thái 5014–5015, đổi ngày làm kết quả ra ngoài 5016, số đo ngoài kỳ cân đo 5017–5018, trùng số đo 5019–5020 (5020 đổi câu 04/11/2026: "Ngày đo {0} trẻ đã có kết quả đo khác. Báo Hiệu trưởng hoặc Phó hiệu trưởng"), ~~ngày đo ngoài kỳ 5021~~ (bỏ 04/11/2026: ngày đo hệ thống ghi luôn trong kỳ; không dùng lại mã), lần đo không tìm thấy 5022, không phải GV lớp 5023, thiếu lý do sửa 5024, không xem được sổ sức khỏe 5025, ngày kết thúc trước ngày bắt đầu 5026. Cần thêm vào bản Word của Cần.

Bổ sung 31/10/2026 (G94-180, phiếu khám Phụ lục II và phiếu theo dõi): 5027–5039 — phiếu khám không tìm thấy hoặc ngoài phạm vi 5027, kỳ cân đo không dùng phiếu khám 5028, không phải GV lớp của trẻ 5029, đợt khám không còn diễn ra 5030, lưu nháp phiếu đã kết luận 5031, mẫu kết luận của phiếu đã đổi 5032, phiếu chưa đủ thông tin 5033 (400 kèm `data: [{ field, message }]`, khóa ô theo phiếu: `exam.vitals.temperatureC`...; từ 04/11/2026 ô `date` chỉ còn là trùng ngày khám với kết quả đo khác, có thể là lỗi duy nhất kể cả khi lưu nháp — client không có ô ngày nên hiện như lỗi chung), sửa phiếu khám qua API số đo 5034. Cần thêm vào bản Word của Cần. Bỏ 04/11/2026 (phiếu theo dõi HLT-16 đã bỏ): 5035–5039 (phiếu theo dõi không tìm thấy, không phải GV lớp, phiếu đã đóng, chưa trao đổi phụ huynh, ảnh không tìm thấy) — không dùng lại mã.

Bổ sung 31/10/2026 (G94-180, đón trẻ, ảnh phụ huynh, thông báo): ảnh chân dung sai loại/quá 2 MB 1131, chưa có ảnh hoặc ngoài phạm vi 1132; thông báo không tìm thấy (hoặc của người khác) 1201; đón trẻ 4101–4107 — trẻ không tìm thấy hoặc ngoài phạm vi 4101, không phải GV lớp 4102, lớp không thuộc năm học đang hoạt động 4103, ngày ngoài khoảng 4104, trẻ chưa điểm danh có mặt 4105, trẻ vắng 4106, đã trả hôm nay 4107. Lỗi theo ô của form trả trẻ, đăng ký thiết bị dùng 9001 `INVALID_REQUEST_DATA` (`verification`, `guardianPosition`, `handoverTime`, `note`, `token`, `platform`). Cần thêm vào bản Word của Cần.

Bổ sung 10/11/2026 (G94-180, yêu cầu đổi SĐT phụ huynh — AUTH-21): 1136–1140 (bảng dưới). Trước khi gộp `dev` 10/11/2026 nhánh dùng 1133–1137; `dev` đã dùng 1133–1135 cho đổi vai trò, sửa hồ sơ nhân sự (AUTH-25) nên mã của nhánh dời sang 1136–1140. Gộp `dev` 11/11/2026: `dev` dùng 1140–1142 cho chữ ký (SIG-01) nên `USERNAME_CHANGE_NOT_ALLOWED` dời 1140 ⇒ 1143.

Bổ sung 10/11/2026 (G94-180, hồ sơ sức khỏe đầy đủ): bản giải thích xu hướng sức khỏe không tìm thấy 5040 (404). Dùng lại 8001/8002 cho AI giải thích xu hướng, 5025 cho nhân sự ngoài phạm vi, 9001 + ô cho sửa khai báo sức khỏe (bảng dưới). Cần thêm vào bản Word của Cần.

Lỗi theo ô do service kiểm (C-5, từ 29/10/2026): service ném `GlobalException.withFieldErrors(ApiCode.X, [{field, message}])` ⇒ `data` là danh sách như validation, `field` theo đường dẫn ô FE (`guardians.0.phone`, `profile.address.ward`, `declaration.medicalHistory.tbContactDate`; không tiền tố `child.`). Dùng cho 400 `INVALID_REQUEST_DATA` (9001) và 409 có ô (`GUARDIAN_IDENTITY_CONFLICT` 3008). Lỗi khác `data = null` như trước.

| Dải | Phân hệ |
| --- | --- |
| 10xx | Xác thực |
| 11xx | Tài khoản & phân quyền |
| 20xx | Năm học |
| 21xx | Điểm trường & phòng |
| 22xx | Nhóm tuổi & lớp |
| 23xx | Phân công giáo viên / tổ trưởng |
| 24xx | Xếp lớp & chuyển năm |
| 30xx | Hồ sơ trẻ |
| 40xx | Điểm danh |
| 50xx | Sức khỏe |
| 60xx | Dinh dưỡng (báo ăn & sổ báo ăn) |
| 70xx–72xx | Kế hoạch giáo dục (kế hoạch năm, tài liệu lớp, cấu hình) |
| 73xx | Đánh giá trẻ (đánh giá hằng ngày, mở lại ngày, tổng quan lớp) |
| 80xx | AI & tệp |
| 90xx | Chung & hệ thống (validation 9001, sai định dạng 9002, không tìm thấy 9009, lỗi hệ thống 9010, ghi đồng thời bị người khác cập nhật trước 9012 → HTTP 409…) |

Mã chung không có `errorCode` (`SUCCESSFUL 200`, `CREATED 201`, `BAD_REQUEST`, `UNAUTHORIZED`, `NOT_FOUND`, `CONFLICT`, `PRECONDITION_REQUIRED`, `PAYLOAD_TOO_LARGE`, `SERVICE_UNAVAILABLE`, `GATEWAY_TIMEOUT`) chỉ dùng cho response thành công hoặc chỗ chưa gắn mã nghiệp vụ.

ADR-0013: `PASSWORD_CHANGE_REQUIRED` (1010, HTTP 428) cho Web gateway: mật khẩu tạm đã xác minh cần đổi lần đầu; `data` chỉ chứa challenge opaque và hạn dùng, không có access/refresh token. FE xử lý 428 bằng màn hình đổi mật khẩu; Mobile PKCE không dùng endpoint này.

## Dùng theo tình huống nghiệp vụ

Học kỳ năm học (Cấu hình thời gian, 30/10/2026; đổi thành 4 ngày cùng ngày): `PUT /school-years/{id}/semester` nhận `semester1Start`, `semester1End`, `semester2Start`, `semester2End`, do Hiệu trưởng chọn. Thiếu ngày nào trả `SEMESTER_DATE_REQUIRED` 2026; ngày nằm ngoài năm học trả `SEMESTER_OUT_OF_YEAR` 2021 (thay `SEMESTER1_END_OUT_OF_RANGE`); học kỳ có ngày bắt đầu sau ngày kết thúc trả `SEMESTER_START_AFTER_END` 2024; HK II bắt đầu không sau ngày kết thúc HK I trả `SEMESTER_OVERLAP` 2025. Tất cả HTTP 400, kiểm tra theo đúng thứ tự trên. Đổi ngày năm học làm học kỳ đã đặt nằm ngoài năm cũng trả 2021. Năm đã kết thúc trả `SCHOOL_YEAR_CLOSED` 2003. FE gắn 2021/2026 vào ô ngày được nêu trong thông báo, 2024 vào ngày bắt đầu của học kỳ đó, 2025 vào `semester2Start`.

Kích hoạt và xếp loại lớp theo năm (13/11/2026, CFG-YR-05, CFG-YR-01): kích hoạt năm học trước ngày mở chuẩn bị trả `ACTIVATION_BEFORE_PREP` 2039, HTTP 409, câu "Năm học chưa tới ngày mở chuẩn bị, chỉ kích hoạt được từ ngày dd/MM/yyyy" (kiểm sau `ACTIVATION_TOO_EARLY` 2015); bảng điều kiện kích hoạt thêm dòng chặn "Chưa tới ngày mở chuẩn bị …" và `openFrom` lấy ngày muộn hơn giữa 7 ngày trước ngày bắt đầu và ngày mở chuẩn bị. Xếp loại lớp của năm sắp tới (PLANNED) nay cũng trả `ASSESSMENT_YEAR_NOT_ACTIVE` 7304, HTTP 409, như năm đã kết thúc; `canEdit` chỉ true khi năm đang hoạt động.

Thiết lập năm học (30/10/2026): `POST /school-years/{id}/copy {sourceYearId, parts}` chép lớp / GV / tổ trưởng / giờ chốt / học kỳ từ năm khác. `parts` rỗng trả `YEAR_COPY_PARTS_REQUIRED` 2023, HTTP 400; năm đích không ở trạng thái chưa bắt đầu trả `YEAR_COPY_TARGET_NOT_PLANNED` 2022, HTTP 409; năm nguồn trùng năm đích trả `COPY_SOURCE_YEAR_INVALID` 2215; thiếu `sourceYearId` trả 400 validation. Lỗi từng dòng (GV đã khóa, không còn ở điểm trường, phòng không dùng được, năm nguồn chưa có giờ chốt/học kỳ...) KHÔNG làm hỏng cả yêu cầu: dòng đó nằm trong `data.skipped[{part, name, reason}]`, FE hiện đỏ ở bước tương ứng. Dữ liệu năm đích đã có (lớp trùng tên, lớp đã có GV, nhóm tuổi đã có tổ trưởng, giờ chốt, học kỳ) giữ nguyên.

Báo cáo chất lượng tháng (sổ chỉ đạo chuyên môn 4.1 + 4.2, 30/10/2026): `GET /assessment/quality-report` trả `QUALITY_REPORT_FORBIDDEN` 7324 (403) khi không phải HT/PHT, `QUALITY_REPORT_MONTH_OUT_OF_YEAR` 7325 (400) khi tháng ngoài năm học, `QUALITY_REPORT_FUTURE_MONTH` 7326 (400) khi tháng sau tháng hiện tại; campus ngoài phạm vi trả `CAMPUS_ACCESS_DENIED` 2102; `month` sai dạng yyyy-MM trả lỗi định dạng 9002. FE gắn 7325/7326 vào ô `month`.

Đơn nghỉ phụ huynh (31/10/2026, ATT-04/05/05b): `/leave-requests` trả `LEAVE_NOT_GUARDIAN` 4018 (403) khi không phải phụ huynh đang liên kết với trẻ, `LEAVE_DATE_RANGE_INVALID` 4019 (400), `LEAVE_DATE_IN_PAST` 4020 (400), `LEAVE_NO_SCHOOL_DAY` 4021 (400) khi không còn ngày học trước giờ chốt, `LEAVE_OVERLAP` 4022 (409) khi trùng đơn còn hiệu lực, `LEAVE_NOT_FOUND` 4023 (404), `LEAVE_CANNOT_CANCEL` 4024 (409) khi mọi ngày còn lại đã qua giờ chốt, `LEAVE_CHILD_NO_CLASS` 4025 (400) khi trẻ chưa có lớp năm đang hoạt động. GV/PHT/HT xem ngoài phạm vi: `CLASS_ACCESS_DENIED` / `CAMPUS_ACCESS_DENIED` (403).

Phiếu bé ngoan (30/10/2026): `/assessment/behaviour-tickets` trả `TICKET_FORBIDDEN` 7327 (403) khi phát phiếu cho lớp không phải lớp mình dạy, `TICKET_VIEW_FORBIDDEN` 7328 (403) khi xem lớp/điểm trường ngoài phạm vi, `TICKET_PERIOD_INVALID` 7329 (400) khi kỳ không phải thứ Hai đầu tuần / ngày 1 đầu tháng hoặc ngoài năm học, `TICKET_PERIOD_NOT_ENDED` 7330 (400) khi kỳ chưa kết thúc, `TICKET_NO_CHILDREN` 7331 (400) khi không chọn trẻ, `TICKET_TOO_MANY_CHILDREN` 7332 (400) khi chọn quá 100 trẻ hoặc trùng trẻ, `TICKET_CHILD_NOT_IN_CLASS` 7333 (400) khi trẻ không thuộc lớp trong kỳ, `TICKET_ALREADY_ISSUED` 7334 (409) khi trẻ đã có phiếu kỳ đó, `TICKET_NOT_ELIGIBLE` 7335 (400) khi trẻ chưa đủ điều kiện (phiếu tuần: ≥ 4 ngày có mặt và ≥ 3 Cờ; phiếu tháng: ≥ 3 phiếu tuần có thứ Hai trong tháng). Năm học chưa kích hoạt trả `ASSESSMENT_YEAR_NOT_ACTIVE` 7304. FE hiện các lỗi này ở thông báo trên bảng (không gắn ô nhập).

Đánh giá tháng / cuối năm (30/10/2026): `/assessment/evaluations` trả `EVALUATION_NOT_FOUND` 7336 (404), `EVALUATION_VIEW_FORBIDDEN` 7337 (403) khi xem lớp ngoài phạm vi hoặc PHT/HT mở đánh giá chưa xác nhận, `EVALUATION_EDIT_FORBIDDEN` 7338 (403) khi sửa / xác nhận / tạo lại bản nháp mà không phải GV đang phụ trách lớp, `EVALUATION_ALREADY_CONFIRMED` 7339 (409), `EVALUATION_CONTENT_REQUIRED` 7340, `EVALUATION_CONTENT_TOO_SHORT` 7341 (dưới 30 ký tự), `EVALUATION_CONTENT_TOO_LONG` 7342 (trên 3000 ký tự), `EVALUATION_DIAGNOSTIC_TERM` 7343 (từ chẩn đoán, cùng danh sách với đánh giá hằng ngày), `EVALUATION_MONTH_INVALID` 7344 khi `month` không phải ngày 1 hoặc ngoài năm học / sau tháng hiện tại; đều HTTP 400. Năm học không hoạt động trả `ASSESSMENT_YEAR_NOT_ACTIVE` 7304. FE gắn 7340–7343 vào ô `content`. Trẻ 5 tuổi hoàn thành chương trình: `PUT /assessment/evaluations/{id}/program-completion` trả `EVALUATION_PROGRAM_NOT_APPLICABLE` 7345 (400) khi không phải đánh giá cuối năm của lớp nhóm tuổi từ 60 tháng, `EVALUATION_PROGRAM_REQUIRED` 7346 (400) khi `completed` null hoặc xác nhận đánh giá cuối năm lớp 5–6 tuổi chưa chọn Hoàn thành / Chưa hoàn thành; `GET /assessment/program-completion` trả `PROGRAM_REPORT_FORBIDDEN` 7347 (403) khi không phải HT/PHT, campus ngoài phạm vi trả 2102. FE gắn 7345/7346 vào ô `programCompleted`.

Hồ sơ / tiến độ phát triển (30/10/2026): `GET /assessment/children` trả `CLASS_ACCESS_DENIED` 2201 (403) khi `classId` ngoài phạm vi. `GET /assessment/children/{childId}/development` trả `INVALID_DATE_RANGE` 9005 (400) khi thiếu `start` / `end`, `start` sau `end` hoặc `start` sau hôm nay; `DATE_RANGE_TOO_LONG` 9006 (400) khi khoảng quá 400 ngày; `DEVELOPMENT_CHILD_NO_CLASS` 7348 (404) khi trẻ không thuộc lớp nào ở đầu / cuối khoảng; `CHILD_ACCESS_DENIED` 3001 (403) khi không phải GV lớp của trẻ hoặc PHT/HT điểm trường của lớp.

Đề xuất khen thưởng theo danh sách lớp (30/10/2026), `/assessment/reward-lists`: `REWARD_LIST_NOT_FOUND` 7349 (404), `REWARD_VIEW_FORBIDDEN` 7350 (403) khi lớp ngoài phạm vi, `REWARD_EDIT_FORBIDDEN` 7351 (403) khi không phải GV đang phụ trách lớp, `REWARD_STATUS_CHANGED` 7352 (409) khi danh sách đã gửi / đã chuyển bước, `REWARD_LIST_EMPTY` 7353 khi gửi mà chưa trẻ nào có danh hiệu, `REWARD_CHILD_NOT_IN_CLASS` 7354, `REWARD_CHILD_DUPLICATE` 7355, `REWARD_TITLE_INVALID` 7356 (danh hiệu ngoài danh sách), `REWARD_PROGRAM_TITLE_INVALID` 7357 (danh hiệu Hoàn thành chương trình khi đánh giá cuối năm chưa đánh dấu hoàn thành), `REWARD_YEAR_END_REQUIRED` 7358 (trẻ chưa có đánh giá cuối năm đã xác nhận), `REWARD_NOTE_TOO_LONG` 7359 (trên 300 ký tự), `REWARD_NOTE_DIAGNOSTIC` 7360, `REWARD_DECISION_INVALID` 7361, `REWARD_COMMENT_REQUIRED` 7362 (trả lại không nêu lý do), `REWARD_COMMENT_TOO_LONG` 7363 (trên 500 ký tự); các lỗi nhập là HTTP 400. `REWARD_REVIEW_FORBIDDEN` 7364 (403) khi không phải PHT điểm trường / HT. Năm học không hoạt động trả 7304. Lỗi 7354–7360 kèm tên trẻ trong thông báo, FE hiện đỏ ngay trên bảng.

Nhận xét chung lớp (OBS-22, 29/10/2026): `CLASS_COMMENT_TOO_LONG` 7322 và `CLASS_COMMENT_DIAGNOSTIC_TERM` 7323, HTTP 400, FE gắn lỗi vào `classComment`. Các lỗi ngày/khóa/quyền của đánh giá ngày tiếp tục dùng dải 7301–7321. PUT General cho phép `entries: []` khi gửi `classComment`; thiếu/null giữ nội dung cũ, chuỗi rỗng xóa nội dung.

Nhận xét tuần theo trẻ (OBS-29, 13/11/2026), `/assessment/weekly-comments`: `WEEKLY_COMMENT_WEEK_INVALID` 7365 (400) khi tuần không có ngày nào trong năm học của lớp, `WEEKLY_COMMENT_FUTURE_WEEK` 7366 (400) khi tuần chưa bắt đầu, `WEEKLY_COMMENT_CHILD_NOT_IN_CLASS` 7368 (400) khi trẻ không thuộc lớp trong tuần, `WEEKLY_COMMENT_NOTHING_TO_SEND` 7399 (400) khi gửi cả lớp mà chưa bản nào có nội dung; `WEEKLY_COMMENT_WEEK_NOT_ENDED` 7398 (409) khi viết nháp / lưu / gửi trước ngày học cuối của tuần ("Tuần này chưa kết thúc. Nhận xét tuần mở từ ngày …"); `GET /children/{childId}/weekly-comments` trả `WEEKLY_COMMENT_PARENT_FORBIDDEN` 7369 (403) khi không phải phụ huynh đã liên kết với trẻ. Nội dung dùng lại `EVALUATION_CONTENT_REQUIRED` 7340 (gửi mà trống), `EVALUATION_CONTENT_TOO_LONG` 7342 (trên 2000 ký tự), `EVALUATION_DIAGNOSTIC_TERM` 7343; không phải GV đang dạy lớp trả 7338, xem lớp ngoài phạm vi 7337, năm học không hoạt động 7304. FE gắn 7340/7342/7343 vào ô `content`. Hạn và mở lại (14/11/2026): `WEEKLY_COMMENT_LOCKED` 7400 (409) khi viết nháp / lưu / gửi sau hạn ("Nhận xét tuần đã khóa từ {ngày}. Liên hệ PHT hoặc Hiệu trưởng để mở lại."). Mở lại lớp – tuần `POST /assessment/weekly-comments/unlocks`: `WEEKLY_COMMENT_NOT_LOCKED_YET` 7401 (409) khi tuần chưa khóa, `WEEKLY_COMMENT_UNLOCK_ALREADY_ACTIVE` 7402 (409) khi lớp – tuần đang có lần mở còn hiệu lực, `WEEKLY_COMMENT_UNLOCK_NOT_FOUND` 7403 (404) khi kết thúc sớm lần mở không có; dùng lại như mở lại ngày đánh giá `ASSESSMENT_UNLOCK_FORBIDDEN` 7320, `ASSESSMENT_UNLOCK_YEAR_NOT_ACTIVE` 7313, `ASSESSMENT_UNLOCK_DEADLINE_INVALID` (hạn "Mở đến" không sau hiện tại hoặc quá hết năm học; đã qua ngày kết thúc mà năm chưa đóng thì không giới hạn), `ASSESSMENT_UNLOCK_ALREADY_ENDED` 7318; lý do trống / quá 300 ký tự trả 9002 (validation).

Hạn, khóa, mở lại đánh giá cuối chủ đề / học kỳ / cuối năm (OBS-30, 14/11/2026): lưu / xác nhận sau hạn trả `EVALUATION_LOCKED` 7404 (409, "{loại} đã khóa từ ngày {ngày}. Liên hệ PHT hoặc Hiệu trưởng để mở lại."). `POST /assessment/evaluation-unlocks`: `EVALUATION_NOT_LOCKED_YET` 7405 (409) khi kỳ chưa khóa, `EVALUATION_UNLOCK_ALREADY_ACTIVE` 7406 (409) khi lớp – kỳ đang có lần mở còn hiệu lực, `EVALUATION_PERIOD_NOT_FOUND` 7408 (400) khi `periodKey` không phải kỳ của lớp; `POST .../{id}/revoke`: `EVALUATION_UNLOCK_NOT_FOUND` 7407 (404); dùng lại 7320, 7313, `ASSESSMENT_UNLOCK_DEADLINE_INVALID`, 7318 như mở lại ngày đánh giá; lý do trống / quá 300 ký tự trả 9002.

| Tình huống | ApiCode (errorCode) | Rule |
| --- | --- | --- |
| Ngoài phạm vi campus/lớp/trẻ | `CAMPUS_ACCESS_DENIED` / `CLASS_ACCESS_DENIED` / `CHILD_ACCESS_DENIED` (403) | AUTH-07 |
| Năm học đã kết thúc | `SCHOOL_YEAR_CLOSED` (409) | CFG-YR-01 |
| Điểm danh sau giờ chốt | `ATTENDANCE_LOCKED` (409) | ATT-08 |
| Sổ báo ăn đã xác nhận | `MEAL_COUNT_ALREADY_CONFIRMED` (409) | NUT-03 |
| Tiếp nhận: form sai, lớp/điểm trường không nhận được (`classId`, `campusId`) | `INVALID_REQUEST_DATA` (9001) + ô | CHD-02, CHD-10 |
| Tiếp nhận trùng trẻ đang học / chờ xếp lớp | `CHILD_DUPLICATE` (3008, 409, không có ô) | CHD-04 |
| CCCD / SĐT phụ huynh đã thuộc người khác | `GUARDIAN_IDENTITY_CONFLICT` (3009, 409) + ô `guardians.{i}.idNumber` / `phone` | AUTH-18 |
| Mở lại hồ sơ trẻ chưa nghỉ học | `CHILD_NOT_LEFT` (3010, 409) | CHD-03 |
| Sửa hồ sơ / cho nghỉ học / xếp lớp trẻ đã nghỉ | `CHILD_ALREADY_LEFT` (3011, 409) | G94-180/CHD-03, CHD-10 |
| Xếp / chuyển lớp: thiếu lớp, cùng lớp, chưa khai báo sức khỏe, lớp ngừng hoạt động / khác năm đang hoạt động / ngoài điểm trường được giao / đủ sĩ số (`classId`); đang có lớp mà thiếu lý do, lý do > 300 (`reason`) | `INVALID_REQUEST_DATA` (9001) + ô | CHD-10, G94-180/CHD-02 |
| Danh sách trẻ: lọc lớp / điểm trường ngoài phạm vi; phụ huynh gọi | `CLASS_ACCESS_DENIED` (2201, 403) / `CAMPUS_ACCESS_DENIED` (2102, 403); `FORBIDDEN` (1101, 403) | AUTH-08, AUTH-04 |
| Cho nghỉ học: ngày nghỉ, lý do sai (`leftAt`, `reason`); Sửa hồ sơ: form sai, ngày sinh sau ngày tiếp nhận, bỏ phụ huynh đang liên kết tài khoản (`guardians`), đổi cùng lúc CCCD và SĐT, thiếu quyền quản lý tài khoản khi sửa họ tên/email/CCCD phụ huynh có tài khoản | `INVALID_REQUEST_DATA` (9001) + ô | G94-180/CHD-03, AUTH-21 |
| Sửa hồ sơ: SĐT mới của phụ huynh có tài khoản đã là tên đăng nhập tài khoản khác | `GUARDIAN_IDENTITY_CONFLICT` (3009, 409) + ô `guardians.{i}.phone` | AUTH-18, G94-180/AUTH-21 |
| Yêu cầu đổi SĐT không tồn tại / đã xử lý / ngoài điểm trường | `PHONE_CHANGE_REQUEST_NOT_FOUND` (1136, 404) / `PHONE_CHANGE_REQUEST_NOT_PENDING` (1137, 409) / `CHILD_GUARDIAN_MANAGE_DENIED` (3006, 403). ~~`PHONE_CHANGE_SELF_DECISION` (1138, 403)~~ bỏ 2026-11-11: người gửi có quyền duyệt thì tự duyệt được; không dùng lại số 1138 | G94-180/AUTH-21 |
| Duyệt đổi SĐT: số mới đã thuộc người khác, tài khoản/hồ sơ đã đổi số, phụ huynh hết liên kết hoặc đã bị bỏ khỏi hồ sơ, phụ huynh vừa có tài khoản, trẻ đã nghỉ (desc nêu lý do) | `PHONE_CHANGE_REQUEST_STALE` (1139, 409) | G94-180/AUTH-21 |
| Gửi yêu cầu đổi SĐT (`POST /children/{id}/guardians/{guardianId}/phone-change-requests`): thiếu/sai số, trùng số đang dùng, thiếu lý do, phụ huynh kiêm nhân sự (ô `newPhone`, `reason`) / số đã thuộc phụ huynh hoặc tài khoản khác (ô `newPhone`) / phụ huynh không thuộc trẻ / trẻ đã nghỉ. Sửa hồ sơ (`PUT /children/{id}`) đổi SĐT phụ huynh đã lưu ⇒ 400 ô `guardians[i].phone` | `INVALID_REQUEST_DATA` (9001, 400) / `GUARDIAN_IDENTITY_CONFLICT` (3009, 409) / `CHILD_GUARDIAN_NOT_FOUND` (3012, 404) / `CHILD_ALREADY_LEFT` (3011, 409) | G94-180/AUTH-21 (sửa 2026-11-11) |
| Duyệt đổi SĐT khi realm Keycloak chưa bật sửa username (`docs/system/KEYCLOAK.md` mục 7) | `USERNAME_CHANGE_NOT_ALLOWED` (1143, 503) | G94-180/AUTH-21 |
| AI không khả dụng / quá thời gian | `AI_SERVICE_UNAVAILABLE` (503) / `AI_TIMEOUT` (504) | AI-03 |
| Sửa khai báo sức khỏe: form sai (ô theo khai báo, không tiền tố `declaration.`: `allergies`, `medicalHistory.tbContactDate`…), thiếu lý do khi đã có khai báo (`reason`, ≤ 300) | `INVALID_REQUEST_DATA` (9001) + ô | G94-180/CHD-06 |
| Giải thích xu hướng sức khỏe: AI chưa cấu hình hoặc viết ngôn ngữ chẩn đoán (bỏ, không lưu) / quá thời gian | `AI_SERVICE_UNAVAILABLE` (8001, 503) / `AI_TIMEOUT` (8002, 504) | HLT-04, AI-03 |
| Duyệt bản giải thích xu hướng không tồn tại hoặc của trẻ khác / nhân sự ngoài phạm vi trẻ | `HEALTH_INTERPRETATION_NOT_FOUND` (5040, 404) / `HEALTH_RECORD_ACCESS_DENIED` (5025, 403) | HLT-05 |
| Phụ huynh xem kết quả sức khỏe của trẻ không liên kết | `CHILD_ACCESS_DENIED` (403) | PAR-03, AUTH-07 |
| Upload quá cỡ / sai loại | `FILE_TOO_LARGE` (413) / `UNSUPPORTED_MEDIA_TYPE` (415) | — |
| Tạo phiên bản kế hoạch năm sai nguồn / còn bản đang soạn | `YEAR_PLAN_VERSION_SOURCE_INVALID` (7027, 409) / `YEAR_PLAN_OPEN_VERSION_EXISTS` (7028, 409) | EDU-25 |
| Ghi tài liệu lớp trên phiên bản đã thay thế / gửi bản chép còn ô đỏ | `YEAR_PLAN_SUPERSEDED` (7122, 409) / `CLASS_DOC_VERSION_FIX_REQUIRED` (7121, 409) | EDU-26 |
| Gửi hồ sơ chủ đề còn mục giáo án ngày trống (`data.details = [{date, section}]`) | `DAY_PLAN_SECTION_EMPTY` (7390, 400) | spec 005 FR-007a |
| Gửi gói học kỳ còn thiếu phần (`data.details` = mục thiếu) | `SEMESTER_PACKAGE_INCOMPLETE` (7387, 400) | spec 004 FR-003 |

Không trả stack trace, tên class, SQL. Lỗi từ Spring Security ghi cùng format (guide 8.5, `docs/contracts/AUTH_CONTRACT.md`): 401 `UNAUTHENTICATED` (1002, kèm `WWW-Authenticate: Bearer`), 403 `FORBIDDEN` (1101), 503 `AUTH_SERVICE_UNAVAILABLE` (1013, không lấy được key kiểm token từ Keycloak).
