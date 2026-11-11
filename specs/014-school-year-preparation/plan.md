# Plan — CFG-YR-05

- DB: V90 thêm `school_years.prep_date` (nullable, backfill 1 tháng trước ngày bắt đầu kẹp sau năm trước), CHECK prep_date ≤ start_date.
- BE: `SchoolYear.prepDate/isPreparing`; `SchoolYearRequest.prepDate` (tùy chọn); `SchoolYearDetailResponse.prepDate, preparing`; validate trong `SchoolYearServiceImpl#resolvePrepDate`; GV không thấy năm PLANNED chưa tới ngày mở chuẩn bị (list + get); `GET /school-years/{id}/prep-classes` (YearTransitionService#getPrepClasses); `StaffAssignmentService#findYearTeamLeaderAgeGroupIds`.
- FE: ô Ngày mở chuẩn bị ở form năm học (validate inline, map mã lỗi 2035–2038), nhãn "Đang chuẩn bị" ở bộ chọn năm và form; màn "Lớp năm học mới" gọi BE thật.
- Không đổi: kích hoạt (7 ngày), điểm danh/suất ăn/đánh giá (đã yêu cầu năm ACTIVE).
- Verify: `node scripts/verify.mjs` BE + FE; khởi động BE kiểm Flyway V90.
