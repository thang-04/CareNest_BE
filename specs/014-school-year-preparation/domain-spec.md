# Domain spec — Ngày mở chuẩn bị năm học (CFG-YR-05)

Nguồn: Cần chốt 11/11/2026 (thread dự án). Trạng thái: CONFIRMED.

## 1. Domain Overview
Năm học mới cần mở sớm hơn ngày bắt đầu để giáo viên biết lớp, biết trẻ và soạn giáo án; dữ liệu theo ngày (điểm danh, suất ăn, đánh giá) vẫn chỉ từ ngày bắt đầu.

## 2. Scope
Trong: ngày mở chuẩn bị ở cấu hình thời gian năm học; giai đoạn "Đang chuẩn bị"; GV/tổ trưởng thấy năm, lớp, trẻ đã xếp (chỉ xem); hàm phân công tổ trưởng theo năm cho module giáo án. Ngoài: sửa code giáo án (Tiền), tiếp nhận trẻ thẳng vào lớp năm mới (Giáp), bán trú (Thắng), đổi cách kích hoạt năm.

## 3. Actors & Roles
Hiệu trưởng (đặt/sửa ngày); PHT (xem mọi năm như hiện tại); Giáo viên, Tổ trưởng nhóm tuổi (xem lớp năm mới, soạn/duyệt giáo án).

## 4. Domain Concepts
Năm học (PLANNED/ACTIVE/CLOSED); ngày mở chuẩn bị (prep_date); giai đoạn chuẩn bị = PLANNED và hôm nay ≥ prep_date; bản xếp lớp năm mới (class_placements); phân công GV/tổ trưởng theo năm.

## 5. Domain Relationships
Năm học 1–n lớp; lớp 1–n phân công GV; năm học 1–n phân công tổ trưởng (theo nhóm tuổi); năm học 1–n bản xếp lớp (trẻ → lớp đích).

## 6. User Requirements
HT đặt ngày mở chuẩn bị (mặc định 1 tháng trước ngày bắt đầu). Từ ngày đó GV thấy năm mới ở bộ chọn năm, thấy lớp mình và danh sách trẻ đã xếp, soạn giáo án; tổ trưởng soạn và duyệt giáo án lớp trong nhóm tuổi.

## 7. Business Rules
CFG-YR-05 (BUSINESS_RULES.md). Giữ CFG-YR-01, CFG-YR-03 (kích hoạt từ 7 ngày trước ngày bắt đầu), OBS-18 (năm không ACTIVE chỉ xem).

## 8. Business Processes
HT tạo năm (ngày gợi ý) → cấu hình lớp, phân công, xếp lớp → tới ngày mở chuẩn bị: GV xem lớp/trẻ, soạn giáo án → ngày bắt đầu: HT kích hoạt, trẻ vào lớp, mở điểm danh/đánh giá.

## 9. State / Lifecycle
PLANNED (ẩn với GV) → PLANNED + đang chuẩn bị (hiện với GV, chỉ giáo án + xem) → ACTIVE → CLOSED. "Đang chuẩn bị" là trạng thái suy ra, không lưu.

## 10. Business Constraints
Ngày mở chuẩn bị: bắt buộc trên form; < ngày bắt đầu; > ngày kết thúc năm liền trước; không trước hôm nay khi đặt/đổi (ngày đã lưu giữ được); năm đã bắt đầu/kết thúc không đổi; chỉ HT sửa; đổi ngày bắt đầu làm ngày chuẩn bị hết hợp lệ ⇒ lỗi tại ô ngày chuẩn bị.

## 11. Non-Functional Requirements
Lỗi tiếng Việt, mã riêng (2035–2038, 2412); FE lỗi inline dưới ô, dd/mm/yyyy; không lộ năm chưa mở chuẩn bị cho GV (404).

## 12. Acceptance Criteria
Test BE `SchoolYearServiceIntegrationTest` (prepDate*, teacherSeesPlannedYearOnlyFromPrepDate), `YearTransitionIntegrationTest.teacherSeesPrepClassesWithPlacedChildren`; FE `tests/children/year-prep-date.test.mjs`.

## 13. Domain Terminology
Ngày mở chuẩn bị (prep date); Đang chuẩn bị (preparing); Lớp năm học mới (màn GV).

## 14. Assumptions & Open Questions
- Dòng năm tạo ngoài service (seed/test) có prep_date NULL ⇒ coi như 1 tháng trước ngày bắt đầu.
- Tiền cần gọi `StaffAssignmentService#findYearTeamLeaderAgeGroupIds` trong giáo án để tổ trưởng năm mới thấy lớp khi đang chuẩn bị (chưa làm, code của Tiền).
- Tiếp nhận trẻ thẳng vào lớp năm mới trước ngày bắt đầu: ngoài phạm vi (code của Giáp).
