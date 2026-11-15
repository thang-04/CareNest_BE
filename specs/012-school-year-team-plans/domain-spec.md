# Domain spec — Tách Kế hoạch năm học và Kế hoạch tổ

Nguồn: user trong chat ngày 07/11/2026, yêu cầu `$speckit-specify` và trả lời Q1=A (bổ sung PHT tạo nhánh, GV tạo thêm), Q2=A, Q3=A. Quyết định đã được user chốt cho đặc tả; mức nguồn tri thức vẫn PROPOSED do chưa có nguồn team/trường xác nhận. Làn L; chỉ đặc tả, chưa cho phép sửa client hoặc triển khai. Theo [.ai/workflows/feature-spec-loop.md](../../.ai/workflows/feature-spec-loop.md).

Cập nhật mới 07/11/2026: GV được sửa tên nhánh lấy từ PHT trên bản lớp; chủ đề lớn chỉ đổi thứ tự dạy, không sửa tên/nội dung nguồn. Thay phần khóa tên nhánh đã ghi trước đó, giữ các quyết định khác.

## 1. Domain Overview

Hiện có một cấp kế hoạch năm theo nhóm tuổi, chứa chủ đề, mục tiêu, nội dung và dấu tích liên kết nội dung với chủ đề. User muốn tách định hướng năm học do ban giám hiệu xây dựng khỏi kế hoạch tổ làm căn cứ cho giáo viên. Mục tiêu là thống nhất định hướng, giữ quyền tổ chức của tổ chuyên môn và cho giáo viên điều chỉnh thời gian trong giới hạn cấu hình.

## 2. Scope

Trong phạm vi: trang “Kế hoạch năm học” đặt trên “Kế hoạch tổ”, tái sử dụng cấu trúc trang tổ; thông tin chung, dự kiến chủ đề/sự kiện, mục tiêu/nội dung, chủ đề nhánh tùy chọn; hai luồng tạo/duyệt; nguồn giáo viên là kế hoạch tổ; thời lượng chủ đề và chủ đề nhánh có cấu hình.

Ngoài phạm vi lượt này: product code, thiết kế vật lý, thay contract, migration, sửa FE/APP, deploy và Git mutation. Cấu hình thuộc một trường đang vận hành, không mở rộng multi-school/multi-tenant.

## 3. Actors & Roles

- Phó hiệu trưởng: mặc định tạo kế hoạch năm học và có quyền tạo trước nhánh tại đó; duyệt kế hoạch tổ.
- Hiệu trưởng: mặc định duyệt kế hoạch năm học; cấu hình người tạo/duyệt riêng từng loại theo EDU-54 và giới hạn tuần của cả chủ đề lớn/nhánh.
- Tổ trưởng: tạo kế hoạch tổ trong phạm vi phân công nhóm tuổi.
- Làm rõ 07/11/2026, câu 1 của `$speckit-clarify`: tổ trưởng không biên soạn lại mục tiêu/nội dung kế thừa, chỉ chọn mục tiêu nguồn để đưa vào chủ đề; không thêm mới/sửa/xóa cây mục tiêu/nội dung, kể cả nhập file. Nguồn: user chọn A, “không được sửa chỉ được thêm mục tiêu vào chủ đề”.
- Câu 2 clarify và chỉ dẫn tiếp theo 07/11/2026: giữ tick từng nội dung vào chủ đề như UI/logic cũ, mục tiêu tương ứng đi theo; không tự tick toàn bộ nội dung khi chọn một mục tiêu. Chỉ cố định thông tin nguồn, không đổi các logic liên kết/lưu/phiên bản hiện có ngoài phạm vi feature.
- Giáo viên: dùng kế hoạch tổ đã ban hành để lập chủ đề nhánh, kế hoạch/giáo án của lớp; được lấy mẫu nhánh PHT, giữ hoặc sửa tên trên bản lớp, tạo thêm nhánh và chỉnh lịch trong giới hạn; được đổi thứ tự dạy chủ đề lớn nhưng không sửa tên/nội dung nguồn.
- Hai chuỗi duyệt là mặc định có thể cấu hình; mọi thao tác theo phân công hiệu lực, không mở quyền ngoài lớp/nhóm tuổi/campus. Quyền tạo nhánh PHT ở cấp năm khác quyền GV thêm nhánh ở cấp lớp.

## 4. Domain Concepts

