# Profile OPERATIONS — Docker, CI/CD, env, VPS, deploy

Mức: L2.

Đọc:
1. `README.md` (Chạy, Cấu hình) + `.env.example`, `compose.yaml`, `src/main/resources/application.yml`. Server/hạ tầng: `docs/system/DEPLOYMENT.md` (VPS demo: luồng CI, bảng cổng, tunnel, rollback) + `compose.vps.yaml`.
2. `docs/knowledge/TROUBLESHOOTING.md` + grep `ENV-` trong `docs/knowledge/ISSUE_INDEX.md`.
3. `docs/quality/NFR.md` (không yêu cầu 24/7/DR đầy đủ).
4. Build/config file thực tế trong repo.

VPS (bất biến): mạng host (không có bridge), mọi service bind `127.0.0.1`; cổng CareNest dải 183xx theo bảng trong `DEPLOYMENT.md` — thêm service ⇒ kiểm `ss -ltnp` rồi ghi bảng ở cả BE và `FE:docs/integration/DEPLOYMENT.md`; `8080`, `18080` trên VPS là app khác.

Quy tắc: không commit secret/`.env`; chỉ ghi tên biến môi trường. Thao tác trên server thật hoặc không đảo ngược được ⇒ xác nhận với người dùng trước. Lỗi môi trường tốn >15 phút ⇒ `.ai/workflows/update-knowledge.md` T2.
