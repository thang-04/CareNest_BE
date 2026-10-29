# Business Rules

Mỗi rule có **ID** (dùng trong code comment, test name, PR) và **Status**. Agent chỉ implement rule `CONFIRMED`/`ACCEPTED`. Rule `PENDING` ⇒ làm cấu hình được hoặc hỏi; không tự quyết.

Status: `CONFIRMED` (khảo sát/Report 1) · `ACCEPTED` (quyết định dự án/ADR đã chấp nhận) · `PROPOSED` · `PENDING` · `OPEN` (ADR chưa chốt) · `REMOVED` (đã bỏ, giữ dòng để truy vết; không implement).

Nguồn "Khảo sát team 27/10 (XXX-nn)" là mã câu trong bộ khảo sát nghiệp vụ của team (file `CareNest_CODE/docs/survey/carenest-business-survey.html`, ngoài repo).

Khi thêm rule mới: thêm ID kế tiếp trong nhóm, ghi nguồn, cập nhật module card tương ứng.

## AUTH — Phạm vi truy cập (module: identity-access, school-structure, child)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| AUTH-01 | Principal có scope toàn trường (mọi campus) | CONFIRMED | Khảo sát |
| AUTH-02 | Vice Principal có scope theo campus được phân công qua StaffAssignment, không gắn cứng role→campus. Mỗi PHT phụ trách một mảng (dinh dưỡng / chuyên môn) và chỉ xem các mảng của cơ sở mình; xem cơ sở khác theo AUTH-20, sửa theo mảng theo AUTH-20 | CONFIRMED | Khảo sát; khảo sát team 27/10 (ROL-01, ROL-03), user chốt 2026-10-27 (đóng P-14) |
| AUTH-03 | Teacher chỉ truy cập lớp đang được phân công. Mở rộng 29/10: GV đã hết phân công vẫn xem, chỉ đọc, dữ liệu của lớp mình từng được phân công trong khoảng thời gian phân công đó (năm học cũ); không xem lớp mới của năm học mới. **Code hiện tại chỉ cho xem theo phân công còn hiệu lực — phần mở rộng chưa làm** | CONFIRMED | Report 1; khảo sát team 27/10 (ROL-10, PER-08), user chốt 2026-10-29 |
| AUTH-04 | Parent chỉ truy cập trẻ có GuardianChildLink với mình, và chỉ dữ liệu được phép hiển thị cho phụ huynh | CONFIRMED | Report 1 |
| AUTH-05 | Kitchen Staff xem dữ liệu bếp của cơ sở mình: thực đơn đã công bố, số suất đã xác nhận theo lớp từng bữa, định lượng. Dị ứng chỉ ở dạng số suất dị ứng/thay thế theo món và lớp, không thấy tên trẻ (NUT-23); không xem hồ sơ trẻ. Mỗi cơ sở có bếp riêng | CONFIRMED (đóng P-06) | Khảo sát; khảo sát team 27/10 (ALG-02, MEA-04, ROL-11), user chốt 2026-10-28 |
| AUTH-06 | Hiệu trưởng cấp tài khoản và phân quyền nghiệp vụ (PHT, GV, bếp, phụ huynh); System Admin giữ phần kỹ thuật: tạo tài khoản PRINCIPAL, khóa/mở tài khoản, xử lý sự cố. Admin mặc định **không** xem dữ liệu trẻ trừ khi được gán thêm role nghiệp vụ | CONFIRMED (phân vai HT/Admin); Admin xem dữ liệu trẻ: PENDING P-16 | Team chốt 2026-10-16 (thay bản PROPOSED "Admin quản lý tài khoản/role") |
| AUTH-07 | Mọi kiểm tra quyền thực hiện ở BE; UI ẩn nút không phải biện pháp phân quyền | ACCEPTED | ADR-0003 |
| AUTH-08 | Báo cáo/tổng hợp chỉ tính dữ liệu trong scope người xem | ACCEPTED | ADR-0002 |
| AUTH-09 | Admin không tự đổi trạng thái (khóa/mở) tài khoản của chính mình qua API; khóa Admin khác vẫn được | CONFIRMED | Team chốt 2026-10-06 |
| AUTH-10 | Hiệu trưởng bật/tắt quyền theo role (ma trận) cho PHT, GV, bếp và tạo ngoại lệ cấp thêm/thu hồi cho từng người; quyền hiệu lực = phần cố định của role ∪ (ma trận ∪ cấp thêm − thu hồi), có hiệu lực ngay ở request kế tiếp | CONFIRMED | Team chốt 2026-10-16, ADR-0017 |
| AUTH-11 | Quyền phân quyền chỉ Hiệu trưởng có, không giao được; quyền của PRINCIPAL, ADMIN, PARENT cố định trong hệ thống, không sửa qua ma trận | CONFIRMED | Team chốt 2026-10-16 |
| AUTH-12 | Không ai tự sửa quyền, trạng thái hoặc role của chính mình (mở rộng AUTH-09) | CONFIRMED | Team chốt 2026-10-16 |
| AUTH-13 | Quyền chỉ quyết định được làm hành động hay không; phạm vi campus/lớp/trẻ (AUTH-01..05) luôn áp dụng, quyền không vượt scope | CONFIRMED | Team chốt 2026-10-16 |
| AUTH-14 | Mọi thay đổi ma trận/ngoại lệ lưu lịch sử: người đổi, thời điểm, trước/sau, lý do bắt buộc | CONFIRMED | Team chốt 2026-10-16 |
| AUTH-15 | Tạo tài khoản nhân sự một bước kèm phân công (PHT/bếp → campus, GV → lớp). Admin chỉ tạo tài khoản Hiệu trưởng (tự có phân công toàn trường); HT tạo PHT/GV/bếp, phân công có thể bổ sung sau; PHT (khi HT bật quyền) chỉ tạo GV/bếp và bắt buộc phân công vào campus mình | CONFIRMED | User chốt 2026-10-17 |
| AUTH-16 | HT phân công PHT/bếp → campus, GV → lớp toàn trường; PHT có quyền phân công (mặc định bật, HT tắt được) chỉ phân công GV/bếp trong campus mình, không phân công PHT. Kết thúc phân công bằng ngày cuối, không xóa; không ai tự phân công cho mình. GV nghỉ ⇒ HT hoặc PHT phân công GV dạy thay vào lớp, có ngày kết thúc | CONFIRMED | User chốt 2026-10-17; khảo sát team 27/10 (ROL-12) |
| AUTH-17 | HT khóa/mở, cấp lại mật khẩu tạm, xem danh sách PHT/GV/bếp toàn trường; PHT (khi HT bật quyền) chỉ với GV/bếp đang phân công trong campus mình. Không ai thao tác tài khoản HT/Admin (trừ Admin) hay chính mình. Cấp lại mật khẩu tạm hủy mọi phiên đang mở | CONFIRMED | User chốt 2026-10-17 |
| AUTH-18 | Tài khoản phụ huynh tạo kèm liên kết trẻ (không tạo rời): tên đăng nhập = SĐT chuẩn hóa (10 số bắt đầu 0), mật khẩu tạm hệ thống sinh, bắt đổi lần đầu. Gửi tên đăng nhập + mật khẩu tạm qua SMS tới SĐT phụ huynh; khi chưa có nhà cung cấp SMS thì hiện một lần để in phiếu (dự phòng). **Code hiện tại chỉ có in phiếu; gửi SMS chưa làm.** SĐT đã là phụ huynh ⇒ chỉ thêm liên kết (giữ mật khẩu); SĐT của nhân sự ⇒ từ chối. HT làm cho mọi trẻ; PHT có quyền (mặc định bật, HT tắt được) chỉ cho trẻ đang học trong campus mình. Quan hệ: Mẹ / Bố / Người giám hộ / Khác. Số tài khoản mỗi trẻ: P-28 | CONFIRMED (SMS: chưa triển khai) | User chốt 2026-10-16, 2026-10-17; khảo sát team 27/10 (CHD-04), user chốt 2026-10-28 (gửi SMS, in phiếu dự phòng) |
| AUTH-19 | Gỡ liên kết phụ huynh–trẻ giữ nguyên tài khoản, phụ huynh mất quyền xem trẻ ngay. Khóa/mở, cấp lại mật khẩu tạm (hệ thống sinh, hủy mọi phiên) cho phụ huynh khi người làm phụ trách campus của ít nhất một trẻ đang liên kết với họ | CONFIRMED | User chốt 2026-10-17 |
| AUTH-20 | Quyền theo mảng là mã quyền bật/tắt (không chia mức Đầy đủ/Giới hạn/Không): mã sửa của từng mảng thêm cùng module, PHT mặc định tắt, HT cấp cho đúng PHT phụ trách mảng (AUTH-10). PHT xem cơ sở khác khi HT cấp quyền xem liên cơ sở: chỉ xem dữ liệu trường (cơ sở, lớp, trẻ, dữ liệu module); tạo tài khoản, phân công, phụ huynh vẫn chỉ cơ sở mình; vẫn cần phân công PHT còn hiệu lực | CONFIRMED | Khảo sát team 27/10 (ROL-01, ROL-03, ROL-09), user chốt 2026-10-27 |
| AUTH-21 | Web chỉ cho ADMIN, Hiệu trưởng, PHT, GV; bếp và phụ huynh chỉ dùng App, gateway Web từ chối đăng nhập nếu tài khoản không có role nào trong nhóm Web. App chỉ cho phụ huynh, GV, bếp; Ban giám hiệu không dùng App (thay quyết định APP 2026-10-13). App tập trung cho phụ huynh và GV (điểm danh, sức khỏe, đánh giá, đón trẻ, thông báo); bếp chỉ giữ màn tối thiểu (số suất theo lớp, thực đơn, ảnh mẫu, sổ lưu mẫu) | CONFIRMED | Khảo sát team 27/10 (ORG-10, SCO-08, INT-10), user chốt 2026-10-27, 2026-10-29 |
| AUTH-22 | Tổ bếp mỗi cơ sở dùng chung một tài khoản; lịch sử thao tác ghi theo tài khoản, không phân biệt từng người | CONFIRMED | Khảo sát team 27/10 (ROL-11) |
| AUTH-23 | Tổ trưởng chuyên môn không phải role đăng nhập: là GV được HT (chỉ HT) phân công theo nhóm tuổi, mỗi nhóm tuổi một người dùng chung hai cơ sở. Khi phân công còn hiệu lực, người đó có thêm quyền của cột "Tổ trưởng" trong ma trận (HT bật/tắt); hết phân công thì mất. Việc: soạn mục tiêu năm/kế hoạch chủ đề của nhóm tuổi, duyệt kế hoạch/giáo án của GV trong nhóm tuổi trước PHT. Không thêm quyền xem lớp/trẻ ngoài lớp mình dạy (AUTH-03) | CONFIRMED | Khảo sát team 27/10 (ROL-04, ROL-05), user chốt 2026-10-27 |

