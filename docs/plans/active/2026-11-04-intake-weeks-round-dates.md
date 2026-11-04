---
title: Tuần thai bắt buộc khi tiếp nhận, kỳ cân đo bỏ vòng đầu, ngày đo/ngày khám do hệ thống ghi
status: in-progress   # chế độ tự chạy (CLAUDE.md, user chốt 2026-10-23): plan tự viết rồi làm tiếp, user soát khi bàn giao
owner: user (CareNest team)
jira: G94-180 / BE-FEAT-43 (nhánh hiện tại; task Jira riêng cho thay đổi này chưa có — hỏi khi commit)
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [child, health]
rules: [G94-180/CHD-05, HLT-01, HLT-08, HLT-13]
risk: high   # đổi API contract (bỏ trường request/response, bỏ mã 5021, đổi câu lỗi), ảnh hưởng FE; không migration
created: 2026-11-04
updated: 2026-11-04
---
# Tuần thai bắt buộc khi tiếp nhận, kỳ cân đo bỏ vòng đầu, ngày đo/ngày khám do hệ thống ghi

Làn L: đổi API contract, 2 module (child, health), ảnh hưởng FE. Đổi nghiệp vụ đã có (không phải feature mới) ⇒ clarify-business → plan, không Spec Kit.

## Context
User 2026-11-04 (nguyên văn): "Phần tuần khai sinh khi tiếp nhận trẻ chuyển sang là trường nhập bắt buộc , kỳ cân đo bỏ chỉ số vòng đầu , trong các kỳ khám cân đo thời gian không được chỉnh sửa". User xác nhận: (a) "thời gian" = ngày khám/ngày đo của từng trẻ — GV không chọn, hệ thống ghi ngày nhập (hôm nay, trong khoảng của đợt/kỳ), sửa sau giữ ngày cũ; ngày bắt đầu/kết thúc của đợt/kỳ vẫn sửa như hiện nay. (b) Giữ ô "Sinh non", kiểm khớp với tuần thai.

Hiện trạng trước sửa (commit `eaa3aa9`):
- `src/main/java/com/carenest/service/child/impl/EnrollmentFormValidator.java:196-215`: tuần thai chỉ bắt buộc khi sinh non; trẻ không sinh non bị bỏ tuần thai khi lưu.
- `src/main/java/com/carenest/dto/health/HealthMeasurementRequest.java:15-29`: có `date` (bắt buộc), `headCircumferenceCm`; `HealthMeasurementServiceImpl` (`checkOpenAndDate`, `headOf`, ô đổi "Ngày đo", "Vòng đầu (cm)"); `ChildHealthRecordResponse.headCircumferenceUntil` chỉ form số đo FE dùng.
- `src/main/java/com/carenest/dto/health/HealthExamRequest.java:13` có `date`; `HealthExamServiceImpl.saveExam` ưu tiên `request.date()`; `HealthExamValidator.checkDate` kiểm ngày như ô nhập; `HealthExamDiff` có ô "Ngày khám".
- `src/main/java/com/carenest/entity/health/HealthCheckCampaign.java:94-102`: IN_PROGRESS khi chưa đóng/hủy và đã tới ngày bắt đầu, không xét ngày kết thúc; chỉ EXAM tự đóng (`HealthCheckServiceImpl.closeExpiredHealthChecks`), kỳ cân đo đóng tay.

