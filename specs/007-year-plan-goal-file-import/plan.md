# Implementation Plan: Nhập file mục tiêu năm học

**Spec**: [spec.md](spec.md) · **Date**: 2026-11-01

## Summary

BE đọc file (Word/Excel/CSV) thành bản xem trước bảng mục tiêu; Web hiện xem trước và bổ sung vào bản đang soạn. Không đổi cấu trúc DB, không thêm thư viện.

## Technical Context

- BE: Java 21, Spring Boot 4; đọc `.docx`/`.xlsx` bằng `java.util.zip` + `DocumentBuilderFactory` (tắt DTD, external entity; giới hạn dung lượng giải nén 20 MB, chỉ đọc entry cần); `.doc` do CareNest xuất là HTML ⇒ tách `<tr>/<td|th>` có `rowspan/colspan`; CSV theo RFC 4180.
- Tầng: `controller/eduplan/EduYearPlanController` (thêm endpoint) → `service/eduplan/EduGoalFileService` (+ `impl`) → `service/eduplan/impl/goalfile/*` (đọc lưới ô, diễn giải bảng). DTO `dto/eduplan/GoalFilePreviewResponse`.
- ApiCode mới 7033–7035 (file không hỗ trợ / không có bảng mục tiêu / file quá lớn hoặc quá nhiều dòng). OpenAPI snapshot cập nhật.
- Web: `services/education-plan` facade `previewGoalFile(file)` (api: multipart; mock: CSV/HTML đọc bằng chính bộ diễn giải JS để demo), `components/education-plan/ImportGoalFileModal.jsx`, ghép bằng `models/education-plan/importGoalFile.mjs` (bổ sung + dấu tích theo viết tắt, báo trùng). File mẫu: `utils/education-plan/yearPlanDoc.js` thêm `goalTemplateFile(themes)`.

## Constitution / rules check

- Không đổi cấu trúc DB (luật Tiền 31/10) ✔ · Không thêm dependency ✔ · BE sở hữu luật đọc/kiểm file ✔ · Lưu tay EDU-22 ✔ · Không đổi luồng sửa ba bước ✔.

## Design

### Lưới ô chung

Mọi định dạng đọc thành `List<List<String>>` đã **trải gộp ô**: ô gộp dọc (`vMerge`, `rowspan`, Excel `mergeCells`) lặp giá trị ở các dòng dưới **kèm cờ "tiếp nối"** để diễn giải biết là cùng mục tiêu; ô gộp ngang (`gridSpan`, `colspan`) đặt giá trị ở ô đầu, các ô sau rỗng.

### Diễn giải bảng

1. Tìm dòng tiêu đề: dòng có ô chứa "mục tiêu" và ô chứa "nội dung". Hai bố cục:
   - **Bảng Thái Dương** (Word/Excel): cột STT, Mục tiêu, Nội dung, (tùy chọn) Nhóm kết quả mong đợi ở đầu, các cột chủ đề nằm dưới "Chủ đề thực hiện" (dòng tiêu đề thứ hai chứa viết tắt).
   - **Mẫu CSV 6 cột**: `Lĩnh vực | Loại dòng | STT | Mục tiêu | Nội dung | Chủ đề (viết tắt;…)`.
2. Dòng dải (chỉ một ô có chữ, không có nội dung): "I./II./…" hoặc tên lĩnh vực ⇒ đổi lĩnh vực; "*…" ⇒ lĩnh vực con; "- …"/"+ …" ⇒ nhóm kết quả mong đợi.
3. Dòng mục tiêu: câu mục tiêu bỏ "1." / "MT1." đầu câu; ô gộp tiếp nối ⇒ cùng mục tiêu; nội dung bỏ "- "/"+ "/"•". Ô chủ đề có chữ ⇒ tích cột đó (khớp viết tắt cột, không theo chữ trong ô).
4. Cảnh báo theo số dòng (đếm từ 1, theo dòng của bảng/sheet/CSV).

### Web ghép vào bảng

`mergeFileGoals(form, preview)` — thêm vào cuối lĩnh vực cùng tên, bỏ mục tiêu trùng câu (bỏ khoảng trắng, không phân biệt hoa thường), nhóm/lĩnh vực con chỉ thêm khi chưa có cùng chữ; dấu tích theo viết tắt chủ đề hiện có; trả `{ domains, report: { added, contents, ticks, duplicates, missingThemes } }`.

## Progress log

- 2026-11-01: spec + clarify (giả định chờ Tiền duyệt), plan, tasks.
- 2026-11-01: BE xong T001–T008 (GoalFileGrid đọc .docx/.xlsx/.doc HTML/CSV bằng JDK, XML chặn DOCTYPE, giới hạn giải nén; GoalFileTable diễn giải; endpoint + OpenAPI; EDU-46 PROPOSED). Số luật dùng EDU-46 vì file luật nháp 30/10 đã đặt EDU-44/45 cho luật đề xuất khác.
- 2026-11-01: FE xong T009–T013. Ghép dùng lại mergeImportedGoals (EDU-24) với 2 tùy chọn mới chỉ cho nhập file: themesOf (giữ dấu tích khớp viết tắt) và freeContext (dòng file không ghi lĩnh vực con/nhóm thì nằm cuối lĩnh vực như gõ tay — E2E lần đầu phát hiện mục tiêu bị loại vì ngữ cảnh). Mock chỉ đọc CSV.
- 2026-11-01: Gate T014: BE verify PASS (556 test, archunit, snapshot, spotless); FE verify PASS; Playwright kế hoạch giáo dục 37/37 với BE bản mới chạy ở cổng 8081 (DEV_BE_TARGET/E2E_BE_URL) vì BE :8080 của Tiền chạy code cũ.
