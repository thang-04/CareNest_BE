# Feature Specification: Cấu hình người tạo/duyệt kế hoạch giáo dục

**Feature Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals` (nhánh đang có; không tạo nhánh mới)

**Created**: 2026-11-06

**Status**: Implemented and verified 06/11/2026 — user authorized `$speckit-implement`; final converge evidence is recorded in `quickstart.md` and `docs/quality/VERIFICATION.md`.

**Input**: User description: “cần sửa rằng để người ký -> người tạo/duyệt. để cấu hình ai là người tạo ai là người duyệt”. User đã chọn cấu hình theo vai trò, áp dụng cả kế hoạch năm và hồ sơ lớp, không tự duyệt.

Nguồn và phạm vi nghiệp vụ: [domain-spec.md](domain-spec.md), EDU-54 (PROPOSED). Thay đổi dự kiến làn L vì tác động quyền và luồng kế hoạch năm; lượt này chưa triển khai.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cấu hình trách nhiệm theo loại hồ sơ (Priority: P1)

Hiệu trưởng có phân công còn hiệu lực muốn chọn riêng vai trò tạo và vai trò duyệt theo thứ tự cho kế hoạch giáo dục, thay vì chỉ chọn người ký.

**Why this priority**: Là căn cứ xác định ai được làm và ai chịu trách nhiệm duyệt.

**Independent Test**: Lưu rồi mở lại cấu hình của từng loại hồ sơ và đối chiếu lựa chọn.

**Acceptance Scenarios**:

1. **Given** người có quyền cấu hình, **When** mở phần cấu hình kế hoạch, **Then** thấy “Người tạo/duyệt” và hai nhóm lựa chọn tạo, duyệt phân biệt.
2. **Given** cấu hình hợp lệ cho một loại hồ sơ, **When** lưu và mở lại, **Then** giữ đúng vai trò tạo và thứ tự vai trò duyệt của loại đó, không ghi đè cấu hình loại khác.
3. **Given** Phó HT hoặc người không phải Hiệu trưởng có phân công còn hiệu lực, **When** cố thay đổi cấu hình người tạo/duyệt kế hoạch giáo dục, **Then** bị từ chối và cấu hình không đổi; Phó HT không có quyền cấu hình phần này.
4. **Given** một vai trò đã nằm trong chuỗi duyệt của loại hồ sơ, **When** chọn cùng vai trò vào nhóm soạn của loại đó, **Then** bị từ chối; các loại hồ sơ khác có cấu hình độc lập.
5. **Given** hồ sơ đang chờ duyệt, **When** Hiệu trưởng lưu chuỗi duyệt mới cho loại đó, **Then** hồ sơ chuyển ngay sang chuỗi mới, giữ lịch sử/kết quả đã duyệt; nếu mọi bước của chuỗi mới đều đã có kết quả duyệt hợp lệ thì hồ sơ hoàn tất, nếu còn bước thiếu thì tiếp tục chờ theo chuỗi mới.

6. **Given** cấu hình thiếu nhóm soạn/duyệt hoặc lặp vai trò, **When** lưu, **Then** bị từ chối và cấu hình/hồ sơ giữ nguyên (FR-012).
7. **Given** nâng cấp hệ thống, **When** Hiệu trưởng mở cấu hình lần đầu, **Then** thấy cấu hình khởi đầu theo FR-015 và có thể thay đổi.

### User Story 2 - Thực hiện đúng trách nhiệm được cấu hình (Priority: P1)

Người dùng được tạo hoặc duyệt hồ sơ theo vai trò đang có hiệu lực, trong phạm vi phân công của mình. Chỉ ba trạng thái nghiệp vụ: Nháp → Duyệt (đang duyệt) → Ban hành; người tới lượt nằm trong thông tin luồng, không tách PENDING theo vai trò.

**Why this priority**: Cấu hình phải quyết định hành vi thực tế, không chỉ thay nhãn.

**Independent Test**: Với cùng một hồ sơ thử người đúng vai trò/phạm vi, người thiếu vai trò, người ngoài phạm vi và người không đúng lượt.

**Acceptance Scenarios**:

1. **Given** người đủ vai trò tạo và đúng phạm vi, **When** tạo hồ sơ, **Then** được ghi nhận là người tạo thực tế.
2. **Given** người không được cấu hình tạo hoặc ngoài phạm vi, **When** tạo hồ sơ, **Then** bị từ chối và không phát sinh hồ sơ.
3. **Given** hồ sơ ở trạng thái Duyệt, **When** người có vai trò/phân công hợp lệ, đúng lượt và không tham gia soạn/sửa duyệt, **Then** còn bước thì giữ Duyệt và chuyển người tới lượt; bước cuối xong thì Ban hành.
4. **Given** người không đúng lượt hoặc ngoài phạm vi, **When** duyệt/trả lại, **Then** bị từ chối và hồ sơ không thay đổi.
5. **Given** người có vai trò soạn đúng phạm vi và không đồng thời có quyền duyệt loại đó, **When** tạo/soạn/sửa/gửi hồ sơ, **Then** chỉ được sửa nội dung khi Nháp; trạng thái Duyệt hoặc Ban hành không cho soạn/sửa. Gửi chuyển Nháp sang Duyệt và đóng phiên sửa.
6. **Given** bước đang chờ không có người hiệu lực đủ điều kiện tách biệt với người soạn, **When** xác định lượt duyệt, **Then** bỏ qua bước nếu còn bước sau; nếu bước sau cũng thiếu người thì tiếp tục xét, còn bước cuối thiếu người thì giữ chờ, không tự hoàn tất.
7. **Given** còn người đúng vai trò/phạm vi và không tham gia soạn/sửa, **When** xác định lượt duyệt, **Then** giữ bước đó cho người hợp lệ, không bỏ bước chỉ vì một người khác ở bước đó đã tham gia soạn.

### User Story 3 - Ngăn tự duyệt và giữ lịch sử (Priority: P1)

Người quản lý muốn có người khác kiểm tra hồ sơ và giữ được bằng chứng về người đã thao tác.

**Why this priority**: Đây là điều kiện user đã yêu cầu rõ.

**Independent Test**: Người tạo và người cùng soạn/sửa thử duyệt; người duyệt thử soạn/sửa; tất cả bị chặn ở chiều không được phép, người khác đủ điều kiện được thực hiện.

**Acceptance Scenarios**:

1. **Given** người đã tạo/soạn/sửa nội dung, **When** duyệt hồ sơ đó, **Then** bị chặn dù đổi vai trò sử dụng; hồ sơ không được tự ban hành.
2. **Given** hồ sơ đã có lịch sử duyệt, **When** cấu hình đổi, **Then** giữ lịch sử người tạo/người duyệt và kết quả đã ghi, không yêu cầu duyệt lại phần đã duyệt. Hồ sơ đã ban hành giữ nguyên kết quả; hồ sơ đang chờ chỉ xử lý các bước còn thiếu theo thứ tự chuỗi mới, vai trò đã duyệt và còn trong chuỗi mới không phải duyệt lại, vai trò bị bỏ khỏi chuỗi chỉ giữ trong lịch sử.
3. **Given** giáo án tuần/ngày thuộc hồ sơ chủ đề, **When** hồ sơ được duyệt, **Then** các giáo án đi theo kết quả hồ sơ, không phát sinh yêu cầu gửi riêng.
4. **Given** người có quyền duyệt một loại hồ sơ, **When** tạo/soạn/sửa/gửi bất kỳ hồ sơ nào thuộc loại đó, **Then** bị chặn kể cả chưa từng duyệt hồ sơ đó hoặc có thêm vai trò soạn; các loại hồ sơ khác kiểm theo cấu hình riêng.

5. **Given** bản đang Duyệt được trả lại, **When** người duyệt nhập lý do và trả lại, **Then** về Nháp để sửa; lý do nằm trong lịch sử, không có trạng thái REJECTED riêng. Sửa/gửi lại hoặc phiên bản mới bắt đầu duyệt từ đầu; lịch sử vòng cũ giữ nguyên.
7. **Given** hồ sơ Ban hành có ô ký/vai trò/tên đã lưu, **When** cấu hình hoặc tên tài khoản thay đổi, **Then** bản in hồ sơ đó vẫn giữ snapshot lúc ban hành; hồ sơ mới dùng dữ liệu của lần ban hành mới.
6. **Given** hồ sơ trước nâng cấp thiếu coauthor, **When** kiểm quyền, **Then** dùng người tạo đã biết và người cùng soạn được ghi từ nâng cấp; thông tin trả về nêu rõ giới hạn lịch sử (FR-014).

### Edge Cases

- Người tạo hoặc cùng soạn/sửa có nhiều vai trò: vẫn không được duyệt nội dung mình tham gia soạn.
- Cùng một vai trò không được chọn cả soạn và duyệt trong cùng loại hồ sơ. Một người có nhiều vai trò, đồng thời khớp vai trò soạn và vai trò duyệt của loại đó, không được tạo/soạn/sửa/gửi loại đó; không thể chọn vai trò khác để vượt hạn chế.
- Người duyệt hết phân công/khóa tài khoản hoặc chỉ còn người đã soạn: bỏ bước nếu còn bước sau; bước cuối thiếu người thì giữ chờ, không tự duyệt.
- Cấu hình mỗi loại phải có ít nhất một vai trò soạn và một vai trò duyệt; không trùng vai trò trong chuỗi, không giao nhau giữa nhóm soạn/duyệt; cấu hình vi phạm bị từ chối mà không đổi cấu hình/hồ sơ.
- Hồ sơ đã gửi trước khi cấu hình đổi: áp dụng ngay chuỗi mới, giữ kết quả đã duyệt và không yêu cầu duyệt lại phần đã duyệt. Nếu mọi bước trong chuỗi mới đều đã được duyệt hợp lệ thì hồ sơ hoàn tất ngay theo kết quả đã có. Thiếu người thực hiện một bước chưa duyệt không được coi là bước đó đã duyệt; bước cuối chưa duyệt mà thiếu người vẫn giữ chờ theo FR-011.
- Hồ sơ cũ thiếu danh sách người cùng soạn: dùng người tạo đã biết làm mốc; theo dõi đầy đủ người cùng soạn từ lúc nâng cấp, công bố giới hạn lịch sử, không đoán từ người cập nhật gần nhất.
- Bản bị trả lại rồi sửa nội dung/gửi lại hoặc tạo phiên bản mới: duyệt lại từ đầu theo chuỗi hiện tại, giữ lịch sử vòng cũ nhưng không tính kết quả vòng cũ cho nội dung mới. Việc chỉ đổi cấu hình mà không sửa nội dung vẫn giữ bước đã duyệt theo FR-009.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Phần cấu hình trách nhiệm phải có tên “Người tạo/duyệt”, phân biệt rõ lựa chọn tạo và duyệt (AC-01).
- **FR-002**: Chỉ Hiệu trưởng có phân công còn hiệu lực được lưu cấu hình vai trò tạo/duyệt theo từng loại hồ sơ thuộc kế hoạch giáo dục. Phó HT và các vai trò khác không có quyền cấu hình phần này; kiểm tra tại server, không chỉ ẩn nút. Cấu hình này không cấp hoặc sửa quyền cho module khác (US1.1/2/3).
- **FR-003**: Lựa chọn dựa trên vai trò, không yêu cầu chọn tài khoản cụ thể; thứ tự vai trò duyệt phải được lưu và thể hiện đúng (AC-01).
- **FR-004**: Tạo mới phải đồng thời thỏa cấu hình vai trò tạo và phạm vi phân công hợp lệ; không tự cấp quyền truy cập mọi lớp (US2.1/2).
- **FR-005**: Duyệt/trả lại phải đúng bước, đúng vai trò hiệu lực và phạm vi; trả lại cần lý do (US2.3/4).
- **FR-006**: Tách người soạn/người duyệt theo từng loại hồ sơ. Không cho cùng một vai trò xuất hiện ở cả nhóm soạn và chuỗi duyệt của cùng loại. Người có quyền duyệt loại đó không được tạo/soạn/sửa/gửi bất kỳ hồ sơ nào thuộc loại đó, kể cả có vai trò soạn khác hoặc chưa thực hiện duyệt. Người đã trực tiếp soạn/sửa không được duyệt nội dung mình tham gia soạn. Không thể đổi vai trò để vượt kiểm tra; từ chối không đổi hồ sơ (US1.4, US3.1/4).
- **FR-007**: Chính sách mới áp dụng cả kế hoạch năm, chủ đề nhánh và hồ sơ chủ đề; giáo án tuần/ngày duyệt cùng hồ sơ chủ đề (US2.3, US3.3).
- **FR-008**: Lưu danh tính người tạo, những người trực tiếp soạn/sửa, người duyệt và thời điểm thực hiện để kiểm tra tách trách nhiệm; thay cấu hình không sửa lại lịch sử đã có (US2.1, US3.2).
- **FR-009**: Khi Hiệu trưởng lưu thay đổi cấu hình, hồ sơ đang chờ duyệt của loại đó chuyển ngay sang chuỗi mới, giữ nguyên lịch sử và kết quả đã ghi. Vai trò đã duyệt còn trong chuỗi mới không phải duyệt lại; chỉ xử lý bước còn thiếu theo thứ tự mới. Vai trò bị bỏ khỏi chuỗi chỉ giữ trong lịch sử. Hồ sơ đã ban hành giữ nguyên, không mở lại. Nếu mọi bước trong chuỗi mới đều đã có kết quả duyệt hợp lệ thì hồ sơ hoàn tất theo kết quả đã có, không yêu cầu người duyệt cuối xác nhận lại. Không tạo sự kiện giả rằng một người vừa duyệt bước bị bỏ hoặc duyệt lại (US1.5, US3.2).
- **FR-010**: Cho chọn một hoặc nhiều vai trò soạn trong Giáo viên, Tổ trưởng, Phó HT, HT, không trùng vai trò duyệt của loại đó. Người soạn đúng phạm vi chỉ sửa khi Nháp; Duyệt và Ban hành không được sửa nội dung hoặc lấy phiên sửa. Khi thay cấu hình làm mất quyền soạn, thu hồi phiên đang giữ cả ở bản nháp/đang mở và tăng revision để tab cũ bị từ chối. Người có quyền duyệt cùng loại không soạn dù có nhiều vai trò (US2.5, AC-06).
- **FR-011**: Nếu bước duyệt không còn người hiệu lực, đúng phạm vi và đáp ứng tách người soạn/người duyệt thì bỏ qua bước khi còn bước sau; tiếp tục xét tương tự qua các bước sau, nhưng bước cuối thiếu người phải giữ chờ. Nếu còn người hợp lệ ở bước hiện tại thì không bỏ bước. Việc bỏ bước không được ghi thành một người đã duyệt hoặc tự ban hành (US2.6/7, AC-06).
- **FR-012**: Mỗi loại hồ sơ phải có ít nhất một vai trò soạn và một vai trò duyệt; không lặp vai trò trong chuỗi, không giao nhóm soạn/duyệt. Lưu cấu hình không hợp lệ phải bị từ chối và không đổi hồ sơ (US1.4/6).
- **FR-013**: Trả lại từ Duyệt về Nháp phải có lý do trong lịch sử, không có trạng thái REJECTED riêng. Sửa/gửi lại và phiên bản mới bắt đầu vòng duyệt mới từ đầu theo chuỗi hiện tại; lịch sử cũ được giữ, kết quả vòng cũ không tính cho vòng mới. Phiên bản mới bắt đầu Nháp; chỉ đổi cấu hình mà không sửa nội dung giữ approvals cùng vòng (US3.5).
- **FR-014**: Với hồ sơ tồn tại trước nâng cấp, dùng người tạo đã biết làm mốc cấm tự duyệt; chỉ ghi nhận đầy đủ người cùng soạn từ lúc nâng cấp. Không suy đoán người soạn từ tên hoặc người cập nhật cuối; công bố giới hạn lịch sử. Hồ sơ mới theo dõi đầy đủ từ khi tạo (US3.6).
- **FR-015**: Cấu hình khởi đầu: năm = Tổ trưởng soạn, Phó HT duyệt; chủ đề nhánh và hồ sơ chủ đề = GV soạn, Tổ trưởng rồi Phó HT duyệt. Hiệu trưởng được thay đổi cấu hình sau nâng cấp. Không sao chép mù quáng cấu hình người ký cũ thành quyền soạn/duyệt (US1.7).

- **FR-016**: Chỉ ba trạng thái hiển thị và nghiệp vụ: Nháp, Duyệt (đang duyệt), Ban hành. Luồng nhiều bước dùng người/vai trò tới lượt trong metadata, không tạo trạng thái PENDING theo vai trò; khóa Đang sửa là thông tin phiên độc lập, không là trạng thái thứ tư. Duyệt bước cuối hợp lệ hoặc hoàn tất theo kết quả cũ được phép thì Ban hành.
- **FR-017**: Khi Ban hành, lưu riêng snapshot các ô ký gồm vai trò/nhãn/tên người theo hồ sơ. Bản đã ban hành không lấy lại ô ký từ cấu hình/tài khoản mới. Legacy dùng snapshot baseline đã công bố giới hạn; không giả dựng người đã ký/duyệt.
- **FR-018**: Phạm vi implementation chỉ module kế hoạch giáo dục và các thành phần dành riêng cho module đó; không đổi nghiệp vụ hoặc API module khác. User cho phép riêng ngày 06/11: helper chỉ đọc `AccountService.findActiveUserIds` và `StaffAssignmentService.findActiveEducationReviewerAssignments` cùng test để lọc tài khoản và phân công reviewer còn hiệu lực; đổi riêng fixture `DailyAssessmentIntegrationTest` từ `PENDING_VP` sang `REVIEW` để phù hợp ba trạng thái. Không mở rộng thêm source hoặc nghiệp vụ module khác.

### Key Entities

- **Chính sách tạo/duyệt**: loại hồ sơ, tập vai trò tạo, các vai trò duyệt có thứ tự; hai nhóm vai trò không giao nhau trong cùng loại.
- **Hồ sơ giáo dục**: loại, phạm vi, người tạo thực tế, những người trực tiếp soạn/sửa, trạng thái và quy trình duyệt áp dụng.
- **Phân công hiệu lực**: căn cứ vai trò và phạm vi của một người tại lúc thao tác.
- **Sự kiện duyệt**: người thao tác, vai trò/bước, thời điểm, kết quả và lý do khi trả lại.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Cả ba nhóm hồ sơ (năm, chủ đề nhánh, hồ sơ chủ đề) lưu/mở lại đúng 100% vai trò tạo và thứ tự duyệt trong các ca nghiệm thu.
- **SC-002**: 100% ca tạo sai vai trò/phạm vi và duyệt sai lượt/phạm vi bị chặn; không phát sinh thay đổi trái phép.
- **SC-003**: 100% ca cấu hình trùng vai trò soạn/duyệt cùng loại bị từ chối; 100% ca người soạn đi duyệt nội dung mình soạn và người có quyền duyệt đi tạo/soạn/sửa/gửi cùng loại bị chặn, kể cả có nhiều vai trò; người khác đủ điều kiện thực hiện đúng luồng.
- **SC-004**: 100% hồ sơ lịch sử dùng trong nghiệm thu giữ nguyên người và kết quả đã ghi sau thay đổi cấu hình.
- **SC-005**: Người quản lý hoàn thành cấu hình, lưu và mở lại cho từng loại hồ sơ mà không cần chọn tài khoản cá nhân trong kịch bản nghiệm thu.
- **SC-006**: 100% ca thiếu người ở bước giữa chuyển đúng bước kế tiếp; ca thiếu người ở bước cuối vẫn chờ và không tự ban hành, kể cả khi mọi bước đều thiếu người.
- **SC-007**: 100% ca cấu hình người tạo/duyệt bởi Phó HT hoặc người không phải Hiệu trưởng hiệu lực bị từ chối; Hiệu trưởng hiệu lực lưu được cấu hình hợp lệ. Không có thay đổi quyền ở module khác.
- **SC-009**: 100% ca nghiệm thu chỉ có Nháp/Duyệt/Ban hành; mọi content write/lấy lease khi Duyệt bị chặn kể cả người soạn, bước cuối xong thì Ban hành và bản in giữ tên/vai trò đã chụp.
- **SC-008**: 100% ca đổi chuỗi làm mọi bước của chuỗi mới đều đã được duyệt hợp lệ thì hồ sơ hoàn tất, không yêu cầu duyệt lại và không tạo lịch sử duyệt giả; ca còn bước chưa duyệt tiếp tục theo chuỗi mới.

## Assumptions

- Chỉ Hiệu trưởng được cấu hình người tạo/duyệt của kế hoạch giáo dục, theo chỉ đạo bổ sung của user 06/11/2026; thay giả định ban đầu HT/PHT cùng quản lý. Quyền cấu hình không mặc nhiên cho phép soạn hoặc duyệt; hai quyền đó phụ thuộc cấu hình của từng loại hồ sơ.
- Không mở rộng phạm vi phân công hoặc quyền chỉ đọc ngoài tác vụ. Người được cấu hình soạn/duyệt cần đọc hồ sơ tương ứng trong cùng scope để thực hiện tác vụ; delta trạng thái được xem so với EDU-06 phải được duyệt cùng plan. Không mở rộng sang module khác.
- User đã chọn chuyển hồ sơ đang chờ sang chuỗi mới ngay khi đổi cấu hình, thay giả định ban đầu giữ chuỗi đã chụp theo EDU-18. Cấu hình không rỗng/không trùng đã được user chốt trong bước plan.
- EDU-02/04/05/06/08/11/18 hiện hành khác một phần yêu cầu mới; EDU-54 được ghi riêng ở trạng thái PROPOSED, không ghi đè rule đã xác nhận.
- Constitution 1.0.0 đáp ứng nguyên tắc hiện hành, không cần đổi. Không có extensions.yml ở lúc chạy; không có hook trước/sau cần thực thi.
- Ảnh hưởng xác định khi specify: Web cấu hình, nút/quyền và luồng năm/lớp; Mobile cần đánh giá consumer giáo dục khi lập plan. FE giáo dục sau đó được user cho phép sửa và kiểm chứng; user xác nhận APP chưa tích hợp kế hoạch giáo dục.

## Clarifications

### Session 2026-11-06

- Cấu hình theo vai trò, không theo tài khoản cụ thể; áp dụng cả kế hoạch năm và hồ sơ lớp.
- Quyết định ban đầu cấm người tạo tự duyệt được mở rộng bởi câu trả lời clarify dưới đây; không chỉ kiểm người tạo mới.
- User đồng ý đề xuất chọn một/nhiều vai trò tạo trong GV, Tổ trưởng, Phó HT, HT; quyền tạo bao gồm tạo/soạn/sửa/gửi trong phạm vi phân công.
- User chọn bỏ bước thiếu người hợp lệ khi còn bước khác; bước cuối thiếu người giữ chờ.
- Đã dùng 5 câu hỏi trong clarify. Các điểm hoãn về dữ liệu cũ, gửi lại/phiên bản mới và cấu hình rỗng đã được user trả lời trong bước plan bên dưới. Ở thời điểm clarify chưa phê duyệt code; user sau đó gọi `$speckit-implement` và tiếp tục chỉ đạo chạy implement/converge đến khi sạch.
- Q: Khi nhiều người cùng soạn một hồ sơ, những ai bị cấm duyệt hồ sơ đó? → A: “người soạn thì không duyệt và người duyệt thì sẽ không được soạn nữa” (user 06/11/2026); tách hai chiều, bao gồm người cùng soạn/sửa, không chỉ người tạo mới. Phạm vi được chốt theo loại hồ sơ ở câu tiếp theo.
- Q: Bạn muốn tách người soạn và người duyệt ngay trong cấu hình từng loại hồ sơ, hay chỉ tách trên từng hồ sơ cụ thể? → A: Theo loại hồ sơ (user 06/11/2026): vai trò đã chọn duyệt không được chọn soạn trong cùng loại; người có quyền duyệt loại đó không được soạn loại đó, kể cả có nhiều vai trò. Loại khác áp dụng cấu hình riêng.
- Q: Khi thay cấu hình người soạn/duyệt, hồ sơ đang chờ duyệt sẽ theo chuỗi cũ hay chuyển sang chuỗi mới? → A: B — chuyển ngay sang chuỗi mới, giữ nguyên lịch sử đã duyệt (user 06/11/2026). Không tự ban hành; phần đã duyệt được giữ theo câu trả lời tiếp theo.
- Q: Khi hồ sơ chuyển sang chuỗi mới, các vai trò đã duyệt trước đó có phải duyệt lại không? → A: User làm rõ: “đã duyệt thì để nguyên k cần duyệt lại gì cả” (06/11/2026). Giữ kết quả đã duyệt, hồ sơ đã hoàn tất không bị mở lại; hồ sơ đang chờ chỉ xử lý các bước còn thiếu trong chuỗi mới, không yêu cầu duyệt lại các bước đã duyệt còn trong chuỗi.
- Q: Nếu cấu hình mới chỉ còn Tổ trưởng đã duyệt, hồ sơ đang chờ Phó HT sẽ hoàn tất luôn hay chờ xác nhận kết thúc? → A: A — hồ sơ hoàn tất theo kết quả duyệt đã có, không cần xác nhận hoặc duyệt lại (user 06/11/2026). Thay giới hạn ban đầu không tự hoàn tất khi đổi cấu hình; không áp dụng cho bước còn chưa duyệt nhưng thiếu người.
- Chỉ đạo bổ sung của user 06/11/2026: cấu hình này chỉ dành cho kế hoạch giáo dục và chỉ Hiệu trưởng được cấu hình; Phó HT không có quyền cấu hình người tạo/duyệt. Không mở rộng thành ma trận phân quyền cho module khác.
- Planning decisions 06/11/2026 — user xác nhận: ít nhất một vai trò soạn và một vai trò duyệt cho mỗi loại, không lặp vai trò duyệt; sửa nội dung/gửi lại sau trả lại hoặc tạo phiên bản mới thì duyệt từ đầu; hồ sơ cũ dùng người tạo đã biết làm mốc và ghi rõ giới hạn coauthor trước nâng cấp; khởi đầu năm Tổ trưởng soạn → PHT duyệt, hồ sơ lớp GV soạn → Tổ trưởng → PHT duyệt.

- User sau analyze06/11/2026: người soạn không sửa hồ sơ đang duyệt; chỉ Nháp → Duyệt → Ban hành, Duyệt là đang duyệt và bước cuối xong thì Ban hành. User đồng ý lưu riêng ô ký/vai trò/tên theo hồ sơ lúc ban hành; chỉ tập trung module giáo dục. Trả lại để sửa là nghiệp vụ đã có; với đúng ba trạng thái mới, thiết kế ánh xạ trả lại về Nháp và giữ lý do/history, không thêm REJECTED riêng.

- Sau báo cáo analyze mới nhất, user gọi fix và cho phép đúng helper chỉ đọc AccountService.findActiveUserIds(Collection<Long> userIds), không mở rộng nghiệp vụ module khác. Sửa thứ tự bằng expand/contract nội bộ; không thay ba trạng thái phát hành đã chốt, không tự duyệt implementation.
