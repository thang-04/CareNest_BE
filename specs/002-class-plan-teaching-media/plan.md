# Implementation Plan: Học liệu và dạy học

Date: 2026-10-27. Giữ branch feature/G94-226-BE-FEAT-09-school-year-goals. Spec: spec.md.

## Technical Context
Java21/Spring Boot/PostgreSQL/Flyway; React18/Vite/JavaScript; dependency hiện có + AWS SDK v2 2.57.3 user đã đồng ý. JUnit/Testcontainers, node:test và browser smoke. Một trường/hai điểm trường. Không đổi ID/MT hay chuỗi duyệt EDU-12..23; tài liệu lớp giữ manual save. Không sửa JSONB cũ.

## Constitution Check
Constitution chưa phê chuẩn (template); áp dụng AGENTS. User giao chọn đề xuất và thực hiện trong scope BE/FE; không tạo branch/commit/deploy, chỉ thêm AWS SDK v2 đã được user đồng ý. API mới Web consume, Mobile giữ nguyên contract cũ. Không ghi các lựa chọn kỹ thuật thành quyết định của trường.

## Design
Metadata edu_learning_resources riêng, binary S3 private theo UUID key; không binary JSONB/localStorage. Mock dùng IndexedDB Blob. Migration mới V25, giữ dữ liệu cũ. Parent doc khóa bằng findForUpdateByDocKey đồng bộ với gửi/duyệt. Assigned teacher mới mutate DRAFT/REJECTED; read theo scope sẵn có.

Giữ activity ID goalId#index của themeActivities. SHA256 toàn chuỗi activities của hàng MT, chuẩn hóa CRLF thành LF. Nếu đổi nội dung/thứ tự, học liệu chuyển phần cần gắn lại, không tự đoán. Gỡ bỏ liên kết, giữ tệp để gắn lại.

Ảnh JPEG/PNG/GIF/WebP 5MB, MP4/WebM100MB, PDF/PPTX/DOCX/XLSX/TXT/CSV20MB. Kiểm extension/magic/nonempty. UUID object key, PutObject private; rollback xóa đúng object mới, lỗi cleanup cần đối soát orphan. File private, Resource response có Range/nosniff/no-store. Chỉ tải tài nguyên được chọn. PDF/ảnh/video trực tiếp; Office tải/mở bằng ứng dụng phù hợp, không public viewer.

## Project Structure
BE dưới src/main/java/com/carenest/: entity/eduplan, repository/eduplan, service/eduplan, controller/eduplan, dto/eduplan; integration/storage/StorageService. Tests cùng package. Không cần Docker data volume cho học liệu; bucket S3 private, credential qua môi trường/runtime role.
FE: services/education-plan, models/education-plan, components/education-plan, pages/education-plan/TeachingPresentationPage.jsx, routes/AppRoutes.jsx, styles/modules/education-plan.css. Native IndexedDB wrapper cho mock, no dependency.

## Delivery
1. Spec/clarify/plan/database/contracts/tasks/analyze trước source code.
2. BE tests trước: metadata/storage/auth/binding/API + snapshot, review làn L.
3. FE upload/rebind/detach, presenter theme/day, regression/full verify/review; Converge append task ID mới đến sạch.

## Validation
FR→task mapping trong tasks.md. Tests verify quyền/trạng thái/race/idempotency/binding/type/size và regression chuỗi duyệt. Full verify BE/FE và check-ai-layer. UI smoke ghi rõ mock hay real BE, không tuyên bố E2E khi chưa chạy.

## Thay đổi theo user
2026-10-27: User yêu cầu mọi loại tệp lưu S3. Không dùng local filesystem làm nơi lưu chính. Giữ metadata/private scope; stream qua backend có kiểm quyền. User đã đồng ý SDK; mặc định AWS S3, endpoint tùy chọn cho S3-compatible. User cung cấp cấu hình tham chiếu ngoài repo để test: live S3 put/head/get byte-compare/delete và anonymous-denied đạt, credential chỉ process test. Provider lỗi không fallback lưu giả thành công.

