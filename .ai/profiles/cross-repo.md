# Profile CROSS-REPO — BE + FE + APP

Mức: L3.

1. Đọc `docs/system/CROSS_REPO_MAP.md`: ownership + điểm chạm.
2. Liệt kê thay đổi thuộc BE / FE / APP / contract dùng chung.
3. Nếu repo FE/APP có sẵn cạnh BE (thư mục sibling), đọc `AGENTS.md` + `.ai/ROUTER.md` của repo đó; không giả định cả ba được checkout.
4. BE sở hữu rule + contract; client sở hữu UI/state/integration. Không copy business rule sang client.
5. Contract chưa có ⇒ mô tả thay đổi cần thống nhất trước khi code. Không tự sửa repo khác khi chưa được yêu cầu.
6. Lỗi contract/nghiệp vụ phát hiện qua client ⇒ ghi `docs/knowledge/CROSS_MODULE_ISSUES.md` ở BE.
