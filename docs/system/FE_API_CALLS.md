# Bảng tra lời gọi API của Web — file FE → controller BE

File sinh tự động, không sửa tay. Sinh lại: `node scripts/check-api-contract.mjs --consumers` (sau khi cập nhật `BE_API_INDEX.md`).

- Cách dùng: grep path (vd. `grep "meal-counts"`) hoặc tên file service để biết file FE nào gọi endpoint nào và controller BE xử lý.
- Path đã chuẩn hóa tham số thành `{x}`. `[module: api|demo]` theo `src/config/integrationModules.js`.
- Lời gọi có đường dẫn không phải chuỗi literal (vd. ghép từ biến) không nằm trong bảng.

Tổng: 354 lời gọi.

```text
DELETE /age-groups/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/AgeGroupController.java#deleteAgeGroup
DELETE /campuses/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/CampusController.java#deleteCampus
DELETE /children/{x}/guardians/{x} · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/child/GuardianController.java#removeGuardian
DELETE /classes/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/ClassController.java#deleteClass
DELETE /education-plans/lessons/{x} · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → (BE chưa có — pending)
DELETE /education-plans/themes/{x} · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → (BE chưa có — pending)
DELETE /kitchen/suppliers/{x} · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
DELETE /locations/{x} · src/services/masterDataService.js [master-data: api] → src/main/java/com/carenest/controller/organization/RoomController.java#deleteRoom
DELETE /menu/allergy-menus/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
DELETE /menu/dishes/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
DELETE /menu/foods/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
DELETE /menu/meal-prices/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
DELETE /menu/sample-menus/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
DELETE /menu/weekly-menus/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
DELETE /nutrition/fresh-forecasts/{x} · src/services/freshGoodsApi.js [fresh-goods: api] → src/main/java/com/carenest/controller/meals/FreshGoodsController.java#deleteForecast
DELETE /nutrition/stock/receipts/{x} · src/services/dryStockApi.js [dry-stock: api] → src/main/java/com/carenest/controller/meals/StockController.java#deleteReceipt
DELETE /school-years/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/SchoolYearController.java#deleteSchoolYear
DELETE /signatures/me/{x} · src/services/signatureService.js [signatures: api] → src/main/java/com/carenest/controller/account/UserSignatureController.java#deleteSignature
GET /accounts · src/services/account/api/permissionApi.js [permission: api] → src/main/java/com/carenest/controller/account/AccountController.java#searchAccounts
GET /accounts · src/services/school-config/schoolConfigService.js [school-config: api] → src/main/java/com/carenest/controller/account/AccountController.java#searchAccounts
GET /accounts/me · src/services/account/api/accountApi.js [account: api] → src/main/java/com/carenest/controller/account/AccountController.java#getMyAccount
GET /accounts/me · src/services/authService.js [account: api] → src/main/java/com/carenest/controller/account/AccountController.java#getMyAccount
GET /accounts/{x}/permission-overrides · src/services/account/api/permissionApi.js [permission: api] → src/main/java/com/carenest/controller/account/PermissionController.java#getPermissionOverrides
GET /accounts/{x}/permission-overrides · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/account/PermissionController.java#getPermissionOverrides
GET /age-groups · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/organization/AgeGroupController.java#getAgeGroups
GET /age-groups · src/services/mealPlanApi.js [meal-plan: api] → src/main/java/com/carenest/controller/organization/AgeGroupController.java#getAgeGroups
GET /age-groups · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/organization/AgeGroupController.java#getAgeGroups
GET /age-groups · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/AgeGroupController.java#getAgeGroups
GET /age-groups/{x}/team-leader-assignments/history · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/TeacherAssignmentController.java#getTeamLeaderHistory
GET /assessment/behaviour-tickets/board · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/BehaviourTicketController.java#getBoard
GET /assessment/behaviour-tickets/overview · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/BehaviourTicketController.java#getOverview
GET /assessment/behaviour-tickets/summary · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/BehaviourTicketController.java#getSummary
GET /assessment/children · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ChildEvaluationController.java#developmentChildren
GET /assessment/children/{x}/development · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ChildEvaluationController.java#development
GET /assessment/class-monthly-comments · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ClassMonthlyCommentController.java#getComment
GET /assessment/class-ratings · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ClassRatingController.java#listRatings
GET /assessment/day-unlocks · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/AssessmentDayUnlockController.java#getUnlocks
GET /assessment/evaluations · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ChildEvaluationController.java#list
GET /assessment/evaluations/{x} · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ChildEvaluationController.java#get
GET /assessment/evaluations/{x}/year-sheet · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ChildEvaluationController.java#getYearSheet
GET /assessment/overview · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ClassAssessmentOverviewController.java#getOverview
GET /assessment/overview/classes/{x} · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ClassAssessmentOverviewController.java#getClassDetail
GET /assessment/program-completion · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ChildEvaluationController.java#programReport
GET /assessment/quality-report · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/QualityReportController.java#getReport
GET /assessment/reward-lists · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/RewardListController.java#classes
GET /assessment/reward-lists/classes/{x} · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/RewardListController.java#detail
GET /assessment/semester-evaluations · src/services/assessment/api/assessmentApi.js [assessment: api] → (BE chưa có)
GET /assessment/theme-evaluations · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ThemeEvaluationController.java#getEvaluation
GET /assessment/theme-evaluations/classes · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ThemeEvaluationController.java#getClasses
GET /assessment/theme-evaluations/themes · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ThemeEvaluationController.java#getThemes
GET /attendance/classes · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/attendance/AttendanceController.java#getClasses
GET /attendance/classes · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/attendance/AttendanceController.java#getClasses
GET /attendance/day-info · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/attendance/AttendanceController.java#getDayInfo
GET /attendance/day-unlocks · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/attendance/AttendanceController.java#getUnlocks
GET /attendance/summary · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/attendance/AttendanceController.java#getSummary
GET /campuses · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/organization/CampusController.java#getCampuses
GET /campuses · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/organization/CampusController.java#getCampuses
GET /campuses · src/services/masterDataService.js [master-data: api] → src/main/java/com/carenest/controller/organization/CampusController.java#getCampuses
GET /campuses · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/CampusController.java#getCampuses
GET /campuses/options · src/services/account/api/permissionApi.js [permission: api] → src/main/java/com/carenest/controller/organization/SchoolStructureController.java#listCampuses
GET /campuses/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/CampusController.java#getCampusDetail
GET /children · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/child/ChildController.java#searchChildren
GET /children · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/child/ChildController.java#searchChildren
GET /children/duplicate-check · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/child/ChildEnrollmentController.java#checkDuplicate
GET /children/parent-accounts · src/services/children/api/childrenApi.js [children: demo] → (BE chưa có)
GET /children/parent-accounts/by-citizen-id · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/child/ChildEnrollmentController.java#findParentAccount
GET /children/placement-classes · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/child/ChildEnrollmentController.java#listPlacementClasses
GET /children/{x} · src/services/schoolService.js [school-lookup: demo] → src/main/java/com/carenest/controller/child/ChildController.java#getChildProfile
GET /children/{x}/account-recipient · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/child/SelectedParentAccountController.java#candidates
GET /children/{x}/daily-health · src/services/daily-health/api/dailyHealthApi.js [daily-health: api] → src/main/java/com/carenest/controller/attendance/DailyHealthController.java#getChildHistory
GET /children/{x}/guardians · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/child/GuardianController.java#listGuardians
GET /children/{x}/guardians/pending · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/child/SelectedParentAccountController.java#pending
GET /children/{x}/health · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthMeasurementController.java#getHealthRecord
GET /children/{x}/profile · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/child/ChildPhotoController.java#getProfile
GET /classes · src/services/schoolService.js [school-lookup: demo] → (BE chưa có)
GET /classes/{x} · src/services/schoolService.js [school-lookup: demo] → (BE chưa có)
GET /classes/{x}/attendance · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/attendance/AttendanceController.java#getClassAttendance
GET /classes/{x}/attendance/summary · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/attendance/AttendanceController.java#getClassSummary
GET /classes/{x}/children/{x}/daily-health · src/services/daily-health/api/dailyHealthApi.js [daily-health: api] → src/main/java/com/carenest/controller/attendance/DailyHealthController.java#getClassChildHealth
GET /classes/{x}/daily-assessment · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/DailyAssessmentController.java#getDailySheet
GET /classes/{x}/pickups · src/services/pickup/api/pickupApi.js [pickup: demo] → src/main/java/com/carenest/controller/attendance/PickupController.java#getBoard
GET /classes/{x}/teacher-assignments/history · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/TeacherAssignmentController.java#getClassTeacherHistory
GET /daily-health/overview · src/services/daily-health/api/dailyHealthApi.js [daily-health: api] → src/main/java/com/carenest/controller/attendance/DailyHealthController.java#getOverview
GET /education-plans/class-docs · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → src/main/java/com/carenest/controller/eduplan/EduClassDocController.java#getClassDocs
GET /education-plans/config · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → src/main/java/com/carenest/controller/eduplan/EduConfigController.java#getEduConfig
GET /education-plans/curriculum/sources · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → src/main/java/com/carenest/controller/eduplan/EduCurriculumController.java#getSources
GET /education-plans/curriculum/{x} · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → (BE chưa có — pending)
GET /health-checks · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthCheckController.java#listHealthChecks
GET /health-checks/growth-report · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthCheckController.java#getGrowthReport
GET /health-checks/schedule-warnings · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthCheckController.java#getScheduleWarnings
GET /health-checks/{x} · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthCheckController.java#getHealthCheck
GET /health-checks/{x}/children/{x}/exam · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthExamController.java#getExam
GET /health-checks/{x}/classes/{x}/children · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthCheckController.java#getHealthCheckClass
GET /inspections/day · src/services/kitchen/api/inspectionApi.js [kitchen-inspection: api] → src/main/java/com/carenest/controller/meals/InspectionController.java#getDay
GET /inspections/signatures/{x} · src/services/kitchen/api/inspectionApi.js [kitchen-inspection: api] → src/main/java/com/carenest/controller/meals/InspectionController.java#getSignatureImage
GET /kitchen/day · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/day-market · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/dry-stock · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/dry-stock/{x} · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/food-samples · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/foods · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/fresh-receipts · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/published-menus · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/stock-issues · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/stock-receipts · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/stock-receipts/{x} · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/suppliers · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/suppliers/{x} · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /kitchen/weekly-forecasts · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
GET /leave-requests · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/attendance/LeaveRequestController.java#searchLeaveRequests
GET /locations · src/services/masterDataService.js [master-data: api] → src/main/java/com/carenest/controller/organization/RoomController.java#getRooms
GET /meal-counts · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/meals/MealCountController.java#getMealCounts
GET /meal-counts · src/services/kitchen/api/confirmedMealCountApi.js [kitchen-meal-count: api] → src/main/java/com/carenest/controller/meals/MealCountController.java#getMealCounts
GET /meal-counts/class · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/meals/MealCountController.java#getClassMealCount
GET /meal-handovers · src/services/attendance/api/attendanceApi.js [attendance: api] → (BE chưa có — pending)
GET /menu/access · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/ai-suggestions/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/allergy-context · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/allergy-menus · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/allergy-menus/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/catalog · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/dishes · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/dishes/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/foods · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/foods/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/meal-prices · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/meal-prices/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/nutrition-setups · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/plans · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/published/daily · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/published/weekly · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/reference-dishes · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/sample-menus · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/sample-menus/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/weekly-menus · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /menu/weekly-menus/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
GET /notifications/me · src/services/notificationService.js [notifications: demo] → (BE chưa có)
GET /notifications/{x} · src/services/account/api/accountApi.js [account: api] → (BE chưa có — pending)
GET /nutrition/age-group-configs · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/AgeGroupMealConfigController.java#getEffectiveConfigs
GET /nutrition/age-group-configs/{x}/versions · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/AgeGroupMealConfigController.java#getConfigVersions
GET /nutrition/allergens · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/NutritionFoodController.java#getAllergens
GET /nutrition/day-board · src/services/dayBoardApi.js [day-board: api] → src/main/java/com/carenest/controller/meals/MealPlanController.java#getDayBoard
GET /nutrition/day-market · src/services/freshGoodsApi.js [fresh-goods: api] → src/main/java/com/carenest/controller/meals/DayMarketController.java#getDayMarket
GET /nutrition/dishes · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/NutritionDishController.java#getDishes
GET /nutrition/dishes/{x} · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/NutritionDishController.java#getDish
GET /nutrition/food-samples · src/services/kitchenRecordsApi.js [kitchen-records: api] → src/main/java/com/carenest/controller/meals/KitchenRecordsController.java#getSamples
GET /nutrition/foods · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/NutritionFoodController.java#getFoods
GET /nutrition/foods/{x} · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/NutritionFoodController.java#getFood
GET /nutrition/fresh-checks · src/services/freshGoodsApi.js [fresh-goods: api] → src/main/java/com/carenest/controller/meals/FreshGoodsController.java#getChecks
GET /nutrition/fresh-forecasts · src/services/freshGoodsApi.js [fresh-goods: api] → src/main/java/com/carenest/controller/meals/FreshGoodsController.java#getForecasts
GET /nutrition/fresh-forecasts/{x} · src/services/freshGoodsApi.js [fresh-goods: api] → src/main/java/com/carenest/controller/meals/FreshGoodsController.java#getForecast
GET /nutrition/kitchen/day · src/services/kitchenRecordsApi.js [kitchen-records: api] → src/main/java/com/carenest/controller/meals/KitchenRecordsController.java#getKitchenDay
GET /nutrition/meal-photo-windows · src/services/kitchenRecordsApi.js [kitchen-records: api] → src/main/java/com/carenest/controller/meals/KitchenRecordsController.java#getWindows
GET /nutrition/meal-photos · src/services/kitchenRecordsApi.js [kitchen-records: api] → src/main/java/com/carenest/controller/meals/KitchenRecordsController.java#getPhotos
GET /nutrition/meal-plans · src/services/mealPlanApi.js [meal-plan: api] → src/main/java/com/carenest/controller/meals/MealPlanController.java#getMealPlans
GET /nutrition/meal-plans/{x} · src/services/mealPlanApi.js [meal-plan: api] → src/main/java/com/carenest/controller/meals/MealPlanController.java#getMealPlan
GET /nutrition/menu-templates · src/services/menuTemplateApi.js [menu-template: api] → src/main/java/com/carenest/controller/meals/MenuTemplateController.java#getTemplates
GET /nutrition/menu-templates/{x} · src/services/menuTemplateApi.js [menu-template: api] → src/main/java/com/carenest/controller/meals/MenuTemplateController.java#getTemplate
GET /nutrition/stock/lots · src/services/dryStockApi.js [dry-stock: api] → src/main/java/com/carenest/controller/meals/StockController.java#getLots
GET /nutrition/stock/movements · src/services/dryStockApi.js [dry-stock: api] → src/main/java/com/carenest/controller/meals/StockController.java#getMovements
GET /nutrition/stock/receipts · src/services/dryStockApi.js [dry-stock: api] → src/main/java/com/carenest/controller/meals/StockController.java#getReceipts
GET /nutrition/stock/receipts/{x} · src/services/dryStockApi.js [dry-stock: api] → src/main/java/com/carenest/controller/meals/StockController.java#getReceipt
GET /nutrition/stock/shortfalls · src/services/dryStockApi.js [dry-stock: api] → src/main/java/com/carenest/controller/meals/StockController.java#getShortfalls
GET /nutrition/suppliers · src/services/kitchen/api/inspectionApi.js [kitchen-inspection: api] → src/main/java/com/carenest/controller/meals/SupplierController.java#getSuppliers
GET /nutrition/suppliers · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/SupplierController.java#getSuppliers
GET /nutrition/suppliers/{x} · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/SupplierController.java#getSupplier
GET /permission-change-logs · src/services/account/api/permissionApi.js [permission: api] → src/main/java/com/carenest/controller/account/PermissionController.java#getPermissionChangeLogs
GET /permissions · src/services/account/api/permissionApi.js [permission: api] → src/main/java/com/carenest/controller/account/PermissionController.java#getPermissionCatalog
GET /permissions · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/account/PermissionController.java#getPermissionCatalog
GET /pickups · src/services/pickup/api/pickupApi.js [pickup: demo] → src/main/java/com/carenest/controller/attendance/PickupController.java#listResults
GET /pickups/children/{x} · src/services/pickup/api/pickupApi.js [pickup: demo] → src/main/java/com/carenest/controller/attendance/PickupController.java#getChild
GET /role-permissions · src/services/account/api/permissionApi.js [permission: api] → src/main/java/com/carenest/controller/account/PermissionController.java#getRolePermissions
GET /role-permissions · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/account/PermissionController.java#getRolePermissions
GET /school-structure · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/organization/ClassController.java#getSchoolStructure
GET /school-structure · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/ClassController.java#getSchoolStructure
GET /school-years · src/services/http/beClient.js [school-config: api] → src/main/java/com/carenest/controller/organization/SchoolYearController.java#getSchoolYears
GET /school-years · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/SchoolYearController.java#getSchoolYears
GET /school-years/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/SchoolYearController.java#getSchoolYear
GET /school-years/{x}/cutoff · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/AttendanceCutoffController.java#getCutoff
GET /school-years/{x}/holidays · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/SchoolYearController.java#getHolidays
GET /school-years/{x}/placements · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/child/YearTransitionController.java#getPlacements
GET /school-years/{x}/readiness · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/child/YearTransitionController.java#getReadiness
GET /school-years/{x}/semester-locks · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/SchoolYearController.java#getSemesterLocks
GET /school-years/{x}/vice-principal-assignments · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/VicePrincipalAssignmentController.java#getAssignments
GET /school/appearance · src/services/appearance/api/appearanceApi.js [appearance: demo] → (BE chưa có)
GET /signatures/me · src/services/signatureService.js [signatures: api] → src/main/java/com/carenest/controller/account/UserSignatureController.java#listSignatures
GET /signatures/me/{x}/content · src/services/signatureService.js [signatures: api] → src/main/java/com/carenest/controller/account/UserSignatureController.java#getSignatureContent
GET /teacher-assignments · src/services/masterDataService.js [master-data: api] → src/main/java/com/carenest/controller/organization/TeacherAssignmentController.java#getTeacherAssignments
GET /teacher-assignments · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/TeacherAssignmentController.java#getTeacherAssignments
GET /team-leader-assignments · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/TeacherAssignmentController.java#getTeamLeaders
GET /users · src/services/masterDataService.js [master-data: api] → (BE chưa có — pending)
GET /users · src/services/school-config/api/schoolConfigApi.js [school-config: api] → (BE chưa có — pending)
GET /users/{x}/role-change-impact · src/services/school-config/api/schoolConfigApi.js [school-config: api] → (BE chưa có — pending)
PATCH /accounts/{x}/status · src/services/school-config/schoolConfigService.js [school-config: api] → src/main/java/com/carenest/controller/account/AccountController.java#updateAccountStatus
PATCH /campuses/{x}/status · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/CampusController.java#changeCampusStatus
PATCH /locations/{x}/status · src/services/masterDataService.js [master-data: api] → src/main/java/com/carenest/controller/organization/RoomController.java#changeRoomStatus
PATCH /notifications/me/read-all · src/services/notificationService.js [notifications: demo] → (BE chưa có)
PATCH /notifications/{x}/read · src/services/notificationService.js [notifications: demo] → (BE chưa có)
PATCH /nutrition/dishes/{x}/status · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/NutritionDishController.java#changeDishStatus
PATCH /nutrition/foods/{x}/status · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/NutritionFoodController.java#changeFoodStatus
PATCH /nutrition/menu-templates/{x}/status · src/services/menuTemplateApi.js [menu-template: api] → src/main/java/com/carenest/controller/meals/MenuTemplateController.java#changeTemplateStatus
PATCH /nutrition/suppliers/{x}/status · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/SupplierController.java#changeSupplierStatus
PATCH /parent-accounts/{x}/status · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/child/GuardianController.java#updateParentStatus
PATCH /signatures/me/{x}/default · src/services/signatureService.js [signatures: api] → src/main/java/com/carenest/controller/account/UserSignatureController.java#setDefaultSignature
PATCH /users/{x}/profile · src/services/school-config/api/schoolConfigApi.js [school-config: api] → (BE chưa có — pending)
PATCH /users/{x}/status · src/services/school-config/api/schoolConfigApi.js [school-config: api] → (BE chưa có — pending)
POST /accounts · src/services/school-config/schoolConfigService.js [school-config: api] → src/main/java/com/carenest/controller/account/AccountController.java#createAccount
POST /accounts/{x}/temporary-password · src/services/school-config/schoolConfigService.js [school-config: api] → src/main/java/com/carenest/controller/account/AccountController.java#resetTemporaryPassword
POST /age-groups · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/AgeGroupController.java#createAgeGroup
POST /assessment/behaviour-tickets · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/BehaviourTicketController.java#issue
POST /assessment/class-monthly-comments/confirm · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ClassMonthlyCommentController.java#confirmComment
POST /assessment/day-unlocks · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/AssessmentDayUnlockController.java#unlockDay
POST /assessment/day-unlocks/{x}/revoke · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/AssessmentDayUnlockController.java#revokeUnlock
POST /assessment/evaluations/{x}/confirm · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ChildEvaluationController.java#confirm
POST /assessment/evaluations/{x}/redraft · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ChildEvaluationController.java#redraft
POST /assessment/reward-lists/{x}/decision · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/RewardListController.java#decide
POST /assessment/reward-lists/{x}/review · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/RewardListController.java#review
POST /assessment/semester-evaluations/confirm · src/services/assessment/api/assessmentApi.js [assessment: api] → (BE chưa có)
POST /assessment/theme-evaluations/confirm · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ThemeEvaluationController.java#confirmEvaluation
POST /attendance/day-unlocks · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/attendance/AttendanceController.java#unlockDay
POST /attendance/day-unlocks/{x}/revoke · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/attendance/AttendanceController.java#revokeUnlock
POST /campuses · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/CampusController.java#createCampus
POST /children/enrollments · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/child/ChildEnrollmentController.java#enroll
POST /children/enrollments/import · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/child/ChildEnrollmentImportController.java#importRows
POST /children/enrollments/import/preview · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/child/ChildEnrollmentImportController.java#preview
POST /children/guardian-check · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/child/ChildEnrollmentController.java#checkGuardians
POST /children/{x}/guardians · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/child/GuardianController.java#addGuardian
POST /children/{x}/guardians/activate · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/child/SelectedParentAccountController.java#activate
POST /children/{x}/guardians/lookup · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/child/GuardianController.java#findGuardianCandidate
POST /children/{x}/health/measurements · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthMeasurementController.java#createMeasurement
POST /children/{x}/health/trend-analysis · src/services/children/api/childrenApi.js [children: demo] → (BE chưa có)
POST /children/{x}/placement · src/services/children/api/childrenApi.js [children: demo] → (BE chưa có)
POST /children/{x}/readmit · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/child/ChildEnrollmentController.java#readmit
POST /children/{x}/withdraw · src/services/children/api/childrenApi.js [children: demo] → (BE chưa có)
POST /classes · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/ClassController.java#createClass
POST /daily-health-entries · src/services/daily-health/api/dailyHealthApi.js [daily-health: api] → src/main/java/com/carenest/controller/attendance/DailyHealthController.java#addEntry
POST /daily-health-entries/{x}/parent-notifications · src/services/daily-health/api/dailyHealthApi.js [daily-health: api] → (BE chưa có — pending)
POST /education-plans/ai/branch-names · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → (BE chưa có — pending)
POST /education-plans/ai/branch-names/rewrite · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → (BE chưa có — pending)
POST /education-plans/ai/lesson-steps · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → (BE chưa có — pending)
POST /education-plans/class-docs/branch/file-preview · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → src/main/java/com/carenest/controller/eduplan/EduClassDocController.java#previewBranchFile
POST /education-plans/class-docs/{x}/{x}/actions · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → src/main/java/com/carenest/controller/eduplan/EduClassDocController.java#actOnClassDoc
POST /health-checks · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthCheckController.java#createHealthCheck
POST /health-checks/{x}/cancel · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthCheckController.java#cancelHealthCheck
POST /health-checks/{x}/children/{x}/exam/review-preview · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthExamController.java#reviewPreview
POST /health-checks/{x}/close · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthCheckController.java#closeHealthCheck
POST /health-checks/{x}/reopen · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthCheckController.java#reopenHealthCheck
POST /inspections/day/{x}/reopen · src/services/kitchen/api/inspectionApi.js [kitchen-inspection: api] → src/main/java/com/carenest/controller/meals/InspectionController.java#reopenSheet
POST /inspections/day/{x}/sign · src/services/kitchen/api/inspectionApi.js [kitchen-inspection: api] → src/main/java/com/carenest/controller/meals/InspectionController.java#signSheet
POST /kitchen/food-samples/{x}/{x}/{x}/destroy · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
POST /kitchen/fresh-receipts/review · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
POST /kitchen/stock-issues/{x}/allocations · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
POST /kitchen/stock-receipts · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
POST /kitchen/suppliers · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
POST /kitchen/weekly-forecasts/sent · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
POST /locations · src/services/masterDataService.js [master-data: api] → src/main/java/com/carenest/controller/organization/RoomController.java#createRoom
POST /meal-counts/adjustments · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/meals/MealCountController.java#adjust
POST /meal-counts/{x}/confirm · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/meals/MealCountController.java#confirm
POST /meal-handovers/{x}/confirm · src/services/attendance/api/attendanceApi.js [attendance: api] → (BE chưa có — pending)
POST /meal-handovers/{x}/shortage · src/services/attendance/api/attendanceApi.js [attendance: api] → (BE chưa có — pending)
POST /meal-handovers/{x}/supplement · src/services/attendance/api/attendanceApi.js [attendance: api] → (BE chưa có — pending)
POST /menu/ai-suggestions · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
POST /menu/ai-suggestions/{x}/apply · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
POST /menu/ai-suggestions/{x}/dismiss · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
POST /menu/reference-dishes/{x}/copy · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
POST /menu/weekly-menus/{x}/publish · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
POST /menu/weekly-menus/{x}/replacements · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
POST /nutrition/age-group-configs/batch · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/AgeGroupMealConfigController.java#saveConfigs
POST /nutrition/dishes · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/NutritionDishController.java#createDish
POST /nutrition/food-samples/{x}/approve · src/services/kitchenRecordsApi.js [kitchen-records: api] → src/main/java/com/carenest/controller/meals/KitchenRecordsController.java#approveSample
POST /nutrition/food-samples/{x}/approve-disposal · src/services/kitchenRecordsApi.js [kitchen-records: api] → src/main/java/com/carenest/controller/meals/KitchenRecordsController.java#approveDisposal
POST /nutrition/foods · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/NutritionFoodController.java#createFood
POST /nutrition/foods/import · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/NutritionFoodController.java#importFoods
POST /nutrition/fresh-forecasts · src/services/freshGoodsApi.js [fresh-goods: api] → src/main/java/com/carenest/controller/meals/FreshGoodsController.java#createForecast
POST /nutrition/fresh-forecasts/preview · src/services/freshGoodsApi.js [fresh-goods: api] → src/main/java/com/carenest/controller/meals/FreshGoodsController.java#previewForecast
POST /nutrition/meal-plans/{x}/publish · src/services/mealPlanApi.js [meal-plan: api] → src/main/java/com/carenest/controller/meals/MealPlanController.java#publishMealPlan
POST /nutrition/menu-statistics · src/services/mealPlanApi.js [meal-plan: api] → src/main/java/com/carenest/controller/meals/MealPlanController.java#getMenuStatistics
POST /nutrition/menu-templates · src/services/menuTemplateApi.js [menu-template: api] → src/main/java/com/carenest/controller/meals/MenuTemplateController.java#createTemplate
POST /nutrition/stock/days/{x}/sync · src/services/dayBoardApi.js [day-board: api] → src/main/java/com/carenest/controller/meals/StockController.java#resyncDay
POST /nutrition/stock/receipts · src/services/dryStockApi.js [dry-stock: api] → src/main/java/com/carenest/controller/meals/StockController.java#createReceipt
POST /nutrition/stock/receipts/{x}/cancel · src/services/dryStockApi.js [dry-stock: api] → src/main/java/com/carenest/controller/meals/StockController.java#cancelReceipt
POST /nutrition/stock/receipts/{x}/confirm · src/services/dryStockApi.js [dry-stock: api] → src/main/java/com/carenest/controller/meals/StockController.java#confirmReceipt
POST /nutrition/suppliers · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/SupplierController.java#createSupplier
POST /parent-accounts/{x}/temporary-password · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/child/GuardianController.java#resetParentPassword
POST /pickups · src/services/pickup/api/pickupApi.js [pickup: demo] → src/main/java/com/carenest/controller/attendance/PickupController.java#recordResult
POST /school-years · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/SchoolYearController.java#createSchoolYear
POST /school-years/{x}/activate · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/child/YearTransitionController.java#activateSchoolYear
POST /school-years/{x}/classes/copy · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/ClassController.java#copyClasses
POST /school-years/{x}/close · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/SchoolYearController.java#closeSchoolYear
POST /school-years/{x}/copy · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/SchoolYearController.java#copyYear
POST /school-years/{x}/placements/auto · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/child/YearTransitionController.java#autoPlace
POST /signatures/me · src/services/signatureService.js [signatures: api] → src/main/java/com/carenest/controller/account/UserSignatureController.java#uploadSignature
POST /staff-assignments · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/StaffAssignmentController.java#assignStaff
POST /users · src/services/school-config/api/schoolConfigApi.js [school-config: api] → (BE chưa có — pending)
PUT /accounts/{x}/permission-overrides · src/services/account/api/permissionApi.js [permission: api] → src/main/java/com/carenest/controller/account/PermissionController.java#updatePermissionOverrides
PUT /age-groups/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/AgeGroupController.java#updateAgeGroup
PUT /assessment/class-monthly-comments · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ClassMonthlyCommentController.java#saveComment
PUT /assessment/class-ratings · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ClassRatingController.java#saveRatings
PUT /assessment/evaluations/{x}/content · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ChildEvaluationController.java#saveContent
PUT /assessment/evaluations/{x}/program-completion · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ChildEvaluationController.java#markProgram
PUT /assessment/evaluations/{x}/year-sheet · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ChildEvaluationController.java#saveYearSheet
PUT /assessment/reward-lists/classes/{x} · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/RewardListController.java#save
PUT /assessment/semester-evaluations · src/services/assessment/api/assessmentApi.js [assessment: api] → (BE chưa có)
PUT /assessment/theme-evaluations · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/ThemeEvaluationController.java#saveEvaluation
PUT /campuses/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/CampusController.java#updateCampus
PUT /children/{x} · src/services/children/api/childrenApi.js [children: demo] → (BE chưa có)
PUT /children/{x}/account-recipient · src/services/account/api/guardianApi.js [guardian: api] → src/main/java/com/carenest/controller/child/SelectedParentAccountController.java#select
PUT /children/{x}/health-declaration · src/services/children/api/childrenApi.js [children: demo] → (BE chưa có)
PUT /children/{x}/health/measurements/{x} · src/services/children/api/childrenApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthMeasurementController.java#updateMeasurement
PUT /classes/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/ClassController.java#updateClass
PUT /classes/{x}/attendance · src/services/attendance/api/attendanceApi.js [attendance: api] → src/main/java/com/carenest/controller/attendance/AttendanceController.java#saveClassAttendance
PUT /classes/{x}/daily-assessment/general · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/DailyAssessmentController.java#saveGeneral
PUT /classes/{x}/daily-assessment/skills · src/services/assessment/api/assessmentApi.js [assessment: api] → src/main/java/com/carenest/controller/observation/DailyAssessmentController.java#saveSkills
PUT /classes/{x}/teachers · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/TeacherAssignmentController.java#assignClassTeachers
PUT /education-plans/class-docs/week/{x}/adjustments · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → src/main/java/com/carenest/controller/eduplan/EduClassDocController.java#saveAdjustments
PUT /education-plans/class-docs/{x}/{x} · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → src/main/java/com/carenest/controller/eduplan/EduClassDocController.java#saveClassDoc
PUT /education-plans/config · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → src/main/java/com/carenest/controller/eduplan/EduConfigController.java#updateEduConfig
PUT /education-plans/lessons/{x} · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → (BE chưa có — pending)
PUT /education-plans/themes/{x} · src/services/education-plan/api/educationPlanApi.js [education-plan: api] → (BE chưa có — pending)
PUT /health-checks/{x} · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthCheckController.java#updateHealthCheck
PUT /health-checks/{x}/children/{x}/exam · src/services/children/api/healthCheckApi.js [children: demo] → src/main/java/com/carenest/controller/health/HealthExamController.java#saveExam
PUT /inspections/day/{x} · src/services/kitchen/api/inspectionApi.js [kitchen-inspection: api] → src/main/java/com/carenest/controller/meals/InspectionController.java#saveSheet
PUT /kitchen/food-samples/{x}/{x}/{x} · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
PUT /kitchen/fresh-receipts · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
PUT /kitchen/fresh-receipts/supplier-notified · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
PUT /kitchen/stock-receipts/{x} · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
PUT /kitchen/supplier-changes · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
PUT /kitchen/suppliers/{x} · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
PUT /kitchen/weekly-forecasts · src/services/kitchen/api/kitchenApi.js [kitchen: demo] → (BE chưa có)
PUT /locations/{x} · src/services/masterDataService.js [master-data: api] → src/main/java/com/carenest/controller/organization/RoomController.java#updateRoom
PUT /me/password · src/services/account/api/accountApi.js [account: api] → src/main/java/com/carenest/controller/account/PasswordController.java#changePassword
PUT /menu/foods/bulk · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
PUT /menu/foods/{x}/favorite · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
PUT /menu/nutrition-setups · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
PUT /menu/weekly-menus/{x}/days/{x} · src/services/menu-planning/api/menuPlanningApi.js [menu-planning: demo] → (BE chưa có)
PUT /nutrition/dishes/{x} · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/NutritionDishController.java#updateDish
PUT /nutrition/foods/{x} · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/NutritionFoodController.java#updateFood
PUT /nutrition/fresh-checks · src/services/freshGoodsApi.js [fresh-goods: api] → src/main/java/com/carenest/controller/meals/FreshGoodsController.java#recordCheck
PUT /nutrition/meal-photo-windows · src/services/kitchenRecordsApi.js [kitchen-records: api] → src/main/java/com/carenest/controller/meals/KitchenRecordsController.java#updateWindows
PUT /nutrition/meal-plans/{x} · src/services/mealPlanApi.js [meal-plan: api] → src/main/java/com/carenest/controller/meals/MealPlanController.java#saveMealPlan
PUT /nutrition/menu-templates/{x} · src/services/menuTemplateApi.js [menu-template: api] → src/main/java/com/carenest/controller/meals/MenuTemplateController.java#updateTemplate
PUT /nutrition/stock/receipts/{x} · src/services/dryStockApi.js [dry-stock: api] → src/main/java/com/carenest/controller/meals/StockController.java#updateReceipt
PUT /nutrition/suppliers/{x} · src/services/nutritionCatalogApi.js [nutrition-catalog: api] → src/main/java/com/carenest/controller/meals/SupplierController.java#updateSupplier
PUT /role-permissions/{x} · src/services/account/api/permissionApi.js [permission: api] → src/main/java/com/carenest/controller/account/PermissionController.java#updateRolePermissions
PUT /role-permissions/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/account/PermissionController.java#updateRolePermissions
PUT /school-years/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/SchoolYearController.java#updateSchoolYear
PUT /school-years/{x}/cutoff · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/AttendanceCutoffController.java#saveCutoff
PUT /school-years/{x}/holidays · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/SchoolYearController.java#replaceHolidays
PUT /school-years/{x}/placements · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/child/YearTransitionController.java#savePlacements
PUT /school-years/{x}/semester · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/SchoolYearController.java#updateSemester
PUT /school-years/{x}/vice-principal-assignments/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/VicePrincipalAssignmentController.java#assign
PUT /school/appearance · src/services/appearance/api/appearanceApi.js [appearance: demo] → (BE chưa có)
PUT /team-leader-assignments · src/services/school-config/api/schoolConfigApi.js [school-config: api] → src/main/java/com/carenest/controller/organization/TeacherAssignmentController.java#assignTeamLeader
PUT /users/{x} · src/services/school-config/api/schoolConfigApi.js [school-config: api] → (BE chưa có — pending)
```
