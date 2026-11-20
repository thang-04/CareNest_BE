#!/usr/bin/env node
// Sinh src/main/resources/db/dev/R__dev_02_catalog.sql: danh mục bán trú của MN Thượng Hồng — thực phẩm (số liệu Viện
// Dinh dưỡng), món + định lượng 4 nhóm tuổi, định mức nhóm tuổi, nhà cung cấp, bộ thực đơn mẫu 4 tuần.
// Chạy riêng: node scripts/dev/dataset/catalog.mjs <đường-dẫn>/nutrition_foods.json
// Tất định: cùng file nguồn ⇒ cùng SQL. Trước khi ghi, tự kiểm mọi ngày của thực đơn mẫu theo đúng cách tính của
// MealPlanEvaluator (BE) và dừng nếu có ngày không cân đối, để kế hoạch tuần sao từ mẫu công bố được.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const AGE_GROUPS = ['ag-2', 'ag-3', 'ag-4', 'ag-5'];
const NURSERY = 'ag-2';
// Định lượng nhóm tuổi = định lượng gốc (mẫu giáo lớn) × hệ số; nhà trẻ ăn cơm nát, khẩu phần nhỏ hơn
const FACTOR = { 'ag-2': 0.74, 'ag-3': 0.93, 'ag-4': 0.965, 'ag-5': 1.0 };
const PRICE_NOTE = 'Giá tham khảo chợ Hải Phòng tháng 10/2026';
const SOURCE = 'Bảng thành phần thực phẩm Việt Nam – Viện Dinh dưỡng';
const SOURCE_VERSION = 'viendinhduong.vn, tra cứu 09/2026';

// [mã Viện, tên hiển thị, nhóm, kho (D khô/F tươi), đơn vị mua, cỡ đơn vị mua (g|ml), giá/đơn vị mua, % thải bỏ, đơn vị gốc]
const FOODS = [
  ['1004', 'Gạo tẻ', 'GRAIN', 'D', 'kg', 1000, 19000, 0],
  ['1002', 'Gạo nếp', 'GRAIN', 'D', 'kg', 1000, 32000, 0],
  ['1022', 'Mì sợi khô', 'GRAIN', 'D', 'gói 500 g', 500, 18000, 0],
  ['2015', 'Miến dong', 'GRAIN', 'D', 'gói 500 g', 500, 42000, 0],
  ['1020', 'Bún tươi', 'GRAIN', 'F', 'kg', 1000, 15000, 0],
  ['1013', 'Bánh phở', 'GRAIN', 'F', 'kg', 1000, 18000, 0],
  ['1007', 'Ngô nếp bắp tươi', 'GRAIN', 'F', 'kg', 1000, 16000, 55],
  ['2008', 'Khoai lang', 'GRAIN', 'F', 'kg', 1000, 22000, 14],
  ['2014', 'Khoai tây', 'GRAIN', 'F', 'kg', 1000, 26000, 12],
  ['7017', 'Thịt lợn nạc', 'MEAT', 'F', 'kg', 1000, 145000, 2],
  ['7083', 'Thịt lợn nạc vai', 'MEAT', 'F', 'kg', 1000, 135000, 2],
  ['7053', 'Sườn lợn non', 'MEAT', 'F', 'kg', 1000, 150000, 35],
  ['7005', 'Thịt bò thăn', 'MEAT', 'F', 'kg', 1000, 280000, 3],
  ['7013', 'Thịt gà ta', 'MEAT', 'F', 'kg', 1000, 130000, 35],
  ['7088', 'Đùi gà công nghiệp', 'MEAT', 'F', 'kg', 1000, 75000, 25],
  ['8092', 'Cá basa phi lê', 'SEAFOOD', 'F', 'kg', 1000, 85000, 0],
  ['8026', 'Cá thu', 'SEAFOOD', 'F', 'kg', 1000, 190000, 20],
  ['8051', 'Tôm biển', 'SEAFOOD', 'F', 'kg', 1000, 260000, 35],
  ['8034', 'Cua đồng', 'SEAFOOD', 'F', 'kg', 1000, 130000, 55],
  ['9001', 'Trứng gà', 'EGG_DAIRY', 'F', 'quả', 55, 3800, 12],
  ['9007', 'Trứng chim cút', 'EGG_DAIRY', 'F', 'quả', 10, 800, 12],
  ['10001', 'Sữa bò tươi', 'EGG_DAIRY', 'F', 'hộp 180 ml', 180, 8500, 0, 'ml'],
  ['10004', 'Sữa chua', 'EGG_DAIRY', 'F', 'hộp 100 g', 100, 6500, 0],
  ['3025', 'Đậu phụ', 'LEGUME', 'F', 'bìa 250 g', 250, 6000, 0],
  ['3010', 'Đậu xanh tách vỏ', 'LEGUME', 'D', 'kg', 1000, 58000, 0],
  ['3017', 'Lạc nhân', 'LEGUME', 'D', 'kg', 1000, 75000, 0],
  ['3020', 'Vừng trắng', 'LEGUME', 'D', 'gói 200 g', 200, 22000, 0],
  ['4002', 'Bí đao', 'VEGETABLE', 'F', 'kg', 1000, 16000, 20],
  ['4003', 'Bí đỏ', 'VEGETABLE', 'F', 'kg', 1000, 20000, 18],
  ['4005', 'Cà chua', 'VEGETABLE', 'F', 'kg', 1000, 28000, 5],
  ['4007', 'Cà rốt', 'VEGETABLE', 'F', 'kg', 1000, 22000, 12],
  ['4010', 'Bắp cải', 'VEGETABLE', 'F', 'kg', 1000, 16000, 12],
  ['4016', 'Cải xanh', 'VEGETABLE', 'F', 'kg', 1000, 20000, 15],
  ['4108', 'Cải ngọt', 'VEGETABLE', 'F', 'kg', 1000, 22000, 12],
  ['4015', 'Cải thìa', 'VEGETABLE', 'F', 'kg', 1000, 24000, 10],
  ['4038', 'Hành lá', 'VEGETABLE', 'F', 'kg', 1000, 40000, 10],
  ['4039', 'Hành tây', 'VEGETABLE', 'F', 'kg', 1000, 24000, 10],
  ['4037', 'Hành khô', 'VEGETABLE', 'F', 'kg', 1000, 60000, 15],
  ['4054', 'Mướp', 'VEGETABLE', 'F', 'kg', 1000, 22000, 12],
  ['4083', 'Rau muống', 'VEGETABLE', 'F', 'kg', 1000, 18000, 25],
  ['4080', 'Rau mồng tơi', 'VEGETABLE', 'F', 'kg', 1000, 18000, 15],
  ['4098', 'Su su', 'VEGETABLE', 'F', 'kg', 1000, 18000, 12],
  ['4099', 'Súp lơ trắng', 'VEGETABLE', 'F', 'kg', 1000, 38000, 30],
  ['4100', 'Súp lơ xanh', 'VEGETABLE', 'F', 'kg', 1000, 45000, 30],
  ['4129', 'Nấm rơm', 'VEGETABLE', 'F', 'kg', 1000, 90000, 8],
  ['4127', 'Nấm hương tươi', 'VEGETABLE', 'F', 'kg', 1000, 110000, 8],
  ['4029', 'Đậu cô ve', 'VEGETABLE', 'F', 'kg', 1000, 32000, 8],
  ['5002', 'Cam sành', 'FRUIT', 'F', 'kg', 1000, 32000, 30],
  ['5007', 'Chuối tiêu', 'FRUIT', 'F', 'kg', 1000, 25000, 35],
  ['5011', 'Dưa hấu', 'FRUIT', 'F', 'kg', 1000, 15000, 40],
  ['5017', 'Đu đủ chín', 'FRUIT', 'F', 'kg', 1000, 22000, 25],
  ['5044', 'Thanh long', 'FRUIT', 'F', 'kg', 1000, 35000, 30],
  ['5051', 'Táo', 'FRUIT', 'F', 'kg', 1000, 55000, 10],
  ['13003', 'Gừng tươi', 'OTHER', 'F', 'kg', 1000, 45000, 10],
  ['13017', 'Nước mắm cá', 'OTHER', 'D', 'chai 500 ml', 500, 38000, 0, 'ml'],
  ['13005', 'Muối i-ốt', 'OTHER', 'D', 'gói 500 g', 500, 5000, 0],
  ['6010', 'Dầu đậu nành', 'FAT_SUGAR', 'D', 'chai 1 lít', 1000, 52000, 0, 'ml'],
  ['12014', 'Đường kính trắng', 'FAT_SUGAR', 'D', 'kg', 1000, 24000, 0],
];
const ALLERGENS = {
  8051: 'SHRIMP', 8034: 'CRAB', 8092: 'FISH', 8026: 'FISH', 13017: 'FISH', 9001: 'EGG', 9007: 'EGG',
  10001: 'COW_MILK', 10004: 'COW_MILK', 3017: 'PEANUT', 3025: 'SOY', 6010: 'SOY', 3020: 'SESAME', 1022: 'WHEAT',
};
// Nước mắm làm từ cá ⇒ đạm/béo tính vào động vật
const ANIMAL_GROUPS = new Set(['MEAT', 'SEAFOOD', 'EGG_DAIRY']);
const ANIMAL_CODES = new Set(['13017']);

