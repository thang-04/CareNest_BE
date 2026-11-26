# Domain spec — Phụ huynh xem thực đơn tuần và ảnh suất ăn của con

Quy trình: [feature-spec-loop](../../.ai/workflows/feature-spec-loop.md). Nguồn: Jira G94-497; `BUSINESS_RULES.md`; user chốt 26/11/2026 (PAR-14).

## 1. Domain Overview
- Business Context: thực đơn và ảnh bữa chỉ nhân sự xem được; màn App phụ huynh chạy dữ liệu giả.
- Business Goals: phụ huynh biết con ăn gì theo ngày, thấy ảnh bữa thực tế, được cảnh báo món có chất con dị ứng.
- Target Users: phụ huynh có con đang học (App).

## 2. Scope
- In Scope: API thực đơn tuần của một con; API xem ảnh bữa; cảnh báo dị ứng.
- Out of Scope: sửa App, Web; giờ bữa; năng lượng; người đăng ảnh; món/suất thay thế (NUT-23); lịch sử cờ báo ăn; chọn năm học; thông báo mới.

## 3. Actors & Roles
- Phụ huynh (`PARENT`): xem dữ liệu của con có liên kết (AUTH-04); mất quyền khi con nghỉ/ra trường (CHD-04).
- Bếp, PHT: tạo ảnh, công bố thực đơn như hiện có; feature này không đổi quyền của họ.
- Server tự suy điểm trường, khối, bữa từ lớp của con; không nhận `campusId`.

## 4. Domain Concepts
- Thực đơn tuần: kế hoạch thứ Hai–thứ Sáu chung toàn trường, theo khối, trạng thái nháp/đã công bố.
- Ảnh bữa: một ảnh mỗi điểm trường, ngày, bữa.
- Giai đoạn học: khoảng con thuộc một lớp trong một năm học.
- Báo ăn: cờ hiện tại của hồ sơ con.
- Khai báo dị ứng: chữ tự do, đối chiếu danh mục khi đọc.

## 5. Domain Relationships
- Phụ huynh n–n con. Con 1–n giai đoạn học; giai đoạn → lớp → điểm trường, nhóm tuổi (khối, bữa).
- Tuần 1–n ngày 1–n món (theo bữa, khối). Ảnh thuộc điểm trường, ngày, bữa. Món → thực phẩm → chất gây dị ứng.

## 6. User Requirements
- US1: xem thực đơn từng ngày trong tuần của con.
- US2: xem ảnh bữa thực tế của điểm trường con.
- US3: được đánh dấu món có chất con đã khai dị ứng.
- FR-1 trả 5 ngày của tuần chọn (mặc định tuần hiện tại). FR-2 giới hạn khối và bữa theo lớp tại ngày. FR-3 kèm ảnh từng bữa và cho tải ảnh. FR-4 đánh dấu dị ứng. FR-5 giải thích ngày trống: chưa công bố, ngày nghỉ, chưa học, không báo ăn.

## 7. Business Rules
PAR-01, PAR-14, AUTH-04, CHD-04, CHD-13, NUT-08, NUT-13, NUT-16, NUT-17, NUT-23, SCH-05 — CONFIRMED; nội dung ở `BUSINESS_RULES.md`.

## 8. Business Processes
- [B] PHT công bố thực đơn; bếp chụp ảnh bữa.
- Main [S]: phụ huynh mở tuần → kiểm liên kết → xác định lớp từng ngày → ghép món, ảnh, cảnh báo → trả.
- Exception: không liên kết/con đã nghỉ ⇒ từ chối; tuần chưa công bố ⇒ không món, không ảnh; ngày chưa học ⇒ đánh dấu; không báo ăn ⇒ trống từ tuần hiện tại; ảnh ngoài quyền ⇒ không tìm thấy.

## 9. State / Lifecycle
Không thêm trạng thái. Phụ thuộc: tuần nháp → đã công bố (không quay lại); công bố rồi thì ảnh và món mới hiện cho phụ huynh. Sửa ngoại lệ (P-39) ⇒ lần đọc sau thấy món mới.

## 10. Business Constraints
- Một trường, hai điểm trường; mỗi điểm trường có ảnh riêng.
- Cờ báo ăn và dị ứng chỉ có giá trị hiện tại ⇒ áp cho cả tuần đã qua theo PAR-14.
- Chỉ đọc; chạy được khi AI tắt.

## 11. Non-Functional Requirements
- Bảo mật: kiểm quyền ở server cho cả ba endpoint; không lộ dữ liệu trẻ khác; không log tên, dị ứng; ảnh không cache.
- Hiệu năng: một lần đọc tuần không truy vấn lặp theo món (nạp món, giai đoạn học, ảnh theo lô).

## 12. Acceptance Criteria
Xem [spec.md](spec.md) (AC-1…AC-9).

## 13. Domain Terminology
Thực đơn tuần (`MealPlanWeek`), bữa (`MealSession`), khối (`MenuBlock`), ảnh bữa (`MealPhoto`), giai đoạn học (`ChildClassStay`), báo ăn (`mealRegistered`). Xem `docs/context/GLOSSARY.md`.

## 14. Assumptions & Open Questions
- Giả định kỹ thuật: ngày chuyển lớp trùng hai giai đoạn thì lấy lớp bắt đầu sau; `weekStart` giữa tuần đưa về thứ Hai.
- Câu hỏi nghiệp vụ mở: không còn (5 câu chốt 26/11/2026).
