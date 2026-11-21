---
title: Sức khỏe theo năm học — năm của đợt khám lưu lúc tạo, nhóm tuổi báo cáo theo năm
status: approved     # user duyệt 22/11: "duyệt và hoàn thành tất cả các phase"
owner: Giáp
jira: G94-180 / BE-FIX-10
branch: fix/G94-180-BE-FIX-10-school-year-scope   # cùng nhánh với plan 2026-11-19-intake-campus-in-year
modules: [health]
rules: [G94-180/HLT-08, HLT-08, HLT-17, HLT-20, G94-180/CHD-09]
risk: high           # migration thêm cột health_check_campaigns.school_year_id và điền dữ liệu cũ; thêm lỗi ngày khi tạo, sửa, mở lại đợt
created: 2026-11-22
updated: 2026-11-22
---
# Sức khỏe theo năm học — BE

Plan này làm các mục 4, 6 trong bản phân tích 22/11. Mục 1, 2, 5: `2026-11-19-intake-campus-in-year.md`.

## Context
- **Mục 6:** năm của đợt khám được tính từ ngày bắt đầu, kể cả năm PLANNED, và tính lại mỗi lần đọc (`HealthCheckRules.java:111-116`). Kết quả dùng ở:
  - `classesOf` (`:120-128`), từ đó ra danh sách trẻ theo lớp (`rosterOf`, ngày tham chiếu trong khoảng của đợt);
  - lọc danh sách đợt theo năm (`HealthCheckServiceImpl.java:94`);
  - cảnh báo đợt khám cùng năm (`:123`, `:137`);
  - báo cáo tăng trưởng (`HealthGrowthReportServiceImpl.java:127`) và thông báo (`HealthNotifier.java:72`).
  - Trẻ chỉ đổi lớp lúc kích hoạt năm (code của Cần, đóng lớp cũ từ ngày trước ngày bắt đầu năm mới). Vì vậy đợt quanh ngày chuyển năm có thể rỗng danh sách trẻ, hoặc đổi năm giữa chừng.
- **Mục 4:** báo cáo lấy nhóm tuổi bằng `ageGroupService.getAgeGroups()` (`HealthGrowthReportServiceImpl.java:142`). Đây là bảng chung, theo V154 của Cần luôn mang giá trị của năm đang hoạt động. Tháng của năm khác bị chia nhà trẻ / mẫu giáo theo cấu hình năm hiện tại.
- Bảng `health_check_campaigns` tạo ở `V64__create_health_checks.sql:3`. Seed `R__dev_05_evaluation_health.sql:662` (của thangnd) và `ThemeEvaluationIntegrationTest.java:507` chèn đợt khám không có cột năm.

## Quyết định đã chốt
1. Cách B: lưu `school_year_id` vào đợt khám lúc tạo, bằng năm đang hoạt động. Không đổi về sau, kể cả khi kích hoạt năm mới — user 22/11.
2. Báo cáo tăng trưởng lấy nhóm tuổi theo năm của lớp — user 22/11.
3. Không sửa code của Cần, seed của thangnd, test của module khác.

## Làm rõ nghiệp vụ
- Actor: HT, PHT tạo, sửa, mở lại đợt; GV nhập kết quả. Luồng `[S]`.
- Đối chiếu: G94-180/HLT-08 (`BUSINESS_RULES.md:413`) ghi "đợt khám thuộc năm học của ngày bắt đầu khám" ⇒ sửa thành "thuộc năm đang hoạt động lúc tạo (lưu cùng đợt)". HLT-08 (`:158`) "1 lần/năm học" vẫn so theo năm của đợt.
- Ảnh hưởng: Web đợt khám (tạo, sửa, mở lại hiện lỗi ngày dưới ô có sẵn, `HealthCheckFormPage.jsx:191`), Tổng hợp cân đo. APP không gọi.

