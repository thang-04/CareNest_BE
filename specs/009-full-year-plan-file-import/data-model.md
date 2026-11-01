# Data model — Feature009

Không migration/entity/table mới. Dùng edu_year_plans, edu_plan_themes, edu_plan_breaks và cây/tick hiện có khi Lưu. Backend source/API không sửa trong delta hai chế độ.

## Preview và mode transient
Preview shape giữ nguyên: Metadata(title,schoolYear,ageFromMonths,ageToMonths), Theme(name,abbr,festivals,start,end,note), Break(name,start,end,note), domains/themeAbbrs/warnings. Không có description. Mode chỉ ở modal: replace-all hoặc replace-present; không gửi thành field API/DB.

Có phần: title có chữ, danh sách themes/breaks/domains có dữ liệu; null/vắng/empty là thiếu. Không có bất kỳ nội dung hợp lệ nào thì không apply. Description chưa có trong preview nên full để trống, present giữ nguyên; chưa thêm parser/DTO để đọc mô tả.

## Transformation
- Full: giữ danh mục lĩnh vực/các phần fixed, thay toàn bộ phần editable bằng preview, thiếu để trống.
- Present: thay nguyên phần có trong preview, phần thiếu giữ nguyên. Mỗi phần gồm title, cây domains, themes, breaks; không ghép thêm dòng của phần cũ được thay.
- Khi themes thay thì tạo ID đích hợp lệ trước ticks; nếu domains giữ thì ánh xạ oldThemeId → oldAbbr → uniqueNewAbbr → newThemeId, loại liên kết không còn đích và báo. Mã mơ hồ không được tự khớp. Nếu themes giữ thì ticks nguồn mới ghép mã sang IDs hiện có. Thay cả hai thì chỉ ghép theo chủ đề mới. Không được tick mồ côi.
- Patch không chứa planId/year/owner/scope/status/history/version/revision/session hoặc ngày bắt đầu-kết thúc năm học; fixed age không đổi. Tuổi trống khi lập mới chỉ nhận qua kiểm nhóm/quyền hiện có.

## Dates và lifecycle
YearRange lấy school year config của hệ thống; không lấy từ source và không đoán fallback khi apply file. Source year chỉ dùng shift ngày theo chênh năm, không tự căn thứ. Thiếu cấu hình range khóa apply và giữ form. Ngoài năm/overlap/không trọn tuần theo cảnh báo/validation EDU23/39/40/42 hiện có.

Apply là patch thuần nguyên tử vào form chưa lưu; đọc lỗi/cancel/lostlease/late response không đổi form. Chỉ Lưu/Ctrl+S hoặc gửi/ban hành mới persistence theo EDU10/22. Không lifecycle/status mới, không lấy người ký từ tài liệu thành dữ liệu tài khoản/quyền. Tick display xuống dòng chỉ là trình bày, không đổi abbr/ID/tick lưu.
