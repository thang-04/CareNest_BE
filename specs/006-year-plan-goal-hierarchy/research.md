# Research
- Decision: dùng ba bảng concrete và mapped superclass cho phần định danh chung. Rationale: repository truy vấn đúng bảng, mapper giữ kiểu rows hiện tại. Alternative: TABLE_PER_CLASS union repository làm truy vấn/delete khó kiểm soát hơn.
- Decision: ba bảng dùng sequence chung, allocationSize=1. Rationale: giữ ID dòng cũ và không trùng khóa UI. Alternative: ba sequence riêng làm rows API có thể trùng id.
- Decision: suy ra cha khi nhận rows, lưu FK; group reset khi gặp sub, sub reset khi chuyển domain. Rationale: tương thích cấu trúc request hiện tại; không lấy cha từ chuỗi text.
- Decision: FK cha ghép ownership và sub_domain_key = COALESCE(sub_domain_id,0), deferred. Rationale: kiểm đúng nhóm/sub kể cả NULL và cho phép chuyển cha trong cùng transaction. Alternative: trigger tạo thêm logic ẩn.
- Decision: backfill theo (year_plan_id, domain_id, sort_order, id); phát hiện sort_order trùng và nội dung trỏ tiêu đề thì fail trước drop bảng. Không tự sửa dữ liệu mơ hồ.
- Không còn NEEDS CLARIFICATION; không có extension hooks được cấu hình.
