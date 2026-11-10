# Implementation Plan: Nhập file chủ đề nhánh và .doc mục tiêu

**Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals` | **Date**: 2026-11-01 | **Spec**: [spec.md](spec.md)
**Status**: Hoàn thành; [plan làn L và Progress log](../../docs/plans/completed/2026-11-01-branch-plan-file-import.md).

## Summary

Thêm hỗ trợ Word .doc nhị phân trong GoalFileGrid dùng chung, giữ cách đọc định dạng khác và merge mục tiêu bước 2.
Nhập nhánh có endpoint preview kiểm scope/lịch tại server, trả tên đã ghép vào tuần hệ thống; FE xác nhận thay tên vào bản nháp rồi lưu qua API đang có.
Luôn hiện Lưu/gửi ở trang nhánh, vô hiệu hóa kèm lý do, giữ chuỗi EDU-18.

## Technical Context

- Language/Version: Java 21, Spring Boot 4.1.1; Web React 18 + JavaScript/JSX theo repo hiện tại.
- Primary Dependencies: thêm `org.apache.poi:poi-scratchpad:6.0.0` chỉ cho OLE2/HWPF, user duyệt thêm thư viện; bộ đọc JDK docx/xlsx/HTML/CSV giữ nguyên.
- Storage: PostgreSQL 17, JSONB tài liệu lớp đang có; không bảng/cột/migration mới.
- Testing: JUnit/MockMvc/Testcontainers/ArchUnit/snapshot/Spotless; node:test và Playwright Web.
- Platform/Project Type: backend REST Linux + trình duyệt Web; không phụ thuộc Word/LibreOffice ở runtime.
- Performance: giới hạn 2 MB/2.000 dòng; kiểm row/cell/text trong quá trình đọc, không nới POI global allocation limit.
- Constraints: quyền và lịch kiểm server; không log/lưu file, không dùng dữ liệu người thật; không đổi chuỗi duyệt hay merge mục tiêu.
- Scale/Scope: một module education-plan, BE + FE; chia ba phase vì >8 file và đổi dependency/API.

## Constitution Check

- Trước thiết kế: đã đối chiếu AGENTS.md, domain-spec 14 mục và constitution CareNest 1.0.0; nguồn stack được user xác nhận, không theo ví dụ MySQL.
- Sau thiết kế: giữ lớp/service/repository, auth và scope; không schema mới; test theo AC; không sửa Mobile hoặc tự commit/push.
- User đã duyệt plan/endpoint và sau phase 1 cho phép hoàn thành liên tục các phase còn lại; review độc lập và full verify vẫn thực hiện, không chờ thêm xác nhận giữa phase.

## Project Structure

Tài liệu tại thư mục feature: domain-spec.md, spec.md, research.md, data-model.md, contracts/file-import.md, quickstart.md, tasks.md, analysis.md.
Plan quản trị tại docs/plans/completed/2026-11-01-branch-plan-file-import.md.

Source BE dự kiến: `service/eduplan/impl/GoalFileGrid.java`, `BranchFileTable.java` mới; `EduClassDocService.java`/Impl và controller hiện có; DTO mới `BranchFilePreviewResponse.java`; ApiCode/snapshot/pom và test tương ứng.
Source Web dự kiến: BranchPlanPage, modal nhập nhánh, service facade/API/mock, helper áp dụng patches và ReviewActions; ImportGoalFileModal cập nhật lời hướng dẫn .doc; unit/E2E hiện có mở rộng.
Không tách thêm một service scope/policy/wrapper chỉ để gọi lại EduClassDocServiceImpl.

## Phase 0 — Research

[research.md](research.md): định dạng OLE2, POI version, scope, lịch, mã lỗi, pending AI và các phương án loại bỏ.

## Phase 1 — Design & Contracts

[data-model.md](data-model.md): không migration, preview chỉ transient.
[contracts/file-import.md](contracts/file-import.md): endpoint mới, error semantics, consumer cụ thể; endpoint/DTO mục tiêu giữ nguyên.
[quickstart.md](quickstart.md): kiểm chứng thủ công/tự động và tiêu chí không ghi dữ liệu.

## Implementation Strategy

Bổ sung 02/11/2026 (làn M, Web cùng module): tái dùng docShell/downloadDoc trong classPlanDoc cho mẫu hai cột, nhận danh sách tuần từ trang thay vì tính lịch mới; modal dùng cùng size/hàng nút/cảnh báo với nhập mục tiêu. Không đổi contract, DB hoặc dependency. Tasks T020–T022 append, test template/handler/render trước code rồi verify FE; cập nhật knowledge và hội tụ, không cần plan L mới cho phần UI này.

Phase triển khai 1: bộ đọc chung + mục tiêu .doc; phase 2: BE preview nhánh/lịch/scope; phase 3: Web/modal/nút và E2E.
Sau mỗi phase verify và ghi Progress log; user đã cho phép hoàn thành toàn bộ không dừng giữa phase. Trước code chạy analyze; sau implement chạy converge, chỉ append task thiếu rồi lặp đến sạch. Vòng 1 append T019, vòng 2 sạch; xem quickstart.

## Complexity Tracking

Dependency mới được user yêu cầu/duyệt để đọc .doc thật; không tự viết parser OLE2/FIB/Piece Table, không thêm dịch vụ chuyển đổi.
Không có vi phạm constitution được miễn. Dependency tree phải kiểm tương thích BOM trước code/test, không đổi dependency khác để ép build qua.
