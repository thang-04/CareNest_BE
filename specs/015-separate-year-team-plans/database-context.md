# Database — hiện tại và yêu cầu tách năm/tổ

Ngày 09/11/2026. Đối chiếu source entity/service và Flyway; chưa truy vấn DB runtime. Đây là giải thích hiện trạng và ràng buộc đầu vào plan, không phải migration hay thiết kế đã duyệt.

## Hiện tại: chung bảng chính

`edu_year_plans` lưu cả hai chức năng. `plan_level = SCHOOL_YEAR` là kế hoạch năm học; `plan_level = TEAM` là kế hoạch tổ. Không có hai bảng chính riêng ở source hiện tại.

| Thông tin | Lưu ở đâu / ý nghĩa |
| --- | --- |
| Tên riêng kế hoạch | `edu_year_plans.title`; mỗi dòng có tên riêng, tối đa 150 ký tự |
| Loại hồ sơ | `plan_level`; không xác định loại bằng title/code |
| Năm học | `school_year_id → school_years.id`; `school_years.name` là tên năm, không phải tên kế hoạch |
| Nhóm tuổi | `age_group_id → age_groups.id` |
| Nguồn nội dung của tổ | `curriculum_source_plan_id → edu_year_plans.id`; nguồn phải là SCHOOL_YEAR |
| Bản trước trong chuỗi | `source_plan_id → edu_year_plans.id`; khác với nguồn nội dung trên |
| Phiên bản | `version_no`; mỗi phiên bản là một dòng đầy đủ, hiện áp dụng cả hai cấp |
| Người soạn hiển thị list | `owner_id → user_accounts.id`; đồng tác giả còn được theo dõi trong `edu_document_authors` |
| Ngày/người gửi | `sent_at`, `sent_by`; response list có `sentBy.at`, không cần lấy ngày tạo thay |
| Trạng thái | `status`: DRAFT/REVIEW/APPROVED; nhãn REVIEW là Chờ duyệt |
| Ban hành và dấu vết | `issued_by/issued_at`, `published_by/completed_at`, `approvals`, `print_snapshot`, `edu_plan_events` |
| Khóa/lưu đồng thời | session sửa, revision, request chống lặp |

V83 đặt unique `(plan_level, school_year_id, age_group_id, version_no)` và tối đa một bản chưa APPROVED cho mỗi loại/năm/tuổi. Tên riêng không tạo thêm chuỗi kế hoạch cùng nhóm tuổi. Tổ cũ không có nguồn được đánh dấu `legacy_team_without_source`; không suy diễn nguồn cho các dòng đó.

## Nội dung con và mapping hiện tại

| Bảng | Vai trò |
| --- | --- |
| `edu_plan_sub_domains` | Lĩnh vực con của một bản kế hoạch |
| `edu_plan_expected_results` | Nhóm kết quả mong đợi |
| `edu_plan_goals` | Mục tiêu, liên kết cha và lineage |
| `edu_goal_contents` | Nội dung thuộc mục tiêu |
| `edu_themes`, `edu_breaks` | Chủ đề/sự kiện và đợt nghỉ của bản kế hoạch |
| `edu_plan_branch_templates` | Mẫu chủ đề nhánh gắn kế hoạch/chủ đề |
| `edu_content_theme_ticks` | Mapping từng nội dung với chủ đề |
| `edu_class_docs` | Tài liệu lớp trỏ `year_plan_id`; trong luồng mới là nguồn tổ ban hành, cần chuyển đúng tham chiếu khi tách |

Các bảng cây/chủ đề/mẫu có liên kết về bản kế hoạch. Khi tạo tổ từ năm, `createYearPlan` gọi `copyContent`: chép nội dung nguồn sang bản tổ và bảo vệ cây kế thừa; không chỉ mở cùng dòng năm để sửa. Nguồn năm và tổ do đó có ID bản ghi khác nhau và title độc lập. Chọn nguồn yêu cầu ban hành **hiện hành**, đúng năm/tuổi và quyền tại `requireCurriculumSource`.

## Đích user yêu cầu: hai bảng nghiệp vụ riêng

