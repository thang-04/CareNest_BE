# Feature Specification: Kho lưu tệp dùng chung trên S3 (ảnh, tài liệu, học liệu)

**Feature Branch**: `017-s3-file-storage`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "Cấu hình AWS S3 làm nơi lưu trữ file (ảnh, tài liệu) cho toàn bộ hệ thống CareNest (Web và App dùng chung một backend). Hệ thống đã có cấu hình AWS sẵn. Folder theo loại file, giới hạn dung lượng trong cấu hình, quy tắc đặt tên, kiểm MIME, bucket private + pre-signed URL ~15 phút (xem/tải và upload trực tiếp), kiểm quyền trước khi cấp URL, IAM riêng, key trong biến môi trường, CORS Web/App, DB chỉ lưu key, xóa/thay thì xóa file cũ, lifecycle imports/ 7 ngày. Thêm folder hoclieu/ cho video, slide, file theo giáo án. Report 3 chưa có bản mới: cấu hình theo mô tả, phần thiếu theo code hiện có."

## Hiện trạng (đối chiếu code 12/11/2026)

- Đã có một client S3 dùng chung, region/endpoint/bucket đọc từ biến môi trường, credential lấy theo chuỗi mặc định (không nằm trong source).
- Đã có các vùng lưu: học liệu (`education/learning-resources/`), ảnh tài khoản (`accounts/photos/`), ảnh trẻ (`children/photos/`),
  chữ ký tài khoản (`accounts/signatures/`), bản sao chữ ký trên biên bản kiểm thực (`meals/inspection-signatures/`), ảnh món ăn.
- Tên đối tượng hiện là chuỗi ngẫu nhiên (UUID), tệp đi qua backend (client gửi lên backend, backend ghi S3; đọc cũng qua backend).
- Giới hạn dung lượng và định dạng đang viết cứng rải rác ở từng module (vd. học liệu 100 MB tối đa; ảnh 2 MB).

Feature này gom về một quy tắc chung; không đổi nghiệp vụ của từng module đang dùng.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tải tệp lên đúng chỗ, đúng giới hạn (Priority: P1)

Người dùng (BGH, giáo viên, phụ huynh, cấp dưỡng) tải ảnh/tài liệu ở bất kỳ màn hình nào có chỗ tải lên (Web hoặc App). Hệ thống
xác định loại tệp theo mục đích (ảnh tài khoản, ảnh trẻ, ảnh người đón, ảnh món ăn, học liệu…), chỉ nhận đúng định dạng và
dung lượng của loại đó, đặt tên theo quy tắc chung và cất vào đúng thư mục.

**Why this priority**: Là nền cho mọi chỗ tải tệp; sai giới hạn hoặc nhận nhầm định dạng gây lỗi dữ liệu và rủi ro bảo mật.

**Independent Test**: Tải một ảnh món ăn 4 MB (jpg) → thành công, nằm trong `meals/` với tên đúng mẫu; tải ảnh 6 MB → bị từ chối
với thông báo nêu giới hạn 5 MB; tải tệp `.exe` đổi đuôi thành `.jpg` → bị từ chối.

**Acceptance Scenarios**:

1. **Given** người dùng có quyền tải ảnh món ăn, **When** tải ảnh png 3 MB, **Then** tệp được lưu trong `meals/` với tên dạng
   `meal_<mã>_<yyyyMMdd-HHmmss>_<4 ký tự>.png`, không chứa tên gốc của người dùng.
2. **Given** loại tệp ảnh tài khoản giới hạn 2 MB, **When** tải ảnh 2,5 MB, **Then** bị từ chối trước khi lưu, nhận mã lỗi
   và thông báo "Tệp vượt quá dung lượng cho phép (tối đa 2 MB)".
3. **Given** một tệp có đuôi `.pdf` nhưng nội dung không phải PDF, **When** tải vào kế hoạch chủ đề, **Then** bị từ chối với
   thông báo định dạng không hợp lệ, không có tệp nào còn lại trong kho.
4. **Given** hai người tải cùng loại, cùng đối tượng, cùng giây, **When** cả hai thành công, **Then** hai tệp có tên khác nhau,
   không ghi đè nhau.

---

### User Story 2 - Xem/tải tệp an toàn qua liên kết có hạn (Priority: P1)

Người dùng mở ảnh, tài liệu hoặc học liệu. Hệ thống kiểm tra người đó có quyền xem đối tượng chứa tệp (vd. phụ huynh chỉ xem ảnh
của con mình; giáo viên xem học liệu giáo án được phép xem), rồi mới cấp liên kết tạm (khoảng 15 phút). Kho lưu không mở công khai.

