# Workflow — Implement feature

Làn S (`AGENTS.md`): chỉ bước 1 (1 dòng rule), 5, 6, 8. Làn L: bước 1 rồi `plan-change.md`, làm theo phase.

0. **Làn:** xác định S/M/L theo `AGENTS.md`; vượt tiêu chí giữa chừng ⇒ nâng làn.
1. **Làm rõ nghiệp vụ:** `clarify-business.md` — actor, outcome, rule ID + status, đối chiếu tài liệu, ảnh hưởng; chưa rõ ⇒ hỏi tới khi rõ, ghi T1. Không code khi còn câu hỏi nghiệp vụ.
2. **Business flow:** đọc module card + flow liên quan. Phân biệt bước `[B]` (người làm ngoài hệ thống) với `[S]`; không biến bước [B] thành chức năng.
3. **Impact analysis:** module sở hữu; module khác bị chạm (MODULE_MAP — giữ thứ tự lớp, không tạo cycle); scope/permission; dữ liệu dẫn xuất; event; FE/APP (CROSS_REPO_MAP). Chạm ≥2 module ⇒ làn L. Đối chiếu `CROSS_MODULE_ISSUES.md` (rủi ro CMR).
4. **API/DB:** theo `update-api.md` / `update-database.md` nếu có.
5. **Code:** theo `docs/backend-coding-guide.md` (mục 3–13) + `docs/architecture/PACKAGE_STRUCTURE.md` + `.claude/rules/`. AI feature ⇒ qua `AiClient` (`integration/`), output là bản nháp chờ duyệt, có baseline không AI.
6. **Test:** theo `docs/quality/TEST_STRATEGY.md` (rule chính, lỗi, scope, AI tắt). Test hành vi có rule ID ⇒ `@DisplayName("RULE-ID: …")`; làn L map AC-n → test trong plan.
7. **Docs & memory:** cập nhật module card (API, bảng, Known pitfalls nếu gặp edge case), rule mới/đổi, `CURRENT_STATE.md`. Edge case nghiệp vụ đáng nhớ ⇒ `update-knowledge.md` T3. Quyết định kiến trúc ⇒ ADR.
8. **Verify + báo cáo** theo `docs/quality/VERIFICATION.md` (mẫu theo làn): đã chạy gì, chưa kiểm chứng gì, tác động liên repo còn chờ. Làn M/L: Checklist mục 17 của guide + DoD.
