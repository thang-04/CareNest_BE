# Implementation Plan: Điều kiện gửi duyệt kế hoạch giáo dục

> **Quyết định user10/11/2026 — duyệt hồ sơ lớp:** Theo chuỗi HT cấu hình, không bắt buộc có bước TT; chỉ PHT duyệt cũng hợp lệ. Giữ cấp trên duyệt cấp dưới, chống tự duyệt, phạm vi phân công và REVIEW cấm sửa. Quyết định này thay yêu cầu TT bắt buộc trước PHT/HT trong tài liệu09/11; chưa phải bằng chứng triển khai/nghiệm thu.

**Branch**: `feature/G94-326-BE-FEAT-09-school-year-goals-backup` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Status**: Draft — thiết kế để review; chưa duyệt triển khai hay thay đổi DB.

## Summary

Kiểm điều kiện năm/tổ khi gửi tại backend và giao diện; lưu bền giới hạn năm; cho tổ trưởng thêm/sửa nội dung riêng, giữ nội dung khi đồng bộ năm và báo rà soát xung đột. Áp dụng EDU-70: duyệt theo thứ bậc, một người được chỉ định soạn và bỏ lease 90/20; giữ lưu nháp, nguồn năm và dữ liệu giáo viên.

## Technical Context

- Java 21, Spring Boot 4.1.1, PostgreSQL 17, JPA/Flyway; React 18, Vite, JavaScript; dùng thư viện hiện có.
- Tác động: education-plan backend, FE Web, hợp đồng request/error hiện có và consumer giáo viên. Mobile phải được rà tác động hợp đồng, không tự sửa repo APP.
- Kiểm chứng bằng Maven/JUnit/integration DB thử, Node test, ESLint và Vite build; không chạy harness/setup wrappers theo AGENTS/constitution. Không tự mở browser FE theo AGENTS.
- Không đặt SLA/khối lượng mới chưa có nguồn. Validation nên dùng truy vấn theo tập và tránh N+1; kiểm bằng tests và dữ liệu đại diện.
- Không sửa migration đã commit; schema mới dùng migration tiếp theo sau khi kiểm số trống. Backup và rehearsal DB thử trước mọi áp dụng; không tự sửa DB thật.

## Constitution Check

- **Nghiệp vụ**: FR-001–015 theo [spec](spec.md); EDU-09/14/22/23/39–41/52/55/56/57/62/65–71 và nguồn ngày 09/11. EDU-65–71 là yêu cầu user trực tiếp, không nâng thành xác nhận trường. EDU-70 thay phần mâu thuẫn về duyệt và lease của EDU-02/03/10/54; EDU-57 cấm mọi cách xóa tổ, kể cả xóa mềm, theo user làm rõ.
- **Bảo mật**: server kiểm quyền/scope, ownership, revision, trạng thái; không dùng FE làm quyền. Giữ dữ liệu trẻ/tài khoản ra khỏi fixture và log.
- **Bảo toàn dữ liệu**: lưu draft; transaction atomic; snapshot cũ, ID/tick/nội dung tổ, bản tổ ban hành và dữ liệu lớp được bảo toàn. Khôi phục snapshot mất chỉ khi có bằng chứng, giữ trạng thái; không mặc định suy cấu hình lịch sử.
- **Quy trình**: lane L do migration, API và FE/BE; cần tasks/analyze và user duyệt plan trước code, dừng review theo phase. Phê duyệt plan này không tự cho phép chạy migration trên DB thật, commit/push/deploy.
- **Sau thiết kế**: không có vi phạm được chấp nhận; thay đổi DTO/ApiCode nếu cần phải qua quy trình duyệt contract và kiểm FE/APP. Có sẵn plan quản trị cùng chủ đề, cập nhật delta tại `docs/plans/active/2026-11-07-school-year-team-plans.md`.

## Project Structure

```text
CareNest_BE/src/main/java/com/carenest/{entity,repository,service,controller,dto}/eduplan/
CareNest_BE/src/main/resources/db/migration/
CareNest_BE/src/test/java/com/carenest/service/eduplan/impl/
CareNest_FE/src/{pages,components,hooks,models,services}/education-plan/
CareNest_FE/tests/education-plan/
CareNest_BE/specs/016-education-submit-validation/{research.md,data-model.md,contracts/,quickstart.md}
```