### Câu hỏi mở
Không còn.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Năm của đợt khám | Lưu lúc tạo = năm đang hoạt động (cách B) | user | 22/11 | G94-180/HLT-08 (Phase 3) |
| Nhóm tuổi của báo cáo | Theo năm của lớp | user | 22/11 | HLT-20 ghi chú (Phase 3) |

## Acceptance criteria
- **AC-1** (G94-180/HLT-08): Given năm 2026-2027 đang hoạt động · When HT tạo đợt khám · Then đợt lưu `school_year_id` = 2026-2027; sau khi kích hoạt 2027-2028, danh sách lớp của đợt vẫn là lớp 2026-2027.
- **AC-2** (G94-180/HLT-08): tạo, sửa, mở lại đợt có ngày bắt đầu trước ngày bắt đầu năm đang hoạt động, hoặc ngày kết thúc từ ngày bắt đầu năm kế tiếp trở đi ⇒ 400 lỗi ô `startDate`/`endDate` "Ngày khám phải trong năm học {tên năm} ({từ} – {đến})".
- **AC-3** (G94-180/HLT-08): lọc danh sách đợt theo năm dùng năm đã lưu; đợt cũ (trước migration) có năm điền sẵn theo luật cũ.
- **AC-4** (HLT-08): cảnh báo "năm học này đã có đợt khám" so theo năm đã lưu.
- **AC-5** (HLT-20): năm 2026-2027 có khoảng tháng tuổi khác bảng chung ⇒ báo cáo tháng của năm 2026-2027 chia nhà trẻ / mẫu giáo theo cấu hình 2026-2027.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Cột năm | `school_year_id` cho phép null; migration điền dữ liệu cũ; null ⇒ tính theo luật cũ (`schoolYearIdOf`) | NOT NULL | Seed của thangnd và test module khác chèn đợt không có cột này; không phải sửa file của người khác |
| Khoảng ngày hợp lệ | Từ ngày bắt đầu năm đang hoạt động tới trước ngày bắt đầu năm kế tiếp (kỳ hè tính vào năm vừa qua, CHD-09) | không giới hạn | Không có khoảng này thì đợt tạo trước kích hoạt nhưng khám sau ngày đầu năm mới vẫn rỗng danh sách trẻ |
| Nguồn nhóm tuổi theo năm | `ageGroupService.getYearAgeGroups(cls.schoolYearId())` (public, của Cần, chỉ gọi), lưu tạm theo năm | sửa `getAgeGroups` của Cần | Không đụng module Cần |
| Số migration | Số kế tiếp của `origin/dev` lúc làm (hiện dev tới V166 ⇒ V167) | — | Pre-push guard G94-440 chặn trùng số |

## Phases
### Phase 1 — Lưu năm của đợt khám (G94-180)
- Files: C `db/migration/V167__health_check_campaign_school_year.sql` · M `entity/health/HealthCheckCampaign.java` · M `HealthCheckRules.java` (:111-128, thêm `yearOf(campaign)`) · M `HealthCheckServiceImpl.java` (:94, :123, :137, tạo :236, sửa :274, mở lại :406) · Test M `HealthCheckIntegrationTest.java`.
- Steps:
  1. Viết test (fail) AC-1..AC-4.
  2. Migration: thêm cột (FK `school_years`), điền theo luật cũ (năm có ngày bắt đầu gần nhất ≤ ngày bắt đầu đợt), thêm index.
  3. `yearOf(campaign)` = `schoolYearId` đã lưu, null thì `schoolYearIdOf(startDate)`. Thay mọi chỗ đang gọi `schoolYearIdOf(c.getStartDate())`.
  4. Tạo đợt: lưu năm đang hoạt động; kiểm khoảng ngày (AC-2). Sửa, mở lại: kiểm theo năm đã lưu.
- Exit: `.\mvnw.cmd -q test -Dtest=HealthCheckIntegrationTest,HealthCheckServiceImplTest` pass.
- Commit: `G94-180: Store the school year of each health check campaign`.
- **Dừng chờ user review.**

