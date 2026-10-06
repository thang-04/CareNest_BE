# Workflow — Review code

Đọc mục tiêu thay đổi + diff + module card liên quan. Làn S: tự kiểm 3 điểm (đúng phạm vi, không đổi contract, có test). Làn M: mục 1–8 phần liên quan. Làn L: đủ, kể cả §0 và Kết luận; review qua subagent nếu công cụ hỗ trợ.

0. **Spec trước (làn L):** từng AC / yêu cầu trong plan ⇒ PASS / MISSING / EXTRA. Code đẹp mà sai yêu cầu vẫn là sai.

Kiểm tra theo thứ tự ưu tiên:

1. **Logic nghiệp vụ:** đúng rule ID? implement rule PENDING như đã chốt? bước [B] bị biến thành chức năng? Code theo rule chưa CONFIRMED/ACCEPTED hoặc lệch tài liệu mà không có Q&A/T1 ⇒ **High**.
2. **Security & privacy:** quyền + scope kiểm tra ở service/security; endpoint mới có `@PreAuthorize` + policy scope ở service; IDOR (truy cập theo id ⇒ kiểm scope **trước** khi load); request DTO không nhận field server sở hữu (`id`, `campusId`, `status`, `createdBy`); dữ liệu trẻ trong log/prompt/response; secret.
3. **Architecture:** `ArchitectureRulesTest` đã tự kiểm layer, repository chéo feature, HTTP client ngoài `integration/`, field injection — chỉ review phần nó không thấy: chiều phụ thuộc MODULE_MAP, event phản ứng chéo chạy trước commit, gọi HTTP trong `@Transactional`. Đối chiếu Checklist mục 17 của `docs/backend-coding-guide.md`.
4. **Dữ liệu dẫn xuất:** sửa ngầm số suất đã xác nhận; bếp thấy số chưa xác nhận (CMR-01, CMR-02).
5. **AI:** gọi SDK trực tiếp; output không qua bản nháp chờ duyệt; thiếu baseline khi AI tắt.
6. **Test:** có test rule chính, scope, regression? List có phân trang; lỗi qua `GlobalException`; không N+1.
7. **Convention:** `.claude/rules/`, API/error contract, snapshot `docs/api/openapi.yaml` đổi có chủ đích.
8. **Memory:** thay đổi fix bug không hiển nhiên có incident/ISSUE_INDEX chưa? Đối chiếu Known pitfalls + PATTERNS.

## Phát hiện
Mỗi phát hiện: `[Severity] file:line — kịch bản gây lỗi — cách sửa — đã chứng minh | giả thuyết`. Severity: **Critical** (lộ dữ liệu, vượt quyền, sai kết quả nghiệp vụ lõi) · **High** · **Medium** · **Low**. Spec không rõ ⇒ ghi là câu hỏi, không phải phát hiện. Không tuyên bố đã chạy runtime nếu chỉ đọc tĩnh.

## Kết luận (làn L)
`PASS` · `PASS_WITH_RISK` (liệt kê rủi ro chấp nhận) · `BLOCKED` (≥1 Critical/High đã chứng minh). Tối đa 3 vòng review–sửa, sau đó hỏi user.

## Nhận review
Kiểm chứng từng phát hiện trước khi sửa; sai ⇒ phản biện bằng bằng chứng (`file:line`, test). Không âm thầm đảo quyết định user đã ghi trong plan/ADR.