## Quyết định đã chốt
1. Tuần thai khi sinh bắt buộc với mọi trẻ khi tiếp nhận; giữ "Sinh non", kiểm khớp (dưới 37 tuần là sinh non) — user, 2026-11-04.
2. Kỳ cân đo bỏ vòng đầu; phiếu khám Phụ lục II giữ vòng đầu (trẻ ≤60 tháng) — user, 2026-11-04.
3. Ngày đo/ngày khám từng trẻ do hệ thống ghi (ngày nhập, trong khoảng đợt/kỳ), không sửa; ngày của đợt/kỳ vẫn sửa được — user, 2026-11-04.
4. Kỳ cân đo chưa đóng mà đã qua ngày kết thúc: vẫn nhập, ngày đo = ngày kết thúc (kẹp như phiếu khám) — người điều phối, 2026-11-04 khi triển khai; chờ user soát (Key decisions).

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** HT/PHT (tiếp nhận), GV lớp (số đo, phiếu khám) · outcome: hồ sơ luôn có tuần thai khớp "Sinh non"; số đo kỳ cân đo chỉ chiều cao, cân nặng, ghi chú; ngày đo/ngày khám không chỉnh · G94-180/CHD-05, HLT-01, HLT-08, HLT-13 (CONFIRMED, user sửa 2026-11-04) · `[B]` GV cân đo/khám → `[S]` ghi ngày.
- **Đối chiếu thiết kế ban đầu:** G94-180/CHD-05 "sinh non (+ tuần thai)" (`docs/business/BUSINESS_RULES.md:324`); HLT-01 "chiều cao, cân nặng, vòng đầu (trẻ ≤60 tháng)" (`docs/business/BUSINESS_RULES.md:132`); `gestation_weeks` "bắt buộc khi premature = YES" (`docs/database/DATA_DICTIONARY.md:515`) ⇒ user đổi, cập nhật cùng task.
- **Ảnh hưởng:** module child, health · quyền không đổi · dẫn xuất: mức BMI tính theo ngày đo hệ thống ghi · FE: form tiếp nhận, form số đo ở sổ sức khỏe, phiếu khám, BE giả · APP: không gọi các API này (đã rà `lib/`) · dữ liệu cũ: hồ sơ cũ thiếu tuần thai giữ NULL (không migration); số đo cũ có vòng đầu giữ, sửa không xóa.

### Câu hỏi mở
- [x] "Thời gian không được chỉnh sửa" là gì? → ngày đo/ngày khám của từng trẻ; ngày của đợt/kỳ vẫn sửa.
- [x] Bỏ ô "Sinh non" khi đã bắt buộc tuần thai? → giữ, kiểm khớp tuần thai.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| "Thời gian" là ngày nào? | Ngày đo/ngày khám của từng trẻ, hệ thống ghi ngày nhập | user | 2026-11-04 | HLT-01, HLT-08, HLT-13, flow, card health |
| Giữ "Sinh non"? | Giữ, kiểm khớp tuần thai | user | 2026-11-04 | G94-180/CHD-05, card child, DATA_DICTIONARY |
| Nhập sau ngày kết thúc khi kỳ cân đo chưa đóng? | Vẫn nhập, ngày đo = ngày kết thúc | người điều phối (chờ user soát) | 2026-11-04 | HLT-01, flow, card health, CROSS_REPO_MAP |

