---
id: BUG-261108-ministry-source-split
type: bug
modules: [education-plan]
rules: [EDU-28, EDU-61]
status: open
date: 2026-11-08
keywords: [Bộ, nguyên văn, VBHN, 5 - 7, dấu hai chấm, nguồn]
similar_to: []
---

# BUG-261108-ministry-source-split — Câu Bộ bị tách sai và thiếu dấu câu

## Symptom

User yêu cầu câu lấy từ Bộ phải chính xác từng chữ. Audit V34 so với PDF chính thức 01/VBHN-BGDĐT phát hiện ag-2/sort27 mục tiêu 3.1 bị tách tại khoảng số “5 - 7”; 33 mục tiêu khác bị loại dấu hai chấm cuối theo cách tách cũ.

## Điều kiện tái hiện

Đọc catalog ag-2 mục tiêu 3.1: goal dừng ở 5, content D sai bắt đầu 7; PDF28/Công báo40 cột24–36 tháng cho thấy đó là một câu liên tục. Các ca dấu câu có trong [bảng audit](../../../specs/014-curriculum-cascade-suggestions/source-colon-audit.md).

## Attempts

| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | So V34 với PDF Công báo chính thức, rà câu/đúng cột với 33 dấu hai chấm và ca 3.1 | 33 dấu bị bỏ; một câu bị tách sai | Không chỉ so substring, phải kiểm ranh giới và cột |
| 2 | Chạy SQL draft trên DB014 riêng với ROLLBACK | 33 UPDATE, 1 DELETE content sai, 1 UPDATE goal, rollback; exit0 | Điều kiện đúng cho DB test, chưa sửa dữ liệu thực |
| 3 | Testcontainers nâng V93→V94 | Lượt đầu fixture JDBC chọn sai schema vì URL đã có query; sau sửa fixture, 2/2 đạt | Dùng `&currentSchema` khi JDBC URL đã có query |

## Root cause

Thiết kế trích xuất cũ bỏ dấu hai chấm cuối; parser nhận dấu gạch khoảng số “5 - 7” nhầm là bullet. Source V34 dòng31–32 và PDF28 là bằng chứng. Không sửa migration V34 đã commit.

## Fix

V94 sửa 33 dấu hai chấm, ghép câu và loại content catalog sai; mock đồng bộ. Không sửa V34 hoặc bản kế hoạch đã sao chép. Chưa áp dụng trên DB người dùng.

## Regression test

Regression FE RED câu dừng ở 5 → GREEN; PostgreSQL migration 2/2 và hierarchy 5/5, không skip; so 327 mục tiêu/100 nội dung D giữa DB test và mock khớp từng ký tự; live E2E 3/3 sau V94. Audit đầy đủ chiều PDF→catalog và ranh giới còn mở theo T032/T035, do đó incident tiếp tục open.

## Ảnh hưởng

Response catalog Bộ ảnh hưởng Web và mọi client sử dụng, Mobile chưa khảo sát. Dữ liệu đã sao chép là bản độc lập, không tự thay.

Đối chiếu ngược sau V94 xác nhận ít nhất 69 cặp câu–tuổi mục C và 4 cặp mục tiêu–tuổi mục D còn thiếu do ô gộp trải nhiều nhóm tuổi. Xem [báo cáo kiểm trang](../../../specs/014-curriculum-cascade-suggestions/audit/reverse-coverage-review.md). Không coi số đoạn ứng viên tự động là số lỗi.

V95 và mock đã bổ sung các cặp đó và thêm hai cặp nội dung hô hấp xác nhận PDF40. Kiểm chứng tại `docs/quality/VERIFICATION.md`; incident tiếp tục open vì còn phải audit toàn bộ ô gộp/ranh giới, không suy toàn catalog đã đầy đủ.

## Lesson

Ranh giới bullet và dấu gạch của khoảng số cần được kiểm theo văn bản/cột nguồn, không chỉ so chuỗi con.
