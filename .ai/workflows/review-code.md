# Workflow — Review code

Đọc mục tiêu thay đổi + diff + module card liên quan. Kiểm tra theo thứ tự ưu tiên:

1. **Logic nghiệp vụ:** đúng rule ID? implement rule PENDING như đã chốt? bước [B] bị biến thành chức năng?
2. **Security & privacy:** quyền + scope kiểm tra ở service/security; IDOR; dữ liệu trẻ trong log/prompt/response; secret.
3. **Architecture:** controller gọi repository; service feature A dùng repository feature B; vòng phụ thuộc giữa feature (`docs/system/MODULE_MAP.md`); event phản ứng chéo chạy trước commit. Đối chiếu Checklist mục 17 của `docs/backend-coding-guide.md`.
4. **Dữ liệu dẫn xuất:** sửa ngầm số suất đã xác nhận; bếp thấy số chưa xác nhận (CMR-01, CMR-02).
5. **AI:** gọi SDK trực tiếp; output không qua bản nháp chờ duyệt; thiếu baseline khi AI tắt.
6. **Test:** có test rule chính, scope, regression?
7. **Convention:** `.claude/rules/`, API/error contract.
8. **Memory:** thay đổi fix bug không hiển nhiên có incident/ISSUE_INDEX chưa? Đối chiếu Known pitfalls + PATTERNS.

Mỗi phát hiện: vị trí, kịch bản gây lỗi, cách sửa. Phân biệt lỗi đã chứng minh với câu hỏi/giả định. Không tuyên bố đã chạy runtime nếu chỉ đọc tĩnh.