## Acceptance criteria
- **AC-1** (`G94-180/CHD-05`): Given form tiếp nhận không có tuần thai (kể cả trẻ không sinh non) When lưu Then 400 9001, ô `profile.gestationWeeks` "Nhập tuần thai khi sinh".
- **AC-2** (`G94-180/CHD-05`): Given "Sinh non" Có + 37–42 tuần hoặc Không + 20–36 tuần When lưu Then lỗi ô `profile.gestationWeeks`; hợp lệ ⇒ lưu tuần thai cả khi không sinh non.
- **AC-3** (`HLT-01`): Given kỳ cân đo đang diễn ra When GV tạo số đo Then ngày đo = hôm nay kẹp trong kỳ (kỳ chưa đóng đã qua ngày kết thúc ⇒ ngày kết thúc), không có vòng đầu; ngày đó trẻ đã có kết quả khác ⇒ 409 5020 kèm ngày.
- **AC-4** (`HLT-13`): Given số đo đã lưu (có thể có vòng đầu cũ) When sửa ở ngày khác, kể cả sau ngày kết thúc khi kỳ chưa đóng Then chỉ đổi chiều cao/cân nặng/ghi chú, giữ ngày đo và vòng đầu; lịch sử không có ô ngày, vòng đầu.
- **AC-5** (`HLT-08`): Given phiếu khám lưu nháp ngày X When kết luận, sửa ở ngày Y Then ngày khám vẫn X; phiếu mới ⇒ hôm nay kẹp trong đợt; review-preview theo ngày đó; trùng kết quả khác ⇒ lỗi ô `date` kèm ngày.
- **AC-6** (tương thích): Given client cũ gửi `date`, `headCircumferenceCm` When gọi API Then không bị từ chối, các trường bị bỏ qua.
- **AC-7** (contract): `GET /children/{id}/health` bỏ `headCircumferenceUntil`; mã 5021 bỏ; snapshot OpenAPI cập nhật có chủ đích.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Ngày đo khi kỳ cân đo chưa đóng mà đã qua ngày kết thúc | Kẹp: ngày đo = min(hôm nay, ngày kết thúc) — `HealthCheckRules.referenceDate`, dùng chung với phiếu khám (người điều phối chốt 2026-11-04) | Chặn nhập (5021) tới khi HT/PHT gia hạn kỳ | Kỳ cân đo đóng tay nên vốn nhận nhập muộn; trước đây GV nhập muộn chọn ngày ≤ ngày kết thúc; giống phiếu khám và BE giả FE; chặn thì thêm việc cho HT. **Đánh đổi:** lần nhập muộn ghi ngày kết thúc, không phải ngày cân thật — user soát |
| Mã 5021 `HEALTH_MEASUREMENT_DATE_OUTSIDE` | Bỏ (không dùng lại mã) | Giữ cho ca ngày sinh | Ngày kẹp luôn trong kỳ và ≤ hôm nay (lưu chỉ sau `checkOpen`); kiểm ngày sinh không thể xảy ra vì `classOn(ngày đo)` cần enrollment đang mở, mà enrollment chỉ bắt đầu ở ngày tiếp nhận (validator: ngày sinh ≤ hôm nay) hoặc ngày chuyển năm (≥ hôm nay), ngày sinh không sửa được. Điều kiện này ghi ở card child/health cho Sửa hồ sơ sau này |
| Thứ tự kiểm khi tạo số đo | Giữ: điểm trường, quyền GV rồi mới trạng thái kỳ (5018) | Đưa `checkOpen` lên đầu (review gợi ý) | Không lộ trạng thái đợt/kỳ của điểm trường khác cho người ngoài phạm vi; kỳ chưa mở vẫn không lưu (`#upcomingRoundSavesNothing`) |
| Câu 5020, lỗi ô `date` của phiếu khám | Kèm ngày dd/MM/yyyy, "Báo Hiệu trưởng hoặc Phó hiệu trưởng" | "Chọn ngày khác…" | GV không còn chọn được ngày; câu phiếu khám khớp BE giả FE |
| `HealthExamValidator.checkDate`, ô "Ngày khám" của `HealthExamDiff` | Bỏ (cùng `campaignStart`, `campaignEnd` của `Input`, `date` của `Snapshot`) | Giữ làm chốt chặn | Ngày do hệ thống ghi luôn trong đợt và không đổi; câu "Chọn ngày khám…" không còn ô để sửa; ô diff không thể phát sinh |
| Sửa số đo | Chỉ kiểm kỳ đang diễn ra (5018) | Kiểm lại ngày | Ngày không đổi; kiểm lại có thể chặn sửa số đo cũ vô lý |
| Entity `HealthMeasurement` | Builder bỏ vòng đầu; `update(height, weight, note, at)` | Truyền null/giá trị cũ ở service | Không đường nào ghi vòng đầu cho kỳ cân đo hay ghi đè ngày, vòng đầu cũ |
| Seed dev `R__dev_seed.sql` | Không sửa | Thêm tuần thai cho trẻ demo | Test không dùng seed; dòng NULL đúng là "hồ sơ cũ" |

