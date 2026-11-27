# Data model — không migration

Chỉ đọc dữ liệu có sẵn; không thêm bảng, cột, chỉ mục.

| Cần | Nguồn | Đọc qua |
| --- | --- | --- |
| Liên kết phụ huynh–con | `guardian_child_links` | `GuardianChildLinkService#isGuardianOf` |
| Con đã nghỉ/ra trường | xếp lớp, hồ sơ | `EnrollmentService#findChildIdsLeftSchool` |
| Giai đoạn học trong tuần | `enrollments` | `EnrollmentService#findChildStays` |
| Điểm trường, năm, bữa, nhóm tuổi | `classrooms`, năm học | `ClassroomService#findClassroomInfo` |
| Khối | nhóm tuổi | `MealDemandCalculator#blockOfAgeGroup` |
| Ngày nghỉ | `school_holidays`, kế hoạch tuần | `SchoolYearService#isSchoolDay`, `MealPlanDay#isOff` |
| Tuần, món | `meal_plan_weeks`, `meal_plan_items` | `MealPlanWeekRepository` |
| Chất gây dị ứng của món | món → nguyên liệu → thực phẩm | `DishRepository#findByIdIn` |
| Báo ăn, dị ứng đã khai | hồ sơ trẻ | `ChildService#findChildIdsWithoutMeals`, `#findAllergyNames` |
| Ảnh | `meal_photos`, kho `MEAL` | `MealPhotoRepository`, `FileStorage` |

Ràng buộc đọc: `findChildStays` kéo giai đoạn chưa đóng tới cuối khoảng hỏi ⇒ phải lọc ngày theo `yearStart`–`yearEnd` của lớp.
