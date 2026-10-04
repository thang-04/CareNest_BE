# Profile OPERATIONS — Docker, CI/CD, env, VPS, deploy

Mức: L2.

Đọc:
1. `README.md` (Chạy, Cấu hình) + `.env.example`, `compose.yaml`, `src/main/resources/application.yml`. Server/hạ tầng: `docs/system/DEPLOYMENT.md` (SKELETON ⇒ không đoán, hỏi).
2. `docs/knowledge/TROUBLESHOOTING.md` + grep `ENV-` trong `docs/knowledge/ISSUE_INDEX.md`.
3. `docs/quality/NFR.md` (không yêu cầu 24/7/DR đầy đủ).
4. Build/config file thực tế trong repo.

Quy tắc: không commit secret/`.env`; chỉ ghi tên biến môi trường. Thao tác trên server thật hoặc không đảo ngược được ⇒ xác nhận với người dùng trước. Lỗi môi trường tốn >15 phút ⇒ `.ai/workflows/update-knowledge.md` T2.
