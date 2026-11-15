# Domain spec — Cấu hình người tạo/duyệt kế hoạch giáo dục

Nguồn: user trong chat ngày 06/11/2026; yêu cầu đổi “Người ký” thành “Người tạo/duyệt”. Ba lựa chọn đã trả lời: cấu hình theo vai trò; áp dụng cả kế hoạch năm và hồ sơ lớp; không tự duyệt. Đây là yêu cầu user, chưa có xác nhận trường/team. Tham chiếu [workflow](../../.ai/workflows/feature-spec-loop.md).

**Implementation status (06/11/2026)**: User subsequently authorized implementation and FE education work. The APP was confirmed not to have education-plan integration. See [verification results](quickstart.md); EDU-54 remains PROPOSED at school/team level.

## 1. Domain Overview

Cấu hình hiện tại chỉ lưu vai trò ký; kế hoạch năm vẫn có luồng cố định. Mục tiêu là cấu hình riêng vai trò tạo và thứ tự vai trò duyệt, để việc phân công phản ánh đúng người được thao tác. Người hưởng lợi: người quản lý cấu hình, người soạn và người duyệt.

## 2. Scope

Trong phạm vi: kế hoạch năm, chủ đề nhánh, hồ sơ chủ đề; giáo án tuần/ngày đi cùng hồ sơ chủ đề. Cấu hình theo vai trò, kiểm quyền thực tế, tách người tạo với người duyệt. Ngoài phạm vi: chỉ định tài khoản cụ thể, chữ ký số/ảnh ký, module khác, thay kiến trúc phân quyền toàn hệ thống. Lượt này chỉ đặc tả trong BE, chưa sửa Web/App hay source ứng dụng.

## 3. Actors & Roles

Chỉ Hiệu trưởng có phân công còn hiệu lực quản lý cấu hình người tạo/duyệt kế hoạch giáo dục. Phó HT không có quyền cấu hình phần này (user bổ sung 06/11/2026); thay giả định ban đầu HT/PHT. Không thay quyền của module khác. Vai trò nghiệp vụ xem xét: Giáo viên, Tổ trưởng, Phó HT, HT. Tổ trưởng là giáo viên có phân công tổ trưởng đúng nhóm tuổi, không phải role đăng nhập độc lập (AUTH-23). Quyền cấu hình không tự trao quyền tạo/duyệt. Phạm vi dữ liệu và phân công vẫn phải kiểm ở server; không tự cấp xem/sửa mọi lớp.

## 4. Domain Concepts

Chính sách tạo/duyệt: tập vai trò tạo và chuỗi vai trò duyệt của một loại hồ sơ. Người tạo: danh tính người tạo mới thực tế, khác tập người được phép tạo. Người duyệt: danh tính người thực hiện một bước duyệt. Phân công: căn cứ xác định vai trò hiệu lực và phạm vi. Chữ ký bản in: thông tin trình bày, không phải quyền duyệt.

## 5. Domain Relationships

Mỗi loại hồ sơ có chính sách riêng. Hai tập vai trò tạo và duyệt không giao nhau trong cùng loại hồ sơ; người có quyền duyệt loại đó không được tạo/soạn/sửa/gửi loại đó dù có thêm vai trò soạn; người tạo hoặc trực tiếp soạn/sửa không được duyệt; người đảm nhiệm duyệt không được soạn/sửa theo chiều ngược lại dù có nhiều vai trò. Một hồ sơ có người tạo và nhiều sự kiện duyệt. Hồ sơ chủ đề bao gồm các giáo án tuần/ngày; duyệt theo hồ sơ, không thêm bước riêng.

## 6. User Requirements

Người quản lý cấu hình được xác định ai có thể tạo và ai duyệt theo từng loại hồ sơ. Người dùng hiểu vai trò của mình trước khi thao tác. Người ngoài cấu hình hoặc ngoài phạm vi bị từ chối. Các yêu cầu kiểm chứng và tình huống nằm ở [spec.md](spec.md).

## 7. Business Rules

