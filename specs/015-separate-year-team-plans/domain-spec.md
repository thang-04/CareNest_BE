# Domain spec — Phân biệt kế hoạch năm học và kế hoạch tổ

Nguồn hiện hành: [bộ 12 quyết định ghi nhận09/11/2026](../../docs/business/education-plan-confirmation-2026-11-09.md), CONFIRMED trong đúng phạm vi user xác nhận đã được giáo viên phía trường xác nhận; không suy ngày/tên giáo viên hoặc nâng mức các chi tiết ngoài bộ câu hỏi. Đối chiếu PROJECT_CONTEXT, module card, flow và BUSINESS_RULES (EDU-01/07/25/26/54/55/57/60/62). Theo [feature-spec-loop](../../.ai/workflows/feature-spec-loop.md). Lệnh user “fix” cho phép sửa artifact, chưa triển khai.

## 1. Domain Overview

Kế hoạch năm gửi HT duyệt đang bị user báo hiện tên loại là kế hoạch tổ. Hai chức năng cần rõ tên, trách nhiệm, nguồn và dữ liệu. Năm xây nội dung giáo dục; tổ chỉ phân bổ nội dung nguồn vào chủ đề, mục tiêu tương ứng đi theo.

## 2. Scope

Trong phạm vi: nhãn chờ duyệt, trang lập tổ trực tiếp, chọn nguồn năm đã ban hành, tên riêng, danh sách tổ, giao diện phiên bản, rà soát mapping khi nguồn đổi và tách lưu trữ năm/tổ thành hai bảng nghiệp vụ.

Feature đích gồm BE + FE; task triển khai FE phải có quyền sửa repo client riêng trước khi thực hiện. Ngoài phạm vi lượt sửa artifact này: code, migration runtime, deploy, sửa FE/APP. Không đổi chuỗi duyệt cấu hình được, giới hạn thời lượng, nghiệp vụ giáo án hay danh mục Bộ. Không tách thành hai hệ quản trị/database độc lập.

## 3. Actors & Roles

- PHT soạn năm, HT duyệt năm theo mặc định EDU-55; cấu hình theo EDU-54.
- Tổ trưởng soạn tổ trong nhóm tuổi được phân công; PHT duyệt tổ theo mặc định.
- Tổ trưởng được duyệt/trả lại năm/tổ nếu HT cấu hình, đúng tuổi/phân công/lượt và không tham gia soạn; không tự duyệt hoặc cấp quyền chỉ từ role.
- GV tiếp tục dùng tổ đã ban hành làm nguồn tài liệu lớp.
- Server kiểm quyền/phân công/phạm vi, không chỉ ẩn nút.
- View-scope (FR-015/EDU-60): tổ trưởng và GV (không kiêm nhiệm quản lý) chỉ xem kế hoạch năm học + kế hoạch tổ của đúng nhóm tuổi được phân công hiệu lực cho tài khoản đăng nhập; không xem mọi nhóm tuổi. HT/PHT có phân công quản lý hiệu lực được xem mọi nhóm tuổi. Kiểm tại server, thay đổi phân công ⇒ thay đổi scope, không cache.

## 4. Domain Concepts

- Kế hoạch giáo dục: tên nhóm chức năng, không phải bản ghi thứ ba tự phát sinh.
- Kế hoạch năm học: nội dung giáo dục nguồn theo năm học và nhóm tuổi; có tên và phiên bản.
- Kế hoạch tổ: tên riêng, liên kết một bản năm hợp lệ; phân bổ từng nội dung vào chủ đề, không sửa cây nguồn.
- Năm học: danh mục thời gian, ví dụ 2026–2027; khác tên hai loại kế hoạch.
- Bản cập nhật tổ: bản để rà soát/gửi duyệt khi nguồn đổi; vẫn lưu lịch sử nội bộ dù không trình bày như phiên bản ở màn tổ.

## 5. Domain Relationships

