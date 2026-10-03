# Data Flow

Luồng trong 1 request: guide mục 4–5 (`Controller → Service → Repository → PostgreSQL`, `Service → integration/`). File này mô tả luồng **giữa feature** và luồng dữ liệu nhạy cảm.

## Trong một request

```text
HTTP → controller/<feature>: @Valid request DTO, lấy user hiện tại
     → service/<feature>: @Transactional; kiểm tra quyền + scope; rule nghiệp vụ;
                          lỗi ⇒ GlobalException(ApiCode, desc)
     → repository/<feature> → PostgreSQL
     ← DTO (mapper) ← ResponseJson.toJsonWithData(ApiCode.X, desc, dto)
```

## Giữa các feature

```text
Đọc:  MealCountService ──gọi──► AttendanceService (đếm trẻ có mặt + đăng ký ăn)
Phản ứng: AttendanceService (commit) ──event──► meals listener: đánh dấu số suất đã thay đổi, cần xác nhận lại
                                    └─event──► notification: báo vai trò cần xử lý
AI:   HealthService → build input tối thiểu → AiClient (integration/) → bản nháp lưu ở health
```

## Luồng nghiệp vụ chính

```text
Đơn nghỉ ─► Điểm danh + đăng ký ăn ─► Số suất (BGH xác nhận) ─► Định lượng ─► Bếp xem
                  │                          ▲
                  └── đổi sau xác nhận ──────┘ (lịch sử + xác nhận lại)
Thực phẩm/dinh dưỡng ─► Món ─► Thực đơn (duyệt) ─┘
Ngữ cảnh hoạt động + Quan sát + Sức khỏe + Điểm danh + Bữa ăn ─► Hồ sơ phát triển ─► Nhận xét (GV duyệt)
Mọi feature ─► Báo cáo (chỉ đọc, trong scope)
```

## Dữ liệu nhạy cảm đi đâu

| Dữ liệu | Được đi tới | Không được đi tới |
| --- | --- | --- |
| Tên/ngày sinh/ảnh trẻ | DB, client có quyền; ảnh qua `StorageService` | AI provider, log, nội dung push |
| Sức khỏe, quan sát, đánh giá | DB, staff có quyền, phụ huynh theo policy | Log, push, bếp, AI (trừ số liệu bí danh) |
| Dị ứng | Bếp (phần cần cho nấu), AI dạng tổng hợp | Log |

Logging: chỉ id, theo format guide mục 10.
