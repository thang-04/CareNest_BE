# Data Model
- edu_plan_sub_domains: id, year_plan_id, domain_id, sort_order, text, lineage_id; UNIQUE(id, year_plan_id, domain_id).
- edu_plan_expected_results: các cột chung + sub_domain_id nullable; sub_domain_key generated COALESCE(sub_domain_id,0); UNIQUE(id, year_plan_id, domain_id, sub_domain_key).
- edu_plan_goals: các cột chung + sub_domain_id nullable, expected_result_id nullable, sub_domain_key generated, goal_no NOT NULL, curriculum_goal_id nullable.
- Cả ba có FK year_plan_id CASCADE, domain_id; FK cha ghép kiểm cùng plan/domain. Goal→group kiểm cả sub_domain_key. Parent FK deferred để chuyển cha an toàn.
- edu_goal_contents đổi goal_row_id thành goal_id, FK edu_plan_goals CASCADE. Java property goalRowId được giữ nội bộ để tránh đổi caller không cần thiết.
- Giữ ID/lineage cũ; dùng edu_plan_rows_seq chung cho các dòng mới.
- Không đổi trạng thái kế hoạch, DTO, ApiCode, nguồn curriculum hay các bảng giáo án/học liệu.
