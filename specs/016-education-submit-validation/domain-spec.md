# Domain spec — Điều kiện gửi duyệt kế hoạch giáo dục

> **Quyết định user10/11/2026 — duyệt hồ sơ lớp:** Theo chuỗi HT cấu hình, không bắt buộc có bước TT; chỉ PHT duyệt cũng hợp lệ. Giữ cấp trên duyệt cấp dưới, chống tự duyệt, phạm vi phân công và REVIEW cấm sửa. Quyết định này thay yêu cầu TT bắt buộc trước PHT/HT trong tài liệu09/11; chưa phải bằng chứng triển khai/nghiệm thu.

Nguồn: user trực tiếp ngày 09/11/2026 và ba câu trả lời làm rõ; delta này chưa được xác nhận là giáo viên trường đã duyệt. Nghiệp vụ nền: [xác nhận 09/11](../../docs/business/education-plan-confirmation-2026-11-09.md), [flow](../../docs/business/flows/education-plan.md), [module](../../docs/modules/education-plan.md), [BUSINESS_RULES](../../docs/business/BUSINESS_RULES.md). Đây là đặc tả đích, không phải chứng nhận đã triển khai.

## 1. Domain Overview
Kế hoạch năm từng gửi được dù chủ đề vượt giới hạn. Trường cần thông báo và chặn gửi thiếu điều kiện; tổ cần phân bổ mục tiêu trước khi gửi.

## 2. Scope
Kiểm gửi năm/tổ, báo lỗi và chỉ dấu trường bắt buộc, lưu nháp; khôi phục việc áp đúng giới hạn đã gắn hồ sơ; áp dụng duyệt theo cấp bậc, phân công một người soạn và bỏ lease 90/20 theo EDU-70. Ngoài phạm vi: tự xóa/đổi trạng thái hồ sơ đã gửi, sửa dữ liệu trực tiếp, gửi duyệt nhánh lớp riêng.

## 3. Actors & Roles
Người được chỉ định soạn năm/tổ gửi trong quyền và phạm vi; HT cấu hình giới hạn; người duyệt theo thứ bậc cố định EDU-70. Kiểm quyền thực hiện ở phía quyết định trạng thái, không cấp quyền cho cấp dưới duyệt cấp trên hoặc tự duyệt.

## 4. Domain Concepts
Năm học, chủ đề lớn, mẫu nhánh năm, tuần học/đợt nghỉ, nhóm kết quả mong đợi, STT, mục tiêu, nội dung, tổ, phân bổ tick nội dung–chủ đề.

## 5. Domain Relationships
Năm có nhiều chủ đề và có thể có mẫu nhánh; chuỗi cây gồm nhóm kết quả → mục tiêu có STT → nội dung. Tổ kế thừa cây năm; mỗi mục tiêu cần ít nhất một nội dung phân bổ tới ít nhất một chủ đề khi gửi.

## 6. User Requirements
Người soạn lưu dở; khi gửi năm mọi chủ đề và mẫu nhánh đã khai báo cần đủ ngày bắt đầu/kết thúc, thấy lỗi khoảng tuần, tối thiểu 9 chủ đề, ít nhất một chuỗi đủ kết quả/STT/mục tiêu/nội dung và mỗi mục tiêu có nội dung. Khi gửi tổ thấy mục tiêu thiếu tick. Các trường bắt buộc có * đỏ. Tổ trưởng thêm/sửa nội dung riêng của tổ; giáo viên phía dưới thấy nội dung tổ theo quyền và trạng thái hiện hành.

