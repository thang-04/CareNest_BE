# Business Rules

Mỗi rule có **ID** (dùng trong code comment, test name, PR) và **Status**. Agent chỉ implement rule `CONFIRMED`/`ACCEPTED`. Rule `PENDING` ⇒ làm cấu hình được hoặc hỏi; không tự quyết.

Status: `CONFIRMED` (khảo sát/Report 1) · `ACCEPTED` (quyết định dự án/ADR đã chấp nhận) · `PROPOSED` · `PENDING` · `OPEN` (ADR chưa chốt).

Khi thêm rule mới: thêm ID kế tiếp trong nhóm, ghi nguồn, cập nhật module card tương ứng.

## AUTH — Phạm vi truy cập (module: identity-access, school-structure, child)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| AUTH-01 | Principal có scope toàn trường (mọi campus) | CONFIRMED | Khảo sát |
| AUTH-02 | Vice Principal có scope theo campus được phân công qua StaffAssignment, không gắn cứng role→campus. Không giao PHT phạm vi toàn trường (kể cả hồ sơ trẻ — chỉ HT toàn trường) | CONFIRMED (hướng); cố định hay luân phiên: PENDING P-14 | Khảo sát; user chốt 2026-10-28 (D-7 A: không giao PHT toàn trường) |
| AUTH-03 | Teacher chỉ truy cập lớp được phân công. Năm học đã kết thúc: GV xem lại (chỉ xem) lớp mình từng dạy — đã áp cho điểm danh, sức khỏe trong ngày | CONFIRMED | Report 1; Cần 23/10/2026 (xem lại năm cũ) |
| AUTH-04 | Parent chỉ truy cập trẻ có GuardianChildLink với mình, và chỉ dữ liệu được phép hiển thị cho phụ huynh | CONFIRMED | Report 1 |
| AUTH-05 | Kitchen Staff truy cập dữ liệu bếp (suất đã chốt, thực đơn, định lượng) của campus liên quan; không xem hồ sơ trẻ ngoài thông tin dị ứng cần cho nấu | CONFIRMED (hướng); phạm vi bếp PENDING P-06 | Khảo sát |
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
| AUTH-16 | HT phân công PHT/bếp → campus, GV → lớp toàn trường; PHT có quyền phân công (mặc định bật, HT tắt được) chỉ phân công GV/bếp trong campus mình, không phân công PHT. Kết thúc phân công bằng ngày cuối, không xóa; không ai tự phân công cho mình | CONFIRMED | User chốt 2026-10-17 |
| AUTH-17 | HT khóa/mở, cấp lại mật khẩu tạm, xem danh sách PHT/GV/bếp toàn trường; PHT (khi HT bật quyền) chỉ với GV/bếp đang phân công trong campus mình. Không ai thao tác tài khoản HT/Admin (trừ Admin) hay chính mình. Cấp lại mật khẩu tạm hủy mọi phiên đang mở | CONFIRMED | User chốt 2026-10-17 |
| AUTH-18 | Tài khoản phụ huynh tạo kèm liên kết trẻ (không tạo rời): tên đăng nhập = SĐT chuẩn hóa (10 số bắt đầu 0), mật khẩu tạm hệ thống sinh, gửi SMS tới SĐT đăng nhập (không trả về giao diện, không in phiếu), bắt đổi lần đầu. Nhận diện phụ huynh theo CCCD (12 số, bắt buộc, mỗi người một tài khoản); CCCD hoặc SĐT đã là phụ huynh ⇒ chỉ thêm liên kết (giữ mật khẩu, không gửi SMS). Email phụ huynh chỉ để liên hệ, không dùng nhận diện. Tài khoản nhân sự tách riêng, không liên quan SĐT phụ huynh: tên đăng nhập nhân sự không được là SĐT (tạo ⇒ 400), nên SĐT phụ huynh không bao giờ trùng tài khoản nhân sự (thay "SĐT của nhân sự ⇒ từ chối"). HT làm cho mọi trẻ; PHT có quyền (mặc định bật, HT tắt được) chỉ cho trẻ thuộc campus mình (đang học, hoặc chờ xếp lớp có điểm trường tiếp nhận là campus mình — CHD-02). Quan hệ (hồ sơ trẻ và liên kết tài khoản dùng chung): Bố / Mẹ / Ông / Bà / Anh/chị / Người giám hộ (`FATHER`, `MOTHER`, `GRANDFATHER`, `GRANDMOTHER`, `SIBLING`, `GUARDIAN`); `OTHER` "Khác" chỉ còn ở dữ liệu cũ. Mỗi trẻ tối đa 3 phụ huynh | CONFIRMED | User chốt 2026-10-16, 2026-10-17; sửa 2026-10-28 (tài khoản nhân sự tách riêng); sửa 2026-10-28 (user chốt: SMS thay in phiếu, nhận diện theo CCCD, email chỉ để liên hệ, trẻ chờ xếp lớp); sửa 2026-10-28 (user chốt D-5a: 6 loại quan hệ thay 4; D-5b: tối đa 3 phụ huynh) |
| AUTH-19 | Gỡ liên kết phụ huynh–trẻ giữ nguyên tài khoản, phụ huynh mất quyền xem trẻ ngay. Khóa/mở, cấp lại mật khẩu tạm (hệ thống sinh, hủy mọi phiên) cho phụ huynh khi người làm phụ trách campus của ít nhất một trẻ đang liên kết với họ | CONFIRMED | User chốt 2026-10-17 |
| AUTH-20 | Phụ huynh ghi trên hồ sơ trẻ (họ tên, quan hệ, SĐT, CCCD, email) trước khi có tài khoản, trạng thái "Chưa kích hoạt". Lưu form tiếp nhận ⇒ tạo/liên kết tài khoản theo AUTH-18 và gửi SMS ngay, cùng giao dịch với lưu hồ sơ; nhập Excel và Sửa hồ sơ thêm phụ huynh mới ⇒ kích hoạt sau ở màn Tài khoản phụ huynh. Người lưu không có quyền quản lý tài khoản phụ huynh (`PARENT_ACCOUNT_MANAGE`) ⇒ vẫn lưu hồ sơ; phụ huynh chưa có tài khoản chờ kích hoạt, phụ huynh trùng CCCD tài khoản vẫn được liên kết, thông tin theo tài khoản có sẵn — người này không sửa tài khoản (P-18). Không còn thêm phụ huynh kèm tạo tài khoản riêng: bỏ `POST /children/{childId}/guardians`, giữ phần tạo/liên kết tài khoản dùng chung cho kích hoạt | CONFIRMED | User chốt 2026-10-28 (P-18 = A cùng ngày; C-1, C-2, C-4 A) |
| AUTH-21 | Thông tin phụ huynh (họ tên, SĐT, email, CCCD) sửa ở hồ sơ trẻ, không khóa kể cả khi đã có tài khoản; sửa ⇒ cập nhật tài khoản. Đổi SĐT của phụ huynh đã có tài khoản ⇒ tên đăng nhập đổi sang SĐT mới, cấp mật khẩu tạm mới, gửi SMS tới SĐT mới | CONFIRMED | User chốt 2026-10-28 |

