# Feature009 — Nhập đầy đủ kế hoạch năm với hai chế độ thay nội dung

Nguồn hiện hành: domain-spec.md và user02/11/2026 duyệt kế hoạch hai chế độ/cập nhật tài liệu BE. Thay hành vi chỉ bổ sung đã duyệt trước đó. Implementation hai chế độ FE có evidence phase2, chờ review/converge cuối; giữ nguyên API/DB/APP, không commit/push/branch mới.

## User stories và acceptance
- US1 (P1): người soạn chọn thay toàn bộ hoặc chỉ thay phần file có, xem phần thay/xóa/giữ rồi xác nhận và Lưu. AC3/4/6/8/9.
- US2 (P1): giữ năm đích/khoảng ngày hệ thống và trường cố định, chặn lệch tuổi; lịch nguồn dời theo năm đích, cảnh báo và validation đúng. AC5/7.
- US3 (P2): giữ UX cards/header/controls đã chốt, tick mã ghép xuống dòng không tràn/cắt. AC1/2/10.

## Functional requirements
FR-01: cùng file formats/limits/reader preview, không ghi DB khi đọc hay apply.
FR-02: metadata hiện có title/năm nguồn/khoảng tuổi; không thêm trường hoặc đọc tài khoản/quyền từ file. Mô tả không có trong preview; phải hiển thị rõ hành vi theo chế độ.
FR-03: giữ phân cấp/lĩnh vực/nội dung/tick của dữ liệu nguồn hợp lệ, không lấy số MT làm ID.
FR-04: mã nguồn phải rõ hoặc ordinal duy nhất như parser đã triển khai, không tự suy mã theo tên; ambiguity không được ghép sai.
FR-05: lịch/nghỉ chỉ dựa preview có ngày rõ; không suy từ số tuần/lễ hội.
FR-06: preview và confirm dùng đúng chế độ mới nhất; patch thuần một lần sau kiểm quyền/lease, không đổi form khi cancel/error/race.
FR-07: hai chế độ full/present. Full thay toàn bộ nội dung có thể sửa, phần thiếu để trống; present thay nguyên phần có trong preview, phần thiếu giữ cũ. Phần có là title không trống hoặc danh sách có dữ liệu, empty/null là thiếu. File hoàn toàn không nội dung hợp lệ khóa apply; không nối thêm mặc định.
FR-08: bảo toàn target year/ID/owner/scope/status/history/version/revision/edit lease, tuổi cố định; chặn lệch tuổi, nhóm trống nhận trong quyền hiện có. Title có thể sửa theo mode; description hiện chỉ clear(full)/keep(present).
FR-09: dùng cấu hình start/end năm hệ thống khi preview/apply; thiếu khoảng hợp lệ khóa apply với thông báo, không dùng ngày file hoặc tự đoán thay cấu hình. Dời ngày nguồn bằng chênh năm hiện có, không tự căn thứ; out-of-year cảnh báo, gửi/ban hành vẫn validate như cũ.
FR-10: giữ nút/cards/status lưu/guard theo UX mới đã chốt, không thay role permissions.
FR-11: dữ liệu kiểm thử tổng hợp, không AI/dependency/DB/API/APP mới.
FR-12: tạo/ghép chủ đề đích trước tick; phần mục tiêu được giữ khi thay chủ đề phải remap mã duy nhất, bỏ tick không còn đích và báo cụ thể, không tick mồ côi hoặc ID trùng.
FR-13: tick mã ghép QH-DN-BH hiển thị ba dòng, mã dài wrap, giữ aria-pressed/chuột/bàn phím và dữ liệu abbr.

## Clarifications
### Session 2026-11-02 — quyết định mới nhất
- Q: File thiếu chủ đề/nghỉ/mô tả thì xóa hay giữ? → A: user muốn có cả hai lựa chọn trong modal.
- Q: Duyệt kế hoạch và cho phép cập nhật bộ đặc tả/tri thức BE, giữ API/DB? → A: user duyệt và cho phép cập nhật tài liệu BE.
- Năm đích/khoảng ngày hệ thống và field cố định phải giữ. Quyết định chỉ bổ sung/metadata chỉ điền trống trước đó đã bị thay thế; lịch sử task/evidence cũ vẫn giữ để đối chiếu.
- Phó HT chọn tuổi lúc lập mới, bản đã lưu cố định; tổ trưởng cố định nhóm. Lập mới khóa nhóm đã có plan trong cùng năm, kể cả chưa ban hành; không thuộc thay đổi API ở đây.

## Success criteria
SC-01: fidelity parser mẫu tham chiếu83MT/5lĩnh vực/10chủ đề giữ baseline, không sửa parser trong delta này.
SC-02: tests mới chứng minh cả hai chế độ, thiếu/đủ từng phần, fixed fields/age/year/range, tick-remap, no-mutation/race/cancel/rights. Không dùng số test cũ làm bằng chứng code mới.
SC-03:375/768/1440 không tràn body; hai chế độ có label/focus, preview đúng; full thay nội dung khi Lưu/mở lại, present giữ đúng phần thiếu. BE thật chưa kiểm thì phải ghi rõ.

## Acceptance scenarios
| AC | Given | When | Then |
| --- | --- | --- | --- |
| AC1/2 | Role có quyền, viewport desktop/mobile | Mở hai trang | Giữ header/cards/controls/field cố định như UX đã chốt |
| AC3 | File hợp lệ | Đọc preview | Giữ fidelity và warnings hiện có |
| AC4 | Form có nội dung tay | Chọn full/present và confirm | Full xóa phần thiếu; present giữ phần thiếu, phần có được thay nguyên phần; form/file đầu vào không bị mutate |
| AC5 | File khác năm hoặc config năm thiếu | Preview/apply | Target year/range không đổi, lịch dời đúng; config thiếu khóa apply; lịch ngoài năm báo và giữ validation |
| AC6 | File cũ chỉ có bảng mục tiêu | Preview hai chế độ | Phần thiếu hiển thị sẽ xóa(full)/giữ(present), không bị âm thầm coi bổ sung |
| AC7 | File khác tuổi hoặc chứa owner/status giả | Confirm | Khác tuổi bị chặn, mọi fixed fields giữ nguyên |
| AC8 | Đổi mode/file, đóng/lỗi/mất lease hoặc double click | Reader phản hồi/apply | Chỉ preview/file/mode mới nhất được dùng; không đổi form khi không hợp lệ |
| AC9 | Giữ mục tiêu cũ, thay chủ đề hoặc có mã trùng | Apply | Remap bằng mã duy nhất, không tick mồ côi; báo liên kết không giữ được, ambiguity không ghép bừa |
| AC10 | Mã QH-DN-BH trong cột hẹp | Xem/sửa | QH,DN,BH ba dòng, không đổi mã hoặc hành vi tick |

## Scope/gates
Reuse constitution1.0.0; không sửa stack/principles. Cập nhật Domain14/spec/plan/data-model/contract/tasks, analyze trước code. Kế hoạch FE được user duyệt, phase tài liệu dừng review trước logic nhập; không đánh dấu implementation/converge sạch khi task delta còn mở.
