# Project Context — CareNest

Nguồn: xem bảng **Nguồn bằng chứng** cuối file. Tổng hợp 26/09/2026, đối chiếu lại 04/10/2026; stack chốt 03/10/2026. Đọc file này **một lần** khi task ở mức L2+; task L1 dùng module card.

## Quy ước trạng thái thông tin

| Status | Ý nghĩa | Agent được làm gì |
| --- | --- | --- |
| CONFIRMED | Khảo sát/Report 1, **chỉ đạo của giảng viên trong review mà team đã nhận**, hoặc team chốt (có ngày + nguồn) | Dùng làm constraint |
| PROPOSED | Đề xuất (team, roadmap, gợi ý giảng viên chưa được team nhận) | Cân nhắc, không coi là requirement |
| PENDING VALIDATION | Chưa khảo sát đủ | Không suy đoán; để cấu hình được hoặc hỏi |
| OPEN DECISION | Cần quyết định kiến trúc (có ADR) | Không tự chọn; làm theo ADR khi đã chốt |

Khi mâu thuẫn về **nghiệp vụ/scope**: quyết định mới nhất có nguồn (team chốt, chỉ đạo giảng viên đã nhận) > khảo sát / Report 1 > đề xuất > giả định cũ. Report 1 là bản nháp đang được sửa theo review 24/09 — câu trong Report 1 trái chỉ đạo review thì theo review.
Về **code/kỹ thuật**: source đã implement > guide > ADR ACCEPTED > đề xuất.

## Trường và quy mô (CONFIRMED)

```text
Trường Mầm non Thượng Hồng (Hải Phòng) — 1 School
 ├── Campus A ─ classes, teachers, children, kitchen-related operations
 └── Campus B ─ classes, teachers, children, kitchen-related operations
24 lớp · ~506 trẻ · ~60 nhân sự
Sau sáp nhập: 2 HT + 4 HP (6 người)  →  1 HT + 2 HP (3 người)
```

Hệ quả: quản lý tập trung, dữ liệu theo điểm trường, phân quyền theo phạm vi, báo cáo theo lớp / điểm trường / toàn trường. **Không phải multi-school SaaS, không multi-tenant.**

## Vấn đề cần giải (CONFIRMED)

Trường đang dùng song song PMS, GoKids, Zalo, sổ giấy, hệ thống ngành ⇒ workflow phân mảnh, nhập lặp, tổng hợp tay, khó tra lịch sử.

- **A. Điều phối vận hành phân mảnh:** điểm danh, báo ăn, tổng hợp suất ăn, đón trả trẻ, báo cáo nội bộ.
- **B. Thông tin trẻ phân mảnh:** sức khỏe, quan sát hằng ngày, phát triển, hoạt động, đánh giá, theo dõi, cập nhật phụ huynh.

CareNest tạo **hồ sơ có cấu trúc, lấy trẻ làm trung tâm, có lịch sử** — không phải kho tin nhắn/tài liệu rời.

**Định vị (review 24/09, CONFIRMED):** CareNest **bao phủ, cải tiến và mở rộng** các chức năng của PMS/GoKids mà workflow của trường cần, để trường không phải dùng song song nhiều app rời rạc. Ví dụ: thực đơn phải nối liền chọn món → tính dinh dưỡng → định lượng/mua (PMS đang làm nhưng không tách theo điểm trường); quan sát/đánh giá cần ngữ cảnh hoạt động. Không viết "CareNest không trùng/không thay thế PMS/GoKids". Vẫn **không** thay hệ thống ngành/quốc gia và không thay Zalo cho chat. Kế hoạch giáo dục (mục tiêu năm học, chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày) soạn và duyệt trong CareNest theo mẫu MN Thái Dương, nối với đánh giá trẻ theo MT; ngữ cảnh hoạt động lấy từ giáo án đã duyệt (Tiền chốt 22/10; khảo sát team 27/10, user chốt 2026-10-29; rule EDU, OBS-07, module education-plan, ADR-0020, ADR-0006).

## Actor (CONFIRMED)

Principal (Hiệu trưởng), Vice Principal (Hiệu phó), Teacher, Parent, Kitchen Staff, System Admin. **System Admin ≠ Principal**: Admin giữ phần kỹ thuật (tạo tài khoản Hiệu trưởng, khóa/mở, xử lý sự cố), không phân quyền nghiệp vụ (AUTH-06); BGH là người dùng nghiệp vụ. Tổ trưởng chuyên môn là phân công trên tài khoản GV, không phải actor đăng nhập riêng (AUTH-23). Client: Web cho Admin/BGH/GV; App cho phụ huynh, GV, bếp (AUTH-21). Chi tiết: `docs/business/USER_ROLES.md`.