## CHD — Hồ sơ trẻ, tiếp nhận (module: child)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| CHD-01 | Tiếp nhận trẻ (form, nhập Excel), mở lại hồ sơ, xếp/chuyển lớp, cho nghỉ học: HT toàn trường (cố định); PHT theo ma trận quyền (AUTH-10, mặc định bật) trong campus mình. Mã quyền: `CHILD_ENROLL` (tiếp nhận, nhập Excel, mở lại hồ sơ), `CHILD_PLACEMENT` (xếp/chuyển lớp, cho nghỉ học), `CHILD_PROFILE_EDIT` (sửa hồ sơ, khai báo sức khỏe), `PARENT_ACCOUNT_MANAGE` (tài khoản phụ huynh) — `docs/business/USER_ROLES.md` | CONFIRMED | User chốt 2026-10-27, 2026-10-28 (mã quyền: D-1 A) |
| CHD-02 | Trẻ tiếp nhận chưa xếp lớp thuộc điểm trường tiếp nhận: người được giao toàn trường chọn điểm trường, PHT một campus dùng campus mình; PHT điểm trường đó xem, sửa, khai báo, xếp lớp, quản lý phụ huynh như trẻ đang học. Có lớp ⇒ điểm trường theo lớp (enrollment); xếp/chuyển sang lớp ở campus khác = chuyển campus, dữ liệu cũ theo lớp tại ngày đó (CMR-04) | CONFIRMED | User chốt 2026-10-28 |
| CHD-03 | Cho nghỉ học: ngày nghỉ, lý do bắt buộc; trẻ thành "Đã nghỉ học", không xóa dữ liệu, phiếu theo dõi sức khỏe đang mở tự đóng. Mở lại hồ sơ cũ (lý do bắt buộc) ⇒ "Chờ xếp lớp", giữ mã trẻ, lịch sử, sức khỏe, tài khoản phụ huynh | CONFIRMED | User chốt 2026-10-23, 2026-10-28 |
| CHD-04 | Trùng hồ sơ = cùng họ tên (giữ dấu; không phân biệt hoa thường, khoảng trắng), ngày sinh và CCCD của ít nhất một phụ huynh. Trùng trẻ đang học/chờ xếp lớp ⇒ chặn ở tiếp nhận, Sửa hồ sơ, nhập Excel; trùng trẻ đã nghỉ ⇒ gợi ý mở lại hồ sơ cũ | CONFIRMED | User chốt 2026-10-23, 2026-10-28 |
| CHD-05 | Hành chính khi tiếp nhận: CCCD/mã định danh trẻ, sinh non (+ tuần thai), dân tộc, đối tượng, nhóm máu, nơi ở; bắt buộc trừ CCCD trẻ và nhóm máu; nguồn chi trả luôn BHYT. Phiếu khám sức khỏe đọc từ hồ sơ | CONFIRMED | User chốt 2026-10-17, 2026-10-22, 2026-10-23, 2026-10-28 |
| CHD-06 | Khai báo sức khỏe khi tiếp nhận: dị ứng (Có / Không có / Chưa cung cấp + danh sách), chế độ ăn, thông tin khác, tiền sử bản thân và gia đình (bắt buộc Có/Không), tiếp xúc người bệnh lao (Có ⇒ bắt buộc ngày gần nhất). Người tiếp nhận (HT/PHT) khai báo và chốt dị ứng ngay khi lưu, bếp dùng luôn; sửa sau ở Khai báo sức khỏe (HT, PHT điểm trường) | CONFIRMED | User chốt 2026-10-14, 2026-10-17, 2026-10-21, 2026-10-28 (đóng P-17) |
| CHD-07 | Khai "Có" tiếp xúc người bệnh lao ⇒ thông báo HT, PHT điểm trường, GV lớp và người lưu; đổi ngày tiếp xúc đã có ⇒ thông báo HT, PHT điểm trường và người lưu. Câu thông báo trung tính, không chẩn đoán | CONFIRMED | User chốt 2026-10-17, 2026-10-21, 2026-10-27, 2026-10-28 |
| CHD-08 | Hồ sơ trẻ luôn đủ thông tin: giới tính, hành chính (CHD-05), khai báo sức khỏe (CHD-06), phụ huynh có CCCD; không có hồ sơ thiếu, không làm chức năng "bổ sung sau". Trẻ có sẵn (seed dev) nhập đủ; bắt đầu dùng thật thì nhập toàn bộ trẻ bằng file Excel đủ thông tin | CONFIRMED | User chốt 2026-10-28 (D-5c B) |
| CHD-09 | Danh sách trẻ theo năm học chọn ở header: trẻ có lớp thuộc năm đó, kèm lớp cuối cùng của trẻ trong năm đó (theo enrollment, CMR-04); trẻ chờ xếp lớp chỉ ở năm đang hoạt động; trẻ nghỉ khi chưa có lớp ở năm của ngày nghỉ. Một ngày thuộc năm học gần nhất đã bắt đầu tính tới ngày đó (kỳ hè tính vào năm vừa qua) | CONFIRMED | User chốt 2026-10-28 (Y-2) |
| CHD-10 | Tiếp nhận, xếp/chuyển lớp chỉ vào lớp của năm học đang hoạt động (lớp năm khác ⇒ từ chối); xếp lớp cho năm sau ở chức năng Xếp lớp năm học mới. Tài khoản phụ huynh không theo năm học | CONFIRMED | User chốt 2026-10-28 (Y-3, Y-5) |

