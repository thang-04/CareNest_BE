---
title: Cảnh báo sức khỏe ở sổ sức khỏe, mức BMI kỳ cân đo, bỏ phiếu theo dõi
status: in-progress   # chế độ tự chạy (CLAUDE.md, user chốt 2026-10-23): plan tự viết rồi làm tiếp, user soát khi bàn giao
owner: user (CareNest team)
jira: G94-180 / BE-FEAT-43 (nhánh hiện tại; task Jira riêng cho thay đổi này chưa có — hỏi khi commit)
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [health]
rules: [HLT-03, HLT-10, HLT-11, HLT-12, HLT-13, HLT-16, HLT-17, HLT-18, HLT-19]
risk: high   # đổi API contract (bỏ endpoint, đổi DTO), ảnh hưởng FE; không migration
created: 2026-11-04
updated: 2026-11-04
---
# Cảnh báo sức khỏe ở sổ sức khỏe, mức BMI kỳ cân đo, bỏ phiếu theo dõi

Spec Kit: `specs/010-health-warnings-book/` (domain spec, spec, data model, contract, tasks). Client plan: `FE:docs/plans/active/2026-11-04-health-warnings-book.md`.

## Context
User 2026-11-04: nghiệp vụ sức khỏe gồm đợt khám (bác sĩ kết luận) và kỳ cân đo (cảnh báo BMI); bỏ phiếu theo dõi sức khỏe; cảnh báo lưu vào sổ sức khỏe của trẻ để giảm việc GV; thiết kế cách hiện cảnh báo ở sổ phù hợp. Hiện trạng trước sửa: kết luận "theo dõi sát" mở phiếu (`HealthFollowUpTracker`), GV ghi trao đổi phụ huynh, kết quả chuyên khoa, đóng phiếu (`HealthFollowUpServiceImpl`, `/health-follow-ups`); BMI chỉ là số (`HealthRecords.bmi`, ghi chú "phân loại hoãn").

## Quyết định đã chốt
1. Bỏ phiếu theo dõi (HLT-16 REMOVED); cảnh báo ở sổ sức khỏe, không có bước xử lý (HLT-19) — user, 2026-11-04.
2. Kỳ cân đo có cảnh báo BMI; đợt khám dùng kết luận bác sĩ — user, 2026-11-04. Ngưỡng theo WHO (HLT-03 CONFIRMED).

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** GV lớp (nhập số đo, phiếu khám), HT/PHT (xem) · outcome: cảnh báo hiện ở sổ, danh sách lớp, chi tiết đợt/kỳ, không việc phải xử lý · HLT-18, HLT-19 (PROPOSED, user yêu cầu), HLT-10/12 sửa · `[S]` xếp mức khi đọc.
- **Đối chiếu:** HLT-10 cũ "phân loại WHO hoãn; khi làm GV chỉnh được kèm lý do" ⇒ chỉ làm mức BMI, phần chỉnh tay ghi P-40 (không tự chế). HLT-16 CONFIRMED 31/10 ⇒ user đổi 04/11, ghi REMOVED.
- **Ảnh hưởng:** module health · quyền không đổi · dữ liệu dẫn xuất tính khi đọc (BACKEND_ARCHITECTURE "Trend sức khỏe: tính lúc đọc") · FE: sổ sức khỏe, đợt/kỳ, phiếu khám, menu phiếu theo dõi (BE giả `children: demo`) · APP: không gọi API · dữ liệu cũ: 3 bảng phiếu theo dõi giữ, không đọc.

### Câu hỏi mở
- [x] Điều chỉnh tay mức BMI (HLT-10 cũ)? → chưa làm, ghi P-40 để user/trường chốt; sai số đo thì sửa số đo (HLT-13).

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Bỏ phiếu theo dõi? Cảnh báo lưu ở đâu? | Bỏ; lưu ở sổ sức khỏe, giảm việc GV | user | 2026-11-04 | HLT-16, HLT-19, HLT-10, HLT-12, HLT-17, G94-180/CHD-03, G94-180/HLT-08 |
| Kỳ cân đo cảnh báo gì? | BMI | user | 2026-11-04 | HLT-18 |

