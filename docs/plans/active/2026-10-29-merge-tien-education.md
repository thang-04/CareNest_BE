---
title: Ghép code giáo dục mới của Tiền với WIP hiện tại
status: in-progress
owner: Nhữ Đình Cần
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [education-plan, learning-observation]
rules: [EDU-19, OBS-22]
risk: high
created: 2026-10-29
updated: 2026-10-29
---
# Ghép code Tiền, giữ code hiện tại

## Phạm vi và nguồn duyệt

Người dùng 29/10 yêu cầu merge code mới của Tiền ở BE/FE vào folder của mình, dùng Git DinhCan. Đích: hai repo trong `D:/FULearning/Fall2026`; GCM đang có `NhuDinhCan`, Git author NhữĐìnhCần. Giữ branch hiện tại, không push hoặc thay DB. Đây là ghép các tính năng đã có, không thiết kế nghiệp vụ mới.

- BE nguồn: `origin/feature/G94-226-BE-FEAT-09-school-year-goals` = `6ce4be2`.
- FE nguồn: `origin/feature/G94-178-FE-FEAT-41-lesson-assessment-parent` = `eaddd72`.
- HEAD trước ghép: BE `9c95058`, FE `f9f98dc`; cả hai là ancestor của nguồn, có thể fast-forward.
- Giữ WIP điểm danh, sức khỏe, đánh giá ngày/mở lại/tổng quan và nhận xét lớp. Thêm nguồn chương trình, phiên bản kế hoạch, học liệu và sửa session của Tiền.

## Key decisions / Rủi ro

- Snapshot ngoài Git ở `D:/FULearning/Fall2026/backups/merge-tien-2026-10-29T16-05-14-089Z`, gồm patch, file WIP và SHA-256 (FE 35, BE 59 file). Không xuất nội dung cấu hình nhạy cảm.
- Stash WIP có untracked, fast-forward đúng SHA nguồn, apply lại stash và giữ stash làm điểm khôi phục; không reset, force, drop stash hoặc push.
- Preview WIP có conflict FE menu/mock assessment/index tri thức; BE các tài liệu nghiệp vụ. Ghép nội dung hai phía, không chọn nguyên file làm mất WIP.
- Tiền đã commit V27–V31; local V27 daily observations và V28 class comments chưa commit. Giữ migration đã commit, chuyển hai file local sang V32/V33, giữ SQL. Không chạy Flyway/repair hoặc sửa history DB người dùng. PostgreSQL Docker hiện tại chưa có V27 trở lên; DB khác chưa kiểm chứng.
- Giữ cấu hình local; ghép thêm thuộc tính học liệu từ incoming. API/mã lỗi và snapshot phải chứa cả education lẫn observation; APP không sửa.
- Dependency từ hai nhánh nguồn là phần cần thiết để ghép: S3 SDK và Playwright. Không tự nâng phiên bản khác.

## Acceptance criteria và phase

1. Cả hai SHA nguồn nằm trong ancestry của branch hiện tại; author/GCM DinhCan giữ nguyên; không push.
2. WIP ngoài phần chồng lấn giữ hash; phần chồng lấn giữ cả tổng quan/nhận xét lớp và tính năng Tiền. Config local không mất.
3. Migration duy nhất V1–V33; V27–V31 khớp nguồn; V32/V33 giữ SQL cũ, cập nhật tham chiếu local. PostgreSQL test container chạy được; không đổi DB người dùng.
4. Full verify hai repo, check-ai-layer, review FE/BE. Full FE baseline trước merge fail test AI week trùng seed; incoming có bản sửa, cần kiểm lại thực tế. Browser/E2E thực chưa được xác nhận trong task.

Phase 1: sao lưu + preview + ghép source, giải quyết conflict và migration. Phase 2: dependency, verify, review và ghi bàn giao; người dùng đã yêu cầu thực hiện merge, tiến hành liên tục trong phạm vi này.

## Progress

- 29/10: đọc hướng dẫn hai repo, fetch thành công bằng GCM NhuDinhCan; snapshot và preview hoàn tất, chưa thay code/DB. Plan client ở FE liên kết về đây. Không có câu hỏi nghiệp vụ mới; đổi số migration local là xử lý kỹ thuật để giữ migration đã commit của nguồn.

- Hoàn tất ghép source và phục hồi WIP; giữ stash FE2eeadd4b/BE90b35016. Review phát hiện lineage EDU-26: đã có red/green daily và tổng quan; BE29/29, FE dashboard/hardening65/65. Thêm unit nhiều ngày/hoạt động. Full FE trước mở rộng assertion334/334; đang chạy lại cổng đầy đủ sau sửa cuối, kết quả ở verify.log/target/verify.log và bàn giao. Không kiểm browser/E2E/S3 hoặc áp migration DB user; không push/commit WIP.
- Npm peer Vite8/plugin-react4 có sẵn: npm install thường ERESOLVE, npm ci legacy gặp native file đang dùng; npm install legacy/ignore-scripts phục hồi. Package/lock giữ nguyên incoming, không nâng dependency khác hoặc dừng dev server.
