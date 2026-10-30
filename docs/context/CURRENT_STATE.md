# Current State

Cập nhật: 2026-10-29 (chốt rule theo khảo sát team 210/210: sức khỏe, đánh giá, kế hoạch giáo dục, đón trẻ, phụ huynh, báo cáo, AI; loại CSVC — chỉ docs, code không đổi; plan `docs/plans/active/2026-10-29-survey-full-alignment.md`). Trước đó 2026-10-28: bán trú, hồ sơ trẻ, sức khỏe đầu ngày (plan `docs/plans/active/2026-10-28-survey-business-alignment.md`). Cùng ngày merge `dev` vào nhánh G94-226 (kế hoạch giáo dục, cấu hình năm học, điểm danh, sức khỏe trong ngày, số suất): migration của nhánh dời thành V10–V35, ADR đổi số 0017→0020, 0019→0021; rule HLT-07 của nhánh đổi thành HLT-15, rule khảo sát EDU-01..10 của `dev` đổi thành EDU-29..38. **Agent: cập nhật file này khi một feature bắt đầu có code, hoàn thành, hoặc một PENDING được chốt.**

## Tổng quan

Auth React cập nhật2026-10-12: extension Phase1 và backend gateway Phase2 opt-in đã có source, V4/session/OTP/cookie-CSRF; FE Phase3 chưa vào FE `dev` (2026-10-22: FE `dev` vẫn mock; source gateway chỉ ở nhánh FE G94-209 — `docs/quality/reports/2026-10-22-auth-fe-integration-gap.md`). Bằng chứng/lưu ý: `docs/quality/reports/2026-10-12-react-auth-phase2.md`. Không thay trạng thái merge hay triển khai thật của phần Resource Server bên dưới.

Phân quyền cấu hình được (ADR-0017) cập nhật 2026-10-16 trên `feature/G94-60-BE-FEAT-01-authentication`, chưa commit: V7 `role_permissions`/`user_permission_overrides`/`permission_change_log`, `@permissionGuard`, API ma trận/ngoại lệ/lịch sử cho Hiệu trưởng, `permissions` trong `/accounts/me`. Phần B 2026-10-17 (plan `docs/plans/active/2026-10-17-staff-account-provisioning.md`): HT/PHT cấp tài khoản kèm phân công, khóa/mở, mật khẩu tạm, danh sách nhân sự, phân công, đọc campus/lớp (V8). Phần C 2026-10-17 (plan `docs/plans/active/2026-10-17-parent-account-provisioning.md`): tài khoản phụ huynh gắn trẻ (SĐT, mật khẩu tạm hệ thống sinh), danh sách trẻ, gỡ liên kết, khóa/mở, mật khẩu phụ huynh (V9).