## Acceptance criteria
- **AC-1** (HLT-18): Given số đo kỳ cân đo của trẻ 0–84 tháng When đọc Then có `bmiStatus` theo z-score WHO (2006 đến 60 tháng, 2007 từ 61 tháng) và `bmiZScore` 2 chữ số; phiếu khám không có `bmiStatus`.
- **AC-2** (HLT-18, HLT-13): Given sửa số đo kèm lý do When đọc Then mức tính lại.
- **AC-3** (HLT-19): Given phiếu khám kết luận chuyển khám/nguy cơ lao When lưu Then không tạo phiếu; sổ có `latestExam` (mức, nhóm, kết luận rút gọn).
- **AC-4** (HLT-19): Given kỳ cân đo When xem chi tiết/danh sách lớp Then có `progress.bmi`, `classes[].bmiWarning`, dòng trẻ có `bmi`, `bmiStatus`, `watchList` = trẻ BMI khác bình thường, mức nặng trước.
- **AC-5** (HLT-16 REMOVED): `/health-follow-ups/**`, `followUp*`, `openFollowUp`, `externalResults`, mã 5035–5039 không còn; phiếu cũ mẫu FOLLOW_UP vẫn xem/sửa đúng mẫu, gửi mẫu khác ⇒ 409 5032.
- **AC-6** (HLT-10, sửa 2026-11-04): Given phiếu khám kết luận có vấn đề sức khỏe When lưu Then không hỏi vấn đề mới hay đã biết, mức cần theo dõi; phiếu cũ đã trả lời "đã biết" giữ mức ổn định khi sửa.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Lưu cảnh báo | Tính khi đọc từ dữ liệu đã lưu | Bảng `health_alerts` ghi khi lưu | Không lệch khi sửa số đo/ngày sinh; giống mức theo dõi, trend (ADR-0005) |
| Bảng WHO | CSV L-M-S chép từ Excel WHO, 0–84 tháng | Hard-code ngưỡng; thư viện ngoài | Đúng nguồn, không dependency; mẫu giáo ≤ 6 tuổi |
| "Nguy cơ thừa cân" | Có (đến 60 tháng, > +1SD) | Bỏ | Định nghĩa WHO 2006; tránh nhảy mức ở mốc 61 tháng |
| Dữ liệu phiếu cũ | Giữ bảng, bỏ entity/API | Migration xóa bảng | Không mất dữ liệu, không thao tác không đảo ngược; xóa sau khi team chốt |
| Mẫu FOLLOW_UP cũ | Giữ khi phiếu đã kết luận theo mẫu đó | Chuyển hết sang STANDARD | Không đổi nội dung phiếu đã công bố |
| `watchList` kỳ cân đo | Trẻ BMI khác bình thường | Để FE tự lọc | BE sở hữu nghiệp vụ; FE không tính |
| Câu "vấn đề mới hay đã biết" (HLT-10) | Bỏ (user 2026-11-04); phiếu cũ đã trả lời giữ câu trả lời khi sửa | Lấy "đã biết" từ lần khám ổn định/tiền sử khai báo (bản sửa review P1, thay thế cùng ngày) | User thấy câu này rối; giữ dữ liệu cũ không đổi mức khi sửa |
| Z-score khi xếp mức | Làm tròn 2 chữ số rồi so ngưỡng | So z thô | Mức khớp số hiển thị, như phần mềm WHO |

