---
id: BUG-261106-edu-draft-lease-reconciliation-n-plus-one
type: bug
modules: [education-plan]
rules: [EDU-54]
status: fixed
date: 2026-11-06
keywords: [lease, reconciliation, batch, n-plus-one]
---

Khi lưu chính sách, kiểm tra từng lease bản nháp trước đây gọi riêng service tài khoản và phân công theo từng hồ sơ. Regression PostgreSQL tại `target/feature011-lease-batch-red.log` tái hiện thiếu lookup theo lô. Đã dùng các API đọc theo lô sẵn có, giữ kiểm tra quyền theo nhóm tuổi và cache tra cứu lớp; `target/feature011-lease-batch-green.log` chạy 5 test không lỗi hoặc skip. Phép đo riêng 24 hồ sơ lớp cho 109 statement và 1 batch mỗi loại phân công/tài khoản; số này không chứng minh đã loại mọi N+1 ở các nhánh khác.

Tiếp tục rà thấy danh sách lớp và người soạn khi đối soát hồ sơ lớp vẫn truy vấn theo hồ sơ. Đã gom theo năm học/lớp và lấy sổ người soạn theo lô, giữ union người soạn của toàn bộ hồ sơ chủ đề. Regression cùng dữ liệu 24 hồ sơ giảm từ 109 xuống 42 statement; nhóm 35 test PostgreSQL đạt tại `target/feature011-class-batch-green-2.log`. Cần full verify sau thay đổi cuối.