## CFG — Năm học và cấu hình trường (module: school-structure)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| CFG-YR-01 | Dữ liệu thuộc năm học đã kết thúc (CLOSED) chỉ đọc ở mọi module. BE kiểm trước khi ghi bằng `SchoolYearService#checkYearOpen` (dữ liệu gắn năm), `checkClassYearOpen` (theo lớp: đánh giá, xếp lớp), `checkDateYearOpen` (theo ngày: điểm danh, suất ăn, thực đơn); vi phạm ⇒ 409 `School year … is closed`. FE: năm đang chọn ở header đã kết thúc ⇒ ẩn thao tác ghi (`useSchoolYear().yearReadOnly`) | CONFIRMED | Cần chốt 22/10/2026 |
| CFG-YR-02 | Dữ liệu từng ngày lưu kèm lớp + ngày (lớp thuộc năm) ⇒ tự thuộc năm học; không sao chép dữ liệu sang năm mới. Dữ liệu dùng chung (phòng, điểm trường) chỉ xóa mềm khi năm cũ đã dùng để năm cũ vẫn xem được | CONFIRMED | Cần chốt 22/10/2026 |
| CFG-YR-03 | Kích hoạt năm học tự đóng năm đang hoạt động và chuyển trẻ theo bản xếp lớp, trong một giao dịch; chỉ kích hoạt năm sắp diễn ra sớm nhất; kết thúc tay chỉ sau ngày kết thúc năm. Năm mới tạo nối tiếp năm mới nhất (tên +1, bắt đầu sau ngày kết thúc năm đó); xếp lớp chỉ cho năm sắp diễn ra sớm nhất; bản xếp chỉ hợp lệ khi trẻ còn ở đúng lớp nguồn của năm đang hoạt động; lớp năm mới đã có trẻ xếp vào không đổi nhóm tuổi/điểm trường, không hạ sĩ số dưới số trẻ đã xếp, không xóa; kích hoạt kiểm lại sĩ số và nhóm tuổi | CONFIRMED | Cần chốt 22/10/2026; bổ sung 27/10/2026 |
| CFG-CP-01 | Điểm trường ngừng hoạt động / hoạt động lại (Hiệu trưởng): không ngừng được điểm trường chính, điểm còn lớp ở năm đang hoạt động/sắp diễn ra, hoặc điểm còn PHT phụ trách (hôm nay hoặc sắp tới; lỗi nêu tên PHT, gỡ phân công trước); ngừng ⇒ không chọn được khi tạo/sửa lớp, phòng, không sao chép lớp sang năm mới; năm cũ vẫn xem đủ. Xóa chỉ cho điểm trường chưa dùng | CONFIRMED | Cần chốt 22–23/10/2026 |