// ---- Món: [mã, tên, loại, kcal mục tiêu/trẻ mẫu giáo lớn, [[mã Viện, gam tỉ lệ]], mô tả] ----
// Định lượng gốc chỉ là tỉ lệ; generator co giãn cả món để đạt kcal mục tiêu rồi nhân hệ số nhóm tuổi.
const DISHES = [
  ['M001', 'Cơm tẻ', 'CARB', 0, [['1004', 66]], 'Cơm nấu mềm; nhà trẻ nấu nát hơn'],
  // Món mặn bữa trưa
  ['M101', 'Thịt gà rim gừng', 'MAIN', 128, [['7013', 50], ['13003', 2], ['4037', 4], ['13017', 3], ['6010', 3]], 'Gà ta chặt nhỏ, lọc xương cho trẻ'],
  ['M102', 'Cá basa sốt cà chua', 'MAIN', 128, [['8092', 50], ['4005', 30], ['4038', 3], ['6010', 4.5]], 'Cá phi lê, kiểm kỹ xương'],
  ['M103', 'Thịt bò xào hành tây', 'MAIN', 128, [['7005', 42], ['4039', 25], ['6010', 5.5]], 'Bò thái mỏng, xào mềm'],
  ['M104', 'Tôm thịt rim', 'MAIN', 128, [['8051', 28], ['7017', 18], ['4037', 3], ['13017', 3], ['6010', 4.5]], ''],
  ['M105', 'Đậu phụ sốt thịt băm', 'MAIN', 128, [['3025', 55], ['7017', 18], ['4005', 25], ['4038', 2], ['6010', 3]], ''],
  ['M106', 'Trứng đúc thịt', 'MAIN', 128, [['9001', 35], ['7017', 18], ['4038', 3], ['6010', 3]], ''],
  ['M107', 'Thịt viên sốt cà chua', 'MAIN', 128, [['7083', 45], ['4005', 25], ['4038', 3], ['6010', 3]], 'Thịt nạc vai xay, viên nhỏ'],
  ['M108', 'Thịt kho trứng cút', 'MAIN', 128, [['7083', 32], ['9007', 16], ['13017', 3], ['12014', 2], ['6010', 2.5]], ''],
  ['M109', 'Cá thu sốt cà chua', 'MAIN', 128, [['8026', 42], ['4005', 25], ['4038', 2], ['6010', 2]], 'Cá thu cắt khúc, gỡ xương trước khi chia'],
  ['M110', 'Gà om nấm rơm', 'MAIN', 128, [['7088', 48], ['4129', 18], ['4037', 3], ['6010', 3.5]], ''],
  ['M111', 'Sườn rim chua ngọt', 'MAIN', 128, [['7053', 50], ['4005', 15], ['12014', 2], ['6010', 2]], 'Sườn non chặt miếng nhỏ'],
  // Canh
  ['M201', 'Canh cua rau muống', 'SOUP', 46, [['8034', 22], ['4083', 40], ['4005', 10], ['6010', 2]], ''],
  ['M202', 'Canh bí đỏ thịt băm', 'SOUP', 46, [['4003', 50], ['7017', 12], ['4038', 2], ['6010', 1.5]], ''],
  ['M203', 'Canh cải xanh thịt băm', 'SOUP', 46, [['4016', 50], ['7017', 12], ['4038', 2], ['6010', 1.5]], ''],
  ['M204', 'Canh mồng tơi nấu mướp', 'SOUP', 46, [['4080', 40], ['4054', 30], ['7017', 10], ['6010', 1.5]], ''],
  ['M205', 'Canh bắp cải cà rốt', 'SOUP', 46, [['4010', 40], ['4007', 15], ['7017', 10], ['6010', 1.5]], ''],
  ['M206', 'Canh su su cà rốt', 'SOUP', 46, [['4098', 40], ['4007', 15], ['7017', 10], ['6010', 1.5]], ''],
  ['M207', 'Canh nấm rơm cải ngọt', 'SOUP', 46, [['4129', 25], ['4108', 30], ['7017', 8], ['6010', 1]], ''],
  ['M208', 'Canh bí đao nấu tôm', 'SOUP', 46, [['4002', 50], ['8051', 10], ['4038', 2], ['6010', 2]], ''],
  // Rau
  ['M301', 'Su su xào tỏi', 'VEGETABLE', 40, [['4098', 60], ['4037', 2], ['6010', 3]], ''],
  ['M302', 'Cải ngọt xào', 'VEGETABLE', 40, [['4108', 60], ['6010', 3]], ''],
  ['M303', 'Súp lơ xào cà rốt', 'VEGETABLE', 40, [['4100', 40], ['4007', 15], ['6010', 3]], ''],
  ['M304', 'Bắp cải xào', 'VEGETABLE', 40, [['4010', 55], ['6010', 3]], ''],
  ['M305', 'Đậu cô ve xào', 'VEGETABLE', 40, [['4029', 40], ['4007', 10], ['6010', 2.5]], ''],
  ['M306', 'Cải thìa xào nấm hương', 'VEGETABLE', 40, [['4015', 50], ['4127', 10], ['6010', 3]], ''],
  // Bữa chiều (món chính bữa chiều)
  ['M401', 'Cháo gà bí đỏ', 'CARB', 178, [['1004', 25], ['7013', 22], ['4003', 20], ['4038', 2], ['6010', 2.5]], ''],
  ['M402', 'Cháo cá basa', 'CARB', 178, [['1004', 25], ['8092', 25], ['4038', 2], ['6010', 3.5]], ''],
  ['M403', 'Miến gà', 'CARB', 178, [['2015', 25], ['7013', 22], ['4038', 2], ['6010', 2]], ''],
  ['M404', 'Bún thịt nạc nấu cà chua', 'CARB', 178, [['1020', 90], ['7017', 22], ['4005', 15], ['4038', 2], ['6010', 3]], ''],
  ['M405', 'Phở bò', 'CARB', 178, [['1013', 75], ['7005', 22], ['4038', 2], ['6010', 3.5]], ''],
  ['M406', 'Xôi đậu xanh', 'CARB', 178, [['1002', 32], ['3010', 10], ['6010', 3.5]], 'Đậu xanh đồ chín, tráng mỡ hành'],
  ['M407', 'Mì nấu thịt bò cải xanh', 'CARB', 178, [['1022', 28], ['7005', 20], ['4016', 20], ['6010', 3]], ''],
  ['M408', 'Cháo thịt băm cà rốt', 'CARB', 178, [['1004', 25], ['7017', 22], ['4007', 15], ['6010', 3]], ''],
  ['M409', 'Súp gà ngô nấm', 'CARB', 178, [['1007', 55], ['7013', 20], ['2014', 25], ['6010', 2]], ''],
  ['M410', 'Cháo tôm bí đỏ', 'CARB', 178, [['1004', 25], ['8051', 18], ['4003', 20], ['6010', 4]], ''],
  // Tráng miệng, quả
  ['M501', 'Sữa chua', 'SNACK', 66, [['10004', 1]], ''],
  ['M502', 'Chuối tiêu', 'SNACK', 66, [['5007', 1]], ''],
  ['M503', 'Cam sành', 'SNACK', 66, [['5002', 1]], ''],
  ['M504', 'Dưa hấu', 'SNACK', 66, [['5011', 1]], ''],
  ['M505', 'Đu đủ chín', 'SNACK', 66, [['5017', 1]], ''],
  ['M506', 'Thanh long', 'SNACK', 66, [['5044', 1]], ''],
  ['M507', 'Táo', 'SNACK', 66, [['5051', 1]], ''],
  ['M508', 'Chè đậu xanh', 'DESSERT', 66, [['3010', 10], ['12014', 6]], ''],
  // Bữa phụ sáng nhà trẻ
  ['M601', 'Sữa tươi', 'SNACK', 158, [['10001', 1]], 'Sữa tươi tiệt trùng, hâm ấm'],
  ['M602', 'Sữa chua chuối', 'SNACK', 158, [['10004', 3], ['5007', 2]], ''],
  ['M603', 'Bánh flan sữa', 'DESSERT', 158, [['9001', 22], ['10001', 60], ['12014', 6]], 'Hấp cách thủy'],
];

