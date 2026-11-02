# Response & Error Contract

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
| 90xx | Chung & hệ thống (validation 9001, sai định dạng 9002, không tìm thấy 9009, lỗi hệ thống 9010…) |

Mã chung không có `errorCode` (`SUCCESSFUL 200`, `CREATED 201`, `BAD_REQUEST`, `UNAUTHORIZED`, `NOT_FOUND`, `CONFLICT`, `PRECONDITION_REQUIRED`, `PAYLOAD_TOO_LARGE`, `SERVICE_UNAVAILABLE`, `GATEWAY_TIMEOUT`) chỉ dùng cho response thành công hoặc chỗ chưa gắn mã nghiệp vụ.

ADR-0013: `PASSWORD_CHANGE_REQUIRED` (1010, HTTP 428) cho Web gateway: mật khẩu tạm đã xác minh cần đổi lần đầu; `data` chỉ chứa challenge opaque và hạn dùng, không có access/refresh token. FE xử lý 428 bằng màn hình đổi mật khẩu; Mobile PKCE không dùng endpoint này.

## Dùng theo tình huống nghiệp vụ

Học kỳ năm học (Cấu hình thời gian, 30/10/2026; đổi thành 4 ngày cùng ngày): `PUT /school-years/{id}/semester` nhận `semester1Start`, `semester1End`, `semester2Start`, `semester2End`, do Hiệu trưởng chọn. Thiếu ngày nào trả `SEMESTER_DATE_REQUIRED` 2026; ngày nằm ngoài năm học trả `SEMESTER_OUT_OF_YEAR` 2021 (thay `SEMESTER1_END_OUT_OF_RANGE`); học kỳ có ngày bắt đầu sau ngày kết thúc trả `SEMESTER_START_AFTER_END` 2024; HK II bắt đầu không sau ngày kết thúc HK I trả `SEMESTER_OVERLAP` 2025. Tất cả HTTP 400, kiểm tra theo đúng thứ tự trên. Đổi ngày năm học làm học kỳ đã đặt nằm ngoài năm cũng trả 2021. Năm đã kết thúc trả `SCHOOL_YEAR_CLOSED` 2003. FE gắn 2021/2026 vào ô ngày được nêu trong thông báo, 2024 vào ngày bắt đầu của học kỳ đó, 2025 vào `semester2Start`.

Thiết lập năm học (30/10/2026): `POST /school-years/{id}/copy {sourceYearId, parts}` chép lớp / GV / tổ trưởng / giờ chốt / học kỳ từ năm khác. `parts` rỗng trả `YEAR_COPY_PARTS_REQUIRED` 2023, HTTP 400; năm đích không ở trạng thái chưa bắt đầu trả `YEAR_COPY_TARGET_NOT_PLANNED` 2022, HTTP 409; năm nguồn trùng năm đích trả `COPY_SOURCE_YEAR_INVALID` 2215; thiếu `sourceYearId` trả 400 validation. Lỗi từng dòng (GV đã khóa, không còn ở điểm trường, phòng không dùng được, năm nguồn chưa có giờ chốt/học kỳ...) KHÔNG làm hỏng cả yêu cầu: dòng đó nằm trong `data.skipped[{part, name, reason}]`, FE hiện đỏ ở bước tương ứng. Dữ liệu năm đích đã có (lớp trùng tên, lớp đã có GV, nhóm tuổi đã có tổ trưởng, giờ chốt, học kỳ) giữ nguyên.

Báo cáo chất lượng tháng (sổ chỉ đạo chuyên môn 4.1 + 4.2, 30/10/2026): `GET /assessment/quality-report` trả `QUALITY_REPORT_FORBIDDEN` 7324 (403) khi không phải HT/PHT, `QUALITY_REPORT_MONTH_OUT_OF_YEAR` 7325 (400) khi tháng ngoài năm học, `QUALITY_REPORT_FUTURE_MONTH` 7326 (400) khi tháng sau tháng hiện tại; campus ngoài phạm vi trả `CAMPUS_ACCESS_DENIED` 2102; `month` sai dạng yyyy-MM trả lỗi định dạng 9002. FE gắn 7325/7326 vào ô `month`.

Đơn nghỉ phụ huynh (31/10/2026, ATT-04/05/05b): `/leave-requests` trả `LEAVE_NOT_GUARDIAN` 4018 (403) khi không phải phụ huynh đang liên kết với trẻ, `LEAVE_DATE_RANGE_INVALID` 4019 (400), `LEAVE_DATE_IN_PAST` 4020 (400), `LEAVE_NO_SCHOOL_DAY` 4021 (400) khi không còn ngày học trước giờ chốt, `LEAVE_OVERLAP` 4022 (409) khi trùng đơn còn hiệu lực, `LEAVE_NOT_FOUND` 4023 (404), `LEAVE_CANNOT_CANCEL` 4024 (409) khi mọi ngày còn lại đã qua giờ chốt, `LEAVE_CHILD_NO_CLASS` 4025 (400) khi trẻ chưa có lớp năm đang hoạt động. GV/PHT/HT xem ngoài phạm vi: `CLASS_ACCESS_DENIED` / `CAMPUS_ACCESS_DENIED` (403).

