# Project Context — CareNest

Nguồn: Report 1, khảo sát trường, feedback review giảng viên 24/09/2026; tổng hợp 26/09/2026; stack chốt 03/10/2026. Đọc file này **một lần** khi task ở mức L2+; task L1 dùng module card.

## Quy ước trạng thái thông tin

| Status | Ý nghĩa | Agent được làm gì |
| --- | --- | --- |
| CONFIRMED | Có trong Report 1 hoặc khảo sát rõ | Dùng làm constraint |
| PROPOSED | Đề xuất / feedback giảng viên | Cân nhắc, không coi là requirement |
| PENDING VALIDATION | Chưa khảo sát đủ | Không suy đoán; để cấu hình được hoặc hỏi |
| OPEN DECISION | Cần quyết định kiến trúc (có ADR) | Không tự chọn; làm theo ADR khi đã chốt |

Khi mâu thuẫn: **Confirmed requirement > Latest validated decision > Instructor proposal > Previous assumption.**

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

CareNest tạo **hồ sơ có cấu trúc, lấy trẻ làm trung tâm, có lịch sử** — không phải kho tin nhắn/tài liệu rời. CareNest **bổ sung**, không thay thế PMS/GoKids/Zalo và không clone chúng.

## Actor (CONFIRMED)

Principal (Hiệu trưởng), Vice Principal (Hiệu phó), Teacher, Parent, Kitchen Staff, System Admin. **System Admin ≠ Principal**: Admin quản lý tài khoản/role/cấu hình; BGH là người dùng nghiệp vụ. Chi tiết: `docs/business/USER_ROLES.md`.

## Tính năng V1

| Tính năng | Status | Module |
| --- | --- | --- |
| FE-01 Điểm danh + báo ăn → số suất đã xác nhận → bếp | CONFIRMED | attendance, nutrition |
| Đơn xin nghỉ ảnh hưởng điểm danh/suất ăn | CONFIRMED (người tạo/duyệt PENDING) | attendance |
| Thực đơn, dinh dưỡng, định lượng thực phẩm; AI gợi ý thực đơn có duyệt | CONFIRMED | nutrition, ai-assistance |
| Hồ sơ sức khỏe định kỳ (~3 tháng/lần), trend, AI diễn giải có duyệt | CONFIRMED | health |
| Quan sát hằng ngày có cấu trúc; hồ sơ phát triển; summary ngày/tuần/kỳ (template + AI draft, GV duyệt) | CONFIRMED | learning-observation |
| Ngữ cảnh hoạt động/học tập phục vụ đánh giá | OPEN DECISION (ADR-0006) | learning-observation |
| Báo sự cố cơ sở vật chất (hỏng/thiếu/không đủ) → BGH theo dõi | CONFIRMED | facility-issue |
| Phụ huynh xem thông tin được cho phép (điểm danh, bữa ăn, sức khỏe, hoạt động, cập nhật phát triển) | CONFIRMED (field PENDING) | nhiều module, ADR-0010 |
| Báo cáo lớp / điểm trường / toàn trường | CONFIRMED | reporting |

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
