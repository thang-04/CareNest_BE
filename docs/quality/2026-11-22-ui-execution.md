# Thực thi UI local — 22/11/2026

## Môi trường

- BE source hiện tại, FE Vite :5173, Keycloak thật :8180, đăng nhập principal qua form.
- Dữ liệu giả: sao lưu DB thử rồi phục hồi vào DB riêng `carenest_ui_20261122` trên PostgreSQL local :5434.
- Không dùng production. Không sửa module vai trò/tài khoản hoặc soạn kế hoạch giáo dục.
- Kết quả dưới đây là quan sát và thao tác UI, tách khỏi số ca tự động trong báo cáo continuity.

## Các ca đã quan sát trực tiếp

| Vai trò | Ca | Kết quả |
| --- | --- | --- |
| HT | Đăng nhập Keycloak, danh sách năm cũ/đang học/sắp tới | Đạt: 2025–26 CLOSED, 2026–27 ACTIVE, 2027–28 PLANNED |
| HT | Thêm phòng trống | Đạt: lỗi campus, mã, tên, sức chứa; không lưu |
| HT | Phòng sức chứa 0 | Đạt: chặn, yêu cầu 1–60 |
| HT | Tạo UI2211 năm 2026–27 | Đạt: lưu, tìm được, sức chứa 25 |
| HT | Chuyển năm 2027–28 | Đạt: chưa tạo thì tìm UI2211 không có |
| HT | Tạo cùng mã và tên UI2211 năm 2027–28, sức chứa 30 | Đạt: tải lại vẫn 30; quay năm 2026–27 vẫn 25 |
| HT | Nhóm tuổi chuẩn, giới hạn sĩ số | Đạt: tên/khoảng tuổi khóa theo chuẩn; nhóm 4–5 nhập 31 bị chặn tối đa 30 |
| HT | Đổi bữa nhóm 4–5 năm 2027–28 chỉ còn chiều | Đạt: năm tới chỉ chiều, năm hiện tại vẫn trưa+chiều |
| HT | Nhóm tuổi năm 2025–26 đã đóng | Đạt: không có nút sửa nhóm/thêm lớp |
| HT | Ngày áp dụng 31/02/2026 | Chặn đúng, nhưng thông báo phạm vi năm chưa phân biệt ngày không tồn tại |
| HT | Sửa riêng phần ngày 23→24 trong 23/11/2026 | Đạt: kết quả 24/11/2026 |
| HT | Đổi giờ bằng bàn phím và lưu 09:30 từ 24/11/2026 | Đạt: xác nhận đúng 09:30, toast đã lưu; hiện tại vẫn dùng mốc cũ |
| HT | HKII bắt đầu 14/01/2027 khi HKI kết thúc 15/01 | Đạt: báo phải sau ngày kết thúc HKI; đã khôi phục ô nhập 18/01, không lưu mốc chồng lấn |

## Lưu ý khi tự động thao tác

- Native select đang mở cần chốt/blur trước thao tác tiếp; không kết luận app tự đổi năm từ một popup chưa đóng.
- Native input time: fill bằng adapter hiển thị DOM nhưng có lần chưa cập nhật state React; chỉnh bằng phím mũi tên thì xác nhận/lưu đúng. Chưa kết luận lỗi sản phẩm từ hành vi fill này.
- Hôm nay thực tế là Chủ nhật 22/11/2026. Các ca cần ngày học sẽ dùng launcher trong test với Clock cố định trên DB UI riêng; không đổi đồng hồ Windows hoặc Clock production. Mốc mô phỏng phải được ghi rõ trong kết quả.

## Đang kiểm tra

GV/tổ trưởng/PHT; quyền theo campus/nhóm tuổi/năm; điểm danh/suất ăn; lưu/xác nhận/mở lại đánh giá; lịch sử chuyển/nghỉ. Chưa kết luận full UI đạt.

## Bổ sung UI GV/PHT (22/11, Clock nghiệp vụ 20/11/2026 07:45)

- GV teacher2: Có mặt tự bật ăn; tắt ăn Bùi Tuệ Hoa, lưu/reload giữ Không. Có mặt tất cả giữ lựa chọn đã có; lưu 19 trẻ, 18 suất trưa và chiều. Hai đơn nghỉ giữ khóa chọn Có mặt (bản sao fixture đã xóa attendance trước thử nên còn Chưa điểm danh, không dùng để kết luận luồng tạo đơn).
- GV: 385 → 38.5; hiện cảnh báo sốt vừa; chọn Sốt/Theo dõi tại lớp, lưu/reload giữ một lần sức khỏe 07:45. Liên hệ phụ huynh hiện bố/mẹ và số từ hồ sơ mẫu; không gọi điện/gửi tin.
- GV: lưu nhận xét riêng và nhận xét chung [UI-2211]. Màn kỹ năng hiện 5 hoạt động, 9 mục tiêu, lưu MT75 Chưa đạt; trạng thái 19/19 đã ghi nhận. Trẻ chưa điểm danh không có ô chấm.
- GV: chủ đề đã xác nhận chỉ xem; chủ đề đang diễn ra cho nhập nháp, nút xác nhận bị khóa. Chưa dùng ca này để khẳng định quyền xác nhận riêng của giáo viên phụ vì thời gian cũng đang khóa.
- PHT: tổng quan ngày 20/11 thấy đúng nhận xét [UI-2211] của GV và 19/19; danh sách 6 lớp campus1. Màn chủ đề cùng phạm vi 6 lớp.
- PHT mở lại đánh giá: thiếu lớp/lý do báo lỗi; BE chặn ngày tương lai 23/11 và hạn mở đã qua 19/11. Đổi 2025–26: chỉ lịch sử, lớp năm cũ, nút mở lại disabled.
- Cần xử lý/đối chiếu: Nhóm tuổi & lớp PHT hiển thị 11 lớp toàn trường (AUTH-20/SchoolConfigAccess.canViewSchool) trong khi bộ lọc ghi Điểm trường của tôi và chỉ có Cơ sở1. Chưa kết luận rò quyền vì rule hiện hành cho đọc cấu hình toàn trường; UI mô tả phạm vi đang sai. Không sửa module phân quyền người khác.
- Cần rà UX: sau bỏ thay đổi để đổi năm ở form mở lại, nội dung lý do vẫn được giữ; khi rời tiếp lại cảnh báo chưa lưu. Chưa vào/ra trống để tái hiện false-dirty.