**Why this priority**: Ảnh trẻ em, ảnh phụ huynh dùng đối chiếu đón trẻ là dữ liệu nhạy cảm; lộ ra ngoài là sự cố nghiêm trọng.

**Independent Test**: Phụ huynh A xin liên kết ảnh của con mình → nhận được và mở được; xin liên kết ảnh trẻ khác → bị từ chối;
mở liên kết sau 15 phút → hết hạn; truy cập thẳng địa chỉ kho không kèm chữ ký → bị chặn.

**Acceptance Scenarios**:

1. **Given** người dùng có quyền xem đối tượng, **When** xin xem tệp, **Then** nhận liên kết có hạn khoảng 15 phút, mở được tệp.
2. **Given** người dùng không có quyền với đối tượng, **When** xin liên kết, **Then** bị từ chối, không lộ đường dẫn tệp.
3. **Given** liên kết đã quá hạn, **When** mở lại, **Then** bị từ chối; người dùng xin liên kết mới qua hệ thống.
4. **Given** bất kỳ ai, **When** truy cập trực tiếp tệp trong kho mà không có liên kết hợp lệ, **Then** bị chặn.

---

### User Story 3 - Tải trực tiếp tệp lớn (học liệu video) từ Web/App (Priority: P2)

Giáo viên tải video/slide/tài liệu học liệu theo giáo án (folder `hoclieu/`). Tệp đi thẳng từ trình duyệt/App vào kho qua
liên kết tải lên có hạn do hệ thống cấp sau khi kiểm quyền, định dạng khai báo và dung lượng khai báo; sau khi tải xong hệ thống
xác nhận lại nội dung thật trước khi gắn tệp vào giáo án.

**Why this priority**: Video tới hàng chục MB; đi qua backend làm chậm và tốn tài nguyên. Ảnh nhỏ vẫn có thể đi qua backend.

**Independent Test**: Xin liên kết tải video mp4 80 MB cho một giáo án mình được sửa → nhận liên kết; tải lên → xác nhận → học
liệu xuất hiện trong giáo án. Xin liên kết cho video 150 MB → bị từ chối ngay. Tải lên một tệp khác loại rồi xác nhận → bị từ
chối và tệp bị xóa khỏi kho.

**Acceptance Scenarios**:

1. **Given** giáo viên có quyền sửa giáo án, **When** xin liên kết tải lên video mp4 khai báo 80 MB, **Then** nhận liên kết tải
   lên có hạn cho đúng một tên tệp trong `hoclieu/`, chỉ chấp nhận đúng loại và dung lượng đã khai báo.
2. **Given** tệp đã tải qua liên kết, **When** người dùng xác nhận, **Then** hệ thống kiểm lại dung lượng và nội dung thật; đúng
   thì gắn key vào giáo án, sai thì xóa tệp và báo lỗi.
3. **Given** liên kết tải lên đã cấp nhưng không bao giờ được xác nhận, **When** quá thời hạn dọn, **Then** tệp mồ côi bị xóa.

---

### User Story 4 - Thay/xóa tệp không để rác (Priority: P2)

Khi người dùng thay ảnh đại diện, thay học liệu hoặc xóa đối tượng có tệp, tệp cũ trong kho bị xóa theo; tệp nhập Excel tạm tự
biến mất sau 7 ngày.

**Why this priority**: Tránh tốn chi phí lưu và tránh dữ liệu cá nhân cũ (ảnh trẻ, ảnh phụ huynh) tồn tại sau khi đã thay.

**Independent Test**: Thay ảnh tài khoản → ảnh cũ không còn trong kho; tải tệp vào `imports/` → sau 7 ngày tự mất.

**Acceptance Scenarios**:

1. **Given** tài khoản đã có ảnh, **When** thay ảnh mới thành công, **Then** ảnh cũ bị xóa khỏi kho; nếu lưu ảnh mới thất bại thì
   ảnh cũ giữ nguyên.
2. **Given** đối tượng có tệp đính kèm bị xóa, **When** xóa thành công, **Then** tệp của nó cũng bị xóa khỏi kho.
3. **Given** tệp trong `imports/`, **When** đủ 7 ngày, **Then** kho tự xóa, không cần thao tác.

---

### User Story 5 - Lập trình viên module dùng lại dịch vụ chung (Priority: P3)

Module mới (vd. ảnh người đón, ảnh lưu mẫu thức ăn) chỉ cần khai báo loại tệp và kiểm quyền của mình, gọi dịch vụ lưu tệp chung
để tải lên, cấp liên kết xem, thay, xóa; có ghi chú cách dùng.

