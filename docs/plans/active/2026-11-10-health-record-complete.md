---
title: Hồ sơ sức khỏe đầy đủ — khai báo, thông báo lao, phụ huynh xem, AI giải thích xu hướng
status: in-progress
owner: G94 team
jira: G94-180 / BE-FEAT-43
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [child, health, notification, ai-assistance]
rules: [G94-180/CHD-06, G94-180/CHD-07, HLT-02, HLT-03, HLT-04, HLT-05, HLT-11, PAR-01, PAR-03, AI-01, AI-02, AI-03, AI-04, AI-05, AI-06, AI-07, AI-09]
risk: high
created: 2026-11-10
updated: 2026-11-10
---
# Hồ sơ sức khỏe đầy đủ

Làn L (migration V83, `integration/ai` mới, API mới, 3 module). Chế độ tự chạy (`CLAUDE.md`): agent chọn phương án khuyến nghị, ghi ở "Key decisions"; `status` không tự đặt `approved`.

## Context
User yêu cầu 2026-11-10 "làm BE phần hồ sơ sức khỏe đầy đủ". Hiện trạng: khai báo sức khỏe chỉ ghi lúc tiếp nhận và từ phiếu khám (`ChildIntakeServiceImpl.updateMedicalHistory`), chưa có `PUT /children/{id}/health-declaration`; `GET /children/{id}/profile` không trả khai báo (`ChildPhotoProfileResponse`); không có thông báo CHD-07; sổ sức khỏe `GET /children/{id}/health` chỉ cho nhân sự (`HealthMeasurementController`); không có `AiClient` (kiến trúc có, code chưa); không thông báo phụ huynh khi công bố kết quả. Contract FE (BE giả): `FE:src/services/children/mock/childrenMockRepository.js` (`saveDeclaration`, `analyzeHealthTrend`), `FE:docs/integration/CHILD_ENROLLMENT_BE_SPEC.md` mục 4.5, 4.6, 6.2. APP: dữ liệu giả, chưa có contract sức khỏe phụ huynh.

## Quyết định đã chốt
1. Rule đã CONFIRMED dùng nguyên: CHD-06, CHD-07 (nội dung, người nhận theo BE giả), HLT-02..05, HLT-11, PAR-01, PAR-03, AI-01..09.
2. Không làm: P-40 (điều chỉnh tay mức BMI — PENDING), báo cáo 5.3 (hoãn).

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** HT/PHT (`CHILD_PROFILE_EDIT`) sửa khai báo; khai Có tiếp xúc lao ⇒ báo HT, PHT điểm trường, GV lớp, người lưu; đổi ngày ⇒ báo HT, PHT, người lưu. Nhân sự xem trẻ (HT, PHT điểm trường, GV lớp) bấm phân tích xu hướng ⇒ AI tạo nháp; nhân sự đó duyệt ⇒ phụ huynh thấy. Phụ huynh xem kết quả đã công bố của con (số đo kỳ cân đo, phiếu khám đã kết luận, xu hướng, giải thích đã duyệt); nhận thông báo khi có kết quả/kết quả sửa.
- **Đối chiếu:** BE giả AiTrendPanel ghi "không có bước duyệt, chỉ nhân sự xem" ⇔ HLT-05/PAR-03 (phụ huynh chỉ thấy bản đã duyệt) ⇒ giữ nháp như BE giả cho nhân sự, thêm bước duyệt cho phụ huynh (không đổi màn FE hiện có).
- **Ảnh hưởng:** child (khai báo, CHD-07), health (phụ huynh xem, thông báo, giải thích AI), notification (loại mới), `integration/ai` mới. FE: HealthDeclarationPage, ChildProfilePage (đọc `declaration`), AiTrendPanel (response như BE giả). APP: màn sức khỏe phụ huynh (API mới), loại thông báo mới.

### Câu hỏi mở
Không chặn; giả định ở "Key decisions".

