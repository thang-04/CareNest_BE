---
id: BUG-261105-health-pickup-context
type: bug
modules: [attendance]
rules: [HLT-15, PCK-02]
status: fixed
date: 2026-11-05
keywords: [daily-health, pickup, contacts, canRecord]
similar_to: []
---
# Sức khỏe còn mở form sau trả trẻ, liên hệ hồ sơ trống

## Symptom
POST đã chặn bằng HEALTH_CHILD_PICKED_UP nhưng GET vẫn trả canRecord=true. FE adapter gán contacts rỗng.

## Điều kiện tái hiện
Trẻ giả có mặt hôm nay, giáo viên lớp xác nhận trả trẻ rồi đọc sức khỏe.

## Attempts
| # | Cách thử | Kết quả |
| --- | --- | --- |
| 1 | PickupIntegrationTest thêm assert canRecord=false sau trả | RED: true; 3 test, 1 failure, 0 skip |

## Root cause
canRecordFor thiếu kiểm bản ghi trả; ChildDay chưa có giờ trả/liên hệ, FE bỏ nguồn liên hệ bằng [].

## Fix
Mở rộng ChildDay bằng handedOverAt, contacts tối thiểu từ ChildIntakeService sau kiểm scope; khóa GET khi đã trả. POST guard và lịch sử giữ nguyên.

## Regression test
PickupIntegrationTest.teacherHandsOverPresentChildAndParentIsNotified: kiểm trước/sau trả, lần không trả, liên hệ nguồn hồ sơ và teacher khác lớp bị chặn. GREEN 3/3 test PickupIntegrationTest, full verify 1039 tests, fail/error/skip 0.

## Ảnh hưởng
FE DailyHealthPanel/adapter và GET lịch sử; App chưa triển khai, field cũ giữ nguyên. Không sửa dữ liệu lịch sử.

- OpenAPI snapshot thêm contacts/handedOverAt đúng dự kiến, snapshot full gate không skip. Không đổi request/endpoint hoặc dải nhập nhiệt độ.
