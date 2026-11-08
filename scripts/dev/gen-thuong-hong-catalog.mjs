// Sinh src/main/resources/db/dev/R__dev_seed_thuong_hong_catalog.sql: danh mục bán trú demo của MN Thượng Hồng.
// Chạy: node scripts/dev/gen-thuong-hong-catalog.mjs <đường-dẫn>/nutrition_foods.json
// Nguồn dinh dưỡng: bản crawl viendinhduong.vn (Viện Dinh dưỡng Quốc gia), giá trị trên 100 g/ml. Giá, hệ số thải bỏ,
// công thức món, NCC, phiếu nhập kho là số ước tính để test (không phải số thật của trường).
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const src = process.argv[2];
if (!src) {
  console.error('Thiếu đường dẫn nutrition_foods.json');
  process.exit(1);
}
const rows = JSON.parse(readFileSync(src, 'utf8'));
const byCode = new Map(rows.map((r) => [String(r.code), r]));

// [mã Viện, tên hiển thị, nhóm BE, kho (D=khô, F=tươi), đơn vị mua, cỡ đơn vị mua (g/ml), giá/đơn vị mua (VND), % thải bỏ, ĐV g|ml]
const FOODS = [
  ['1004', 'Gạo tẻ xát máy', 'GRAIN', 'D', 'kg', 1000, 18000, 0],
  ['1002', 'Gạo nếp', 'GRAIN', 'D', 'kg', 1000, 30000, 0],
  ['1018', 'Bột mì trắng', 'GRAIN', 'D', 'kg', 1000, 20000, 0],
  ['1022', 'Mỳ sợi khô', 'GRAIN', 'D', 'kg', 1000, 35000, 0],
  ['2015', 'Miến dong khô', 'GRAIN', 'D', 'kg', 1000, 70000, 0],
  ['1020', 'Bún tươi', 'GRAIN', 'F', 'kg', 1000, 22000, 0],
  ['1013', 'Bánh phở', 'GRAIN', 'F', 'kg', 1000, 25000, 0],
  ['1007', 'Ngô tươi cả bắp', 'GRAIN', 'F', 'kg', 1000, 15000, 55],
  ['2008', 'Khoai lang tươi', 'GRAIN', 'F', 'kg', 1000, 20000, 14],
  ['2014', 'Khoai tây tươi', 'GRAIN', 'F', 'kg', 1000, 25000, 12],
  ['2013', 'Khoai sọ tươi', 'GRAIN', 'F', 'kg', 1000, 30000, 15],
  ['7017', 'Thịt lợn nạc tươi', 'MEAT', 'F', 'kg', 1000, 140000, 5],
  ['7083', 'Thịt lợn nạc vai', 'MEAT', 'F', 'kg', 1000, 130000, 5],
  ['7085', 'Thịt lợn nạc thăn', 'MEAT', 'F', 'kg', 1000, 150000, 5],
  ['7053', 'Sườn lợn', 'MEAT', 'F', 'kg', 1000, 130000, 35],
  ['7005', 'Thịt bò lưng nạc', 'MEAT', 'F', 'kg', 1000, 260000, 5],
  ['7013', 'Thịt gà ta', 'MEAT', 'F', 'kg', 1000, 120000, 35],
  ['7088', 'Thịt gà công nghiệp đùi', 'MEAT', 'F', 'kg', 1000, 70000, 25],
  ['7041', 'Gan lợn', 'MEAT', 'F', 'kg', 1000, 90000, 0],
  ['7040', 'Gan gà', 'MEAT', 'F', 'kg', 1000, 80000, 0],
  ['8092', 'Cá basa phi lê', 'SEAFOOD', 'F', 'kg', 1000, 80000, 0],
  ['8003', 'Cá chép', 'SEAFOOD', 'F', 'kg', 1000, 80000, 45],
  ['8024', 'Cá rô phi', 'SEAFOOD', 'F', 'kg', 1000, 70000, 50],
  ['8026', 'Cá thu', 'SEAFOOD', 'F', 'kg', 1000, 150000, 25],
  ['8029', 'Cá trắm cỏ', 'SEAFOOD', 'F', 'kg', 1000, 90000, 45],
  ['8051', 'Tôm biển tươi', 'SEAFOOD', 'F', 'kg', 1000, 250000, 38],
  ['8034', 'Cua đồng tươi', 'SEAFOOD', 'F', 'kg', 1000, 120000, 60],
  ['8040', 'Mực tươi', 'SEAFOOD', 'F', 'kg', 1000, 200000, 20],
  ['9001', 'Trứng gà ta', 'EGG_DAIRY', 'F', 'quả', 55, 3500, 12],
  ['9007', 'Trứng chim cút', 'EGG_DAIRY', 'F', 'quả', 10, 700, 12],
  ['9004', 'Trứng vịt', 'EGG_DAIRY', 'F', 'quả', 70, 4000, 12],
  ['10004', 'Sữa chua', 'EGG_DAIRY', 'F', 'hộp 100 g', 100, 7000, 0],
  ['10009', 'Phó mát', 'EGG_DAIRY', 'F', 'hộp 120 g', 120, 30000, 0],
  ['3025', 'Đậu phụ', 'LEGUME', 'F', 'bìa 300 g', 300, 7000, 0],
  ['3010', 'Đậu xanh hạt khô', 'LEGUME', 'D', 'kg', 1000, 55000, 0],
  ['3004', 'Đậu đen hạt khô', 'LEGUME', 'D', 'kg', 1000, 50000, 0],
  ['3017', 'Lạc hạt khô', 'LEGUME', 'D', 'kg', 1000, 70000, 0],
  ['3020', 'Vừng hạt khô', 'LEGUME', 'D', 'kg', 1000, 80000, 0],
  ['4001', 'Quả bầu', 'VEGETABLE', 'F', 'kg', 1000, 18000, 25],
  ['4002', 'Bí đao', 'VEGETABLE', 'F', 'kg', 1000, 16000, 20],
  ['4003', 'Bí ngô (bí đỏ)', 'VEGETABLE', 'F', 'kg', 1000, 20000, 20],
  ['4005', 'Cà chua', 'VEGETABLE', 'F', 'kg', 1000, 30000, 5],
  ['4007', 'Cà rốt củ tươi', 'VEGETABLE', 'F', 'kg', 1000, 22000, 15],
  ['4010', 'Cải bắp trắng', 'VEGETABLE', 'F', 'kg', 1000, 18000, 15],
  ['4016', 'Cải xanh', 'VEGETABLE', 'F', 'kg', 1000, 20000, 10],
  ['4108', 'Rau cải ngọt', 'VEGETABLE', 'F', 'kg', 1000, 22000, 10],
  ['4015', 'Cải thìa', 'VEGETABLE', 'F', 'kg', 1000, 22000, 10],
  ['4109', 'Cải thảo', 'VEGETABLE', 'F', 'kg', 1000, 22000, 10],
  ['4027', 'Dưa chuột', 'VEGETABLE', 'F', 'kg', 1000, 25000, 5],
  ['4036', 'Giá đậu xanh', 'VEGETABLE', 'F', 'kg', 1000, 25000, 5],
  ['4038', 'Hành lá', 'VEGETABLE', 'F', 'kg', 1000, 40000, 10],
  ['4039', 'Hành tây', 'VEGETABLE', 'F', 'kg', 1000, 25000, 10],
  ['4037', 'Hành củ tươi', 'VEGETABLE', 'F', 'kg', 1000, 40000, 20],
  ['4054', 'Mướp', 'VEGETABLE', 'F', 'kg', 1000, 25000, 10],
  ['4083', 'Rau muống', 'VEGETABLE', 'F', 'kg', 1000, 20000, 20],
  ['4080', 'Rau mồng tơi', 'VEGETABLE', 'F', 'kg', 1000, 20000, 15],
  ['4098', 'Su su quả', 'VEGETABLE', 'F', 'kg', 1000, 25000, 10],
  ['4099', 'Súp lơ trắng', 'VEGETABLE', 'F', 'kg', 1000, 40000, 30],
  ['4100', 'Súp lơ xanh', 'VEGETABLE', 'F', 'kg', 1000, 45000, 30],
  ['4081', 'Rau mùi', 'VEGETABLE', 'F', 'kg', 1000, 60000, 10],
  ['4021', 'Củ cải trắng', 'VEGETABLE', 'F', 'kg', 1000, 20000, 15],
  ['4129', 'Nấm rơm tươi', 'VEGETABLE', 'F', 'kg', 1000, 90000, 10],
  ['4127', 'Nấm hương tươi', 'VEGETABLE', 'F', 'kg', 1000, 120000, 10],
  ['4128', 'Nấm mỡ tươi', 'VEGETABLE', 'F', 'kg', 1000, 80000, 10],
  ['4029', 'Đậu cô ve', 'VEGETABLE', 'F', 'kg', 1000, 35000, 8],
  ['5002', 'Cam', 'FRUIT', 'F', 'kg', 1000, 35000, 30],
  ['5007', 'Chuối tiêu', 'FRUIT', 'F', 'kg', 1000, 30000, 35],
  ['5011', 'Dưa hấu', 'FRUIT', 'F', 'kg', 1000, 15000, 35],
  ['5017', 'Đu đủ chín', 'FRUIT', 'F', 'kg', 1000, 25000, 20],
  ['5044', 'Thanh long', 'FRUIT', 'F', 'kg', 1000, 40000, 30],
  ['5051', 'Táo tây', 'FRUIT', 'F', 'kg', 1000, 60000, 8],
  ['5055', 'Xoài chín', 'FRUIT', 'F', 'kg', 1000, 50000, 30],
  ['5023', 'Lê', 'FRUIT', 'F', 'kg', 1000, 50000, 10],
  ['5047', 'Quýt', 'FRUIT', 'F', 'kg', 1000, 40000, 20],
  ['5014', 'Dứa ta', 'FRUIT', 'F', 'kg', 1000, 25000, 30],
  ['5013', 'Dưa lê', 'FRUIT', 'F', 'kg', 1000, 30000, 25],
  ['13003', 'Gừng tươi', 'OTHER', 'F', 'kg', 1000, 40000, 10],
  ['13017', 'Nước mắm cá', 'OTHER', 'D', 'chai 500 ml', 500, 35000, 0, 'ml'],
  ['13026', 'Bột nêm', 'OTHER', 'D', 'gói 400 g', 400, 38000, 0],
  ['13005', 'Muối ăn', 'OTHER', 'D', 'kg', 1000, 8000, 0],
];
const ALLERGENS = {
  8051: 'SHRIMP', 8034: 'CRAB', 8003: 'FISH', 8024: 'FISH', 8026: 'FISH', 8029: 'FISH', 8092: 'FISH',
  9001: 'EGG', 9007: 'EGG', 9004: 'EGG', 10004: 'COW_MILK', 10009: 'COW_MILK', 3017: 'PEANUT', 3025: 'SOY',
  3020: 'SESAME', 1018: 'WHEAT', 1022: 'WHEAT',
};
// Thực phẩm demo có sẵn trong R__dev_seed.sql (dùng lại, không tạo mới)
const DEMO = { oil: 'TP005', sugar: 'TP007' };
const fc = (vdd) => `VDD${vdd}`;

