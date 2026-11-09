# Nghiên cứu — kiểm tra gửi duyệt

## Thời lượng và dữ liệu cũ

**Bằng chứng**: V90 bỏ `edu_year_plans.duration_policy_snapshot`; `EduPlan.durationPolicySnapshot` là transient; `EduYearPlan` chưa map trong khi `EduTeamPlan` đã map. Snapshot khi tạo bị mất sau reload. `requireReadyForReview` chưa kiểm khoảng tuần; `YearPlanPublicationValidator` kiểm lúc ban hành. Nhánh thiếu cả hai ngày hiện lọt kiểm tra ở validator.

**Phương án**: migration mới bổ sung lại snapshot cho năm, map như tổ; dùng lại `EduTeachingCalendar` kiểm ở submit và ban hành. Tách kiểm đủ ngày khỏi kiểm BOUNDED để xử lý cả legacy. Không sửa V90, không đoán snapshot lịch sử hoặc tự đổi trạng thái hồ sơ.

**Quyết định user 09/11**: hồ sơ mất snapshot chỉ khôi phục khi có bằng chứng, giữ trạng thái; phiên bản điều chỉnh dùng cấu hình hiện tại lúc tạo. Không tự sửa dữ liệu đang dùng ở bước plan.

**Làm rõ tiếp 09/11 (EDU-71)**: mất snapshot hoặc chưa thể xác định legacy thật thì chặn gửi và báo cần kiểm tra/khôi phục; legacy thật có bằng chứng giữ ngoại lệ cũ. Phải phân biệt ba trạng thái, không migration mọi giá trị thiếu thành LEGACY_UNBOUNDED có thể gửi.

## Nội dung kế hoạch tổ

**Bằng chứng**: `InheritedCurriculumGuard.validateInheritedTree` hiện khóa nội dung; `EduTeamPlanServiceImpl.syncTree` có thể ghi đè text từ năm và `hasInvalidReferences` coi nội dung riêng không có lineage năm là sai. FE `planLevel.mjs` hiện tái tạo cây theo nguồn, `reapplyTeamDraft` không giữ nội dung riêng.

**Phương án**: cho thêm/sửa nội dung thuộc mục tiêu của đúng tổ, giữ cấu trúc mục tiêu/nhóm/năm không đổi; kiểm quyền và chủ sở hữu ở server. Lưu và đồng bộ phải giữ ID, nội dung riêng, tick và dữ liệu đang nhập. GV chỉ nhận bản tổ được phép theo trạng thái và scope hiện hành. Không tự cấp quyền xóa.

**Quyết định user 09/11**: nguồn năm mới và tổ cùng sửa một nội dung thì giữ nội dung tổ, báo tổ trưởng rà soát. So sánh ba phía (nguồn cũ, nội dung tổ, nguồn mới) nhận diện thay đổi; phải giữ ID, nội dung riêng và tick, không tự ghi đè.

## Kiểm khi gửi và giao diện

**Bằng chứng**: `GoalFormPage` và `GoalDetailPage` có hai đường gửi; server chưa kiểm đủ 9 chủ đề, nội dung từng mục tiêu và phân bổ từng mục tiêu tổ. `GoalTables` khóa toàn cây tổ; mock guard so nội dung với năm.

**Phương án**: kiểm server trong giao dịch trước chuyển trạng thái, giữ lưu nháp; FE dùng kiểm tra chung cho hai đường gửi, đánh dấu * có tên truy cập và lỗi gần ô/tổng hợp. Nới UI và mock đúng phạm vi nội dung tổ; kiểm API mapper giữ ID, lineage, nội dung, tick.

**Lựa chọn**: tái sử dụng validation, quyền, API hiện có; không bổ sung thư viện hay luồng duyệt. Không dùng riêng validation FE làm ranh giới tin cậy.

## Phân công và duyệt — lựa chọn mới nhất của user

**Quyết định user trực tiếp 09/11**: áp dụng duyệt thứ bậc cố định, một người được chỉ định soạn và bỏ lease 90 giây/heartbeat 20 giây theo flow; EDU-70 PROPOSED thay phần mâu thuẫn của EDU-02/03/10/54. REVIEW vẫn không cho sửa. Thay tuyên bố giữ nguyên quyền/luồng và API trong lựa chọn lịch sử ở trên: phải rà consumer và duyệt contract nếu thay đổi, không tự xóa endpoint.

**Bảo toàn**: revision/chống ghi đè, request trùng và giao dịch phải được kiểm khi bỏ lease; đánh giá hành vi session đang mở và khả năng khôi phục nháp. Phạm vi tác động FE/APP và dữ liệu phân công cần thiết kế cụ thể trước code.

**STT theo nguồn hiện hành**: EDU-09 tự sinh số liên tục; EDU-58 nhãn nhập là tùy chọn, rỗng quay về STT mặc định. Không bắt buộc người dùng nhập nhãn riêng cho mọi mục tiêu.

**Hồ sơ lớp**: `EduClassDocServiceImpl` gọi `isAuthorEligible` và `evaluateClassDoc` trong các đường soạn/gửi/duyệt, nên ma trận EDU-70, phân công một người và test GV/TT/PHT/HT phải bao phủ hồ sơ lớp lẫn năm/tổ.