Phiếu bé ngoan (30/10/2026): `/assessment/behaviour-tickets` trả `TICKET_FORBIDDEN` 7327 (403) khi phát phiếu cho lớp không phải lớp mình dạy, `TICKET_VIEW_FORBIDDEN` 7328 (403) khi xem lớp/điểm trường ngoài phạm vi, `TICKET_PERIOD_INVALID` 7329 (400) khi kỳ không phải thứ Hai đầu tuần / ngày 1 đầu tháng hoặc ngoài năm học, `TICKET_PERIOD_NOT_ENDED` 7330 (400) khi kỳ chưa kết thúc, `TICKET_NO_CHILDREN` 7331 (400) khi không chọn trẻ, `TICKET_TOO_MANY_CHILDREN` 7332 (400) khi chọn quá 100 trẻ hoặc trùng trẻ, `TICKET_CHILD_NOT_IN_CLASS` 7333 (400) khi trẻ không thuộc lớp trong kỳ, `TICKET_ALREADY_ISSUED` 7334 (409) khi trẻ đã có phiếu kỳ đó, `TICKET_NOT_ELIGIBLE` 7335 (400) khi trẻ chưa đủ điều kiện (phiếu tuần: ≥ 4 ngày có mặt và ≥ 3 Cờ; phiếu tháng: ≥ 3 phiếu tuần có thứ Hai trong tháng). Năm học chưa kích hoạt trả `ASSESSMENT_YEAR_NOT_ACTIVE` 7304. FE hiện các lỗi này ở thông báo trên bảng (không gắn ô nhập).

Đánh giá tháng / cuối năm (30/10/2026): `/assessment/evaluations` trả `EVALUATION_NOT_FOUND` 7336 (404), `EVALUATION_VIEW_FORBIDDEN` 7337 (403) khi xem lớp ngoài phạm vi hoặc PHT/HT mở đánh giá chưa xác nhận, `EVALUATION_EDIT_FORBIDDEN` 7338 (403) khi sửa / xác nhận / tạo lại bản nháp mà không phải GV đang phụ trách lớp, `EVALUATION_ALREADY_CONFIRMED` 7339 (409), `EVALUATION_CONTENT_REQUIRED` 7340, `EVALUATION_CONTENT_TOO_SHORT` 7341 (dưới 30 ký tự), `EVALUATION_CONTENT_TOO_LONG` 7342 (trên 3000 ký tự), `EVALUATION_DIAGNOSTIC_TERM` 7343 (từ chẩn đoán, cùng danh sách với đánh giá hằng ngày), `EVALUATION_MONTH_INVALID` 7344 khi `month` không phải ngày 1 hoặc ngoài năm học / sau tháng hiện tại; đều HTTP 400. Năm học không hoạt động trả `ASSESSMENT_YEAR_NOT_ACTIVE` 7304. FE gắn 7340–7343 vào ô `content`. Trẻ 5 tuổi hoàn thành chương trình: `PUT /assessment/evaluations/{id}/program-completion` trả `EVALUATION_PROGRAM_NOT_APPLICABLE` 7345 (400) khi không phải đánh giá cuối năm của lớp nhóm tuổi từ 60 tháng, `EVALUATION_PROGRAM_REQUIRED` 7346 (400) khi `completed` null hoặc xác nhận đánh giá cuối năm lớp 5–6 tuổi chưa chọn Hoàn thành / Chưa hoàn thành; `GET /assessment/program-completion` trả `PROGRAM_REPORT_FORBIDDEN` 7347 (403) khi không phải HT/PHT, campus ngoài phạm vi trả 2102. FE gắn 7345/7346 vào ô `programCompleted`.

Hồ sơ / tiến độ phát triển (30/10/2026): `GET /assessment/children` trả `CLASS_ACCESS_DENIED` 2201 (403) khi `classId` ngoài phạm vi. `GET /assessment/children/{childId}/development` trả `INVALID_DATE_RANGE` 9005 (400) khi thiếu `start` / `end`, `start` sau `end` hoặc `start` sau hôm nay; `DATE_RANGE_TOO_LONG` 9006 (400) khi khoảng quá 400 ngày; `DEVELOPMENT_CHILD_NO_CLASS` 7348 (404) khi trẻ không thuộc lớp nào ở đầu / cuối khoảng; `CHILD_ACCESS_DENIED` 3001 (403) khi không phải GV lớp của trẻ hoặc PHT/HT điểm trường của lớp.