## ATT — Điểm danh, báo ăn, đơn nghỉ (module: attendance)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| ATT-01 | GV nhập điểm danh theo lớp/ngày cho trẻ đang học trong lớp; chỉ lớp được phân công và ngày học đang mở | CONFIRMED | FE-01, guide 13.1 |
| ATT-01b | Một trẻ chỉ có một trạng thái điểm danh cho một ngày/lớp; gửi lặp không tạo bản ghi trùng (upsert + unique) | ACCEPTED | guide 13.1 |
| ATT-02 | GV nhập tham gia ăn (MealParticipation) cùng lúc với điểm danh | CONFIRMED | FE-01 |
| ATT-03 | Trẻ đến muộn: điểm danh được cập nhật sau lần nhập đầu | CONFIRMED | FE-01 |
| ATT-04 | Báo nghỉ đã gửi (SUBMITTED) ⇒ trẻ được đánh dấu vắng có phép các ngày tương ứng; tham gia ăn = không (hủy suất). GV vẫn chỉnh được nếu trẻ thực tế đến lớp | CONFIRMED (hủy suất); GV chỉnh: PROPOSED | Review 24/09 (flow "Thông báo nghỉ phép và hủy suất ăn"), team 02/10 |
| ATT-05 | Đơn nghỉ là **thông báo, không có bước duyệt**: phụ huynh gửi qua app; GV/BGH trong scope chỉ xem/ghi nhận. GV tạo thay phụ huynh (báo qua điện thoại/Zalo): PROPOSED | CONFIRMED (chốt 02/10, đóng P-15) | Team 02/10, review 24/09 |
| ATT-05b | Báo nghỉ/hủy báo nghỉ cho ngày đã qua cut-off (P-03) hoặc khi số suất đã xác nhận ⇒ xử lý như ATT-07 (đánh dấu thay đổi, xác nhận lại) | PROPOSED | Suy từ ATT-07 |
| ATT-06 | Thời điểm khóa nhập điểm danh/báo ăn trong ngày (cut-off) | PENDING P-03 — để cấu hình, không hard-code | — |
| ATT-07 | Thay đổi điểm danh/báo ăn sau khi số suất đã xác nhận ⇒ số suất bị đánh dấu thay đổi, cần xác nhận lại (NUT-03); attendance không tự sửa số suất | ACCEPTED | ADR-0004, ADR-0005 |
| ATT-08 | Sau giờ chốt (ATT-06) điểm danh và cột tham gia ăn của GV chỉ xem; không có hủy suất sau giờ khóa. Thay đổi chỉ trong lần HT/PHT mở lại ngày ⇒ sổ báo ăn chờ PHT xác nhận lại (ATT-07). Sức khỏe trong ngày vẫn ghi sau giờ chốt | CONFIRMED | Cần chốt 24/10/2026 |
| ATT-09 | Đón trẻ (trả trẻ, gọi phụ huynh) chỉ theo lớp của năm học đang hoạt động, như điểm danh; xem lại từ ngày bắt đầu năm học đang hoạt động tới hôm nay (năm chưa bắt đầu thì không chặn ngày cũ); lớp năm khác hoặc ngày ngoài khoảng ⇒ từ chối | CONFIRMED | User chốt 2026-10-28 (Y-1); FE: `FE:docs/integration/CHILD_ENROLLMENT_BE_SPEC.md` mục 9 |

## NUT — Dinh dưỡng, suất ăn, bếp (module: nutrition)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| NUT-01 | Số suất = trẻ **có mặt** và **có đăng ký ăn**; không tính từ sĩ số lớp | CONFIRMED | Report 1, guide 13.1 |
| NUT-02 | MealCount có bước xác nhận bởi người có quyền trước khi chuyển bếp; HT xác nhận được mọi điểm trường, PHT xác nhận điểm trường mình (lưu vai trò người xác nhận); điều chỉnh số suất chỉ PHT điểm trường | CONFIRMED (Cần chốt 27/10/2026, thay bản 24/10 "HT chỉ xem") | FE-01 |
| NUT-03 | Thay đổi sau khi BGH xác nhận không sửa ngầm số đã xác nhận: lưu lịch sử, đánh dấu đã thay đổi, yêu cầu xác nhận lại, thông báo vai trò cần xử lý; bếp chỉ thấy số đã xác nhận | ACCEPTED | ADR-0005, guide 13.1 |
| NUT-04 | Chi tiết xử lý thay đổi muộn: giờ chốt, ai nhận thông báo, bếp thêm/bớt suất thực tế thế nào | PENDING P-04 | — |
| NUT-05 | Food master và NutrientValue phải có nguồn (`source`, `source_version`); AI không tạo/sửa dữ liệu này | CONFIRMED | Report 1 |
| NUT-06 | Tính dinh dưỡng và định lượng là tính toán xác định từ dữ liệu đã lưu | CONFIRMED | Report 1 |
| NUT-07 | FoodQuantityPlan = MealCount CONFIRMED × định lượng công thức của MealPlan APPROVED | CONFIRMED (luồng); công thức chi tiết PENDING P-10 | Report 1 |
| NUT-08 | Thực đơn (MealPlan) chỉ dùng cho bếp khi được người có quyền duyệt; AI chỉ tạo phương án tham khảo | CONFIRMED | Report 1, guide 13.3 |
| NUT-09 | Gợi ý/chọn thực đơn phải kiểm tra dị ứng, dữ liệu bắt buộc, ngân sách bằng logic xác định (không chỉ prompt); không gửi danh tính trẻ cho AI | CONFIRMED | Brief, guide 13.3 |
| NUT-10 | Phân loại nguyên liệu Fresh (NCC giao hằng ngày) / Stored (kho khô) | CONFIRMED | Khảo sát |
| NUT-11 | Quản lý tồn kho, nhập/xuất kho khô, xuất theo điểm trường | OPEN (ADR-0009) — chưa làm, không tự tạo entity | BP-BT-03, review 24/09 |
| NUT-12 | Quy trình NCC, giá theo hợp đồng, đổi/trả thực phẩm tươi | PENDING P-09 — đề xuất ngoài V1 (ADR-0009 phương án C) | Khảo sát |
| NUT-13 | MealPlan chung toàn trường hay theo campus/nhóm tuổi | PENDING P-07 | — |
| NUT-14 | Chuẩn bị bữa theo campus/ngày/bữa có trạng thái `WAITING_TO_COOK → COOKING → READY_FOR_HANDOVER → HANDED_OVER`; bếp cập nhật, hệ thống chỉ ghi trạng thái (nấu là bước [B]) | PROPOSED | BP-BT-03, review 24/09 L302-305, Roadmap (Must) |
| NUT-15 | Bàn giao suất: hệ thống hiển thị số suất mỗi lớp (từ MealCount CONFIRMED); GV xác nhận số nhận; thiếu/sai ⇒ bếp bổ sung, bàn giao lại; hệ thống ghi hoàn tất | PROPOSED | BP-BT-03 |
| NUT-16 | Ảnh món ăn/bữa ăn do bếp chụp, lưu qua `StorageService` | PROPOSED | Roadmap (Must) |

