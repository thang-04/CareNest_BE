---
title: Đồng bộ sức khỏe sau trả trẻ và liên hệ hồ sơ
status: done
owner: Cần
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [attendance]
rules: [HLT-15, PCK-02]
risk: medium
created: 2026-11-05
updated: 2026-11-05
---
# Đồng bộ sức khỏe sau trả trẻ và liên hệ hồ sơ

## Context
`DailyHealthServiceImpl.java:131` đã chặn POST sau HANDED_OVER nhưng `canRecordFor` chưa kiểm trả trẻ. `DailyHealthResponse.ChildDay` chưa có giờ trả và liên hệ; FE adapter gán contacts rỗng. Không đổi dữ liệu lịch sử.

## Quyết định đã chốt
- User duyệt 05/11/2026: thêm khóa sức khỏe sau trả trẻ, nối liên hệ từ hồ sơ và sửa các mục FE đã liệt kê. Không sửa vai trò/quyền hay App.
- HLT-15 CONFIRMED: ghi nhiều lần sau giờ chốt, dừng sau trả trẻ. PCK-02 CONFIRMED: nguồn liên hệ là hồ sơ trẻ.
- Mở rộng response cộng thêm handedOverAt và contacts (fullName, relationship, phone); không trả userId hoặc ảnh/CCCD.
- Consumers: FE `createDailyHealthBeApi.getDailyHealth`, `getChildDailyHealthHistory`, `DailyHealthPanel`; App chưa triển khai, giữ các field cũ để tương thích.
- Validation nhiệt độ chờ user chọn dải kỹ thuật; không tự nhận đây là khoảng do Bộ Y tế quy định.

## Làm rõ nghiệp vụ
GV lớp ghi, HT/PHT xem theo phạm vi hiện có. Kiểm quyền và trẻ trong lớp trước đọc hồ sơ liên hệ. Không thêm endpoint hoặc mở quyền xem trẻ ngoài scope. Chỉ lần trả thành công khóa; lần từ chối trả không khóa. RequestId đã lưu được replay nhưng không tạo bản ghi mới sau trả.

## Acceptance criteria
- AC-1 (HLT-15): Given trẻ đã trả, When đọc sức khỏe, Then canRecord=false và trả handedOverAt, lịch sử giữ nguyên; POST mới bị HEALTH_CHILD_PICKED_UP.
- AC-2 (PCK-02): Given actor được xem trẻ, When đọc sức khỏe, Then contacts lấy trực tiếp hồ sơ; hồ sơ không có liên hệ trả [] và FE báo rõ.
- AC-3 (HLT-15): Given chỉ có lần không trả hoặc chưa trả, When đọc ngày hợp lệ và trẻ có mặt, Then vẫn ghi được; actor ngoài scope bị từ chối trước đọc liên hệ.

## Key decisions
| Chọn | Phương án khác | Lý do |
| --- | --- | --- |
| Trả trạng thái và liên hệ trong ChildDay sau kiểm scope | FE gọi thêm API hồ sơ/đón trẻ | Tránh quyền endpoint không khớp và dữ liệu tải khác thời điểm |
| Giữ POST guard | Chỉ khóa nút FE | API/App vẫn phải được bảo vệ |

## Phases
### Phase 1 — Đồng bộ context sức khỏe
- Files: DTO ChildDay, DailyHealthServiceImpl, test service, OpenAPI snapshot, FE_MAPPING và CROSS_REPO_MAP.
- RED/GREEN: unit service đọc trạng thái, liên hệ, từ chối scope; integration sau trả POST mới không tăng số dòng.
- Exit: `node scripts/verify.mjs` + `node scripts/check-ai-layer.mjs`, snapshot diff chỉ response cộng thêm; không đổi request/endpoint.
- Dừng review sau phần sửa đã được user yêu cầu; nhiệt độ chỉ triển khai khi câu trả lời chốt.

## Test matrix
| AC | Test | Kết quả |
| --- | --- | --- |
| AC-1 | DailyHealthServiceImplTest, PickupIntegrationTest | Đạt trên PostgreSQL, full gate 0 skip |
| AC-2/3 | DailyHealthServiceImplTest | Đạt trên PostgreSQL, full gate 0 skip |

## Rủi ro & rollback
Không migration. Response cộng thêm không phá caller cũ. Không log họ tên/số điện thoại. Rollback chỉ diff task, giữ application.yml và scripts/dev có sẵn.

## Progress log
### 2026-11-05 — Phase 1 bắt đầu
- User duyệt bằng yêu cầu “thêm đi bạn và sửa cái này đi bạn”, gồm bảng BE/FE.
- Chưa commit, chưa verify. Plan FE ở repo sibling.

- Quyết định tiếp theo của user: CHỜ loại nhiệt kế; không sửa dải nhập tại BE trong đợt này. Chuẩn hóa ba chữ số là tiện ích nhập FE, API vẫn nhận số thập phân thật.


### 2026-11-05 — Phase 1 hoàn tất phần được duyệt
- Chưa commit/push. FE: `VERIFY PASS full | npm run verify | log verify.log` (840 tests, 0 fail/skip).
- BE: `VERIFY PASS full | tests 1039 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 672s | log target/verify.log`.
- Review độc lập không có P1/P2. Nhiệt độ min/max hoãn theo lựa chọn user, không phải phần đã sửa.
- Source và contract giữ lịch sử, không thay vai trò/quyền/App. Không restart BE đang mở ở IntelliJ; chưa full UI ba role trong lượt sửa này.
