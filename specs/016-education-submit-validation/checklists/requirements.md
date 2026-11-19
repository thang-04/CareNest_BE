# Specification Quality Checklist: Điều kiện gửi duyệt kế hoạch giáo dục

**Purpose**: Kiểm tra chất lượng đặc tả trước lập kế hoạch
**Created**: 2026-11-09
**Feature**: [spec.md](../spec.md)

## Content Quality

- [ ] Không đưa chi tiết triển khai vào đặc tả nghiệp vụ
- [x] Tập trung giá trị và nhu cầu người dùng
- [x] Người không chuyên kỹ thuật có thể đọc
- [x] Các mục bắt buộc đã hoàn thành

## Requirement Completeness

- [x] Không còn marker NEEDS CLARIFICATION
- [x] Yêu cầu kiểm chứng được, phạm vi và ngưỡng cụ thể
- [x] Tiêu chí thành công đo được và không phụ thuộc công nghệ
- [x] Có kịch bản chính, biên, lỗi và giữ dữ liệu
- [x] Phạm vi, giả định và phụ thuộc được nêu

## Feature Readiness

- [x] Yêu cầu có kịch bản chấp nhận tương ứng
- [x] Các luồng năm, tổ và báo lỗi được bao phủ
- [x] Có thể đánh giá bằng kết quả đo được
- [ ] Đặc tả tập trung hành vi, không áp đặt thiết kế triển khai

## Notes

- Ba điểm phạm vi được user làm rõ trong chat ngày 09/11. Các delta mới là chỉ đạo user, không tự suy giáo viên trường đã xác nhận.
- Snapshot năm lịch sử bị mất cần nghiên cứu bằng chứng ở bước lập kế hoạch, không tự thay đổi dữ liệu đã gửi.
- Đã bổ sung kịch bản và FR-015 theo quyết định không xóa tổ, cùng kịch bản bỏ khóa 90 giây. Spec còn các thuật ngữ kỹ thuật hiện hữu như revision, heartbeat và LEGACY_UNBOUNDED; hai mục nội dung không phụ thuộc triển khai chưa đạt tuyệt đối. Không suy đã triển khai hay được giáo viên trường xác nhận delta mới.
