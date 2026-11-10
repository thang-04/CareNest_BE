# Domain spec — Xóa nhánh mục tiêu năm học và select lĩnh vực
Nguồn: user02/11/2026 chấp nhận xóa toàn nhánh có xác nhận counts, yêu cầu ô Lĩnh vực dài hiện ba chấm và vòng spec tới sạch. User sau đó chọn “Duyệt kế hoạch và triển khai tới khi sạch”. Delta của feature006, lịch sử DB29/10 được giữ.
## 1. Domain Overview
Người soạn hiểu lĩnh vực con là cha của nhóm/goal; UI hiện chỉ bỏ heading. Mục đích xóa đúng nhánh và đọc/chọn tên lĩnh vực dài không wrap.
## 2. Scope
Trong scope: xóa nhánh, xác nhận số lượng, regression và ellipsis ô Lĩnh vực lấy từ Bộ. Ngoài scope: sửa dữ liệu trùng cũ, API/schema/dependency/quyền/APP/nguồn Bộ, commit/push.
## 3. Actors & Roles
Người có quyền sửa theo EDU-06/10/22 thao tác trên bản soạn; read-only không có nút xóa. Không thêm quyền client/server.
## 4. Domain Concepts
Kế hoạch năm, lĩnh vực, lĩnh vực con, nhóm kết quả mong đợi, mục tiêu, nội dung, dấu tích gắn nội dung với chủ đề.
## 5. Domain Relationships
Sub chứa nhóm và có thể goal trực tiếp; nhóm chứa goal; goal chứa content; content có nhiều tick. Cha tùy chọn dữ liệu cũ vẫn hợp lệ. Dấu tích không phải chủ đề.
## 6. User Requirements
US4 xóa cả nhánh sau xác nhận tác động; US5 select Lĩnh vực một dòng/ellipsis khi tên dài, giữ giá trị đầy đủ.
## 7. Business Rules
EDU-09/48(CONFIRMED user02/11), EDU-06/10/22, EDU-24/28; source of truth docs/business/BUSINESS_RULES.md.
## 8. Business Processes
[B] quyết định bỏ nhánh → [S] thùng rác/hiển thị tên+counts → [B] Hủy hoặc Xóa → [S] chỉ Xóa đổi bản soạn → [B] Lưu → [S] ghi cây bằng API hiện có.
## 9. State / Lifecycle
Không trạng thái mới; mở/Hủy không đổi form, xác nhận chỉ đổi bản soạn, Lưu mới ghi. Giữ lease/revision/read-only/ban hành.
## 10. Business Constraints
Không xóa hoặc chuyển cha ngoài nhánh. Giữ các chủ đề/đợt nghỉ/nguồn Bộ. Không endpoint xóa cha/migration; giữ PUT cho phép chuyển cha có chủ đích.
## 11. Non-Functional Requirements
Tính đoạn theo ID bằng lượt duyệt hàng; không mutate đầu vào. Bàn phím dùng được.360/390/768/1440 không tràn ngang, tên chọn một dòng/ellipsis và có title đủ chữ.
## 12. Acceptance Criteria
- AC-D1: Nhánh1sub/2group/3goal/4content/5tick, mở xác nhận phải đúng tên/counts, chưa đổi form.
- AC-D2: Hủy giữ100%form.
- AC-D3: Xác nhận xóa đúng đoạn, kể cả goal trực tiếp/nhóm rỗng; giữ ID/text/tick/thứ tự nhánh khác.
- AC-D4: Nhánh đầu/giữa/cuối/duy nhất/rỗng/ID không còn/cùng tên khác ID không xóa nhầm.
- AC-D5: Normalize/numberGoals/themeGoalIds không nhận lại con đã xóa; danh sách chủ đề còn nguyên.
- AC-D6: Read-only không thùng rác; trước Lưu không write request; lease/revision giữ luồng cũ.
- AC-U1: Tên lĩnh vực dài hiển thị một dòng/ellipsis/title/value/option đủ ở4viewport.
- AC-U2: Ô target/bộ lọc/keyboard không hồi quy khi tái dùng cơ chế cắt chữ.
## 13. Domain Terminology
“Cần đạt” trong trao đổi là nhóm kết quả mong đợi; “xóa nhánh” không xóa toàn lĩnh vực hoặc xóa chủ đề.
## 14. Assumptions & Open Questions
Không còn câu hỏi nghiệp vụ; user đã duyệt plan L sau review. Chỉ fixture tổng hợp. Save/reopen server cần runtime/credential kiểm thử; thiếu phải báo và giữ task persistence mở, không dùng mock để nhận sạch.
