---
title: Nhập file chủ đề nhánh và Word doc mục tiêu
status: done
owner: CareNest team
jira: G94-226 / BE-FEAT-09
branch: feature/G94-226-BE-FEAT-09-school-year-goals
modules: [education-plan]
rules: [EDU-12, EDU-14, EDU-18, EDU-26, EDU-39, EDU-40, EDU-41, EDU-46, EDU-47]
risk: high
created: 2026-11-01
updated: 2026-11-02
---
# Nhập file chủ đề nhánh và Word .doc mục tiêu

## Context

Làn L: dependency mới, API mới và Web, >8 file; không migration. [Spec/design](../../../specs/008-branch-plan-file-import/plan.md).
GoalFileGrid.java:70 từ chối OLE2; BranchPlanPage.jsx:304 ẩn Lưu, reviewUi.jsx:63 ẩn gửi khi không có quyền.
Scope/lịch đã có tại EduClassDocServiceImpl.java:243–289 và :622, dùng lại thay vì service/policy mới.

## Quyết định đã chốt

1. User 01/11: ghép tên theo chủ đề vào tuần hệ thống; báo không khớp/thừa, thiếu để trống; không nhận thời gian từ file.
2. Cho thay tên đang có sau xác nhận; không xóa tuần ngoài match. Hai nút luôn hiện/khóa đúng điều kiện, giữ chuỗi EDU-18.
3. User duyệt thêm thư viện đọc .doc và bổ sung cho cả mục tiêu bước 2. Chọn POI HWPF 6.0.0 theo research.
4. User cung cấp quy trình Domain spec/Spec Kit/analyze/implement-converge và xác nhận giữ stack PostgreSQL 17.

## Làm rõ nghiệp vụ

Actor: giáo viên lớp, người nhập mục tiêu hiện hành; outcome: tái sử dụng tên/giữ lịch và quyền. Rules trên đều CONFIRMED.
Flow: [B] chuẩn bị/kiểm file → [S] preview → [B] xác nhận → [S] nháp → [B] Lưu/gửi. [Domain spec 14 mục](../../../specs/008-branch-plan-file-import/domain-spec.md).
Tài liệu EDU-46 chỉ hỗ trợ .doc HTML trước yêu cầu mới; cập nhật .doc nhị phân theo user, không đổi merge mục tiêu. EDU-18 giữ nguyên.
Ảnh hưởng: chỉ education-plan BE/Web; JSONB hiện có, không đổi dữ liệu cũ/schema. App không có màn nhập này, không sửa App.

### Câu hỏi mở

Không còn câu hỏi nghiệp vụ Q1–Q4; user duyệt plan và bắt đầu phase 1 ngày 01/11/2026.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc |
| --- | --- | --- | --- | --- |
| Ghép tuần | Theo chủ đề, báo không khớp/thừa, thiếu để trống | User | 01/11/2026 | EDU-47 |
| Tên cũ | Thay sau xác nhận | User | 01/11/2026 | EDU-47 |
| Nút/duyệt | Luôn hiện, khóa khi không hợp lệ, giữ chuỗi | User | 01/11/2026 | EDU-18/47 |
| .doc | Duyệt lib, hỗ trợ cả nhánh và mục tiêu bước 2 | User | 01/11/2026 | EDU-46/47 |

## Acceptance criteria

- AC-1 (EDU-47): Given mẫu tổng hợp, When preview, Then đủ 35 tên đúng chủ đề/ô gộp, bỏ nhãn/dòng nghỉ/chữ ký.
- AC-2 (EDU-14/39/40/41): Given ngày/tuần file khác, When preview, Then matches dựa lịch server, không đổi năm học/nghỉ.
- AC-3 (EDU-47): Given tên cũ, When xác nhận, Then thay key khớp; Hủy/key không ghép giữ nguyên, clear AI pending tại key thay.
- AC-4 (EDU-18/47): Given bốn trạng thái/quyền xem, When mở trang, Then hai nút hiện/khóa đúng điều kiện, không đổi chuỗi.
- AC-5 (EDU-46): Given goal .doc tổng hợp, When đọc, Then tương đương .docx, hồi quy định dạng/merge/quyền đạt.
- AC-6 (EDU-12/18/26/47): Given trái scope/trạng thái/file lỗi, When preview/save, Then chặn, không ghi dữ liệu trái phép.

## Key decisions

| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| .doc | POI HWPF trong grid hiện có | Tự parser/chuyển Office | Ít code, chạy Linux, user duyệt |
| API | POST class-docs/branch/file-preview, kiểm scope và ghép tuần server | FE tự parse/ghép tuần | BE sở hữu lịch/quyền |
| Dữ liệu | Preview transient, lưu JSONB cũ | Upload lưu S3/bảng mới | Không cần lưu binary/migration |
| Nút | Branch bật showSend, default caller khác false | Đổi chung mọi màn | Giữ hành vi màn khác |