const esc = (s) => String(s).replaceAll("'", "''");
const num = (v) => (v == null || v === '' ? 0 : Number(v));

// Kiểm 4-9-4: số liệu Viện để trống đạm/béo/bột (không xác định ≈ 0) nên chỉ nhận dòng kcal khớp công thức
function foodValues(code) {
  const r = byCode.get(String(code));
  if (!r) throw new Error(`Không có mã Viện ${code}`);
  const kcal = num(r.energy_kcal);
  const p = num(r.protein_g);
  const l = num(r.total_lipid_fat_g);
  const g = num(r.carbohydrate_by_difference_g);
  const est = 4 * p + 9 * l + 4 * g;
  if (Math.abs(kcal - est) > Math.max(15, 0.15 * kcal)) throw new Error(`Mã ${code} lệch 4-9-4: ${kcal} vs ${est}`);
  return { kcal, p, l, g };
}

// ---- Món: [mã, tên, loại, mô tả, [[food key, gam/trẻ nhóm mẫu giáo lớn]]] ----
// food key: mã Viện ('7013') hoặc 'oil' | 'sugar' (thực phẩm demo)
const DISHES = [
  ['M010', 'Cơm tẻ', 'CARB', [['1004', 60]]],
  ['M011', 'Thịt gà rim', 'MAIN', [['7013', 50], ['4037', 5], ['13017', 3], ['oil', 3]]],
  ['M012', 'Cá basa sốt cà chua', 'MAIN', [['8092', 45], ['4005', 30], ['4038', 3], ['oil', 3]]],
  ['M013', 'Thịt bò xào hành tây', 'MAIN', [['7005', 35], ['4039', 25], ['oil', 3]]],
  ['M014', 'Tôm rim', 'MAIN', [['8051', 40], ['4037', 4], ['13017', 3], ['oil', 3]]],
  ['M015', 'Đậu phụ sốt cà chua', 'MAIN', [['3025', 70], ['4005', 30], ['4038', 3], ['oil', 3]]],
  ['M016', 'Trứng chiên', 'MAIN', [['9001', 45], ['4038', 3], ['oil', 4]]],
  ['M017', 'Thịt viên sốt cà chua', 'MAIN', [['7083', 40], ['4005', 25], ['4038', 3], ['oil', 3]]],
  ['M018', 'Thịt lợn rang', 'MAIN', [['7017', 40], ['4037', 4], ['13017', 3], ['sugar', 2], ['oil', 3]]],
  ['M020', 'Canh cua đồng rau muống', 'SOUP', [['8034', 25], ['4083', 40], ['4005', 15], ['oil', 2]]],
  ['M021', 'Canh bí ngô thịt bằm', 'SOUP', [['4003', 50], ['7017', 12], ['4038', 2]]],
  ['M022', 'Canh cải xanh thịt bằm', 'SOUP', [['4016', 50], ['7017', 12], ['4038', 2]]],
  ['M023', 'Canh mồng tơi mướp', 'SOUP', [['4080', 40], ['4054', 30], ['7017', 10]]],
  ['M024', 'Canh bắp cải thịt', 'SOUP', [['4010', 50], ['7017', 12], ['4038', 2]]],
  ['M025', 'Canh su su cà rốt', 'SOUP', [['4098', 40], ['4007', 20], ['7017', 10]]],
  ['M026', 'Canh nấm rơm', 'SOUP', [['4129', 30], ['4108', 30], ['7017', 10]]],
  ['M030', 'Su su xào', 'VEGETABLE', [['4098', 70], ['4037', 3], ['oil', 3]]],
  ['M031', 'Rau cải ngọt luộc', 'VEGETABLE', [['4108', 70]]],
  ['M032', 'Súp lơ xào cà rốt', 'VEGETABLE', [['4099', 50], ['4007', 20], ['oil', 3]]],
  ['M033', 'Cải bắp luộc', 'VEGETABLE', [['4010', 70]]],
  ['M034', 'Đậu cô ve xào', 'VEGETABLE', [['4029', 60], ['4007', 10], ['oil', 3]]],
  ['M040', 'Sữa chua', 'SNACK', [['10004', 100]]],
  ['M041', 'Chuối tiêu', 'SNACK', [['5007', 90]]],
  ['M042', 'Cam', 'SNACK', [['5002', 100]]],
  ['M043', 'Dưa hấu', 'SNACK', [['5011', 100]]],
  ['M044', 'Đu đủ chín', 'SNACK', [['5017', 90]]],
  ['M045', 'Chè đậu xanh', 'DESSERT', [['3010', 15], ['sugar', 12]]],
  ['M046', 'Khoai lang luộc', 'SNACK', [['2008', 90]]],
  ['M047', 'Ngô luộc', 'SNACK', [['1007', 120]]],
  ['M048', 'Xôi đậu xanh', 'CARB', [['1002', 45], ['3010', 15]]],
  ['M050', 'Cháo gà', 'CARB', [['1004', 25], ['7013', 25], ['4038', 3]]],
  ['M051', 'Cháo cá basa', 'CARB', [['1004', 25], ['8092', 25], ['4038', 3]]],
  ['M052', 'Miến gà', 'CARB', [['2015', 30], ['7013', 30], ['4038', 3]]],
  ['M053', 'Bún thịt nạc', 'CARB', [['1020', 120], ['7017', 25], ['4005', 15], ['4038', 3]]],
];
const FACTOR = { 'ag-2': 0.6, 'ag-3': 0.8, 'ag-4': 0.9, 'ag-5': 1.0 };
const gramsFor = (g5, ag) => Math.max(1, Math.round(g5 * FACTOR[ag]));