- EDU-54 (PROPOSED): yêu cầu mới, theo BUSINESS_RULES.md; nguồn user 06/11/2026, chưa tuyên bố trường/team chốt.
- EDU-02/04/05/08/11/18: mô tả hành vi hiện tại, cần điều chỉnh sau khi toàn bộ quyết định được làm rõ; không âm thầm ghi đè rule CONFIRMED.
- EDU-17: giáo án tuần/ngày duyệt cùng hồ sơ chủ đề; EDU-21/33: duyệt trên hệ thống khác chữ ký tay bản in; AUTH-23: tổ trưởng theo phân công.
- Cấm tự duyệt dựa trên danh tính người tạo và những người trực tiếp soạn/sửa, không dựa trên tên hiển thị hoặc vai trò đang chọn.

## 8. Business Processes

[B] Người quản lý xác định trách nhiệm → [S] chọn loại hồ sơ, vai trò tạo và vai trò duyệt theo thứ tự → [S] kiểm tra, lưu → [S] người được phép tạo hồ sơ trong phạm vi → [S] gửi duyệt → [S] kiểm đúng lượt, phân công và đáp ứng tách người soạn/người duyệt → [S] ghi kết quả/lịch sử. Trả lại giữ yêu cầu có lý do. Người không đủ quyền không làm thay đổi hồ sơ.

Quyền tạo gồm tạo/soạn/sửa/gửi trong phạm vi phân công và trạng thái cho phép. Bước duyệt không có người hợp lệ đáp ứng tách người soạn/người duyệt được bỏ qua nếu còn bước sau; tiếp tục xét như vậy, nhưng bước cuối thiếu người phải giữ chờ. Nếu còn người khác hợp lệ cùng vai trò thì không bỏ bước. Bỏ bước không được ghi thành một người đã duyệt và không tự ban hành.

## 9. State / Lifecycle

User sau analyze06/11 chốt đúng ba trạng thái Nháp → Duyệt (đang duyệt) → Ban hành. Role tới lượt nằm trong metadata; không PENDING theo role. Người soạn không sửa khi Duyệt; return để sửa ánh xạ về Nháp có lý do/history, lease không là trạng thái riêng. Vai trò tạo không mặc nhiên cho quyền chuyển sang đã duyệt. Kế hoạch năm không còn mặc định chỉ Phó HT ban hành trong thiết kế đích; chi tiết chuyển trạng thái được thiết kế sau clarify. Hồ sơ đã duyệt giữ lịch sử, không suy diễn lại người tạo/người duyệt từ cấu hình mới.

## 10. Business Constraints

Một trường, hai điểm trường; kiểm phạm vi theo dữ liệu hiện có. Không dùng cấu hình để vượt phân công. Không xóa danh tính và lịch sử hồ sơ cũ. User chọn B trong clarify 06/11/2026: đổi cấu hình thì hồ sơ đang chờ chuyển ngay sang chuỗi mới, giữ lịch sử đã duyệt; thay giả định giữ chuỗi đã chụp theo EDU-18. Không mở lại bản đã ban hành; chỉ hoàn tất bản đang chờ khi mọi bước trong chuỗi mới đều có kết quả duyệt hợp lệ. User làm rõ: phần đã duyệt giữ nguyên, không phải duyệt lại; bản hoàn tất không mở lại, bản đang chờ xử lý các bước còn thiếu theo chuỗi mới. User chọn A: nếu mọi bước trong chuỗi mới đã có kết quả duyệt hợp lệ, hồ sơ hoàn tất theo kết quả đã có, không yêu cầu xác nhận lại.

## 11. Non-Functional Requirements

Tất cả đường ghi phải kiểm quyền tại server; không dựa riêng vào ẩn nút. Từ chối thao tác không được sửa nội dung/trạng thái/lịch sử duyệt. Không lộ tài khoản hoặc dữ liệu thật trong tài liệu/test. Người dùng đọc phân biệt được quyền tạo, quyền duyệt và chữ ký bản in; không hứa SLA mới.

## 12. Acceptance Criteria

