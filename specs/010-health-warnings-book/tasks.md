# Tasks — Feature010

Thứ tự: Design → BE → tri thức → verify → FE (agent song song, client plan) → converge.

## Phase1 — Specification/design
- [x] T001 Domain spec 14 mục, spec, research, data model, contract, plan quản trị; rule HLT-18, HLT-19, HLT-16 REMOVED, P-40 (`docs/business/BUSINESS_RULES.md`).

## Phase2 — Backend (US1–US4, AC-1..5)
- [x] T002 (`src/main/resources/health/who-bmi-for-age.csv`, `WhoBmiForAge.java`, `HealthBmiStatus.java`, `WhoBmiForAgeTest.java`) Bảng WHO và phân loại; test ngưỡng 2006/2007, đuôi ±3SD, đối chiếu SD công bố, dữ liệu thiếu. AC-1.
- [x] T003 (`HealthRecords.java`, `HealthMeasurementServiceImpl.java`, DTO `HealthMeasurementResponse`, `ChildHealthRecordResponse`) Mức BMI theo lần đo, `latestExam`, `latestBmi`. AC-1, AC-2.
- [x] T004 (`HealthCheckServiceImpl.java`, DTO `HealthCheckResponse`, `HealthCheckDetailResponse`, `HealthCheckClassResponse`, `HealthCheckCloseResponse`) Đếm theo mức BMI, `bmiWarning`, `watchList`, bỏ đếm phiếu theo dõi. AC-4.
- [x] T005 (`HealthExamServiceImpl.java`, `HealthExamOutcome.java`, `HealthExamResponse.java`, `ApiCode.java`) Kết luận không mở phiếu; mẫu FOLLOW_UP chỉ phiếu cũ. AC-3, AC-5.
- [x] T006 Xóa phiếu theo dõi (controller/service/entity/repository/DTO/enum, `HealthImageStorage`, `carenest.health-media`, `.env.example`). AC-5.
- [x] T007 (`HealthExamIntegrationTest.java` thay `HealthExamFollowUpIntegrationTest.java`) Testcontainers: kết luận không tạo phiếu, cảnh báo ở sổ, đợt khám đếm theo dõi sát, kỳ cân đo xếp mức và tính lại khi sửa. AC-2..5.
- [x] T008 (`docs/api/openapi.yaml`, module card, flow, DATA_DICTIONARY, ERROR_CONTRACT, CROSS_REPO_MAP, CURRENT_STATE, GLOSSARY, DOMAIN_MODEL, CONTEXT_MAP, EDUCATION_MEDIA_S3) Snapshot và tri thức.
- [x] T009 `node scripts/verify.mjs` full (`VERIFY PASS full | tests 999 …`), `node scripts/check-ai-layer.mjs`; ghi `quickstart.md`, Progress log.

## Phase3 — Web (client plan FE)
- [ ] T010 FE bỏ màn/menu/mock phiếu theo dõi; thẻ "Cảnh báo sức khỏe", cột "Cảnh báo", BE giả theo contract; FE verify.
- [ ] T011 Converge: đối chiếu spec/contract với code BE/FE, append gap nếu có.

## Phase2b — Review độc lập BE (04/11, append)
- [x] T012 (`HealthExamServiceImpl.java`, `WhoBmiForAge.java`, `HealthCheckRules.java`, `HealthExamIntegrationTest.java`, `WhoBmiForAgeTest.java`, docs) Sửa P1 "vấn đề đã biết" không còn nguồn (lấy thêm từ tiền sử khai báo — AC-6); xếp mức theo z đã làm tròn; bỏ `managedCampusIds` chết; BOM/LF bảng WHO; test mẫu FOLLOW_UP cũ (AC-5); Javadoc/tài liệu còn nhắc phiếu theo dõi.

## Test trace
AC-1 → `WhoBmiForAgeTest` (21 case) · AC-2, AC-4 → `HealthExamIntegrationTest#measurementRoundWarnsByBmiForAge` · AC-3 → `#referralIsAHealthBookWarningWithoutFollowUpForm`, `#examCampaignListsChildrenNeedingAttention` · AC-5 → `OpenApiSnapshotTest`, `#legacyFollowUpConclusionKeepsItsMode` · AC-6 → `#declaredHistoryMakesAKnownStableConclusion`. Dữ liệu test tổng hợp, tên "(giả)".