// ---- Nhà cung cấp (tên mô tả, không dùng tên doanh nghiệp thật) ----
const SUPPLIERS = [
  ['NCC Thịt tươi số 1', '0902000001', 'Hải Phòng (địa chỉ cập nhật sau)', 'Nguyễn Văn Bình'],
  ['NCC Thủy sản số 1', '0902000002', 'Hải Phòng (địa chỉ cập nhật sau)', 'Trần Văn Cường'],
  ['NCC Rau củ quả số 1', '0902000003', 'Hải Phòng (địa chỉ cập nhật sau)', 'Lê Thị Dung'],
  ['NCC Lương thực khô số 1', '0902000004', 'Hải Phòng (địa chỉ cập nhật sau)', 'Phạm Văn Đức'],
  ['NCC Sữa và trứng số 1', '0902000005', 'Hải Phòng (địa chỉ cập nhật sau)', 'Hoàng Thị Hà'],
];

// ---- Kho khô mỗi cơ sở: [food key, số lượng g|ml] ----
const STOCK = [
  ['1004', 150000], ['1002', 40000], ['1018', 10000], ['1022', 8000], ['2015', 15000], ['3010', 20000],
  ['3004', 5000], ['3017', 8000], ['3020', 2000], ['oil', 20000], ['sugar', 15000], ['13017', 10000],
  ['13026', 3000], ['13005', 5000],
];

