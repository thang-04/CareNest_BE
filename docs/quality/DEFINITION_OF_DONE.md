# Definition of Done

Agent và developer đối chiếu trước khi báo hoàn thành. Bỏ qua mục không áp dụng, nhưng nêu lý do.

## Code
- [ ] Đúng module sở hữu; chỉ import service của feature đó của module khác (`docs/system/MODULE_MAP.md`).
- [ ] Rule nghiệp vụ dẫn chiếu ID (`BUSINESS_RULES.md`); không implement rule PENDING như đã chốt.
- [ ] Permission + access scope kiểm tra ở BE (nếu chạm dữ liệu trẻ/lớp).
- [ ] Không secret, không dữ liệu trẻ thật trong code/test/log.

## Test
- [ ] Test cho hành vi chính và lỗi quan trọng (`TEST_STRATEGY.md`).
- [ ] Bug fix có regression test fail-trước/pass-sau.
- [ ] Chạm scope ⇒ có test chặn truy cập ngoài scope.
- [ ] Chạm AI ⇒ có test luồng khi AI tắt.
- [ ] Đã chạy test, báo kết quả thật (không báo pass khi chưa chạy).

## Contract & docs
- [ ] Đổi API ⇒ cập nhật contract, đánh giá tác động FE/APP (`CROSS_REPO_MAP.md`).
- [ ] Đổi schema ⇒ migration + `DATA_DICTIONARY.md`.
- [ ] Đổi/chốt rule ⇒ `BUSINESS_RULES.md` + module card.
- [ ] Module bắt đầu có code / xong ⇒ `docs/context/CURRENT_STATE.md`.
- [ ] Quyết định kiến trúc mới ⇒ ADR.

## Engineering memory
- [ ] Lỗi không hiển nhiên / thử >1 cách / liên module / lỗi môi trường >15 phút ⇒ incident + dòng `ISSUE_INDEX.md` (kèm các cách đã thử thất bại).
- [ ] Bẫy đặc thù module ⇒ 1 dòng "Known pitfalls" trong module card.
- [ ] Bài học tổng quát ⇒ `PATTERNS.md`.
- [ ] Giới hạn còn tồn tại ⇒ `KNOWN_ISSUES.md`.