| Hạng mục | Trạng thái |
| --- | --- |
| Scaffold BE | **Có** — Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17, Flyway, springdoc, Docker Compose |
| Response/exception chuẩn | **Có** — `ApiCode`, `ResponseJson`, `GlobalException`, `GlobalExceptionHandler`, `PageResponse`, `ResponseContractTest` |
| Prefix API cấu hình được | **Có** — `carenest.api.prefix` (`WebMvcConfig`), mặc định `/api` |
| Coding guide | **Có** — `docs/backend-coding-guide.md` |
| Docs nghiệp vụ / kiến trúc / memory | Bản đầu |
| Authentication/authorization | **Xong trên `feature/authentication`, chờ merge** — Keycloak + Resource Server (ADR-0011): kiểm JWT, role, 401/403/503, `user_accounts` + `GET /accounts/me`, bảng scope (V2/V3) + seed dev, policy campus/lớp/trẻ, `GET /children/{childId}`, cấp/khóa tài khoản qua Keycloak Admin API (Admin không tự khóa mình — AUTH-09). Chưa có: API quản lý phân công/liên kết phụ huynh. Giới hạn: KI-003..KI-009 (`docs/plans/active/2026-10-04-keycloak-auth.md`) |
| Schema/migration nghiệp vụ | Bắt đầu — V1 `user_accounts`, V2 campus/năm học/lớp/phân công, V3 trẻ/enrollment/liên kết PH, V7 phân quyền, V8 `primary_role`, V9 quyền phụ huynh, V10 nhóm tuổi + phân công Tổ trưởng, V11–V12 kế hoạch năm học `edu_*`, V13–V20 vòng đời năm học, điểm trường, phòng, giờ chốt, phân công GV/tổ trưởng, xếp lớp, snapshot năm, V21 điểm danh, V22 sức khỏe trong ngày, V23 bỏ bảng duyệt thừa, V24 khóa Đang sửa, V25 trạng thái điểm trường, V26 + V29 số suất, V27 cấu hình kế hoạch, V28 hồ sơ kế hoạch lớp, V30–V35 phiên sửa, học liệu, phiên bản, danh mục Bộ, ba bảng mục tiêu, V36 bỏ bảng gợi ý `edu_expected_results` rỗng, V37 danh mục Bộ 3 tầng + liên kết nguồn cho lĩnh vực con/nhóm, V38 cột `TEAM_LEADER` trong ma trận quyền; seed dev `db/dev` |
| OpenAPI export (`docs/api/`) | Chưa |
| AI provider | Chốt Gemini API, Groq (AI-09, ADR-0007); chưa có adapter |
| Hạ tầng deploy | VPS demo qua Docker Hub private + Caddy (`docs/system/DEPLOYMENT.md`, `compose.vps.yaml`); dữ liệu seed dev; compose dev/demo giữ cho máy dev |

## Feature