## Phases
### Phase 1 — BE + tri thức (một phase: DTO, service, test gắn chặt cùng contract)
- Files: M `EnrollmentFormValidator`, `ChildProfileInput`, `HealthMeasurementRequest`, `HealthExamRequest`, `ChildHealthRecordResponse`, `HealthMeasurement`, `HealthMeasurementServiceImpl`, `HealthExamServiceImpl`, `HealthExamValidator`, `HealthExamDiff`, `HealthCheckRules`, `HealthRecords`, `ApiCode`, 6 test; C `src/test/java/com/carenest/controller/health/HealthControllerTest.java`; docs: BUSINESS_RULES, flow health-check, card health/child, DATA_DICTIONARY, ERROR_CONTRACT, CROSS_REPO_MAP, CURRENT_STATE, VERIFICATION, `docs/api/openapi.yaml`.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0; `node scripts/check-ai-layer.mjs` không thêm lỗi.
### Phase 2 — FE (agent song song, repo FE, theo contract ở `docs/system/CROSS_REPO_MAP.md` mục 04/11)

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-2 | G94-180/CHD-05 | `EnrollmentFormValidatorTest#profileRules`, `#prematureMatchesGestationWeeks`, `#missingFieldsUseFrontendPaths` (unit); `ChildEnrollmentIntegrationTest#principalEnrollsPendingChildAndCreatesAccount` (Testcontainers); `ChildEnrollmentControllerTest#fieldErrorsAreReturnedAsList` | 1 | pass |
| AC-3 | HLT-01 | `HealthCheckIntegrationTest#teacherRecordsMeasurementsOnlyInsideOpenRound`, `#measurementDateIsEntryDateClampedToRound`, `#upcomingRoundSavesNothing` | 1 | pass |
| AC-4 | HLT-13 | `HealthCheckIntegrationTest#editingMeasurementNeedsReasonAndKeepsHistory` | 1 | pass |
| AC-5 | HLT-08 | `HealthExamIntegrationTest#examDateIsRecordedOnFirstSaveAndKept`, `#newExamOnTakenDateAsksLeaders`; `HealthExamLogicTest#examDateIsNotAFormField`, `#diffDisplayFormats` | 1 | pass |
| AC-6 | HLT-01, HLT-08 | `HealthControllerTest` (WebMvc, 3 test) | 1 | pass |
| AC-7 | HLT-01 | `OpenApiSnapshotTest`, `ApiCodeTest` | 1 | pass |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| FE chưa cập nhật gửi tiếp nhận không có tuần thai | 400 ô `profile.gestationWeeks` | FE làm song song cùng ngày; câu lỗi rõ | git revert phần validator |
| Nhập muộn ghi ngày kết thúc của kỳ | Ngày đo khác ngày cân thật, tuổi tháng/BMI có thể lệch vài ngày | Ghi đánh đổi ở HLT-01 để user soát | Chỉ trong `createMeasurement`: dùng `rules.today()` và chặn khi qua ngày kết thúc (không đổi `referenceDate` dùng chung cho sĩ số lớp, phiếu khám) |
| Sửa hồ sơ (sau này) cho đổi ngày sinh sang sau ngày vào lớp | Ngày đo/khám trước ngày sinh (không còn 5021 chặn) | Ghi điều kiện ở card child, health | Chặn ở Sửa hồ sơ |
| Phiếu nháp cũ do client gọi thẳng API lưu ngày ngoài đợt | Kết luận không còn kiểm ngày | FE cũ chỉ cho chọn ngày trong [bắt đầu, min(kết thúc, hôm nay)]; kiểm SQL ở Progress log trước deploy | Sửa dữ liệu |
| Client cũ còn gửi `date`/vòng đầu | Trường bị bỏ qua | Jackson bỏ trường lạ (`HealthControllerTest`) | — |