## HLT — Sức khỏe (module: health)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| HLT-01 | Đo định kỳ chiều cao, cân nặng, tình trạng sức khỏe, ghi chú; khoảng ~3 tháng/lần (không hard-code chu kỳ) | CONFIRMED | Khảo sát |
| HLT-02 | Trend/biểu đồ tính xác định từ measurement đã lưu | CONFIRMED | Report 1 |
| HLT-03 | Ngưỡng tham chiếu tăng trưởng (chuẩn nào) | PENDING P-13 | — |
| HLT-04 | AI chỉ giải thích dữ liệu, highlight trend, gợi ý điểm cần chú ý; **không** chẩn đoán bệnh/rối loạn phát triển, **không** khuyên điều trị | CONFIRMED | Report 1 |
| HLT-05 | AI interpretation là DRAFT, cần nhân viên duyệt trước khi hiển thị chính thức | CONFIRMED | Report 1 |
| HLT-06 | Dị ứng là thông tin cơ bản của trẻ (module child); người tiếp nhận khai báo và chốt khi tiếp nhận, sửa sau ở Khai báo sức khỏe (CHD-06) | CONFIRMED | Brief; user chốt 2026-10-28 (đóng P-17) |
| HLT-07 | Sức khỏe trong ngày ghi được cả sau giờ chốt điểm danh (nhiều lần/ngày); khóa khi trẻ đã được trả cho phụ huynh. Chưa có BE Đón trẻ ⇒ BE khóa hết ngày (chỉ ghi cho hôm nay), FE khóa theo lần trả trẻ ở mock Đón trẻ; có BE Đón trẻ thì BE khóa theo giờ trả trẻ thật | CONFIRMED | Cần chốt 24/10/2026 |
| HLT-08 | Theo năm học chọn ở header: đợt khám thuộc năm học của ngày bắt đầu khám; phiếu theo dõi đang mở luôn hiện, phiếu đã đóng hiện ở các năm phiếu còn mở (ngày thuộc năm như CHD-09) | CONFIRMED | User chốt 2026-10-28 (Y-4) |

## OBS — Quan sát, hoạt động, phát triển (module: learning-observation)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| OBS-01 | Quan sát hằng ngày nhập theo tiêu chí có cấu trúc: option định sẵn + ghi chú tùy chọn | CONFIRMED | Report 1 |
| OBS-02 | Tiêu chí quan sát do trường cấu hình (data), không enum cứng; danh sách cụ thể PENDING P-11 | CONFIRMED / PENDING | Report 1 |
| OBS-03 | Hồ sơ phát triển là view liên tục theo trẻ, tổng hợp điểm danh, bữa ăn, sức khỏe, quan sát, hoạt động, đánh giá, follow-up | CONFIRMED | Report 1 |
| OBS-04 | Summary có baseline template không dùng AI; AI chỉ là tùy chọn tạo narrative draft | CONFIRMED | Report 1 |
| OBS-05 | Summary: DRAFT → GV review/sửa → GV APPROVED → BGH xem. AI draft không tự thành đánh giá chính thức | CONFIRMED | Report 1 |
| OBS-06 | Summary theo trẻ hằng **tuần** và **cuối kỳ** (GV soạn/duyệt → gửi BGH); tóm tắt lớp hằng ngày (FE-09). Có gửi phụ huynh không: PENDING P-12 | CONFIRMED (tuần + kỳ); phụ huynh: PENDING | Review 24/09 L102-107 (chỉ đạo giảng viên), Report 1 FE-09 |
| OBS-07 | Ngữ cảnh hoạt động của đánh giá trẻ = hoạt động và MT trong giáo án tuần/ngày **đã duyệt** của lớp (module education-plan, EDU-19); không import GoKids | CONFIRMED | Review 24/09; Tiền 20/10 |
| OBS-09 | Biểu mẫu/chu kỳ assessment & follow-up | PENDING P-16b | — |
| OBS-10 | Khen thưởng "phiếu bé ngoan" (tuần/tháng) và "bé ngoan toàn diện" dựa trên đánh giá hằng ngày; hệ thống chỉ gợi ý từ dữ liệu, GV/BGH quyết định | PROPOSED; tiêu chí PENDING P-11 | Review 24/09 L101, L310; Roadmap (Must) |
| OBS-11 | Thuật ngữ: dùng "đánh giá" (assessment), không dùng "feedback" cho nhận xét trẻ | CONFIRMED | Review 24/09 L311-313 |

## EDU — Kế hoạch giáo dục (module: education-plan)

CareNest soạn và duyệt toàn bộ kế hoạch giáo dục theo mẫu MN Thái Dương (Tiền chốt 22/10; UI CareNest_FE là chuẩn, Tiền 20/10). Kế hoạch năm làm theo **nhóm tuổi**, áp dụng toàn trường; chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày làm theo **từng lớp**. Sợi dây xuyên suốt là **số MT** của kế hoạch năm. Luồng: `docs/business/flows/education-plan.md`.