// ---- Thực đơn mẫu 4 tuần × 5 ngày ----
const MAINS = ['M011', 'M012', 'M013', 'M014', 'M015', 'M016', 'M017', 'M018'];
const SOUPS = ['M020', 'M021', 'M022', 'M023', 'M024', 'M025', 'M026'];
const VEGS = ['M030', 'M031', 'M032', 'M033', 'M034'];
const KG_PM = ['M040', 'M041', 'M050', 'M045', 'M042', 'M048', 'M043', 'M052', 'M046', 'M051', 'M044', 'M053'];
const NUR_AM = ['M040', 'M041', 'M042', 'M044', 'M043'];
const NUR_PM = ['M050', 'M051', 'M052', 'M053', 'M048', 'M045'];
function templateItems(block) {
  const out = [];
  for (let w = 1; w <= 4; w++) {
    for (let d = 1; d <= 5; d++) {
      const i = (w - 1) * 5 + (d - 1);
      const lunch = ['M010', MAINS[i % MAINS.length], SOUPS[i % SOUPS.length], VEGS[i % VEGS.length]];
      lunch.forEach((dish, n) => out.push([w, d, 'LUNCH', dish, n]));
      if (block === 'NURSERY') {
        out.push([w, d, 'MORNING', NUR_AM[i % NUR_AM.length], 0]);
        out.push([w, d, 'AFTERNOON', NUR_PM[i % NUR_PM.length], 0]);
      } else {
        out.push([w, d, 'AFTERNOON', KG_PM[i % KG_PM.length], 0]);
      }
    }
  }
  return out;
}

