---
id: BUG-261029-assessment-version-lineage
type: bug
modules: [education-plan, learning-observation]
rules: [EDU-26]
status: fixed
date: 2026-10-29
keywords: [merge, lineage, phiên bản, tổng quan]
---
# Ghép phiên bản giáo án với đánh giá đã lưu

EDU-26/spec004 FR-028 yêu cầu dùng giáo án ngày hiện tại và chuyển mức chấm theo lineage. WIP đánh giá mới chưa nối bước này; tổng quan cũng đếm ID cũ kể cả mục tiêu đã bỏ.

Test PostgreSQL approvedNewVersionProjectsSavedGradesByLineageWithoutRewritingOnRead fail trước sửa tại ID cũ, sau đó thêm assertion tổng quan tái hiện số Chưa đạt còn thừa. Sửa bằng AssessmentResultProjection dùng chung daily/overview, tìm lineage qua service eduplan một lần cho tập ID; giữ riêng ngày và hoạt động, không mutate entity khi GET. Mock FE cùng phép chiếu. Bộ liên quan BE29/29 và FE65/65 đạt; thêm unit test nhiều ngày/hoạt động để khóa mapping theo từng ngày. Full verify xem target/verify.log và plan merge.

Migration: giữ Tiền V27–V31, đổi hai migration WIP chưa commit sang V32/V33, SQL nguyên bytes. Maven target còn resource tên cũ gây duplicate V27; clean test đã loại resource cũ. Không sửa Flyway history hoặc áp DB người dùng.
