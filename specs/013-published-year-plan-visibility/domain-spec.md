# Domain spec — Quyền xem năm học và danh sách mục tiêu trong kế hoạch tổ

Nguồn: user yêu cầu `$speckit-specify` ngày 08/11/2026. Đây là delta của [feature012](../012-school-year-team-plans/spec.md), không thay thiết kế cấp năm/tổ đã có. Quyết định user đã nêu được ghi nhận; mức nguồn PROPOSED vì chưa có xác nhận từ trường/team.

## 1. Domain Overview

- Người lập kế hoạch tổ cần căn cứ năm học đã được duyệt cuối và ban hành, tránh dùng nội dung còn đang soạn/duyệt.
- Phần mục tiêu năm học trong kế hoạch tổ cần liệt kê trọn vẹn để đọc và phân bổ nội dung thuận tiện, không cuộn bên trong card.
- Đối tượng: người có quyền tạo kế hoạch tổ theo cấu hình hiệu lực; mặc định là tổ trưởng.

## 2. Scope

- Trong phạm vi: quyền xem kế hoạch năm học của người lập kế hoạch tổ; danh sách, chi tiết và nguồn năm trong luồng lập tổ; cách trình bày toàn bộ cây mục tiêu/nội dung nguồn của kế hoạch tổ.
- Ngoài phạm vi: thay luồng duyệt, cấu hình vai trò, quyền soạn/duyệt năm độc lập, quyền sửa cây mục tiêu, lịch, nhãn, dữ liệu cũ và phiên bản. Lượt specify chỉ tạo tài liệu BE, không sửa source BE/FE/APP.
- Tác động triển khai là làn L vì có phân quyền và Web; cần plan được duyệt trước code.

## 3. Actors & Roles

- Người tạo kế hoạch tổ: quyền lập tổ theo cấu hình hiệu lực, chỉ nhận quyền xem năm đã ban hành từ tư cách này.
- Người soạn/duyệt kế hoạch năm: giữ quyền riêng theo EDU-54/55 để hoàn thành công việc trước ban hành.
- Người kiêm nhiệm (user chốt Q1=A, 08/11/2026): được xem năm chưa ban hành để thực hiện quyền soạn/duyệt năm độc lập hợp lệ theo cấu hình và phân công; đồng thời có quyền lập tổ không làm mất quyền năm này. Giới hạn của quyền lập tổ không thay phạm vi riêng của quyền năm độc lập.
- Hệ thống kiểm tra ở server: năm đã ban hành VÀ đúng phạm vi tuổi của chính tài khoản đăng nhập. User làm rõ sau analyze08/11/2026: HT/PHT có phân công hiệu lực xem mọi nhóm tuổi; tổ trưởng/GV chỉ đúng nhóm tuổi phân công. Phân công quản lý hiệu lực không đòi hỏi giao từng nhóm tuổi; vai trò danh nghĩa không có phân công không cấp scope. Không có/đã hết/bị thu hồi phân công thì quyền lập tổ không cấp quyền xem nguồn năm. Giữ kiểm tra campus/class liên quan; không tin nhóm tuổi hoặc tài khoản người dùng tự truyền.

## 4. Domain Concepts

- Kế hoạch năm học: định hướng theo năm học và nhóm tuổi, cấp SCHOOL_YEAR.
- Kế hoạch tổ: kế hoạch triển khai cấp TEAM, kế thừa nguồn năm đã ban hành.
- Ban hành: duyệt xong bước cuối, trạng thái APPROVED theo EDU-54; REVIEW vẫn là đang duyệt.
- Mục tiêu năm học trong tổ: toàn bộ mục tiêu/nội dung kế thừa cùng cấu trúc phân nhóm và nhãn nguồn.

## 5. Domain Relationships

- Một kế hoạch tổ tham chiếu phiên bản nguồn năm theo cơ chế feature012; delta không đổi quan hệ hay cách nâng phiên bản.
- Cây nguồn gồm lĩnh vực, lĩnh vực con, nhóm kết quả mong đợi, mục tiêu và nội dung; giữ liên kết phân bổ từng nội dung vào chủ đề tổ.
- Quyền xem nguồn gắn với trạng thái ban hành và phạm vi người dùng, không phát sinh quyền sở hữu nguồn.

## 6. User Requirements

- UR-01: người lập tổ chỉ thấy/mở được kế hoạch năm đã ban hành trong phạm vi được phép.
- UR-02: khi chưa có nguồn năm hợp lệ, hiểu được lý do chưa thể dùng nguồn để lập tổ mà không bị lộ nội dung chưa ban hành.
- UR-03: đọc đầy đủ mục tiêu/nội dung nguồn trong kế hoạch tổ bằng luồng cuộn trang thông thường, không cuộn trong card.
- Yêu cầu chi tiết và ca nghiệm thu tại [spec.md](spec.md).