| Module (feature) | Thiết kế | Code | Ghi chú |
| --- | --- | --- | --- |
| identity-access (`account`, `security/`) | Có | Gần xong | Resource Server, CurrentUser, policy, phân quyền cấu hình được (ADR-0017), HT/PHT cấp/khóa tài khoản, mật khẩu tạm, danh sách nhân sự, phân công (AUTH-15..17), tài khoản phụ huynh gắn trẻ (AUTH-18, AUTH-19) đã vào `dev`. Khảo sát 27/10: xem liên cơ sở (AUTH-20), chặn Web cho bếp/phụ huynh ở đăng nhập/refresh/quên mật khẩu (AUTH-21), cột Tổ trưởng (AUTH-23) xong trên nhánh G94-60, chưa vào `dev`. Xem liên cơ sở chỉ qua `canRead*`, đường ghi giữ `canView*`. AUTH-03 mở rộng 29/10 (GV cũ xem lại lớp/năm từng dạy, chỉ đọc): đã áp cho điểm danh, sức khỏe trong ngày (G94-226); module khác chỉ theo phân công còn hiệu lực — policy chung còn làm (plan `2026-10-27-survey-auth-alignment.md` Phase 9) |
| school-structure (`organization`) | Có | Bảng scope + phân công + đọc campus/lớp (G94-60); API năm học, điểm trường, phòng, nhóm tuổi, lớp, giờ chốt, phân công GV/tổ trưởng (G94-226) | Card `docs/modules/school-structure.md` |
| child (`child`) | Có | Bảng scope + `GET /children/{childId}` | Mới có hồ sơ cơ bản. Rule chốt 28/10: CHD-01..05, dị ứng HLT-06; gửi tài khoản phụ huynh qua SMS (AUTH-18) chưa làm — code hiện in phiếu |
| attendance (`attendance`) | Có | Điểm danh theo lớp/ngày, mở lại ngày, sức khỏe trong ngày (G94-226; viết theo rule trước khảo sát 28/10 — đối chiếu lại) | Rule chốt 28/10 theo khảo sát team: một giờ chốt HT cấu hình, không nhận trẻ đến muộn, PHT mở lại có lý do, có mặt là ăn (ATT-01..11). Đón trẻ chốt 29/10 (PCK-01..04). Còn P-19, P-20, P-33 |
| nutrition (`meals`) | Có | Sổ số suất: tổng hợp, xác nhận, điều chỉnh (G94-226; đối chiếu lại NUT-01..04) | Rule chốt 28/10: thực đơn chung hai cơ sở, PHT cơ sở xác nhận suất, kho khô trừ dự kiến + điều chỉnh, hàng tươi PHT ghi, danh mục NCC, dị ứng 2 mức (NUT-01..23; NUT-14/15 REMOVED). Thiết kế bảng (`MEAL_NUTRITION_AI_SCHEMA.md`) chưa cập nhật theo bản 28/10 — xem §13. Còn P-24..P-27 |
| health (`health`) | Có | — | Chốt 29/10: chuẩn WHO, GV cân đo, công bố cho phụ huynh, sức khỏe trong ngày (HLT-01..14). Kiểm tra đầu ngày HLT-07 (chốt 28/10), còn P-21..P-23 |
| learning-observation (`observation`) | Có | — | Chốt 29/10: đánh giá ngày 7 mặt 3 mức, nhận xét định kỳ GV xác nhận, phiếu bé ngoan, khen cuối năm (OBS-01..14). Ngữ cảnh hoạt động = giáo án đã duyệt (ADR-0006); đánh giá theo MT: FE mock. Còn P-11, P-30, P-31 |
| education-plan (`eduplan`) | Có (ADR-0020, EDU-01..28, EDU-39..41, flow `education-plan`) | Mục tiêu năm học (khóa Đang sửa), cấu hình kế hoạch, tài liệu lớp (chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày, hồ sơ chủ đề): API + service + test (G94-226) | Có thêm: phiên bản kế hoạch năm, danh mục mục tiêu của Bộ, ba bảng mục tiêu, học liệu S3. BE chưa có: AI gợi ý. Quyền theo role tại `EduPlanAccessPolicy` (EDU-08), không theo ma trận. User chốt 29/10: kế hoạch giáo dục theo code nhánh — ADR-0019 của `dev` SUPERSEDED bởi ADR-0020; khảo sát EDU-31, EDU-32, EDU-35, EDU-38 SUPERSEDED (thay bằng EDU-18, EDU-17..19, EDU-11/16, EDU-08). Lịch chủ đề EDU-39..41 (đóng P-29, 30/10): chủ đề Thứ 2 → Thứ 6, không trùng; nghỉ lẻ ngày không bỏ tuần. FE đã có UI đủ luồng và gọi BE thật khi `VITE_USE_MOCK=false`. Trang "Kế hoạch toàn trường" của Hiệu trưởng còn đọc dữ liệu chủ đề/giáo án kiểu cũ nên trống khi nối BE |
| notification | Có | — | Thông báo trường do BGH gửi, push + trong App (PAR-05); push provider chưa chốt |
| audit | Có | — | |
| ai-assistance (`integration/`) | Có | — | Provider chốt Gemini API, Groq (AI-09); 5 tính năng (AI-07) |
| reporting (`report`) | Có | — | Báo cáo ngày, định kỳ, Excel + PDF (RPT-01..03). Còn P-34 |

## Giới hạn hiện tại

Có xác thực Keycloak, phạm vi campus/lớp/trẻ và cấp/khóa tài khoản; chưa có nghiệp vụ điểm danh trở đi. Mọi rule PENDING (P-xx trong `BUSINESS_RULES.md`) chưa có hành vi chốt ⇒ làm cấu hình được, không hard-code.

## Thứ tự triển khai (guide mục 15)

1. Nền móng — **xong**.
2. Đăng nhập và phạm vi: user/role, campus/class assignment, liên kết phụ huynh–trẻ, test quyền — xác thực + phạm vi **xong** (ADR-0011, chờ merge); API quản lý phân công/liên kết chưa làm.
3. Điểm danh → BGH xác nhận số ăn → bếp xem.
4. Hồ sơ trẻ: đánh giá/sức khỏe, chia sẻ phụ huynh, audit, lịch sử.
5. Mở rộng: thông báo, thực đơn, kế hoạch giáo dục; AI sau khi dữ liệu + baseline sẵn sàng. (CSVC đã loại 29/10.)