Mỗi kế hoạch thuộc một năm học và một nhóm tuổi. Một bản tổ mới liên kết đúng một bản năm đã ban hành hiện hành, cùng năm/tuổi và trong quyền. Một bản năm có thể được nhiều bản lịch sử tổ tham chiếu; không mở thêm nhiều chuỗi tổ trong cùng năm/tuổi. Hai bản tổ/năm có tên độc lập. Lớp tiếp tục tham chiếu bản tổ ban hành tương ứng.

## 6. User Requirements

Bấm Lập kế hoạch tổ → vào trang soạn chính → nhập tên riêng, chọn nguồn năm hợp lệ → hiển thị nguồn chỉ đọc → tick từng nội dung vào chủ đề → lưu/gửi duyệt. Không modal; không tự tạo tổ lần đầu khi năm ban hành. Danh sách tổ có tên tổ, nhóm tuổi, ngày gửi, người soạn, trạng thái; hành động theo quyền.

## 7. Business Rules

Giữ EDU-54/55/60 về workflow, nguồn và quyền; EDU-25/26/36 về lịch sử và bảo vệ bản ban hành. Delta EDU-62 trong BUSINESS_RULES.md áp dụng giao diện phiên bản chỉ ở năm; lưu lịch sử tổ nội bộ; tách bảng năm/tổ. Mâu thuẫn với mô hình chung bảng và hiển thị phiên bản tổ ở feature012 được giải quyết bằng chỉ đạo mới nhất của user, không viết lại lịch sử quyết định cũ.

EDU-07/57: không ai xóa tổ ở bất kỳ trạng thái nào, giữ quyền xóa năm riêng. EDU-55: server giữ cây nguồn bất biến kể cả nhập file, chỉ cho phân bổ từng nội dung. EDU-54: TT được duyệt năm/tổ theo cấu hình/tuổi/lượt, loại cả người tạo và người tham gia soạn. EDU-62: cập nhật bản tổ đang mở trước; chỉ sinh bản cập nhật nếu chưa có bản mở và đã có tổ ban hành; không tạo chuỗi tổ lần đầu.

## 8. Business Processes

1. [B] Người soạn chọn lập tổ. [S] Mở trang soạn, không modal.
2. [B] Nhập tên tổ và chọn đúng một nguồn. [S] Chỉ cho nguồn ban hành hiện hành cùng năm/tuổi, trong quyền; hiện dữ liệu kế thừa.
3. [B] Tick nội dung vào chủ đề, lưu/gửi. [S] Lưu tên tổ độc lập, kiểm quyền và giữ workflow tổ.
4. [B] HT duyệt cuối phiên bản năm mới. [S] Tổ liên quan có nhu cầu cập nhật; bản tổ ban hành cũ vẫn cấp nguồn cho lớp.
5. [B] Người soạn mở cập nhật tổ, rà soát phần đỏ, tick lại/xác nhận mapping, gửi PHT. [S] Chỉ bản cập nhật được duyệt cuối mới thành nguồn tổ hiện hành; hồ sơ lớp cũ không bị ghi đè.

## 9. State / Lifecycle

Hai loại giữ Nháp → Chờ duyệt → Ban hành; trả lại theo workflow hiện có. Nháp năm mới không đổi tổ đang dùng. Tổ có bản cập nhật nháp/chờ duyệt song song bản ban hành cũ; không sửa trực tiếp bản cũ. Ẩn giao diện phiên bản tổ không xóa vòng đời/lịch sử nội bộ.

## 10. Business Constraints

Không thêm/sửa/xóa cây nguồn ở tổ. Không cho nguồn nháp, chờ duyệt, khác năm/tuổi hoặc ngoài quyền. Giữ một chuỗi mỗi loại/năm/tuổi và tối đa một bản chưa ban hành. Không đổi tên nguồn khi đổi tên tổ. Không xóa lịch sử, dấu tích hoặc liên kết lớp khi tách bảng. Legacy không nguồn giữ nguyên theo FR-011, không suy nguồn năm theo tên/tuổi để đưa vào cập nhật tự động. Phiên soạn cũ khi nguồn chuyển phải bị từ chối ghi với cơ chế revision/session, giữ phần nhập để đối chiếu; không ghi đè mapping mới.

## 11. Non-Functional Requirements

