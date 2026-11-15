# Domain spec — Lọc phân cấp và gợi ý từ chương trình Bộ

Nguồn: user và ba câu trả lời 08/11/2026; EDU-61 PROPOSED, chưa xác nhận team/trường. Tham chiếu [spec](spec.md), [mẫu 14 mục](../../docs/business/DOMAIN_SPEC_TEMPLATE.md).

## 1. Domain Overview

User báo bộ lọc lĩnh vực con không giới hạn đúng mục tiêu đích. Mục tiêu: chọn đúng nhánh và giảm nhập lại bằng gợi ý theo cha; nguyên nhân lỗi chưa tái hiện.

## 2. Scope

Trong phạm vi: lọc nguồn/đích, loại lựa chọn sai, gợi ý tại ba ô nhập. Ngoài phạm vi: thay văn bản Bộ, ghép mục C với nhóm, AI, đổi quyền hoặc tự sửa dữ liệu cũ; lượt này chỉ đặc tả BE.

## 3. Actors & Roles

Người có quyền soạn theo cấu hình/phân công và trạng thái hồ sơ dùng gợi ý. Người chỉ xem không được thêm; soạn tổ không được sửa cây năm kế thừa. Server giữ kiểm quyền/phạm vi hiện có.

## 4. Domain Concepts

Lĩnh vực, lĩnh vực con, nhóm kết quả mong đợi, mục tiêu, nội dung và ngữ cảnh lựa chọn như Key Entities của spec. Nhóm tuổi lấy từ kế hoạch.

## 5. Domain Relationships

Lĩnh vực → lĩnh vực con → nhóm → mục tiêu → nội dung. Cấp con/nhóm có thể vắng trong dữ liệu kế hoạch. Mục D có nhóm–mục tiêu–nội dung; mục C độc lập chưa có quan hệ nhóm. Không ghép nguồn chỉ theo tên.

## 6. User Requirements

US1/FR001–005 chọn đúng đích; US2/FR006–008/010 bấm mở/gõ lọc/chọn gợi ý; US3/FR009/011–012 giữ dữ liệu/quyền khi rỗng, lỗi hoặc thiếu cấp.

## 7. Business Rules

EDU-09 phân cấp; EDU-22 lưu tay; EDU-28 nguồn Bộ; EDU-48 được EDU-53 thay phần cấu trúc lẫn cấp; EDU-54 quyền/trạng thái; EDU-55 cây năm cố định tại tổ; EDU-60 phạm vi xem; EDU-61 yêu cầu mới. Giữ các rule đã xác nhận.

## 8. Business Processes

[B] Người soạn xác định nhánh cần dùng. [S] Mở bản được sửa, chọn cha; hệ thống lọc và bỏ lựa chọn sai. [S] Bấm ô, gõ lọc, chọn gợi ý, chọn đích và thêm rồi lưu tay. Ngoại lệ: thiếu cha hướng dẫn; nhánh rỗng báo rỗng; tải lỗi cho thử lại, giữ bản soạn. Kết quả: nội dung đúng nhánh/đích.

## 9. State / Lifecycle

Chưa đủ ngữ cảnh → đủ để gợi ý → chọn hợp lệ → đã thêm → lưu tay. Đổi cha loại con không hợp lệ, giữ con còn hợp lệ. Tải lỗi/đang tải không đổi vòng đời hồ sơ hay dữ liệu đã thêm.

## 10. Business Constraints

Giữ nhóm tuổi, phân công, quyền, lease/trạng thái, nguồn và bỏ trùng. Một trường/hai campus. Bộ lọc không là thao tác xóa kế hoạch; không tạo cấp giả.

## 11. Non-Functional Requirements

100% gợi ý đúng nhánh; không mất dữ liệu khi đổi bộ lọc/lỗi tải; mở gợi ý một lần bấm khi đủ cha; dữ liệu tải muộn không đổi ngữ cảnh. Không đặt SLA chưa chốt hoặc dùng dữ liệu thật/secret.

## 12. Acceptance Criteria

- AC-01 → FR001–003/SC001: Given A/A1 và B/B1, When lọc A1, Then chỉ chọn được mục tiêu A1 đúng tuổi (US1.1–2).
- AC-02 → FR004–005/SC001–002: Given đích G1/A1 và nội dung đã thêm, When đổi A2, Then bỏ G1, giữ dữ liệu đã thêm, chặn thêm thiếu đích (US1.3–5).
- AC-03 → FR006/SC003: Given lĩnh vực/con, When bấm ô nhóm rồi gõ, Then mở ngay và lọc nhóm đúng nhánh (US2.1).
- AC-04 → FR007/SC003–004: Given R1, When bấm ô mục tiêu, Then chỉ có mục tiêu R1 (US2.2).
- AC-05 → FR008/SC003–004: Given R1, When bấm ô nội dung, Then dùng nội dung kèm mục tiêu R1, không gắn mục C ngầm (US2.3).
- AC-06 → FR009–010/SC002: Given chưa chọn gợi ý hoặc tải lỗi/muộn, When nhập/chuyển/thử lại, Then không sửa bảng hoặc hồi sinh lựa chọn cũ (US2.4–5/US3.1–3).
- AC-07 → FR011: Given chỉ xem hoặc cây tổ kế thừa, When mở thao tác, Then không cho sửa nguồn (US3.4).
- AC-08 → FR012/SC001–002: Given thiếu cấp hoặc tên trùng, When lọc, Then dùng quan hệ thực, không cấp giả/mất dữ liệu (Edge Cases).

## 13. Domain Terminology

“Lĩnh vực to” = lĩnh vực; “lvc” = lĩnh vực con; “kết quả mong đợi” = nhóm kết quả mong đợi; “ấn nhập” = bấm/focus mở gợi ý rồi gõ lọc. Đích là mục tiêu bản soạn nhận nội dung, khác mục tiêu nguồn.

## 14. Assumptions & Open Questions

User chốt Q1 bỏ lựa chọn sai/giữ dữ liệu, Q2 nội dung kèm mục tiêu trong nhóm, Q3 bấm mở rồi gõ lọc ngày 08/11/2026. Không còn câu hỏi nghiệp vụ chặn planning. Plan cần khảo sát Web/Mobile, cách xác định ngữ cảnh nguồn cho ô nhập và nhu cầu đổi contract. User đã cấp quyền sửa giáo dục FE014 ngày08/11/2026; đã duyệt vòng implement→converge plan014 ngày08/11/2026; chưa duyệt delta sửa catalog bằng migration hoặc sửa APP/API/dependency.
