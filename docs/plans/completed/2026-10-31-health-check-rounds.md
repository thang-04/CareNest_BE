---
title: BE đợt khám sức khỏe, kỳ cân đo và số đo trong kỳ
status: done
owner: carenestg94@gmail.com
jira: G94-180 / BE-FEAT-43
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [health]
rules: [HLT-01, HLT-08, HLT-09, HLT-11, HLT-13, G94-180/HLT-08]
risk: high
created: 2026-10-31
updated: 2026-10-31
---

# BE đợt khám sức khỏe, kỳ cân đo và số đo trong kỳ

## Context

- User (2026-10-31): "hãy code BE cho phần đợt khám sức khỏe của tôi". FE đã có mock và contract đề xuất: `FE:docs/plans/completed/2026-10-31-health-measurement-rounds.md`, `FE:src/services/children/api/healthCheckApi.js`, `FE:src/services/children/api/childrenApi.js`.
- BE chưa có bảng, API nào cho đợt khám hay số đo (`docs/modules/health.md` mục "API & bảng": "Chưa có"). Migration mới nhất: `V39__child_enrollment_guardian_accounts.sql`.
- Rule BE còn ghi theo khảo sát cũ: HLT-01 "mặc định 3 lần/năm, chu kỳ cấu hình được" (`docs/business/BUSINESS_RULES.md:128`), HLT-08 "2 lần/năm" (`:135`). User chốt lại 2026-10-31 (plan FE).

## Quyết định đã chốt

1. Đợt khám có loại `EXAM` (bác sĩ khám, thường 1 lần/năm học, không chặn) và `MEASUREMENT` (kỳ cân đo, khoảng 3 tháng/lần); đợt khám cũng là một kỳ cân đo — user, 2026-10-31.
2. HT quản lý mọi điểm trường, PHT điểm trường của mình; GV nhập cho lớp mình (HLT-09) — user, 2026-10-31.
3. Ngày bắt đầu cách đợt/kỳ khác chưa hủy cùng điểm trường dưới 2 tháng 15 ngày, hoặc đợt khám mà năm học đã có đợt khám ⇒ cảnh báo, vẫn lưu được; không nhắc tự động — user, 2026-10-31.
4. Số đo chỉ nhập trong kỳ cân đo đang diễn ra; không cân đo lẻ — user, 2026-10-31.
5. Hoãn báo cáo 5.1, 5.3, phân loại WHO cho báo cáo, nhóm bệnh — user, 2026-10-31.

**Mặc định (tự chọn theo chế độ tự chạy, user đổi được):**
- **B1 — Phạm vi đợt này:** đợt khám/kỳ cân đo (danh sách, chi tiết, danh sách lớp, tạo, sửa, hủy, đóng, mở lại, cảnh báo lịch) và số đo của kỳ cân đo (nhập, sửa có lý do, Sổ sức khỏe rút gọn). **Chưa làm:** phiếu khám Phụ lục II, phiếu theo dõi, mức theo dõi — nghiệp vụ còn lệch HLT-08 (nội dung khám chưa có rule), HLT-10, HLT-12 (`docs/context/CURRENT_STATE.md`); làm ở đợt sau khi chốt.
- **B2 — Công bố:** lưu số đo là công bố (`published_at` = lúc lưu), theo quyết định user 2026-10-23 (FE `docs/plans/completed/2026-10-23-auto-publish-health-results.md`). HLT-11 (khảo sát 27/10) ghi "GV hoặc PHT công bố" ⇒ lệch; theo thứ tự ưu tiên (`AGENTS.md` nguyên tắc 2: quyết định có nguồn > khảo sát) chọn tự công bố, ghi ở báo cáo để user soát.
- **B3 — Quyền:** theo role + phạm vi điểm trường (`CampusAccessPolicy`, như mở lại điểm danh), không thêm mã quyền mới vào ma trận.
- **B4 — Lỗi theo ô:** 400 `INVALID_REQUEST_DATA` + ô theo quy ước BE (C-5), không dùng 422 như mock FE; PHT tạo cho điểm trường khác ⇒ 403 `CAMPUS_ACCESS_DENIED`.
- **B5 — Không thông báo:** BE chưa có module thông báo; tạo/mở lại không gửi thông báo cho GV.
- **B6 — Vắng:** đóng đợt/kỳ lưu danh sách trẻ chưa có kết quả (bảng `health_check_absences`, kèm lớp); mở lại xóa danh sách.
- **B7 — Một kết quả/trẻ/kỳ, một lần đo/trẻ/ngày:** ràng buộc DB.
- **B8 — Sổ sức khỏe:** `GET /children/{childId}/health` trả phần BE có: `measurements`, `openCampaign`, `headCircumferenceUntil`. Hồ sơ trẻ, khai báo, phiếu theo dõi ở API khác/đợt sau.