## Tính năng V1 (truy vết Report 1 FE-01..FE-10)

| Report 1 | Tính năng | Status | Module | Flow |
| --- | --- | --- | --- | --- |
| FE-01 | Điểm danh (có mặt là ăn) → số suất PHT cơ sở xác nhận → bếp; một giờ chốt HT cấu hình; PHT mở lại có lý do; xác nhận trả trẻ | CONFIRMED (khảo sát team 27/10, user chốt 28/10) | attendance, nutrition | attendance |
| — | Kiểm tra sức khỏe đầu ngày (nhiệt độ, biểu hiện) cùng màn điểm danh | CONFIRMED (HLT-07); ưu tiên Must/Should PENDING P-23 | health, attendance | health-check |
| — | Báo nghỉ của phụ huynh (không duyệt, GV không tạo thay) → vắng có phép tự động; suất theo điểm danh | CONFIRMED (02/10; khảo sát team 27/10) | attendance | leave-request |
| FE-02 | Thực đơn chung hai cơ sở (bộ 4 tuần xoay vòng), cân đối dinh dưỡng chặn công bố, định lượng, giá suất, dị ứng 2 mức | CONFIRMED (khảo sát team 27/10, user chốt 28/10) | nutrition | meal-management |
| — | ~~Chuẩn bị bếp + bàn giao suất cho lớp~~ — thay bằng ảnh mẫu bữa + sổ lưu mẫu trên App (NUT-16, NUT-21) | REMOVED (NUT-14, NUT-15) | nutrition | meal-management |
| — | Kho khô rút gọn (nhập, trừ dự kiến khi công bố + điều chỉnh theo suất chốt, xem tồn), kiểm nhận hàng tươi do PHT ghi, danh mục NCC | CONFIRMED (ADR-0009, user chốt 28/10) | nutrition | meal-management |
| FE-03 | Cân đo định kỳ (mặc định 3 lần/năm, cấu hình được), phân loại WHO, công bố cho phụ huynh, khám bác sĩ 2 lần/năm, trend, AI giải thích có duyệt; sức khỏe trong ngày báo phụ huynh ngay | CONFIRMED (HLT-01..14; khảo sát team 27/10) | health | health-check |
| FE-04 | Kế hoạch giáo dục: mục tiêu năm học theo nhóm tuổi → chủ đề nhánh → kế hoạch chủ đề → giáo án tuần/ngày, duyệt trong hệ thống, in ký tay | CONFIRMED (Tiền 22/10; khảo sát team 27/10 — EDU-29..38; mục mâu thuẫn code đã chốt theo code 29/10) | education-plan | education-plan |
| — | Đánh giá trẻ hằng ngày theo MT của giáo án đã duyệt (ngữ cảnh hoạt động) | CONFIRMED (Tiền 20/10, ADR-0006) | learning-observation, education-plan | child-observation |
| FE-05 | Đánh giá hằng ngày có cấu trúc: 7 mặt, 3 mức, mặc định "bình thường" | CONFIRMED (OBS-01, OBS-02); tiêu chí P-11 | learning-observation | child-observation |
| FE-06 | Hồ sơ phát triển (không có mục theo dõi riêng) | CONFIRMED (OBS-03) | learning-observation | child-observation |
| — | Phiếu bé ngoan tuần, khen thưởng cuối năm | CONFIRMED (OBS-10, OBS-14); làm cả hai: P-31 | learning-observation | child-observation |
| FE-07 | Phụ huynh xem thông tin được phép (PAR-01), dữ liệu có công bố (PAR-03); thông báo của trường (PAR-05) | CONFIRMED (khảo sát team 27/10) | nhiều module, ADR-0010 | — |
| FE-08 | ~~Báo + theo dõi sự cố CSVC~~ | REMOVED (Report 1 v2, khảo sát team 27/10, user chốt 29/10) | — | — |
| FE-09 | Nhận xét định kỳ theo trẻ tuần/tháng/học kỳ/năm (mẫu nền + AI nháp, GV xác nhận); nhận xét lớp hằng ngày | CONFIRMED (OBS-05, OBS-06, RPT-02) | learning-observation | child-observation |
| FE-10 | AI gợi ý thực đơn có duyệt, có bật/tắt | CONFIRMED (AI-07) | nutrition, ai-assistance | meal-management |
| — | Báo cáo ngày cho BGH; báo cáo định kỳ (số ngày ăn từng trẻ, thực đơn tuần, kết quả học kỳ, tổng kết năm, tồn kho) xuất Excel + PDF; xuất Excel danh sách trẻ, cân đo theo mẫu ngành để trường tự nhập CSDL ngành | CONFIRMED (RPT-01..03; khảo sát team 27/10: SCO-04, SCO-05); tổng kết năm P-34 | reporting | — |
| — | Nhập danh sách trẻ từ Excel để tạo hồ sơ | CONFIRMED (khảo sát team 27/10: SCO-05) | child | — |
| — | Đón trẻ: danh sách phụ huynh trên màn đón, người lạ thì gọi phụ huynh + ghi lý do, lưu người/giờ đón, báo phụ huynh | CONFIRMED (PCK-01..04) | attendance | pickup |