### Kế hoạch năm học (mục tiêu năm học) — đã có BE

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| EDU-01 | Mỗi (năm học, nhóm tuổi) có đúng 1 kế hoạch năm học | CONFIRMED | Tiền 22/10 |
| EDU-02 | Người lập là Phó HT, hoặc Tổ trưởng của đúng nhóm tuổi đó; người lập ghi là người soạn (owner). Phó HT và Tổ trưởng nhóm tuổi cùng soạn một kế hoạch | CONFIRMED | Tiền 22/10, 23/10 |
| EDU-03 | Nháp/Bị trả lại: Phó HT và Tổ trưởng nhóm tuổi đều sửa được, mỗi lúc một người (EDU-10); Chờ Phó HT duyệt: chỉ Phó HT sửa; đã ban hành thì khóa sửa | CONFIRMED | Tiền 23/10 |
| EDU-04 | Tổ trưởng của nhóm tuổi (không cần là người tạo) gửi Phó HT duyệt khi Nháp/Bị trả lại, không bị người khác giữ khóa sửa, và có ≥1 mục tiêu (MT) | CONFIRMED | Tiền 23/10 |
| EDU-05 | Phó HT ban hành kế hoạch đang chờ duyệt, hoặc ban hành luôn bản nháp do chính mình soạn; trả lại chỉ khi chờ duyệt và bắt buộc lý do | CONFIRMED | Tiền 22/10 |
| EDU-06 | Xem: Phó HT toàn trường mọi trạng thái; Hiệu trưởng toàn trường trừ Nháp, Đang sửa; Tổ trưởng mọi bản của nhóm tuổi mình; Giáo viên chỉ bản đã ban hành của nhóm tuổi các lớp mình dạy | CONFIRMED | Tiền 22/10, 23/10 |
| EDU-07 | Chỉ Phó HT xóa được, và chỉ khi Nháp (không xóa khi người khác đang sửa) | CONFIRMED | Tiền 23/10 |
| EDU-08 | Quyền kiểm tra theo mã `goals.view`, `goals.edit`, `goals.approve`, `plans.config` (cấu hình); tạm gán theo role tại một chỗ, thay bằng phân quyền động khi BE có | CONFIRMED | Tiền 22/10 |
| EDU-09 | Bảng mục tiêu 5 tầng: lĩnh vực > lĩnh vực con > nhóm kết quả mong đợi > MT > nội dung giáo dục. MT đánh số 1..n liên tục cả năm theo thứ tự lĩnh vực và dòng (không dùng mã YCCĐ); mỗi nội dung tích vào các chủ đề sẽ dạy nó; MT của một chủ đề là kết quả của dấu tích, không nhập riêng | CONFIRMED | Tiền 14/10, 20/10; mẫu MN Thái Dương |
| EDU-10 | Vào trang soạn (hoặc lưu) bản Nháp/Bị trả lại ⇒ trạng thái "Đang sửa" (EDITING) khóa cho người đó; người còn lại chỉ xem, thấy tên người sửa và giờ bắt đầu. Lưu nhiều lần vẫn là Đang sửa; thoát trang soạn mới về Nháp/Bị trả lại như trước. Khóa tự nhả sau 30 phút không lưu (cấu hình `carenest.edu-plan.edit-lock-ttl`). Chờ duyệt không khóa | CONFIRMED | Tiền 23/10 (khảo sát `huong_dan_nghiep_vu/1_muc_tieu_nam_hoc/khao_sat_quy_trinh_lap_ke_hoach_nam.md`) |