## Làm rõ nghiệp vụ

- **Hiểu nghiệp vụ:** HT/PHT mở đợt/kỳ cho điểm trường → `[S]` cảnh báo lịch → GV nhập số đo từng trẻ lớp mình trong kỳ → HT/PHT đóng → `[S]` trẻ chưa có kết quả thành vắng. Rule: HLT-01, HLT-08 (sửa theo user 2026-10-31), HLT-09, HLT-13, G94-180/HLT-08 (đợt thuộc năm học của ngày bắt đầu).
- **Đối chiếu:** HLT-01, HLT-08 cũ ⇒ sửa ở `BUSINESS_RULES.md`. HLT-11 ⇒ B2.
- **Ảnh hưởng:** module health (mới), child (mã trẻ), organization (tên điểm trường). FE: `/children/health-checks*`, `/children/:id/health*` (đang dùng mock). APP: chưa dùng.

### Câu hỏi mở

Không còn câu hỏi chặn; mặc định B1–B8 ghi ở trên.

## API

| Method | Path | Ai | Ghi chú |
| --- | --- | --- | --- |
| GET | `/health-checks?campusId&kind&status&schoolYear` | HT, PHT, GV | Đợt/kỳ trong phạm vi; năm học theo ngày bắt đầu |
| GET | `/health-checks/schedule-warnings?campusId&kind&startDate&excludeId` | HT, PHT | `{ warnings: [{ code, campaignId, name, kind, startDate }] }` |
| GET | `/health-checks/{id}` | HT, PHT, GV | `{ campaign, classes, watchList }` (watchList rỗng tới khi có phiếu khám) |
| GET | `/health-checks/{id}/classes/{classId}/children` | HT, PHT, GV | `{ campaign, cls, children, canRecord }` |
| POST | `/health-checks` | HT, PHT | `kind` mặc định `EXAM` |
| PUT | `/health-checks/{id}` | HT, PHT | Không đổi `kind`, điểm trường |
| POST | `/health-checks/{id}/cancel` · `/close` · `/reopen` | HT, PHT | Mở lại cần `reason` |
| GET | `/children/{childId}/health` | HT, PHT, GV | B8 |
| POST | `/children/{childId}/health/measurements` | GV lớp | Bắt buộc `campaignId` kỳ cân đo đang diễn ra |
| PUT | `/children/{childId}/health/measurements/{measurementId}` | GV lớp | Bắt buộc `reason`; lưu lịch sử (HLT-13) |

## Acceptance criteria

- **AC-1** (`HLT-08`): HT tạo đợt/kỳ cho mọi điểm trường; PHT chỉ điểm trường của mình (khác ⇒ 403); GV không tạo được.
- **AC-2** (`HLT-01`): kỳ cân đo lưu `examiner` rỗng; `kind` không đổi khi sửa; danh sách lọc được theo loại, trạng thái, năm học.
- **AC-3** (`HLT-01`): cảnh báo `TOO_CLOSE` khi ngày bắt đầu cách dưới 2 tháng 15 ngày (cả hai chiều), không tính đợt/kỳ đã hủy, bỏ qua chính nó khi sửa; đúng ngưỡng thì không cảnh báo.
- **AC-4** (`HLT-08`): đợt khám mới mà năm học đã có đợt khám chưa hủy ⇒ `EXAM_IN_SCHOOL_YEAR`; vẫn tạo được.
- **AC-5**: mỗi điểm trường một đợt/kỳ chưa đóng (tạo, mở lại khi đang có đợt khác ⇒ 409).
- **AC-6** (`HLT-09`): GV lớp nhập số đo trong kỳ đang diễn ra; ngày ngoài kỳ ⇒ 400; trẻ đã có số đo trong kỳ ⇒ 409; đóng kỳ ⇒ trẻ chưa cân là `ABSENT`, sửa ⇒ 409; mở lại ⇒ hết vắng.
- **AC-7**: không có `campaignId` hoặc là đợt khám ⇒ 400; GV lớp khác ⇒ 403.
- **AC-8** (`HLT-13`): sửa số đo bắt buộc lý do, lưu lịch sử thay đổi, công bố lại; không đổi gì thì không ghi lịch sử.
- **AC-9**: hủy chỉ khi chưa có kết quả; đổi ngày không được loại kết quả đã có ra ngoài khoảng.

## Phases

### Phase 1 — Migration, entity, repository, service, API, test
- Files: C `V40__create_health_checks.sql`, `entity/health/*`, `repository/health/*`, `service/health/*`, `controller/health/*`, `dto/health/*`, `utils/enums/HealthCheck*.java`, test `HealthCheckIntegrationTest`; M `ApiCode`, `ChildService(+Impl)`, `SchoolStructureService(+Impl)`.
- Exit: `node scripts/verify.mjs` → PASS.