// ---- Định mức nhóm tuổi (chương trình GDMN, 01/VBHN-BGDĐT): năng lượng tại trường, cơ cấu P:L:G ----
const NORM_BASIS_NT = 'Chương trình GDMN (văn bản hợp nhất 01/VBHN-BGDĐT): nhà trẻ ăn tại trường 600–651 kcal (60–70% nhu cầu cả ngày), đạm 13–20%, béo 30–40%, bột đường 40–57% năng lượng';
const NORM_BASIS_MG = 'Chương trình GDMN (văn bản hợp nhất 01/VBHN-BGDĐT): mẫu giáo ăn tại trường 615–726 kcal (50–55% nhu cầu cả ngày), đạm 13–20%, béo 25–35%, bột đường 52–60% năng lượng';
// Vi chất mỗi trẻ mỗi ngày trên các bữa ở trường (khoảng 50–60% nhu cầu khuyến nghị); max trống = chỉ cần đạt tối thiểu
const MICRO_NT = { calcium: [180, null], phosphorus: [200, null], iron: [3, null], vitaminA: [180, null], vitaminB1: [0.25, null], vitaminB2: [0.25, null], vitaminPp: [3, null], vitaminC: [15, null] };
const MICRO_MG = { calcium: [200, null], phosphorus: [250, null], iron: [3.5, null], vitaminA: [200, null], vitaminB1: [0.3, null], vitaminB2: [0.3, null], vitaminPp: [4, null], vitaminC: [18, null] };
const CONFIGS = {
  'ag-2': { price: 32000, kcal: [600, 651], p: [13, 20], l: [30, 40], g: [40, 57], need: 980, morning: [8, 15], lunch: [28, 35], afternoon: [15, 25], basis: NORM_BASIS_NT, micro: MICRO_NT },
  'ag-3': { price: 34000, kcal: [615, 726], p: [13, 20], l: [25, 35], g: [52, 60], need: 1290, morning: null, lunch: [28, 35], afternoon: [15, 25], basis: NORM_BASIS_MG, micro: MICRO_MG },
  'ag-4': { price: 34000, kcal: [615, 726], p: [13, 20], l: [25, 35], g: [52, 60], need: 1290, morning: null, lunch: [28, 35], afternoon: [15, 25], basis: NORM_BASIS_MG, micro: MICRO_MG },
  'ag-5': { price: 34000, kcal: [615, 726], p: [13, 20], l: [25, 35], g: [52, 60], need: 1290, morning: null, lunch: [28, 35], afternoon: [15, 25], basis: NORM_BASIS_MG, micro: MICRO_MG },
};