## Phases

Đề xuất tách ba phase trong cùng plan để review độc lập vì >8 file; mỗi phase là một lát thay đổi hoàn chỉnh.

### Phase 1 — Bộ đọc .doc và bước 2

- Files: M pom.xml, service/eduplan/impl/GoalFileGrid.java, utils/ApiCode.java, FE ImportGoalFileModal.jsx; test/fixture tổng hợp và docs lỗi tương ứng.
- Steps: dependency tree → test RED → HWPF/Table/Cell với ô gộp/giới hạn → test GREEN, hồi quy định dạng, giữ mục tiêu merge.
- Tests: AC-5/6 → GoalFileImportTest, EduGoalFileServiceImplTest, format/security regression.
- Exit: BE `node scripts/verify.mjs` → VERIFY PASS full, skip 0; FE verify nếu sửa lời UI; AI layer exit 0. **Dừng chờ user review.**

### Phase 2 — Preview nhánh BE

- Files: M EduClassDocController.java, EduClassDocService.java, EduClassDocServiceImpl.java, ApiCode.java, docs/api/openapi.yaml; C BranchFileTable.java, BranchFilePreviewResponse.java và test.
- Steps: test RED → parser/mapping/scope read-only → endpoint/DTO → GREEN → snapshot có chủ đích.
- Tests: AC-1/2/6 → BranchFileImportTest, EduClassDocServiceIntegrationTest và EduClassDocControllerTest; EDU-ID trong DisplayName.
- Exit: BE full verify skip 0, review diff OpenAPI và ERROR_CONTRACT/CROSS_REPO_MAP; AI layer exit 0. **Dừng chờ user review.**

### Phase 3 — Web, hai nút, kiểm chứng và hội tụ

- Files: M FE BranchPlanPage.jsx, reviewUi.jsx, educationPlanApi.js, educationPlanService.js và mock repo; C ImportBranchFileModal.jsx, helper áp dụng preview; unit/E2E/knowledge/docs liên quan.
- Steps: test hành vi → modal/xác nhận/patch/pending AI → nút luôn hiện, disabled reason → browser E2E → implement/converge append-only đến sạch.
- Tests: AC-1..6 → node:test importer và Playwright nhập file/save/reload/quyền/trạng thái/hồi quy goal.
- Exit: verify full BE/FE, skip 0 BE; E2E x/y thật; analyze không còn lỗi chặn, converge sạch, docs/check-ai exit 0. **Dừng chờ user review.**

## Test matrix

| AC | Rule | Test | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| 1/2 | EDU-14/39/40/41/47 | Parser + service mapping, ô gộp, 35 tên, không dùng ngày file | 2/3 | Đạt unit/integration + E2E nhánh3/3; file gốc35 tên |
| 3 | EDU-47 | Unit áp patches/AI pending và browser Hủy/xác nhận/lưu/mở lại | 3 | Đạt unit + E2E |
| 4 | EDU-18/47 | Browser 4 trạng thái, chain, caller khác, chặn server | 3 | Đạt E2E chuỗi/return/resubmit, SSR/caller khác |
| 5 | EDU-46 | GoalFileImportTest OLE2/tất cả định dạng | 1/3 | Đạt unit, E2E mục tiêu17/17 |
| 6 | EDU-12/18/26/47 | Service/MockMvc scope, phiên bản/trạng thái, file xấu/no write | 1/2/3 | Đạt service/MVC/E2E và guard7input RED/GREEN |

## Rủi ro & rollback

| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| .doc/ô gộp/POI transitives | Đọc sai mục tiêu hoặc build lệch BOM | Synthetic fixtures, dependency tree, giới hạn trước cấp phát, full regression | Gỡ thay đổi scoped, không sửa dữ liệu |
| Ghép/overwrite/AI pending | Mất hoặc phục hồi tên sai | Preview + xác nhận, chỉ patch key khớp, clear pending tương ứng | Hủy trước Lưu; sau Lưu chỉnh lại thủ công theo quy trình hiện có |
| Nút luôn hiện | Gợi thao tác sai quyền | Disabled reason và server guard hiện có | Gỡ tùy chọn riêng branch, caller khác không đổi |

## Validation log

- [x] Khẳng định code có file:line trong Context/research.
- [x] Contract draft có consumer cụ thể; API/shape goal cũ giữ nguyên.
- [x] Mỗi AC có test và phase exit; >8 file đã chia 3 phase review độc lập.
- [x] Không dùng PENDING/OPEN làm rule đã chốt, Q1–Q4 có nguồn.
- [x] Analyze/review độc lập hoàn tất; sửa tên test/config trước trình duyệt; FR 11/11, SC 5/5 có task.

## Progress log

### 2026-11-01 — Thiết kế, chưa code

