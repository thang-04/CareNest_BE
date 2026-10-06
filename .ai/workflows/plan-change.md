# Workflow — Plan cho thay đổi làn L

Nguồn duy nhất: khi nào cần plan file, plan gồm gì, duyệt thế nào. Template: `docs/plans/_TEMPLATE.md`.

## Khi nào (làn L — bất kỳ điều nào)
- Sửa `src/main/resources/db/migration/**`, `security/**`, `integration/**`.
- Đổi contract: endpoint/DTO, `ApiCode`, `ResponseJson`, `GlobalExceptionHandler`, prefix, `docs/api/openapi.yaml`.
- `pom.xml`, `Dockerfile`, `compose.yaml`, harness (`.githooks/`, `.claude/hooks/`, `.claude/settings.json`, `scripts/`).
- Chạm ≥2 module, ảnh hưởng FE/APP, cần ADR, hoặc >8 file.

Không cần plan file: làn S/M (mini-plan 3–5 gạch trong chat nếu >1 bước). Iron Law vẫn áp dụng. Hook plan gate chỉ nhận ra tập con theo đường dẫn (security, integration, migration mới).

## Bước
1. `clarify-business.md` trước. Đã có plan cùng chủ đề trong `docs/plans/active/` ⇒ cập nhật, không tạo mới.
2. Tạo `docs/plans/active/YYYY-MM-DD-<slug>.md` từ template, `status: draft`, `branch:` = branch hiện tại.
3. Rule ID (chỉ CONFIRMED/ACCEPTED) → AC-n Given/When/Then → phase → test `@DisplayName("RULE-ID: …")`.
4. **Tự kiểm** (Validation log): claim về code có `file:line`; đổi contract ⇒ consumer cụ thể; >8 file hoặc >3 phase ⇒ đề xuất tách; ≥2 phương án ⇒ Key decisions (kiến trúc ⇒ ADR); không placeholder/mâu thuẫn/câu mơ hồ.
5. Trình user. Chỉ khi user duyệt **và** "Câu hỏi mở" không còn `- [ ]` ⇒ `status: approved` + entry Progress log (ngày, người duyệt). Không tự approve.
6. Mỗi phase: làm → verify (`docs/quality/VERIFICATION.md`) → Progress log → **dừng chờ review**. Bắt đầu phase 1 ⇒ `in-progress`.
7. Lệch plan ⇒ ghi "Khác plan" + lý do; đổi "Quyết định đã chốt" ⇒ hỏi user trước.
8. Xong + DoD ⇒ `status: done`, chuyển sang `docs/plans/completed/` (commit khi user cho phép).

## Quy tắc
- Plan ≤ ~150 dòng; hướng dẫn dài ⇒ doc riêng, link.
- Hook hỏi "chưa có plan approved" ⇒ trả lời đúng sự thật, không lách.
- Review làn L chạy qua subagent nếu công cụ hỗ trợ (không nạp diff lớn vào context chính).