// ---- Nhà cung cấp: hộ kinh doanh/công ty quanh Thượng Hồng (tên, địa chỉ giả nhưng hợp lệ) ----
// [tên, SĐT, địa chỉ, người giao hàng, CCCD người giao]
const SUPPLIERS = [
  ['Hộ kinh doanh Thực phẩm sạch Hùng Liên', '0912486735', 'Số 15 chợ Thượng Hồng, Xã Thượng Hồng, Thành phố Hải Phòng', 'Phạm Văn Hùng', '030082014576'],
  ['Hộ kinh doanh Thủy sản Minh Thu', '0975314268', 'Thôn Hà Tiên, Xã Thượng Hồng, Thành phố Hải Phòng', 'Nguyễn Thị Thu', '030187005129'],
  ['Hợp tác xã Rau an toàn Đường An', '0384627190', 'Thôn An Thái, Xã Đường An, Thành phố Hải Phòng', 'Vũ Văn Toàn', '030090021874'],
  ['Công ty TNHH Lương thực Bình Giang', '02203765418', 'Cụm công nghiệp Nhân Quyền, Xã Bình Giang, Thành phố Hải Phòng', 'Đỗ Văn Khải', '030085031462'],
  ['Đại lý Sữa và Trứng Hạnh Phúc', '0868254917', 'Số 42 đường 392, Xã Thanh Miện, Thành phố Hải Phòng', 'Trần Thị Hạnh', '031189027305'],
  ['Hộ kinh doanh Hoa quả Ngọc Lâm', '0947102853', 'Thôn Ngọc Lâm, Xã Thượng Hồng, Thành phố Hải Phòng', 'Lê Thị Lan', '030194016238'],
];

