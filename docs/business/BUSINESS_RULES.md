# Business Rules

Mỗi rule có **ID** (dùng trong code comment, test name, PR) và **Status**. Agent chỉ implement rule `CONFIRMED`/`ACCEPTED`. Rule `PENDING` ⇒ làm cấu hình được hoặc hỏi; không tự quyết.

Status: `CONFIRMED` (khảo sát/Report 1) · `ACCEPTED` (quyết định dự án/ADR đã chấp nhận) · `PROPOSED` · `PENDING` · `OPEN` (ADR chưa chốt).

Khi thêm rule mới: thêm ID kế tiếp trong nhóm, ghi nguồn, cập nhật module card tương ứng.

## AUTH — Phạm vi truy cập (module: identity-access, school-structure, child)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| AUTH-01 | Principal có scope toàn trường (mọi campus) | CONFIRMED | Khảo sát |
| AUTH-02 | Vice Principal có scope theo campus được phân công qua StaffAssignment, không gắn cứng role→campus | CONFIRMED (hướng); cố định hay luân phiên: PENDING P-14 | Khảo sát |
| AUTH-03 | Teacher chỉ truy cập lớp được phân công | CONFIRMED | Report 1 |
| AUTH-04 | Parent chỉ truy cập trẻ có GuardianChildLink với mình, và chỉ dữ liệu được phép hiển thị cho phụ huynh | CONFIRMED | Report 1 |
| AUTH-05 | Kitchen Staff truy cập dữ liệu bếp (suất đã chốt, thực đơn, định lượng) của campus liên quan; không xem hồ sơ trẻ ngoài thông tin dị ứng cần cho nấu | CONFIRMED (hướng); phạm vi bếp PENDING P-06 | Khảo sát |
| AUTH-06 | System Admin quản lý tài khoản/role/cấu hình; mặc định **không** xem dữ liệu trẻ trừ khi được gán thêm role nghiệp vụ | PROPOSED; PENDING P-16 | Thiết kế |
| AUTH-07 | Mọi kiểm tra quyền thực hiện ở BE; UI ẩn nút không phải biện pháp phân quyền | ACCEPTED | ADR-0003 |
| AUTH-08 | Báo cáo/tổng hợp chỉ tính dữ liệu trong scope người xem | ACCEPTED | ADR-0002 |

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

## NUT — Dinh dưỡng, suất ăn, bếp (module: nutrition)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| NUT-01 | Số suất = trẻ **có mặt** và **có đăng ký ăn**; không tính từ sĩ số lớp | CONFIRMED | Report 1, guide 13.1 |
| NUT-02 | MealCount có bước xác nhận bởi người có quyền trước khi chuyển bếp | CONFIRMED; ai xác nhận: PENDING P-05 | FE-01 |
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
| HLT-06 | Dị ứng là thông tin cơ bản của trẻ (module child); nguồn khai báo/người xác nhận PENDING P-17 | CONFIRMED / PENDING | Brief |

## OBS — Quan sát, hoạt động, phát triển (module: learning-observation)

| ID | Rule | Status | Nguồn |
| --- | --- | --- | --- |
| OBS-01 | Quan sát hằng ngày nhập theo tiêu chí có cấu trúc: option định sẵn + ghi chú tùy chọn | CONFIRMED | Report 1 |
| OBS-02 | Tiêu chí quan sát do trường cấu hình (data), không enum cứng; danh sách cụ thể PENDING P-11 | CONFIRMED / PENDING | Report 1 |
| OBS-03 | Hồ sơ phát triển là view liên tục theo trẻ, tổng hợp điểm danh, bữa ăn, sức khỏe, quan sát, hoạt động, đánh giá, follow-up | CONFIRMED | Report 1 |
| OBS-04 | Summary có baseline template không dùng AI; AI chỉ là tùy chọn tạo narrative draft | CONFIRMED | Report 1 |
| OBS-05 | Summary: DRAFT → GV review/sửa → GV APPROVED → BGH xem. AI draft không tự thành đánh giá chính thức | CONFIRMED | Report 1 |
| OBS-06 | Summary theo trẻ hằng **tuần** và **cuối kỳ** (GV soạn/duyệt → gửi BGH); tóm tắt lớp hằng ngày (FE-09). Có gửi phụ huynh không: PENDING P-12 | CONFIRMED (tuần + kỳ); phụ huynh: PENDING | Review 24/09 L102-107 (chỉ đạo giảng viên), Report 1 FE-09 |
| OBS-07 | Nguồn ngữ cảnh hoạt động (nhập tối thiểu / import / GoKids) | OPEN (ADR-0006) | Review 24/09 |
| OBS-08 | CareNest không soạn/nộp/duyệt giáo án (GoKids giữ) | CONFIRMED | Report 1 |
| OBS-09 | Biểu mẫu/chu kỳ assessment & follow-up | PENDING P-16b | — |
| OBS-10 | Khen thưởng "phiếu bé ngoan" (tuần/tháng) và "bé ngoan toàn diện" dựa trên đánh giá hằng ngày; hệ thống chỉ gợi ý từ dữ liệu, GV/BGH quyết định | PROPOSED; tiêu chí PENDING P-11 | Review 24/09 L101, L310; Roadmap (Must) |
| OBS-11 | Thuật ngữ: dùng "đánh giá" (assessment), không dùng "feedback" cho nhận xét trẻ | CONFIRMED | Review 24/09 L311-313 |

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
| P-01 | GoKids có API/export (Excel/PDF) kế hoạch tuần không? Mục tiêu hoạt động theo độ tuổi ghi ở đâu? | OBS-07 |
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
| P-17 | Dị ứng: ai khai báo, ai xác nhận, khi nào cập nhật? | HLT-06 |

## Đã đóng

| ID | Kết luận | Ngày | Nguồn |
| --- | --- | --- | --- |
| P-15 | Đơn nghỉ = thông báo của phụ huynh qua app, không duyệt (ATT-05) | 2026-10-02 | Team, review 24/09 |
