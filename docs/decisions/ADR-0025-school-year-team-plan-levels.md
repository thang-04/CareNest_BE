# ADR-0025 — Hai cấp kế hoạch và lịch nhánh của lớp

**Thiết kế delta Q4=A/Q5=A cập nhật 07/11/2026:** mẫu nhánh PHT tùy chọn lấy/không lấy và sửa tên bản lớp; đổi thứ tự chuyển cả khối chủ đề kèm nhánh, giữ số tuần học, dời ngày. Thiết kế đã cập nhật theo quyết định mới, còn draft chờ duyệt triển khai; không giữ ràng buộc khóa tên hoặc bắt lấy đủ mẫu cũ.

- Status: PROPOSED
- Date: 2026-11-07
- Liên quan: EDU-55/56 (nguồn user, chưa có team/trường xác nhận), EDU-25/26/54, ADR-0020/0021/0024.
- Hồ sơ: [spec012](../../specs/012-school-year-team-plans/spec.md), [plan làn L](../plans/active/2026-11-07-school-year-team-plans.md).

## Bối cảnh

User yêu cầu tách năm/tổ, cấp năm định hướng, tổ chỉ tick nội dung nguồn cố định, GV nhận tổ đã ban hành. Nhánh có thể nhiều tuần, GV chỉnh lịch lớp trong giới hạn. Source hiện chỉ có một họ kế hoạch và names theo từng Monday; nhiều FK/lineage downstream đã dùng IDs của họ này. `source_plan_id` hiện là predecessor, workflow key year hiện mang nghĩa tổ. Cần tránh đổi nghĩa dữ liệu/contract cũ khi thêm cấp năm.

## Quyết định đề xuất

1. Giữ edu_year_plans và các bảng cây, thêm plan_level SCHOOL_YEAR/TEAM, migration đưa legacy về TEAM giữ IDs. curriculum_source_plan_id riêng trỏ annual exact version; version predecessor giữ nghĩa. Unique/current/open version đều theo cấp. Tạo version TEAM có thể chọn annual mới tường minh (kể cả legacy nhận nguồn lần đầu); không chọn thì clone như cũ, không rebind bản đã tồn tại.
2. TEAM kế thừa clone snapshot cây annual, không share live rows; source-backed tree chỉ đọc, content-theme ticks giữ logic cũ. Legacy không có annual là ngoại lệ tương thích có marker server-only, không giả nguồn.
3. Workflow year tiếp tục TEAM; schoolYear mới mặc định PHT→HT. Giữ cấu hình hiện hành, approval JSON/print snapshot/author ledger theo document; không nhập tác giả nguồn vào bản tổ.
4. Template nhánh có FK plan/theme; GV tùy chọn không lấy/lấy một phần/tất cả, sửa tên bản lớp và thêm custom. Branch JSON v2 có UUID/source/range và weekNotes theo offset tuần học; không cột mandatory/selected. Reorder pure preview giữ counts/offsets/identity, server thực hiện orderedThemeIds command tại PUT, khác resize thường; không tự nén/lấp gaps, không move week docs/media. Giáo án vẫn một week doc/Monday, version>1 giữ suffix TEAM planId. Calendar resolver dùng chung cho đọc/preview/ghi/submit/hồ sơ/đánh giá.
5. Cấu hình duration HT-only và snapshot khi tạo. Legacy không hồi tố min/max; JSON legacy đọc được, không ghi chuyển đổi khi GET. Giữ các nguyên tắc ba trạng thái, phiên bản, scope, in và manual save.
6. Observation đọc projection eduplan theo class cho ngày hiệu lực; các query nguồn chọn TEAM. Union/lookup confirmed trước themes current, thêm presentation snapshot để version mới bỏ theme không làm mất view frozen. Legacy baseline có nguồn, không tính lại cells hoặc giả metadata lịch sử chưa lưu. Không đổi công thức đánh giá hoặc dependency chiều ngược.

## Phương án đã cân nhắc

| Phương án | Ưu | Nhược / kết luận |
| --- | --- | --- |
| Một họ plan + level + copy snapshot | Giữ IDs/FK/version, guard nguồn rõ | Phải audit mọi query/current/policy; đề xuất chọn |
| Bảng/source CRUD riêng cho annual | Tên khái niệm tách hẳn | Nhân đôi workflow/version/author targets và migration; không chọn |
| TEAM tham chiếu trực tiếp cây annual | Ít bản sao | Ownership/tick/diff gắn live source, tăng nguy cơ đổi bản đã duyệt; không chọn |
| names mỗi tuần như hiện tại | Ít sửa UI | Không định danh nhánh hai tuần, khó theo dõi nguồn khi nhánh tại lớp đổi tên; không đủ yêu cầu |
| Swap range, tự pack gaps hoặc chỉ FE retime | Ít kiểm tra | Có thể đổi số tuần/notes/IDs hoặc tự tạo quy tắc lấp khoảng trống; chọn helper slots/offsets + preview/PUT kiểm server thay thế |

## Hệ quả

Migration/API/client phối hợp, hơn tám file và hai module BE bị ảnh hưởng nên dùng lane L/ba phase. Không sửa migration đã commit, không library mới. Default API vẫn TEAM nhưng POST nguồn-less mới sẽ bị chặn; không hứa backward compatibility write tuyệt đối. Khi đã ghi payload mới không rollback sang binary cũ không hiểu ranges/level. Rollout/restore vận hành cần quyền riêng.

## Còn chờ

User duyệt plan/contract/migration và phạm vi sửa FE trước implement. Chưa có code, ADR này không tự supersede quyết định ACCEPTED hoặc đánh dấu đã triển khai. Nếu thiết kế triển khai cần đổi nghiệp vụ đã chốt phải quay lại user; không giải quyết bằng thêm giả định kỹ thuật.
