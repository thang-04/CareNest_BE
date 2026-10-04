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

- **A. Điều phối vận hành phân mảnh:** điểm danh, báo ăn, tổng hợp suất ăn, thông tin cơ sở vật chất, báo cáo nội bộ.
- **B. Thông tin trẻ phân mảnh:** sức khỏe, quan sát hằng ngày, phát triển, hoạt động, đánh giá, theo dõi, cập nhật phụ huynh.

CareNest tạo **hồ sơ có cấu trúc, lấy trẻ làm trung tâm, có lịch sử** — không phải kho tin nhắn/tài liệu rời.

**Định vị (review 24/09, CONFIRMED):** CareNest **bao phủ, cải tiến và mở rộng** các chức năng của PMS/GoKids mà workflow của trường cần, để trường không phải dùng song song nhiều app rời rạc. Ví dụ: thực đơn phải nối liền chọn món → tính dinh dưỡng → định lượng/mua (PMS đang làm nhưng không tách theo điểm trường); quan sát/đánh giá cần ngữ cảnh hoạt động. Không viết "CareNest không trùng/không thay thế PMS/GoKids". Vẫn **không** thay hệ thống ngành/quốc gia và không thay Zalo cho chat. Giáo án: team chốt giữ ở GoKids (OBS-08); ngữ cảnh hoạt động: ADR-0006.

## Actor (CONFIRMED)

Principal (Hiệu trưởng), Vice Principal (Hiệu phó), Teacher, Parent, Kitchen Staff, System Admin. **System Admin ≠ Principal**: Admin quản lý tài khoản/role/cấu hình; BGH là người dùng nghiệp vụ. Chi tiết: `docs/business/USER_ROLES.md`.

## Tính năng V1 (truy vết Report 1 FE-01..FE-10)

| Report 1 | Tính năng | Status | Module | Flow |
| --- | --- | --- | --- | --- |
| FE-01 | Điểm danh + báo ăn → số suất xác nhận → bếp | CONFIRMED | attendance, nutrition | attendance |
| — | Báo nghỉ của phụ huynh (không duyệt) → vắng có phép, hủy suất | CONFIRMED (02/10) | attendance | leave-request |
| FE-02 | Tính số suất/định lượng thực phẩm | CONFIRMED | nutrition | meal-management |
| — | Chuẩn bị bếp + bàn giao suất cho lớp | PROPOSED (BP-BT-03, roadmap Must) | nutrition | meal-management |
| — | Kho thực phẩm, NCC | OPEN (ADR-0009) | nutrition | — |
| FE-03 | Hồ sơ sức khỏe định kỳ (~3 tháng), trend, AI giải thích có duyệt | CONFIRMED | health | health-check |
| FE-04 | Lịch/kế hoạch hoạt động làm ngữ cảnh cho quan sát | OPEN (ADR-0006); không soạn/duyệt giáo án | learning-observation | child-observation |
| FE-05 | Quan sát + đánh giá hằng ngày có cấu trúc | CONFIRMED | learning-observation | child-observation |
| FE-06 | Hồ sơ phát triển + follow-up | CONFIRMED | learning-observation | child-observation |
| — | Phiếu bé ngoan / khen thưởng tuần-tháng | PROPOSED | learning-observation | child-observation |
| FE-07 | Phụ huynh xem thông tin được phép | CONFIRMED (field PENDING) | nhiều module, ADR-0010 | — |
| FE-08 | Báo + theo dõi sự cố CSVC | CONFIRMED | facility-issue | facility-issue |
| FE-09 | Tóm tắt lớp/trẻ (template + AI draft, GV duyệt) | CONFIRMED | learning-observation | child-observation |
| FE-10 | AI gợi ý thực đơn có duyệt | CONFIRMED | nutrition, ai-assistance | meal-management |
| — | Báo cáo lớp / điểm trường / toàn trường, xuất Excel | CONFIRMED (Excel: PROPOSED) | reporting | — |

## Exclusions — KHÔNG làm trong V1

Thay thế hệ thống ngành/quốc gia · tích hợp trực tiếp CSDL quốc gia · thay hoàn toàn Zalo (chat) · chẩn đoán y tế · chẩn đoán tâm lý/rối loạn phát triển · AI tự ra quyết định · kế toán đầy đủ · payroll · quản lý vòng đời tài sản (khấu hao, mua sắm, lịch bảo trì, kiểm kê) · multi-school enterprise SaaS · kiến trúc production 24/7 · disaster recovery đầy đủ · soạn/nộp/duyệt giáo án (GoKids giữ).

## Ràng buộc dự án

- Team 5 người, SEP490, thời gian giới hạn ⇒ kiến trúc đơn giản đủ dùng.
- Backend: Java 21 + Spring Boot 4.1.1 + PostgreSQL 17, **layered monolith** với ranh giới theo feature (ADR-0001); quy tắc code: `docs/backend-coding-guide.md`.
- Web/Mobile framework: PROPOSED (React/Next.js, React Native).
- 3 repo: CareNest_BE (source of truth), CareNest_FE, CareNest_APP. Không có repo docs riêng.

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
| Khảo sát | Khảo sát tại trường 21/09 (HT, 1 HP, 3 GV; trước đó 3 PH, 2 bếp) | 21/09/2026 | Biên bản xác nhận chưa ký; số liệu thời gian là ước tính tự báo |
| Roadmap | CareNest_Roadmap_15W_SEP490.xlsx | 26/09/2026 | Kế hoạch team — chỉ là PROPOSED |
| BP-BT-03 | Sơ đồ kho + chuẩn bị + bàn giao suất | 02/10/2026 | Flow team mới nhất — PROPOSED |
| Team 02/10 | Quyết định trưởng nhóm (đơn nghỉ, giáo án, kho) | 02/10/2026 | CONFIRMED (team chốt) |