- User duyệt nghiệp vụ, thư viện và quy trình; chưa duyệt plan/API mới. Không commit/push/branch.
- Đã tạo spec 008/domain spec/design/tasks (18 task pending). Review độc lập: 11/11 FR và 5/5 SC có coverage, không còn lỗi chặn sau sửa đường dẫn test/config; lịch sử tại feature analysis.md.
- Verify ứng dụng: chưa chạy cho feature (chưa sửa source); tests và E2E đều chưa có kết quả.
- Tiếp: hoàn tất review/analyze, trình user duyệt plan ba phase và endpoint mới.

### 2026-11-01 — User duyệt plan, bắt đầu phase 1

- Nguồn: user trả lời Duyệt plan, bắt đầu phase 1. Status approved theo quyết định user, chuyển in-progress khi bắt đầu.
- Scope hiện tại T001–T006; làm phase 1 rồi dừng review. Phase 2/3 chưa thực hiện; không branch/commit/push.

### 2026-11-01 — Phase 1 hoàn thành, chờ review

- T001–T006 done (6/18); HWPF trong grid chung, giới hạn OLE2, lời lỗi/hướng dẫn BE–FE đồng bộ; không đổi merge mục tiêu. Thêm fixture tổng hợp, không dùng tài liệu thật.
- RED binaryDocMatchesDocx: 1 error/GOAL_FILE_UNSUPPORTED đúng hành vi cũ; GREEN GoalFileImportTest + EduGoalFileServiceImplTest: 13/13, 0 fail/error/skip; chi tiết quickstart feature008.
- BE: `node scripts/verify.mjs` → `VERIFY PASS full | tests 633 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 260s | log target/verify.log`.
- FE: `node scripts/verify.mjs` → `VERIFY PASS full | npm run verify | log verify.log`; lần đầu fail Prettier câu lỗi mới, format đúng file rồi chạy lại đạt.
- AI layer BE 0 lỗi/12 cảnh báo, FE 0 lỗi/14 cảnh báo (cũ); OpenAPI snapshot diff exit 0. Review độc lập không có P1/P2 chặn; AC-5 và phần reader của AC-6 có bằng chứng.
- Khác plan: đồng bộ thêm lời mock/eduMockErrors.js theo contract BE, không đổi mock parser (vẫn CSV). Không có schema/migration hoặc thay đổi API shape.
- Chưa kiểm chứng: browser E2E, upload .doc qua mock Web. Nhập nhánh và hai nút luôn hiện còn ở phase 2/3; chưa tuyên bố toàn feature sạch, không chạy converge toàn feature trước thời điểm phase 3 đã định.
- Không commit/push/branch. Tiếp: user review phase 1; chỉ bắt đầu phase 2 sau review theo plan.

### 2026-11-01 — User cho phép hoàn tất toàn bộ

- User: tiếp tục, bàn giao khi hoàn thành xong hết, tôi đi ngủ đây. Cho phép tiếp tục phase 2/3 và qua các điểm dừng review giữa phase; vẫn tự review và ghi verify theo phase, chỉ bàn giao khi hoàn tất toàn bộ hoặc có blocker thật.
- Không bao gồm commit/push/branch/deploy. Scope/contract/nghiệp vụ đã chốt giữ nguyên.

### 2026-11-01 — Hoàn thành phase 2/3 và hội tụ

- 19/19 task done. Endpoint/mapping server read-only, modal/xác nhận/patch/AI pending, hai nút luôn hiện/disabled theo điều kiện và giữ chain. Không migration.
- BE cuối: `VERIFY PASS full | tests 644 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 248s | log target/verify.log`; FE cuối full verify342 tests, 0 fail/skip, lint/format/design/build đạt.
- Browser jar cuối: branch3/3, goal/import17/17 (gồm .doc bước2), mobile375 không tràn và nút trong viewport trước/sau cuộn. Edu-flow19/19 đạt lượt trước decoderguard; không gọi một lượt39/39. Bằng chứng/lượt thử tại quickstart.
- Hội tụ1 append T019 sửa guard trước cấp phát (RED7, GREEN20); hội tụ2 không gap 11FR/5SC, tasks byte-identical trong thao tác converge, không append rỗng. Review cuối không P1/P2.
- Khác plan: CSV parser tách nguyên trạng sang tiện ích chung để tránh import chéo module, export cũ giữ tương thích; mock errors7124–26/scope cùng BE; sửa test-helper exact label và assertion ID theo loại thực thể. Không đổi nghiệp vụ.
- Memory3 incident fixed, OpenAPI chỉ thêm76dòng đúng endpoint/schema mới; source cũ không đổi shape. Check-ai-layer chạy sau lần sửa tài liệu cuối; kết quả bàn giao.
- Cleanup e2e_plans/temporary_leader/shifted_assignment đều0; dừng/xóa runtime DB/BE riêng, BE8080/Keycloak8180 giữ nguyên. Không commit/push/branch/deploy. DoD feature hoàn thành; chuyển plan sang completed.
