# Research: Danh mục Chương trình GDMN của Bộ

## R1. Văn bản nguồn

- **Quyết định**: Văn bản hợp nhất 01/VBHN-BGDĐT ngày 13/4/2021 của Bộ GDĐT (hợp nhất TT 17/2009/TT-BGDĐT, TT 28/2016/TT-BGDĐT, TT 51/2020/TT-BGDĐT) — Chương trình GDMN đang có hiệu lực.
- **Lý do**: Tiền chốt 1A; Thái Dương lập kế hoạch năm theo chương trình này. QĐ 4222/QĐ-BGDĐT (Bộ chuẩn 5 tuổi, 6 lĩnh vực) và QĐ 388/QĐ-BGDĐT (chương trình thí điểm 2026–2027) không thuộc phạm vi; bảng nguồn cho phép thêm sau.
- **Nơi lấy dữ liệu**: toàn văn HTML tại caselaw.vn (tải 28/10/2026). Link PDF vbpl.vn trước đây (`/FileData/TW/Lists/vbpq/Attachments/147452/...`) trả 404 ngày 28/10/2026. Link chính thức lưu: Cơ sở dữ liệu quốc gia về văn bản pháp luật (vbpl.vn), tra số hiệu 01/VBHN-BGDĐT — **NEEDS VERIFICATION**: nhóm thay bằng URL trang văn bản cụ thể khi tìm được (chỉ sửa 1 dòng dữ liệu nguồn).

## R2. Cách tách

- Tách bảng HTML có rowspan/colspan thành lưới; lấy cột nhà trẻ "24–36 tháng tuổi" (5 bảng mục D, 5 bảng mục C) và mẫu giáo "3–4/4–5/5–6 tuổi" (8 bảng mục D, 8 bảng mục C).
- Ánh xạ D (Tiền chốt "giống Thái Dương"): tiêu đề hàng "n. …" → nhóm kết quả mong đợi; câu "n.m. …" trong ô độ tuổi → mục tiêu (bỏ dấu ":" cuối); gạch đầu dòng dưới câu → nội dung; gạch đầu dòng tràn sang hàng bảng sau thuộc câu liền trước; ô không đánh số → một mục tiêu là cả ô.
- Ánh xạ C: tiêu đề hàng "n. …" → mục nội dung; dòng "- X:" có dòng "+ …" bên dưới → nhãn X + từng dòng "+" là một gợi ý; dòng "- …" không có dòng con → một gợi ý.
- Văn bản hợp nhất đã gồm câu đang có hiệu lực; dấu chú thích "[nn]" bị bỏ; dòng chỉ còn chú thích (đoạn đã bãi bỏ theo TT 28/2016) bị bỏ.
- Nhà trẻ mục IV "Tình cảm, kỹ năng xã hội và thẩm mỹ": nhóm/mục về hát, vận động theo nhạc, vẽ, nặn, xem tranh (D nhóm 4, C mục 3) → Phát triển thẩm mỹ; còn lại → Phát triển tình cảm và kỹ năng xã hội; tham chiếu mục giữ "IV".
- Kết quả: 327 mục tiêu (41/84/99/103), 423 gợi ý nội dung (77/119/121/106). Mẫu đối chiếu: nhóm "Thực hiện được các động tác phát triển các nhóm cơ và hô hấp", MT 3–4 tuổi "Thực hiện đủ các động tác trong bài tập thể dục theo hướng dẫn." khớp gợi ý Thái Dương đang có ở FE.
- Công cụ tách (không commit, nằm scratchpad của phiên): `tables.py`, `gridlib.py`, `extract.py`, `extract_contents.py`; migration V34 sinh từ JSON kết quả.

## R3. Lưu trữ

- **Quyết định**: bảng riêng cho danh mục (nguồn, mục tiêu + nội dung, gợi ý nội dung), nạp bằng migration; `edu_goal_rows.curriculum_goal_id` giữ liên kết (ON DELETE SET NULL). Bảng cũ `edu_expected_results` giữ nguyên (đang rỗng, ngoài phạm vi).
- **Phương án bỏ**: nhúng JSON vào FE (không minh bạch, không dùng được ở App); thêm cột vào `edu_expected_results` (bảng chỉ có 1 tầng câu).

## R4. Lấy vào kế hoạch

- **Quyết định**: làm ở client như EDU-24: FE dựng "kế hoạch nguồn ảo" từ danh mục rồi dùng lại hàm ghép mục tiêu hiện có; lưu bằng PUT kế hoạch như cũ (gửi thêm `curriculumGoalId`). BE kiểm `curriculumGoalId` thuộc đúng nhóm tuổi của kế hoạch.
- Nhãn "Theo Bộ"/"Đã sửa từ Bộ": BE trả câu gốc `curriculumGoalText`; FE so với câu hiện tại.