**Why this priority**: Giảm lặp code và giữ quy tắc thống nhất; không trực tiếp tạo giá trị cho người dùng cuối.

**Independent Test**: Thêm một loại tệp mới chỉ bằng cấu hình + một dòng khai báo; tải lên/xem/xóa chạy được mà không viết lại
logic lưu trữ.

### Edge Cases

- Kho chưa cấu hình (thiếu bucket/credential trên máy dev): mọi thao tác tệp trả lỗi rõ "Chưa cấu hình kho lưu tệp", các chức
  năng không dùng tệp vẫn chạy.
- Kho tạm thời lỗi/mất mạng: báo "Không lưu được tệp, vui lòng thử lại sau", không ghi key vào dữ liệu; tệp đã ghi dở được dọn.
- Tệp rỗng (0 byte): từ chối.
- Đuôi và nội dung thật lệch nhau (png đổi đuôi jpg): nhận nếu nội dung thuộc định dạng cho phép của loại đó, lưu theo đuôi đúng
  với nội dung thật.
- Khai báo dung lượng khi xin liên kết tải lên nhỏ hơn tệp thật: kho từ chối hoặc bị phát hiện khi xác nhận, tệp bị xóa.
- Xóa tệp cũ thất bại sau khi thay thành công: không làm hỏng thao tác thay; tệp cũ được ghi nhận để dọn sau.
- Key lưu trong dữ liệu không đúng mẫu (bị sửa tay): từ chối, không cho truy cập ngoài thư mục của loại tệp.
- Người dùng mất quyền sau khi đã nhận liên kết: liên kết đã cấp còn dùng được tới khi hết hạn (tối đa ~15 phút) — chấp nhận.

## Requirements *(mandatory)*

### Functional Requirements

**Loại tệp và thư mục**

- **FR-001**: Hệ thống MUST lưu mỗi loại tệp vào một thư mục riêng trong một kho private chung theo bảng sau (giá trị mặc định,
  đặt trong cấu hình):

  | Thư mục | Nội dung | Tối đa | Định dạng |
  |---|---|---|---|
  | `avatars/users/` | Ảnh tài khoản BGH, GV, PH (ảnh PH dùng đối chiếu khi đón trẻ) | 2 MB | jpg, png, webp |
  | `avatars/children/` | Ảnh hồ sơ trẻ | 2 MB | jpg, png, webp |
  | `pickups/` | Ảnh người đón khi giao trẻ (UC 56) | 3 MB | jpg, png, webp |
  | `plans/year-goals/` | File mục tiêu năm học đã ký | 10 MB | pdf, docx |
  | `plans/themes/` | File kế hoạch chủ đề đã ký | 10 MB | pdf, docx |
  | `plans/lessons/` | Giáo án / kế hoạch tuần, ngày đã ký | 10 MB | pdf, docx |
  | `meals/` | Ảnh món ăn | 5 MB | jpg, png, webp |
  | `food-samples/` | Ảnh lưu mẫu thức ăn | 5 MB | jpg, png, webp |
  | `imports/` | Excel nhập danh sách trẻ (UC 32), tệp tạm | 5 MB | xlsx |
  | `hoclieu/` | Học liệu theo giáo án: ảnh, video, slide, tài liệu | ảnh 5 MB, video 100 MB, tệp khác 20 MB | jpg, png, gif, webp, mp4, webm, pdf, pptx, docx, xlsx, txt, csv |
  | `signatures/users/` | Ảnh chữ ký tài khoản (SIG-01, đang có) | 2 MB | jpg, png, webp |
  | `signatures/inspections/` | Bản sao chữ ký trên biên bản kiểm thực đã ký (INS-03, đang có) | 2 MB | jpg, png, webp |

- **FR-002**: Giới hạn dung lượng và danh sách định dạng của từng loại MUST nằm trong tệp cấu hình, đổi được theo môi trường
  mà không sửa code. Thêm loại tệp mới MUST chỉ cần thêm cấu hình và khai báo loại.
- **FR-003**: Chỗ tải lên mới phát sinh (theo Report 3 bản sau) MUST thêm theo cùng quy tắc FR-001/FR-002.

**Kiểm tra khi tải lên**

- **FR-004**: Hệ thống MUST từ chối tệp vượt giới hạn của loại **trước khi** lưu vào kho, trả mã lỗi riêng và thông báo nêu giới
  hạn (vd. "Tệp vượt quá dung lượng cho phép (tối đa 5 MB)").