### Phase 2 — Nhóm tuổi của báo cáo theo năm (G94-180)
- Files: M `HealthGrowthReportServiceImpl.java` (:142, chỗ dùng `groups`) · Test M `HealthGrowthReportIntegrationTest.java`.
- Steps: test (fail) AC-5, đổi khoảng tháng tuổi của năm cũ bằng SQL trên `school_year_age_group_members`; thay `getAgeGroups()` bằng `getYearAgeGroups(cls.schoolYearId())` theo từng năm.
- Exit: `.\mvnw.cmd -q test -Dtest=HealthGrowthReportIntegrationTest,HealthGrowthLevelTest` pass.
- Commit: `G94-180: Classify growth reports by the age groups of the class year`.
- **Dừng chờ user review.**

### Phase 3 — Tài liệu và verify (G94-180)
- Files: M `BUSINESS_RULES.md` (G94-180/HLT-08 :413; ghi chú HLT-20 :170) · M `docs/modules/health.md`.
- Exit: `.\mvnw.cmd -q clean spotless:check verify` exit 0, test skip = 0 (Docker Desktop đang chạy).
- Commit: `G94-180: Document the stored school year of health checks`.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | G94-180/HLT-08 | `campaignKeepsItsYearAfterActivation` (IT) | 1 | |
| AC-2 | G94-180/HLT-08 | `createRejectsDatesOutsideActiveYear`, `updateRejectsDatesOutsideStoredYear` (IT) | 1 | |
| AC-3 | G94-180/HLT-08 | `listFiltersByStoredYear` (IT) | 1 | |
| AC-4 | HLT-08 | `examWarningComparesStoredYears` (IT) | 1 | |
| AC-5 | HLT-20 | `reportUsesAgeGroupsOfClassYear` (IT) | 2 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Điền năm cho đợt cũ sai khi có đợt trước mọi năm học | Đợt không có năm | Để null ⇒ tính theo luật cũ như hiện tại | Migration chỉ thêm cột; revert code là về hành vi cũ |
| Chặn ngày làm HT không tạo được đợt trong 7 ngày kích hoạt sớm | Phải đợi ngày bắt đầu năm mới | Câu lỗi nói rõ khoảng ngày hợp lệ | Nới khoảng ngày nếu nghiệp vụ yêu cầu |
| Trùng số migration với nhánh khác | Flyway lỗi khi gộp | Lấy số kế tiếp của dev ngay trước khi commit; G94-440 chặn | Đổi số trước khi push |

## Validation log
- [x] Khẳng định về code có `file:line` (đọc trên `be-new-year-intake` @3f0f9a25).
- [x] Đổi contract ⇒ consumer cụ thể: Web `HealthCheckFormPage` (lỗi ngày dưới ô, có sẵn `:191`), `HealthCheckDetailPage` (mở lại). Response không đổi field bắt buộc.
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả.
- [x] 3 phase, 7 file. Không cần tách.
- [x] Không dùng rule PENDING/OPEN như đã chốt.
- [x] Không placeholder, không mâu thuẫn.

## Progress log
### 2026-11-22 — plan draft
- Commit: chưa commit.
- Tiếp: user duyệt plan ⇒ `status: approved`, làm Phase 1 sau plan tiếp nhận (cùng nhánh).
### 2026-11-22 — duyệt
- User duyệt plan và yêu cầu làm hết các phase liên tục (không dừng giữa phase). Nhánh tạo trong worktree riêng.
### 2026-11-22 — Phase 1, 2 done
- Commit: `7eeab932` (năm của đợt, V169), báo cáo theo nhóm tuổi của năm (commit kế tiếp).
- Verify: `HealthCheckIntegrationTest` 25/25, `HealthCheckServiceImplTest` 6/6, `HealthExamIntegrationTest` 9/9, `HealthGrowthReportIntegrationTest` 6/6, `HealthGrowthLevelTest` 2/2, `HealthGrowthReportControllerTest` 8/8.
- Khác plan: migration là V169 (V167, V168 đã có ở nhánh khác); test kích hoạt năm cần `entityManager.clear()` sau khi đổi trạng thái năm bằng SQL.