// ================= Sinh SQL =================
const sql = [];
const w = (s = '') => sql.push(s);
w('-- DỮ LIỆU GIẢ CHỈ CHO DEV/DEMO (profile "dev"): danh mục bán trú, NCC, kho khô, thực đơn mẫu của MN Thượng Hồng.');
w('-- SINH TỰ ĐỘNG bởi scripts/dev/gen-thuong-hong-catalog.mjs — sửa script rồi sinh lại, không sửa tay file này.');
w('-- Dinh dưỡng: Viện Dinh dưỡng Quốc gia (viendinhduong.vn), trên 100 g/ml. Giá, % thải bỏ, công thức món, NCC, phiếu nhập,');
w('-- thực đơn là số ước tính để test; thực đơn chưa qua kiểm cân đối của hệ thống. Chạy sau R__dev_seed_thuong_hong.sql.');
w('-- Idempotent theo mã thực phẩm/món, tên NCC, ghi chú phiếu nhập, tên bộ thực đơn.');
w();

// 1. Thực phẩm
w('INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,');
w('                   kcal, protein, lipid, glucid, nutrient_source, nutrient_source_version, waste_pct, storage, origin, note)');
w('VALUES');
const foodLines = FOODS.map(([code, name, grp, st, pu, size, price, waste, unit = 'g']) => {
  const v = foodValues(code);
  const origin = ['MEAT', 'SEAFOOD', 'EGG_DAIRY'].includes(grp) ? 'ANIMAL' : 'PLANT';
  return `    ('${fc(code)}', '${esc(name)}', '${grp}', '${unit}', '${esc(pu)}', ${size}, ${price}, ${v.kcal}, ${v.p}, ${v.l}, ${v.g}, 'Viện Dinh dưỡng Quốc gia (viendinhduong.vn)', 'crawl 2026-09-21', ${waste}, '${st === 'D' ? 'DRY' : 'FRESH'}', '${origin}', 'Giá ước tính (demo)')`;
});
w(foodLines.join(',\n'));
w('ON CONFLICT (code) DO NOTHING;');
w();