## Acceptance criteria
- **AC-1** (CHD-06): PUT khai báo hợp lệ ⇒ lưu mọi mục, thay danh sách dị ứng, lịch sử UPDATED kèm `allergyChange`/`historyChange` khi đổi; lỗi ô không tiền tố `declaration.`; thiếu lý do khi đã có khai báo ⇒ 400 `reason`; trẻ đã nghỉ ⇒ 409 3011.
- **AC-2** (CHD-07): chuyển sang Có tiếp xúc lao (tiếp nhận, nhập Excel, sửa khai báo) ⇒ `CHILD_TB_CONTACT`; đổi ngày đã có (sửa khai báo, kết luận phiếu khám) ⇒ `CHILD_TB_CONTACT_DATE_CHANGED`; lưu lại không đổi ⇒ không báo.
- **AC-3** (CHD-06, FE mục 4.6): `GET /children/{id}/profile` trả `declaration` (+ `declaredByName`, `history[]`).
- **AC-4** (PAR-01, PAR-03, HLT-11): phụ huynh có liên kết, trẻ chưa nghỉ ⇒ `GET /children/{id}/health/published` trả kết quả đã công bố (không nháp phiếu khám, không lịch sử sửa, không người nhập) + giải thích đã duyệt; phụ huynh trẻ khác ⇒ 403.
- **AC-5** (HLT-11): lưu số đo / kết luận phiếu khám ⇒ phụ huynh nhận `HEALTH_RESULT_PUBLISHED`; sửa kết quả đã công bố ⇒ `HEALTH_RESULT_UPDATED`. Mở/mở lại đợt khám, kỳ cân đo ⇒ GV lớp + PHT điểm trường nhận `HEALTH_CHECK_OPENED`.
- **AC-6** (HLT-04, HLT-05, AI-01..06): `POST /children/{id}/health/trend-analysis` ⇒ <2 lần đo: `NOT_ENOUGH_DATA`; đủ: nháp `{status OK, draft true, summary, warnings[]}` lưu `source=AI`, model, thời điểm; AI tắt/lỗi ⇒ 503 8001, quá giờ ⇒ 504 8002; input không có tên, ngày sinh, mã trẻ; output có ngôn ngữ chẩn đoán ⇒ bỏ, báo không dùng được. Duyệt ⇒ APPROVED, phụ huynh thấy bản duyệt mới nhất.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| API phụ huynh | `GET /children/{id}/health/published` (PARENT + nhân sự xem trước) | Trả DTO khác theo vai ở `GET /children/{id}/health` | Một endpoint một shape (OpenAPI rõ); quyền theo `ChildAccessPolicy.checkCanReadChild` (phụ huynh liên kết, trẻ chưa nghỉ) |
| Duyệt giải thích AI | Nhân sự xem được trẻ (HT, PHT điểm trường, GV lớp) duyệt; sửa câu chữ trước khi duyệt được | Quyền mới `HEALTH_INTERPRETATION_APPROVE` | HLT-05 "nhân viên duyệt"; không thêm mã quyền khi team chưa chốt |
| Adapter AI | Một adapter OpenAI-compatible (Gemini và Groq đều có endpoint này), cấu hình `carenest.ai.*`; không cấu hình ⇒ AI tắt (503) | SDK riêng từng hãng | AI-09 hai hãng; không thêm dependency; AI-03 tắt vẫn chạy |
| Cảnh báo xu hướng | Tính xác định (HLT-02, `HealthGrowth.warnings`) từ số đo đã công bố + z WHO: sụt ≥ 0,2 kg giữa hai lần đo, chiều cao không tăng ≥ 90 ngày, z cân nặng/tuổi hoặc chiều cao/tuổi < −2, z BMI ngoài ±2, z BMI đổi ≥ 1 (info); AI chỉ viết `summary` | AI tự sinh cảnh báo | HLT-02, HLT-04: số liệu xác định, AI chỉ diễn giải |
| Nội dung thông báo phụ huynh | Không ghi số đo/kết luận cạnh tên trẻ (APP PUSH_NOTIFICATION) | Ghi số đo | Push gửi title/body ra thiết bị |
| Nguồn đổi ngày lao từ phiếu khám | Thêm tham số nguồn cho `updateMedicalHistory` | Suy từ ghi chú | Rõ ràng, một caller |
| Push thông báo sức khỏe | Loại sức khỏe đẩy câu chung (`PushDispatchListener.HEALTH_TYPES`), câu đầy đủ chỉ trong ứng dụng | Đẩy nguyên câu | Câu lao có tên trẻ + tình trạng sức khỏe; push đi qua bên thứ ba |
| Lời nói chẩn đoán từ AI | Lọc từ khóa (`FORBIDDEN`) ⇒ bỏ, 503 8001, không lưu | Lưu nháp gắn cờ | HLT-04: không lưu ngôn ngữ chẩn đoán; GV bấm lại |
| Bản đã duyệt khi có lần đo mới | Phụ huynh vẫn thấy bản duyệt mới nhất (kèm `measurement_ids`) tới khi nhân sự phân tích lại | Ẩn khi có lần đo mới | PAR-03 không nói; giữ đơn giản, ghi giả định |