- AC-01 → FR-001..003: lưu cấu hình rồi mở lại thấy đúng vai trò tạo, thứ tự duyệt và tên “Người tạo/duyệt”.
- AC-02 → FR-004/005: người đủ vai trò nhưng ngoài phân công không tạo/duyệt được; người trong phạm vi thực hiện đúng bước.
- AC-03 → FR-006: người tạo/người cùng soạn sửa không được duyệt; người duyệt không được soạn/sửa. Tách theo loại hồ sơ; vai trò đã chọn duyệt không được chọn soạn; người nhiều vai trò khớp quyền duyệt bị chặn soạn cùng loại.
- AC-04 → FR-007: kế hoạch năm và hồ sơ lớp dùng chính sách tương ứng; tuần/ngày theo hồ sơ.
- AC-05 → FR-008/009: hồ sơ cũ giữ thông tin lịch sử; thay cấu hình không làm hồ sơ đã duyệt mất hiệu lực; bản đang chờ theo chuỗi mới, giữ các bước đã duyệt. Nếu mọi bước trong chuỗi mới đều đã duyệt thì hoàn tất theo kết quả cũ, không tạo sự kiện duyệt giả.
- AC-06 → FR-010/011: vai trò tạo cho phép tạo/soạn/sửa/gửi đúng phạm vi và trạng thái; thiếu người duyệt ở bước giữa chuyển tiếp, ở bước cuối giữ chờ. Người khác cùng vai trò vẫn được duyệt; người tạo thì bị chặn. Kiểm thêm mọi bước đều thiếu người: không tự ban hành.

## 13. Domain Terminology

“Vai trò tạo” là tập người đủ điều kiện khởi tạo, không đồng nghĩa người đã tạo một hồ sơ. “Người tạo” là cá nhân được ghi nhận lúc khởi tạo. “Chuỗi duyệt” là các vai trò theo thứ tự; “người duyệt” là cá nhân thực hiện. Không gọi duyệt trên hệ thống là ký số.

## 14. Assumptions & Open Questions

Đã trả lời bởi user 06/11/2026: theo vai trò; cả kế hoạch năm và hồ sơ lớp; phải có người khác duyệt.

Q1 đã trả lời (user 06/11/2026): đồng ý đề xuất chọn một/nhiều vai trò tạo GV/Tổ trưởng/Phó HT/HT; quyền tạo gồm tạo/soạn/sửa/gửi trong phạm vi phân công.

Q2 đã trả lời (user 06/11/2026): bỏ bước nếu còn bước khác; bước cuối thiếu người giữ chờ. Clarify đã chốt tách theo loại hồ sơ; plan cần rà dữ liệu cũ và trình user duyệt trước triển khai làn L.

Các quyết định đã được user chốt: chỉ Hiệu trưởng cấu hình; duy trì lịch sử; pending theo ngay chuỗi mới; mỗi loại có ít nhất một vai trò soạn và một duyệt, không lặp và không giao nhau. Nếu chỉ có chính người tạo đủ điều kiện thì không được tự duyệt, cách chuyển bước theo đáp án Q2 đã ghi ở trên. Legacy dùng creator chắc chắn (ownerId/createdBy) làm baseline, theo dõi coauthor đầy đủ từ nâng cấp và công bố giới hạn; user chọn trong bước plan. Không dùng updatedBy làm người tạo/coauthor.


Clarify 06/11/2026 — Q: Khi nhiều người cùng soạn một hồ sơ, những ai bị cấm duyệt hồ sơ đó? → A: Người soạn không duyệt; người duyệt không được soạn/sửa. Áp dụng cả người cùng soạn, thay cách hiểu chỉ cấm người tạo mới. Phạm vi đã được chốt theo loại hồ sơ ở câu tiếp theo; chưa triển khai.

Clarify 06/11/2026 — Q: Bạn muốn tách người soạn và người duyệt ngay trong cấu hình từng loại hồ sơ, hay chỉ tách trên từng hồ sơ cụ thể? → A: Theo loại hồ sơ. Vai trò đã chọn duyệt không được chọn soạn cùng loại; người có quyền duyệt loại đó không được soạn loại đó, kể cả có nhiều vai trò. Loại khác áp dụng cấu hình riêng.