// ---- Thực đơn mẫu 4 tuần × 5 ngày (xoay vòng) ----
const MAINS = ['M101', 'M102', 'M103', 'M104', 'M105', 'M106', 'M107', 'M108', 'M109', 'M110', 'M111'];
const SOUPS = ['M201', 'M202', 'M203', 'M204', 'M205', 'M206', 'M207', 'M208'];
const VEGS = ['M301', 'M302', 'M303', 'M304', 'M305', 'M306'];
const PM_CARBS = ['M401', 'M402', 'M403', 'M404', 'M405', 'M406', 'M407', 'M408', 'M409', 'M410'];
const PM_FRUITS = ['M501', 'M502', 'M503', 'M505', 'M508', 'M504', 'M506', 'M501', 'M507', 'M502'];
const NT_AM = ['M601', 'M602', 'M601', 'M603', 'M601'];
export const TEMPLATE_SETS = [
  ['Thực đơn 4 tuần năm học 2026-2027 - Nhà trẻ', 'NURSERY'],
  ['Thực đơn 4 tuần năm học 2026-2027 - Mẫu giáo', 'KINDERGARTEN'],
];
// Chọn món từng ngày: thử tổ hợp gần vòng xoay mặc định nhất, không lặp món mặn/món chiều trong tuần, lấy tổ hợp đầu tiên
// cân đối cho mọi nhóm tuổi của khối (planTemplates gán vào PLAN trước khi render).
const PLAN = new Map();
function templateItems(block) {
  return PLAN.get(block);
}
function dayItemsFor(block, w, d, [main, soup, veg, carb, fruit], i) {
  const out = [];
  if (block === 'NURSERY') out.push([w, d, 'MORNING', NT_AM[i % NT_AM.length], 0]);
  ['M001', main, soup, veg].forEach((dish, n) => out.push([w, d, 'LUNCH', dish, n]));
  [carb, fruit].forEach((dish, n) => out.push([w, d, 'AFTERNOON', dish, n]));
  return out;
}
// Mọi bộ 5 độ lệch có tổng bằng span (thứ tự cố định ⇒ tất định)
function offsets(span) {
  const out = [];
  const rec = (k, left, acc) => {
    if (k === 4) { out.push([...acc, left]); return; }
    for (let v = 0; v <= left; v++) rec(k + 1, left - v, [...acc, v]);
  };
  rec(0, span, []);
  return out;
}
function planTemplates(dishBy, foodBy) {
  const lists = [MAINS, SOUPS, VEGS, PM_CARBS, PM_FRUITS];
  for (const [, block] of TEMPLATE_SETS) {
    const items = [];
    for (let w = 1; w <= 4; w++) {
      const usedMain = new Set();
      const usedCarb = new Set();
      for (let d = 1; d <= 5; d++) {
        const i = (w - 1) * 5 + (d - 1);
        let chosen = null;
        let good = null;
        let fallback = null;
        search: for (let span = 0; span < 14; span++) {
          for (const off of offsets(span)) {
            const combo = lists.map((list, k) => list[(i + off[k]) % list.length]);
            if (usedMain.has(combo[0]) || usedCarb.has(combo[3])) continue;
            const day = dayItemsFor(block, w, d, combo, i);
            fallback ??= day;
            const r = evalDay(block, w, d, day, dishBy, foodBy);
            if (r.problems.length === 0 && r.micro.length === 0) { chosen = day; break search; }
            if (r.problems.length === 0) good ??= day;
          }
        }
        const day = chosen ?? good ?? fallback;
        usedMain.add(day.find((x) => x[2] === 'LUNCH' && x[4] === 1)[3]);
        usedCarb.add(day.find((x) => x[2] === 'AFTERNOON' && x[4] === 0)[3]);
        items.push(...day);
      }
    }
    PLAN.set(block, items);
  }
}

// ================= Tính toán =================
const num = (v) => (v == null || v === '' ? 0 : Number(v));
const opt = (v) => (v == null || v === '' ? 0 : Number(v));
const r1 = (x) => Math.round((x + Number.EPSILON) * 10) / 10;
const r2 = (x) => Math.round((x + Number.EPSILON) * 100) / 100;

function buildFoods(rows) {
  const byCode = new Map(rows.map((r) => [String(r.code), r]));
  return FOODS.map(([code, name, group, st, pu, size, price, waste, unit = 'g']) => {
    const r = byCode.get(code);
    if (!r) throw new Error(`Không có mã Viện ${code}`);
    const kcal = num(r.energy_kcal);
    const p = num(r.protein_g);
    const l = num(r.total_lipid_fat_g);
    const g = num(r.carbohydrate_by_difference_g);
    // Số liệu Viện để trống đạm/béo/bột (≈ 0) nên kiểm lại theo công thức 4-9-4
    const est = 4 * p + 9 * l + 4 * g;
    if (Math.abs(kcal - est) > Math.max(15, 0.15 * kcal)) throw new Error(`Mã ${code} lệch 4-9-4: ${kcal} vs ${est}`);
    // Vi chất: ô trống trong bảng Viện coi như không đáng kể (0); vitamin A lấy RAE, thiếu thì retinol + β-caroten/12
    const vitA = r.vit_a_rae_mcg != null ? Number(r.vit_a_rae_mcg) : opt(r.retinol_mcg) + opt(r.carotene_mcg) / 12;
    const micro = {
      calcium: opt(r.ca_mg), phosphorus: opt(r.p_mg), iron: opt(r.fe_mg), vitaminA: r2(vitA),
      vitaminB1: opt(r.thiamin_mg), vitaminB2: opt(r.riboflavin_mg), vitaminPp: opt(r.niacin_mg), vitaminC: opt(r.vit_c_mg),
    };
    const origin = ANIMAL_GROUPS.has(group) || ANIMAL_CODES.has(code) ? 'ANIMAL' : 'PLANT';
    return { code, fcode: `VDD${code}`, name, group, storage: st === 'D' ? 'DRY' : 'FRESH', pu, size, price, waste, unit,
      kcal, p, l, g, micro, origin, allergen: ALLERGENS[code] ?? '' };
  });
}

const gramRound = (x) => (x < 20 ? Math.max(0.5, Math.round(x * 2) / 2) : Math.round(x));