## Bổ sung xác minh lỗi ô bình thường trống

- Tái hiện theo phản ánh mới: DB có 19 dòng NGU_TOT/HAPPY/BINH_THUONG sau teacher2 lưu General, nhưng ConcernCell cố ý trả null nếu không có bất thường. Không phải lỗi mất dữ liệu.
- Sửa FE riêng assessment: chi tiết DAY tải phiếu ngày sẵn có, hiện đúng mức đã lưu của từng tiêu chí; generalRecorded=false hiện Chưa lưu, không lấy skillsRecorded thay thế. Trường thiếu không tự sinh giá trị bình thường.
- UI thật đã đối chiếu cùng ngày/lớp bằng teacher1 (tổ trưởng), viceprincipal và principal: Chu Bảo Hải hiện Bình thường, các bữa Ăn hết, Ngủ tốt, Vui vẻ, Hành vi/Tham gia Bình thường. Bùi Tuệ Hoa giữ dấu hiệu Sốt và MT75 Chưa đạt.
- Phạm vi overview: tổ trưởng nhóm4–5 thấy Chồi1/Chồi2 (lớp khác chỉ xem); PHT cơ sở1 thấy 6 lớp; HT thấy 11 lớp hai cơ sở.
- 27/27 Node tests, lint nguồn sửa, Vite build đạt. Ảnh tại FE:logs/ui-proof/{teamleader,pht,ht}-daily-saved.jpg (không commit).
- Cần kiểm tra thêm liên thông ăn: trẻ đã tắt tham gia ăn vẫn được gợi ý Ăn hết ở Hoạt động chung. Bản sửa trên chỉ hiện đúng dữ liệu đã lưu, chưa tự thay đổi dữ liệu ăn; chưa kết luận nghiệp vụ này đạt.
- Mốc mô phỏng BE khác đồng hồ trình duyệt: các ca dùng Date.now của FE (ví dụ sổ ăn coi 20/11 là ngày đã qua) không dùng để khẳng định giờ chốt mô phỏng đạt. Cần kiểm bằng ngày/giờ đồng bộ hoặc test tích hợp đã có.

## 22/11 — Xếp thủ công khác nhóm tuổi (yêu cầu mới)

- Giới hạn riêng bảng Xếp lớp năm học mới. Không làm lớp ghép và không sửa chuyển lớp giữa năm, giáo án hay module quyền/tài khoản.
- BE bỏ kiểm lệch nhóm tuổi ở lưu tay và readiness, giữ quyền/phạm vi, lớp đúng năm, campus đăng ký của trẻ mới, sức chứa, tốt nghiệp, ngày hiệu lực. Tự xếp vẫn theo tuổi.
- Maven: YearTransitionIntegrationTest 18/18; PlacementRulesTest 1/1; YearTransitionControllerTest 4/4, 0 fail/error/skip. Logs manual-year-placement-test.log và manual-year-placement-final-test.log. Có ca khác nhóm rồi kích hoạt, STAY khác nhóm giữ sau auto, lớp sai năm và vượt sĩ số vẫn chặn.
- FE: 282 tests ở dashboard/hardening/integration-selective-merge đạt; lint, build API và diff check đạt. BE Spotless check đạt.
- UI clone DB: HT xếp Hoàng Minh Thùy (lớp cũ Nhà trẻ1, gợi ý Mầm1) vào Chồi1 nhóm4–5; lưu/reload đúng. Tab trẻ mới: Lê Bảo Ngọc (gợi ý3–4) vào Chồi1 nhóm4–5 cùng campus1; lưu/reload đúng23/30. Trẻ mới campus2 không có lớp campus1 trong select.
- UI phát hiện và sửa thêm lỗi reset lựa chọn trẻ mới do parent tạo mảng admissions mới khi dirty; memo danh sách ngăn xóa bản nhập. Ảnh FE:logs/ui-proof/manual-other-age-group-saved.jpg.
- Kích hoạt khác nhóm đã kiểm integration thật PostgreSQL; UI clone chưa kích hoạt vì năm2027–28 còn ngoài cửa sổ và còn thiếu các bước khác. Không bỏ các điều kiện đó chỉ để chạy thử.