## SCH — Tổ chức trường (module: school-structure)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| SCH-01 | 4 nhóm tuổi: nhà trẻ 24–36 tháng; mẫu giáo 3–4, 4–5, 5–6 tuổi. Không có lớp ghép: mỗi lớp thuộc đúng một nhóm tuổi | CONFIRMED | Khảo sát team 27/10 (ORG-03, ORG-04) |
| SCH-02 | Mỗi năm học giữ các lớp cũ, có lớp mới thì thêm; lớp có thể đổi tên; danh sách trẻ thay đổi theo năm. Cách lưu lớp qua năm (DATA_DICTIONARY `classrooms`) chờ thiết kế school-structure | CONFIRMED | Khảo sát team 27/10 (ORG-05) |
| SCH-03 | Mỗi lớp một phòng cố định cả năm; sửa chữa thì có thể chuyển phòng khác | CONFIRMED | Khảo sát team 27/10 (ORG-06) |
| SCH-04 | Mỗi lớp 2 GV cố định; GV dạy thay theo AUTH-16 | CONFIRMED | Khảo sát team 27/10 (ORG-07) |
| SCH-05 | Ngày nghỉ/lễ thay đổi theo thông báo phòng giáo dục, phải sửa được linh hoạt; không học thứ Bảy. Học kỳ/trông hè: PENDING P-18 | CONFIRMED / PENDING | Khảo sát team 27/10 (ORG-08, ORG-09) |
| SCH-06 | Hai cơ sở dùng chung: thực đơn, kho khô (quản lý chung, hàng để riêng từng cơ sở), NCC, mục tiêu năm học và kế hoạch chủ đề, giờ đón trẻ và giờ chốt suất, tổ chuyên môn; báo cáo tách riêng từng cơ sở. Phần bán trú áp vào NUT-11, NUT-12, NUT-13 | CONFIRMED | Khảo sát team 27/10 (ORG-01); đối chiếu nutrition 2026-10-28 |

## CHD — Hồ sơ trẻ (module: child)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| CHD-01 | PHT chuyên môn tiếp nhận và nhập hồ sơ trẻ mới, bằng form hoặc nhập từ file Excel (danh sách lấy từ CSDL ngành hoặc trường tự lập) | CONFIRMED | Khảo sát team 27/10 (CHD-01, CHD-02, SCO-05) |
| CHD-02 | Hồ sơ trẻ gồm: họ tên, ngày sinh, giới tính, địa chỉ, ảnh, tiền sử bệnh, dị ứng (HLT-06), bố mẹ/người giám hộ và SĐT, lưu ý đặc biệt (chế độ ăn, hành vi), số định danh/CCCD nếu có. Số định danh và tiền sử bệnh là dữ liệu nhạy cảm, hiển thị theo AUTH-03, AUTH-04, PAR-02 | CONFIRMED | Khảo sát team 27/10 (CHD-03) |
| CHD-03 | Trẻ được chuyển lớp trong năm học; không chuyển cơ sở | CONFIRMED | Khảo sát team 27/10 (CHD-06); Review 1 11/10 |
| CHD-04 | Trẻ thôi học ⇒ phụ huynh mất quyền xem trẻ đó ngay (tài khoản vẫn dùng cho trẻ khác còn học); dữ liệu giữ trong DB, không xóa | CONFIRMED | Khảo sát team 27/10 (CHD-07) |
| CHD-05 | Phụ huynh tự sửa thông tin liên lạc của mình; thông tin của trẻ do nhà trường sửa | CONFIRMED | Khảo sát team 27/10 (CHD-08) |