## Phases
### Phase 1 — Khai báo sức khỏe + CHD-07 (child)
- C `HealthDeclarationRequest`, `HealthDeclarationResponse`, `TbContactNotifier`; M `ChildRecordService(+Impl)` (`findDeclaration`, `saveDeclaration`), `ChildIntakeService(+Impl)` (CHD-07 khi tiếp nhận/nhập Excel, `updateMedicalHistory` có `changeSource`), `ChildHealthDeclaration(+History)`, `ChildAllergyRepository`, `ChildPhotoProfileResponse`, `ChildPhotoController`, `ChildEnrollmentController`, `EnrollmentFormValidator`, `HealthExamServiceImpl`, `PushDispatchListener`, `NotificationType`.
### Phase 2 — Phụ huynh xem + thông báo kết quả (health)
- C `PublishedHealthResponse`, `HealthGrowth`, `HealthNotifier`; M `HealthMeasurementService(+Impl)` (`getPublishedHealth`, báo khi lưu/sửa), `HealthMeasurementController` (`/published`), `HealthExamServiceImpl` (báo khi kết luận), `HealthCheckServiceImpl` (báo khi mở/mở lại).
### Phase 3 — AI giải thích xu hướng
- C `integration/ai` (`AiClient`, `AiProperties`, `OpenAiCompatibleAiClient`, `AiException`), `HealthInterpretation(+Repository)`, `InterpretationStatus`, `HealthTrendService(+Impl)`, `HealthTrendController`, `HealthTrendAnalysisResponse`, `HealthTrendApproveRequest`; migration V83 (bảng + loại thông báo); `ApiCode` 5040; cấu hình `carenest.ai.*` (`application.yml`, `.env.example`, `compose*.yaml`, `deploy/vps.env.example`).
- Exit mọi phase: unit + WebMvc + integration (PostgreSQL); `node scripts/verify.mjs` full.

