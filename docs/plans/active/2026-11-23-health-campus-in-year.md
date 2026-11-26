---
title: Đợt khám, kỳ cân đo chỉ tạo ở điểm trường có và đang hoạt động trong năm của đợt
status: draft        # user 23/11: ghi vào plan để làm sau, chưa làm lúc này
owner: Giáp
jira: G94-180 / BE-FIX-10
branch: fix/G94-180-BE-FIX-10-school-year-scope
modules: [health]
rules: [G94-180/HLT-08, HLT-08, G94-180/CHD-02]
risk: medium         # không migration, không đổi DTO; POST /health-checks có thêm 2 lỗi (2125, 2113) ở ô campusId
created: 2026-11-23
updated: 2026-11-23
---
# Đợt khám, kỳ cân đo chỉ tạo ở điểm trường có và đang hoạt động trong năm của đợt

Làm sau, chưa làm lúc này (user 23/11). Chỉ BE; FE, APP không đổi.

## Context
- Phát hiện khi test ca D3 (file test ngoài repo `TEST-tac-dong-Can-2026-11-23.md`, 23/11). Gọi `POST /api/health-checks` với điểm trường id 4, chỉ thuộc 2027-2028, ngày 24–30/11/2026. Kết quả 201: đợt id 5 lưu `school_year_id` của 2026-2027, `progress.total` = 0 (đợt rỗng).
- `createHealthCheck` (`HealthCheckServiceImpl.java:233-270`):
  - chỉ kiểm điểm trường tồn tại (`schoolStructureService.findCampusNames`, `:241-245`);
  - lấy năm đang hoạt động (`rules.activeYearWindow()`, `:247`) và lưu năm đó (`:253`);
  - không kiểm điểm trường thuộc năm, không kiểm trạng thái của điểm trường trong năm.
- Sửa đợt (`updateHealthCheck`, `:274-300`) không đổi được điểm trường: `campaign.update` chỉ đổi tên, ngày, đơn vị khám, ghi chú. Vì vậy chỉ cần kiểm lúc tạo.
- Tiếp nhận đã kiểm việc này: `ChildIntakeServiceImpl.requireCampusInYear` (`:1212-1220`, G94-180/CHD-02, user chốt 22/11) gọi `campusService.getCampuses(actor, schoolYearId)` rồi báo 2125 ở ô `campusId`.
- Mở lớp (code Cần) chặn cả hai trường hợp: không thuộc năm ⇒ 2125, ngừng hoạt động trong năm ⇒ 2113 (`ClassManagementServiceImpl.java:340-348`).
- `CampusService.getCampuses` (`CampusServiceImpl.java:78-110`) trả điểm trường của năm (V151), trạng thái lấy theo năm (`withYearInfo`, V154). Người không xem được mọi điểm trường nhận danh sách rỗng (`:84-86`). Chỉ HT và PHT tạo được đợt (`rules.canManage`), nên không gặp danh sách rỗng.
- FE: form tạo đợt gọi `useMasterData()` không truyền năm (`FE:src/pages/children/HealthCheckFormPage.jsx:71`, `:146`), nên chỉ liệt kê điểm trường của năm đang hoạt động. Lỗi chỉ lộ khi gọi API thẳng.

## Quyết định đã chốt
1. Ghi vào plan, chưa làm lúc này — user, 23/11.
2. Điểm trường của đợt phải có trong năm của đợt (năm đang hoạt động lúc tạo). Không có ⇒ 2125 ở ô `campusId`, giống CHD-02 khi tiếp nhận — đề xuất 23/11, user cho ghi vào plan.
3. Điểm trường có trong năm nhưng "Ngừng hoạt động" ở năm đó ⇒ chặn tạo đợt mới, mã 2113, giống luật mở lớp của Cần — đề xuất "chặn luôn" 23/11, user cho ghi vào plan.
4. Chỉ chặn khi tạo đợt. Đợt đã có ở điểm trường sau này ngừng hoạt động thì giữ nguyên: vẫn xem, nhập kết quả, đóng như cũ.
5. Không có năm đang hoạt động (`year` null) ⇒ giữ hành vi cũ, không kiểm.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** HT (mọi điểm trường) và PHT (điểm trường được phân công) tạo đợt khám, kỳ cân đo. Mục tiêu: đợt luôn thuộc một điểm trường có trẻ của năm đó. Rule G94-180/HLT-08 (năm của đợt lưu lúc tạo, V183) và HLT-08. Flow `[S]` tạo đợt.
- **Đối chiếu thiết kế ban đầu:** `BUSINESS_RULES.md:417` (G94-180/HLT-08) chỉ ghi năm của đợt, chưa ghi điểm trường phải thuộc năm.
- **Ảnh hưởng:**
  - module health gọi service của organization (`CampusService`), đúng chiều phụ thuộc, không sửa code của Cần;
  - FE không đổi; APP không tạo đợt;
  - dữ liệu cũ: chỉ có đợt id 5 tạo khi test trên DB local `carenest_scope2`, hủy tay.

### Câu hỏi mở
Không còn câu hỏi mở.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Điểm trường "Ngừng hoạt động" trong năm có chặn tạo đợt mới không? | Chặn (mã 2113), ghi vào plan | Giáp | 2026-11-23 | Chưa (Phase 1, bước 4) |