## ATT — Điểm danh, báo ăn, đơn nghỉ (module: attendance)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| ATT-01 | GV điểm danh theo lớp/ngày cho trẻ đang học trong lớp, chủ yếu trên App (điện thoại); chỉ lớp được phân công và ngày còn mở (trước giờ chốt hoặc khi được mở lại — ATT-08) | CONFIRMED | FE-01, guide 13.1; khảo sát team 27/10 (ATT-01) |
| ATT-01b | Một trẻ chỉ có một trạng thái điểm danh cho một ngày/lớp; gửi lặp không tạo bản ghi trùng (upsert + unique) | ACCEPTED | guide 13.1 |
| ATT-02 | Có mặt là ăn tất cả các bữa đã cấu hình cho nhóm tuổi của trẻ (NUT-17); GV không nhập tham gia ăn riêng, không tách theo bữa. Số suất tính từ điểm danh | CONFIRMED | Khảo sát team 27/10 (ATT-10, LEA-02), user chốt 2026-10-28 (thay bản "GV nhập tham gia ăn cùng điểm danh") |
| ATT-03 | Sau giờ chốt trường không nhận trẻ đến muộn (phụ huynh đưa về); điểm danh đã khóa, không có suất | CONFIRMED | Khảo sát team 27/10 (ATT-04, ATT-05), khảo sát trường 18/10 (#2), user chốt 2026-10-28 (thay bản "cập nhật điểm danh khi trẻ đến muộn") |
| ATT-04 | Phụ huynh gửi đơn nghỉ ⇒ các ngày tương ứng trẻ được ghi Vắng có phép tự động (ATT-09); không có suất vì suất tính theo điểm danh (ATT-02). Trẻ có đơn nghỉ mà vẫn đến lớp: PENDING P-20 | CONFIRMED / PENDING | Review 24/09, team 02/10; khảo sát team 27/10 (ATT-08, LEA-02, LEA-05) |
| ATT-05 | Đơn nghỉ là **thông báo, không có bước duyệt**: phụ huynh tự gửi qua App, gồm ngày bắt đầu–kết thúc và lý do; GV/BGH trong scope chỉ xem. GV không tạo đơn thay phụ huynh | CONFIRMED (chốt 02/10, đóng P-15) | Team 02/10, review 24/09; khảo sát team 27/10 (LEA-01, LEA-03, LEA-04) |
| ATT-05b | Đơn nghỉ gửi sau giờ chốt của một ngày không đổi điểm danh và số suất ngày đó (điểm danh đã khóa); áp từ ngày kế tiếp | PROPOSED | Suy từ ATT-02, ATT-06 (khảo sát team 27/10: LEA-02) |
| ATT-06 | Một giờ chốt điểm danh chung toàn trường; chốt điểm danh là chốt suất cả ngày. HT cấu hình, đổi theo mùa, có ngày áp dụng và lưu vết thay đổi; không hard-code | CONFIRMED (đóng P-03) | Khảo sát team 27/10 (ATT-02, ATT-03); GVHD 22/10; khảo sát trường 18/10 (#2); Report 1 v2 FE-01 |
| ATT-07 | Thay đổi điểm danh/báo ăn sau khi số suất đã xác nhận ⇒ số suất bị đánh dấu thay đổi, cần xác nhận lại (NUT-03); attendance không tự sửa số suất | ACCEPTED | ADR-0004, ADR-0005; khảo sát team 27/10 (ATT-07) |
| ATT-08 | Sau giờ chốt, PHT mở lại điểm danh của lớp để GV điểm danh hoặc sửa; bắt buộc ghi lý do, lưu nhật ký. Số suất đã xác nhận xử lý theo ATT-07 | CONFIRMED | Khảo sát team 27/10 (ATT-02, ATT-06); Report 1 v2 FE-01 |
| ATT-09 | Trạng thái điểm danh: Có mặt / Vắng. Vắng là có phép khi phụ huynh đã gửi đơn nghỉ cho ngày đó, còn lại mặc định không phép | CONFIRMED | Khảo sát team 27/10 (ATT-08) |
| ATT-10 | Cuối ngày GV xác nhận trả trẻ cho từng trẻ (điểm danh lúc về). Người đón, xác minh, giờ đón: PCK-02, PCK-04 | CONFIRMED | Khảo sát team 27/10 (ATT-09, PCK-01..06) |
| ATT-11 | Thống kê có mặt/vắng theo lớp, theo cơ sở, toàn trường cho BGH, trong scope người xem (AUTH-08) | CONFIRMED | Khảo sát team 27/10 (ATT-12) |

## PCK — Đón trẻ (module: attendance)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| PCK-01 | Đón trẻ là bắt buộc trong Capstone | CONFIRMED | Khảo sát team 27/10 (PCK-07, SCO-02) |
| PCK-02 | Màn đón trẻ hiện danh sách phụ huynh/người giám hộ của trẻ: GV nhận đúng người ⇒ xác nhận đón; người lạ ⇒ GV bấm gọi phụ huynh để xác nhận, ghi lý do rồi xác nhận đón. Đối chiếu ảnh người đón: nguồn ảnh P-33 | CONFIRMED / PENDING | Khảo sát team 27/10 (PCK-01, PCK-03, PCK-04) |
| PCK-03 | Phụ huynh không đăng ký trước người đón hộ trên App; báo GV qua gọi/nhắn như hiện nay | CONFIRMED | Khảo sát team 27/10 (PCK-02) |
| PCK-04 | Mỗi lần trả trẻ ghi người đón và giờ đón thực tế (kể cả đón muộn); trẻ về sớm (ốm…) ghi "về sớm", người đón, giờ đón; phụ huynh nhận thông báo khi con đã được đón. Đây là bước xác nhận trả trẻ cuối ngày (ATT-10) | CONFIRMED | Khảo sát team 27/10 (PCK-05, PCK-06, DHL-03, ATT-09) |

## NUT — Dinh dưỡng, suất ăn, bếp (module: nutrition)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| NUT-01 | Số suất = số trẻ **có mặt** (ATT-02), cho từng bữa đã cấu hình theo nhóm tuổi; không tính từ sĩ số lớp; không tính suất GV/nhân viên | CONFIRMED | Report 1, guide 13.1; khảo sát team 27/10 (ATT-10, MEA-03) |
| NUT-02 | Số suất mỗi cơ sở được PHT đang phân công tại cơ sở đó xác nhận trước khi chuyển bếp; PHT vắng ⇒ HT xác nhận thay. PHT chỉ xem và xác nhận; số sai thì trả về GV sửa (qua mở lại điểm danh — ATT-08). Khảo sát trường 18/10 (#3) nêu thêm PHT còn lại làm thay; team chọn HT | CONFIRMED (đóng P-05) | FE-01; khảo sát team 27/10 (ROL-02, MEA-05) |
| NUT-03 | Thay đổi sau khi BGH xác nhận không sửa ngầm số đã xác nhận: lưu lịch sử, đánh dấu đã thay đổi, yêu cầu xác nhận lại, thông báo vai trò cần xử lý; bếp chỉ thấy số đã xác nhận | ACCEPTED | ADR-0005, guide 13.1; khảo sát team 27/10 (ATT-07) |
| NUT-04 | Không bổ sung suất cho trẻ đến muộn (ATT-03). Thay đổi sau khi xác nhận chỉ đi qua mở lại điểm danh (ATT-08) rồi xác nhận lại (NUT-03); bếp làm theo số đã xác nhận lại. Thiếu/thừa suất thực tế: NUT-22 | CONFIRMED (đóng P-04) | Khảo sát team 27/10 (ATT-04, ATT-05, ATT-06, MEA-09), user chốt 2026-10-28 |
| NUT-05 | Food master và NutrientValue phải có nguồn (`source`, `source_version`); AI không tạo/sửa dữ liệu này | CONFIRMED | Report 1 |
| NUT-06 | Tính dinh dưỡng và định lượng là tính toán xác định từ dữ liệu đã lưu | CONFIRMED | Report 1 |
| NUT-07 | Lượng thực phẩm = số trẻ × gam/trẻ của món theo nhóm tuổi (NUT-18), cộng hệ số thải bỏ. Dùng 2 số trẻ: sĩ số dự kiến khi công bố thực đơn (đặt hàng tươi, trừ kho dự kiến) và số suất đã xác nhận mỗi ngày (điều chỉnh kho — NUT-11). Sĩ số dự kiến lấy trước bao lâu: P-24 | CONFIRMED / PENDING | Report 1; khảo sát team 27/10 (MNU-13, KIT-01, KIT-04), user chốt 2026-10-28 |
| NUT-08 | Thực đơn do PHT phụ trách dinh dưỡng (AUTH-20) lập và công bố; công bố là duyệt, không cần HT duyệt. Chưa đạt cân đối dinh dưỡng thì chặn công bố. Đã công bố thì không sửa. AI chỉ tạo phương án tham khảo | CONFIRMED | Report 1, guide 13.3; khảo sát team 27/10 (MNU-01, MNU-05, MNU-10) |
| NUT-09 | Gợi ý/chọn thực đơn phải kiểm tra dị ứng, dữ liệu bắt buộc, ngân sách bằng logic xác định (không chỉ prompt); không gửi danh tính trẻ cho AI | CONFIRMED | Brief, guide 13.3 |
| NUT-10 | Phân loại nguyên liệu Fresh (NCC giao hằng ngày) / Stored (kho khô) | CONFIRMED | Khảo sát |
| NUT-11 | Kho khô mỗi cơ sở để hàng riêng, quản lý chung, theo lô (số lô, hạn dùng, giá, NCC). PHT nhập phiếu nhận trên Web; bếp không dùng Web. Công bố thực đơn ⇒ trừ dự kiến (NUT-07); mỗi ngày chốt suất ⇒ điều chỉnh chênh lệch. Bếp nhận đúng lượng theo thực đơn, không nhận thừa/thiếu. Không chuyển liên cơ sở, không kiểm kê. Phạm vi Capstone: nhập kho, trừ tự động, xem tồn. Chọn lô khi trừ, kho không đủ khi công bố: P-25 | CONFIRMED (đóng P-08) | User chốt 2026-10-21 (lô, không chuyển, không kiểm kê); khảo sát team 27/10 (KIT-03, KIT-04, KIT-05, KIT-06, KIT-12, ORG-01); user chốt 2026-10-28 (trừ dự kiến + điều chỉnh, thay phiếu xuất theo ngày và bước bếp chọn lô) |
| NUT-12 | Hàng tươi đặt theo sĩ số dự kiến; sau giờ chốt PHT báo NCC tăng/giảm. Bếp kiểm hàng rồi báo PHT (ngoài hệ thống); PHT ghi đủ/thiếu/thừa trên hệ thống 2 lần: so với thực đơn dự kiến, rồi so với thực đơn đã công bố. Không cộng kho. Danh mục NCC dùng chung hai cơ sở: tên, SĐT, địa chỉ, CCCD, người giao hàng (CCCD là dữ liệu cá nhân) | CONFIRMED (đóng P-09) | User chốt 2026-10-21; khảo sát team 27/10 (KIT-01, KIT-02, KIT-07, ORG-01); user chốt 2026-10-28 (thay "bếp ghi, PHT duyệt" và "không có danh mục NCC") |
| NUT-13 | Một thực đơn dùng chung hai cơ sở. Nhà trẻ khác mẫu giáo; các lứa mẫu giáo cùng món, khác định lượng. Lập theo bộ 4 tuần xoay vòng cả tháng/học kỳ, có bộ theo mùa | CONFIRMED (đóng P-07) | Khảo sát team 27/10 (MNU-02, MNU-03, ORG-01) |
| NUT-14 | ~~Trạng thái chuẩn bị bữa (chờ nấu → đang nấu → xong → đã giao)~~ — không theo dõi trạng thái nấu | REMOVED | Khảo sát team 27/10 (MEA-07) |
| NUT-15 | ~~Bàn giao suất có GV xác nhận số nhận~~ — không ghi nhận bàn giao; bếp chia suất theo danh sách lớp | REMOVED | Khảo sát team 27/10 (MEA-06, ALG-04) |
| NUT-16 | Bếp chụp ảnh mẫu mỗi bữa trên App, dùng chung toàn trường, lưu qua `StorageService`; phụ huynh xem ảnh trong thực đơn ngày (PAR-01) | CONFIRMED | Roadmap; khảo sát team 27/10 (MEA-08, MNU-12) |
| NUT-17 | HT cấu hình số bữa/ngày theo nhóm tuổi. Bếp xem số suất đã xác nhận theo lớp, từng bữa | CONFIRMED | Khảo sát team 27/10 (MEA-02, MEA-04) |
| NUT-18 | Cân đối theo nhu cầu dinh dưỡng của chương trình giáo dục mầm non (Bộ GD&ĐT); thành phần thực phẩm theo Viện Dinh dưỡng. Món = công thức nguyên liệu + gam/trẻ theo nhóm tuổi. Lượng mua cộng hệ số thải bỏ theo bảng chuẩn. Danh mục thực phẩm/món ban đầu do team chuẩn bị, trường bổ sung | CONFIRMED (đóng P-10) | Khảo sát team 27/10 (MNU-04, MNU-06, MNU-07, MNU-08) |
| NUT-19 | Giá suất ăn/ngày theo nhóm tuổi và giá thực phẩm để kiểm soát chi phí thực đơn; vượt giá suất thì cảnh báo. Không thu tiền. Báo cáo tháng số suất và số ngày ăn từng trẻ, tính theo điểm danh | CONFIRMED | Khảo sát team 27/10 (SCO-04, MNU-09, LEA-06) |
| NUT-20 | Cảnh báo khi món lặp trong tuần (căn cứ quy định của Bộ; văn bản cụ thể chưa ghi) | CONFIRMED | Khảo sát team 27/10 (MNU-11) |
| NUT-21 | Bếp ghi sổ lưu mẫu thức ăn mỗi bữa trên App. Trường cần ghi theo mẫu sổ nào: P-26 | CONFIRMED | ADR-0009; khảo sát team 27/10 (KIT-08); khảo sát trường 18/10 (#10) |
| NUT-22 | Bếp thiếu nguyên liệu, hoặc thiếu/thừa suất thực tế: báo PHT qua Zalo/điện thoại; hệ thống không ghi | CONFIRMED | Khảo sát team 27/10 (KIT-11, MEA-09) |
| NUT-23 | Dị ứng 2 mức (HLT-06): nhẹ ⇒ trẻ bỏ món có chất dị ứng, tăng khẩu phần món khác; nặng ⇒ PHT chọn món thay thế cho món có chất dị ứng, hệ thống gợi ý món không chứa chất đó. Bếp xem số suất dị ứng/thay thế theo món và lớp, không thấy tên trẻ (AUTH-05). GV không cần cảnh báo dị ứng khi chia ăn vì bếp chia | CONFIRMED | Khảo sát trường 18/10 (#9); khảo sát team 27/10 (ALG-01, ALG-02, ALG-04); user chốt 2026-10-28 (2 mức) |

## HLT — Sức khỏe (module: health)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| HLT-01 | Đo định kỳ chiều cao, cân nặng, tình trạng sức khỏe, ghi chú; mặc định 3 lần/năm, chu kỳ cấu hình được (không hard-code) | CONFIRMED | Khảo sát; khảo sát team 27/10 (HLT-01), user chốt 2026-10-28 (thay "khoảng 3 tháng/lần") |
| HLT-02 | Trend/biểu đồ tính xác định từ measurement đã lưu | CONFIRMED | Report 1 |
| HLT-03 | Ngưỡng tham chiếu tăng trưởng theo chuẩn WHO (biểu đồ tăng trưởng) | CONFIRMED (đóng P-13) | Khảo sát team 27/10 (HLT-04) |
| HLT-04 | AI chỉ giải thích dữ liệu, highlight trend, gợi ý điểm cần chú ý; **không** chẩn đoán bệnh/rối loạn phát triển, **không** khuyên điều trị | CONFIRMED | Report 1 |
| HLT-05 | AI interpretation là DRAFT: giải thích ngắn cho GV, cần nhân viên duyệt trước khi hiển thị chính thức; phụ huynh chỉ thấy bản đã duyệt | CONFIRMED | Report 1; khảo sát team 27/10 (AI-06) |
| HLT-06 | Dị ứng là thông tin cơ bản của trẻ (module child): phụ huynh khai khi nhập học, PHT chuyên môn ghi vào hồ sơ (CHD-01); nhà trường bổ sung khi các đợt khám sức khỏe phát hiện thêm. Chất gây dị ứng chọn từ danh mục cố định (sữa, trứng, hải sản, đậu phộng…) và mục "khác"; PHT thêm được mục. Mỗi dị ứng có mức nhẹ/nặng (NUT-23). Không có chế độ ăn đặc biệt khác | CONFIRMED (đóng P-17) | Brief; khảo sát team 27/10 (ALG-03, ALG-05, ALG-06), user chốt 2026-10-28 (mức) |
| HLT-07 | Kiểm tra sức khỏe đầu ngày: khi nhận trẻ, GV quan sát và đo nhiệt độ mọi trẻ bằng nhiệt kế, nhập cùng màn điểm danh (App hoặc Web). Không chụp ảnh trẻ; dặn dò miệng của phụ huynh không ghi. Danh sách triệu chứng: P-21; trẻ có dấu hiệu bất thường có nhận không: P-22; mức ưu tiên Capstone: P-23 | CONFIRMED / PENDING | Khảo sát team 27/10 (ARR-01, ARR-02, ARR-04, ARR-06, ARR-07); Review 1 11/10 |
| HLT-08 | Bác sĩ khám sức khỏe định kỳ 2 lần/năm (đợt khám) | CONFIRMED | Khảo sát team 27/10 (HLT-02) |
| HLT-09 | GV cân đo và nhập số đo cho lớp mình | CONFIRMED | Khảo sát team 27/10 (HLT-03) |
| HLT-10 | Hệ thống tự phân loại kết quả cân đo theo chuẩn WHO (HLT-03); GV hoặc y tế trường được điều chỉnh kết quả phân loại, bắt buộc ghi lý do. Y tế trường chưa có vai trò trong hệ thống (USER_ROLES) | CONFIRMED | Khảo sát team 27/10 (HLT-05) |
| HLT-11 | Phụ huynh thấy kết quả cân đo sau khi GV hoặc PHT công bố | CONFIRMED | Khảo sát team 27/10 (HLT-06) |
| HLT-12 | Trẻ suy dinh dưỡng/thừa cân chỉ được đánh dấu; không có quy trình theo dõi, can thiệp riêng | CONFIRMED | Khảo sát team 27/10 (HLT-07, PER-07) |
| HLT-13 | Lưu từng lần đo; xu hướng tính từ dữ liệu đã lưu (HLT-02). Sửa số đo phải ghi lý do, lưu lịch sử | CONFIRMED | Khảo sát team 27/10 (HLT-08, HLT-09) |
| HLT-14 | Sức khỏe trong ngày: GV ghi triệu chứng và cách xử trí, xử lý tùy mức độ; phụ huynh nhận thông báo ngay. Tai nạn nhỏ (ngã, trầy xước) ghi vào ghi chú và báo phụ huynh. Trẻ về sớm: PCK-04. BGH xem tổng hợp: RPT-01 | CONFIRMED | Khảo sát team 27/10 (DHL-01, DHL-02, DHL-04) |

## OBS — Đánh giá, nhận xét, hồ sơ phát triển, khen thưởng (module: learning-observation)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| OBS-01 | Đánh giá hằng ngày cho mọi trẻ có mặt (trẻ vắng tự bỏ qua): mặc định "bình thường", GV chỉ sửa trẻ khác thường; nhập theo tiêu chí có cấu trúc, có ô nhận xét không bắt buộc | CONFIRMED | Report 1; khảo sát team 27/10 (DAS-01, DAS-04, DAS-07, DAS-10) |
| OBS-02 | Các mặt đánh giá ngày: ăn uống, ngủ, cảm xúc, hành vi/quan hệ với bạn, tham gia hoạt động, kiến thức–kỹ năng theo bài học, sức khỏe. Mức: Đạt / Cần cố gắng / Chưa đạt. Tiêu chí cố định theo chương trình của Bộ hay trường cấu hình: P-11 | CONFIRMED / PENDING | Report 1; khảo sát team 27/10 (DAS-02, DAS-03, DAS-09) |
| OBS-03 | Hồ sơ phát triển là view liên tục theo trẻ, gồm: điểm danh, sức khỏe và tăng trưởng, đánh giá ngày, nhận xét định kỳ, khen thưởng. Không có mục theo dõi/can thiệp riêng | CONFIRMED | Report 1; khảo sát team 27/10 (PER-06, PER-07) |
| OBS-04 | Nhận xét có mẫu nền không dùng AI; AI chỉ là tùy chọn tạo nháp. AI tắt/lỗi ⇒ dùng mẫu nhận xét điền số liệu sẵn | CONFIRMED | Report 1; khảo sát team 27/10 (AI-08) |
| OBS-05 | Nhận xét định kỳ: AI tạo nháp từ dữ liệu hằng ngày (tùy chọn) → GV sửa → GV xác nhận là xong; BGH xem, không có bước duyệt; phụ huynh nhận. AI draft không tự thành đánh giá chính thức | CONFIRMED | Report 1; khảo sát team 27/10 (PER-03, PER-04, PER-05) |
| OBS-06 | Nhận xét định kỳ theo trẻ: tuần, tháng, học kỳ, cuối năm; theo mẫu riêng của trường (P-30); phụ huynh nhận tất cả các kỳ | CONFIRMED (đóng P-12) | Review 24/09; khảo sát team 27/10 (PER-01, PER-02, PER-05) |
| OBS-07 | Ngữ cảnh hoạt động lấy từ kế hoạch ngày trong CareNest (EDU-01); đánh giá kiến thức–kỹ năng theo mục tiêu của hoạt động trong kế hoạch ngày | CONFIRMED (đóng P-01; ADR-0006 SUPERSEDED bởi ADR-0019) | Khảo sát team 27/10 (DAS-05, EDU-09), user chốt 2026-10-29 |
| OBS-08 | ~~CareNest không soạn/nộp/duyệt giáo án (GoKids giữ)~~ — thay bằng nhóm EDU | REMOVED | Khảo sát team 27/10 (SCO-01, EDU-09), user chốt 2026-10-29 |
| OBS-09 | Biểu mẫu, chu kỳ, người lập nhận xét: theo OBS-05, OBS-06; không có follow-up riêng (OBS-03) | CONFIRMED (đóng P-16b) | Khảo sát team 27/10 (PER-01, PER-02, PER-04, PER-07) |
| OBS-10 | Phiếu bé ngoan tuần: trẻ đi học ≥4 ngày và có ≥3 cờ trong tuần; 3 phiếu trong tháng là "xuất sắc". GV cắm cờ theo biểu hiện trong ngày và tự chọn trẻ nhận phiếu (hệ thống không gợi ý); phụ huynh thấy ngay khi GV xác nhận | CONFIRMED | Review 1 11/10; khảo sát team 27/10 (REW-01, REW-02, REW-03, REW-06) |
| OBS-11 | Thuật ngữ: dùng "đánh giá" (assessment), không dùng "feedback" cho nhận xét trẻ | CONFIRMED | Review 24/09 L311-313 |
| OBS-12 | Đánh giá ngày chỉ sửa trong ngày; hết ngày thì khóa | CONFIRMED | Khảo sát team 27/10 (DAS-06) |
| OBS-13 | Phụ huynh xem đánh giá ngày của con sau khi GV lưu | CONFIRMED | Khảo sát team 27/10 (DAS-08, PAR-06) |
| OBS-14 | Khen thưởng cuối năm: GV đề xuất → PHT xét → HT duyệt trên hệ thống. Làm cả phiếu bé ngoan và khen cuối năm trong Capstone: P-31 | CONFIRMED / PENDING | Khảo sát team 27/10 (REW-04, REW-05) |

## EDU — Kế hoạch giáo dục (module: education-plan)

Làm lại trên `dev` (ADR-0019), thay OBS-08. Rule nhánh G94-226 (EDU-01..09, Tiền 22/10) đã đối chiếu: phần không mâu thuẫn giữ nguyên ý, ghi ở nguồn.

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| EDU-01 | Chuỗi kế hoạch: mục tiêu năm học (theo nhóm tuổi) → chủ đề nhánh → chủ đề chung của tuần → kế hoạch các ngày trong tuần | CONFIRMED | Khảo sát team 27/10 (EDU-09), user chốt 2026-10-29 |
| EDU-02 | Mục tiêu năm học: mỗi (năm học, nhóm tuổi) một bộ; tổ trưởng của nhóm tuổi soạn (cùng PHT chuyên môn); PHT được phân công duyệt và ban hành; trả lại phải ghi lý do | CONFIRMED | Khảo sát team 27/10 (EDU-01, EDU-06); G94-226 EDU-01, EDU-05 |
| EDU-03 | GV lập chủ đề nhánh và kế hoạch giáo dục theo chủ đề nhánh cho lớp mình; gửi duyệt GV → tổ trưởng chuyên môn của nhóm tuổi → PHT được phân công. Tổ trưởng được duyệt kế hoạch của chính lớp mình | CONFIRMED | Khảo sát team 27/10 (EDU-01, EDU-03, EDU-12) |
| EDU-04 | Chỉ duyệt mục tiêu năm học, chủ đề nhánh và kế hoạch giáo dục, duyệt một lần đầu năm; chủ đề tuần và kế hoạch ngày không qua duyệt | CONFIRMED | Khảo sát team 27/10 (EDU-02) |
| EDU-05 | Duyệt là bấm duyệt trên hệ thống, lưu người duyệt và thời điểm; không ký, không ảnh chữ ký | CONFIRMED | Khảo sát team 27/10 (EDU-05, EDU-09) |
| EDU-06 | Soạn theo mẫu có cấu trúc trong hệ thống, kèm tải file Word | CONFIRMED | Khảo sát team 27/10 (EDU-04) |
| EDU-07 | Kế hoạch ngày gồm: đón trẻ/thể dục sáng, hoạt động học, hoạt động ngoài trời, mục tiêu của từng hoạt động, đồ dùng chuẩn bị; GV tự thêm mục. Thời lượng, cách chia chủ đề: P-29 | CONFIRMED / PENDING | Khảo sát team 27/10 (EDU-07, EDU-08) |
| EDU-08 | Bản đã duyệt/ban hành không sửa trực tiếp; cần thay đổi (vd. quy định mới) ⇒ nhân bản thành bản mới để sửa, bản cũ giữ nguyên; bản mới thuộc loại cần duyệt (EDU-04) thì đi lại luồng duyệt | CONFIRMED | Khảo sát team 27/10 (EDU-10); G94-226 EDU-03 |
| EDU-09 | Phụ huynh thấy tên các hoạt động trong ngày của lớp con mình | CONFIRMED | Khảo sát team 27/10 (EDU-11) |
| EDU-10 | Quyền theo mã quyền (ADR-0017); quyền của tổ trưởng là cột "Tổ trưởng" áp cho GV có phân công tổ trưởng còn hiệu lực (AUTH-23); không map cứng theo role (thay map tạm EDU-08 của G94-226) | ACCEPTED | ADR-0017, AUTH-23 |

## FAC — Sự cố cơ sở vật chất (đã loại khỏi Capstone)

Loại theo Report 1 v2 (17/10) và khảo sát team 27/10 (FAC-01, FAC-02), user chốt 2026-10-29. Module card và flow đã xóa; code FE giữ ẩn (`FACILITY_ENABLED=false`). Không implement.

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| FAC-01 | ~~GV báo sự cố thiết bị/CSVC loại: hỏng / thiếu / không đủ, gắn vị trí~~ | REMOVED | Report 1; khảo sát team 27/10 (FAC-01) |
| FAC-02 | ~~BGH trong scope xem, cập nhật trạng thái, theo dõi lịch sử xử lý~~ | REMOVED | Report 1; khảo sát team 27/10 (FAC-01) |
| FAC-03 | ~~Không có khấu hao, mua sắm, lịch bảo trì, kiểm kê, vòng đời tài sản~~ — cả mảng CSVC đã loại | REMOVED | Report 1; khảo sát team 27/10 (FAC-01) |
| FAC-04 | ~~Trạng thái xử lý sự cố~~ | REMOVED | Thiết kế; khảo sát team 27/10 (FAC-01) |

## PAR — Hiển thị cho phụ huynh, thông báo

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| PAR-01 | Phụ huynh xem về con mình: điểm danh hằng ngày; thực đơn tuần/ngày và ảnh suất ăn (NUT-16); ăn, ngủ (đánh giá ngày); sức khỏe trong ngày và xử trí (HLT-14); cân đo và biểu đồ; hoạt động hôm nay (EDU-09); nhận xét định kỳ; phiếu bé ngoan, khen thưởng; thông báo của trường; ai đã đón con (PCK-04) | CONFIRMED | Report 1; khảo sát team 27/10 (PAR-01, MNU-12, EDU-11) |
| PAR-02 | Mặc định không hiển thị; chỉ dữ liệu được policy/nhà trường cho phép mới hiện (default-deny) | ACCEPTED | ADR-0010 |
| PAR-03 | Chỉ hiện cho phụ huynh sau khi được công bố/xác nhận: kết quả cân đo (HLT-11), đánh giá ngày (OBS-13), nhận xét định kỳ (OBS-05), xu hướng sức khỏe (HLT-05), khen thưởng (OBS-10, OBS-14), ảnh hoạt động. Sức khỏe trong ngày báo ngay (HLT-14) | CONFIRMED (đóng P-13b) | Khảo sát team 27/10 (PAR-06, HLT-06, DHL-02, DAS-08, REW-06) |
| PAR-04 | Không có chat thay Zalo. Kênh gửi ý kiến của phụ huynh: P-32 | CONFIRMED / PENDING | Report 1; khảo sát team 27/10 (PAR-04) |
| PAR-05 | Thông báo của trường chỉ BGH gửi; gửi qua thông báo đẩy và trong App; không theo dõi phụ huynh đã đọc | CONFIRMED | Khảo sát team 27/10 (PAR-02, PAR-03, PAR-05) |
| PAR-06 | Một tài khoản phụ huynh xem tất cả các con của mình | CONFIRMED | Khảo sát team 27/10 (PAR-07) |

## RPT — Báo cáo (module: reporting)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| RPT-01 | Báo cáo ngày cho BGH: sĩ số, có mặt, vắng theo lớp và cơ sở (ATT-11); số suất ăn; số trẻ ốm/bất thường theo lớp (HLT-14); việc đang chờ duyệt | CONFIRMED | Khảo sát team 27/10 (RPT-01, DHL-05) |
| RPT-02 | GV viết nhận xét chung về lớp 1–2 câu mỗi ngày; BGH xem trong báo cáo ngày | CONFIRMED | Khảo sát team 27/10 (RPT-02); GVHD 22/10 |
| RPT-03 | Báo cáo định kỳ: số ngày ăn trong tháng từng trẻ (cho kế toán — NUT-19), thực đơn và dinh dưỡng tuần, kết quả đánh giá học kỳ, tổng kết năm học, tồn kho. Xuất Excel và PDF; xem theo cơ sở hoặc toàn trường, trong scope người xem (AUTH-08). Tổng kết năm học làm trong hệ thống tới đâu: P-34 | CONFIRMED / PENDING | Khảo sát team 27/10 (RPT-03, RPT-04, RPT-05, RPT-06) |

## AI — Lớp hỗ trợ AI (module: ai-assistance + module sở hữu nghiệp vụ)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| AI-01 | AI output luôn là DRAFT lưu ở module nghiệp vụ, có `source=AI`, model, thời điểm | ACCEPTED | ADR-0008 |
| AI-02 | Chỉ con người có quyền mới chuyển DRAFT thành chính thức | CONFIRMED | Report 1 |
| AI-03 | Nghiệp vụ phải hoạt động khi AI tắt/lỗi/timeout (baseline không AI) | CONFIRMED | Report 1 |
| AI-04 | Input gửi AI tối thiểu, ẩn danh/bí danh khi phù hợp; không gửi tên, ngày sinh, ảnh trẻ | ACCEPTED | ADR-0007 |
| AI-05 | Không log nội dung prompt/response chứa dữ liệu trẻ; chỉ log metadata (task, model, token, latency, kết quả) | ACCEPTED | ADR-0007 |
| AI-06 | Service nghiệp vụ không gọi trực tiếp SDK provider; chỉ qua `AiClient` trong `integration/` | ACCEPTED | ADR-0007, guide 4 |
| AI-07 | Tính năng AI trong Capstone, đều là nháp có người duyệt (AI-01, AI-02): (1) nháp nhận xét định kỳ tuần/tháng/năm (OBS-05); (2) gợi ý nội dung hoạt động từ mục tiêu khi soạn kế hoạch, GV sửa (EDU); (3) giải thích ngắn xu hướng sức khỏe cho GV (HLT-04, HLT-05); (4) gợi ý thực đơn tuần đơn giản từ danh mục món, kiểm bằng quy tắc, có bật/tắt (NUT-08, NUT-09); (5) nháp báo cáo tổng kết năm — phụ thuộc P-34 | CONFIRMED | Khảo sát team 27/10 (AI-01, AI-05, AI-06, AI-07); GVHD 22/10 |
| AI-08 | Input AI: dữ liệu có cấu trúc, tài liệu tiêu chí của trường, nhận xét tự do của GV; dùng bí danh, chỉ gửi số liệu cần thiết; nhận xét tự do cũng theo AI-04 (không tên, ngày sinh, ảnh trẻ) | CONFIRMED | Khảo sát team 27/10 (AI-02, AI-03) |
| AI-09 | Nhà cung cấp: Gemini API và Groq, gọi qua `AiClient` (AI-06) | CONFIRMED (đóng P-02) | Khảo sát team 27/10 (AI-04) |
| AI-10 | Giọng văn nháp: ngắn gọn, khách quan, hướng tới BGH | CONFIRMED | Khảo sát team 27/10 (AI-09) |

## Pending register

Mỗi mục cần khảo sát. Khi chốt: cập nhật rule liên quan sang CONFIRMED, ghi ngày + nguồn, cập nhật module card và `CURRENT_STATE.md`.

| ID | Câu hỏi | Rule bị chặn |
| --- | --- | --- |
| P-11 | Tiêu chí đánh giá ngày cố định theo chương trình của Bộ hay trường cấu hình? *Đã chốt các mặt và 3 mức (OBS-02). Khảo sát team 27/10 (DAS-09): cố định theo Bộ — "chưa rõ"* | OBS-02 |
| P-16 | System Admin có được xem dữ liệu trẻ không? *Khảo sát team 27/10 (ROL-06): giữ Admin kỹ thuật thuộc bên triển khai, không xem dữ liệu trẻ — team ghi "chưa rõ, cần hỏi thêm"* | AUTH-06 |
| P-18 | Năm học chia mấy học kỳ, có trông trẻ hè không? *Khảo sát team 27/10 (ORG-08): 2 học kỳ, có trông hè — team ghi "chưa rõ, cần hỏi thêm"* | SCH-05 |
| P-19 | Ngày nghỉ theo lịch trường có tự khóa điểm danh không? *Khảo sát team 27/10 (ATT-11): có — "chưa rõ"* | ATT-06, SCH-05 |
| P-20 | Trẻ có đơn nghỉ vẫn đến lớp: GV sửa điểm danh, hay hệ thống khóa điểm danh trẻ đó tới khi phụ huynh hủy đơn? *Khảo sát team 27/10 (LEA-05): chọn GV sửa, ghi chú đề xuất khóa + phụ huynh hủy đơn — "chưa rõ"* | ATT-04 |
| P-21 | Danh sách triệu chứng chọn nhanh khi nhận trẻ? *Nháp (ARR-03, "chưa rõ"): sốt, ho, sổ mũi, tiêu chảy, phát ban/mẩn ngứa, đau bụng, đau mắt. GVHD 22/10: phải lấy từ giáo viên* | HLT-07 |
| P-22 | Trẻ có dấu hiệu bất thường lúc nhận (sốt cao, mệt): trường không nhận, phụ huynh đưa về? *ARR-05: không nhận — "chưa rõ"* | HLT-07 |
| P-23 | Sức khỏe đầu ngày là Must hay Should trong Capstone? *ARR-08: Should — "chưa rõ"* | HLT-07 |
| P-24 | Sĩ số dự kiến lấy trước bao lâu (1 tuần hay 1 tháng) để đặt hàng và trừ kho dự kiến? *MNU-13 — "chưa rõ"* | NUT-07, NUT-11 |
| P-25 | Khi trừ kho lấy lô nào (đề xuất: lô gần hết hạn trước)? Kho không đủ lúc công bố thực đơn thì xử lý thế nào? | NUT-11 |
| P-26 | Mỗi ngày bếp dùng những giấy tờ nào; sổ lưu mẫu ghi theo mẫu nào (khảo sát trường 18/10: 2 mẫu)? *KIT-09 — "chưa rõ"* | NUT-21 |
| P-27 | Kiểm thực ba bước (trước, trong, sau chế biến) có ghi trên hệ thống? *KIT-10: có — "chưa rõ"* | NUT-* |
| P-28 | Một trẻ cần mấy tài khoản phụ huynh (bố và mẹ mỗi người một tài khoản)? *CHD-05 — "chưa rõ"* | AUTH-18 |
| P-29 | Một chủ đề kéo dài bao lâu, chia thế nào? *EDU-07 — "chưa rõ"* | EDU-07 |
| P-30 | Mẫu nhận xét định kỳ riêng của trường: cần xin mẫu (PER-02) | OBS-06 |
| P-31 | Capstone làm cả phiếu bé ngoan và khen thưởng cuối năm? *REW-05: cả hai — "chưa rõ"* | OBS-10, OBS-14 |
| P-32 | Phụ huynh có gửi ý kiến cho trường trên App không? *PAR-04: không, liên lạc qua Zalo/điện thoại — "chưa rõ"* | PAR-04 |
| P-33 | Đối chiếu ảnh người đón: lấy ảnh từ đâu khi phụ huynh không đăng ký người đón trên App? *PCK-04 chọn gọi phụ huynh + so ảnh; PCK-02 chọn không đăng ký trước* | PCK-02 |
| P-34 | Báo cáo tổng kết năm học làm trong hệ thống tới đâu (số liệu theo mẫu, AI nháp)? *RPT-04 — "chưa rõ"* | RPT-03, AI-07 |
| P-35 | Chi phí vận hành trường chấp nhận? *OPS-03: dưới 500 nghìn/tháng — "chưa rõ"* | `docs/quality/NFR.md` |

## Đã đóng

| ID | Kết luận | Ngày | Nguồn |
| --- | --- | --- | --- |
| P-15 | Đơn nghỉ = thông báo của phụ huynh qua app, không duyệt (ATT-05) | 2026-10-02 | Team, review 24/09 |
| P-14 | PHT gắn cơ sở được phân công, mỗi người một mảng (dinh dưỡng / chuyên môn); xem cơ sở khác khi HT cấp quyền, chỉ xem (AUTH-02, AUTH-20) | 2026-10-27 | Khảo sát team 27/10 (ROL-01, ROL-03), user chốt |
| P-03 | Một giờ chốt chung toàn trường, HT cấu hình theo mùa, có ngày áp dụng; chốt điểm danh là chốt suất cả ngày (ATT-06) | 2026-10-28 | Khảo sát team 27/10 (ATT-02, ATT-03); GVHD 22/10; khảo sát trường 18/10 (#2) |
| P-04 | Sau giờ chốt trường không nhận trẻ đến muộn, không bổ sung suất; thay đổi sau xác nhận qua mở lại điểm danh + xác nhận lại (ATT-03, ATT-08, NUT-04) | 2026-10-28 | Khảo sát team 27/10 (ATT-04, ATT-05, ATT-06), user chốt |
| P-05 | PHT đang phân công tại cơ sở xác nhận số suất cơ sở đó; HT thay khi vắng (NUT-02) | 2026-10-28 | Khảo sát team 27/10 (ROL-02, MEA-05) |
| P-06 | Mỗi cơ sở có bếp riêng, tổ bếp dùng chung một tài khoản; kho khô quản lý chung, hàng để riêng từng cơ sở (AUTH-05, AUTH-22, NUT-11) | 2026-10-28 | Khảo sát team 27/10 (ROL-11, ORG-01); khảo sát trường 18/10 (#4) |
| P-07 | Một thực đơn chung hai cơ sở; nhà trẻ khác mẫu giáo; bộ 4 tuần xoay vòng (NUT-13) | 2026-10-28 | Khảo sát team 27/10 (MNU-02, MNU-03, ORG-01) |
| P-08 | Kho khô rút gọn: nhập kho, trừ dự kiến khi công bố + điều chỉnh theo suất chốt, xem tồn; không kiểm kê (NUT-11) | 2026-10-28 | Khảo sát team 27/10 (KIT-03..KIT-06, KIT-12), user chốt |
| P-09 | Hàng tươi đặt theo dự kiến; PHT ghi kiểm nhận 2 lần; có danh mục NCC (NUT-12) | 2026-10-28 | Khảo sát team 27/10 (KIT-01, KIT-02, KIT-07), user chốt |
| P-10 | Nhu cầu theo chương trình GDMN của Bộ, thành phần theo Viện Dinh dưỡng; định lượng gam/trẻ theo nhóm tuổi (NUT-18) | 2026-10-28 | Khảo sát team 27/10 (MNU-04, MNU-06) |
| P-17 | Phụ huynh khai dị ứng khi nhập học, nhà trường ghi và bổ sung sau khám; dị ứng có mức nhẹ/nặng (HLT-06, NUT-23) | 2026-10-28 | Khảo sát team 27/10 (ALG-03, ALG-06), user chốt |
| P-01 | Không cần GoKids: kế hoạch giáo dục soạn trong CareNest (EDU-01); ngữ cảnh hoạt động lấy từ kế hoạch ngày (OBS-07) | 2026-10-29 | Khảo sát team 27/10 (SCO-01, EDU-09), user chốt |
| P-02 | Nhà cung cấp AI: Gemini API, Groq (AI-09) | 2026-10-29 | Khảo sát team 27/10 (AI-04) |
| P-12 | Phụ huynh nhận tất cả các kỳ nhận xét (OBS-06) | 2026-10-29 | Khảo sát team 27/10 (PER-05) |
| P-13 | Chuẩn WHO (HLT-03) | 2026-10-29 | Khảo sát team 27/10 (HLT-04) |
| P-13b | Danh sách dữ liệu phụ huynh chỉ thấy sau khi công bố, người công bố (PAR-03) | 2026-10-29 | Khảo sát team 27/10 (PAR-06, HLT-06) |
| P-16b | Nhận xét theo kỳ tuần/tháng/học kỳ/năm, mẫu riêng của trường, GV lập; không có follow-up riêng (OBS-09) | 2026-10-29 | Khảo sát team 27/10 (PER-01, PER-02, PER-04, PER-07) |