// Món đã co giãn: ingredients[foodCode] = { 'ag-2': g, ... }
function buildDishes(foodBy) {
  return DISHES.map(([code, name, type, target, items, description]) => {
    const parts = items.filter(([fc, g]) => g > 0 && foodBy.has(fc));
    const kcalBase = parts.reduce((s, [fc, g]) => s + (foodBy.get(fc).kcal * g) / 100, 0);
    const scale = target > 0 ? target / kcalBase : 1;
    const ingredients = parts.map(([fc, g]) => {
      const base = g * scale;
      return { food: fc, grams: Object.fromEntries(AGE_GROUPS.map((ag) => [ag, gramRound(base * FACTOR[ag])])) };
    });
    return { code, name, type, description, ingredients };
  });
}

// Cùng cách tính với MealPlanEvaluator.dishTotals: làm tròn 0,1 theo món, bữa, ngày
function dishTotals(dish, ag, foodBy) {
  let kcal = 0, glucid = 0, ap = 0, pp = 0, al = 0, pl = 0, cost = 0;
  const micro = {};
  for (const ing of dish.ingredients) {
    const f = foodBy.get(ing.food);
    const ratio = ing.grams[ag] / 100;
    const bought = f.waste > 0 && f.waste < 100 ? ing.grams[ag] / (1 - f.waste / 100) : ing.grams[ag];
    cost += (f.price * bought) / f.size;
    kcal += f.kcal * ratio; glucid += f.g * ratio;
    if (f.origin === 'ANIMAL') { ap += f.p * ratio; al += f.l * ratio; } else { pp += f.p * ratio; pl += f.l * ratio; }
    for (const [k, v] of Object.entries(f.micro)) micro[k] = (micro[k] ?? 0) + v * ratio;
  }
  const t = { kcal: r1(kcal), glucid: r1(glucid), ap: r1(ap), pp: r1(pp), al: r1(al), pl: r1(pl), micro, cost: Math.round(cost) };
  t.protein = r1(t.ap + t.pp); t.lipid = r1(t.al + t.pl);
  return t;
}
function addTotals(a, b) {
  const micro = { ...a.micro };
  for (const [k, v] of Object.entries(b.micro)) micro[k] = (micro[k] ?? 0) + v;
  return { kcal: a.kcal + b.kcal, glucid: a.glucid + b.glucid, protein: a.protein + b.protein, lipid: a.lipid + b.lipid,
    ap: a.ap + b.ap, pp: a.pp + b.pp, al: a.al + b.al, pl: a.pl + b.pl, micro, cost: (a.cost ?? 0) + (b.cost ?? 0) };
}
const ZERO = () => ({ kcal: 0, glucid: 0, protein: 0, lipid: 0, ap: 0, pp: 0, al: 0, pl: 0, micro: {}, cost: 0 });
const roundT = (t) => ({ kcal: r1(t.kcal), glucid: r1(t.glucid), protein: r1(t.protein), lipid: r1(t.lipid), ap: r1(t.ap),
  pp: r1(t.pp), al: r1(t.al), pl: r1(t.pl), micro: Object.fromEntries(Object.entries(t.micro).map(([k, v]) => [k, r2(v)])),
  cost: t.cost ?? 0 });

function evalDay(block, w, d, dayItems, dishBy, foodBy) {
  const problems = [];
  const warnings = [];
  const micro = [];
  const rows = [];
  const groups = block === 'NURSERY' ? [NURSERY] : ['ag-3', 'ag-4', 'ag-5'];
  for (const ag of groups) {
    const cfg = CONFIGS[ag];
    let day = ZERO();
    const sessionKcal = {};
    for (const session of ['MORNING', 'LUNCH', 'AFTERNOON']) {
      let s = ZERO();
      dayItems.filter((x) => x[2] === session).forEach((x) => { s = addTotals(s, dishTotals(dishBy.get(x[3]), ag, foodBy)); });
      s = roundT(s);
      day = addTotals(day, s);
      if (dayItems.some((x) => x[2] === session)) sessionKcal[session] = s.kcal;
    }
    day = roundT(day);
    const pct = (factor, v) => r1((factor * v * 100) / day.kcal);
    const row = { kcal: day.kcal, p: pct(4, day.protein), l: pct(9, day.lipid), g: pct(4, day.glucid) };
    rows.push([`${block}/${ag}`, { ...row, ap: day.ap, pp: day.pp, al: day.al, pl: day.pl, glucid: day.glucid, micro: day.micro }]);
    const where = `${block} tuần ${w} thứ ${d + 1} ${ag}`;
    const out = (name, v, [lo, hi]) => { if (v < lo || v > hi) problems.push(`${where}: ${name} ${v} ngoài ${lo}–${hi}`); };
    out('kcal', row.kcal, cfg.kcal); out('đạm%', row.p, cfg.p); out('béo%', row.l, cfg.l); out('bột%', row.g, cfg.g);
    if (day.cost > cfg.price) problems.push(`${where}: chi phí ${day.cost} > giá suất ${cfg.price}`);
    for (const [session, range] of [['MORNING', cfg.morning], ['LUNCH', cfg.lunch], ['AFTERNOON', cfg.afternoon]]) {
      if (!range || sessionKcal[session] == null) continue;
      const p = r1((sessionKcal[session] * 100) / cfg.need);
      if (p < range[0] || p > range[1]) warnings.push(`${where}: bữa ${session} ${p}% nhu cầu`);
    }
    for (const [k, [lo]] of Object.entries(cfg.micro)) {
      if ((day.micro[k] ?? 0) < lo) { warnings.push(`${where}: ${k} ${day.micro[k]} < ${lo}`); micro.push(k); }
    }
  }
  return { problems, warnings, rows, micro };
}