// 2. Dị ứng
w('INSERT INTO food_allergens (food_id, allergen_id)');
w('SELECT f.id, a.id');
w('FROM (VALUES');
w(Object.entries(ALLERGENS).map(([c, a]) => `    ('${fc(c)}', '${a}')`).join(',\n'));
w(') AS v (food_code, allergen_code)');
w('JOIN foods f ON f.code = v.food_code');
w('JOIN allergens a ON a.code = v.allergen_code');
w('ON CONFLICT DO NOTHING;');
w();

// 3. Món + công thức (gam/trẻ theo 4 nhóm tuổi)
w('INSERT INTO dishes (code, name, dish_type, description)');
w('VALUES');
w(DISHES.map(([c, n, t]) => `    ('${c}', '${esc(n)}', '${t}', 'Món demo')`).join(',\n'));
w('ON CONFLICT (code) DO NOTHING;');
w();
const ing = [];
for (const [code, , , items] of DISHES) {
  for (const [key, g5] of items) {
    const fcode = DEMO[key] ?? fc(key);
    for (const ag of Object.keys(FACTOR)) ing.push(`    ('${code}', '${fcode}', '${ag}', ${gramsFor(g5, ag)})`);
  }
}
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

// 4. NCC
w('INSERT INTO suppliers (name, phone, address, delivery_person)');
w('SELECT v.name, v.phone, v.address, v.delivery_person');
w('FROM (VALUES');
w(SUPPLIERS.map(([n, p, a, d]) => `    ('${esc(n)}', '${p}', '${esc(a)}', '${esc(d)}')`).join(',\n'));
w(') AS v (name, phone, address, delivery_person)');
w('WHERE NOT EXISTS (SELECT 1 FROM suppliers s WHERE s.name = v.name);');
w();