- Kế hoạch năm học: cấp định hướng mới, có thể đặt trước mẫu chủ đề nhánh để GV lấy/sửa tên tại lớp.
- Kế hoạch tổ: cấp triển khai do tổ trưởng lập, là nguồn gửi đến giáo viên.
- Chủ đề lớn: chủ đề chứa các chủ đề nhánh; thời lượng yêu cầu 2–4 tuần.
- Chủ đề nhánh: đơn vị triển khai có thể kéo dài 1–2 tuần theo yêu cầu mới, không đồng nhất một nhánh với một tuần như trước.
- Mục tiêu/nội dung: cây giáo dục đang có, không còn bảng tick chủ đề ở phần 3 của trang năm mới.
- Cấu hình thời lượng: HT thiết lập giới hạn tuần học cho cả chủ đề lớn và nhánh; chỉ áp dụng hồ sơ tạo mới, hồ sơ đã tồn tại giữ giới hạn trước đó.

## 5. Domain Relationships

Luồng: năm học theo nhóm tuổi → tổ đúng nhóm tuổi → tài liệu lớp. Cấp năm áp dụng toàn trường trong nhóm tuổi đó. Tổ lấy nội dung và nhánh định sẵn từ năm đã ban hành; tổ đã ban hành là nguồn trực tiếp của giáo viên. Nhánh mẫu nguồn không bị GV sửa, nhưng bản nhánh lấy về lớp được đổi tên; GV vẫn được tạo thêm nhánh. Chủ đề lớn được đổi thứ tự dạy ở lớp, không sửa tên/nội dung nguồn; Q4 đã chốt mẫu là tham khảo, GV được không lấy từng mẫu; Q5=A: chuyển cả chủ đề kèm nhánh, giữ số tuần, dời ngày theo thứ tự mới. Giữ nguyên quy tắc chuỗi phiên bản và tối đa một bản chưa ban hành cho mỗi loại/năm học/nhóm tuổi. Kế hoạch cũ giữ ở cấp tổ, là ngoại lệ chuyển tiếp không cần nguồn năm mới. Quan hệ một nhánh–nhiều tuần phải được tính đến; cơ chế lưu quan hệ thuộc thiết kế sau, chưa thiết kế bảng.

## 6. User Requirements

US1: Phó HT soạn kế hoạch năm và gửi HT duyệt. US2: Tổ trưởng soạn kế hoạch tổ, PHT duyệt, giáo viên nhận nguồn đúng cấp. US3: Giáo viên lấy/sửa tên nhánh gợi ý, tạo thêm nhánh và đổi thứ tự dạy chủ đề lớn trong giới hạn. US4: Người được phân quyền cấu hình thời lượng. Chi tiết FR/AC tại [spec.md](spec.md).

## 7. Business Rules

- EDU-55 (PROPOSED, user đã chốt đặc tả): năm theo nhóm tuổi, hai luồng mặc định cấu hình được, tổ lấy nội dung năm; cập nhật mới nhất cho GV lấy/sửa tên nhánh PHT trên bản lớp, thêm nhánh và đổi thứ tự dạy chủ đề lớn mà không sửa tên/nội dung chủ đề lớn; bố cục bốn cột có STT; nguồn GV là tổ đã ban hành; dữ liệu cũ ở cấp tổ.
- EDU-56 (PROPOSED, user đã chốt đặc tả): HT cấu hình cả hai loại, mặc định lớn 2–4 tuần học, nhánh 1–2 tuần học; chỉ áp dụng hồ sơ tạo mới, nhánh nằm trong chủ đề cha.
- EDU-01/12/13: giữ nhóm tuổi, phiên bản, trình tự tài liệu lớp; thay nguồn trực tiếp bằng tổ sau triển khai feature012.
- EDU-14/39: phần “mỗi nhánh một tuần” được user chọn thay bằng nhánh có thời lượng cấu hình trong feature012; giữ không trùng chủ đề cùng cấp. Source hiện tại chưa triển khai thay đổi.
- EDU-40/41: giữ trọn Thứ Hai–Thứ Sáu, bỏ tuần nghỉ toàn bộ, tuần nghỉ lẻ ngày vẫn tính tuần học.
- EDU-54: giữ cấu hình theo loại, chống tự duyệt và ba trạng thái; thêm loại năm/tổ riêng với mặc định đã chọn, không bỏ các bảo vệ cũ.

