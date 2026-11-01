# Feature Specification: Ba bảng mục tiêu cha–con
**Feature Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals`
**Created**: 2026-10-29
**Status**: Approved — user duyệt plan và yêu cầu vòng lặp Spec Kit, 29/10/2026.
**Input**: Tách lĩnh vực con, nhóm kết quả mong đợi, mục tiêu thành ba bảng cha–con; giữ cấp cha tùy chọn.

## User Scenarios & Testing
### User Story 1 — Giữ dữ liệu kế hoạch cũ (Priority: P1)
Tổ trưởng mở và tiếp tục soạn kế hoạch sau nâng cấp với đủ chữ, thứ tự, nội dung và chủ đề cũ.
**Independent Test**: Nâng cấp dữ liệu phiên bản cũ rồi đọc và lưu lại kế hoạch.
**Acceptance Scenarios**:
1. Given nhiều lĩnh vực con/nhóm/MT, When nâng cấp, Then đúng cha và giữ định danh từng dòng.
2. Given MT không có nhóm hoặc lĩnh vực con, When nâng cấp/lưu, Then không thêm tiêu đề giả, không mất MT.
### User Story 2 — Sửa và xem lại cây mục tiêu (Priority: P1)
Tổ trưởng thêm, bỏ, chuyển tiêu đề và mục tiêu mà không làm mất nội dung còn giữ lại.
**Independent Test**: Lưu, mở lại, kiểm tra quan hệ và số MT xuyên lĩnh vực.
**Acceptance Scenarios**:
1. Given nhóm/MT được chuyển, When lưu, Then đúng cha mới và giữ ID, nội dung/dấu tích.
2. Given ID dòng thuộc kế hoạch/loại khác hoặc ID lặp, When lưu, Then từ chối toàn bộ lần ghi.
### User Story 3 — Phiên bản và tài liệu lớp (Priority: P1)
Giáo viên tiếp tục dùng mục tiêu và học liệu; PHT tạo bản điều chỉnh mà không đổi bản ban hành.
**Independent Test**: Chép phiên bản, so sánh và dùng lại tham chiếu mục tiêu.
**Acceptance Scenarios**:
1. Given bản ban hành, When tạo phiên bản, Then chép đúng cha và lineage, bản cũ bất biến.
2. Given giáo án/học liệu đang tham chiếu MT, When nâng cấp, Then tham chiếu còn đúng và quyền xem vẫn kiểm tại server.

### Edge Cases
- Nhóm đứng trước lĩnh vực con đầu tiên; mục tiêu trực tiếp dưới lĩnh vực hoặc dưới lĩnh vực con.
- Xóa tiêu đề nhưng giữ/chuyển các MT trong cùng lần lưu; nhóm đổi lĩnh vực con.
- ID/lineage trên nhiều phiên bản; dữ liệu cũ có nội dung gắn tiêu đề phải bị báo lỗi khi nâng cấp.
- Tiêu đề rỗng vẫn theo hành vi lưu nháp cũ: bỏ qua, không tự tạo cấp cha.

## Requirements
### Functional Requirements
- **FR-001** (EDU-09): Lưu ba loại bằng ba bảng riêng và quan hệ cha–con bằng khóa ngoại; cấp cha tùy chọn.
- **FR-002** (EDU-09): Nâng cấp giữ 100% ID, lineage, chữ, thứ tự, số MT, nội dung và dấu tích hợp lệ.
- **FR-003** (EDU-09): Lưu/cập nhật/xóa/chuyển dòng phải toàn vẹn và không mất dữ liệu được giữ trong request.
- **FR-004** (EDU-09): Từ chối tham chiếu sai kế hoạch/lĩnh vực/loại, ID lặp và quan hệ nhóm/lĩnh vực con không khớp.
- **FR-005** (EDU-25/26): Sao chép và so sánh phiên bản giữ đúng lineage và ánh xạ ba cấp.
- **FR-006** (EDU-24/28): Giữ liên kết nguồn Bộ, MT trong giáo án và học liệu.
- **FR-007** (EDU-06/10/22): Giữ quyền, khóa sửa, revision, lưu tay và giao tiếp client hiện tại.
- **FR-008**: Tài liệu CSDL, sơ đồ và hướng dẫn kiểm chứng phải khớp source mới.
### Key Entities
Lĩnh vực con, nhóm kết quả mong đợi, mục tiêu, nội dung giáo dục, kế hoạch năm có phiên bản.
## Success Criteria
- **SC-001**: 100% dòng hợp lệ của bộ dữ liệu nâng cấp được giữ nguyên định danh và nội dung.
- **SC-002**: Các trường hợp thiếu cấp cha và chuyển nhóm lưu/mở lại thành công, không tạo tiêu đề giả.
- **SC-003**: Không có tham chiếu giáo án/học liệu hợp lệ nào mất sau nâng cấp; phiên bản cũ không đổi.
- **SC-004**: Mọi trường hợp tham chiếu sai được kiểm thử đều bị từ chối toàn bộ lần ghi.
## Clarifications
- 29/10: User chọn giữ cấp cha tùy chọn, sau đó nhắc lại đúng ba bảng cha–con.
- 29/10: User duyệt kế hoạch và yêu cầu triển khai theo vòng lặp spec.