## 7. Business Rules
EDU-09 CONFIRMED: năm soạn cây, tổ tick từng nội dung; EDU-65 PROPOSED theo user trực tiếp 09/11: tổ trưởng thêm/sửa nội dung riêng của tổ, thay giới hạn chỉ đọc nội dung của EDU-55/62 trong phạm vi này. EDU-14 CONFIRMED và EDU-64 PROPOSED: nhánh lớp đi cùng hồ sơ chủ đề, không gửi riêng; EDU-22/23 CONFIRMED: lưu tay/nháp; EDU-39/40/41 quy tắc lịch hiện hành; EDU-56 PROPOSED: khoảng thời lượng theo snapshot, không hồi tố. EDU-66 PROPOSED thay phần EDU-52 cho phép gửi thiếu ngày. EDU-67/68 PROPOSED: giữ nội dung tổ khi cùng sửa và báo rà soát; phiên bản năm điều chỉnh dùng cấu hình lúc tạo, snapshot mất khôi phục theo bằng chứng. EDU-69 PROPOSED: ngưỡng 9 chủ đề, chuỗi tối thiểu và bao phủ mỗi mục tiêu. EDU-70 PROPOSED: duyệt theo thứ bậc, một người soạn, bỏ lease 90/20; thay phần mâu thuẫn EDU-02/03/10/54. Các delta trực tiếp user chưa tự gán CONFIRMED của giáo viên.

## 8. Business Processes
[B] Người soạn nhập và lưu nháp → [S] hiển thị dấu bắt buộc/cảnh báo → [B] bấm gửi → [S] kiểm quyền, dữ liệu và thời lượng → đạt thì chuyển chờ duyệt, lỗi thì chỉ phần sai và giữ nháp.

## 9. State / Lifecycle
DRAFT → REVIEW khi mọi điều kiện gửi đạt; lỗi giữ DRAFT. Không tự thay đổi REVIEW/APPROVED đã tồn tại.

## 10. Business Constraints
Giới hạn thời lượng theo hồ sơ; tuần nghỉ trọn không tính, nghỉ lẻ vẫn tính. Mẫu nhánh năm tùy chọn; nếu có phải hợp lệ. Không hồi tố cấu hình. Các ràng buộc hiện hành vẫn áp dụng.

## 11. Non-Functional Requirements
Thông báo dễ xác định đối tượng và lý do; dấu * có ý nghĩa truy cập ngoài màu; bảo toàn dữ liệu khi lỗi; kiểm ở cả giao diện và nơi chuyển trạng thái; không lộ dữ liệu riêng tư.

## 12. Acceptance Criteria
AC-01 (FR-001/002): 5 tuần với giới hạn2–4 bị chặn gửi; 4 tuần qua kiểm thời lượng. AC-02 (FR-003/004): 8 chủ đề hoặc thiếu chuỗi bắt buộc bị chặn, đủ 9 và chuỗi qua kiểm tương ứng, trường bắt buộc có * đỏ. AC-03 (FR-005): mục tiêu không có nội dung tick bị chỉ ra; một nội dung tick cho mỗi mục tiêu qua kiểm. AC-04 (FR-007/008): lưu nháp và snapshot cũ được giữ; lỗi gửi không đổi trạng thái.

## 13. Domain Terminology
“Mục tiêu năm” là kế hoạch năm học; “chủ đề nhánh” trong yêu cầu thời lượng là mẫu nhánh nếu có ở năm, không hàm ý luồng gửi nhánh riêng. “Tick mục tiêu” thực hiện qua tick nội dung thuộc mục tiêu theo EDU-09.

## 14. Assumptions & Open Questions
Theo trả lời user: 9 chủ đề áp dụng năm; mọi chủ đề và mẫu nhánh khai báo phải đủ ngày khi gửi; mỗi mục tiêu năm phải có ít nhất một nội dung khi gửi; mỗi mục tiêu tổ chỉ cần một nội dung tick. Nội dung thêm/sửa thuộc riêng tổ, giáo viên thấy theo quyền và trạng thái hiện hành. Khi năm và tổ cùng sửa nội dung, giữ bản tổ và báo tổ trưởng rà soát. STT tự sinh theo EDU-09; nhãn tùy chọn rỗng về mặc định theo EDU-58; một chuỗi đủ bốn thành phần là mức tối thiểu. Snapshot bị mất/chưa phân loại bị chặn gửi, chỉ khôi phục khi có bằng chứng và giữ trạng thái theo EDU-71; phiên bản điều chỉnh dùng cấu hình hiện tại lúc tạo.