## Acceptance criteria
- **AC-1** (`G94-180/HLT-08`): Given điểm trường chỉ thuộc năm PLANNED; When HT tạo đợt với ngày trong năm đang hoạt động; Then 400, mã 2125, lỗi ở ô `campusId`, không lưu đợt.
- **AC-2** (`G94-180/HLT-08`): Given điểm trường thuộc năm đang hoạt động nhưng "Ngừng hoạt động" ở năm đó; When HT tạo đợt; Then 409, mã 2113, không lưu đợt.
- **AC-3** (`HLT-08`): Given điểm trường thuộc năm và đang hoạt động; When HT hoặc PHT của điểm trường đó tạo đợt; Then 201 như cũ (các test tạo đợt hiện có vẫn xanh).

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Lấy điểm trường của năm | `campusService.getCampuses(currentUser, year.yearId())`, như tiếp nhận | Thêm method `isCampusInYear` vào `CampusService` | Không sửa module của Cần; cùng cách với CHD-02 |
| Thứ tự kiểm | Quyền → tồn tại (404) → ngày → thuộc năm (2125) → đang hoạt động (2113) → trùng lịch | Kiểm thuộc năm trước ngày | Giữ nguyên các lỗi cũ và thứ tự đang có |

## Phases
### Phase 1 — Chặn khi tạo đợt (plan chỉ có 1 phase)
- Files: M `src/main/java/com/carenest/service/health/impl/HealthCheckServiceImpl.java` · M `src/test/java/com/carenest/service/health/impl/HealthCheckIntegrationTest.java` · M `src/test/java/com/carenest/service/health/impl/HealthCheckServiceImplTest.java` (nếu cần mock thêm) · M `docs/business/BUSINESS_RULES.md` · M `docs/modules/health.md`
- Steps:
  1. Inject `CampusService` vào `HealthCheckServiceImpl` (nếu chưa có).
  2. Trong `createHealthCheck`, sau `checkWithinYear(year, …)`, khi `year != null`:
     - lấy `campusService.getCampuses(currentUser, year.yearId())`;
     - không có `campusId` ⇒ `GlobalException.withFieldErrors(ApiCode.CAMPUS_NOT_IN_YEAR, …)` với ô `campusId`, câu "Điểm trường không có trong năm học <tên năm>. Chọn điểm trường khác";
     - có nhưng `status() == CampusStatus.INACTIVE` ⇒ `GlobalException.of(ApiCode.CAMPUS_INACTIVE)`.
  3. Thêm comment 1 dòng dẫn rule G94-180/HLT-08.
  4. Tài liệu:
     - dòng G94-180/HLT-08 (`BUSINESS_RULES.md:417`) thêm "điểm trường của đợt phải có và đang hoạt động trong năm của đợt (như CHD-02 và luật mở lớp; user 23/11)";
     - `docs/modules/health.md` thêm 1 dòng tương ứng.
- Tests:
  - AC-1, AC-2 → 2 test mới trong `HealthCheckIntegrationTest`, `@DisplayName("G94-180/HLT-08: …")`. Tạo điểm trường chỉ thuộc năm PLANNED, và một điểm trường ngừng hoạt động trong năm đang hoạt động.
  - AC-3 → các test tạo đợt hiện có (`HealthCheckIntegrationTest.java:141-155`).
  - `HealthCheckServiceImplTest`: nếu dựng service bằng mock thì thêm `@Mock CampusService` và stub trả điểm trường của năm.
- Exit: `./mvnw.cmd "-Dtest=HealthCheckIntegrationTest,HealthCheckServiceImplTest" test` (Docker bật) → `BUILD SUCCESS`, 0 failure; rồi verify theo `docs/quality/VERIFICATION.md`.
- **Dừng chờ user review.** Commit khi user cho phép, ví dụ `G94-180: Reject health checks at campuses outside the campaign year`.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | G94-180/HLT-08 | `HealthCheckIntegrationTest` — điểm trường ngoài năm ⇒ 2125 (integration) | 1 | chưa chạy |
| AC-2 | G94-180/HLT-08 | `HealthCheckIntegrationTest` — điểm trường ngừng hoạt động ⇒ 2113 (integration) | 1 | chưa chạy |
| AC-3 | HLT-08 | Test tạo đợt hiện có (integration) | 1 | chưa chạy |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Form FE còn hiện điểm trường ngừng hoạt động | Bấm lưu mới thấy lỗi 2113 | Lỗi hiện qua toast hoặc ô điểm trường; muốn gọn hơn thì FE ẩn điểm trường ngừng hoạt động (việc riêng, ngoài plan) | Revert commit |
| Đã có đợt ở điểm trường ngoài năm (dữ liệu cũ) | Đợt rỗng còn trong danh sách | Trước khi phát hành, chạy câu SQL dò ở dưới | Hủy đợt |

Câu SQL dò đợt có điểm trường không thuộc năm của đợt:

```sql
SELECT h.id, h.name, h.campus_id, h.school_year_id
FROM health_check_campaigns h
WHERE NOT EXISTS (
  SELECT 1 FROM school_year_campus_members m
  WHERE m.school_year_id = h.school_year_id AND m.campus_id = h.campus_id);
```

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể: `POST /api/health-checks` có thêm lỗi 2125, 2113. FE `HealthCheckFormPage.jsx` không cần đổi (form không đưa ra điểm trường ngoài năm); APP không gọi endpoint này.
- [x] Mỗi AC có test; phase có Exit = lệnh + kết quả mong đợi
- [x] ≤8 file, 1 phase
- [x] Không dùng rule PENDING/OPEN như đã chốt
- [x] Tự review: không placeholder, không mâu thuẫn, scope vừa 1 plan

## Progress log
### 2026-11-23 — Lập plan (draft)
- Commit: chưa commit.
- Nguồn: test D3 ngày 23/11. Đợt thử id 5 ("Thử D3", điểm trường id 4) còn trên DB local `carenest_scope2`, cần hủy tay.
- Tiếp: khi user bảo làm ⇒ chuyển `approved` rồi `in-progress`, làm Phase 1.
