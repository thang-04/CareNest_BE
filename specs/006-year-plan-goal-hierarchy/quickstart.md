# Quickstart validation
Prerequisite: Java 21, Maven wrapper, Docker đang chạy; chỉ dùng dữ liệu giả.
1. Chạy regression nâng cấp V31→V32: `./mvnw.cmd test -Dtest=EduGoalHierarchyMigrationTest`.
2. Chạy schema/service/class-doc/resource liên quan; xem tên test trong tasks.
3. Chạy `node scripts/verify.mjs`; yêu cầu VERIFY PASS full, skip 0, ArchUnit/snapshot/Spotless ok.
4. Chạy `node scripts/check-ai-layer.mjs`; xem E6 theo output thực tế.
5. Kiểm `git diff --exit-code docs/api/openapi.yaml` và đối chiếu sơ đồ/tài liệu.
6. Converge dựa trên spec/plan/tasks và source; append task nếu còn thiếu, chạy implement và verify sau sửa.
Không chạy ứng dụng để tự migrate DB dev/production đang dùng.

## Evidence vòng 1
- Analyze: 8/8 FR có task; 4/4 SC có acceptance/test; không ambiguity/critical; constitution là template nên không dùng placeholder làm luật.
- RED: hierarchy-red.log: bảng mới chưa tồn tại và ambiguous order chưa bị từ chối. Lần setup đầu sai query separator URL container đã được sửa trước khi lấy RED.
- Implement regression: hierarchy-green2.log: 46 tests, 0 failure/error/skip; V31→V32 trên PostgreSQL thật. Lỗi deferred trigger khi CREATE INDEX được ghi vào BUG-261029-hierarchy-deferred-ddl, đã có GREEN.
- Review độc lập vòng 2: PASS sau fix migration; kiểm cha nullable, ownership, cascade, copy và API.
- Converge 1: FR-002/SC-001 còn thiếu fixture nhiều domain/phiên bản và sequence đã vượt max ID; append-only T011, không đổi ý định spec/plan. Đã bổ sung test, chờ gate cuối xác nhận.
- Drawio: thêm tab đầu `V32 - Ba bảng cha con`, giữ tab đang được user chỉnh sửa; XML/ID/edge/nhãn văn bản thuần đã kiểm.

## Converge vòng 2 — 29/10/2026
Đã đối chiếu lại 8 FR, 4 SC, 3 user stories và các quyết định ownership/ID/migration/API/copy trong plan với source và test. Không còn gap implementation; T011 bổ sung fixture nhiều domain/phiên bản và sequence cũ đã được viết. Converge không thay đổi tasks.md (SHA256 trước/sau: 411067E55257C896ED9B25895FD99158EF22E4FB8054AD1D27A2FE09EED6B1E6). Cổng cuối sau convergence đang chạy, kết quả được ghi bên dưới khi có output.

## Kết quả cuối — 29/10/2026
- `node scripts/verify.mjs` → `VERIFY PASS full | tests 433 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 247s | log target/verify.log`.
- `EduGoalHierarchyMigrationTest`: 5 tests, failure/error/skip 0; có nâng cấp V31 thật với nhiều domain/phiên bản và sequence cao hơn max ID.
- `git diff --exit-code -- docs/api/openapi.yaml` exit 0.
- `node scripts/check-ai-layer.mjs`: 0 lỗi, 0 cảnh báo; E6 bỏ qua theo công cụ.
- 11/11 tasks done; review độc lập PASS; converge không còn gap.
- Giới hạn: chưa chạy V32 lên DB dev đang dùng hoặc production; chưa browser FE E2E. Plan gốc vẫn in-progress cho phạm vi cũ, riêng DB-1 đã hoàn tất.