- **FR-005**: Hệ thống MUST chỉ nhận định dạng của loại; kiểm cả đuôi tệp, kiểu khai báo và nội dung thật (chữ ký đầu tệp), không
  chỉ dựa vào đuôi. Sai định dạng trả mã lỗi riêng và thông báo nêu các định dạng được phép. Tệp rỗng bị từ chối.
- **FR-006**: Tên tệp lưu MUST theo mẫu `<loại>_<mã đối tượng>_<yyyyMMdd-HHmmss>_<4 ký tự ngẫu nhiên [a-z0-9]>.<đuôi>`
  (vd. `meal_123_20261105-143012_a1b2.jpg`), giờ theo múi giờ trường; không dùng tên gốc của người dùng. Tên gốc (nếu module cần
  hiển thị) lưu riêng trong dữ liệu, không nằm trong key.
- **FR-007**: Nếu key sinh ra đã tồn tại (trùng ngẫu nhiên), hệ thống MUST sinh lại, không ghi đè.

**Truy cập và bảo mật**

- **FR-008**: Kho MUST private, chặn mọi truy cập công khai; mọi object chỉ đọc/ghi được bằng quyền của hệ thống hoặc liên kết có hạn.
- **FR-009**: Xem/tải tệp MUST qua liên kết có hạn (mặc định 15 phút, đặt trong cấu hình), chỉ cấp sau khi module sở hữu tệp kiểm
  tra người dùng có quyền với đối tượng chứa tệp. Không trả key thô cho client khi không cần.
- **FR-010**: Hệ thống MUST hỗ trợ tải lên trực tiếp từ Web/App qua liên kết tải lên có hạn: cấp sau khi kiểm quyền, loại tệp,
  kiểu khai báo và dung lượng khai báo; liên kết chỉ dùng cho đúng một key, đúng kiểu và dung lượng đã khai báo. Sau khi tải,
  client MUST xác nhận; hệ thống kiểm lại dung lượng và nội dung thật rồi mới gắn key vào dữ liệu, sai thì xóa tệp.
- **FR-011**: Tệp tải trực tiếp nhưng không được xác nhận MUST bị dọn tự động (không quá 1 ngày).
- **FR-012**: Truy cập kho MUST dùng một danh tính riêng của dự án chỉ có quyền đọc/ghi/xóa/liệt kê trên đúng kho của dự án; khóa
  truy cập chỉ nằm trong biến môi trường/kho bí mật, không nằm trong mã nguồn hay tệp cấu hình commit lên git.
- **FR-013**: Kho MUST cho phép tải lên/tải xuống trực tiếp chỉ từ các domain Web và App được khai báo trong cấu hình (CORS).
- **FR-014**: Key lưu trong dữ liệu MUST được kiểm đúng mẫu và đúng thư mục của loại trước khi dùng; key sai mẫu bị từ chối.

**Lưu trữ và dọn dẹp**

- **FR-015**: Dữ liệu nghiệp vụ MUST chỉ lưu key (đường dẫn tương đối trong kho), không lưu URL đầy đủ hay liên kết có hạn.
- **FR-016**: Khi thay tệp, hệ thống MUST lưu tệp mới thành công trước, rồi mới xóa tệp cũ; xóa đối tượng thì xóa tệp kèm theo.
  Xóa tệp cũ lỗi không làm thất bại thao tác chính.
- **FR-017**: Kho MUST tự xóa tệp trong `imports/` sau 7 ngày.
- **FR-018**: Tệp đã có trước feature (thư mục/tên cũ, vd. `education/learning-resources/<uuid>`, `accounts/photos/<uuid>`) MUST
  vẫn xem/thay/xóa được như cũ; chỉ tệp tải lên mới theo thư mục và tên mới. Thay tệp cũ ⇒ tệp mới theo quy tắc mới, tệp cũ bị xóa.
  Không chạy đợt chuyển hay xóa hàng loạt (User chốt 12/11/2026, bỏ phương án xóa toàn bộ tệp cũ).

**Học liệu và kế hoạch giáo dục**

- **FR-019**: Học liệu (`hoclieu/`) MUST gắn với hoạt động/giáo án như hiện có (feature 002); quyền xem/sửa học liệu theo quyền
  xem/sửa giáo án chứa nó, không đổi chuỗi duyệt/quyền/MT của kế hoạch giáo dục.
