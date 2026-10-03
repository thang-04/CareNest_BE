# Cross-Repo Map

## Ownership

| Hạng mục | CareNest_BE | CareNest_FE | CareNest_APP |
| --- | --- | --- | --- |
| Business rule, domain model | **Owner** | Không | Không |
| Authorization / access scope | **Owner** (kiểm tra thật) | Ẩn/hiện UI theo dữ liệu BE | Ẩn/hiện UI theo dữ liệu BE |
| API contract (`docs/contracts/`) | **Owner** | Consumer | Consumer |
| Màn hình, route/navigation, client state | — | Owner (Web) | Owner (Mobile) |
| Push notification | Gửi qua provider | — | Nhận, đăng ký token |
| Engineering memory nghiệp vụ/contract | **Owner** (`docs/knowledge/`) | Lỗi UI riêng | Lỗi UI riêng |

Lỗi liên quan contract/nghiệp vụ phát hiện ở FE/APP ⇒ ghi ở BE `docs/knowledge/CROSS_MODULE_ISSUES.md`.

## Actor → client → module BE

| Actor | Client | Module BE chính |
| --- | --- | --- |
| System Admin | Web | identity-access, school-structure, child (cấu hình) |
| Principal / Vice Principal | Web (App: sau, nếu có yêu cầu) | reporting, nutrition (xác nhận/duyệt), learning-observation, health, facility-issue |
| Teacher | Web + App | attendance, learning-observation, health, facility-issue |
| Parent | App | attendance (đơn nghỉ — PENDING), đọc: attendance, nutrition, health, learning (theo PAR-*) |
| Kitchen Staff | App (+Web) | nutrition (suất đã chốt, thực đơn, định lượng) |

## Điểm chạm cần kiểm tra khi đổi BE

| Thay đổi BE | Kiểm tra |
| --- | --- |
| Auth/token/permission | `CareNest_FE/docs/integration/AUTH_FLOW.md`, `CareNest_APP/docs/integration/AUTH_FLOW.md` |
| Endpoint/DTO/error format | `CareNest_FE/docs/integration/BACKEND_INTEGRATION.md`, `CareNest_APP/docs/integration/BACKEND_INTEGRATION.md`, BE `docs/contracts/` |
| Parent visibility (PAR-*) | `CareNest_APP/docs/features/parent/` |
| MealCount/menu | `CareNest_APP/docs/features/kitchen/`, `CareNest_FE/docs/architecture/ROUTE_MAP.md` (màn hình BGH) |
| Event gửi notification | `CareNest_APP/docs/integration/PUSH_NOTIFICATION.md` |

Repo URL: CareNest_FE `https://github.com/thang-04/CareNest_FE.git`, CareNest_APP `https://github.com/thang-04/CareNest_APP.git`. Không tự sửa repo khác; nêu thay đổi cần thống nhất.
