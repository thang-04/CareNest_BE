# Specification — Module dynamic permissions

## User stories

1. HT cấu hình từng việc của cấu hình trường, điểm danh và đánh giá trẻ theo vai trò hoặc cá nhân.
2. PHT/GV/tổ trưởng/bếp chỉ thấy và thực hiện việc đã được cấp trong phạm vi phân công.
3. Hệ thống từ chối API trực tiếp khi thiếu quyền hoặc ngoài scope.

## Functional requirements

- FR-01: catalog công bố các mã quyền mới với role cấu hình được và nhãn module.
- FR-02: controller/service ghi dùng permission guard; policy campus/lớp tiếp tục kiểm sau guard.
- FR-03: endpoint đọc nhận VIEW hoặc quyền thao tác cùng module khi cần mở dữ liệu để làm việc.
- FR-04: capability trả từ BE phản ánh effective permission và scope.
- FR-05: FE menu, route và action đọc bePermissions/capability, không tự cấp theo role.
- FR-06: migration backfill quyền role tương ứng hành vi cũ.
- FR-07: quyền cố định của HT và PERMISSION_MANAGE không thay đổi.

## Success criteria

- Không còn endpoint ghi thuộc phạm vi feature chỉ dựa vào hasRole cho PHT/GV/tổ trưởng/bếp.
- Test chứng minh thiếu quyền, sai campus và sai lớp đều bị chặn độc lập.
- BE và FE full verification pass sau thay đổi cuối.