// 5. Kho khô: mỗi cơ sở một phiếu nhập đã xác nhận (NCC lương thực khô), lô còn hạn 6 tháng
const stockRows = STOCK.map(([key, qty], i) => {
  const fcode = DEMO[key] ?? fc(key);
  return `    ('${fcode}', ${qty}, ${i + 1})`;
});
w("-- Phiếu nhập kho khô đã xác nhận ngày 26/10/2026, mỗi cơ sở một phiếu (note đánh dấu để chạy lại không nhân đôi)");
w('INSERT INTO stock_receipts (campus_id, supplier_id, received_date, status, note, confirmed_by, confirmed_at, created_by)');
w("SELECT c.id, s.id, DATE '2026-10-26', 'CONFIRMED', 'Seed dev Thượng Hồng: nhập kho khô đầu kỳ', u.id, TIMESTAMPTZ '2026-10-26 09:00+07', u.id");
w('FROM (VALUES (\'CS-A\', \'viceprincipal\'), (\'CS-B\', \'th.pht.b\')) AS v (campus_code, username)');
w('JOIN campuses c ON c.code = v.campus_code');
w('JOIN user_accounts u ON u.username = v.username');
w("JOIN suppliers s ON s.name = 'NCC Lương thực khô số 1'");
w("WHERE NOT EXISTS (SELECT 1 FROM stock_receipts r WHERE r.campus_id = c.id AND r.note = 'Seed dev Thượng Hồng: nhập kho khô đầu kỳ');");
w();
w('INSERT INTO stock_receipt_lines (receipt_id, food_id, lot_no, expiry_date, quantity, unit_price)');
w("SELECT r.id, f.id, 'L2610-' || right(c.code, 1) || '-' || lpad(v.n::text, 2, '0'), DATE '2027-04-26', v.qty, f.unit_price");
w('FROM (VALUES');
w(stockRows.join(',\n'));
w(') AS v (food_code, qty, n)');
w('JOIN foods f ON f.code = v.food_code');
w("JOIN stock_receipts r ON r.note = 'Seed dev Thượng Hồng: nhập kho khô đầu kỳ'");
w('JOIN campuses c ON c.id = r.campus_id');
w('WHERE NOT EXISTS (SELECT 1 FROM stock_receipt_lines l WHERE l.receipt_id = r.id AND l.food_id = f.id);');
w();
w('INSERT INTO stock_lots (campus_id, food_id, receipt_line_id, supplier_id, lot_no, expiry_date, quantity, unit_price, received_date)');
w('SELECT r.campus_id, l.food_id, l.id, r.supplier_id, l.lot_no, l.expiry_date, l.quantity, l.unit_price, r.received_date');
w('FROM stock_receipt_lines l');
w("JOIN stock_receipts r ON r.id = l.receipt_id AND r.note = 'Seed dev Thượng Hồng: nhập kho khô đầu kỳ'");
w('WHERE NOT EXISTS (SELECT 1 FROM stock_lots x WHERE x.receipt_line_id = l.id);');
w();
w('INSERT INTO stock_movements (lot_id, campus_id, food_id, movement_type, quantity, receipt_id, reason, created_by)');
w("SELECT x.id, x.campus_id, x.food_id, 'RECEIPT', x.quantity, r.id, 'Nhập kho đầu kỳ', r.confirmed_by");
w('FROM stock_lots x');
w('JOIN stock_receipt_lines l ON l.id = x.receipt_line_id');
w("JOIN stock_receipts r ON r.id = l.receipt_id AND r.note = 'Seed dev Thượng Hồng: nhập kho khô đầu kỳ'");
w("WHERE NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.lot_id = x.id AND m.movement_type = 'RECEIPT');");
w();

// 6. Bộ thực đơn mẫu 4 tuần (nhà trẻ, mẫu giáo)
const SETS = [['Thực đơn mùa thu - Nhà trẻ', 'NURSERY'], ['Thực đơn mùa thu - Mẫu giáo', 'KINDERGARTEN']];
w('INSERT INTO menu_template_sets (name, season_label, menu_block)');
w('SELECT v.name, \'Mùa thu\', v.block');
w('FROM (VALUES');
w(SETS.map(([n, b]) => `    ('${n}', '${b}')`).join(',\n'));
w(') AS v (name, block)');
w('WHERE NOT EXISTS (SELECT 1 FROM menu_template_sets s WHERE s.name = v.name);');
w();
for (const [name, block] of SETS) {
  w('INSERT INTO menu_template_items (set_id, week_no, weekday, session, dish_id, sort_order)');
  w('SELECT s.id, v.week_no, v.weekday, v.session, d.id, v.sort_order');
  w('FROM (VALUES');
  w(templateItems(block).map(([wk, wd, ss, dish, so]) => `    (${wk}, ${wd}, '${ss}', '${dish}', ${so})`).join(',\n'));
  w(') AS v (week_no, weekday, session, dish_code, sort_order)');
  w(`JOIN menu_template_sets s ON s.name = '${name}'`);
  w('JOIN dishes d ON d.code = v.dish_code');
  w('ON CONFLICT (set_id, week_no, weekday, session, dish_id) DO NOTHING;');
  w();
}