Clarify 06/11/2026 — Q: Khi thay cấu hình người soạn/duyệt, hồ sơ đang chờ duyệt sẽ theo chuỗi cũ hay chuyển sang chuỗi mới? → A: B, chuyển ngay sang chuỗi mới và giữ lịch sử đã duyệt. Phần đã duyệt giữ nguyên theo câu trả lời tiếp theo.

Clarify 06/11/2026 — Q: Khi hồ sơ chuyển sang chuỗi mới, các vai trò đã duyệt trước đó có phải duyệt lại không? → A: Đã duyệt để nguyên, không cần duyệt lại. Bản hoàn tất không mở lại; bản đang chờ chỉ xử lý bước còn thiếu theo chuỗi mới. Cách kết thúc đã chốt A ở câu tiếp theo: hoàn tất theo kết quả duyệt đã có.

Clarify 06/11/2026 — Q: Nếu cấu hình mới chỉ còn Tổ trưởng đã duyệt, hồ sơ đang chờ Phó HT sẽ hoàn tất luôn hay chờ xác nhận kết thúc? → A: A, hoàn tất theo kết quả đã có; không cần xác nhận hay duyệt lại. Chỉ áp dụng khi mọi bước trong chuỗi mới đã có kết quả duyệt hợp lệ, không thay quy tắc bước cuối chưa duyệt mà thiếu người phải giữ chờ.

Chỉ đạo bổ sung user 06/11/2026: chỉ Hiệu trưởng cấu hình người tạo/duyệt và chỉ áp dụng cho kế hoạch giáo dục; Phó HT không có quyền cấu hình phần này.

Kết thúc clarify: 5/5 câu đã trả lời. Bước plan đã khảo sát source và user trả lời bốn điểm hoãn ngày 06/11/2026: non-empty/distinct; sửa/gửi lại sau trả lại và version mới duyệt từ đầu; legacy creator baseline và giới hạn lịch sử; seed year TL→VP, class GV→TL→VP. Không còn câu hỏi nghiệp vụ chặn thiết kế; approval plan/contract/migration vẫn bắt buộc trước implement.

Quyết định bước plan 06/11/2026 — user: cấu hình tối thiểu một vai trò soạn và duyệt, không lặp; sau trả lại rồi sửa/gửi lại hoặc tạo phiên bản mới duyệt từ đầu. Policy-only change cùng nội dung vẫn giữ kết quả cũ. Legacy creator baseline được chấp thuận, coauthor trước nâng cấp không thể khôi phục đầy đủ. Cấu hình ban đầu năm TL soạn→VP duyệt, branch/theme GV soạn→TL→VP duyệt, HT được đổi sau.

AC-07 → FR-012/015: non-empty/distinct/disjoint và seed đúng user. AC-08 → FR-013: nội dung mới/vòng mới không dùng approvals vòng cũ, history giữ. AC-09 → FR-014: migration dùng creator chắc chắn, không updatedBy; legacy coverage/mốc tracking rõ. Các ca kỹ thuật/mapping AC-01..14 của plan ở quickstart.md, không đổi số AC lịch sử ở domain này.

User sau analyze06/11/2026 xác nhận: Duyệt là đang đi qua chuỗi, bước cuối xong tự Ban hành; người soạn không sửa khi Duyệt. Bản in chốt lưu ô ký/vai trò/tên riêng khi ban hành, đổi cấu hình/tên sau đó không đổi hồ sơ cũ. Implementation chỉ giáo dục, không source module khác. Điểm U1 xử lý thêm mọi Nháp đang giữ lease mất quyền trong cùng transaction cấu hình; U2 có schema print_snapshot/review.printSnapshot tại data-model.md §9. Thiết kế kỹ thuật delta cần review/analyze lại; user sau lệnh fix cho phép đúng helper AccountService.findActiveUserIds chỉ đọc và test, không mở API/quyền/nghiệp vụ tài khoản mới; không còn dependency chưa chọn phương án.

Sửa analyze I2/U3/I3 ngày06/11/2026: user cho phép helper ACTIVE-ID tối thiểu như trên. Expand/contract là trình tự nội bộ chưa rollout, không thay ba trạng thái nghiệp vụ khi phát hành; print chỉ lấy printSnapshot, không completionPolicy. Giữ plan draft/approval trước implementation.