## Test matrix
| AC | Rule | Test | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | G94-180/CHD-06 | `ChildRecordServiceImplTest#saveDeclarationRequiresReasonAndUsesUnprefixedFields`, `#saveDeclarationNotifiesTbContactAndReplacesAllergies`; `ChildEnrollmentControllerTest#profileEditorSavesHealthDeclaration`, `#healthDeclarationNeedsProfileEditPermission`; `HealthRecordIntegrationTest#declarationEditNotifiesTbContact` (dị ứng thay, lịch sử JSONB) | 1 | pass |
| AC-2 | G94-180/CHD-07 | `ChildRecordServiceImplTest#saveDeclarationNotifiesTbDateChange`; `HealthRecordIntegrationTest#declarationEditNotifiesTbContact` (HT, PHT, GV; đổi ngày từ phiếu khám ⇒ 3 người); `ChildEnrollmentIntegrationTest#intakeWithTbContactNotifiesLeaders` (tiếp nhận, chung đường nhập Excel) | 1 | pass |
| AC-3 | G94-180/CHD-06 | `ChildPhotoControllerTest#profileIncludesHealthDeclaration` | 1 | pass |
| AC-4 | PAR-01, PAR-03, HLT-11 | `HealthRecordIntegrationTest#parentSeesPublishedResults` (kết quả + biểu đồ z WHO, phụ huynh khác 403); `HealthRecordControllerTest#parentReadsPublishedHealth`, `#parentCannotReadStaffHealthRecord`, `#anonymousRejected` | 2 | pass |
| AC-5 | HLT-11, HLT-13, HLT-08 | `HealthExamIntegrationTest#teacherDraftsThenConcludesAndEditsWithReason` (kết luận ⇒ PUBLISHED, sửa phiếu đã kết luận ⇒ UPDATED, nháp không báo); `HealthRecordIntegrationTest#parentSeesPublishedResults` (`HEALTH_RESULT_PUBLISHED`, `HEALTH_RESULT_UPDATED`, câu không số đo), `#openingCampaignNotifiesTeachersAndVicePrincipal`; `HealthCheckServiceImplTest` (bộ cũ chạy với `HealthNotifier`) | 2 | pass |
| AC-6 | HLT-02..05, AI-01..06 | `HealthTrendServiceImplTest` (8: thiếu dữ liệu, nháp không danh tính, prompt không ngày đo, chặn chẩn đoán, AI tắt/quá giờ, duyệt có sửa ⇒ HUMAN, ngoài phạm vi, cảnh báo xác định); `OpenAiCompatibleAiClientTest#disabledWithoutKey`; `HealthRecordIntegrationTest#interpretationVisibleToParentOnlyAfterApproval` (V83); `HealthRecordControllerTest#teacherAnalyzesTrend`, `#approveWithoutBody`, `#parentCannotAnalyzeTrend` | 3 | pass |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Chưa có khóa API AI ở môi trường | Phân tích xu hướng 503 | AI-03: sổ sức khỏe, biểu đồ vẫn chạy; cấu hình env khi có khóa | Bỏ cấu hình ⇒ tắt |
| AI viết ngôn ngữ chẩn đoán | Vi phạm HLT-04 | Prompt cấm + lọc từ khóa output, bị lọc ⇒ không lưu | — |
| Thông báo nhiều khi GV lưu cả lớp | Phụ huynh nhận nhiều thông báo | Mỗi lần lưu một trẻ ⇒ một thông báo/phụ huynh | — |

## Validation log
- [x] Khẳng định về code có `file:line` (Context; tên file/hàm ở Phases)
- [x] Đổi contract ⇒ consumer cụ thể: FE `HealthDeclarationPage` (PUT `/children/{id}/health-declaration`), `ChildProfilePage` (`data.declaration` thêm cạnh `data.child`, không phá shape cũ), `AiTrendPanel` (response như BE giả + `id`, `source`, `approvedAt`; thêm duyệt `POST …/{id}/approve` — FE chưa có nút duyệt); APP màn sức khỏe phụ huynh (`GET /children/{id}/health/published`), loại thông báo mới (`CHILD_TB_CONTACT`, `CHILD_TB_CONTACT_DATE_CHANGED`, `HEALTH_RESULT_PUBLISHED`, `HEALTH_RESULT_UPDATED`, `HEALTH_CHECK_OPENED`)
- [x] Mỗi AC có test; Exit có lệnh (`node scripts/verify.mjs`)
- [x] Không dùng rule PENDING như đã chốt (P-40 không làm)

## Progress log
- 2026-11-10: Phase 1–3 xong. V83; `PUT /children/{id}/health-declaration`, `declaration` ở `GET /children/{id}/profile`, CHD-07 (tiếp nhận, nhập Excel, sửa khai báo, phiếu khám); `GET /children/{id}/health/published`; thông báo kết quả, mở/mở lại đợt/kỳ; `integration/ai` + `/children/{id}/health/trend-analysis` (phân tích, xem, duyệt). Docs: card health, child, notification, ai-assistance; ERROR_CONTRACT (5040); DATA_DICTIONARY (`health_interpretations`, loại thông báo V83); OpenAPI snapshot. Chưa: FE nút duyệt giải thích AI, APP màn sức khỏe phụ huynh (repo khác, cần user cho phép); khóa AI ở môi trường (`CARENEST_AI_API_KEY`).