### Phase 2 — Tài liệu
- M `BUSINESS_RULES.md` (HLT-01, HLT-08), `modules/health.md`, `flows/health-check.md`, `database/DATA_DICTIONARY.md`, `contracts/ERROR_CONTRACT.md`, `api/openapi.yaml` (snapshot), `context/CURRENT_STATE.md`.
- Exit: `node scripts/check-ai-layer.mjs` pass; `node scripts/verify.mjs` → PASS.

## Test matrix

| AC | Rule | Test | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-2 | HLT-08, HLT-01 | `HealthCheckIntegrationTest#leadersManageTheirCampusesAndTeachersOnlyView` | 1 | Đạt |
| AC-3 | HLT-01 | `#warnsWhenStartIsWithinTwoMonthsFifteenDaysOfAnotherRound` | 1 | Đạt |
| AC-4 | HLT-08 | `#secondExamInSchoolYearWarnsButCanBeCreated` | 1 | Đạt |
| AC-5 | HLT-01 | `#oneUnclosedRoundPerCampusAcrossKinds` | 1 | Đạt |
| AC-6, AC-7, AC-9 | HLT-09 | `#teacherRecordsMeasurementsOnlyInsideOpenRound` | 1 | Đạt |
| AC-8 | HLT-13 | `#editingMeasurementNeedsReasonAndKeepsHistory` | 1 | Đạt |

## Rủi ro & rollback

| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Lệch contract FE (400 thay 422, Sổ sức khỏe rút gọn) | FE cần sửa lớp API khi tích hợp | Ghi rõ ở "Khác FE" | — |
| B2 lệch HLT-11 | Phụ huynh thấy số đo ngay | Báo user soát | Thêm bước công bố, giữ `published_at` |
| Migration mới | — | Chỉ thêm bảng, không sửa bảng cũ | Migration mới DROP bảng |

## Validation log

- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer: FE `healthCheckApi.js`, `childrenApi.js` (mock đang dùng); APP chưa dùng
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] >8 file ⇒ module mới, không tách được thêm có ích
- [x] Không dùng rule PENDING/OPEN như đã chốt

## Progress log

### 2026-10-31 — Plan (tự chạy, không chờ duyệt theo CLAUDE.md)
- Tiếp: Phase 1.

### 2026-10-31 — Phase 1 (done)
- Commit: chưa commit
- Test `HealthCheckIntegrationTest` (6 test) chạy đỏ trước (chưa có bean service), xanh sau khi cài đặt.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 657 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`
- Snapshot OpenAPI cập nhật có chủ đích (`-Dopenapi.snapshot.update=true`): thêm 10 đường dẫn `/api/health-checks*`, `/api/children/{childId}/health*`; không schema cũ nào đổi.
- Khác plan:
  - Lỗi dùng mã riêng 5009–5026 (không trả lỗi theo ô); 5010 `HEALTH_CHECK_ACCESS_DENIED` thay cho `CAMPUS_ACCESS_DENIED` khi PHT tạo cho điểm trường khác.
  - Thêm hàm tra cứu liên module: `ChildService.findChildBriefs` (mã, họ tên, ngày sinh) và `SchoolStructureService.findCampusNames` (ADR-0001: lấy dữ liệu feature khác qua service).
  - Danh sách lớp trả thêm `heightCm`, `weightKg`; sổ sức khỏe trả `canRecord`.
  - Lần thử đầu đặt tên record lồng `History`, `Child`… ⇒ springdoc ghi đè schema `History` có sẵn trong snapshot. Đổi mọi record lồng sang tên riêng (`HealthCheckHistory`, `HealthCheckChild`…). Ghi vào Known pitfalls của `docs/modules/health.md`.

### 2026-10-31 — Phase 2 (done)
- Tài liệu: `BUSINESS_RULES.md` (HLT-01, HLT-08), `modules/health.md`, `flows/health-check.md`, `database/DATA_DICTIONARY.md`, `contracts/ERROR_CONTRACT.md`, `context/CURRENT_STATE.md`, `api/openapi.yaml`.
- Khác FE (khi tích hợp, sửa lớp API FE): lỗi theo mã thay cho 422 + ô; id số thay cho chuỗi; `GET /children/{id}/health` chỉ có `measurements`, `openCampaign`, `headCircumferenceUntil`, `canRecord`; danh sách lớp chưa có trường của phiếu khám (`previous`, `conclusion`, `followUp`…); không có `byConclusion` trong `progress`.
- Chờ user: B2 (tự công bố, lệch HLT-11); phạm vi đợt sau (phiếu khám Phụ lục II, phiếu theo dõi — cần chốt HLT-08 nội dung khám, HLT-10, HLT-12).
