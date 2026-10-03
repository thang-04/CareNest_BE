# Deployment

> **Status: CHƯA CÓ NỘI DUNG — không dùng làm nguồn.** Hạ tầng chưa chốt. Agent không suy đoán server, domain, cổng, secret.

Ràng buộc đã biết (NFR): 1 instance BE + 1 PostgreSQL đủ cho quy mô trường; không yêu cầu 24/7 HA hay DR đầy đủ.

Điền khi chốt:

- [ ] Môi trường (dev / staging / demo)
- [ ] Server/VPS, OS
- [ ] Docker image, compose
- [ ] Reverse proxy, TLS
- [ ] Biến môi trường (tên biến, không ghi giá trị secret)
- [ ] Backup PostgreSQL
- [ ] AI provider endpoint (nếu external)
- [ ] Quy trình: `.ai/workflows/deploy.md`