function evaluateTemplates(dishBy, foodBy) {
  const problems = [];
  const warnings = [];
  const stats = {};
  for (const [, block] of TEMPLATE_SETS) {
    const items = templateItems(block);
    for (let w = 1; w <= 4; w++) {
      for (let d = 1; d <= 5; d++) {
        const r = evalDay(block, w, d, items.filter((x) => x[0] === w && x[1] === d), dishBy, foodBy);
        problems.push(...r.problems);
        warnings.push(...r.warnings);
        for (const [key, row] of r.rows) (stats[key] ??= []).push(row);
      }
    }
  }
  return { problems, warnings, stats };
}

// ================= Sinh SQL =================
const q = (v) => (v === null || v === undefined ? 'NULL' : typeof v === 'number' ? String(v) : `'${String(v).replace(/'/g, "''")}'`);

export function renderCatalog(rows, { check = true } = {}) {
  const foods = buildFoods(rows);
  const foodBy = new Map(foods.map((f) => [f.code, f]));
  const dishes = buildDishes(foodBy);
  const dishBy = new Map(dishes.map((d) => [d.code, d]));
  planTemplates(dishBy, foodBy);
  const { problems, warnings, stats } = evaluateTemplates(dishBy, foodBy);
  if (check && problems.length) throw new Error(`Thực đơn mẫu không cân đối:\n${problems.join('\n')}`);

  const sql = [];
  const w = (s = '') => sql.push(s);
  w('-- GENERATED bởi scripts/dev/dataset/catalog.mjs — sửa generator rồi chạy lại, không sửa tay.');
  w('-- Bộ dữ liệu dev (profile "dev"): danh mục bán trú. Dữ liệu giả nhìn như thật. Chạy sau R__dev_01_people_structure.sql.');
  w(`-- Thực phẩm: thành phần dinh dưỡng trên 100 g/ml ăn được theo ${SOURCE}; giá, % thải bỏ, công thức món,`);
  w('-- nhà cung cấp là số giả lập. Thực đơn mẫu đã qua kiểm cân đối theo định mức nhóm tuổi bên dưới.');
  w('-- Idempotent: ON CONFLICT theo mã thực phẩm/món/khóa duy nhất, NOT EXISTS theo tên NCC và tên bộ thực đơn.');
  w();

  w('-- 1. Thực phẩm (mã VDD<mã Viện>)');
  w('INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price, kcal, protein, lipid, glucid,');
  w('                   nutrient_source, nutrient_source_version, waste_pct, storage, origin, note, allergen_codes,');
  w('                   calcium, phosphorus, iron, vitamin_a, vitamin_b1, vitamin_b2, vitamin_pp, vitamin_c)');
  w('VALUES');
  w(foods.map((f) => `    (${[f.fcode, f.name, f.group, f.unit, f.pu, f.size, f.price, f.kcal, f.p, f.l, f.g, SOURCE, SOURCE_VERSION,
    f.waste, f.storage, f.origin, PRICE_NOTE, f.allergen, f.micro.calcium, f.micro.phosphorus, f.micro.iron, f.micro.vitaminA,
    f.micro.vitaminB1, f.micro.vitaminB2, f.micro.vitaminPp, f.micro.vitaminC].map(q).join(', ')})`).join(',\n'));
  w('ON CONFLICT (code) DO NOTHING;');
  w();

  w('-- 2. Món và định lượng gam/trẻ cho cả 4 nhóm tuổi');
  w('INSERT INTO dishes (code, name, dish_type, description)');
  w('VALUES');
  w(dishes.map((d) => `    (${[d.code, d.name, d.type, d.description].map(q).join(', ')})`).join(',\n'));
  w('ON CONFLICT (code) DO NOTHING;');
  w();
  const ing = [];
  for (const d of dishes) for (const i of d.ingredients) for (const ag of AGE_GROUPS) ing.push(`    (${[d.code, `VDD${i.food}`, ag, i.grams[ag]].map(q).join(', ')})`);
  w('INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child)');
  w('SELECT d.id, f.id, ag.id, v.grams');
  w('FROM (VALUES');
  w(ing.join(',\n'));
  w(') AS v (dish_code, food_code, age_group_code, grams)');
  w('JOIN dishes d ON d.code = v.dish_code');
  w('JOIN foods f ON f.code = v.food_code');
  w('JOIN age_groups ag ON ag.code = v.age_group_code');
  w('ON CONFLICT (dish_id, food_id, age_group_id) DO NOTHING;');
  w();

  w('-- 3. Định mức nhóm tuổi hiệu lực từ đầu năm học (PHT dinh dưỡng cơ sở chính cấu hình)');
  const cfgCols = ['meal_price', 'kcal_min', 'kcal_max', 'protein_pct_min', 'protein_pct_max', 'lipid_pct_min', 'lipid_pct_max',
    'glucid_pct_min', 'glucid_pct_max', 'daily_kcal_need', 'morning_kcal_pct_min', 'morning_kcal_pct_max', 'lunch_kcal_pct_min',
    'lunch_kcal_pct_max', 'afternoon_kcal_pct_min', 'afternoon_kcal_pct_max', 'calcium_min', 'phosphorus_min', 'iron_min',
    'vitamin_a_min', 'vitamin_b1_min', 'vitamin_b2_min', 'vitamin_pp_min', 'vitamin_c_min', 'norm_basis'];
  const cfgRows = AGE_GROUPS.map((ag) => {
    const c = CONFIGS[ag];
    return [ag, c.price, ...c.kcal, ...c.p, ...c.l, ...c.g, c.need, ...(c.morning ?? [null, null]), ...c.lunch, ...c.afternoon,
      c.micro.calcium[0], c.micro.phosphorus[0], c.micro.iron[0], c.micro.vitaminA[0], c.micro.vitaminB1[0], c.micro.vitaminB2[0],
      c.micro.vitaminPp[0], c.micro.vitaminC[0], c.basis];
  });
  w(`INSERT INTO age_group_meal_configs (age_group_id, effective_from, ${cfgCols.join(', ')},`);
  w('                                    created_by, updated_by, created_at, updated_at)');
  w(`SELECT ag.id, DATE '2026-08-24', ${cfgCols.map((c) => `v.${c}${c === 'norm_basis' ? '' : '::numeric'}`).join(', ')},`);
  w("       pht.user_id, pht.user_id, TIMESTAMPTZ '2026-08-24 09:00+07', TIMESTAMPTZ '2026-08-24 09:00+07'");
  w('FROM (VALUES');
  w(cfgRows.map((r) => `    (${r.map(q).join(', ')})`).join(',\n'));
  w(`) AS v (age_group_code, ${cfgCols.join(', ')})`);
  w('JOIN age_groups ag ON ag.code = v.age_group_code');
  w("LEFT JOIN LATERAL (SELECT a.user_id FROM staff_assignments a JOIN campuses c ON c.id = a.campus_id");
  w("                   WHERE a.staff_role = 'VICE_PRINCIPAL' AND c.is_main ORDER BY a.id LIMIT 1) pht ON TRUE");
  w('ON CONFLICT (age_group_id, effective_from) DO NOTHING;');
  w();

  w('-- 4. Nhà cung cấp');
  w('INSERT INTO suppliers (name, phone, address, delivery_person, id_card, created_at, updated_at)');
  w("SELECT v.name, v.phone, v.address, v.delivery_person, v.id_card, TIMESTAMPTZ '2026-08-20 14:00+07', TIMESTAMPTZ '2026-08-20 14:00+07'");
  w('FROM (VALUES');
  w(SUPPLIERS.map(([n, p, a, d, c]) => `    (${[n, p, a, d, c].map(q).join(', ')})`).join(',\n'));
  w(') AS v (name, phone, address, delivery_person, id_card)');
  w('WHERE NOT EXISTS (SELECT 1 FROM suppliers s WHERE s.name = v.name);');
  w();

  w("-- 4b. Mức giá ban đầu của thực phẩm (NUT-27): giá theo ngày hiệu lực, chưa gán NCC — PHT gán qua \"Áp dụng hợp đồng mới\"");
  w("INSERT INTO food_prices (food_id, price, effective_from, reason, created_at, updated_at)");
  w("SELECT f.id, f.unit_price, DATE '2026-08-20', 'Giá ban đầu', TIMESTAMPTZ '2026-08-20 14:00+07', TIMESTAMPTZ '2026-08-20 14:00+07'");
  w("FROM foods f");
  w("WHERE f.code LIKE 'VDD%' AND NOT EXISTS (SELECT 1 FROM food_prices p WHERE p.food_id = f.id);");
  w();

  w('-- 5. Bộ thực đơn mẫu 4 tuần (nhà trẻ 3 bữa, mẫu giáo 2 bữa theo age_groups.meal_sessions)');
  w('INSERT INTO menu_template_sets (name, season_label, menu_block, created_at, updated_at)');
  w("SELECT v.name, 'Năm học 2026-2027', v.block, TIMESTAMPTZ '2026-08-25 10:00+07', TIMESTAMPTZ '2026-08-25 10:00+07'");
  w('FROM (VALUES');
  w(TEMPLATE_SETS.map(([n, b]) => `    (${q(n)}, ${q(b)})`).join(',\n'));
  w(') AS v (name, block)');
  w('WHERE NOT EXISTS (SELECT 1 FROM menu_template_sets s WHERE s.name = v.name);');
  w();
  for (const [name, block] of TEMPLATE_SETS) {
    w('INSERT INTO menu_template_items (set_id, week_no, weekday, session, dish_id, sort_order)');
    w('SELECT s.id, v.week_no, v.weekday, v.session, d.id, v.sort_order');
    w('FROM (VALUES');
    w(templateItems(block).map(([wk, wd, ss, dish, so]) => `    (${wk}, ${wd}, '${ss}', '${dish}', ${so})`).join(',\n'));
    w(') AS v (week_no, weekday, session, dish_code, sort_order)');
    w(`JOIN menu_template_sets s ON s.name = ${q(name)}`);
    w('JOIN dishes d ON d.code = v.dish_code');
    w('ON CONFLICT (set_id, week_no, weekday, session, dish_id) DO NOTHING;');
    w();
  }
  return { sql: sql.join('\n'), foods, dishes, stats, warnings };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const src = process.argv[2];
  if (!src) {
    console.error('Thiếu đường dẫn nutrition_foods.json');
    process.exit(1);
  }
  const { sql, foods, dishes, stats, warnings } = renderCatalog(JSON.parse(readFileSync(src, 'utf8')));
  const out = resolve(dirname(fileURLToPath(import.meta.url)), '../../../src/main/resources/db/dev/R__dev_02_catalog.sql');
  writeFileSync(out, sql.replace(/\r\n/g, '\n') + '\n', 'utf8');
  for (const [key, list] of Object.entries(stats)) {
    const span = (k) => `${Math.min(...list.map((x) => x[k]))}–${Math.max(...list.map((x) => x[k]))}`;
    console.log(`${key}: kcal ${span('kcal')} P% ${span('p')} L% ${span('l')} G% ${span('g')} ĐĐV ${span('ap')} ĐTV ${span('pp')} BĐV ${span('al')} BTV ${span('pl')}`);
  }
  if (warnings.length) console.log(`Cảnh báo (không chặn công bố): ${warnings.length}\n  ${warnings.slice(0, 15).join('\n  ')}`);
  console.log(`Đã ghi ${out}: ${foods.length} thực phẩm, ${dishes.length} món, ${SUPPLIERS.length} NCC`);
}