Đề xuất khen thưởng theo danh sách lớp (30/10/2026), `/assessment/reward-lists`: `REWARD_LIST_NOT_FOUND` 7349 (404), `REWARD_VIEW_FORBIDDEN` 7350 (403) khi lớp ngoài phạm vi, `REWARD_EDIT_FORBIDDEN` 7351 (403) khi không phải GV đang phụ trách lớp, `REWARD_STATUS_CHANGED` 7352 (409) khi danh sách đã gửi / đã chuyển bước, `REWARD_LIST_EMPTY` 7353 khi gửi mà chưa trẻ nào có danh hiệu, `REWARD_CHILD_NOT_IN_CLASS` 7354, `REWARD_CHILD_DUPLICATE` 7355, `REWARD_TITLE_INVALID` 7356 (danh hiệu ngoài danh sách), `REWARD_PROGRAM_TITLE_INVALID` 7357 (danh hiệu Hoàn thành chương trình khi đánh giá cuối năm chưa đánh dấu hoàn thành), `REWARD_YEAR_END_REQUIRED` 7358 (trẻ chưa có đánh giá cuối năm đã xác nhận), `REWARD_NOTE_TOO_LONG` 7359 (trên 300 ký tự), `REWARD_NOTE_DIAGNOSTIC` 7360, `REWARD_DECISION_INVALID` 7361, `REWARD_COMMENT_REQUIRED` 7362 (trả lại không nêu lý do), `REWARD_COMMENT_TOO_LONG` 7363 (trên 500 ký tự); các lỗi nhập là HTTP 400. `REWARD_REVIEW_FORBIDDEN` 7364 (403) khi không phải PHT điểm trường / HT. Năm học không hoạt động trả 7304. Lỗi 7354–7360 kèm tên trẻ trong thông báo, FE hiện đỏ ngay trên bảng.

Nhận xét chung lớp (OBS-22, 29/10/2026): `CLASS_COMMENT_TOO_LONG` 7322 và `CLASS_COMMENT_DIAGNOSTIC_TERM` 7323, HTTP 400, FE gắn lỗi vào `classComment`. Các lỗi ngày/khóa/quyền của đánh giá ngày tiếp tục dùng dải 7301–7321. PUT General cho phép `entries: []` khi gửi `classComment`; thiếu/null giữ nội dung cũ, chuỗi rỗng xóa nội dung.

| Tình huống | ApiCode (errorCode) | Rule |
| --- | --- | --- |
| Ngoài phạm vi campus/lớp/trẻ | `CAMPUS_ACCESS_DENIED` / `CLASS_ACCESS_DENIED` / `CHILD_ACCESS_DENIED` (403) | AUTH-07 |
| Năm học đã kết thúc | `SCHOOL_YEAR_CLOSED` (409) | CFG-YR-01 |
| Điểm danh sau giờ chốt | `ATTENDANCE_LOCKED` (409) | ATT-08 |
| Sổ báo ăn đã xác nhận | `MEAL_COUNT_ALREADY_CONFIRMED` (409) | NUT-03 |
| AI không khả dụng / quá thời gian | `AI_SERVICE_UNAVAILABLE` (503) / `AI_TIMEOUT` (504) | AI-03 |
| Upload quá cỡ / sai loại | `FILE_TOO_LARGE` (413) / `UNSUPPORTED_MEDIA_TYPE` (415) | — |
| Tạo phiên bản kế hoạch năm sai nguồn / còn bản đang soạn | `YEAR_PLAN_VERSION_SOURCE_INVALID` (7027, 409) / `YEAR_PLAN_OPEN_VERSION_EXISTS` (7028, 409) | EDU-25 |
| Ghi tài liệu lớp trên phiên bản đã thay thế / gửi bản chép còn ô đỏ | `YEAR_PLAN_SUPERSEDED` (7122, 409) / `CLASS_DOC_VERSION_FIX_REQUIRED` (7121, 409) | EDU-26 |
| Kế hoạch điều chỉnh sai ngày / quá dài | `CLASS_DOC_ADJUSTMENT_INVALID` (7123, 400) | EDU-27 |

Không trả stack trace, tên class, SQL. Lỗi từ Spring Security ghi cùng format (guide 8.5, `docs/contracts/AUTH_CONTRACT.md`): 401 `UNAUTHENTICATED` (1002, kèm `WWW-Authenticate: Bearer`), 403 `FORBIDDEN` (1101), 503 `AUTH_SERVICE_UNAVAILABLE` (1013, không lấy được key kiểm token từ Keycloak).
