# Analyze và tiến độ
- Domain/spec/contracts/data model/tasks đã đối chiếu với câu trả lời user. Quyết định triển khai trực tiếp học kỳ và lũy kế năm, không lấy cuối chủ đề gần nhất nữa.
- Review read-only tìm rủi ro đổi năm khi đang lưu; đã guard header và chỉ nhận response đúng scope/mount.
- Targeted ThemeEvaluationIntegrationTest + SemesterDomainResultTest: 11 tests, fail0/error0/skip0 với PostgreSQL sau source cuối; lưu/xác nhận snapshot, scope, kỳ cấu hình, đúng50%, thiếu/ngàyvắng, lineage/lưu sửa, năm lũy kế/lớp cũ.
- Chưa kết luận converge sạch; chờ full verify, kiểm browser và các ca FE. Không báo hoàn tất trong khi còn bước.