## 8. Business Processes

Luồng năm: [B] PHT xác định định hướng → [S] tạo nháp năm, nhập nội dung/nhánh định sẵn → [S] gửi → [S] HT duyệt/ban hành.

Luồng tổ: [B] tổ trưởng triển khai định hướng → [S] lập kế hoạch tổ → [S] gửi PHT duyệt → [S] cung cấp kế hoạch tổ cho giáo viên → [S] GV lập nhánh/giáo án theo phạm vi lớp.

Ngoại lệ: sai người/phạm vi bị từ chối; lịch sai giới hạn phải báo lý do. Chỉ kế hoạch tổ đã ban hành được dùng làm nguồn GV. Tổ kế thừa nội dung năm; bản đã ban hành không bị cập nhật ngược khi nguồn đổi, giữ phiên bản/lịch sử theo rule cũ. Không tự thêm kênh email/push.

## 9. State / Lifecycle

Tái sử dụng nguyên tắc EDU-54: Nháp → Đang duyệt → Ban hành; trả lại về Nháp kèm lý do; không sửa khi đang duyệt/đã ban hành, không tự duyệt. Chỉnh lịch/thêm nhánh chỉ khi tài liệu lớp được phép soạn, không sửa bản nguồn đã ban hành. Bản đã ban hành và snapshot cũ bảo toàn theo EDU-25/26/36/54. Kế hoạch cũ giữ cấp tổ cùng trạng thái/phiên bản/liên kết; thay cấu hình tuần không hồi tố các hồ sơ đã tồn tại.

## 10. Business Constraints

Một trường, hai điểm trường. Không xóa dấu tích/dữ liệu lịch sử. Lịch bắt đầu Thứ Hai/kết thúc Thứ Sáu, tính tuần học và bỏ tuần nghỉ toàn bộ; chủ đề lớn nằm trong năm học, nhánh nằm trong cha; chủ đề cùng cấp không trùng. Tên mẫu nguồn giữ nguyên trong bản nguồn, tên bản lớp được GV sửa. Chủ đề lớn chỉ đổi thứ tự dạy, giữ tên/nội dung; mẫu nhánh PHT là tham khảo, không bắt buộc lấy đủ (Q4=A); đổi thứ tự chuyển cả khối chủ đề kèm nhánh, giữ số tuần học riêng, dời ngày theo thứ tự mới (Q5=A). Giới hạn là số tuần nguyên dương, nhỏ nhất không vượt lớn nhất. Phải bao phủ giáo án của cả hai tuần và nguồn đánh giá trẻ khi một nhánh kéo dài hai tuần.

## 11. Non-Functional Requirements

Từ chối 100% thao tác tạo/duyệt/chỉnh lịch ngoài quyền trong bộ nghiệm thu. Lưu/mở lại giữ đúng nội dung, nhánh, lịch và liên kết nguồn. Hiển thị lý do khi lịch vi phạm. Không đặt SLA mới chưa có nguồn; không cần AI để hoàn thành luồng.

## 12. Acceptance Criteria

- AC-01 → FR-001..003 / US1: thấy trang năm trên tổ, đúng tiêu đề phần, đủ dữ liệu nhập.
- AC-02 → FR-004..005 / US1: phần 2 không có cột mục tiêu liên kết/tự tick; phần 3 đúng bốn cột Nhóm kết quả mong đợi/STT/Mục tiêu giáo dục/Nội dung giáo dục, không tick/viết tắt.
- AC-03 → FR-006..007 / US1,2: đúng hai cặp người tạo/duyệt; sai quyền và tự duyệt bị chặn.
- AC-04 → FR-008..009/014 / US2: tổ đúng nhóm tuổi lấy nội dung năm đã ban hành; GV lấy tổ sau ban hành, được lấy/đổi tên nhánh PHT ở lớp và thêm nhánh; trường hợp ngoài quyền bị chặn.
- AC-05 → FR-010..012/015 / US3: thử nhánh 1/2/3 tuần, lớn 1/2/4/5 tuần; chỉ các mốc trong giới hạn được chấp nhận. Bỏ tuần nghỉ trọn, giữ tuần nghỉ lẻ, lịch nhánh không ra ngoài cha; nhánh hai tuần có đủ hai tuần giáo án. HT đổi cấu hình lưu/mở lại được, không hồi tố; người khác bị chặn, cấu hình đảo min/max hoặc tuần không nguyên dương bị từ chối.
- AC-06 → FR-013 / US2: dữ liệu/phiên bản đã ban hành giữ nguyên, kế hoạch cũ thuộc tổ cùng liên kết tài liệu lớp, không yêu cầu tạo ngược nguồn năm mới.
- AC-07 → FR-006..007 / US2: HT cấu hình riêng hai loại theo EDU-54; chống tự duyệt, snapshot cũ giữ nguyên và hồ sơ đang chờ theo quy tắc đổi chuỗi hiện hành.
- AC-08 → FR-016 / US2.8: tổ trưởng chọn được mục tiêu nguồn vào chủ đề; không sửa/thêm mới/xóa được mục tiêu/nội dung kế thừa; ghi trực tiếp/nhập file không vượt được giới hạn này.
- AC-09 → FR-016 / US2.9: với mục tiêu có nhiều nội dung, tick/bỏ tick một nội dung chỉ đổi liên kết đó, mục tiêu tương ứng đi theo; các nội dung khác không tự tích, câu chữ/cây nguồn giữ nguyên; lưu/mở lại không mất lựa chọn. Nội dung trống vẫn theo EDU-50.

