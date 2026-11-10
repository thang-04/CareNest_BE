# Data Model
- edu_plan_sub_domains: id, year_plan_id, domain_id, sort_order, text, lineage_id; UNIQUE(id, year_plan_id, domain_id).
- edu_plan_expected_results: các cột chung + sub_domain_id nullable; sub_domain_key generated COALESCE(sub_domain_id,0); UNIQUE(id, year_plan_id, domain_id, sub_domain_key).
- edu_plan_goals: các cột chung + sub_domain_id nullable, expected_result_id nullable, sub_domain_key generated, goal_no NOT NULL, curriculum_goal_id nullable.
- Cả ba có FK year_plan_id CASCADE, domain_id; FK cha ghép kiểm cùng plan/domain. Goal→group kiểm cả sub_domain_key. Parent FK deferred để chuyển cha an toàn.
- edu_goal_contents đổi goal_row_id thành goal_id, FK edu_plan_goals CASCADE. Java property goalRowId được giữ nội bộ để tránh đổi caller không cần thiết.
- Giữ ID/lineage cũ; dùng edu_plan_rows_seq chung cho các dòng mới.
- Không đổi trạng thái kế hoạch, DTO, ApiCode, nguồn curriculum hay các bảng giáo án/học liệu.

## Delta02/11 — semantics xóa nhánh tại bản soạn
- Dùng lại domain.rows[] phẳng. Một nhánh bắt đầu ở row kind=sub có ID được chọn và kết thúc trước row sub kế tiếp trong cùng domain, hoặc cuối domain. Không dựa vào tên nên hai nhánh cùng tên không bị gộp/xóa chung.
- Bao gồm group, goal có/không có group trong đoạn; goal sở hữu contents[], mỗi content sở hữu themes[] (dấu tích). Xóa nhánh bỏ các row/content/tick của nhánh khỏi bản soạn; các entity chủ đề ở danh sách themes cấp kế hoạch còn nguyên.
- Confirmation payload chỉ dùng cho UI: số group/goal/content/tick và tên sub. Hủy không mutate; ID không tìm thấy không chọn nhánh gần nhất.
- API lưu vẫn nhận cả cây: các hàng đã xóa không nằm trong PUT; server xử lý bằng save hiện có. Chưa thay FK/nullability/backfill/lineage/contract; không cần migration cho delta.
- Select Lĩnh vực giữ nguyên domainFilter string; ellipsis là trình bày, title là nhãn đầy đủ. Không cắt value/option để tạo dấu ba chấm.