**Ưu tiên MVP (khảo sát team 27/10, SCO-02, SCO-03):** cắt theo lõi "một ngày của trẻ" — đón trẻ, điểm danh, suất ăn, sức khỏe, đánh giá, phụ huynh; phần còn lại làm sau nếu kịp. Team chọn demo chạy thật: điểm danh → số suất → bếp; thực đơn, cân đối dinh dưỡng, định lượng; kho khô; sức khỏe định kỳ và đầu ngày/trong ngày; kế hoạch giáo dục và duyệt giáo án; đánh giá hằng ngày; nhận xét định kỳ có AI hỗ trợ; phiếu bé ngoan; ứng dụng phụ huynh và thông báo; đón trẻ; báo cáo BGH; phân quyền linh hoạt. Tiền: không thu tiền, chỉ giữ giá suất ăn theo nhóm tuổi để kiểm soát chi phí thực đơn (SCO-04). CareNest thay hẳn PMS (thực đơn, dinh dưỡng, kho) và phần duyệt kế hoạch nội bộ đang làm trên eDoc; CSDL ngành giữ riêng (SCO-01); giáo án theo nhóm rule EDU (ADR-0020). Bảng ước tính chi phí vận hành: chưa làm ở đợt này (SCO-06); mức chi phí trường chấp nhận: P-35.

## Exclusions — KHÔNG làm trong V1

Thay thế hệ thống ngành/quốc gia · tích hợp trực tiếp CSDL quốc gia · thay hoàn toàn Zalo (chat) · chẩn đoán y tế · chẩn đoán tâm lý/rối loạn phát triển · AI tự ra quyết định · ký ảnh/ký điện tử trên kế hoạch giáo dục (duyệt trong hệ thống, in ký tay — EDU-21) · kế toán đầy đủ · payroll · quản lý vòng đời tài sản (khấu hao, mua sắm, lịch bảo trì, kiểm kê) · thu tiền ăn · kiểm kê kho thực phẩm · theo dõi trạng thái nấu, ghi nhận bàn giao suất · đơn nghỉ của giáo viên · multi-school enterprise SaaS · kiến trúc production 24/7 · disaster recovery đầy đủ · báo và theo dõi sự cố CSVC (REMOVED 29/10) · đăng ký trước người đón hộ trên App (PCK-03) · theo dõi phụ huynh đã đọc thông báo (PAR-05) · quy trình can thiệp suy dinh dưỡng/thừa cân (HLT-12).

## Ràng buộc dự án

- Team 5 người, SEP490, thời gian giới hạn ⇒ kiến trúc đơn giản đủ dùng.
- Backend: Java 21 + Spring Boot 4.1.1 + PostgreSQL 17, **layered monolith** với ranh giới theo feature (ADR-0001); quy tắc code: `docs/backend-coding-guide.md`.
- Web/Mobile framework: PROPOSED (React/Next.js, React Native).
- 3 repo: CareNest_BE (source of truth), CareNest_FE, CareNest_APP. Không có repo docs riêng.

## Vận hành và kiểm thử (khảo sát team 27/10: OPS)

- Thiết bị: GV dùng điện thoại cá nhân, có thể thêm máy tính (OPS-01). Mạng ở lớp và bếp ổn định (OPS-02).
- Thử nghiệm bằng dữ liệu giả lập, không dùng dữ liệu thật (OPS-04).
- UAT: 1 PHT, 2 GV, 1 bếp dùng thử 1–2 tuần (OPS-05).
- Hướng dẫn sử dụng: buổi hướng dẫn tại trường + tài liệu, và video (OPS-07).
- Hiệu năng, sẵn sàng, sao lưu, giờ cao điểm 7:00–9:00: `docs/quality/NFR.md` (OPS-06, OPS-08). Chi phí vận hành: P-35.

## Quy trình nhóm (khảo sát team 27/10: INT)

