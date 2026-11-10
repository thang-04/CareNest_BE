# Research — Phiên bản kế hoạch năm

## R1. Phiên bản lưu thế nào
- **Quyết định**: mỗi phiên bản là một dòng `edu_year_plans` + cây con riêng (rows/contents/themes/breaks/ticks), thêm `version_no`, `source_plan_id`.
- **Lý do**: dùng lại nguyên luồng EDU-03..10/22/23 (phiên sửa, revision, gửi/ban hành), PUT cả cây, quyền EDU-06; tài liệu lớp đã có `year_plan_id` nên tự tách theo phiên bản; bản đã ban hành bất biến vì không ai sửa dòng của nó.
- **Loại**: bảng snapshot JSON (phải viết lại mọi truy vấn và luồng sửa); sửa tại chỗ + lịch sử diff (không giữ được bản ban hành nguyên vẹn).

## R2. Định danh ổn định
- **Quyết định**: cột `lineage_id BIGINT NULL` trên `edu_goal_rows`, `edu_goal_contents`, `edu_themes`, `edu_breaks`; NULL nghĩa là "chính nó" (lineage hiệu dụng = `COALESCE(lineage_id, id)`); khi chép đặt `lineage_id = COALESCE(src.lineage_id, src.id)`.
- **Lý do**: không cần cập nhật dòng ngay sau insert để lấy id; dữ liệu cũ không phải backfill; PUT giữ id nên giữ lineage.
- **Loại**: UUID lineage mới (phải backfill, đổi DTO); so theo số MT (đổi khi chèn dòng, spec cấm).

## R3. Ô "Kế hoạch điều chỉnh và bổ sung"
- **Quyết định**: cột `adjustments JSONB` (ngày ISO → văn bản) trên `edu_class_docs`, endpoint `PUT /class-docs/week/{docKey}/adjustments`; không thuộc `content`.
- **Lý do**: sửa sau duyệt không làm đổi nội dung đã duyệt; quyền/trạng thái kiểm riêng; lịch sử ghi dòng riêng.
- **Loại**: field trong `content.days[i]` (phải mở khóa PUT nội dung khi đã duyệt ⇒ rủi ro sửa ngầm).

## R4. Khóa Web giáo án tuần
- **Quyết định**: phiên bản 1 giữ `wp_<lớp>_<thứ Hai>`; phiên bản ≥2 dùng `wp_<lớp>_<thứ Hai>_<id kế hoạch năm>`. `bp_`, `tp_` đã khác theo id kế hoạch/chủ đề.
- **Lý do**: không đổi khóa dữ liệu hiện có; `doc_key` unique toàn bảng.

## R5. Chặn gửi khi còn ô đỏ
- **Quyết định**: BE kiểm khi `submit` tài liệu có `source_doc_id` (và với hồ sơ chủ đề: cả giáo án tuần trong hồ sơ): (a) mọi tham chiếu MT (`rows` key của kế hoạch chủ đề, `picks[*][*].goalId` của giáo án tuần) phải là MT của phiên bản tài liệu; tên tuần chủ đề nhánh phải là tuần của lịch phiên bản; (b) MT/chủ đề được tham chiếu mà đổi so với phiên bản của tài liệu nguồn phải có trong `content.versionChecks` (`goal:<id>`, `theme:<id>`). Bỏ qua chủ đề đã kết thúc (A/FR-025). Lỗi `CLASS_DOC_VERSION_FIX_REQUIRED` (7121) kèm số mục.
- **Lý do**: SC-006; logic so sánh có sẵn ở BE (R6) nên không chép rule sang FE ngược chiều; FE dùng cùng dữ liệu `changes` để vẽ ô.

## R6. Phạm vi "đổi"
- MT đổi khi `text` đổi, hoặc bất kỳ nội dung của nó đổi chữ / bị bỏ / thêm / đổi tập chủ đề tích (tích so theo lineage chủ đề). MT chỉ đổi số/thứ tự/lĩnh vực ⇒ UNCHANGED.
- Nội dung: CHANGED khi chữ hoặc tập chủ đề (lineage) đổi; REMOVED/ADDED theo lineage.
- Chủ đề: CHANGED khi tên/viết tắt/ngày đổi; REMOVED/ADDED. Đợt nghỉ không liệt kê riêng: ảnh hưởng tuần đã phản ánh qua lịch tuần ở chủ đề nhánh.
- So sánh giữa hai phiên bản bất kỳ cùng chuỗi (`from` mặc định là `source_plan_id`).

## R7. Phiên bản bị thay thế
- Hiện hành = phiên bản APPROVED có `version_no` lớn nhất của chuỗi. Tài liệu lớp trên phiên bản thấp hơn: chặn PUT nội dung và submit (`YEAR_PLAN_SUPERSEDED` 7122); duyệt/trả lại hồ sơ đang chờ vẫn được; PUT adjustments vẫn được.
- Đánh giá trẻ (mock Web): ngày có nhiều giáo án đã duyệt ⇒ chọn bản có phiên bản kế hoạch lớn nhất.
