---
id: BUG-261106-edu-media-contributor
type: bug
modules: [education-plan]
rules: [EDU-54]
status: fixed
date: 2026-11-06
keywords: [học liệu, coauthor, contributor, phân quyền duyệt]
---

Tải lên, gắn lại hoặc gỡ học liệu thực sự thay đổi hồ sơ nhưng không ghi actor vào sổ người soạn; sau khi đổi cấu hình hoặc phân công, actor có thể không bị loại khỏi lượt duyệt. Regression PostgreSQL RED tại `target/feature011-media-author-red.log` tái hiện contributor thiếu. Đã ghi actor trong cùng transaction khi có thay đổi và không ghi actor cho retry/no-op; test đổi policy, đổi phân công và kiểm không được tự duyệt đạt tại `target/feature011-media-author-policy-green.log`. Cần full verify sau sửa cuối.
