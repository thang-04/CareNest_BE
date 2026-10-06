# Definition of Done

Agent và developer đối chiếu trước khi báo hoàn thành, theo làn (`AGENTS.md`). Bỏ qua mục không áp dụng, nhưng nêu lý do. Bằng chứng: `docs/quality/VERIFICATION.md`.

## Làn S (3 mục)
- [ ] Đúng phạm vi, không đổi contract/hành vi nghiệp vụ ngoài yêu cầu (đổi ⇒ đã qua `clarify-business.md`).
- [ ] Có test cho thay đổi; bug fix có regression test fail-trước/pass-sau.
- [ ] `VERIFY PASS quick` (hoặc full) trong lượt, sau lần sửa cuối.

## Làn M/L — Code
- [ ] Nghiệp vụ đã rõ (`clarify-business.md`); câu trả lời mới đã ghi T1.
- [ ] Đúng module sở hữu; chỉ import service của feature đó của module khác (`docs/system/MODULE_MAP.md`).
- [ ] Rule nghiệp vụ dẫn chiếu ID (`BUSINESS_RULES.md`); không implement rule PENDING như đã chốt.
- [ ] Permission + access scope kiểm tra ở BE (nếu chạm dữ liệu trẻ/lớp).
- [ ] Không secret, không dữ liệu trẻ thật trong code/test/log.

## Làn M/L — Test
- [ ] Test cho hành vi chính và lỗi quan trọng (`TEST_STRATEGY.md`); test hành vi có rule ID ⇒ `@DisplayName("RULE-ID: …")`.
- [ ] Bug fix có regression test fail-trước/pass-sau.
- [ ] Chạm scope ⇒ có test chặn truy cập ngoài scope.
- [ ] Chạm AI ⇒ có test luồng khi AI tắt.
- [ ] `VERIFY PASS full`, skip 0 trong lượt (Docker tắt ⇒ báo "chưa kiểm chứng", không báo pass).

## Làn M/L — Contract & docs
- [ ] Đổi API ⇒ snapshot `docs/api/openapi.yaml` cập nhật có chủ đích + consumer FE/APP cụ thể (`CROSS_REPO_MAP.md`).
- [ ] Đổi schema ⇒ migration + `DATA_DICTIONARY.md`.
- [ ] Đổi/chốt rule ⇒ `BUSINESS_RULES.md` + module card.
- [ ] Module bắt đầu có code / xong ⇒ `docs/context/CURRENT_STATE.md`.
- [ ] Quyết định kiến trúc mới ⇒ ADR.
- [ ] Làn L: plan `approved`, Progress log cập nhật; xong ⇒ `done` + chuyển `completed/`.

## Engineering memory
- [ ] Đã chạy `.ai/workflows/update-knowledge.md` nếu có trigger T1/T2/T3 (hoặc 1 dòng `Memory: không cần — <lý do>`).
- [ ] Sửa `.ai/`, `.claude/`, `.agents/`, `docs/` ⇒ `node scripts/check-ai-layer.mjs` exit 0 (gồm skills mirror).
