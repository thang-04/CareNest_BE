# Kiểm chứng dự kiến

Chỉ chạy sau khi code/plan được duyệt; dùng DB kiểm thử riêng và dữ liệu giả, không sửa DB thật.

1. Chạy Maven tests tương ứng `EduTeachingCalendarTest`, `EduYearPlanServiceIntegrationTest`, `EduBranchRangeIntegrationTest`, `SchoolYearTeamPlanIntegrationTest`; bổ sung test cho snapshot qua reload/version, biên 2–4 tuần, 5 tuần, tuần nghỉ, nhánh thiếu ngày, 8/9 chủ đề, mỗi mục tiêu thiếu nội dung và submit trực tiếp qua API.
2. Kiểm draft lưu được dữ liệu chưa hoàn chỉnh; submit lỗi giữ trạng thái và dữ liệu; đủ điều kiện được gửi. Kiểm một người được chỉ định soạn, thứ bậc duyệt, REVIEW không sửa, revision/chống ghi đè, session cũ sau bỏ lease và lỗi trước khi đổi trạng thái.
3. Kiểm tổ thêm/sửa nội dung không đổi năm; tick cho mỗi mục tiêu; thêm/sửa trái quyền, ID ngoại, xóa không được phép bị từ chối. Reload, đổi nguồn và phục hồi nháp không làm mất nội dung/tick; khi năm và tổ cùng sửa phải giữ nội dung tổ và báo rà soát.
4. Kiểm FE bằng Node tests có sẵn cho năm, tổ, workflow và mapper, ESLint các file sửa và Vite build. Chỉ kiểm browser khi được phép theo `CareNest_FE/AGENTS.md`; kiểm dấu *, bàn phím, nội dung lỗi và hai đường gửi.
5. Kiểm migration trên bản DB thử đã sao lưu: hồ sơ mới giữ snapshot sau restart; legacy thật có chứng cứ giữ ngoại lệ; snapshot mất/chưa phân biệt bị chặn gửi và báo kiểm tra, không đoán giá trị; khôi phục có bằng chứng giữ trạng thái/revision. Không tự thao tác DB đang dùng.
6. Kiểm ma trận EDU-70 cho năm/tổ/hồ sơ lớp: GV/TT/PHT/HT đúng cấp, tự duyệt/cấp dưới bị chặn, một người được chỉ định, REVIEW không sửa; kiểm tương thích phiên/client cũ và xung đột revision.

Lệnh cụ thể theo tên test thật sẽ được ghi ở `tasks.md` khi tách nhiệm vụ; không chạy wrapper/harness theo chỉ đạo repo.

## Rehearsal phân loại và khôi phục giới hạn

1. Trên bản sao DB thử đã sao lưu, kiểm kê từng hồ sơ/phiên bản và đối chiếu nguồn theo tiêu chí trong `data-model.md`; lưu bảng kiểm đã ẩn danh gồm mã, trạng thái/revision ban đầu, nguồn, thời điểm nguồn, giá trị hoặc chính sách chứng minh được, kết quả và người kiểm tra.
2. Dùng fixture bao gồm snapshot BOUNDED đủ bằng chứng, legacy thật có bằng chứng, thiếu bằng chứng và nguồn mâu thuẫn. Hai trường hợp cuối phải ở MISSING_OR_UNKNOWN và bị chặn gửi; null, ngày tạo hoặc cấu hình hiện tại không đủ chứng minh legacy.
3. Trên DB thử, khôi phục hoặc phân loại chỉ theo bằng chứng hợp lệ; kiểm so sánh trước/sau cho đúng hồ sơ/phiên bản, trạng thái và revision giữ nguyên, kiểm gửi theo chế độ được chứng minh, lưu vết và thử rollback từ backup. Không thay đổi hồ sơ khác.
4. Ghi kết quả, trường hợp chưa xác định và đề xuất xử lý để review. Mọi thao tác với DB đang dùng cần phê duyệt riêng; rehearsal không phải là phê duyệt hoặc bằng chứng đã sửa dữ liệu thật.