Toàn bộ ca trái quyền phải bị server từ chối. Mọi mục cần rà soát có dấu hiệu kèm giải thích chữ, không chỉ màu đỏ. Lưu/mở lại giữ đủ mapping và trạng thái rà soát. Không dùng dữ liệu trẻ thật trong kiểm chứng.

## 12. Acceptance Criteria

AC-01..AC-06 tương ứng US1/US2 trong spec: đúng loại/nơi duyệt; không modal; chỉ một nguồn hợp lệ; tên độc lập; đúng cột; không nguồn thì không tạo được tổ hợp lệ.

AC-07..AC-10 tương ứng US3: ẩn bảng/nút phiên bản tổ; năm nháp không ảnh hưởng tổ; năm ban hành mới dẫn tới rà soát; bản cũ và lớp giữ nguyên tới khi bản cập nhật được duyệt.

AC-13..AC-14 tương ứng US3.6/7 và FR-013/014: tổ đang làm chuyển nguồn mới nhất, bản chờ về nháp, giữ phần đã nhập và rà soát lại; chủ đề đã kết thúc không đỏ.

AC-11..AC-12 tương ứng US4: hai tập bản ghi năm/tổ có lưu trữ riêng và quan hệ nguồn rõ; bảo toàn lịch sử/liên kết khi chuyển đổi, gồm tổ legacy không có nguồn năm.

AC-15 = US1.5/FR-017 (TT duyệt/trả lại theo cấu hình); AC-16 = US2.7/FR-004 (chặn sửa cây qua API/import); AC-17 = US4.3/FR-016 (cấm mọi người xóa tổ); AC-18 = US3.8/FR-012/013 (một bản mở, nhiều lần chuyển nguồn); AC-19 = US3.9/FR-009/014 (resolve hợp lệ, baseline/tiến độ bền vững). Nhánh nhiều tuần/phủ đủ tuần học thuộc EDU-14/17/56 và feature012, chỉ regression liên kết lớp khi tách bảng, không mở rộng nghiệp vụ giáo án trong feature015.

## 13. Domain Terminology

Tên loại hồ sơ ≠ tên riêng hồ sơ ≠ tên năm học. “Link năm” là nguồn nội dung; “bản trước” là quan hệ lịch sử cùng loại. Tick mapping thực hiện ở nội dung như EDU-55, không tự tick cả mục tiêu. “Tách DB” ở đặc tả này là tách bảng nghiệp vụ trong PostgreSQL hiện có.

## 14. Assumptions & Open Questions

- Chỉ đạo cuối thay đề xuất tự hiển thị tổ/không nút lập và thay đề xuất modal; các quyết định về ban hành nguồn, rà soát và giữ lịch sử vẫn giữ.
- Chọn nguồn dùng quy tắc hiện hành: bản ban hành hiện hành; không mở quyền lấy bản đã bị thay thế. Giữ cơ chế chọn/lưu hiện có, không thêm tự lưu.
- Không yêu cầu tên phải khác nhau bằng ràng buộc duy nhất; tên là trường riêng, không sao chép/đồng bộ bắt buộc. Không suy diễn thêm loại hồ sơ “kế hoạch giáo dục”.
- Q1 đã chốt, user09/11/2026: khi năm mới ban hành, tổ đang nháp/chờ chuyển sang nguồn mới nhất, giữ phần đã nhập để rà soát; bản đang chờ về nháp và gửi duyệt lại. Bản tổ đã ban hành vẫn giữ nguyên làm nguồn lớp.
- Q2 đã chốt, user09/11/2026: so với bản năm tổ đang dùng trước chuyển nguồn; chủ đề đã kết thúc không đỏ như nhánh/giáo án hiện có.
- Không còn câu hỏi nghiệp vụ đang mở trong đặc tả này. Tên bảng vật lý, cách phân tách bảng con, cutover/backfill và cơ chế lưu tiến độ rà soát thuộc thiết kế plan trước code.
- Làn L: cần plan được duyệt; API/dependency/migration giữ nguyên quyền phê duyệt. Đặc tả không đồng nghĩa cho phép sửa repo FE/APP.
