// Họ theo tần suất ước tính ở Việt Nam (%), tên đệm/tên theo giới và thế hệ (phụ huynh 8x–9x, trẻ sinh 2020–2024).
export const SURNAMES = [
  ['Nguyễn', 38], ['Trần', 11], ['Lê', 9.5], ['Phạm', 7], ['Hoàng', 4], ['Vũ', 4], ['Đặng', 2.5], ['Bùi', 2.5],
  ['Đỗ', 2.3], ['Ngô', 1.5], ['Dương', 1.2], ['Lý', 0.6], ['Phan', 1.5], ['Đinh', 1], ['Mai', 0.6], ['Trịnh', 0.8],
  ['Đào', 0.8], ['Lương', 0.6], ['Tô', 0.4], ['Hà', 0.6], ['Cao', 0.5], ['Lưu', 0.4], ['Nghiêm', 0.3], ['Tạ', 0.3],
  ['Vương', 0.3], ['Phùng', 0.4], ['Quách', 0.2], ['Chu', 0.3], ['Đoàn', 0.6], ['Kiều', 0.2],
];

export const ADULT_MIDDLE = {
  MALE: [['Văn', 30], ['Đức', 10], ['Minh', 10], ['Quang', 7], ['Hữu', 6], ['Thanh', 6], ['Xuân', 4], ['Tiến', 4],
    ['Mạnh', 4], ['Duy', 4], ['Ngọc', 3], ['Trọng', 4], ['Công', 4], ['Anh', 4]],
  FEMALE: [['Thị', 40], ['Thu', 8], ['Thanh', 6], ['Ngọc', 6], ['Minh', 4], ['Hồng', 5], ['Phương', 5], ['Thùy', 5],
    ['Kim', 4], ['Hải', 3], ['Mai', 3], ['Bích', 3], ['Lan', 2], ['Diệu', 2]],
};

export const ADULT_GIVEN = {
  MALE: ['Hùng', 'Dũng', 'Tuấn', 'Hải', 'Nam', 'Thắng', 'Cường', 'Long', 'Sơn', 'Trung', 'Quân', 'Hưng', 'Phong', 'Việt',
    'Toàn', 'Tùng', 'Đạt', 'Hiếu', 'Huy', 'Khánh', 'Lâm', 'Linh', 'Nghĩa', 'Quyết', 'Tâm', 'Thành', 'Thịnh', 'Tú', 'Vinh',
    'Bình', 'Chiến', 'Công', 'Định', 'Đông', 'Giang', 'Hoàng', 'Kiên', 'Lực', 'Mạnh', 'Phúc'],
  FEMALE: ['Hương', 'Lan', 'Hà', 'Linh', 'Trang', 'Huyền', 'Thảo', 'Hằng', 'Nga', 'Yến', 'Hạnh', 'Hoa', 'Mai', 'Nhung',
    'Phương', 'Thủy', 'Loan', 'Oanh', 'Quyên', 'Tâm', 'Thơm', 'Thu', 'Trinh', 'Vân', 'Xuân', 'Anh', 'Duyên', 'Giang',
    'Hiền', 'Hồng', 'Liên', 'Ly', 'Minh', 'Ngọc', 'Nhàn', 'Thanh', 'Tuyết', 'Uyên', 'Dung', 'Diệp'],
};

// Tên trẻ: đệm + tên kiểu đặt gần đây (đệm có thể trống với tên ghép sẵn như "Minh Khang").
export const CHILD_MIDDLE = {
  MALE: ['Minh', 'Gia', 'Bảo', 'Đức', 'Quang', 'Anh', 'Hoàng', 'Tuấn', 'Nhật', 'Hải', 'Đăng', 'Phúc', 'Thiên', 'Khôi',
    'Trung', 'Hữu', 'Duy', 'Thành', 'Văn', 'Tiến'],
  FEMALE: ['Ngọc', 'Bảo', 'Minh', 'Khánh', 'Gia', 'Thảo', 'Hà', 'Phương', 'An', 'Tuệ', 'Diệu', 'Thu', 'Hoài', 'Mai',
    'Thanh', 'Linh', 'Quỳnh', 'Tú', 'Yến', 'Thị'],
};

export const CHILD_GIVEN = {
  MALE: ['An', 'Khang', 'Phúc', 'Lâm', 'Huy', 'Khôi', 'Nam', 'Minh', 'Long', 'Bách', 'Đăng', 'Duy', 'Hiếu', 'Hưng', 'Kiên',
    'Khoa', 'Lộc', 'Nhật', 'Phong', 'Quân', 'Sơn', 'Tài', 'Thiện', 'Trí', 'Tùng', 'Việt', 'Vinh', 'Vũ', 'Anh', 'Bảo',
    'Đạt', 'Dũng', 'Hải', 'Hoàng', 'Khải', 'Lực', 'Nguyên', 'Quang', 'Thịnh', 'Tú'],
  FEMALE: ['An', 'Anh', 'Chi', 'Diệp', 'Giang', 'Hân', 'Hà', 'Linh', 'Lan', 'My', 'Ngân', 'Ngọc', 'Nhi', 'Như', 'Quỳnh',
    'Tâm', 'Thảo', 'Trang', 'Trâm', 'Vy', 'Yến', 'Châu', 'Hương', 'Khuê', 'Mai', 'Minh', 'Nga', 'Nhung', 'Phương',
    'Thư', 'Tiên', 'Uyên', 'Vân', 'Xuân', 'Đan', 'Hằng', 'Hoa', 'Loan', 'Mỹ', 'Thùy'],
};

// Thôn/xóm quanh trường (địa chỉ hồ sơ trẻ); tỉnh theo đơn vị hành chính hiện hành của hồ sơ.
export const VILLAGES = ['Thôn Ngọc Lâm', 'Thôn Hòa Bình', 'Thôn An Thái', 'Thôn Đồng Tâm', 'Thôn Phú Xuyên',
  'Thôn Tân Hồng', 'Thôn Cậy', 'Thôn Ngọc Quế', 'Thôn Hà Tiên', 'Thôn Bùi Xá'];
export const WARDS = [['Xã Thượng Hồng', 70], ['Xã Đường An', 15], ['Xã Bình Giang', 10], ['Xã Thanh Miện', 5]];
export const PROVINCE = 'Thành phố Hải Phòng';

// Đầu số di động theo nhà mạng (Viettel, VinaPhone, MobiFone)
export const MOBILE_PREFIXES = [
  ['086', 4], ['096', 6], ['097', 7], ['098', 7], ['032', 3], ['033', 3], ['034', 3], ['035', 3], ['036', 3], ['037', 3],
  ['038', 3], ['039', 3], ['088', 2], ['091', 4], ['094', 4], ['081', 1], ['082', 1], ['083', 1], ['084', 1], ['085', 1],
  ['089', 2], ['090', 4], ['093', 3], ['070', 1], ['076', 1], ['077', 1], ['078', 1], ['079', 1],
];
