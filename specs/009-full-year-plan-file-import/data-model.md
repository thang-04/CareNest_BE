# Data model — Feature009

Không migration/entity/table mới. Dùng edu_year_plans, edu_plan_themes, edu_plan_breaks, cây lĩnh vực con/nhóm/mục tiêu/nội dung và ticks hiện có khi người dùng Lưu.

Preview transient: Metadata(title,schoolYear,ageFromMonths,ageToMonths); Theme(name,abbr,festivals,start,end,note); Break(name,start,end,note). Ngày ISO hoặc null, không suy từ số tuần. Payload vẫn có domains/themeAbbrs/warnings. Metadata vắng → null, themes/breaks vắng → []. Các khóa nguồn trước merge là viết tắt; ID đích do form/server quản lý, không chép số MT thành ID.

Preview không tạo lifecycle mới. Bổ sung thay đổi form chưa lưu; permission/lease/revision/save/gửi/ban hành giữ theo EDU-06/10/22/23. Source year chỉ để dời ngày và hiển thị, target year không đổi. Tên người ký/trường/role trong tài liệu không thành dữ liệu tài khoản hoặc quyền.
