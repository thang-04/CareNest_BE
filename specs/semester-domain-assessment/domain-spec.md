# Domain spec — Đánh giá học kỳ theo lĩnh vực

## 1. Domain Overview

Thay danh sách nhận xét học kỳ bằng bảng tổng hợp trẻ × lĩnh vực theo mẫu trường, đồng bộ màn cuối chủ đề và cuối năm.

## 2. Scope

Web GV/PHT/HT; kỳ I/II riêng; cuối năm lũy kế. Không sửa APP, không thay dữ liệu đã xác nhận, không commit/push.

## 3. Actors & Roles

GV được phân công lớp ghi/xác nhận; HT/PHT/tổ trưởng xem theo scope BE hiện có. Không thêm bước BGH duyệt.

## 4. Domain Concepts

Mục tiêu gốc, hoạt động được chấm, lĩnh vực kế hoạch năm, kỳ cấu hình, bảng lớp và snapshot xác nhận.

## 5. Domain Relationships

Một lớp-năm có hai kỳ cấu hình; bảng mỗi kỳ chứa trẻ, lĩnh vực và mục tiêu. Mục tiêu thuộc một lĩnh vực; nối lineage qua bản kế hoạch.

## 6. User Requirements

Chọn lớp/kỳ, xem bảng hai cột Đạt/Chưa đạt mỗi lĩnh vực; xem mục tiêu làm căn cứ; ghi lưu ý, lưu/xác nhận, in/xuất.

## 7. Business Rules

Cần chốt 05/11/2026: kỳ II riêng; cuối năm cả năm; mục tiêu >=50% hoạt động đạt; lĩnh vực >=50% mục tiêu đạt. Chưa chấm/vắng không tính lượt. Chưa có dữ liệu để trống/báo thiếu. Thời gian đọc từ cấu hình HT.

## 8. Business Processes

Đọc kỳ cấu hình → lấy mục tiêu áp dụng trong kỳ → gom hoạt động theo trẻ/lineage → tính mục tiêu → tính lĩnh vực → GV kiểm dữ liệu, lưu/xác nhận → BGH xem. In cùng response.

## 9. State / Lifecycle

NONE → DRAFT → CONFIRMED. Xác nhận chốt metadata/kết quả. Không tự tính lại bảng/phiếu đã xác nhận. Không chặn rời trang khi chưa thao tác.

## 10. Business Constraints

Không hardcode ngày, số tuần, lĩnh vực, mục tiêu; không tính lại ở FE; không đếm trùng mục tiêu gốc; không thêm lượt khi sửa cùng hoạt động.

## 11. Non-Functional Requirements

Kiểm scope server trước đọc/ghi, khóa lớp trước xác nhận, không N+1 theo ô; dữ liệu được tải theo kỳ/lớp; không lộ thông tin trẻ thật trong fixture/log.

## 12. Acceptance Criteria

AC1 ngày đúng cấu hình; AC2 50% chính xác; AC3 vắng/thiếu/lineage/lưu sửa đúng; AC4 snapshot không đổi; AC5 GV ghi và BGH chỉ xem; AC6 in/xlsx khớp UI; AC7 dữ liệu cũ còn nguyên; AC8 cuối năm cả năm.

## 13. Domain Terminology

Lần đánh giá = một hoạt động đã lưu; mục tiêu đạt không đồng nghĩa trẻ đạt cả chủ đề. Lĩnh vực là nhóm mục tiêu trong kế hoạch năm đã duyệt.

## 14. Assumptions & Open Questions

Hai câu hỏi nghiệp vụ user đã trả lời ngày 05/11. Bảng có mục tiêu chưa đánh giá hiển thị thiếu; thiết kế đã duyệt yêu cầu bổ sung trước xác nhận. Plan kỹ thuật cần duyệt trước thay contract/migration.