## Validation log
- [x] Khẳng định về code có `file:line` (Context)
- [x] Đổi contract ⇒ consumer: FE form tiếp nhận, form số đo sổ sức khỏe, phiếu khám, BE giả (`FE:src/services/children/api/childrenApi.js`, `FE:src/services/children/api/healthCheckApi.js`); APP không gọi (đã rà `lib/`)
- [x] Mỗi AC có test; phase có Exit
- [x] >8 file: FE tách sang repo FE; BE một phase vì DTO/service/test cùng contract
- [x] Không dùng rule PENDING/OPEN như đã chốt; quyết định 4 ghi rõ chờ user soát
- [x] Tự review: không placeholder, không mâu thuẫn

## Progress log
### 2026-11-04 — Phase 1 (done, chờ user soát)
- Commit: commit, push lên nhánh feature (user cho phép 2026-11-04).
- Giả định: ngưỡng sinh non 37 tuần (định nghĩa WHO; user chốt kiểm khớp); câu lỗi tiếp nhận theo brief.
- Lượt 1: ngày đo = hôm nay, giữ 5021 khi kỳ chưa đóng đã qua ngày kết thúc → `VERIFY PASS full | tests 1005 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 506s | log target/verify.log`.
- Review độc lập (subagent, chỉ đọc): không P1/P2. P3 đã xử lý: bỏ ô "Ngày khám" chết ở `HealthExamDiff`; thêm test sửa số đo sau ngày kết thúc; sửa câu card health về `HealthExamValidator`. P3 ghi lại: ngày khám chốt từ lần lưu nháp đầu (kết luận muộn vẫn tính tuổi, so ngày tiếp xúc lao theo ngày đó — card health Known pitfalls); phiếu nháp cũ có ngày lệch chỉ khi client gọi thẳng API — trước deploy chạy `SELECT m.id FROM health_measurements m JOIN health_check_campaigns c ON c.id = m.campaign_id WHERE m.exam_status = 'DRAFT' AND (m.date < c.start_date OR m.date > c.end_date OR m.date > CURRENT_DATE)`.
- Khác plan (người điều phối sửa brief cùng ngày): ngày đo mới = kẹp trong kỳ thay vì chặn ⇒ bỏ 5021; dùng chung `HealthCheckRules.referenceDate` cho số đo, phiếu khám (bỏ `clamp` riêng của `HealthExamServiceImpl`); câu 5020 và lỗi ô `date` phiếu khám kèm ngày, "Báo Hiệu trưởng hoặc Phó hiệu trưởng".
- Lượt 2 (sau sửa brief: kẹp ngày đo, bỏ 5021, câu mới) → `VERIFY PASS full | tests 1006 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 468s | log target/verify.log`.
- Review độc lập lượt 2 (subagent, chỉ đọc phần đổi): không P1/P2; xác nhận 5021 không thể xảy ra. P3 đã xử lý: test kỳ chưa bắt đầu không lưu (`#upcomingRoundSavesNothing`), sửa comment test sửa số đo, điều kiện ngày sinh cho Sửa hồ sơ (card child, health), câu 5020 trong HLT-01 + cách HT/PHT xử lý ở flow, CROSS_REPO_MAP liệt kê đủ 4 câu lỗi ô `date` đã bỏ và lịch sử cũ còn ô `date`, ERROR_CONTRACT ghi 5033 có thể chỉ mang ô `date`, rollback chỉ trong `createMeasurement`. Không làm: đưa `checkOpen` lên đầu (lộ trạng thái kỳ của điểm trường khác — Key decisions); `canRecord` của sổ theo lớp tại ngày kẹp (ca hiếm, ghi Known pitfalls card health); BE giả FE còn câu 5020 khác — báo người điều phối.
- Lượt 3, sau lần sửa test cuối: `VERIFY PASS full | tests 1007 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 461s | log target/verify.log`. `node scripts/check-ai-layer.mjs` → 1 lỗi (E2 `BUG-261102` có sẵn, link FE chỉ có trên `origin/dev`), 12 cảnh báo — như trước task.
- Tiếp: Phase 2 FE; user soát quyết định 4 (đánh đổi ngày kết thúc) và các giả định trên.