// 7. Kế hoạch tuần đã công bố, xoay vòng bộ mẫu: 19/10 = tuần 3, 26/10 = tuần 4, 02/11 = tuần 1, 09/11 = tuần 2
// (3 tuần đầu có dữ liệu vận hành ở R__dev_seed_thuong_hong_operations.sql, tuần 09/11 là tuần sắp tới)
const PLAN_WEEKS = [
  ['2026-10-19', '2026-10-16 16:00+07', 3],
  ['2026-10-26', '2026-10-23 16:00+07', 4],
  ['2026-11-02', '2026-10-30 16:00+07', 1],
  ['2026-11-09', '2026-11-05 16:00+07', 2],
];
const weekStarts = PLAN_WEEKS.map(([s]) => `DATE '${s}'`).join(', ');
w('-- Kế hoạch tuần đã công bố (sao từ bộ mẫu, xoay vòng 4 tuần). Chưa qua kiểm cân đối của hệ thống.');
w('INSERT INTO meal_plan_weeks (week_start, status, published_by, published_at, created_by, updated_by)');
w("SELECT v.week_start::date, 'PUBLISHED', u.id, v.published_at::timestamptz, u.id, u.id");
w(`FROM (VALUES ${PLAN_WEEKS.map(([s, p]) => `('${s}', '${p}')`).join(', ')}) AS v (week_start, published_at)`);
w("JOIN user_accounts u ON u.username = 'viceprincipal'");
w('ON CONFLICT (week_start) DO NOTHING;');
w();
w('INSERT INTO meal_plan_days (week_id, menu_date)');
w("SELECT pw.id, pw.week_start + g.n");
w('FROM meal_plan_weeks pw');
w('CROSS JOIN generate_series(0, 4) AS g (n)');
w(`WHERE pw.week_start IN (${weekStarts})`);
w('ON CONFLICT (week_id, menu_date) DO NOTHING;');
w();
w('INSERT INTO meal_plan_items (day_id, session, menu_block, dish_id, sort_order)');
w('SELECT d.id, t.session, s.menu_block, t.dish_id, t.sort_order');
w('FROM meal_plan_days d');
w('JOIN meal_plan_weeks pw ON pw.id = d.week_id');
w("JOIN menu_template_sets s ON s.name IN ('Thực đơn mùa thu - Nhà trẻ', 'Thực đơn mùa thu - Mẫu giáo')");
w("JOIN menu_template_items t ON t.set_id = s.id AND t.weekday = EXTRACT(ISODOW FROM d.menu_date)::int");
w(`    AND t.week_no = CASE pw.week_start ${PLAN_WEEKS.map(([s, , n]) => `WHEN DATE '${s}' THEN ${n}`).join(' ')} END`);
w(`WHERE pw.week_start IN (${weekStarts})`);
w('ON CONFLICT (day_id, session, menu_block, dish_id) DO NOTHING;');

const out = join(dirname(fileURLToPath(import.meta.url)), '../../src/main/resources/db/dev/R__dev_seed_thuong_hong_catalog.sql');
writeFileSync(out, sql.join('\n') + '\n', 'utf8');
console.log(`Đã ghi ${out}: ${FOODS.length} thực phẩm, ${DISHES.length} món, ${SUPPLIERS.length} NCC, ${STOCK.length} dòng kho/cơ sở`);
