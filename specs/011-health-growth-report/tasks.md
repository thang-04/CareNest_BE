# Tasks — Feature011

Thứ tự: bảng WHO → phân loại → báo cáo → tri thức → verify → FE (agent FE) → converge.

## Phase1 — Specification/design
- [x] T001 Domain spec 14 mục, spec, research, data model, contract, plan quản trị; rule HLT-20, sửa HLT-10 (`docs/business/BUSINESS_RULES.md`).

## Phase2 — Backend (US1–US3, AC-1..5)
- [x] T002 (`src/main/resources/health/who-weight-for-age.csv`, `who-height-for-age.csv`) Bảng WHO W/A, L/H/A 0–84 tháng từ Excel WHO, kiểm −2SD từng dòng; báo "main" để FE chuyển sang BE giả.
- [x] T003 (`WhoLmsTable.java`, `WhoBmiForAge.java`, `WhoWeightForAge.java`, `WhoHeightForAge.java`, `WeightForAgeStatus.java`, `HeightForAgeStatus.java`, `WhoWeightHeightForAgeTest.java`) Tách LMS dùng chung, thêm W/A (giới hạn đuôi), H/A (công thức thường); test trung vị z = 0, −2SD/−3SD công bố, ranh giới làm tròn, dữ liệu thiếu; `WhoBmiForAgeTest` giữ nguyên. AC-1.
- [x] T004 (`HealthGrowthReportResponse.java`, `HealthGrowthLevel.java`, `HealthGrowthReportService.java`, `HealthGrowthReportServiceImpl.java`, `HealthCheckController.java`) API báo cáo: phạm vi, đợt/kỳ của tháng, hợp trẻ, đếm, cấp học, thứ tự, tổng. AC-2..4.
- [x] T005 (`HealthGrowthReportIntegrationTest.java`, `HealthGrowthReportControllerTest.java`) Testcontainers: HT/PHT, 403, 404, tháng, phiếu kết luận tính, nháp/vắng không tính, `unclassified`, thứ tự lớp, tổng MG/NT/điểm trường/toàn trường; WebMvc: `month` sai/thiếu 400 9002, GV 403 1101. AC-2..4.
- [x] T006 (`docs/api/openapi.yaml`, card health, flow, BUSINESS_RULES, CROSS_REPO_MAP, CURRENT_STATE, GLOSSARY) Snapshot và tri thức. AC-5.
- [x] T007 `node scripts/verify.mjs` full, `node scripts/check-ai-layer.mjs`; ghi `quickstart.md`, Progress log.
- [x] T007a Sửa sau review độc lập (P3): điểm trường ngừng hoạt động không đợt/kỳ bị ẩn (`HealthGrowthReportServiceImpl`, test lọc điểm trường `INACTIVE`); test làm tròn ranh giới −2,004/−3,004, đuôi H/A, trẻ chuyển lớp tính một lần, `HealthGrowthLevelTest`, PHT với `campusId` không tồn tại ⇒ 403; ghi giả định BMI/tuổi cho cột "cân nặng/chiều cao", giới hạn kế thừa ở card; HLT-18 ghi ngoại lệ HLT-20; verify lại.

## Phase3 — Web (agent FE)
- [ ] T008 FE màn báo cáo, BE giả dùng bảng WHO; FE verify.
- [ ] T009 Converge: đối chiếu spec/contract với code BE/FE, append gap nếu có.

## Test trace
AC-1 → `WhoWeightHeightForAgeTest` · AC-2..4 → `HealthGrowthReportIntegrationTest` · AC-5 → `OpenApiSnapshotTest`. Dữ liệu test tổng hợp, tên "(giả)".