### Cấu hình và tài liệu của lớp (đã có BE; AI chưa làm)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| EDU-11 | Cấu hình kế hoạch giáo dục (Hiệu trưởng, Phó HT): tên hiển thị lĩnh vực, thêm lĩnh vực (5 lĩnh vực của Chương trình GDMN không xóa được); chuỗi người ký cho kế hoạch năm, chủ đề nhánh, hồ sơ chủ đề (mặc định Tổ trưởng → Phó HT, thêm được Hiệu trưởng; kế hoạch năm luôn có Phó HT); các dòng hoạt động của kế hoạch tuần (mặc định Đón trẻ, Trò chuyện, Thể dục sáng, Hoạt động học, Chơi HĐ ngoài trời, Chơi HĐ ở các góc, Chơi HĐ buổi chiều, Trả trẻ; dòng nào được gộp cả tuần; không xóa dòng Hoạt động học) | CONFIRMED | Tiền 15/10, 16/10, 20/10 |
| EDU-12 | Tài liệu của lớp chỉ lập khi kế hoạch năm của nhóm tuổi đã ban hành: chủ đề nhánh (1/lớp/năm học), kế hoạch chủ đề (1/lớp/chủ đề), giáo án tuần (1/lớp/tuần nhánh). Giáo viên được phân công vào lớp soạn và gửi | CONFIRMED | Tiền 20/10 |
| EDU-13 | Trình tự: chủ đề nhánh cả năm đã duyệt → lưu kế hoạch chủ đề → giáo án tuần của các nhánh trong chủ đề đó (các nhánh soạn song song). Chưa đủ bước trước thì không mở bước sau | CONFIRMED | Tiền 20/10 |
| EDU-14 | Chủ đề nhánh: mỗi tuần học (đánh số cả năm, bỏ tuần nghỉ) của từng chủ đề có một tên do giáo viên đặt; gửi khi mọi tuần đã có tên và không còn tên AI điền chưa giữ/bỏ | CONFIRMED | Tiền 20/10 |
| EDU-15 | Kế hoạch chủ đề: MT và nội dung lấy sẵn từ kế hoạch năm (chỉ nội dung đã tích vào chủ đề), giáo viên không sửa; giáo viên viết hoạt động giáo dục cho từng MT theo loại giờ (tên dòng của kế hoạch tuần) và môi trường giáo dục (trong lớp, ngoài lớp). Bản lưu giữ cả các dòng lấy từ kế hoạch năm để bản đã duyệt không đổi theo kế hoạch năm | CONFIRMED | Tiền 20/10 |
| EDU-16 | Giáo án tuần = kế hoạch tuần (mục đích, chuẩn bị, bảng dòng hoạt động × Thứ 2–6) + 5 kế hoạch ngày. Ô tuần gắn MT khi chọn (hoặc gõ trùng) hoạt động của kế hoạch chủ đề; mỗi hoạt động chỉ xếp vào 1 ô ngày trong cả chủ đề (ô gộp cả tuần lặp được). MT trong ngày = MT của các hoạt động đã xếp vào ngày đó | CONFIRMED | Tiền 20/10 |
| EDU-17 | Hồ sơ chủ đề = kế hoạch chủ đề + giáo án tất cả các nhánh của chủ đề, gửi **một lần**; chỉ gửi khi chủ đề nhánh đã duyệt và đủ giáo án mọi tuần nhánh. Giáo án tuần không gửi riêng. Hạn gửi là ngày bắt đầu chủ đề, chỉ nhắc, không chặn | CONFIRMED | Tiền 15/10, 20/10 |
| EDU-18 | Chủ đề nhánh và hồ sơ chủ đề duyệt theo chuỗi người ký đã cấu hình, đúng lượt, mỗi bước một người; Tổ trưởng chỉ duyệt lớp thuộc nhóm tuổi mình; bỏ bước Tổ trưởng khi nhóm tuổi không có Tổ trưởng hoặc Tổ trưởng dạy chính lớp đó; chuỗi rỗng thì gửi là duyệt. Trả lại bắt buộc lý do ⇒ Bị trả lại, sửa rồi gửi lại từ đầu chuỗi. Chỉ sửa khi Nháp hoặc Bị trả lại | CONFIRMED | Tiền 15/10, 16/10, 20/10 |
| EDU-19 | Đánh giá trẻ hằng ngày (module learning-observation) chấm theo MT của các hoạt động trong giáo án tuần **đã duyệt** của lớp, theo giờ và ngày; lớp chưa có giáo án duyệt thì khóa phần chấm theo MT. Đánh giá không ghi trong giáo án | CONFIRMED | Tiền 20/10 |
| EDU-20 | AI chỉ tạo bản nháp: gợi ý / viết lại tên chủ đề nhánh, gợi ý tiến trình giờ học (chỉ điền ô trống). Giáo viên giữ hoặc bỏ từng phần; bản dùng AI vẫn qua gửi, duyệt như thường; tắt AI vẫn soạn được (AI-*) | CONFIRMED | Tiền 20/10 |
| EDU-21 | Không ký ảnh hay ký điện tử trong hệ thống: chỉ gửi, duyệt, trả lại. Mọi tài liệu xuất Word / in PDF đúng mẫu MN Thái Dương ở mọi bước, chừa chỗ ký tay; bản in kế hoạch năm in sẵn họ tên người soạn, gửi, ban hành | CONFIRMED | Tiền 16/10 |
| EDU-22 | Lưu tay: nút Lưu hoặc Ctrl+S (không tự lưu); rời trang khi còn thay đổi chưa lưu thì hỏi Hủy / Không lưu / Lưu; xóa một mục (dòng, chủ đề, lĩnh vực, người ký…) phải xác nhận. Quy tắc giao diện, chi tiết ở `CareNest_FE/.claude/rules/year-plan-unsaved.md` | CONFIRMED | Tiền 22/10, 23/10 |
| EDU-23 | Ngày bắt đầu, kết thúc của chủ đề và đợt nghỉ trong kế hoạch năm phải nằm trong năm học của kế hoạch (ngày bắt đầu → ngày kết thúc năm học). Lịch chọn ngày chỉ cho chọn trong khoảng này; BE từ chối lưu nếu sai | CONFIRMED | Tiền 24/10 |

Trạng thái:
- Kế hoạch năm: `DRAFT` (Nháp) ⇄ `EDITING` (Đang sửa) → `PENDING_VP` (Chờ Phó HT ban hành) → `APPROVED` (Đã ban hành) / `REJECTED` (Bị trả lại).
- Chủ đề nhánh, hồ sơ chủ đề: `DRAFT` → `PENDING_TL` / `PENDING_VP` / `PENDING_HT` (theo người ký đang tới lượt) → `APPROVED` / `REJECTED`.

## FAC — Sự cố cơ sở vật chất (module: facility-issue)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| FAC-01 | GV báo sự cố thiết bị/CSVC loại: hỏng / thiếu / không đủ, gắn vị trí (campus/lớp/khu vực) | CONFIRMED | Report 1 |
| FAC-02 | BGH trong scope xem, cập nhật trạng thái, theo dõi lịch sử xử lý | CONFIRMED | Report 1 |
| FAC-03 | Không có khấu hao, mua sắm, lịch bảo trì, kiểm kê, vòng đời tài sản | CONFIRMED (exclusion) | Report 1 |
| FAC-04 | Danh sách trạng thái xử lý cụ thể | PROPOSED: OPEN → IN_PROGRESS → RESOLVED / REJECTED | Thiết kế |

## PAR — Hiển thị cho phụ huynh

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| PAR-01 | Phụ huynh xem nhóm: điểm danh, bữa ăn, sức khỏe, hoạt động hằng ngày, cập nhật phát triển của con mình | CONFIRMED | Report 1 |
| PAR-02 | Mặc định không hiển thị; chỉ dữ liệu được policy/nhà trường cho phép mới hiện (default-deny) | ACCEPTED | ADR-0010 |
| PAR-03 | Field cụ thể và ai quyết định công bố | PENDING P-13b | — |
| PAR-04 | Không có chat thay Zalo | CONFIRMED | Report 1 |

