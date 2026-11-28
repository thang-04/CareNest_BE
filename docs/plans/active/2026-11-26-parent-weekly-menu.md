---
title: Phụ huynh xem thực đơn tuần và ảnh suất ăn của con (API cho App)
status: in-progress
owner: Nguyen Duy Thang
jira: G94-497 (cha G94-495) / mã công việc chưa chốt
branch: chưa tạo (worktree detached từ origin/dev)
modules: [nutrition, child]
rules: [PAR-01, PAR-14, AUTH-04, CHD-04, CHD-13, NUT-08, NUT-13, NUT-16, NUT-17, NUT-23, SCH-05]
risk: high
created: 2026-11-26
updated: 2026-11-26
---
# Phụ huynh xem thực đơn tuần và ảnh suất ăn của con

Làn L: thêm API contract, dữ liệu trẻ em, tác động App. Không migration, không sửa `security/`.
Đặc tả: `specs/020-parent-weekly-menu/` (domain-spec, spec, data-model, contracts, tasks). AC và hợp đồng chỉ ghi ở đó.

## Context
- `GET /nutrition/meal-plans*`, `/day-board`, `/meal-photos/week` chỉ HT/PHT; `/meal-photos*` chỉ HT/PHT/bếp. Phụ huynh chưa có API nào.
- App `parent_menu/` (màn P05 "Thực đơn tuần") đang chạy `FakeWeeklyMenuSource`.
- Kế hoạch cũ `2026-11-03-day-boarding-l5-l6-kitchen-parent.md` hoãn L6 (phụ huynh); plan này thay phần L6.
- `ChildSchoolYearService` mà Jira nêu không có trên `origin/dev` (chỉ ở `origin/fix/G94-180-BE-FIX-10-school-year-scope`) ⇒ không phụ thuộc.

## Quyết định đã chốt
1. Điểm trường, khối, bữa suy từ lớp của con; client không gửi `campusId` — Jira G94-497.
2. Không trả số suất, sổ lưu mẫu, biên bản kiểm thực, người đăng ảnh, `onTime` — Jira G94-497, ADR-0010.
3. Dị ứng chỉ cảnh báo, không món/suất thay thế — NUT-23.
4. Đánh dấu chất con đã khai, liệt kê chữ không khớp — user, 26/11/2026.
5. Ngày con không học: 200, đánh dấu chưa học (khác chữ "từ chối" trong Jira) — user, 26/11/2026.
6. Tuần chưa công bố: ẩn cả ảnh — user, 26/11/2026.
7. Con không báo ăn: trống từ tuần hiện tại, tuần đã qua vẫn xem — user, 26/11/2026.
8. Bỏ giờ bữa, năng lượng, người đăng ảnh — user, 26/11/2026.
Quyết định 4–8 ghi ở `BUSINESS_RULES.md` PAR-14.

## Làm rõ nghiệp vụ
- **Actor/outcome:** phụ huynh liên kết với con xem món theo ngày và ảnh bữa thực tế của điểm trường con học.
- **Ảnh hưởng:** meals đọc child, organization; chỉ đọc dữ liệu; Web không đổi. APP: `parent_menu/`, ô "Thực đơn tuần" ở Trang chủ phụ huynh.

### Câu hỏi mở
Không còn.

## Acceptance criteria
AC-1…AC-9 ở `specs/020-parent-weekly-menu/spec.md`.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Kiểm quyền phụ huynh | kiểm trong service như `ParentAttendanceServiceImpl#checkParent` | `ChildAccessPolicy#checkCanViewChild` | policy cho cả nhân sự theo phạm vi và trả 3001; gom hàm chung ở `security/` để việc riêng |
| Xác định lớp | theo từng ngày từ `findChildStays`, lọc `yearStart`–`yearEnd` | lớp hiện tại | chuyển lớp giữa tuần; giai đoạn chưa đóng bị kéo quá năm học |
| Service | `ParentMenuService` mới trong meals | mở rộng `MealPlanService` | DTO phụ huynh tách khỏi nhân sự (ADR-0010) |
| Quyền ảnh | cùng một luật hiển thị với API tuần | chỉ so điểm trường | không tải được ảnh mà màn tuần đang ẩn |

## Phases
### Phase 1 — Thực đơn tuần (T001–T006)
- Files: C `ParentMenuController`, `ParentMenuService`, `ParentMenuServiceImpl`, `ParentMenuResponse`, `ParentMenuIntegrationTest`, `ParentMenuControllerTest`.
- Exit: verify pass; dừng chờ review.

### Phase 2 — Ảnh bữa cho phụ huynh (T007–T010)
- Files: M các file Phase 1; M `openapi.yaml`, `API_INDEX.md`, `docs/modules/nutrition.md`.
- Exit: verify pass, snapshot; ghi việc App cần đổi; dừng chờ review.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | AUTH-04, CHD-04 | integration + controller | 1, 2 | pass (phase 1, 2) |
| AC-2…AC-6, AC-8, AC-9 | PAR-14, NUT, SCH-05 | integration + controller | 1 | pass |
| AC-7 | NUT-16, PAR-14 | integration + controller | 2 | pass |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Lộ ảnh/thực đơn ngoài quyền | dữ liệu trẻ | kiểm ở server theo từng ngày, test từ chối | gỡ endpoint mới, không ghi dữ liệu |
| Dị ứng, báo ăn không có lịch sử | tuần cũ phản ánh hồ sơ hiện tại | đã nêu trong PAR-14 và contract | — |
| Lẫn với G94-496/499 chưa commit cùng worktree | khó tách commit | commit 496, 499 trước khi code | — |

## Validation log
- 26/11/2026: đối chiếu code `origin/dev` (6e28faeb) và đọc màn App; chưa viết code.
- 26/11/2026: user trả lời hết câu mở; đối chiếu spec ↔ data-model ↔ contract ↔ tasks ở `specs/020-parent-weekly-menu/plan.md`.

## Progress log
- 26/11/2026: bản nháp đủ quyết định, chờ user duyệt.
- 26/11/2026: user (Nguyen Duy Thang) duyệt plan và cho làm Phase 1; bắt đầu Phase 1. Code cùng worktree với G94-496/499 chưa commit (chưa có mã công việc).
- 26/11/2026: Phase 1 xong (T001–T006), test mới đã fail trước khi code. `node scripts/verify.mjs` PASS full: 2759 test, 0 fail, 0 error, 3 skip (AI live eval thiếu biến môi trường). Dừng chờ review. Chưa commit, chưa thử với App, chưa review bằng subagent.
- Khác plan: `openapi.yaml`, `API_INDEX.md`, `docs/modules/nutrition.md` cập nhật ngay ở Phase 1 (snapshot test bắt buộc khớp contract); Phase 2 cập nhật tiếp cho hai API ảnh. Sửa thêm `KitchenAllergyWarnings#clean` thành package-private để dùng lại.
- 26/11/2026: Phase 2 xong (T007–T010), 4 test ảnh đã fail trước khi code. Chạy test liên quan: ParentMenuIntegrationTest (13), ParentMenuControllerTest (5), OpenApiSnapshotTest (4), ArchitectureRulesTest (14) + spotless:check = 36 test, 0 fail, BUILD SUCCESS. Không chạy toàn bộ suite theo chỉ đạo user; lần chạy toàn bộ trước Phase 1 PASS (2759 test). Chưa commit, chưa thử với App, chưa review bằng subagent. `origin/dev` đã có thêm 5 commit đụng `openapi.yaml`, `BUSINESS_RULES.md`: cần gộp khi tạo nhánh.