- Migration đánh số theo thứ tự merge vào `dev`; giữ số trước khi viết (INT-01). Nhánh G94-226 làm lại trên `dev` (INT-02, ADR-0019) — 29/10 user đã chọn merge `dev` vào nhánh G94-226 thay vì làm lại (migration nhánh dời thành V10–V35, ADR 0017→0020); INT-02 không còn áp dụng (user chốt 29/10, ADR-0019 SUPERSEDED). Phần AI đánh giá trong worktree BE-ai giữ local tới khi xong (INT-03).
- Mỗi người làm song song phần mình (INT-04). Mốc: đóng băng code 30/11 theo Report 2 (INT-08).
- Mã rule BE là chuẩn; cần bảng đối chiếu mã GBR → BE (INT-06, việc riêng). Tranh luận nghiệp vụ: trưởng nhóm chốt sau khi nghe BA (INT-09).
- App tập trung cho phụ huynh và GV; bếp giữ màn tối thiểu (INT-10, AUTH-21). Docs mỗi repo tự cập nhật (INT-11).
- Dữ liệu giả ở web/app lệch BE: khảo sát chọn lấy web làm chuẩn nhưng ghi "chưa rõ" (INT-05). Giữ nguyên tắc BE là chủ contract cho tới khi team chốt lại (user, 29/10).

## Business workflow ≠ System workflow

Nhiều bước do con người làm ngoài hệ thống (đo chiều cao cân nặng, bếp nấu, nhận hàng NCC). Flow trong `docs/business/flows/` đánh dấu `[B]` = bước nghiệp vụ ngoài hệ thống, `[S]` = hành động trong hệ thống. Không biến mọi bước thực tế thành software action.

## Câu hỏi đang chờ khảo sát

Danh sách đầy đủ: `docs/business/BUSINESS_RULES.md` mục "Pending register".

## Nguồn bằng chứng

Dùng mã này ở cột Nguồn của rule. Bản gốc nằm ngoài repo (thư mục dự án của team).

| Mã | Nguồn | Ngày | Lưu ý |
| --- | --- | --- | --- |
| Review 24/09 | Biên bản/transcript họp giảng viên (Memory_Claude/Meeting_20260924) | 24/09/2026 | Transcript máy, có nhiễu; dòng `Lxxx` dẫn theo file transcript |
| Report 1 | G94-Report-1 Project Introduction (bản nháp đang sửa) | ~25/09/2026 | FE-01..10, EX-01..09; một số câu cần sửa theo review 24/09 |
| Report 1 v2 | G94-Report-1 bản v2 | 19/10/2026 | Mốc phạm vi chính thức hiện tại; SRS, Report 2, Report 4 và docs repo cập nhật theo sau (SCO-07) |
| Khảo sát team 27/10 | Bộ khảo sát nghiệp vụ của team (`CareNest_CODE/docs/survey/carenest-business-survey.html`), 210/210 câu: SCO, ORG, ROL (đưa vào docs 27/10); CHD, ARR, ATT, LEA, MEA, MNU, ALG, KIT, HLT-01 (28/10); HLT-02..09, DHL, EDU, DAS, PER, REW, PAR, PCK, RPT, AI, FAC, OPS, INT (29/10). DOC thuộc Report 3/4, ngoài repo | 27/10/2026 | CONFIRMED (team chốt); mục ghi "chưa rõ" giữ PENDING |
| Khảo sát trường 18/10 | Câu hỏi bếp–kho gửi trường, trường trả lời 10 câu (#1..#10) | 18/10/2026 | CONFIRMED (trường trả lời); một số điểm team chọn khác, ghi rõ ở rule |
| Khảo sát | Khảo sát tại trường 21/09 (HT, 1 HP, 3 GV; trước đó 3 PH, 2 bếp) | 21/09/2026 | Biên bản xác nhận chưa ký; số liệu thời gian là ước tính tự báo |
| Roadmap | CareNest_Roadmap_15W_SEP490.xlsx | 26/09/2026 | Kế hoạch team — chỉ là PROPOSED |
| BP-BT-03 | Sơ đồ kho + chuẩn bị + bàn giao suất | 02/10/2026 | Flow team mới nhất — PROPOSED |
| Team 02/10 | Quyết định trưởng nhóm (đơn nghỉ, kho) | 02/10/2026 | CONFIRMED (team chốt) |
| Mẫu MN Thái Dương | File kế hoạch giáo dục năm học, chủ đề, tuần, ngày của trường MN Thái Dương | 10/2026 | Mẫu nghiệp vụ và mẫu in của kế hoạch giáo dục |
| Tiền 14–23/10 | Quyết định nghiệp vụ kế hoạch giáo dục (MT liên tục, chuỗi duyệt, bỏ ký ảnh 16/10, UI CareNest_FE là chuẩn 20/10, đưa vào V1 22/10, khóa Đang sửa 23/10) | 14–23/10/2026 | CONFIRMED; tài liệu ở `huong_dan_nghiep_vu/` |
