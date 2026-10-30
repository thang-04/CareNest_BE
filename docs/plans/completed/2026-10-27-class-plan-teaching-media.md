---
title: Học liệu hoạt động và chế độ dạy học
status: done
owner: Nguyễn Quang Tiền
jira: unassigned
branch: feature/G94-226-BE-FEAT-09-school-year-goals
modules: [education-plan]
rules: [EDU-12, EDU-13, EDU-14, EDU-17, EDU-18, EDU-22]
risk: high
created: 2026-10-27
updated: 2026-10-27
---
# Học liệu hoạt động và chế độ dạy học

## Context
User yêu cầu tiếp tục chủ đề nhánh/chủ đề/tuần/ngày theo Spec Kit, thêm ảnh/video/slide/tệp và giao diện dạy học. `EduClassDocServiceImpl.java:179` save và `:247` action đã kiểm tiền đề/quyền/trạng thái; FE classPlan.js giữ liên kết hoạt động theo MT. Thiết kế chi tiết: [feature plan](../../../specs/002-class-plan-teaching-media/plan.md).

## Quyết định đã chốt
User đã yêu cầu thực hiện BE/FE, tiếp tục tự động theo quy trình và giao agent chọn đề xuất cho điểm mơ hồ trong tin nhắn hiện tại 27/10/2026. Đây là căn cứ tiếp tục triển khai trong phạm vi này; không tự nhận quyết định từ trường, không có quyền git/deploy/dependency mới.
Giữ EDU-12..23, manual save tài liệu lớp và khóa bản đã gửi/duyệt. Thêm metadata/tệp riêng; PDF/ảnh/video trực tiếp, Office download.

## Làm rõ nghiệp vụ
Actor assigned teacher soạn/gắn học liệu, người có scope xem tài liệu được trình chiếu. Không mở khóa bản đã ban hành hay thay MT. Web: theme và week/day; Mobile không consume API mới. Dữ liệu cũ không đổi.

### Câu hỏi mở
Không có câu hỏi chặn trong phạm vi user giao chọn đề xuất; các lựa chọn kỹ thuật ghi trong feature plan.

## Acceptance criteria
- AC-1 (EDU-12..18): chuỗi tài liệu/duyệt và dữ liệu cũ vẫn hoạt động.
- AC-2 (EDU-12/18): assigned teacher upload/rebind/detach chỉ khi editable; ngoài scope/bản khóa bị từ chối.
- AC-3: tệp hợp lệ, retry không duplicate, binding không chuyển nhầm sau sửa.
- AC-4: presenter theme/day đúng hoạt động/MT, private media, keyboard/fullscreen.

## Key decisions
Metadata mới V31 + S3 private; không binary JSONB. Fingerprint toàn hàng thay đổi → cần gắn lại; tránh đổi ID cũ. Gỡ giữ tệp. Không Office converter mới.

## Phases
1. Spec/Clarify/Plan/Database/Tasks/Analyze: kiểm tra tài liệu trước code.
2. BE private storage/contract/schema + integration tests/snapshot/review.
3. FE quản lý học liệu/presenter + regression/full verify/review/Converge.
Tách BE/FE thành hai phần kiểm chứng, không gộp commit. User yêu cầu chạy liên tục nên review bằng subagent theo AGENTS, tiếp tục trong scope đã giao.

## Test matrix
AC-1: existing EduClassDocServiceIntegrationTest và FE class-plan tests.
AC-2/3: LearningResourcePolicyTest, LearningResourceServiceIntegrationTest, StorageServiceTest.
AC-4: FE learning-resources test và browser smoke.
Exit mỗi phần: node scripts/verify.mjs; docs: node scripts/check-ai-layer.mjs.

## Rủi ro & rollback
Upload lớn/đường dẫn: giới hạn stream/UUID/private. Race duyệt: parent row lock. Vị trí hoạt động đổi: fingerprint conservative. Storage bền vững: S3 private. Rollback app để dữ liệu cũ tiếp tục dùng, không drop bảng/tệp mới.

## Validation log
- Scope/consumer/test mapping nêu trên; không thay nghiệp vụ PENDING/OPEN.
- Baseline tự lưu năm BE380 tests fail0 err0 skip0; FE full verify pass, không phải kiểm chứng học liệu mới.

## Progress log
### 2026-10-27 — Phase 1
- Chưa commit. Spec/clarify và thiết kế dữ liệu đã ghi, đang phân task/analyze.

### 2026-10-27 — Implementation và Converge1..3
- Chưa commit. User đổi nơi lưu sang S3 và đồng ý AWS SDK v2; thay local-storage design bằng ADR-0018, không có Docker data volume cho học liệu.
- BE full 390 tests fail0 err0 skip0, archunit/snapshot/spotless ok137s; FE full pass. Lần đầu phát hiện policy phải ở utils, AWS client factory phải ở integration; đã di chuyển theo gate, không đổi harness.
- Converge append T025 S3, T026 legacy day text, T027 signature-readiness, T028 PDF fallback; không xóa task cũ. Browser mock upload/reload/rebind/detach/presenter/day/keyboard/fullscreen đạt; native PDF render và live S3 chưa kiểm chứng.
- Còn: gate sau sửa PDF/CORS test cuối và review code làn L; subagent review chạm giới hạn, user yêu cầu đợi reset để tiếp tục.


### 2026-10-27 — Review cuối / S3 thật
- User chỉ định cấu hình project ngoài repo để kiểm S3; đã test StorageS3Smoke1/1 pass,4objects roundtrip bytes và anonymous-denied, xóa đúng4object thử. Không đổi policy/IAM, không copy/log secret. Runtime deploy CareNest chưa được cấu hình bằng credential này.
- Review code làn L kết luận không có concrete P1/P2 còn lại. T029 sửa contract multipart và có HTTP test + snapshot; T030 live smoke self-contained, không tự chạyCI.
- BE fresh full391 fail0 err0 skip0, archunit/snapshot/spotless ok110s; đang chạy full lần cuối sau smoke class. FE full verify pass sau PDF fallback; native PDF render trong IAB và full browser-BE-cloud E2E chưa kiểm chứng.

### 2026-10-27 — Hoàn tất
- BE VERIFY PASS full | tests 392 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 121s | log target/verify.log.
- FE VERIFY PASS full | npm run verify | log verify.log; education suite31tests. Check-ai-layer BE0/0, FE0errors/14warnings cũ. Review code và parser delta không concrete P1/P2.
- Live S3 StorageS3Smoke1/1 pass,4objects mỗi lần được đọc khớp bytes, anonymous-denied và dọn sạch. Credential ngoài repo, process-only. Native PDF render/IAB và complete browser-BE-cloud E2E vẫn giới hạn kiểm chứng được ghi trong quickstart.
- T001..T031 resolved; Converge cuối không còn thiếu sót triển khai, tasks giữ nguyên hash sau kiểm tra. Chưa commit/push/deploy.