## 7. Business Rules

- EDU-54 (CONFIRMED): ba trạng thái DRAFT/REVIEW/APPROVED, duyệt cuối thì ban hành, quyền soạn/duyệt theo cấu hình.
- EDU-55 (PROPOSED, lựa chọn user đã chốt): tổ dùng năm đã ban hành; cây mục tiêu/nội dung nguồn chỉ đọc, giữ tick từng nội dung.
- EDU-60 (PROPOSED, nguồn user 08/11/2026): tư cách lập tổ chỉ cho xem năm đã ban hành; HT/PHT hiệu lực mọi nhóm tuổi, tổ trưởng/GV đúng tuổi được giao (user làm rõ sau analyze cùng ngày); toàn bộ mục tiêu/nội dung năm trong tổ hiển thị đầy đủ, không cuộn riêng trong card.

## 8. Business Processes

1. [B] Người phụ trách năm hoàn thiện và duyệt cuối theo phân công.
2. [S] Khi duyệt cuối hợp lệ, năm chuyển Ban hành theo cơ chế hiện có.
3. [S] Người lập tổ mở danh sách/chi tiết/chọn nguồn; hệ thống chỉ cung cấp nguồn đã ban hành đúng phạm vi, trừ quyền năm độc lập.
4. [S] Chưa có nguồn hợp lệ: thông báo rõ, không cung cấp nội dung DRAFT/REVIEW và không cho dùng làm nguồn mới.
5. [S] Mở kế hoạch tổ: liệt kê toàn bộ cây mục tiêu/nội dung kế thừa; card giãn theo dữ liệu, người dùng cuộn trang để đọc các hàng phía dưới.
6. [S] Việc tick nội dung theo chủ đề và lưu/mở lại vẫn theo EDU-55; dữ liệu nguồn không đổi.

## 9. State / Lifecycle

- DRAFT: chưa ban hành, không hiển thị cho người chỉ có quyền lập tổ.
- REVIEW: kể cả đã duyệt bước trung gian, vẫn chưa ban hành và bị giới hạn tương tự DRAFT.
- APPROVED: đã ban hành, có thể xem/dùng trong phạm vi quyền hiện hành.
- Bản nháp phiên bản mới không làm lộ nội dung nháp qua phiên bản đã ban hành; cách chọn nguồn/đọc hồ sơ tổ cũ giữ theo feature012.
- Delta không thêm trạng thái/chuyển trạng thái hoặc hồi tố nội dung tổ.

## 10. Business Constraints

- Quyền phải được kiểm tra ở server khi đọc, kể cả người dùng mở đường dẫn đã biết hoặc đổi bộ lọc.
- Không thay cấu hình quyền năm đã có để đáp ứng riêng luồng lập tổ.
- Liệt kê hết bao gồm mục tiêu chưa tick và mọi nội dung của từng mục tiêu; không dùng bộ lọc ngầm theo dấu tích để làm ngắn danh sách.
- Không giới hạn chiều cao làm ẩn hàng hoặc tạo vùng cuộn riêng trong card; cho phép cuộn toàn trang.
- Giữ nguồn chỉ đọc, thứ tự/nhãn/phân nhóm/dấu tích, không thêm quyền biên soạn.

## 11. Non-Functional Requirements

- Security: mọi ca đọc năm chưa ban hành bằng quyền lập tổ đều bị chặn; không lộ nội dung qua danh sách/chi tiết/chọn nguồn.
- Usability: 100% mục tiêu/nội dung trong dữ liệu mẫu đầy đủ có thể đọc bằng cuộn trang; không bị cắt nội dung dài.
- Availability: nghiệp vụ không phụ thuộc AI. Không bổ sung SLA hiệu năng chưa được chốt.

## 12. Acceptance Criteria

