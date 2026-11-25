---
id: BUG-261123-child-transfer-history-labels
type: bug
modules: [attendance, observation]
status: fixed
date: 2026-11-23
keywords: [chuyển lớp, nghỉ học, historyStatus, lịch sử, mục tiêu, cô lớp mới]
---

# Nhãn và quyền của trẻ rời lớp

Boolean historical không phân biệt chuyển lớp với nghỉ học; nhiều bảng không trả nhãn. Cờ lớp canEdit cho phép gửi ô của mọi trẻ từng học, không kiểm căn cứ từng mục tiêu. Hồ sơ trẻ lọc khoảng trước chuyển có thể mất quyền của cô lớp nhận.

Dùng lịch enrollment để trả historyStatus, ghép nhãn cạnh tên tại FE. Nhãn động không làm thay đổi snapshot kết quả. Quyết định cuối của user: trẻ chuyển vẫn cho cô lớp cũ hoàn tất MT có hoạt động trong thời gian ở lớp; MT chưa học không được thêm. API và Cell.canEdit dùng cùng điều kiện. Trẻ nghỉ học/phiếu đã chốt chỉ xem. Giới hạn ngày, quyền ghi, lớp/năm học vẫn kiểm riêng.

Cô mới được quyền xem chính trẻ hiện phụ trách trước khi lọc khoảng; cô cũ cắt về stay và đúng lớp. Test timeline cả hai phía, mục tiêu chưa học, snapshot và nhãn sức khỏe; kết quả trong docs/quality/2026-11-23-history-continuity-verification.md. Không coi toàn bộ UI đã phủ xong.
