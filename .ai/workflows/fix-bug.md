# Workflow — Fix bug

Làn S (1–2 file, nguyên nhân rõ): bước 1 (ngắn), 2, 5, 6 — triệu chứng, grep memory, sửa, regression test fail-trước/pass-sau. Làn M/L: đủ các bước.

1. **Triệu chứng:** ghi lại chuỗi lỗi nguyên văn, input, hành vi mong đợi (từ rule ID / flow / test hiện có). Không có nguồn nào cho "hành vi đúng" ⇒ `clarify-business.md` trước khi sửa. Tái hiện bằng dữ liệu tối thiểu, dữ liệu giả.
2. **Tra memory trước khi điều tra:** grep `docs/knowledge/ISSUE_INDEX.md` (chuỗi lỗi, module, từ khóa VN/EN); nếu chạm ≥2 module xem thêm `CROSS_MODULE_ISSUES.md`; lỗi môi trường xem `TROUBLESHOOTING.md`. Có match (kể cả status `open`) ⇒ đọc incident, **đặc biệt mục Attempts** để không lặp cách đã thất bại.
3. **Kiểm chứng với code hiện tại:** incident cũ là manh mối, không phải kết luận. Đọc source/test gần lỗi + module card (Known pitfalls).
4. **Root cause đủ 6 mục trước khi sửa:** (1) triệu chứng nguyên văn · (2) cách tái hiện tối thiểu · (3) mong đợi (rule/test) vs thực tế · (4) nguyên nhân tại `file:line` + bằng chứng (test fail, log) · (5) vì sao giờ mới lộ · (6) phạm vi ảnh hưởng: dữ liệu dẫn xuất (MealCount), scope, API/FE/APP, dữ liệu đã sai cần sửa lại. Thiếu mục nào ⇒ chưa sửa. Ghi từng cách thử và kết quả — dùng cho bước 7.
5. **Sửa hẹp + regression test** fail-trước/pass-sau: dán output fail trước fix và pass sau fix (`docs/quality/VERIFICATION.md`).
6. **Dừng khi 3 lần sửa thất bại:** không thử lần 4; ghi Attempts, xem lại giả định/thiết kế, hỏi user.
7. **Cập nhật engineering memory:** theo `update-knowledge.md` T2 (incident mở ngay từ bước 4 nếu lỗi không hiển nhiên; Attempts ghi vào incident).
8. Verify + báo cáo theo làn; làn M/L đối chiếu `docs/quality/DEFINITION_OF_DONE.md`.