| ID | Given / When / Then | Truy vết |
| --- | --- | --- |
| AC-01 | Chỉ có quyền lập tổ; mở danh sách hoặc chọn nguồn có DRAFT/REVIEW/APPROVED cùng phạm vi; chỉ nhận năm đã ban hành. | UR-01, EDU-60, US1.1 |
| AC-02 | Biết đường dẫn năm DRAFT/REVIEW; mở trực tiếp hoặc đổi bộ lọc; không đọc được nội dung. | UR-01, EDU-60, US1.2/3 |
| AC-03 | Năm được duyệt cuối; người lập tổ tải lại; thấy và mở được năm đúng phạm vi. | UR-01, EDU-54/60, US1.4 |
| AC-04 | Không có nguồn ban hành hợp lệ; mở luồng lập tổ; thấy lý do, không dùng nháp làm nguồn. | UR-02, EDU-55/60, US1.5 |
| AC-05 | Người có quyền soạn/duyệt năm độc lập đồng thời lập tổ; thực hiện nhiệm vụ năm; giữ quyền theo EDU-54. | EDU-54/60, US1.6 |
| AC-06 | Mở tổ có nhiều nhóm/mục tiêu/nội dung và hàng chưa tick; cuộn trang; đọc được mọi hàng, không cuộn trong card, không mất nhãn/thứ tự/dấu tích. | UR-03, EDU-55/60, US2.1/2 |
| AC-07 | Tick một nội dung hợp lệ rồi lưu/mở lại; chỉ liên kết đã chọn đổi, cây nguồn và toàn bộ danh sách giữ nguyên. | UR-03, EDU-55, US2.3 |
| AC-08 | Năm đã ban hành ngoài phạm vi; mở danh sách/chi tiết/chọn nguồn; vẫn bị giới hạn theo quyền hiện hành. | UR-01, EDU-60, US1.7 |
| AC-09 | Hai tài khoản tổ trưởng/GV chỉ có quyền lập tổ, không HT/PHT hiệu lực, phụ trách hai nhóm tuổi khác nhau; xem danh sách/chi tiết/chọn nguồn hoặc đổi đường dẫn/bộ lọc; chỉ xem được nguồn năm đã ban hành đúng nhóm tuổi từng tài khoản được giao. | UR-01, EDU-60, US1.8 |
| AC-10 | Chưa có/đã hết/bị thu hồi phân công; đọc nguồn năm bằng quyền lập tổ, kể cả liên kết cũ; không được xem/dùng nguồn; lần đọc sau đổi phân công áp dụng phạm vi mới. | UR-01, EDU-60, US1.9 |
| AC-11 | HT/PHT có phân công hiệu lực và quyền đọc hợp lệ; xem annual APPROVED ở hai nhóm tuổi khác nhau; được xem mọi nhóm tuổi. Tổ trưởng/GV không kiêm HT/PHT hiệu lực chỉ xem tuổi được giao; mất phân công quản lý không còn cấp scope mọi tuổi, trạng thái chưa ban hành vẫn theo Q1=A. | UR-01, EDU-60, US1.10 |
| AC-12 | Form/detail tổ; xem phần2; bốn header/cột nguồn nền xám, ô tick còn thao tác và giữ cấu trúc phân nhóm. | FR-008, US2 |
| AC-13 | Tick hai nội dung vào chủ đề; mở select có nhãn Xem mục tiêu ở phần3; chỉ xem đủ mục tiêu/nội dung tương ứng, không ghi dữ liệu hoặc hiện dãy MT ngoài select. | FR-009, US2 |
| AC-14 | Mở phần3 tổ; cột nhánh/dự án và mục tiêu cùng có, toàn bảng/header xám, chỉ select xem mục tiêu thao tác; năm vẫn biên soạn như cũ. | FR-010, US2 |

## 13. Domain Terminology

- “Đã được duyệt ban hành”: APPROVED sau bước duyệt cuối, không phải duyệt trung gian.
- “Người tạo kế hoạch tổ”: người có quyền lập tổ theo cấu hình, không cố định theo tên vai trò mặc định.
- “Đúng độ tuổi và tài khoản đảm nhận”: tổ trưởng/GV chỉ đúng tuổi phân công; HT/PHT có phân công hiệu lực có scope mọi nhóm tuổi theo user làm rõ sau analyze. Không yêu cầu tài khoản đó là tác giả bản năm, không tự cấp quyền từ tên role khi phân công hết hiệu lực.
- “Liệt kê hết”: đầy đủ mục tiêu và nội dung kế thừa trong kế hoạch tổ đang mở, bao gồm hàng chưa phân bổ.
- “Không scroll trong card”: không có vùng cuộn riêng làm ẩn danh sách bên trong card; trang vẫn được cuộn.

## 14. Assumptions & Open Questions

- User bổ sung08/11/2026 rồi làm rõ sau analyze: HT/PHT mọi nhóm tuổi, tổ trưởng/GV đúng tuổi phân công. Đã đồng bộ đặc tả quyền server và AC-09/10/11; không phải bằng chứng source đã triển khai.
- Giả định phạm vi giao diện: phần mục tiêu nguồn của kế hoạch tổ ở màn soạn và màn xem chi tiết; không mở rộng sang danh sách kế hoạch hoặc màn quản trị khác.
- Đã chốt Q1=A trong speckit-clarify ngày 08/11/2026: giữ quyền xem năm chưa ban hành để soạn/duyệt theo quyền năm độc lập và phân công hợp lệ; giới hạn mới áp dụng quyền xem cấp từ tư cách lập tổ. Không còn là giả định.
- Nguồn/phiên bản và khả năng đọc tổ cũ giữ theo feature012. Không tự đổi contract hay dữ liệu lưu trữ.
- Không có câu hỏi nghiệp vụ chặn đặc tả theo phạm vi trên. Plan phải đối chiếu source hiện tại và tác động client; không xem tài liệu là bằng chứng hành vi đã triển khai.
