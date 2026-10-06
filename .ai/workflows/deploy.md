# Workflow — Build & deploy

> Hạ tầng server chưa chốt (`docs/system/DEPLOYMENT.md` là SKELETON). Không suy đoán server, domain, secret; hỏi người dùng. Chạy local/dev: `README.md` (mục Chạy, Cấu hình).

1. **Package:** `./mvnw -B package` (Windows: `mvnw.cmd`).
2. **Test:** `node scripts/verify.mjs` (= `mvnw verify`: test + ArchUnit + snapshot OpenAPI + Spotless); không deploy khi fail hoặc test tích hợp/snapshot bị skip. Testcontainers cần Docker.
3. **Docker:** `docker compose up --build` (API + PostgreSQL) theo `Dockerfile`, `compose.yaml`.
4. **Environment:** tên biến theo `.env.example` + bảng Cấu hình trong README; secret không nằm trong repo/image.
5. **Deploy server:** chỉ khi `DEPLOYMENT.md` đã có nội dung. Thao tác server thật, migration DB production, xóa dữ liệu ⇒ xác nhận với người dùng trước.
6. **Verify:** `/actuator/health`, Swagger UI, 1 luồng chính khi đã có (điểm danh → số suất).
7. **Memory:** lỗi môi trường tốn >15 phút ⇒ `update-knowledge.md` T2 (`ENV-` + `TROUBLESHOOTING.md`).
