# Troubleshooting

Cách xử lý nhanh lỗi môi trường, build, chạy local, deploy. Mỗi mục: triệu chứng (chuỗi lỗi nguyên văn) → nguyên nhân → cách xử lý → link ENV-xxx nếu có incident.

Keycloak/xác thực: bảng lỗi đầy đủ ở `docs/system/KEYCLOAK.md` mục 8.

### `invalid_grant` — `Account is not fully set up` (Keycloak)
- Nguyên nhân: user còn required action (mật khẩu tạm) hoặc thiếu field bắt buộc của user profile.
- Xử lý: đăng nhập trình duyệt một lần; kiểm *Realm settings → User profile* (`email`, `lastName` không bắt buộc).
- Đã thử không hiệu quả: kiểm `requiredActions` (rỗng).
- Incident: ENV-001

## Format

```markdown
### <chuỗi lỗi hoặc triệu chứng ngắn>
- Nguyên nhân: ...
- Xử lý: lệnh/bước cụ thể
- Đã thử không hiệu quả: ...
- Incident: ENV-xxx
```