**Structure Decision**: Backend là nguồn quyết định quyền/validation; FE phản ánh và kiểm sớm. Dùng mô hình, lịch tuần, service và hook hiện tại; không thêm dependency.

## Thiết kế và thứ tự thực hiện

1. **Phân công, thứ bậc và giới hạn bền vững** — xác định chính xác thay đổi endpoint/DTO/permission và tương thích client khi bỏ lease; trước code trình duyệt contract và rà FE/APP. Kiểm cạnh tranh bằng revision/chống ghi đè, không phụ thuộc lease; đảm bảo duy nhất người được phân công và thứ bậc EDU-70. Migration mới thêm snapshot annual, map entity; tạo mới chụp cấu hình; phiên bản điều chỉnh chụp cấu hình hiện tại lúc tạo; bản cũ giữ snapshot. Kiểm đủ ngày riêng khỏi mode BOUNDED; kiểm khoảng từ snapshot ở submit và approve; nháp cho phép thiếu/sai. Kiểm 9 chủ đề, ít nhất một chuỗi nhóm kết quả/STT/mục tiêu/nội dung, từng mục tiêu có nội dung trước REVIEW; dùng transaction và quy tắc lịch hiện có.
2. **Nội dung tổ** — nới `InheritedCurriculumGuard` cho thêm/sửa content thuộc đúng tổ/mục tiêu, giữ guard cấu trúc/nhãn/mục tiêu, chặn ID ngoại và xóa chưa được phép. Sửa save/read/mapper, `hasInvalidReferences`, `syncTree`: phân biệt content nguồn và riêng; đối chiếu nguồn cũ–tổ–nguồn mới; cùng sửa thì giữ bản tổ và đánh dấu rà soát. Không mất tick/ID/nội dung đang soạn; xử lý tham chiếu không còn hợp lệ qua rà soát, không xóa ngầm. Kiểm giáo viên dùng bản tổ được phép đúng lớp/tuổi.
3. **Phân bổ và FE** — backend từ chối gửi tổ khi có mục tiêu không có nội dung tick chủ đề hợp lệ. FE tách quyền sửa content khỏi cây mục tiêu, đồng bộ mock, mapper và phục hồi nháp khi đổi nguồn. Cùng quy tắc ở form và chi tiết; lỗi gần ô/tổng hợp, trường bắt buộc có * đỏ kèm mô tả truy cập; giữ lưu nháp.
4. **Kiểm chứng** — tests ở [quickstart](quickstart.md), API trực tiếp/FE, save-reload và luồng cập nhật nguồn/giáo viên. Tạo tasks, analyze chéo artifacts, rà contract/APP; không tự triển khai trong lệnh plan.

## Rủi ro và rollback

- Snapshot đã mất: chỉ khôi phục có bằng chứng và giữ trạng thái; hồ sơ mất snapshot hoặc chưa phân loại được legacy thật phải chặn gửi và báo kiểm tra/khôi phục theo EDU-71, không âm thầm mặc định legacy. Legacy thật có bằng chứng giữ ngoại lệ cũ. Với plan345 đang REVIEW, không tự sửa, trả lại hay phê duyệt.
- Nguồn và tổ cùng sửa: giữ bản tổ, báo rà soát theo user; giữ bản tổ APPROVED cũ cho lớp đến khi cập nhật được duyệt. Khôi phục nháp sau conflict không thay nội dung tổ bằng nguồn mới.
- Migration/contract: test DB scratch và backup trước; rollback ứng dụng không drop cột làm mất snapshot. Nếu cần đổi API/errors, trình thay đổi cùng consumer và kiểm tương thích trước code.
- Duyệt/lease thay đổi theo EDU-70: cần ma trận vai trò/phân công, session cũ và đồng thời; không xóa cơ chế chống ghi đè khi bỏ lease. Chưa quyết định xóa endpoint cũ hay schema request trước khi kiểm consumer và được duyệt contract.

## Artifacts

[Research](research.md) · [Data model](data-model.md) · [Contract](contracts/submit-validation.md) · [Quickstart](quickstart.md) · [Tasks](tasks.md).