- AC-10 → FR-009 / US2.3: lấy nhánh mẫu rồi đổi tên/lưu/mở lại được, mẫu PHT không đổi; GV được không lấy/bỏ chọn từng mẫu (Q4=A), mẫu nguồn không đổi.
- AC-11 → FR-017 / US3.8: đổi thứ tự chủ đề lớn tại lớp, tên/nội dung và quan hệ mục tiêu/nhánh giữ đúng chủ đề; chuyển cả chủ đề kèm nhánh, giữ số tuần học mỗi chủ đề/nhánh, ngày chuyển theo thứ tự mới (Q5=A); ca A2/B4 đổi B trước A vẫn B4/A2, lưu/mở lại không mất nhánh.

## 13. Domain Terminology

“Kế hoạch năm học” trong feature này là cấp mới; tên “kế hoạch năm” trong source/tài liệu cũ không tự chứng minh tương đương cấp mới. “Kế hoạch tổ” là nguồn trực tiếp cho giáo viên. “Dự kiến chủ đề / sự kiện năm học” là tên phần được user yêu cầu. “Chủ đề lớn” và “chủ đề nhánh” có thời lượng khác nhau; một nhánh không còn mặc định tương đương một tuần.

## 14. Assumptions & Open Questions

Giả định phục vụ phân tích: tái sử dụng cấu trúc giáo dục, phân quyền, lưu tay và vòng duyệt hiện có ngoài phần user yêu cầu đổi; đây không phải quyết định mới của user. Constitution 1.0.0 được tái sử dụng, không cần đổi stack hay nguyên tắc.

- Q1 — đã trả lời 07/11/2026: A, năm theo nhóm tuổi; tổ lấy nội dung năm; phần khóa tên nhánh của quyết định ban đầu đã được user thay bằng quyền sửa tên nhánh ở lớp trong delta mới; GV vẫn chỉnh lịch và tạo thêm; PHT có quyền tạo nhánh; hai chuỗi duyệt là mặc định cấu hình được; GV nhận sau tổ ban hành; kế hoạch cũ giữ cấp tổ.
- Q2 — đã trả lời 07/11/2026: A, bốn cột có STT; phần 2 bỏ cột mục tiêu liên kết/tự tick; phần 3 bỏ tick/viết tắt chủ đề.
- Q3 — đã trả lời 07/11/2026: A, HT cấu hình cả hai loại; tính tuần học, bỏ tuần nghỉ trọn, giữ Thứ Hai–Thứ Sáu; nhánh trong cha, cấu hình mới chỉ áp dụng hồ sơ tạo mới.

Q1–Q3 của bước specify đã được chốt. Vòng `$speckit-clarify` đầu hoàn thành hai câu: (1) tổ trưởng chỉ phân bổ, không sửa cây nguồn; (2) giữ tick từng nội dung và mục tiêu đi theo như logic cũ, chỉ cố định thông tin nguồn. Delta mới đã làm phát sinh Q4/Q5; user đã trả lời A/A ở vòng clarify tiếp theo. Kết luận review thiết kế trước delta là lịch sử, cần cập nhật thiết kế theo quyết định mới. Thiết kế quan hệ hai cấp, chuyển dữ liệu, kiểm tra contract và kế hoạch kiểm chứng thuộc bước plan/data model; giữ EDU-25/26, không tự thêm cơ chế đồng bộ hoặc thay nghiệp vụ phiên bản. Nếu thiết kế phát hiện cần đổi nghiệp vụ phải trình user.