- **FR-020**: Các thư mục `plans/year-goals/`, `plans/themes/`, `plans/lessons/` MUST chỉ được khai báo sẵn trong cấu hình (loại,
  giới hạn, định dạng); feature này không thêm màn hình/API tải bản đã ký và không đổi luồng kế hoạch giáo dục, vì nghiệp vụ đã chốt
  "duyệt trong hệ thống, in file để ký tay, không bắt buộc tải bản đã ký" (Report 3; quyết định không ký điện tử). Muốn thêm tải bản
  ký tay (scan) để lưu trữ ⇒ chốt nghiệp vụ riêng.

**Dịch vụ dùng chung**

- **FR-021**: Hệ thống MUST cung cấp một dịch vụ lưu tệp dùng chung cho mọi module: tải lên (qua backend hoặc cấp liên kết tải
  trực tiếp + xác nhận), cấp liên kết xem/tải, thay, xóa; nhận vào loại tệp, mã đối tượng và nội dung/khai báo.
- **FR-022**: Dịch vụ MUST có ghi chú cách dùng (ví dụ cho một module mẫu, cách thêm loại tệp mới, các mã lỗi) trong tài liệu module.
- **FR-023**: Hướng dẫn dựng kho trên môi trường dev MUST có: tạo kho, chặn public, CORS, danh tính riêng + quyền tối thiểu,
  lifecycle `imports/` và dọn tệp chưa xác nhận, biến môi trường cần đặt.

### Key Entities

- **Loại tệp (File category)**: tên loại dùng trong tên tệp (vd. `meal`), thư mục, giới hạn dung lượng (có thể theo nhóm định
  dạng), danh sách định dạng cho phép. Nằm trong cấu hình.
- **Tệp lưu trữ (Stored object)**: key = thư mục + tên tệp theo FR-006; dữ liệu nghiệp vụ của từng module giữ key này (và tên gốc,
  kiểu, dung lượng nếu module cần hiển thị).
- **Phiếu tải lên chờ xác nhận (Pending upload)**: key đã cấp, loại, đối tượng, người xin, kiểu và dung lượng khai báo, hạn; hết hạn
  hoặc xác nhận sai thì tệp bị xóa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% tệp vượt giới hạn hoặc sai định dạng (kể cả đổi đuôi) bị từ chối với thông báo nêu giới hạn/định dạng được phép;
  không tệp nào bị từ chối còn lại trong kho.
- **SC-002**: 0 tệp xem được khi không có liên kết hợp lệ; liên kết hết hạn sau ~15 phút; người không có quyền với đối tượng không
  nhận được liên kết (kiểm trên mọi loại tệp).
- **SC-003**: Tải trực tiếp video học liệu 100 MB hoàn tất với thời gian chỉ phụ thuộc đường truyền người dùng, không bị giới hạn
  bởi backend.
- **SC-004**: Sau thay/xóa, tệp cũ không còn trong kho (trừ khi xóa thất bại và đã ghi nhận để dọn); `imports/` không còn tệp quá 7 ngày.
- **SC-005**: Thêm một loại tệp mới chỉ cần sửa cấu hình và một khai báo, không sửa logic lưu trữ.
- **SC-006**: Kho trên môi trường dev dựng được theo hướng dẫn trong dưới 30 phút; không có khóa truy cập nào trong lịch sử git.

## Assumptions

- Report 3 chưa có bản mới: dùng bảng thư mục theo mô tả; các vùng lưu đã có trong code nhưng không có trong mô tả (chữ ký tài
  khoản, chữ ký biên bản kiểm thực) được đưa vào bảng theo cùng quy tắc (User 12/11/2026).
- `hoclieu/` giữ giới hạn và định dạng đã chốt ở feature 002 (FR-004/FR-005): ảnh 5 MB, video 100 MB, tệp khác 20 MB.
- Dùng một kho chung cho Web và App (một backend), như cấu hình đang có; không tách kho theo môi trường trong một máy dev.
- Ảnh nhỏ (≤ 5 MB) được phép tiếp tục đi qua backend (backend kiểm nội dung trước khi ghi); tải trực tiếp bắt buộc cho học liệu
  lớn, tùy chọn cho các loại khác.
- Thời hạn liên kết tải lên trực tiếp mặc định 15 phút, như liên kết xem.
- Việc dựng tài nguyên cloud thật (tạo kho, danh tính, khóa) do người có tài khoản AWS thực hiện theo hướng dẫn; agent không tự
  tạo tài nguyên cloud hay ghi khóa vào repo.
- Module ảnh người đón (UC 56), ảnh lưu mẫu thức ăn, nhập Excel (UC 32) dùng dịch vụ chung khi các module đó làm; feature này chỉ
  khai báo loại tệp và thư mục cho chúng, không làm màn hình.
