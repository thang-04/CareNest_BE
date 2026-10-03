# Module Map

Tổng quan dependency (L3/L4). Chi tiết từng module: `docs/modules/`.

## Dependency (A → B = service A gọi service B)

```text
 common/utils ◄── (tất cả)

 school-structure ──► (common/utils)
 identity-access  ──► (common/utils)          kiểm tra scope ở security/
 child            ──► school-structure
 attendance       ──► child, school-structure
 nutrition        ──► attendance, child, school-structure, ai-assistance
 health           ──► child, ai-assistance
 learning-obs     ──► child, attendance, nutrition, health, ai-assistance
 facility-issue   ──► school-structure
 reporting        ──► mọi module domain (query only)

 Mọi module domain ──► identity-access (CurrentUser, permission, AccessScopeService)
 Domain ──(event after-commit)──► notification, audit
 ai-assistance, notification, audit ──► không module domain nào
```

Thứ tự lớp (chỉ được phụ thuộc xuống dưới):

```text
L5  reporting
L4  learning-observation
L3  nutrition · health · facility-issue
L2  attendance
L1  child
L0  school-structure · identity-access · ai-assistance · notification · audit · common/utils
```

## Cycle đã phá

| Cycle tiềm ẩn | Cách phá |
| --- | --- |
| identity-access ↔ school-structure/child (scope cần assignment, guardian link) | Kiểm tra scope ở `security/` (AccessScopeService) đọc service organization + child; 2 service đó không gọi ngược |
| attendance ↔ nutrition | Đăng ký ăn ở attendance; meals đọc qua AttendanceService; attendance không gọi meals, chỉ phát event `AttendanceChanged` |
| child ↔ health/learning | DevelopmentProfile ở learning (downstream); child không đọc ngược |
| domain ↔ ai-assistance | Domain build input và gọi port; AI không query domain |
| domain ↔ notification | Event mang thông tin người nhận dạng scope; notification resolve qua identity |

## Quy tắc giao tiếp (ADR-0004)

1. Service feature A chỉ gọi service feature B (guide mục 4); không inject repository của B.
2. Entity xuyên feature: ưu tiên tham chiếu ID; quan hệ JPA chỉ khi không tạo vòng (chốt khi vẽ ERD).
3. Phản ứng ngược chiều (vd. attendance → meals): Spring event sau commit (ADR-0004, PROPOSED).
4. Thêm phụ thuộc mới phải giữ thứ tự lớp ở trên; nếu cần phụ thuộc ngược ⇒ dùng event hoặc đề xuất ADR.
5. Kiểm tra: review theo Checklist guide mục 17; test kiến trúc tự động chỉ khi nhóm đồng ý thêm dependency.