| Khái niệm | Trách nhiệm dữ liệu đích |
| --- | --- |
| Bảng kế hoạch năm học | Sở hữu tên năm, phạm vi năm/tuổi, phiên bản và workflow năm |
| Bảng kế hoạch tổ | Sở hữu tên tổ, phạm vi năm/tuổi, liên kết bản năm nguồn, mapping/workflow tổ và lịch sử nội bộ |
| Liên kết nguồn năm | FK từ tổ sang đúng bản năm; khác FK bản tổ trước trong lịch sử |
| Nội dung, mapping, lịch sử và lớp | Ownership/FK phải thể hiện rõ năm hay tổ; không giữ FK mơ hồ tới bảng chung đã bị tách |

Tên vật lý ví dụ có thể là `edu_school_year_plans` và `edu_team_plans`; **chưa chốt tên hoặc DDL**. Không cần hai database/server riêng. `school_years`, `age_groups`, tài khoản và danh mục Bộ tiếp tục là danh mục dùng chung.

Ẩn bảng/nút phiên bản ở UI tổ không xóa lịch sử: tổ vẫn cần bản ban hành bất biến và bản cập nhật để duyệt lại. Tên riêng tổ không đồng bộ bắt buộc với tên năm nguồn. “Kế hoạch giáo dục” là nhãn nhóm chức năng, chưa có yêu cầu tạo bảng thứ ba.

Chưa kết luận chỉ tách bảng header là đủ. Plan phải chọn cách sở hữu/snapshot cây con và mapping, chuyển FK class-docs/events/authors/templates cùng các consumers đánh giá trẻ; giữ lineage, revision và nguồn lịch sử. Không dùng một `plan_id` mơ hồ mà không bảo đảm tham chiếu đúng bảng.

## Chuyển đổi phải thiết kế trong plan làn L

- Tạo migration mới; không sửa V83 hoặc migration đã commit.
- Phân loại dữ liệu theo level; bảo toàn tên riêng, lịch sử, ticks và snapshot. Không ghép hồ sơ theo tên.
- Có bảng ánh xạ ID nếu đổi ID; cập nhật toàn bộ FK/consumer tương ứng, không làm lớp trỏ sang năm vì trùng số ID.
- Giữ đọc tổ legacy không nguồn; không tự tạo năm giả.
- Kiểm unique, FK, rollback/cutover và đối chiếu số lượng/nội dung/tham chiếu trên dữ liệu tổng hợp trước áp dụng thật.
- API contract phải đánh giá tác động Web/Mobile và xin duyệt thay đổi cụ thể nếu cần. Đặc tả tách DB không tự cấp quyền đổi contract hay sửa client.

## Bằng chứng source

- [Entity chính](../../src/main/java/com/carenest/entity/eduplan/EduYearPlan.java), [tách level V83](../../src/main/resources/db/migration/V83__split_education_plan_levels.sql), [phiên bản V32](../../src/main/resources/db/migration/V32__add_edu_plan_versions.sql).
- [Service tạo/chọn nguồn/chép](../../src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java), [list response](../../src/main/java/com/carenest/dto/eduplan/YearPlanSummaryResponse.java), [mapper ngày gửi](../../src/main/java/com/carenest/mapper/eduplan/EduYearPlanMapper.java).
- [Cây ba tầng V35](../../src/main/resources/db/migration/V35__split_edu_goal_hierarchy.sql), [mẫu nhánh V84](../../src/main/resources/db/migration/V84__add_education_branch_templates.sql).

## Thiết kế refinement 09/11/2026 — chưa triển khai

Plan/data-model hiện đề xuất 9 bảng nhận owner, shared sequence cho hai header và cập nhật tổ đồng bộ cùng transaction ban hành. Trạng thái source ở trên không đổi do sửa tài liệu. V80 cho edu_document_authors trỏ year_plan_id **hoặc class_doc_id**: tác giả lớp phải giữ class_doc_id, không ép owner header. V35/V84 có FK/unique ghép chứa year_plan_id ở cây và theme/templates; cutover phải tái tạo ràng buộc tương đương theo owner, không chỉ thay FK header. Chi tiết và gate tại [data-model](data-model.md), [tasks](tasks.md), [quickstart](quickstart.md).
