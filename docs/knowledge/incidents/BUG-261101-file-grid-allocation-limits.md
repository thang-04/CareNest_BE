---
id: BUG-261101-file-grid-allocation-limits
type: bug
modules: [education-plan]
rules: [EDU-46, EDU-47]
status: fixed
date: 2026-11-01
keywords: [xlsx, docx, colspan, gridSpan, allocation, memory, giới hạn dòng, merge]
similar_to: []
---
# BUG-261101-file-grid-allocation-limits — Lưới mở rộng trước kiểm giới hạn

## Symptom

Bộ đọc JDK dựng lưới thưa XLSX và colspan/gridSpan theo số trong file trước khi GoalFileTable kiểm 2.000 dòng. File nhỏ có thể yêu cầu bảng rất lớn. OLE2 mới đã chặn sớm, các định dạng cũ chưa tương đương.

## Điều kiện tái hiện

Probe an toàn bằng XLSX có A2001/IW1, DOCX gridSpan 257, HTML colspan 257/rowspan 2001, CSV 257 cột/2.001 dòng. Chỉ kiểm vượt giới hạn nhỏ, không chạy probe OOM thật.

## Attempts

| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Hội tụ và review độc lập GoalFileGrid xlsx/ref/docx/html/set | Xác nhận vòng cấp phát trước kiểm, ref tính cột bằng int chưa giới hạn | Validation ở interpreter là quá muộn cho parser tài liệu không tin cậy |
| 2 | Test trực tiếp decoder với 7 input nhỏ vượt row/col/span | RED 1 test/7 assertion failures vì không ném lỗi; sau guard GREEN 20 tests liên quan, 0 fail/error/skip | Không cần thử OOM thật để chứng minh guard nằm sai tầng |

## Root cause

XLSX tạo rows tới grid.lastKey; DOCX/HTML dùng span từ file trong vòng thêm ô; CSV đọc hết mới kiểm ở interpreter. Mục tiêu trước đây từ chối sau parse; endpoint nhánh tái sử dụng decoder nên cần đưa guard về nơi cấp phát.

## Fix

Task converge T019: requireGridSize kiểm 2.000 dòng/256 cột trước mở rộng; ref kiểm cột từng bước nên không overflow, kiểm tọa độ merge; DOCX/HTML kiểm span/hàng và CSV kiểm từng hàng/ô. Không đổi kết quả bảng hợp lệ hoặc thêm dependency; service giữ remap 7035 → 7126.

## Regression test

GoalFileImportTest.gridDimensionsAreCheckedBeforeExpansion: RED 7 failures; GREEN cùng GoalFileImportTest/EduGoalFileServiceImplTest/BranchFileImportTest/EduClassDocControllerTest 20/20. Log target/grid-bounds-red.log, target/grid-bounds-green.log. Full gate và browser import với jar mới ghi tại quickstart feature008.

## Ảnh hưởng

Preview mục tiêu/nhánh dùng chung; không có dữ liệu thật bị sửa, preview không ghi. Mã 7035 và remap 7126 giữ nguyên.

## Lesson

Giới hạn file nén không giới hạn tọa độ/span bên trong; phải kiểm ngay trước mở rộng hàng/cột.
