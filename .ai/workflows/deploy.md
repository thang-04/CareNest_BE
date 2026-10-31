# Workflow — Build & deploy

> Server demo: `docs/system/DEPLOYMENT.md` (VPS, Docker Hub, CI `.github/workflows/deploy.yml`). Không suy đoán secret; giá trị thật chỉ ở `.env` trên VPS. Chạy local/dev: `README.md` (mục Chạy, Cấu hình).

VPS demo tóm tắt (chi tiết + bảng cổng: `DEPLOYMENT.md`): push `main` (merge PR từ `release/*`; PR vào `main` chỉ verify) ⇒ CI verify ⇒ push `thangnd04/carenest-be:api-<hash>` + `:keycloak-<hash>` (private, tag theo nội dung; tag đã có ⇒ bỏ verify/build phần đó) ⇒ VPS `/data/thangnd/CAPSTONE/CareNest_BE` pull + `docker compose -f compose.vps.yaml up -d --no-build`. Cổng loopback: api 18310 · Keycloak 18320/18321 · Postgres 18330 · Mailpit 18340/18341 · FE 18300. Public: `web.carenest.edu.vn` (FE + `/api` ⇒ 18310), `auth.carenest.edu.vn` (Keycloak, chặn `/admin`).

1. **Package:** `./mvnw -B package` (Windows: `mvnw.cmd`).
2. **Test:** `node scripts/verify.mjs` (= `mvnw verify`: test + ArchUnit + snapshot OpenAPI + Spotless); không deploy khi fail hoặc test tích hợp/snapshot bị skip. Testcontainers cần Docker.
3. **Docker:** `docker compose up --build` (API + PostgreSQL) theo `Dockerfile`, `compose.yaml`.
4. **Environment:** tên biến theo `.env.example` + bảng Cấu hình trong README; secret không nằm trong repo/image.
5. **Deploy server:** merge PR vào `main` ⇒ CI deploy theo `DEPLOYMENT.md`. Thao tác server thật, migration DB production, xóa dữ liệu ⇒ xác nhận với người dùng trước.
6. **Verify:** `/actuator/health`, Swagger UI, 1 luồng chính khi đã có (điểm danh → số suất).
7. **Memory:** lỗi môi trường tốn >15 phút ⇒ `update-knowledge.md` T2 (`ENV-` + `TROUBLESHOOTING.md`).
