# Workflow — Implement feature

1. **Requirement:** actor, outcome, điều kiện hoàn thành. Map sang rule ID trong `docs/business/BUSINESS_RULES.md`. Rule PENDING/OPEN ⇒ nêu khoảng trống, hỏi hoặc làm cấu hình được — không tự quyết.
2. **Business flow:** đọc module card + flow liên quan. Phân biệt bước `[B]` (người làm ngoài hệ thống) với `[S]`; không biến bước [B] thành chức năng.
3. **Impact analysis:** module sở hữu; module khác bị chạm (MODULE_MAP — giữ thứ tự lớp, không tạo cycle); scope/permission; dữ liệu dẫn xuất; event; FE/APP (CROSS_REPO_MAP). Chạm ≥2 module ⇒ L3. Đối chiếu `CROSS_MODULE_ISSUES.md` (rủi ro CMR).
4. **API/DB:** theo `update-api.md` / `update-database.md` nếu có.
5. **Code:** theo `docs/backend-coding-guide.md` (mục 3–13) + `docs/architecture/PACKAGE_STRUCTURE.md` + `.claude/rules/`. AI feature ⇒ qua `AiClient` (`integration/`), output là bản nháp chờ duyệt, có baseline không AI.
6. **Test:** theo `docs/quality/TEST_STRATEGY.md` (rule chính, lỗi, scope, AI tắt).
7. **Docs & memory:** cập nhật module card (API, bảng, Known pitfalls nếu gặp edge case), rule mới/đổi, `CURRENT_STATE.md`. Edge case nghiệp vụ đáng nhớ ⇒ `update-knowledge.md` T3. Quyết định kiến trúc ⇒ ADR.
8. Báo: đã chạy gì, chưa kiểm chứng gì, tác động liên repo còn chờ. Chạy Checklist mục 17 của guide + DoD.