## Phases
### Phase 1 — BE + tri thức (xong 2026-11-04)
- Files: C `WhoBmiForAge`, `HealthBmiStatus`, `health/who-bmi-for-age.csv`, `WhoBmiForAgeTest`, `HealthExamIntegrationTest` · M `HealthRecords`, `HealthExamOutcome`, `HealthExamServiceImpl`, `HealthCheckServiceImpl`, `HealthMeasurementServiceImpl`, DTO sức khỏe, `ApiCode`, `application.yml`, `.env.example`, docs · D 24 file main của phiếu theo dõi (gồm `HealthImageStorage`, `HealthImageData`), test `HealthExamFollowUpIntegrationTest`, `HealthFollowUpSelfHistoryTest`.
- Tests: AC-1 → `WhoBmiForAgeTest`; AC-2, AC-4 → `HealthExamIntegrationTest#measurementRoundWarnsByBmiForAge`; AC-3 → `#referralIsAHealthBookWarningWithoutFollowUpForm`, `#examCampaignListsChildrenNeedingAttention`; AC-5 → OpenAPI snapshot + `#legacyFollowUpConclusionKeepsItsMode`; AC-6 → `#healthIssueIsWatchedWithoutAskingIfKnown`.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0.
### Phase 2 — FE (agent song song, plan FE)
- Bỏ màn phiếu theo dõi, thẻ "Cảnh báo sức khỏe", cột "Cảnh báo", BE giả theo contract mới. Exit: FE `node scripts/verify.mjs`.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | HLT-18 | `WhoBmiForAgeTest` (unit, 21) | 1 | pass |
| AC-2, AC-4 | HLT-18, HLT-19 | `HealthExamIntegrationTest#measurementRoundWarnsByBmiForAge` (Testcontainers) | 1 | pass |
| AC-3 | HLT-19 | `HealthExamIntegrationTest#referral…`, `#examCampaign…` | 1 | pass |
| AC-5 | HLT-16 | `OpenApiSnapshotTest`, `HealthExamIntegrationTest#legacyFollowUpConclusionKeepsItsMode` | 1 | pass |
| AC-6 | HLT-10 | `HealthExamIntegrationTest#healthIssueIsWatchedWithoutAskingIfKnown`, `HealthExamLogicTest#issueKindIsNeverRequired` | 1 | pass |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Client cũ gọi `/health-follow-ups` | 404 | FE bỏ màn cùng ngày; APP chưa gọi | Khôi phục file bằng git |
| Mức BMI sai bảng | Cảnh báo sai | Chép từ Excel WHO, test đối chiếu SD công bố | Sửa CSV |
| Trẻ từng có phiếu đóng "bình thường" sau lần khám theo dõi sát | Đầu sổ vẫn hiện mức của lần khám | Ghi pitfall module card; lần khám sau thay | — |

## Validation log
- [x] Khẳng định về code có `file:line` (Context, module card)
- [x] Đổi contract ⇒ consumer: FE `ChildHealthRecordPage`, `HealthCheckDetailPage`, `HealthCheckClassRoster`, `HealthExamFormPage`, mock `childrenMockRepository`/`healthCheckMockRepository`; APP không gọi (đã rà `lib/`)
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] >8 file: đã tách FE thành plan riêng; BE một phase vì xóa/sửa liên kết chặt
- [x] Không dùng rule PENDING/OPEN như đã chốt (P-40 để mở)
- [x] Tự review: không placeholder, không mâu thuẫn

## Progress log
### 2026-11-04 — Phase 1 (done, chờ user soát)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 999 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 471s | log target/verify.log` (sau sửa theo review); chi tiết `specs/010-health-warnings-book/quickstart.md`.
- Khác plan: thêm AC-6 và nguồn "vấn đề đã biết" từ tiền sử khai báo (review P1); xếp mức theo z đã làm tròn; xóa `HealthCheckRules.managedCampusIds` (chỉ phiếu theo dõi dùng).
- Tiếp: Phase 2 FE; user soát quyết định mở (P-40, giữ bảng phiếu cũ, có "nguy cơ thừa cân", nguồn "vấn đề đã biết").

### 2026-11-04 — Bỏ câu "Vấn đề này" (user yêu cầu)
- User: "thiết kế lại UI cho dễ nhìn hơn, bỏ phần vấn đề này". Phiếu không còn hỏi vấn đề mới hay đã biết ⇒ có vấn đề sức khỏe là WATCH; KNOWN_STABLE chỉ còn ở phiếu cũ (normalizer giữ `issueKind` khi sửa phiếu đã kết luận có câu trả lời). Bỏ `knownCondition` khỏi `HealthExamResponse` và `HealthLatestExam`, bỏ `HealthExamServiceImpl.knownCondition` (bản sửa review P1 cùng ngày), validator không còn bắt `issueKind`. HLT-10, flow, module card, contract, CROSS_REPO_MAP cập nhật; snapshot OpenAPI bỏ 2 trường.
- Commit: commit, push (user cho phép 2026-11-04).
