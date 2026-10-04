# Workflow — Fix bug

1. **Triệu chứng:** ghi lại chuỗi lỗi nguyên văn, input, hành vi mong đợi (từ rule ID / flow / test hiện có). Tái hiện bằng dữ liệu tối thiểu, dữ liệu giả.
2. **Tra memory trước khi điều tra:** grep `docs/knowledge/ISSUE_INDEX.md` (chuỗi lỗi, module, từ khóa VN/EN); nếu chạm ≥2 module xem thêm `CROSS_MODULE_ISSUES.md`; lỗi môi trường xem `TROUBLESHOOTING.md`. Có match (kể cả status `open`) ⇒ đọc incident, **đặc biệt mục Attempts** để không lặp cách đã thất bại.
3. **Kiểm chứng với code hiện tại:** incident cũ là manh mối, không phải kết luận. Đọc source/test gần lỗi + module card (Known pitfalls).
4. **Chứng minh root cause** bằng bằng chứng (test fail, log, file:line). Ghi lại từng cách thử và kết quả khi điều tra — dùng cho bước 7.
5. **Đánh giá tác động** trước khi sửa: dữ liệu dẫn xuất (MealCount), scope, API/FE/APP, dữ liệu đã sai cần sửa lại.
6. **Sửa hẹp + regression test** fail-trước/pass-sau. Chạy test liên quan; báo đúng những gì đã chạy.
7. **Cập nhật engineering memory:** theo `update-knowledge.md` T2 (incident mở ngay từ bước 4 nếu lỗi không hiển nhiên; Attempts ghi vào incident).
8. Đối chiếu `docs/quality/DEFINITION_OF_DONE.md`.