## AI — Lớp hỗ trợ AI (module: ai-assistance + module sở hữu nghiệp vụ)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| AI-01 | AI output luôn là DRAFT lưu ở module nghiệp vụ, có `source=AI`, model, thời điểm | ACCEPTED | ADR-0008 |
| AI-02 | Chỉ con người có quyền mới chuyển DRAFT thành chính thức | CONFIRMED | Report 1 |
| AI-03 | Nghiệp vụ phải hoạt động khi AI tắt/lỗi/timeout (baseline không AI) | CONFIRMED | Report 1 |
| AI-04 | Input gửi AI tối thiểu, ẩn danh/bí danh khi phù hợp; không gửi tên, ngày sinh, ảnh trẻ | ACCEPTED | ADR-0007 |
| AI-05 | Không log nội dung prompt/response chứa dữ liệu trẻ; chỉ log metadata (task, model, token, latency, kết quả) | ACCEPTED | ADR-0007 |
| AI-06 | Service nghiệp vụ không gọi trực tiếp SDK provider; chỉ qua `AiClient` trong `integration/` | ACCEPTED | ADR-0007, guide 4 |

## Pending register

Mỗi mục cần khảo sát. Khi chốt: cập nhật rule liên quan sang CONFIRMED, ghi ngày + nguồn, cập nhật module card và `CURRENT_STATE.md`.

| ID | Câu hỏi | Rule bị chặn |
| --- | --- | --- |
| P-02 | AI provider: external API hay local model? | AI-* (ADR-0007) |
| P-03 | Điểm danh/báo ăn khóa lúc mấy giờ? *AS-IS: bếp nhận số đã xác nhận qua Zalo khoảng 8:30 (Report 1, khảo sát)* | ATT-06 |
| P-04 | Trẻ đến muộn sau khi bếp đã nhận số: báo bếp thế nào, có thêm suất không? *AS-IS: GV báo lại, PHT tổng hợp lại; bếp điều chỉnh, trả bớt hoặc xin thêm qua BGH (khảo sát, review 24/09)* | NUT-04 |
| P-05 | Ai xác nhận số suất (HT, HP điểm trường, nhân viên được giao)? *AS-IS: HP của điểm trường gom, kiểm, xác nhận rồi gửi bếp (Report 1, khảo sát)* | NUT-02 |
| P-06 | Mỗi campus có bếp riêng hay bếp trung tâm? *Team trình bày 24/09: 2 bếp ở 2 điểm trường, 1 kho chung (chưa xác nhận với trường)* | AUTH-05, NUT-* |
| P-07 | MealPlan chung toàn trường hay riêng campus/nhóm tuổi? *Review 24/09: cấu hình thực đơn ở cấp chung (PHT cơ sở chính); PMS tính dinh dưỡng theo nhóm tuổi* | NUT-13 |
| P-08 | Kho khô: có sổ nhập-xuất-tồn? CareNest quản lý tới đâu? Ai quản lý kho chung? *Bằng chứng: review 24/09 gợi ý kho chung ở cơ sở chính, xuất theo điểm trường; BP-BT-03 có luồng kho đầy đủ* | NUT-11, ADR-0009 |
| P-09 | Quy trình NCC: nhận/kiểm/đổi trả thực phẩm tươi, phiếu nhận? | NUT-12 |
| P-10 | Nguồn bảng thành phần dinh dưỡng chuẩn; phần mềm tính khẩu phần hiện dùng? | NUT-05, NUT-07 |
| P-11 | Danh sách tiêu chí quan sát và thang giá trị? | OBS-02 |
| P-12 | Summary có gửi phụ huynh không, gửi bản nào? (tần suất tuần + kỳ đã có — OBS-06) | OBS-06 |
| P-13 | Ngưỡng tham chiếu tăng trưởng (WHO / Bộ Y tế)? *Roadmap đề xuất WHO z-score tính bằng rule* | HLT-03 |
| P-13b | Phụ huynh được xem field nào; ai quyết định công bố? | PAR-03 |
| P-14 | Hiệu phó phụ trách cố định một campus hay luân phiên/theo mảng? *Report 1: mỗi HP chủ yếu điều phối 1 điểm trường* | AUTH-02 |
| P-16 | System Admin có được xem dữ liệu trẻ không? | AUTH-06 |
| P-16b | Assessment/follow-up: biểu mẫu, chu kỳ, người lập? | OBS-09 |

## Đã đóng

| ID | Kết luận | Ngày | Nguồn |
| --- | --- | --- | --- |
| P-01 | Không import GoKids: ngữ cảnh hoạt động lấy từ giáo án tuần/ngày đã duyệt trong CareNest (OBS-07, EDU-19) | 2026-10-20 | Tiền |
| P-15 | Đơn nghỉ = thông báo của phụ huynh qua app, không duyệt (ATT-05) | 2026-10-02 | Team, review 24/09 |
| P-17 | Người tiếp nhận (HT/PHT) khai báo và chốt dị ứng khi tiếp nhận, bếp dùng ngay; sửa sau ở Khai báo sức khỏe (CHD-06, HLT-06) | 2026-10-28 | User chốt |
| P-18 | Người lưu form tiếp nhận có quyền tiếp nhận (CHD-01) nhưng không có `PARENT_ACCOUNT_MANAGE` ⇒ vẫn lưu hồ sơ; phụ huynh chưa có tài khoản chờ kích hoạt (người có quyền kích hoạt sau), phụ huynh trùng CCCD tài khoản vẫn được liên kết (AUTH-20) | 2026-10-28 | User chốt (A) |
