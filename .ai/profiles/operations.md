# Profile OPERATIONS — Docker, CI/CD, env, VPS, deploy

Mức: L2.

Đọc:
1. `docs/system/DEPLOYMENT.md` (hiện SKELETON — nếu vẫn skeleton, không đoán hạ tầng; hỏi).
2. `docs/knowledge/TROUBLESHOOTING.md` + grep `ENV-` trong `docs/knowledge/ISSUE_INDEX.md`.
3. `docs/quality/NFR.md` (không yêu cầu 24/7/DR đầy đủ).
4. Build/config file thực tế trong repo.

Quy tắc: không commit secret/`.env`; chỉ ghi tên biến môi trường. Thao tác trên server thật hoặc không đảo ngược được ⇒ xác nhận với người dùng trước. Lỗi môi trường tốn >15 phút ⇒ ghi `TROUBLESHOOTING.md` + `ENV-xxx`.