- Q4 — đã trả lời A, user 07/11/2026: mẫu nhánh PHT là tham khảo; GV chọn lấy/không lấy từng mẫu, sửa tên và tạo thêm, không bắt buộc lấy đủ.
- Q5 — đã trả lời A, user 07/11/2026: chuyển cả khối chủ đề kèm các nhánh ở lớp, giữ số tuần từng chủ đề/nhánh, dời ngày theo thứ tự mới; không đổi tên/nội dung chủ đề lớn hoặc bản nguồn.

Không còn câu hỏi nghiệp vụ chặn đặc tả delta; Q4/Q5 đã chọn A/A. Plan/data-model/contracts đã cập nhật và review lại ở lượt speckit-plan tiếp theo ngày07/11/2026, vẫn draft; chưa được duyệt code. Plan làn L phải trình user duyệt trước triển khai, quyền sửa FE/APP vẫn cần theo AGENTS.md.

### Delta đã chốt trong FE review — 07/11/2026

- AC-12 / FR-018 / EDU-57: PHT không xóa được kế hoạch tổ ở mọi trạng thái, kể cả nếu cấu hình là người soạn; server và UI phải chặn, dữ liệu không đổi. Các quyền và chức năng khác giữ nguyên.
- AC-02 bổ sung: phần 2 năm có cột “Chủ đề nhánh / dự án” thay cột mục tiêu map, gõ/Enter thêm nhánh dưới cùng chủ đề; tổ giữ cột mục tiêu. Nhánh gợi ý chỉ cần tên ở cấp năm, GV quyết định lịch bản lớp hợp lệ; không bắt nhập hai nơi.

### Delta nhãn mục tiêu — user duyệt toàn bộ 07/11/2026

- Nguồn user: đổi nhãn MT ở năm học, kéo tự tăng số như Excel; đã duyệt plan và sửa BE liên quan, sau đó “duyệt hết k cần hỏi nữa”. Thực hiện liên tục, không dừng xin duyệt từng phase.
- EDU-58 (PROPOSED theo nguồn user, chưa xác nhận team/trường), AC-13: nhãn hiển thị tách khỏi goalNo/ID. Tùy chỉnh ở SCHOOL_YEAR, TEAM nhận clone và không sửa nhãn. Không hồi tố snapshot bản ban hành.
- Field displayLabel tối đa100, null/không gửi giữ nhãn đã lưu để tương thích client cũ; chuỗi rỗng xóa về mặc định MTn. Dữ liệu cũ null, không backfill đổi số.
- Kéo tăng cụm số cuối, giữ zero padding, không số thì sao chép chữ; có bàn phím và hủy, chỉ thay nhãn của range. Liên kết/tick giữ nguyên.
- Bố cục hai loại: thông tin chung → mục tiêu/nội dung → dự kiến chủ đề; TEAM cây nguồn và chủ đề/đợt nghỉ chỉ đọc, xám STT, lĩnh vực con vàng. Ngày nhánh dùng lịch chủ đề lớn và trong cha.

### Delta lịch nhánh — user07/11/2026
- EDU-59 theo yêu cầu user (PROPOSED theo nguồn user, không tự nâng trường/team): kiểm nhánh trùng lịch trong cùng chủ đề cha và tuần Thứ Hai–Thứ Sáu như chủ đề lớn. Ngày trùng tính cả hai đầu. Không so nhánh với cha vì nhánh phải nằm trong cha; các chủ đề cha có kiểm trùng riêng.
- Nháp giữ khả năng lưu để sửa; gửi duyệt/ban hành kiểm lại server. Mẫu chỉ có tên vẫn hợp lệ như trước; không bắt nhập ngày mới.
- Hai đầu ngày luôn ở dạng dd/mm/yy - dd/mm/yy; bấm đầu nào sửa riêng đầu đó bằng cùng DatePickerCell. Không nhãn Từ/Đến và không chế độ thu gọn/mở rộng. Ngày nhánh vẫn nằm trong cha.
