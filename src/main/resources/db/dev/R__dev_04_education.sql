-- Bộ dữ liệu dev (profile "dev"): kế hoạch giáo dục (năm học → tổ → hồ sơ lớp) và kho học liệu cá nhân. Dữ liệu giả nhìn như thật. Chạy sau dev 01–03 (người, cấu trúc, danh mục, vận hành ngày). Neo CURRENT_DATE.
-- Luồng duyệt theo edu_settings.workflow_policy mặc định: kế hoạch năm PHT soạn → HT duyệt; kế hoạch tổ TT soạn → PHT duyệt;
-- gói học kỳ/hồ sơ chủ đề GVCN soạn → TT → PHT (TT là GVCN lớp đó thì bỏ qua bước TT).
-- Lịch chủ đề cả năm là mốc cố định; trạng thái hồ sơ chủ đề tính theo CURRENT_DATE: chủ đề đã bắt đầu ⇒ đã ban hành,
-- chủ đề kế tiếp ⇒ đủ trạng thái chờ duyệt/trả lại/nháp, chủ đề sau nữa ⇒ nháp tuần đầu.
-- Chạy lại: chỉ thêm phần còn thiếu (khóa tự nhiên: mã kế hoạch, doc_key), không ghi đè dữ liệu người dùng đã sửa.

SET LOCAL client_min_messages = warning;

-- ===== Ngữ cảnh: năm học, người phụ trách (tra theo phân công còn hiệu lực) =====
DROP TABLE IF EXISTS _e_ctx, _e_ag, _e_theme_def, _e_branch_def, _e_act_def, _e_act_meta, _e_yp, _e_tick,
    _e_team, _e_tt, _e_tw, _e_tg, _e_tact, _e_cls, _e_cc, _e_wk, _e_cell, _e_root, _e_ev, _e_res_def,
    _e_crow, _e_ccont, _e_wkcal, _e_copy, _e_tstate, _e_hol, _e_doc, _e_corner;

CREATE TEMP TABLE _e_ctx AS
WITH sy AS (
    SELECT id, start_date, end_date, semester1_start, semester1_end, semester2_start, semester2_end
    FROM school_years WHERE name = '2026-2027'
), act AS (
    SELECT sa.user_id, sa.staff_role, sa.campus_id, u.full_name
    FROM staff_assignments sa JOIN user_accounts u ON u.id = sa.user_id
    WHERE u.status = 'ACTIVE' AND sa.valid_from <= CURRENT_DATE AND (sa.valid_to IS NULL OR sa.valid_to >= CURRENT_DATE)
)
SELECT sy.id AS sy_id, sy.start_date, sy.end_date, sy.semester1_start AS s1s, sy.semester1_end AS s1e,
       sy.semester2_start AS s2s, sy.semester2_end AS s2e,
       p.user_id AS hp_id, p.full_name AS hp_name,
       va.user_id AS vpa_id, va.full_name AS vpa_name, vb.user_id AS vpb_id, vb.full_name AS vpb_name,
       ca.id AS cs_a, cb.id AS cs_b,
       CURRENT_DATE AS cd,
       st.workflow_revision AS wf_rev,
       st.duration_policy || jsonb_build_object('mode', 'BOUNDED', 'revision', st.duration_revision) AS dur_snap,
       st.week_rows::jsonb AS week_rows
FROM sy
CROSS JOIN edu_settings st
JOIN campuses ca ON ca.code = 'CS-A'
JOIN campuses cb ON cb.code = 'CS-B'
JOIN LATERAL (SELECT user_id, full_name FROM act WHERE staff_role = 'PRINCIPAL' ORDER BY user_id LIMIT 1) p ON true
JOIN LATERAL (SELECT user_id, full_name FROM act WHERE staff_role = 'VICE_PRINCIPAL' AND campus_id = ca.id ORDER BY user_id LIMIT 1) va ON true
JOIN LATERAL (SELECT user_id, full_name FROM act WHERE staff_role = 'VICE_PRINCIPAL' AND campus_id = cb.id ORDER BY user_id LIMIT 1) vb ON true
WHERE st.id = 1;

-- Nhóm tuổi: mã kế hoạch như UI (KHNH-<năm>-<chữ đầu tên nhóm>), tổ trưởng duy nhất của nhóm (EDU-72)
CREATE TEMP TABLE _e_ag AS
SELECT ag.id AS ag_id, ag.code AS ag_code, ag.short_name,
       v.initials, v.cat, v.tag,
       'KHNH-2627-' || v.initials AS team_code,
       tl.user_id AS tl_id, tl.full_name AS tl_name
FROM age_groups ag
JOIN (VALUES ('ag-2', 'NT', 'NT', 'nha_tre'), ('ag-3', 'MGB', 'MG', 'mau_giao_be'),
             ('ag-4', 'MGN', 'MG', 'mau_giao_nho'), ('ag-5', 'MGL', 'MGL', 'mau_giao_lon')) v(code, initials, cat, tag)
  ON v.code = ag.code
CROSS JOIN _e_ctx c
JOIN LATERAL (
    SELECT sa.user_id, u.full_name FROM staff_assignments sa JOIN user_accounts u ON u.id = sa.user_id
    WHERE sa.staff_role = 'AGE_GROUP_TEAM_LEADER' AND sa.age_group_id = ag.id AND sa.school_year_id = c.sy_id
      AND u.status = 'ACTIVE' AND sa.valid_from <= c.cd AND (sa.valid_to IS NULL OR sa.valid_to >= c.cd)
    ORDER BY sa.id LIMIT 1) tl ON true;

-- ===== Lịch chủ đề cả năm (Thứ 2 → Thứ 6, 2–4 tuần học, liền nhau, Tết là đợt nghỉ) =====
-- cat: MG = mẫu giáo bé/nhỡ/lớn, NT = nhà trẻ, MGL = riêng mẫu giáo lớn (chủ đề cuối năm)
CREATE TEMP TABLE _e_theme_def (cat text, sort int, name text, abbr text, start_d date, end_d date, festivals text, note text);
INSERT INTO _e_theme_def VALUES
 ('MG', 1, 'Trường mầm non của bé', 'TMN', '2026-09-07', '2026-09-25', 'Ngày hội đến trường; Tết Trung thu', 'Tuần đầu ổn định nề nếp, làm quen lớp mới'),
 ('MG', 2, 'Bản thân', 'BT', '2026-09-28', '2026-10-09', '', ''),
 ('MG', 3, 'Gia đình', 'GD', '2026-10-12', '2026-10-30', 'Ngày Phụ nữ Việt Nam 20/10', ''),
 ('MG', 4, 'Nghề nghiệp', 'NN', '2026-11-02', '2026-11-27', 'Ngày Nhà giáo Việt Nam 20/11', 'Phối hợp phụ huynh giới thiệu nghề'),
 ('MG', 5, 'Thế giới động vật', 'TGDV', '2026-11-30', '2026-12-18', '', ''),
 ('MG', 6, 'Thế giới thực vật', 'TGTV', '2026-12-21', '2027-01-15', 'Ngày thành lập QĐND Việt Nam 22/12; Tết Dương lịch', ''),
 ('MG', 7, 'Tết và mùa xuân', 'TMX', '2027-01-18', '2027-02-19', 'Tết Nguyên đán Đinh Mùi', 'Nghỉ Tết theo lịch của trường'),
 ('MG', 8, 'Phương tiện và luật lệ giao thông', 'GT', '2027-02-22', '2027-03-12', 'Ngày Quốc tế Phụ nữ 8/3', ''),
 ('MG', 9, 'Nước và các hiện tượng tự nhiên', 'NHT', '2027-03-15', '2027-04-02', '', ''),
 ('MG', 10, 'Quê hương – Đất nước – Bác Hồ', 'QHBH', '2027-04-05', '2027-04-30', 'Giỗ Tổ Hùng Vương; Ngày Giải phóng miền Nam 30/4', ''),
 ('MG', 11, 'Mùa hè của bé', 'MH', '2027-05-03', '2027-05-28', 'Ngày sinh nhật Bác Hồ 19/5', ''),
 ('MGL', 11, 'Trường tiểu học', 'TTH', '2027-05-03', '2027-05-28', 'Ngày sinh nhật Bác Hồ 19/5', 'Phối hợp trường tiểu học cho trẻ tham quan'),
 ('NT', 1, 'Bé và các bạn', 'BCB', '2026-09-07', '2026-09-25', 'Tết Trung thu', 'Tuần đầu cho trẻ làm quen cô, bạn và lớp'),
 ('NT', 2, 'Bé là ai', 'BLA', '2026-09-28', '2026-10-09', '', ''),
 ('NT', 3, 'Gia đình yêu thương của bé', 'GD', '2026-10-12', '2026-10-30', 'Ngày Phụ nữ Việt Nam 20/10', ''),
 ('NT', 4, 'Cô và các bác trong nhà trẻ', 'CBNT', '2026-11-02', '2026-11-27', 'Ngày Nhà giáo Việt Nam 20/11', ''),
 ('NT', 5, 'Những con vật bé yêu', 'CV', '2026-11-30', '2026-12-18', '', ''),
 ('NT', 6, 'Cây và những bông hoa đẹp', 'CHD', '2026-12-21', '2027-01-15', 'Tết Dương lịch', ''),
 ('NT', 7, 'Tết vui vẻ', 'TVV', '2027-01-18', '2027-02-19', 'Tết Nguyên đán Đinh Mùi', 'Nghỉ Tết theo lịch của trường'),
 ('NT', 8, 'Bé đi khắp nơi bằng phương tiện gì', 'PT', '2027-02-22', '2027-03-12', 'Ngày Quốc tế Phụ nữ 8/3', ''),
 ('NT', 9, 'Những giọt nước kỳ diệu', 'NUOC', '2027-03-15', '2027-04-02', '', ''),
 ('NT', 10, 'Bác Hồ kính yêu', 'BH', '2027-04-05', '2027-04-30', 'Ngày Giải phóng miền Nam 30/4', ''),
 ('NT', 11, 'Mùa hè đến rồi', 'MH', '2027-05-03', '2027-05-28', 'Ngày sinh nhật Bác Hồ 19/5', '');

-- Chủ đề nhánh mẫu, mỗi nhánh một tuần học (thứ tự tuần học trong chủ đề)
CREATE TEMP TABLE _e_branch_def (cat text, sort int, wk int, name text);
INSERT INTO _e_branch_def VALUES
 ('MG',1,1,'Bé vui đến trường'),('MG',1,2,'Lớp học của bé'),('MG',1,3,'Bé vui Tết Trung thu'),
 ('MG',2,1,'Bé là ai'),('MG',2,2,'Cơ thể của bé'),
 ('MG',3,1,'Gia đình thân yêu của bé'),('MG',3,2,'Ngôi nhà gia đình bé ở'),('MG',3,3,'Đồ dùng trong gia đình'),
 ('MG',4,1,'Nghề sản xuất'),('MG',4,2,'Nghề dạy học'),('MG',4,3,'Nghề xây dựng'),('MG',4,4,'Nghề dịch vụ'),
 ('MG',5,1,'Động vật nuôi trong gia đình'),('MG',5,2,'Động vật sống trong rừng'),('MG',5,3,'Động vật sống dưới nước'),
 ('MG',6,1,'Cây xanh quanh bé'),('MG',6,2,'Các loại hoa'),('MG',6,3,'Rau, củ, quả bé thích'),('MG',6,4,'Cây lương thực'),
 ('MG',7,1,'Mùa xuân đến rồi'),('MG',7,2,'Bé vui đón Tết'),('MG',7,3,'Tết quê em'),('MG',7,4,'Lễ hội mùa xuân'),
 ('MG',8,1,'Phương tiện giao thông đường bộ'),('MG',8,2,'Phương tiện giao thông đường thủy, hàng không'),('MG',8,3,'Bé với luật giao thông'),
 ('MG',9,1,'Nước quanh bé'),('MG',9,2,'Mưa, nắng và các mùa'),('MG',9,3,'Bé bảo vệ nguồn nước'),
 ('MG',10,1,'Quê hương của bé'),('MG',10,2,'Thủ đô Hà Nội'),('MG',10,3,'Danh lam thắng cảnh đất nước'),('MG',10,4,'Bác Hồ kính yêu'),
 ('MG',11,1,'Mùa hè đến rồi'),('MG',11,2,'Trang phục mùa hè'),('MG',11,3,'Bé vui chơi mùa hè'),('MG',11,4,'Chia tay lớp học'),
 ('MGL',11,1,'Trường tiểu học của bé'),('MGL',11,2,'Đồ dùng học sinh lớp 1'),('MGL',11,3,'Bé chuẩn bị vào lớp 1'),('MGL',11,4,'Chia tay trường mầm non'),
 ('NT',1,1,'Bé đến lớp'),('NT',1,2,'Các bạn của bé'),('NT',1,3,'Bé vui Trung thu'),
 ('NT',2,1,'Bé là ai'),('NT',2,2,'Đôi bàn tay của bé'),
 ('NT',3,1,'Bố mẹ của bé'),('NT',3,2,'Đồ dùng của bé ở nhà'),('NT',3,3,'Ông bà yêu thương'),
 ('NT',4,1,'Cô giáo của bé'),('NT',4,2,'Bác cấp dưỡng'),('NT',4,3,'Đồ dùng, đồ chơi của lớp'),('NT',4,4,'Cô chú công nhân'),
 ('NT',5,1,'Con vật nuôi trong nhà'),('NT',5,2,'Con vật sống trong rừng'),('NT',5,3,'Con vật dưới nước'),
 ('NT',6,1,'Cây xanh của bé'),('NT',6,2,'Những bông hoa đẹp'),('NT',6,3,'Quả bé thích'),('NT',6,4,'Rau bé ăn hằng ngày'),
 ('NT',7,1,'Mùa xuân của bé'),('NT',7,2,'Bé đón Tết'),('NT',7,3,'Bánh chưng ngày Tết'),('NT',7,4,'Bé đi chơi xuân'),
 ('NT',8,1,'Xe đạp, xe máy'),('NT',8,2,'Ô tô của bé'),('NT',8,3,'Tàu, thuyền, máy bay'),
 ('NT',9,1,'Nước của bé'),('NT',9,2,'Mưa và nắng'),('NT',9,3,'Bé tắm mát'),
 ('NT',10,1,'Bác Hồ của bé'),('NT',10,2,'Quê hương bé yêu'),('NT',10,3,'Đất nước tươi đẹp'),('NT',10,4,'Cháu yêu Bác Hồ'),
 ('NT',11,1,'Mùa hè của bé'),('NT',11,2,'Quần áo mùa hè'),('NT',11,3,'Bé thích đi chơi'),('NT',11,4,'Bé lớn khôn');

-- Thẻ hoạt động của kế hoạch chủ đề (EDU-78): loại giờ + lĩnh vực của MT gắn vào thẻ
CREATE TEMP TABLE _e_act_meta (act_no int, slot text, domain_id bigint, dom_rank int, dom_cnt int);
INSERT INTO _e_act_meta
SELECT act_no, slot, domain_id,
       row_number() OVER (PARTITION BY domain_id ORDER BY act_no)::int,
       count(*) OVER (PARTITION BY domain_id)::int
FROM (VALUES (1,'the_duc',1),(2,'hoc',1),(3,'hoc',2),(4,'hoc',2),(5,'hoc',3),(6,'hoc',5),(7,'hoc',5),
             (8,'goc',4),(9,'ngoai_troi',2),(10,'chieu',3),(11,'tro_chuyen',4)) v(act_no, slot, domain_id);

-- cat: ALL = mọi nhóm tuổi; chủ đề 11 khác nhau giữa mẫu giáo lớn (TTH) và các nhóm khác (MH)
CREATE TEMP TABLE _e_act_def (cat text, sort int, act_no int, name text);
INSERT INTO _e_act_def VALUES
 ('ALL',1,1,'Tập bài phát triển chung theo nhạc bài "Trường chúng cháu là trường mầm non"'),
 ('ALL',1,2,'Thể dục – Đi trong đường hẹp, chạy theo hướng thẳng'),
 ('ALL',1,3,'Khám phá – Trường mầm non của bé'),
 ('ALL',1,4,'Toán – Nhận biết hình tròn, hình vuông'),
 ('ALL',1,5,'Văn học – Truyện "Đôi bạn tốt"'),
 ('ALL',1,6,'Âm nhạc – Dạy hát "Cháu đi mẫu giáo"'),
 ('ALL',1,7,'Tạo hình – Vẽ đồ chơi trong lớp'),
 ('ALL',1,8,'Góc phân vai: Cô giáo – học sinh, bán đồ dùng học tập'),
 ('ALL',1,9,'Quan sát sân trường; TCVĐ "Kéo co", "Lộn cầu vồng"'),
 ('ALL',1,10,'Làm quen bài thơ "Bàn tay cô giáo"'),
 ('ALL',1,11,'Trò chuyện về trường lớp, cô giáo và các bạn'),
 ('ALL',2,1,'Tập các động tác hô hấp, tay, bụng, chân theo nhạc bài "Hãy xoay nào"'),
 ('ALL',2,2,'Thể dục – Bật tại chỗ, bật về phía trước'),
 ('ALL',2,3,'Khám phá – Cơ thể của bé'),
 ('ALL',2,4,'Toán – So sánh chiều dài của hai đối tượng'),
 ('ALL',2,5,'Văn học – Thơ "Đôi mắt của em"'),
 ('ALL',2,6,'Âm nhạc – Dạy hát "Tay thơm tay ngoan"'),
 ('ALL',2,7,'Tạo hình – Vẽ chân dung bé'),
 ('ALL',2,8,'Góc phân vai: Gia đình chăm sóc em bé, phòng khám'),
 ('ALL',2,9,'Chơi "Tìm bạn thân"; TCVĐ "Mèo đuổi chuột"'),
 ('ALL',2,10,'Rèn kỹ năng rửa tay bằng xà phòng'),
 ('ALL',2,11,'Trò chuyện về tên, tuổi, sở thích của bé'),
 ('ALL',3,1,'Tập bài phát triển chung theo nhạc bài "Cả nhà thương nhau"'),
 ('ALL',3,2,'Thể dục – Bò bằng bàn tay, cẳng chân'),
 ('ALL',3,3,'Khám phá – Gia đình của bé'),
 ('ALL',3,4,'Toán – Đếm số lượng thành viên trong gia đình'),
 ('ALL',3,5,'Văn học – Truyện "Ba cô gái"'),
 ('ALL',3,6,'Âm nhạc – Dạy hát "Cả nhà thương nhau"'),
 ('ALL',3,7,'Tạo hình – Vẽ ngôi nhà của bé'),
 ('ALL',3,8,'Góc phân vai: Mẹ con, nấu ăn'),
 ('ALL',3,9,'Quan sát đồ dùng gia đình; TCVĐ "Về đúng nhà"'),
 ('ALL',3,10,'Làm quen đồng dao "Lộn cầu vồng"'),
 ('ALL',3,11,'Trò chuyện về các thành viên trong gia đình bé'),
 ('ALL',4,1,'Tập bài phát triển chung theo nhạc bài "Cháu yêu cô chú công nhân"'),
 ('ALL',4,2,'Thể dục – Ném xa bằng một tay, chạy nhanh 10 m'),
 ('ALL',4,3,'Khám phá – Một số nghề quen thuộc'),
 ('ALL',4,4,'Toán – Phân loại dụng cụ theo nghề'),
 ('ALL',4,5,'Văn học – Truyện "Hai anh em"'),
 ('ALL',4,6,'Âm nhạc – Biểu diễn văn nghệ mừng ngày 20/11'),
 ('ALL',4,7,'Tạo hình – Làm thiệp tặng cô nhân ngày 20/11'),
 ('ALL',4,8,'Góc phân vai: Bác sĩ, cửa hàng, bếp ăn'),
 ('ALL',4,9,'Quan sát bác bảo vệ, cô cấp dưỡng làm việc; TCVĐ "Thi xem ai nhanh"'),
 ('ALL',4,10,'Làm quen bài thơ "Bé làm bao nhiêu nghề"'),
 ('ALL',4,11,'Trò chuyện về nghề của bố mẹ'),
 ('ALL',5,1,'Tập bài phát triển chung theo nhạc bài "Gà trống, mèo con và cún con"'),
 ('ALL',5,2,'Thể dục – Bò chui qua cổng, nhảy lò cò'),
 ('ALL',5,3,'Khám phá – Một số con vật nuôi trong gia đình'),
 ('ALL',5,4,'Toán – Đếm và so sánh số lượng các con vật'),
 ('ALL',5,5,'Văn học – Truyện "Chú dê đen"'),
 ('ALL',5,6,'Âm nhạc – Dạy hát "Đố bạn"'),
 ('ALL',5,7,'Tạo hình – Nặn con vật bé yêu'),
 ('ALL',5,8,'Góc phân vai: Cửa hàng thức ăn chăn nuôi, bác sĩ thú y'),
 ('ALL',5,9,'Quan sát con gà, con mèo; TCVĐ "Cáo và thỏ"'),
 ('ALL',5,10,'Làm quen bài thơ "Mèo đi câu cá"'),
 ('ALL',5,11,'Trò chuyện về con vật bé yêu thích'),
 ('ALL',6,1,'Tập bài phát triển chung theo nhạc bài "Em yêu cây xanh"'),
 ('ALL',6,2,'Thể dục – Đi trên ghế thể dục, đầu đội túi cát'),
 ('ALL',6,3,'Khám phá – Sự phát triển của cây'),
 ('ALL',6,4,'Toán – Sắp xếp theo quy tắc lá – hoa – quả'),
 ('ALL',6,5,'Văn học – Truyện "Sự tích quả dưa hấu"'),
 ('ALL',6,6,'Âm nhạc – Dạy hát "Em yêu cây xanh"'),
 ('ALL',6,7,'Tạo hình – Vẽ vườn hoa'),
 ('ALL',6,8,'Góc phân vai: Cửa hàng hoa quả, bán rau củ'),
 ('ALL',6,9,'Chăm sóc vườn rau của lớp; TCVĐ "Gieo hạt"'),
 ('ALL',6,10,'Làm quen bài thơ "Hoa kết trái"'),
 ('ALL',6,11,'Trò chuyện về cây xanh và ích lợi của cây'),
 ('ALL',7,1,'Tập bài phát triển chung theo nhạc bài "Sắp đến Tết rồi"'),
 ('ALL',7,2,'Thể dục – Chạy chậm, tung bắt bóng với cô'),
 ('ALL',7,3,'Khám phá – Tết Nguyên đán'),
 ('ALL',7,4,'Toán – Đếm số lượng bánh, kẹo, hoa quả ngày Tết'),
 ('ALL',7,5,'Văn học – Truyện "Sự tích bánh chưng, bánh giầy"'),
 ('ALL',7,6,'Âm nhạc – Dạy hát "Sắp đến Tết rồi"'),
 ('ALL',7,7,'Tạo hình – Làm bao lì xì, trang trí cây đào'),
 ('ALL',7,8,'Góc phân vai: Chợ Tết, gói bánh chưng'),
 ('ALL',7,9,'Quan sát cây đào, cây quất ngày Tết; TCVĐ "Rồng rắn lên mây"'),
 ('ALL',7,10,'Làm quen bài thơ "Mùa xuân"'),
 ('ALL',7,11,'Trò chuyện về ngày Tết của gia đình bé'),
 ('ALL',8,1,'Tập bài phát triển chung theo nhạc bài "Em đi qua ngã tư đường phố"'),
 ('ALL',8,2,'Thể dục – Đi theo đường dích dắc, bò theo đường hẹp'),
 ('ALL',8,3,'Khám phá – Một số phương tiện giao thông'),
 ('ALL',8,4,'Toán – Định hướng phải – trái, trước – sau'),
 ('ALL',8,5,'Văn học – Thơ "Đèn giao thông"'),
 ('ALL',8,6,'Âm nhạc – Dạy hát "Em đi qua ngã tư đường phố"'),
 ('ALL',8,7,'Tạo hình – Vẽ ô tô, thuyền buồm'),
 ('ALL',8,8,'Góc phân vai: Bến xe, chú cảnh sát giao thông'),
 ('ALL',8,9,'Thực hành đi đúng phần đường dành cho người đi bộ; TCVĐ "Ô tô và chim sẻ"'),
 ('ALL',8,10,'Làm quen một số biển báo giao thông đơn giản'),
 ('ALL',8,11,'Trò chuyện về phương tiện bé đi đến trường'),
 ('ALL',9,1,'Tập bài phát triển chung theo nhạc bài "Cho tôi đi làm mưa với"'),
 ('ALL',9,2,'Thể dục – Bật qua vật cản, ném trúng đích'),
 ('ALL',9,3,'Khám phá – Nước và ích lợi của nước'),
 ('ALL',9,4,'Toán – Đo dung tích bằng các vật đo khác nhau'),
 ('ALL',9,5,'Văn học – Truyện "Giọt nước tí xíu"'),
 ('ALL',9,6,'Âm nhạc – Dạy hát "Cho tôi đi làm mưa với"'),
 ('ALL',9,7,'Tạo hình – Vẽ cảnh trời mưa'),
 ('ALL',9,8,'Góc phân vai: Cửa hàng bán nước giải khát'),
 ('ALL',9,9,'Thí nghiệm vật chìm – vật nổi; TCVĐ "Trời nắng, trời mưa"'),
 ('ALL',9,10,'Rèn kỹ năng tiết kiệm nước khi rửa tay'),
 ('ALL',9,11,'Trò chuyện về thời tiết hôm nay'),
 ('ALL',10,1,'Tập bài phát triển chung theo nhạc bài "Quê hương tươi đẹp"'),
 ('ALL',10,2,'Thể dục – Trèo lên xuống thang, chạy liên tục theo hướng thẳng'),
 ('ALL',10,3,'Khám phá – Bác Hồ kính yêu'),
 ('ALL',10,4,'Toán – Ôn đếm, thêm bớt trong phạm vi đã học'),
 ('ALL',10,5,'Văn học – Thơ "Ảnh Bác"'),
 ('ALL',10,6,'Âm nhạc – Dạy hát "Em mơ gặp Bác Hồ"'),
 ('ALL',10,7,'Tạo hình – Vẽ lá cờ Tổ quốc'),
 ('ALL',10,8,'Góc phân vai: Hướng dẫn viên du lịch, cửa hàng đặc sản quê hương'),
 ('ALL',10,9,'Quan sát cờ Tổ quốc; TCVĐ "Kéo co"'),
 ('ALL',10,10,'Kể chuyện về Bác Hồ với các cháu thiếu nhi'),
 ('ALL',10,11,'Trò chuyện về quê hương và các ngày lễ lớn trong tháng 4'),
 ('MH',11,1,'Tập bài phát triển chung theo nhạc bài "Mùa hè đến"'),
 ('MH',11,2,'Thể dục – Đi, chạy thay đổi tốc độ theo hiệu lệnh'),
 ('MH',11,3,'Khám phá – Mùa hè của bé'),
 ('MH',11,4,'Toán – Ôn nhận biết các hình đã học'),
 ('MH',11,5,'Văn học – Truyện "Chú đỗ con"'),
 ('MH',11,6,'Âm nhạc – Dạy hát "Mùa hè đến"'),
 ('MH',11,7,'Tạo hình – Vẽ trang phục mùa hè'),
 ('MH',11,8,'Góc phân vai: Cửa hàng bán kem, nước giải khát'),
 ('MH',11,9,'Chơi với nước và cát; TCVĐ "Thả đỉa ba ba"'),
 ('MH',11,10,'Biểu diễn văn nghệ cuối năm'),
 ('MH',11,11,'Trò chuyện về những việc bé làm trong mùa hè'),
 ('TTH',11,1,'Tập bài phát triển chung theo nhạc bài "Tạm biệt búp bê"'),
 ('TTH',11,2,'Thể dục – Bật xa, ném xa bằng hai tay'),
 ('TTH',11,3,'Khám phá – Trường tiểu học'),
 ('TTH',11,4,'Toán – Ôn tập các số trong phạm vi 10'),
 ('TTH',11,5,'Văn học – Truyện "Gà tơ đi học"'),
 ('TTH',11,6,'Âm nhạc – Dạy hát "Tạm biệt búp bê"'),
 ('TTH',11,7,'Tạo hình – Làm bưu thiếp tặng bạn khi chia tay'),
 ('TTH',11,8,'Góc phân vai: Lớp học, cửa hàng đồ dùng học sinh'),
 ('TTH',11,9,'Tham quan trường tiểu học gần trường; TCVĐ "Rồng rắn lên mây"'),
 ('TTH',11,10,'Làm quen chữ cái, tập tô các nét cơ bản'),
 ('TTH',11,11,'Trò chuyện về trường tiểu học và ước mơ vào lớp 1');

-- ===== Kế hoạch năm học (SCHOOL_YEAR): PHT chuyên môn (CS-B) soạn, HT duyệt; mẫu giáo lớn có phiên bản điều chỉnh 2 =====
-- Nhà trẻ có phiên bản điều chỉnh 2 đã gửi trước ngày bắt đầu năm học và còn chờ HT duyệt: từ ngày bắt đầu năm học code
-- khóa tạo/gửi phiên bản mới (7389), bản đang Chờ duyệt vẫn được duyệt/trả lại. Kế hoạch tổ vẫn dùng nguồn bản 1.
CREATE TEMP TABLE _e_yp AS
SELECT a.ag_id, a.initials, a.short_name, v.version_no, v.review,
       'KHN-KHNH-2627-' || a.initials || CASE WHEN v.version_no > 1 THEN '-V' || v.version_no ELSE '' END AS code,
       v.created_at, v.sent_at, v.approved_at,
       'Kế hoạch giáo dục năm học 2026-2027 – ' || a.short_name AS title,
       CASE WHEN v.version_no = 1
            THEN 'Mục tiêu, nội dung và dự kiến chủ đề cả năm cho nhóm ' || lower(a.short_name) || ', áp dụng chung hai cơ sở.'
            WHEN v.review THEN 'Bổ sung nội dung đọc đồng dao, ca dao gắn với chủ đề cho phần phát triển ngôn ngữ.'
            ELSE 'Điều chỉnh nội dung giáo dục dinh dưỡng theo thực đơn bán trú mới của trường.' END AS description
FROM _e_ag a
JOIN (VALUES
    (1, false, timestamptz '2026-08-15 08:30+07', timestamptz '2026-08-17 09:00+07', timestamptz '2026-08-18 10:00+07', NULL),
    (2, false, timestamptz '2026-08-19 08:00+07', timestamptz '2026-08-20 09:15+07', timestamptz '2026-08-21 10:30+07', 'MGL'),
    (2, true,  timestamptz '2026-08-27 08:30+07', timestamptz '2026-08-28 15:00+07', NULL::timestamptz, 'NT')
) v(version_no, review, created_at, sent_at, approved_at, only_ag) ON v.only_ag IS NULL OR v.only_ag = a.initials;

INSERT INTO edu_year_plans (code, school_year_id, age_group_id, title, description, status, owner_id, owner_title,
    sent_by, sent_at, issued_by, issued_at, created_at, updated_at, content_revision, version_no, source_plan_id,
    review_round, approvals, applied_policy_revision, completion_policy, print_snapshot, published_by,
    completion_mode, completed_at, author_tracking_since, author_coverage, duration_policy_snapshot)
SELECT y.code, c.sy_id, y.ag_id, y.title, y.description, 'APPROVED', c.vpb_id, 'Phó hiệu trưởng',
       c.vpb_id, y.sent_at, NULL, NULL, y.created_at, y.approved_at, 6, y.version_no,
       (SELECT p.id FROM edu_year_plans p WHERE p.school_year_id = c.sy_id AND p.age_group_id = y.ag_id
          AND p.version_no = y.version_no - 1 AND NOT p.disabled),
       1,
       jsonb_build_array(jsonb_build_object('userId', c.hp_id, 'name', c.hp_name, 'roleKey', 'PRINCIPAL',
           'at', to_char(y.approved_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'), 'note', 'Đồng ý ban hành.', 'round', 1)),
       c.wf_rev,
       jsonb_build_object('creators', jsonb_build_array('VICE_PRINCIPAL'), 'reviewers', jsonb_build_array('PRINCIPAL')),
       jsonb_build_object('schemaVersion', 1,
           'capturedAt', to_char(y.approved_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
           'origin', 'PUBLICATION', 'documentType', 'schoolYear', 'policyRevision', c.wf_rev,
           'slots', jsonb_build_array(
               jsonb_build_object('roleKey', 'CREATOR', 'label', 'Người soạn', 'names', jsonb_build_array(c.vpb_name)),
               jsonb_build_object('roleKey', 'PRINCIPAL', 'label', 'Hiệu trưởng', 'names', jsonb_build_array(c.hp_name)))),
       c.hp_id, 'HUMAN_APPROVAL', y.approved_at, y.created_at, 'COMPLETE', c.dur_snap
FROM _e_yp y CROSS JOIN _e_ctx c
WHERE y.version_no = 1 AND NOT EXISTS (SELECT 1 FROM edu_year_plans p
                  WHERE p.school_year_id = c.sy_id AND p.age_group_id = y.ag_id AND p.version_no = y.version_no AND NOT p.disabled)
ORDER BY y.version_no, y.ag_id;

-- Phiên bản 2 cần id bản 1 (source_plan_id) nên chèn sau; bản chờ duyệt chưa có người duyệt, ô ký, policy hoàn tất
INSERT INTO edu_year_plans (code, school_year_id, age_group_id, title, description, status, owner_id, owner_title,
    sent_by, sent_at, issued_by, issued_at, created_at, updated_at, content_revision, version_no, source_plan_id,
    review_round, approvals, applied_policy_revision, completion_policy, print_snapshot, published_by,
    completion_mode, completed_at, author_tracking_since, author_coverage, duration_policy_snapshot)
SELECT y.code, c.sy_id, y.ag_id, y.title, y.description, CASE WHEN y.review THEN 'REVIEW' ELSE 'APPROVED' END,
       c.vpb_id, 'Phó hiệu trưởng', c.vpb_id, y.sent_at, NULL, NULL, y.created_at, COALESCE(y.approved_at, y.sent_at),
       CASE WHEN y.review THEN 4 ELSE 6 END, y.version_no,
       (SELECT p.id FROM edu_year_plans p WHERE p.school_year_id = c.sy_id AND p.age_group_id = y.ag_id
          AND p.version_no = y.version_no - 1 AND NOT p.disabled),
       1,
       CASE WHEN y.review THEN '[]'::jsonb ELSE
       jsonb_build_array(jsonb_build_object('userId', c.hp_id, 'name', c.hp_name, 'roleKey', 'PRINCIPAL',
           'at', to_char(y.approved_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'), 'note', 'Đồng ý ban hành.', 'round', 1)) END,
       c.wf_rev,
       CASE WHEN NOT y.review THEN
       jsonb_build_object('creators', jsonb_build_array('VICE_PRINCIPAL'), 'reviewers', jsonb_build_array('PRINCIPAL')) END,
       CASE WHEN NOT y.review THEN
       jsonb_build_object('schemaVersion', 1,
           'capturedAt', to_char(y.approved_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
           'origin', 'PUBLICATION', 'documentType', 'schoolYear', 'policyRevision', c.wf_rev,
           'slots', jsonb_build_array(
               jsonb_build_object('roleKey', 'CREATOR', 'label', 'Người soạn', 'names', jsonb_build_array(c.vpb_name)),
               jsonb_build_object('roleKey', 'PRINCIPAL', 'label', 'Hiệu trưởng', 'names', jsonb_build_array(c.hp_name)))) END,
       CASE WHEN NOT y.review THEN c.hp_id END, CASE WHEN NOT y.review THEN 'HUMAN_APPROVAL' END, y.approved_at,
       y.created_at, 'COMPLETE', c.dur_snap
FROM _e_yp y CROSS JOIN _e_ctx c
WHERE y.version_no = 2 AND NOT EXISTS (SELECT 1 FROM edu_year_plans p
                  WHERE p.school_year_id = c.sy_id AND p.age_group_id = y.ag_id AND p.version_no = y.version_no AND NOT p.disabled)
  -- mỗi chuỗi chỉ một bản Nháp/Chờ duyệt (uq_edu_year_plan_open)
  AND (NOT y.review OR NOT EXISTS (SELECT 1 FROM edu_year_plans p WHERE p.school_year_id = c.sy_id AND p.age_group_id = y.ag_id
                                   AND p.status <> 'APPROVED' AND NOT p.disabled))
ORDER BY y.version_no, y.ag_id;

ALTER TABLE _e_yp ADD COLUMN plan_id bigint, ADD COLUMN fresh boolean;
UPDATE _e_yp y SET plan_id = p.id
FROM edu_year_plans p, _e_ctx c
WHERE p.school_year_id = c.sy_id AND p.age_group_id = y.ag_id AND p.version_no = y.version_no AND NOT p.disabled;
-- Chỉ dựng cây cho kế hoạch chưa có dòng nào (lần chạy đầu); chạy lại không chạm cây đã có
UPDATE _e_yp y SET fresh = NOT EXISTS (SELECT 1 FROM edu_themes t WHERE t.school_year_plan_id = y.plan_id)
                       AND NOT EXISTS (SELECT 1 FROM edu_plan_goals g WHERE g.school_year_plan_id = y.plan_id);

-- ===== Cây mục tiêu năm (bản 1) lấy nguyên từ Chương trình GDMN của Bộ (VBHN 01/2021) =====
-- Thứ tự trong lĩnh vực: lĩnh vực con → nhóm kết quả mong đợi → mục tiêu; MT đánh số liên tục cả kế hoạch
CREATE TEMP TABLE _e_crow AS
WITH src AS (SELECT id FROM edu_curriculum_sources WHERE code = 'VBHN-01-2021-GDMN'),
r AS (
    SELECT sd.age_group_id AS ag_id, sd.domain_id, 'SUB' AS kind, sd.text, sd.id AS cur_id, sd.id AS cur_sub,
           NULL::bigint AS cur_er, sd.sort_order AS k1, 0 AS k2, 0 AS k3, 0 AS k4
    FROM edu_curriculum_sub_domains sd JOIN src ON src.id = sd.source_id
    UNION ALL
    SELECT er.age_group_id, er.domain_id, 'GROUP', er.text, er.id, er.sub_domain_id, er.id,
           COALESCE(sd.sort_order, 0), 1, er.sort_order, 0
    FROM edu_curriculum_expected_results er JOIN src ON src.id = er.source_id
    LEFT JOIN edu_curriculum_sub_domains sd ON sd.id = er.sub_domain_id
    UNION ALL
    SELECT g.age_group_id, er.domain_id, 'GOAL', g.goal_text, g.id, er.sub_domain_id, er.id,
           COALESCE(sd.sort_order, 0), 1, er.sort_order, 1 + g.sort_order
    FROM edu_curriculum_goals g JOIN src ON src.id = g.source_id
    JOIN edu_curriculum_expected_results er ON er.id = g.expected_result_id
    LEFT JOIN edu_curriculum_sub_domains sd ON sd.id = er.sub_domain_id
)
SELECT r.*, (row_number() OVER (PARTITION BY r.ag_id, r.domain_id ORDER BY r.k1, r.k2, r.k3, r.k4) - 1)::smallint AS sort_order
FROM r;
ALTER TABLE _e_crow ADD COLUMN goal_no smallint;
UPDATE _e_crow c SET goal_no = n.no
FROM (SELECT r.cur_id, row_number() OVER (PARTITION BY r.ag_id ORDER BY d.sort_order, r.sort_order) AS no
      FROM _e_crow r JOIN edu_domains d ON d.id = r.domain_id WHERE r.kind = 'GOAL') n
WHERE c.kind = 'GOAL' AND c.cur_id = n.cur_id;

-- Nội dung của MT: các ý con của MT trong chương trình; MT không có ý con thì lấy nội dung mục C cùng lĩnh vực (con),
-- chia theo thứ tự; vẫn trống thì dùng chính câu MT. Tối đa 4 nội dung một MT.
CREATE TEMP TABLE _e_ccont AS
WITH own AS (
    SELECT gc.curriculum_goal_id AS goal_id, gc.sort_order::int AS k, btrim(gc.text) AS text
    FROM edu_curriculum_goal_contents gc
), g AS (
    SELECT g.id, g.age_group_id AS ag, er.domain_id AS d, COALESCE(er.sub_domain_id, 0) AS sub,
           row_number() OVER (PARTITION BY g.age_group_id, er.domain_id, COALESCE(er.sub_domain_id, 0) ORDER BY g.sort_order) AS i,
           count(*) OVER (PARTITION BY g.age_group_id, er.domain_id, COALESCE(er.sub_domain_id, 0)) AS n
    FROM edu_curriculum_goals g JOIN edu_curriculum_expected_results er ON er.id = g.expected_result_id
    WHERE NOT EXISTS (SELECT 1 FROM own o WHERE o.goal_id = g.id)
), c AS (
    SELECT cc.age_group_id AS ag, cc.domain_id AS d, COALESCE(cc.sub_domain_id, 0) AS sub,
           CASE WHEN btrim(COALESCE(cc.label, '')) <> '' THEN btrim(cc.label) || ': ' || btrim(cc.text) ELSE btrim(cc.text) END AS text,
           row_number() OVER (PARTITION BY cc.age_group_id, cc.domain_id, COALESCE(cc.sub_domain_id, 0) ORDER BY cc.sort_order) AS k,
           count(*) OVER (PARTITION BY cc.age_group_id, cc.domain_id, COALESCE(cc.sub_domain_id, 0)) AS m
    FROM edu_curriculum_contents cc
), pairs AS (
    SELECT g.id AS goal_id, c.k::int AS k, c.text FROM g JOIN c ON c.ag = g.ag AND c.d = g.d AND c.sub = g.sub
    WHERE ceil(c.k * g.n::numeric / c.m) = g.i
), fb AS (
    SELECT g.id AS goal_id, c.k::int AS k, c.text FROM g JOIN c ON c.ag = g.ag AND c.d = g.d AND c.sub = g.sub
    WHERE c.k = ceil(g.i * c.m::numeric / g.n) AND NOT EXISTS (SELECT 1 FROM pairs p WHERE p.goal_id = g.id)
), allc AS (
    SELECT * FROM own UNION ALL SELECT * FROM pairs UNION ALL SELECT * FROM fb
    UNION ALL
    SELECT g.id, 0, rtrim(btrim(cg.goal_text), ': ')
    FROM g JOIN edu_curriculum_goals cg ON cg.id = g.id
    WHERE NOT EXISTS (SELECT 1 FROM pairs p WHERE p.goal_id = g.id) AND NOT EXISTS (SELECT 1 FROM fb WHERE fb.goal_id = g.id)
), dedup AS (
    SELECT DISTINCT ON (goal_id, lower(text)) goal_id, k, text FROM allc WHERE text <> '' ORDER BY goal_id, lower(text), k
)
SELECT goal_id, (row_number() OVER (PARTITION BY goal_id ORDER BY k, text) - 1)::smallint AS sort_order, text
FROM dedup;
DELETE FROM _e_ccont WHERE sort_order >= 4;

INSERT INTO edu_plan_sub_domains (domain_id, sort_order, text, curriculum_sub_domain_id, school_year_plan_id)
SELECT r.domain_id, r.sort_order, r.text, r.cur_id, y.plan_id
FROM _e_crow r JOIN _e_yp y ON y.ag_id = r.ag_id AND y.version_no = 1 AND y.fresh
WHERE r.kind = 'SUB';

INSERT INTO edu_plan_expected_results (domain_id, sort_order, text, sub_domain_id, curriculum_expected_result_id, school_year_plan_id)
SELECT r.domain_id, r.sort_order, r.text, s.id, r.cur_id, y.plan_id
FROM _e_crow r JOIN _e_yp y ON y.ag_id = r.ag_id AND y.version_no = 1 AND y.fresh
LEFT JOIN edu_plan_sub_domains s ON s.school_year_plan_id = y.plan_id AND s.curriculum_sub_domain_id = r.cur_sub
WHERE r.kind = 'GROUP';

INSERT INTO edu_plan_goals (domain_id, sort_order, text, goal_no, curriculum_goal_id, sub_domain_id, expected_result_id, school_year_plan_id)
SELECT r.domain_id, r.sort_order, r.text, r.goal_no, r.cur_id, e.sub_domain_id, e.id, y.plan_id
FROM _e_crow r JOIN _e_yp y ON y.ag_id = r.ag_id AND y.version_no = 1 AND y.fresh
JOIN edu_plan_expected_results e ON e.school_year_plan_id = y.plan_id AND e.curriculum_expected_result_id = r.cur_er
WHERE r.kind = 'GOAL';

INSERT INTO edu_goal_contents (goal_id, sort_order, text)
SELECT g.id, c.sort_order, c.text
FROM _e_yp y JOIN edu_plan_goals g ON g.school_year_plan_id = y.plan_id
JOIN _e_ccont c ON c.goal_id = g.curriculum_goal_id
WHERE y.version_no = 1 AND y.fresh
ORDER BY g.id, c.sort_order;

-- Chủ đề, đợt nghỉ (theo lịch nghỉ của năm học), chủ đề nhánh mẫu
INSERT INTO edu_themes (sort_order, name, abbr, start_date, end_date, festivals, note, school_year_plan_id)
SELECT d.sort, d.name, d.abbr, d.start_d, d.end_d, d.festivals, d.note, y.plan_id
FROM _e_yp y JOIN _e_ag a ON a.ag_id = y.ag_id
JOIN _e_theme_def d ON d.cat = CASE WHEN a.cat = 'NT' THEN 'NT' WHEN a.cat = 'MGL' AND d.sort = 11 THEN 'MGL' ELSE 'MG' END
WHERE y.version_no = 1 AND y.fresh
ORDER BY y.plan_id, d.sort;

INSERT INTO edu_breaks (sort_order, label, start_date, end_date, school_year_plan_id)
SELECT (row_number() OVER (PARTITION BY y.plan_id ORDER BY h.start_date) - 1)::smallint, h.name, h.start_date, h.end_date, y.plan_id
FROM _e_yp y CROSS JOIN _e_ctx c JOIN school_holidays h ON h.school_year_id = c.sy_id
WHERE y.version_no = 1 AND y.fresh;

-- Tuần học của từng chủ đề: bỏ tuần mà cả Thứ 2–6 đều nghỉ (EDU-41)
CREATE TEMP TABLE _e_wkcal AS
SELECT x.cat, x.sort, x.monday, row_number() OVER (PARTITION BY x.cat, x.sort ORDER BY x.monday)::int AS wk
FROM (
    SELECT d.cat, d.sort, m::date AS monday
    FROM _e_theme_def d CROSS JOIN _e_ctx c
    CROSS JOIN LATERAL generate_series(d.start_d, d.end_d, interval '7 days') m
    WHERE EXISTS (SELECT 1 FROM generate_series(m::date, m::date + 4, interval '1 day') dd
                  WHERE NOT EXISTS (SELECT 1 FROM school_holidays h WHERE h.school_year_id = c.sy_id
                                    AND dd::date BETWEEN h.start_date AND h.end_date))
) x;

INSERT INTO edu_plan_branch_templates (theme_id, name, start_date, end_date, sort_order, created_at, updated_at, school_year_plan_id)
SELECT t.id, b.name, w.monday, w.monday + 4,
       (row_number() OVER (PARTITION BY y.plan_id ORDER BY w.monday) - 1)::int, y.created_at, y.created_at, y.plan_id
FROM _e_yp y JOIN _e_ag a ON a.ag_id = y.ag_id
JOIN edu_themes t ON t.school_year_plan_id = y.plan_id
JOIN _e_theme_def d ON d.cat = CASE WHEN a.cat = 'NT' THEN 'NT' WHEN a.cat = 'MGL' AND d.sort = 11 THEN 'MGL' ELSE 'MG' END
                   AND d.sort = t.sort_order
JOIN _e_wkcal w ON w.cat = d.cat AND w.sort = d.sort
JOIN _e_branch_def b ON b.cat = d.cat AND b.sort = d.sort AND b.wk = w.wk
WHERE y.version_no = 1 AND y.fresh;

-- Tích nội dung vào chủ đề: MT số n thuộc chủ đề chính ((n-1) mod 11)+1; nội dung đầu tích thêm một chủ đề khác để MT
-- xuất hiện ở hai chủ đề. Mọi nội dung đều được phân bổ (EDU-69), mọi MT năm đều có chủ đề (EDU-80).
INSERT INTO edu_content_theme_ticks (content_id, theme_id)
SELECT DISTINCT gc.id, t.id
FROM _e_yp y
JOIN edu_plan_goals g ON g.school_year_plan_id = y.plan_id
JOIN edu_goal_contents gc ON gc.goal_id = g.id
CROSS JOIN LATERAL (SELECT ((g.goal_no - 1) % 11) + 1 AS p,
                           CASE WHEN ((g.goal_no * 4) % 11) + 1 = ((g.goal_no - 1) % 11) + 1
                                THEN (((g.goal_no - 1) % 11) + 1) % 11 + 1 ELSE ((g.goal_no * 4) % 11) + 1 END AS s) k
JOIN edu_themes t ON t.school_year_plan_id = y.plan_id
                 AND (t.sort_order = k.p OR (gc.sort_order = 0 AND t.sort_order = k.s))
WHERE y.version_no = 1 AND y.fresh;

-- ===== Kế hoạch tổ (TEAM): tự tạo khi kế hoạch năm ban hành, giao tổ trưởng duy nhất của nhóm tuổi soạn (EDU-72),
-- PHT chuyên môn duyệt. Mẫu giáo bé bị trả lại một lần; mẫu giáo lớn chuyển nguồn sang kế hoạch năm bản 2 (EDU-62). =====
CREATE TEMP TABLE _e_team AS
SELECT a.ag_id, a.initials, a.short_name, a.team_code, a.tl_id, a.tl_name,
       v1.plan_id AS v1_id, cur.plan_id AS source_id, CASE WHEN cur.version_no > 1 THEN v1.plan_id END AS prev_source_id,
       v1.approved_at AS created_at, v.sent1, v.returned_at, v.sent2, v.approved_at, v.resolved_at
FROM _e_ag a
JOIN _e_yp v1 ON v1.ag_id = a.ag_id AND v1.version_no = 1
JOIN _e_yp cur ON cur.ag_id = a.ag_id AND cur.version_no = (SELECT max(z.version_no) FROM _e_yp z WHERE z.ag_id = a.ag_id AND NOT z.review)
JOIN (VALUES
    ('NT',  timestamptz '2026-08-20 14:00+07', NULL::timestamptz, NULL::timestamptz, timestamptz '2026-08-21 09:30+07', NULL::timestamptz),
    ('MGB', timestamptz '2026-08-19 15:00+07', timestamptz '2026-08-20 10:00+07', timestamptz '2026-08-21 14:00+07', timestamptz '2026-08-22 09:00+07', NULL),
    ('MGN', timestamptz '2026-08-20 16:00+07', NULL, NULL, timestamptz '2026-08-21 10:15+07', NULL),
    ('MGL', timestamptz '2026-08-24 14:00+07', NULL, NULL, timestamptz '2026-08-25 09:30+07', timestamptz '2026-08-22 08:45+07')
) v(initials, sent1, returned_at, sent2, approved_at, resolved_at) ON v.initials = a.initials;

INSERT INTO edu_team_plans (code, school_year_id, age_group_id, title, description, status, curriculum_source_plan_id,
    previous_source_plan_id, legacy_team_without_source, duration_policy_snapshot, version_no, source_plan_id, owner_id,
    owner_title, sent_by, sent_at, issued_by, issued_at, review_round, approvals, applied_policy_revision,
    completion_policy, print_snapshot, published_by, completion_mode, completed_at, author_tracking_since,
    author_coverage, content_revision, created_at, updated_at)
SELECT t.team_code, c.sy_id, t.ag_id, 'Kế hoạch giáo dục tổ ' || t.short_name,
       'Phân bổ nội dung giáo dục vào các chủ đề cho các lớp ' || lower(t.short_name) || ' hai cơ sở.',
       'APPROVED', t.source_id, t.prev_source_id, false, c.dur_snap, 1, NULL, t.tl_id, 'Tổ trưởng',
       t.tl_id, COALESCE(t.sent2, t.sent1), c.vpb_id, t.approved_at, rr.n,
       jsonb_build_array(jsonb_build_object('userId', c.vpb_id, 'name', c.vpb_name, 'roleKey', 'VICE_PRINCIPAL',
           'at', to_char(t.approved_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
           'note', 'Phân bổ hợp lý, đồng ý ban hành cho các lớp triển khai.', 'round', rr.n)),
       c.wf_rev,
       jsonb_build_object('creators', jsonb_build_array('TEAM_LEADER'), 'reviewers', jsonb_build_array('VICE_PRINCIPAL')),
       jsonb_build_object('schemaVersion', 1,
           'capturedAt', to_char(t.approved_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
           'origin', 'PUBLICATION', 'documentType', 'year', 'policyRevision', c.wf_rev,
           'slots', jsonb_build_array(
               jsonb_build_object('roleKey', 'CREATOR', 'label', 'Người soạn', 'names', jsonb_build_array(t.tl_name)),
               jsonb_build_object('roleKey', 'VICE_PRINCIPAL', 'label', 'Phó Hiệu trưởng', 'names', jsonb_build_array(c.vpb_name)))),
       c.vpb_id, 'HUMAN_APPROVAL', t.approved_at, t.created_at, 'COMPLETE', 9, t.created_at, t.approved_at
FROM _e_team t CROSS JOIN _e_ctx c
CROSS JOIN LATERAL (SELECT CASE WHEN t.sent2 IS NULL THEN 1 ELSE 2 END AS n) rr
WHERE NOT EXISTS (SELECT 1 FROM edu_team_plans p WHERE p.school_year_id = c.sy_id AND p.age_group_id = t.ag_id)
ORDER BY t.ag_id;

ALTER TABLE _e_team ADD COLUMN team_id bigint, ADD COLUMN fresh boolean;
UPDATE _e_team t SET team_id = p.id
FROM edu_team_plans p, _e_ctx c
WHERE p.school_year_id = c.sy_id AND p.age_group_id = t.ag_id AND p.version_no = 1;
UPDATE _e_team t SET fresh = NOT EXISTS (SELECT 1 FROM edu_themes x WHERE x.team_plan_id = t.team_id)
                         AND NOT EXISTS (SELECT 1 FROM edu_plan_goals x WHERE x.team_plan_id = t.team_id);

-- Chép cây: kế hoạch năm bản 1 → bản 2 và → kế hoạch tổ, giữ lineage như EduTeamUpdateGenerator.copyContent
CREATE TEMP TABLE _e_copy (src bigint, dst bigint, team boolean);
INSERT INTO _e_copy
SELECT v1.plan_id, v2.plan_id, false FROM _e_yp v2 JOIN _e_yp v1 ON v1.ag_id = v2.ag_id AND v1.version_no = 1
WHERE v2.version_no = 2 AND v2.fresh
UNION ALL
SELECT t.v1_id, t.team_id, true FROM _e_team t WHERE t.fresh;

INSERT INTO edu_plan_sub_domains (domain_id, sort_order, text, lineage_id, curriculum_sub_domain_id, school_year_plan_id, team_plan_id)
SELECT s.domain_id, s.sort_order, s.text, COALESCE(s.lineage_id, s.id), s.curriculum_sub_domain_id,
       CASE WHEN cp.team THEN NULL ELSE cp.dst END, CASE WHEN cp.team THEN cp.dst END
FROM _e_copy cp JOIN edu_plan_sub_domains s ON s.school_year_plan_id = cp.src;

INSERT INTO edu_plan_expected_results (domain_id, sort_order, text, lineage_id, sub_domain_id, curriculum_expected_result_id,
    school_year_plan_id, team_plan_id)
SELECT e.domain_id, e.sort_order, e.text, COALESCE(e.lineage_id, e.id), ds.id, e.curriculum_expected_result_id,
       CASE WHEN cp.team THEN NULL ELSE cp.dst END, CASE WHEN cp.team THEN cp.dst END
FROM _e_copy cp JOIN edu_plan_expected_results e ON e.school_year_plan_id = cp.src
LEFT JOIN edu_plan_sub_domains ss ON ss.id = e.sub_domain_id
LEFT JOIN edu_plan_sub_domains ds ON COALESCE(ds.team_plan_id, ds.school_year_plan_id) = cp.dst
                                 AND ds.lineage_id = COALESCE(ss.lineage_id, ss.id);

INSERT INTO edu_plan_goals (domain_id, sort_order, text, lineage_id, goal_no, curriculum_goal_id, sub_domain_id,
    expected_result_id, display_label, school_year_plan_id, team_plan_id)
SELECT g.domain_id, g.sort_order, g.text, COALESCE(g.lineage_id, g.id), g.goal_no, g.curriculum_goal_id, de.sub_domain_id,
       de.id, g.display_label, CASE WHEN cp.team THEN NULL ELSE cp.dst END, CASE WHEN cp.team THEN cp.dst END
FROM _e_copy cp JOIN edu_plan_goals g ON g.school_year_plan_id = cp.src
JOIN edu_plan_expected_results se ON se.id = g.expected_result_id
JOIN edu_plan_expected_results de ON COALESCE(de.team_plan_id, de.school_year_plan_id) = cp.dst
                                 AND de.lineage_id = COALESCE(se.lineage_id, se.id);

INSERT INTO edu_goal_contents (goal_id, sort_order, text, lineage_id)
SELECT dg.id, c.sort_order, c.text, COALESCE(c.lineage_id, c.id)
FROM _e_copy cp JOIN edu_plan_goals sg ON sg.school_year_plan_id = cp.src
JOIN edu_goal_contents c ON c.goal_id = sg.id
JOIN edu_plan_goals dg ON COALESCE(dg.team_plan_id, dg.school_year_plan_id) = cp.dst AND dg.lineage_id = COALESCE(sg.lineage_id, sg.id)
ORDER BY dg.id, c.sort_order;

INSERT INTO edu_themes (sort_order, name, abbr, start_date, end_date, festivals, note, lineage_id, school_year_plan_id, team_plan_id)
SELECT t.sort_order, t.name, t.abbr, t.start_date, t.end_date, t.festivals, t.note, COALESCE(t.lineage_id, t.id),
       CASE WHEN cp.team THEN NULL ELSE cp.dst END, CASE WHEN cp.team THEN cp.dst END
FROM _e_copy cp JOIN edu_themes t ON t.school_year_plan_id = cp.src
ORDER BY cp.dst, t.sort_order;

INSERT INTO edu_breaks (sort_order, label, start_date, end_date, lineage_id, school_year_plan_id, team_plan_id)
SELECT b.sort_order, b.label, b.start_date, b.end_date, COALESCE(b.lineage_id, b.id),
       CASE WHEN cp.team THEN NULL ELSE cp.dst END, CASE WHEN cp.team THEN cp.dst END
FROM _e_copy cp JOIN edu_breaks b ON b.school_year_plan_id = cp.src;

INSERT INTO edu_content_theme_ticks (content_id, theme_id)
SELECT dc.id, dt.id
FROM _e_copy cp
JOIN edu_themes st ON st.school_year_plan_id = cp.src
JOIN edu_content_theme_ticks k ON k.theme_id = st.id
JOIN edu_goal_contents sc ON sc.id = k.content_id
JOIN edu_themes dt ON COALESCE(dt.team_plan_id, dt.school_year_plan_id) = cp.dst AND dt.lineage_id = COALESCE(st.lineage_id, st.id)
JOIN edu_plan_goals dg ON COALESCE(dg.team_plan_id, dg.school_year_plan_id) = cp.dst
JOIN edu_goal_contents dc ON dc.goal_id = dg.id AND dc.lineage_id = COALESCE(sc.lineage_id, sc.id);

-- Mẫu nhánh của tổ nhớ mẫu nguồn của kế hoạch năm (source_template_id); phiên bản năm thì không
INSERT INTO edu_plan_branch_templates (theme_id, source_template_id, lineage_id, name, start_date, end_date, sort_order,
    created_at, updated_at, school_year_plan_id, team_plan_id)
SELECT dt.id, CASE WHEN cp.team THEN COALESCE(b.lineage_id, b.id) END, COALESCE(b.lineage_id, b.id), b.name, b.start_date,
       b.end_date, b.sort_order, COALESCE(tm.created_at, y.created_at), COALESCE(tm.created_at, y.created_at),
       CASE WHEN cp.team THEN NULL ELSE cp.dst END, CASE WHEN cp.team THEN cp.dst END
FROM _e_copy cp JOIN edu_plan_branch_templates b ON b.school_year_plan_id = cp.src
JOIN edu_themes st ON st.id = b.theme_id
JOIN edu_themes dt ON COALESCE(dt.team_plan_id, dt.school_year_plan_id) = cp.dst AND dt.lineage_id = COALESCE(st.lineage_id, st.id)
LEFT JOIN _e_team tm ON tm.team_id = cp.dst AND cp.team
LEFT JOIN _e_yp y ON y.plan_id = cp.dst AND NOT cp.team;

-- Bản 2 (mẫu giáo lớn): điều chỉnh hai nội dung dinh dưỡng; kế hoạch tổ nhận nội dung mới (tổ chưa sửa ô đó) và
-- có mục rà soát đã được tổ trưởng xử lý
CREATE TEMP TABLE _e_tstate AS
SELECT DISTINCT ON (c.id) c.id AS v1_content_id
FROM _e_yp y
JOIN edu_plan_sub_domains s ON s.school_year_plan_id = y.plan_id AND s.text ILIKE 'Giáo dục dinh dưỡng%'
JOIN edu_plan_goals g ON g.sub_domain_id = s.id
JOIN edu_goal_contents c ON c.goal_id = g.id AND c.sort_order IN (0, 1)
WHERE y.version_no = 1 AND y.ag_id IN (SELECT ag_id FROM _e_yp WHERE version_no = 2 AND NOT review)
  AND g.goal_no = (SELECT min(g2.goal_no) FROM edu_plan_goals g2 WHERE g2.sub_domain_id = s.id);

UPDATE edu_goal_contents c
SET text = c.text || ' Liên hệ với các món ăn trong thực đơn bán trú của trường.'
FROM edu_plan_goals g, _e_copy cp, _e_yp y
WHERE c.goal_id = g.id AND COALESCE(g.team_plan_id, g.school_year_plan_id) = cp.dst
  AND y.version_no = 1 AND y.plan_id = cp.src AND y.ag_id IN (SELECT ag_id FROM _e_yp WHERE version_no = 2 AND NOT review)
  AND c.lineage_id IN (SELECT v1_content_id FROM _e_tstate);

INSERT INTO edu_team_review_items (team_plan_id, source_plan_id, item_kind, item_id, reason, resolved, resolved_at, resolved_by)
SELECT t.team_id, t.source_id, 'CONTENT', c.id, 'SOURCE_CHANGED', true, t.resolved_at, t.tl_id
FROM _e_team t
JOIN edu_plan_goals g ON g.team_plan_id = t.team_id
JOIN edu_goal_contents c ON c.goal_id = g.id
WHERE t.prev_source_id IS NOT NULL AND c.lineage_id IN (SELECT v1_content_id FROM _e_tstate)
ON CONFLICT (team_plan_id, source_plan_id, item_kind, item_id) DO NOTHING;

-- Lịch sử duyệt kế hoạch năm/tổ (edu_plan_events) và người soạn
INSERT INTO edu_plan_events (action, action_label, actor_id, actor_name, actor_role_label, tone, note, occurred_at, school_year_plan_id)
SELECT e.action, e.label, e.actor_id, e.actor_name, e.role_label, e.tone, e.note, e.at, y.plan_id
FROM _e_yp y CROSS JOIN _e_ctx c
CROSS JOIN LATERAL (VALUES
    ('SUBMIT', 'Gửi duyệt kế hoạch', c.vpb_id, c.vpb_name, 'Phó hiệu trưởng', '',
     CASE WHEN y.version_no = 1 THEN 'Kính gửi cô Hiệu trưởng xem xét kế hoạch năm học của nhóm ' || lower(y.short_name) || '.'
          ELSE 'Điều chỉnh nội dung dinh dưỡng theo thực đơn bán trú mới, kính gửi cô Hiệu trưởng duyệt.' END, y.sent_at),
    ('ISSUE', 'Ban hành', c.hp_id, c.hp_name, 'Hiệu trưởng', 'ok', 'Đồng ý ban hành.', y.approved_at)
) e(action, label, actor_id, actor_name, role_label, tone, note, at)
-- Bản đang chờ HT duyệt chỉ có sự kiện gửi duyệt
WHERE e.at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM edu_plan_events x WHERE x.school_year_plan_id = y.plan_id AND x.action = e.action);

INSERT INTO edu_plan_events (action, action_label, actor_id, actor_name, actor_role_label, tone, note, occurred_at, team_plan_id)
SELECT e.action, e.label, e.actor_id, e.actor_name, e.role_label, e.tone, e.note, e.at, t.team_id
FROM _e_team t CROSS JOIN _e_ctx c
CROSS JOIN LATERAL (VALUES
    ('SUBMIT', 'Gửi duyệt kế hoạch', t.tl_id, t.tl_name, 'Tổ trưởng nhóm tuổi', '',
     'Tổ đã phân bổ nội dung vào các chủ đề, kính gửi cô xem giúp.', t.sent1),
    ('RETURN', 'Trả lại để sửa', c.vpb_id, c.vpb_name, 'Phó hiệu trưởng', 'err',
     'Chủ đề "Tết và mùa xuân" còn ít mục tiêu phát triển thẩm mỹ, tổ rà soát lại phần phân bổ giúp chị.', t.returned_at),
    ('SUBMIT', 'Gửi duyệt kế hoạch', t.tl_id, t.tl_name, 'Tổ trưởng nhóm tuổi', '',
     'Tổ đã bổ sung phần thẩm mỹ theo góp ý.', t.sent2),
    ('ISSUE', 'Ban hành', c.vpb_id, c.vpb_name, 'Phó hiệu trưởng', 'ok',
     'Phân bổ hợp lý, đồng ý ban hành cho các lớp triển khai.', t.approved_at)
) e(action, label, actor_id, actor_name, role_label, tone, note, at)
WHERE e.at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM edu_plan_events x WHERE x.team_plan_id = t.team_id AND x.action = e.action AND x.occurred_at = e.at);

INSERT INTO edu_document_authors (user_id, origin, first_authored_at, last_authored_at, school_year_plan_id)
SELECT c.vpb_id, 'CREATOR', y.created_at, y.sent_at, y.plan_id FROM _e_yp y CROSS JOIN _e_ctx c
ON CONFLICT (school_year_plan_id, user_id) WHERE school_year_plan_id IS NOT NULL DO NOTHING;

INSERT INTO edu_document_authors (user_id, origin, first_authored_at, last_authored_at, team_plan_id)
SELECT t.tl_id, 'CONTENT_WRITE', t.created_at + interval '2 hours', COALESCE(t.sent2, t.sent1), t.team_id FROM _e_team t
ON CONFLICT (team_plan_id, user_id) WHERE team_plan_id IS NOT NULL DO NOTHING;

-- ===== Hồ sơ lớp (edu_class_docs): lịch nhánh, gói học kỳ (kế hoạch chủ đề + kế hoạch tuần), hồ sơ chủ đề (kế hoạch ngày) =====
-- Ngày làm việc gần nhất trước hôm nay (d1), trước nữa (d2, d3) cho các bước gửi/duyệt đang dở
ALTER TABLE _e_ctx ADD COLUMN d1 date, ADD COLUMN d2 date, ADD COLUMN d3 date;
UPDATE _e_ctx SET d1 = (cd - 1) - CASE extract(isodow FROM cd - 1) WHEN 6 THEN 1 WHEN 7 THEN 2 ELSE 0 END;
UPDATE _e_ctx SET d2 = (d1 - 1) - CASE extract(isodow FROM d1 - 1) WHEN 6 THEN 1 WHEN 7 THEN 2 ELSE 0 END;
UPDATE _e_ctx SET d3 = (d2 - 1) - CASE extract(isodow FROM d2 - 1) WHEN 6 THEN 1 WHEN 7 THEN 2 ELSE 0 END;

-- Chủ đề của tổ + trạng thái theo hôm nay; học kỳ theo ngày bắt đầu chủ đề (EduSemesters)
CREATE TEMP TABLE _e_tt AS
SELECT t.team_id, t.ag_id, th.id AS theme_id, th.sort_order AS sort, th.name, th.start_date AS start_d, th.end_date AS end_d,
       CASE WHEN th.start_date <= c.s1e THEN 1 ELSE 2 END AS sem_no
FROM _e_team t CROSS JOIN _e_ctx c JOIN edu_themes th ON th.team_plan_id = t.team_id;
ALTER TABLE _e_tt ADD COLUMN state text, ADD COLUMN sem_state text, ADD COLUMN first_of_sem boolean;
UPDATE _e_tt x SET state = CASE WHEN x.start_d <= c.cd THEN 'PAST'
                                WHEN x.start_d = n.s1 THEN 'NEXT'
                                WHEN x.start_d = n.s2 THEN 'AFTER' ELSE 'LATER' END
FROM _e_ctx c,
     (SELECT team_id, min(start_d) FILTER (WHERE rk = 1) AS s1, min(start_d) FILTER (WHERE rk = 2) AS s2
      FROM (SELECT team_id, start_d, dense_rank() OVER (PARTITION BY team_id ORDER BY start_d) AS rk
            FROM _e_tt, _e_ctx WHERE start_d > cd) f GROUP BY team_id) n
WHERE n.team_id = x.team_id;
UPDATE _e_tt SET state = 'PAST' WHERE state IS NULL;
-- Gói học kỳ đã ban hành khi kỳ đó có chủ đề đã bắt đầu hoặc chứa chủ đề kế tiếp (hồ sơ chủ đề cần gói đã ban hành, 7386)
UPDATE _e_tt x SET sem_state = CASE WHEN EXISTS (SELECT 1 FROM _e_tt y WHERE y.team_id = x.team_id AND y.sem_no = x.sem_no
                                                AND y.state IN ('PAST', 'NEXT')) THEN 'APPROVED' ELSE 'DRAFT' END,
                   first_of_sem = x.sort = (SELECT min(y.sort) FROM _e_tt y WHERE y.team_id = x.team_id AND y.sem_no = x.sem_no);

-- Tuần học của tổ = mẫu nhánh (mỗi nhánh một tuần); ghi chú tuần có ngày lễ
CREATE TEMP TABLE _e_tw AS
SELECT b.team_plan_id AS team_id, b.theme_id, b.id AS template_id, b.start_date AS week_start, b.name AS branch_name,
       row_number() OVER (PARTITION BY b.team_plan_id, b.theme_id ORDER BY b.start_date)::int AS wk,
       row_number() OVER (PARTITION BY b.team_plan_id ORDER BY b.start_date)::int AS week_no,
       (SELECT n.note FROM (VALUES (date '2026-09-25', 'Tổ chức vui Tết Trung thu cho trẻ'),
                                   (date '2026-10-20', 'Hoạt động chào mừng ngày Phụ nữ Việt Nam 20/10'),
                                   (date '2026-11-20', 'Chào mừng ngày Nhà giáo Việt Nam 20/11'),
                                   (date '2026-12-22', 'Trò chuyện về chú bộ đội nhân ngày 22/12'),
                                   (date '2027-03-08', 'Hoạt động chào mừng ngày Quốc tế Phụ nữ 8/3'),
                                   (date '2027-05-19', 'Kỷ niệm ngày sinh nhật Bác Hồ 19/5')) n(d, note)
        WHERE n.d BETWEEN b.start_date AND b.end_date) AS note
FROM edu_plan_branch_templates b JOIN _e_team t ON t.team_id = b.team_plan_id;

-- MT của từng chủ đề (có nội dung được tích) và chuỗi nội dung chụp vào kế hoạch chủ đề (EDU-15)
CREATE TEMP TABLE _e_tg AS
SELECT x.*, row_number() OVER (PARTITION BY x.team_id, x.theme_id, x.domain_id ORDER BY x.goal_no)::int AS rn
FROM (
    SELECT tt.team_id, tt.theme_id, g.id AS goal_id, g.goal_no, g.domain_id, g.curriculum_goal_id,
           string_agg('- ' || gc.text, E'\n' ORDER BY gc.sort_order) AS contents_text
    FROM _e_tt tt
    JOIN edu_content_theme_ticks k ON k.theme_id = tt.theme_id
    JOIN edu_goal_contents gc ON gc.id = k.content_id
    JOIN edu_plan_goals g ON g.id = gc.goal_id
    GROUP BY tt.team_id, tt.theme_id, g.id, g.goal_no, g.domain_id, g.curriculum_goal_id
) x;

CREATE TEMP TABLE _e_tact AS
SELECT tt.team_id, tt.theme_id, m.act_no, m.slot, m.domain_id, d.name
FROM _e_tt tt JOIN _e_ag a ON a.ag_id = tt.ag_id
JOIN _e_act_def d ON d.sort = tt.sort AND d.cat = CASE WHEN tt.sort <> 11 THEN 'ALL' WHEN a.cat = 'MGL' THEN 'TTH' ELSE 'MH' END
JOIN _e_act_meta m ON m.act_no = d.act_no;

-- Lớp: GVCN đang hiệu lực soạn; tổ trưởng là GVCN lớp đó thì bước TT bị bỏ qua; PHT theo cơ sở của lớp
CREATE TEMP TABLE _e_cls AS
SELECT cr.id AS class_id, cr.name AS class_name, cr.age_group_id AS ag_id, cr.campus_id, t.team_id, t.tl_id, t.tl_name,
       hr.user_id AS hr_id, hr.full_name AS hr_name, hr.user_id = t.tl_id AS tl_is_hr,
       CASE WHEN cr.campus_id = c.cs_a THEN c.vpa_id ELSE c.vpb_id END AS vp_id,
       CASE WHEN cr.campus_id = c.cs_a THEN c.vpa_name ELSE c.vpb_name END AS vp_name,
       row_number() OVER (ORDER BY cr.campus_id, cr.age_group_id, cr.name)::int AS rk,
       COALESCE(s.state, 'DRAFT') AS next_state
FROM classrooms cr CROSS JOIN _e_ctx c
JOIN _e_team t ON t.ag_id = cr.age_group_id
JOIN LATERAL (
    SELECT sa.user_id, u.full_name FROM staff_assignments sa JOIN user_accounts u ON u.id = sa.user_id
    WHERE sa.staff_role = 'TEACHER' AND sa.is_homeroom AND sa.classroom_id = cr.id AND u.status = 'ACTIVE'
      AND sa.valid_from <= c.cd AND (sa.valid_to IS NULL OR sa.valid_to >= c.cd)
    ORDER BY sa.valid_from DESC LIMIT 1) hr ON true
-- Hồ sơ chủ đề kế tiếp: đủ việc cần làm cho mỗi tổ trưởng, mỗi PHT và GV ở cả hai cơ sở
LEFT JOIN (VALUES ('Lớp Nhà trẻ 2', 'REVIEW_TL'), ('Lớp Mầm 1', 'REVIEW_TL'), ('Lớp Chồi 2', 'REVIEW_TL'), ('Lớp Lá 4', 'REVIEW_TL'),
                  ('Lớp Lá 1', 'REVIEW_VP'), ('Lớp Nhà trẻ 1', 'REVIEW_VP'), ('Lớp Lá 3', 'REVIEW_VP'), ('Lớp Mầm 2', 'REVIEW_VP'),
                  ('Lớp Lá 2', 'RETURNED')) s(name, state) ON s.name = cr.name
WHERE cr.school_year_id = c.sy_id AND cr.status = 'ACTIVE';
UPDATE _e_cls SET next_state = 'REVIEW_VP' WHERE next_state = 'REVIEW_TL' AND tl_is_hr;

CREATE TEMP TABLE _e_hol AS
SELECT DISTINCT d::date AS d, h.name
FROM _e_ctx c JOIN school_holidays h ON h.school_year_id = c.sy_id
CROSS JOIN LATERAL generate_series(h.start_date, h.end_date, interval '1 day') d;

-- Thẻ hoạt động của lớp: mỗi MT của chủ đề gắn vào một thẻ cùng lĩnh vực (xoay vòng); nội dung đầu của MT là contentRefs
CREATE TEMP TABLE _e_cc AS
SELECT cl.class_id, a.theme_id, a.act_no, a.slot, a.name,
       'c-' || md5('card:' || cl.class_id || ':' || a.theme_id || ':' || a.act_no)::uuid::text AS card_id,
       COALESCE(gl.goal_ids, '[]'::jsonb) AS goal_ids, COALESCE(gl.refs, '[]'::jsonb) AS refs,
       COALESCE((SELECT r->>'label' FROM _e_ctx c, jsonb_array_elements(c.week_rows) r WHERE r->>'key' = a.slot), '') AS slot_label,
       row_number() OVER (PARTITION BY cl.class_id, a.theme_id, a.slot ORDER BY a.act_no)::int AS slot_rank,
       count(*) OVER (PARTITION BY cl.class_id, a.theme_id, a.slot)::int AS slot_cnt
FROM _e_cls cl JOIN _e_tact a ON a.team_id = cl.team_id
LEFT JOIN LATERAL (
    SELECT jsonb_agg(g.goal_id::text ORDER BY g.goal_no) AS goal_ids, jsonb_agg(g.goal_id || ':0' ORDER BY g.goal_no) AS refs
    FROM _e_tg g JOIN _e_act_meta m ON m.domain_id = g.domain_id AND m.dom_rank = ((g.rn - 1) % m.dom_cnt) + 1
    WHERE g.team_id = cl.team_id AND g.theme_id = a.theme_id AND m.act_no = a.act_no) gl ON true;
ALTER TABLE _e_cc ADD COLUMN card jsonb, ADD COLUMN pick jsonb;
UPDATE _e_cc SET card = jsonb_build_object('id', card_id, 'slot', slot, 'name', name, 'goalIds', goal_ids, 'contentRefs', refs),
                 pick = jsonb_build_object('id', card_id, 'cardId', card_id, 'goalIds', goal_ids,
                                           'text', CASE WHEN slot_label = '' THEN name ELSE slot_label || ': ' || name END);

-- Tuần của lớp có kế hoạch tuần (thuộc gói học kỳ đã ban hành)
CREATE TEMP TABLE _e_wk AS
SELECT cl.class_id, cl.team_id, w.theme_id, tt.sort, w.week_start, w.wk, w.week_no, w.branch_name, w.note, w.template_id,
       tt.state, tt.sem_no, tt.sem_state, abs(hashtext(cl.class_id || ':' || w.week_start)) % 3 AS v
FROM _e_cls cl JOIN _e_tw w ON w.team_id = cl.team_id JOIN _e_tt tt ON tt.theme_id = w.theme_id;

-- Lượt xếp thẻ vào ô kế hoạch tuần: học xoay vòng 5 ngày, ngoài trời mỗi ngày, chiều Thứ 3 và Thứ 5, cả tuần cho thể dục
-- sáng, trò chuyện, góc. Ngày nghỉ lễ không xếp.
CREATE TEMP TABLE _e_cell AS
SELECT w.class_id, w.week_start, x.d, x.slot, x.cell_key, cc.card_id, cc.name, cc.goal_ids, cc.pick
FROM _e_wk w
CROSS JOIN LATERAL (
    SELECT d, 'hoc' AS slot, 'hoc.' || d AS cell_key, (w.wk - 1) * 5 + d AS seq FROM generate_series(0, 4) d
    UNION ALL SELECT d, 'ngoai_troi', 'ngoai_troi.' || d, 0 FROM generate_series(0, 4) d
    UNION ALL SELECT d, 'chieu', 'chieu.' || d, 0 FROM (VALUES (1), (3)) v(d)
    UNION ALL SELECT NULL::int, s, s || '.all', 0 FROM (VALUES ('the_duc'), ('tro_chuyen'), ('goc')) v(s)
) x
JOIN _e_cc cc ON cc.class_id = w.class_id AND cc.theme_id = w.theme_id AND cc.slot = x.slot
             AND cc.slot_rank = (x.seq % cc.slot_cnt) + 1
WHERE w.sem_state = 'APPROVED'
  AND (x.d IS NULL OR NOT EXISTS (SELECT 1 FROM _e_hol h WHERE h.d = w.week_start + x.d));

-- Bảng góc của từng tuần (dòng Góc của bảng Thứ 2–6 sinh từ bảng góc, EduDayTemplate.normalizeWeek)
CREATE TEMP TABLE _e_corner AS
SELECT w.class_id, w.week_start, k.n, 'goc-' || substr(md5(w.class_id || ':' || w.week_start || ':' || k.n), 1, 8) AS id, k.name,
       CASE k.n WHEN 1 THEN regexp_replace(COALESCE(g.name, 'Gia đình, bán hàng'), '^Góc phân vai:\s*', '')
                WHEN 2 THEN 'Xây dựng mô hình "' || w.branch_name || '"'
                WHEN 3 THEN 'Vẽ, tô màu, hát múa các bài hát về "' || w.branch_name || '"'
                ELSE 'Xem tranh ảnh, làm album về "' || w.branch_name || '"' END AS content,
       CASE k.n WHEN 1 THEN 'Trẻ biết nhận vai, thể hiện hành động phù hợp với vai chơi.'
                WHEN 2 THEN 'Trẻ biết phối hợp các khối, lắp ghép tạo thành mô hình theo ý tưởng.'
                WHEN 3 THEN 'Trẻ mạnh dạn thể hiện ý tưởng qua sản phẩm tạo hình, âm nhạc.'
                ELSE 'Trẻ biết giở sách, xem tranh và kể lại theo tranh.' END AS aims,
       CASE k.n WHEN 1 THEN 'Đồ dùng gia đình, đồ chơi bán hàng.' WHEN 2 THEN 'Khối gỗ, hàng rào, cây xanh, đồ lắp ghép.'
                WHEN 3 THEN 'Giấy màu, bút sáp, đất nặn, xắc xô.' ELSE 'Sách truyện, tranh ảnh, keo dán.' END AS prep
FROM _e_wk w
CROSS JOIN (VALUES (1, 'Góc phân vai'), (2, 'Góc xây dựng'), (3, 'Góc nghệ thuật'), (4, 'Góc sách – truyện')) k(n, name)
LEFT JOIN _e_cc g ON g.class_id = w.class_id AND g.theme_id = w.theme_id AND g.slot = 'goc'
WHERE w.sem_state = 'APPROVED';

-- Gốc gửi/duyệt: gói học kỳ (sp_) và hồ sơ chủ đề (ds_), kèm kịch bản và mốc thời gian
CREATE TEMP TABLE _e_root (class_id bigint, kind text, doc_key text, theme_id bigint, sem_no int, scenario text,
    created_at timestamptz, t_sub1 timestamptz, t_rej timestamptz, t_sub2 timestamptz, t_tl timestamptz, t_vp timestamptz);

INSERT INTO _e_root
SELECT cl.class_id, 'semester', 'sp_' || cl.class_id || '_' || cl.team_id || '_' || s.no, NULL, s.no,
       CASE WHEN st.sem_state = 'APPROVED' THEN 'APPROVED' ELSE 'DRAFT' END,
       timestamptz '2026-08-24 08:00+07' + cl.rk * interval '7 minutes',
       CASE s.no WHEN 1 THEN timestamptz '2026-08-27 14:00+07' ELSE timestamptz '2026-12-09 14:00+07' END + cl.rk * interval '5 minutes',
       NULL, NULL,
       CASE s.no WHEN 1 THEN timestamptz '2026-08-28 09:30+07' ELSE timestamptz '2026-12-10 09:30+07' END + cl.rk * interval '6 minutes',
       CASE s.no WHEN 1 THEN timestamptz '2026-08-29 10:00+07' ELSE timestamptz '2026-12-11 10:00+07' END + cl.rk * interval '6 minutes'
FROM _e_cls cl CROSS JOIN (VALUES (1), (2)) s(no)
JOIN LATERAL (SELECT max(sem_state) AS sem_state FROM _e_tt WHERE team_id = cl.team_id AND sem_no = s.no) st ON true;

INSERT INTO _e_root
SELECT cl.class_id, 'dossier', 'ds_' || cl.class_id || '_' || tt.theme_id, tt.theme_id, NULL,
       CASE WHEN tt.state = 'PAST' AND cl.class_name = 'Lớp Mầm 1' AND tt.sort = 3 AND NOT cl.tl_is_hr THEN 'APPROVED_R2'
            WHEN tt.state = 'PAST' THEN 'APPROVED'
            WHEN tt.state = 'NEXT' THEN cl.next_state ELSE 'DRAFT' END,
       CASE WHEN tt.sem_state = 'APPROVED' AND tt.sem_no = 1 THEN timestamptz '2026-08-24 09:00+07'
            WHEN tt.sem_state = 'APPROVED' THEN timestamptz '2026-12-03 09:00+07'
            ELSE ((c.cd - 7) + time '15:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' END
           + tt.sort * interval '20 minutes' + cl.rk * interval '3 minutes',
       CASE WHEN tt.state = 'NEXT' THEN (c.d3 + time '08:30') AT TIME ZONE 'Asia/Ho_Chi_Minh'
            WHEN cl.class_name = 'Lớp Mầm 1' AND tt.sort = 3 THEN ((tt.start_d - 9) + time '08:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'
            ELSE ((tt.start_d - 7) + time '08:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' END + cl.rk * interval '4 minutes',
       CASE WHEN tt.state = 'NEXT' THEN (c.d1 + time '16:30') AT TIME ZONE 'Asia/Ho_Chi_Minh'
            ELSE ((tt.start_d - 8) + time '16:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' END + cl.rk * interval '3 minutes',
       ((tt.start_d - 7) + time '08:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' + cl.rk * interval '4 minutes',
       CASE WHEN tt.state = 'NEXT' THEN (c.d2 + time '14:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'
            ELSE ((tt.start_d - 7) + time '15:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' END + cl.rk * interval '4 minutes',
       ((tt.start_d - 4) + time '10:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' + cl.rk * interval '4 minutes'
FROM _e_cls cl CROSS JOIN _e_ctx c JOIN _e_tt tt ON tt.team_id = cl.team_id
WHERE tt.sem_state = 'APPROVED' OR tt.first_of_sem;

-- Sự kiện gửi/duyệt/trả lại (history) theo kịch bản; người soạn = GVCN, TT duyệt nếu không phải người soạn
CREATE TEMP TABLE _e_ev AS
SELECT r.doc_key, e.seq, e.action, e.actor_id, e.actor_name, e.role_key, e.role_label, e.tone, e.at, e.note, e.round
FROM _e_root r JOIN _e_cls cl ON cl.class_id = r.class_id
CROSS JOIN LATERAL (SELECT CASE WHEN r.scenario = 'APPROVED_R2' THEN 2 ELSE 1 END AS rnd,
                           abs(hashtext(r.doc_key)) % 3 AS v) z
CROSS JOIN LATERAL (VALUES
    (1, 'submit', cl.hr_id, cl.hr_name, '', 'Người soạn', '', r.t_sub1, '', 1, r.scenario <> 'DRAFT'),
    (2, 'reject', cl.tl_id, cl.tl_name, 'TEAM_LEADER', 'Tổ trưởng', 'err', r.t_rej,
        'Thứ Tư tuần 2 chưa ghi phần chuẩn bị của trẻ ở hoạt động học, em bổ sung giúp chị nhé.', 1, r.scenario = 'APPROVED_R2'),
    (3, 'submit', cl.hr_id, cl.hr_name, '', 'Người soạn', '', r.t_sub2, 'Em đã bổ sung theo góp ý của chị.', 2, r.scenario = 'APPROVED_R2'),
    (4, 'approve', cl.tl_id, cl.tl_name, 'TEAM_LEADER', 'Tổ trưởng', 'ok', r.t_tl,
        (ARRAY['Kế hoạch đầy đủ, chuyển Phó hiệu trưởng duyệt.', 'Đã xem, đồng ý.', 'Hoạt động phù hợp độ tuổi, đề nghị duyệt.'])[z.v + 1],
        z.rnd, r.scenario IN ('APPROVED', 'APPROVED_R2', 'REVIEW_VP') AND NOT cl.tl_is_hr),
    (5, 'approve', cl.vp_id, cl.vp_name, 'VICE_PRINCIPAL', 'Phó hiệu trưởng', 'ok', r.t_vp,
        (ARRAY['Đồng ý ban hành.', 'Đồng ý, lớp triển khai theo kế hoạch.', 'Duyệt ban hành.'])[z.v + 1],
        z.rnd, r.scenario IN ('APPROVED', 'APPROVED_R2')),
    (6, 'reject', CASE WHEN cl.tl_is_hr THEN cl.vp_id ELSE cl.tl_id END, CASE WHEN cl.tl_is_hr THEN cl.vp_name ELSE cl.tl_name END,
        CASE WHEN cl.tl_is_hr THEN 'VICE_PRINCIPAL' ELSE 'TEAM_LEADER' END,
        CASE WHEN cl.tl_is_hr THEN 'Phó hiệu trưởng' ELSE 'Tổ trưởng' END, 'err', r.t_rej,
        'Hoạt động góc tuần đầu còn giống chủ đề trước, em đổi vai chơi cho sát chủ đề mới rồi gửi lại giúp chị.', 1,
        r.scenario = 'RETURNED')
) e(seq, action, actor_id, actor_name, role_key, role_label, tone, at, note, round, keep)
WHERE e.keep;

ALTER TABLE _e_root ADD COLUMN status text, ADD COLUMN review_round int, ADD COLUMN approvals jsonb, ADD COLUMN history jsonb,
    ADD COLUMN last_at timestamptz, ADD COLUMN last_by bigint, ADD COLUMN print_snapshot jsonb, ADD COLUMN completion_policy jsonb,
    ADD COLUMN sign_chain jsonb, ADD COLUMN policy_rev bigint, ADD COLUMN version bigint, ADD COLUMN approver bigint;
UPDATE _e_root r SET
    status = CASE WHEN r.scenario LIKE 'APPROVED%' THEN 'APPROVED' WHEN r.scenario LIKE 'REVIEW%' THEN 'REVIEW' ELSE 'DRAFT' END,
    review_round = COALESCE((SELECT max(e.round) FROM _e_ev e WHERE e.doc_key = r.doc_key), 0),
    history = COALESCE((SELECT jsonb_agg(jsonb_build_object('action', e.action, 'by', e.actor_name, 'actorId', e.actor_id,
                  'round', e.round, 'policyRevision', c.wf_rev,
                  'at', to_char(e.at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'), 'note', e.note,
                  'roleKey', e.role_key, 'role', e.role_label, 'tone', e.tone) ORDER BY e.at, e.seq)
               FROM _e_ev e WHERE e.doc_key = r.doc_key), '[]'::jsonb),
    last_at = COALESCE((SELECT max(e.at) FROM _e_ev e WHERE e.doc_key = r.doc_key), r.created_at),
    last_by = COALESCE((SELECT e.actor_id FROM _e_ev e WHERE e.doc_key = r.doc_key ORDER BY e.at DESC, e.seq DESC LIMIT 1),
                       (SELECT cl.hr_id FROM _e_cls cl WHERE cl.class_id = r.class_id)),
    approver = (SELECT e.actor_id FROM _e_ev e WHERE e.doc_key = r.doc_key AND e.action = 'approve' AND e.role_key = 'VICE_PRINCIPAL')
FROM _e_ctx c;
UPDATE _e_root r SET
    approvals = COALESCE((SELECT jsonb_agg(jsonb_build_object('userId', e.actor_id, 'name', e.actor_name, 'roleKey', e.role_key,
                    'at', to_char(e.at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'), 'note', e.note, 'round', e.round) ORDER BY e.at)
                 FROM _e_ev e WHERE e.doc_key = r.doc_key AND e.action = 'approve' AND e.round = r.review_round), '[]'::jsonb),
    sign_chain = CASE WHEN r.review_round > 0
                      THEN st.workflow_policy -> (CASE WHEN r.kind = 'semester' THEN 'branch' ELSE 'theme' END) -> 'reviewers' END,
    policy_rev = CASE WHEN r.review_round > 0 THEN st.workflow_revision END,
    completion_policy = CASE WHEN r.status = 'APPROVED'
                             THEN st.workflow_policy -> (CASE WHEN r.kind = 'semester' THEN 'branch' ELSE 'theme' END) END,
    version = CASE WHEN r.scenario = 'APPROVED_R2' THEN 5 WHEN r.status = 'APPROVED' OR r.scenario = 'RETURNED' THEN 3
                   WHEN r.status = 'REVIEW' THEN 2 ELSE 1 END,
    print_snapshot = CASE WHEN r.status = 'APPROVED' THEN jsonb_build_object('schemaVersion', 1,
        'capturedAt', to_char(r.last_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
        'origin', 'PUBLICATION', 'documentType', r.kind, 'policyRevision', st.workflow_revision,
        'slots', jsonb_build_array(
            jsonb_build_object('roleKey', 'CREATOR', 'label', 'Người soạn', 'names', jsonb_build_array(cl.hr_name)),
            jsonb_build_object('roleKey', 'TEAM_LEADER', 'label', 'Tổ trưởng',
                'names', CASE WHEN cl.tl_is_hr THEN '[]'::jsonb ELSE jsonb_build_array(cl.tl_name) END),
            jsonb_build_object('roleKey', 'VICE_PRINCIPAL', 'label', 'Phó Hiệu trưởng', 'names', jsonb_build_array(cl.vp_name)))) END
FROM edu_settings st, _e_cls cl
WHERE st.id = 1 AND cl.class_id = r.class_id;

-- Nội dung từng tài liệu thành viên (gắn với gốc qua root_key)
CREATE TEMP TABLE _e_doc (doc_key text, kind text, class_id bigint, team_id bigint, theme_id bigint, week_start date,
    root_key text, content jsonb, created_at timestamptz, created_by bigint, ord int);

-- Kế hoạch chủ đề: MT + nội dung chụp từ kế hoạch tổ, thẻ hoạt động; gói học kỳ còn nháp thì mới soạn vài thẻ
INSERT INTO _e_doc
SELECT 'tp_' || cl.class_id || '_' || tt.theme_id, 'theme', cl.class_id, cl.team_id, tt.theme_id, NULL,
       'sp_' || cl.class_id || '_' || cl.team_id || '_' || tt.sem_no,
       jsonb_build_object(
           'rows', COALESCE((SELECT jsonb_object_agg(g.goal_id::text, jsonb_build_object('contents', g.contents_text))
                             FROM _e_tg g WHERE g.team_id = tt.team_id AND g.theme_id = tt.theme_id), '{}'::jsonb),
           'activities', COALESCE((SELECT jsonb_agg(cc.card ORDER BY cc.act_no) FROM _e_cc cc
                                   WHERE cc.class_id = cl.class_id AND cc.theme_id = tt.theme_id
                                     AND (tt.sem_state = 'APPROVED' OR cc.act_no IN (1, 2, 3, 8))), '[]'::jsonb),
           'envIn', '- Trang trí lớp theo chủ đề "' || tt.name || '": tranh ảnh, sản phẩm tạo hình của trẻ.' || E'\n'
                    || '- Bổ sung đồ dùng, đồ chơi ở các góc phù hợp với chủ đề.',
           'envOut', '- Sân trường sạch sẽ, an toàn; vườn rau, góc thiên nhiên cho trẻ quan sát, chăm sóc.' || E'\n'
                    || '- Đồ chơi ngoài trời, nguyên vật liệu thiên nhiên cho trẻ khám phá.'),
       r.created_at, cl.hr_id, 2
FROM _e_cls cl JOIN _e_tt tt ON tt.team_id = cl.team_id
JOIN _e_root r ON r.doc_key = 'ds_' || cl.class_id || '_' || tt.theme_id;

-- Kế hoạch tuần (thuộc gói học kỳ đã ban hành)
INSERT INTO _e_doc
SELECT 'wp_' || w.class_id || '_' || w.week_start, 'week', w.class_id, w.team_id, w.theme_id, w.week_start,
       'sp_' || w.class_id || '_' || w.team_id || '_' || w.sem_no,
       jsonb_build_object(
           'weekNo', w.week_no,
           'aims', jsonb_build_object(
               'knowledge', (ARRAY['Trẻ biết một số đặc điểm nổi bật về "' || w.branch_name || '"; nhớ tên bài hát, bài thơ, câu chuyện trong tuần.',
                                   'Trẻ nói được những điều quen thuộc về "' || w.branch_name || '" qua quan sát, trò chuyện.',
                                   'Trẻ có hiểu biết ban đầu về "' || w.branch_name || '", biết liên hệ với cuộc sống hằng ngày.'])[w.v + 1],
               'skills', (ARRAY['Rèn kỹ năng quan sát, so sánh, trả lời câu hỏi rõ ràng; phối hợp vận động khéo léo.',
                                'Trẻ mạnh dạn giao tiếp, biết hợp tác với bạn trong nhóm; rèn sự khéo léo của đôi tay.',
                                'Phát triển ngôn ngữ mạch lạc, kỹ năng tạo hình và vận động theo nhạc.'])[w.v + 1],
               'attitude', (ARRAY['Trẻ hứng thú tham gia hoạt động, giữ gìn đồ dùng đồ chơi.',
                                  'Trẻ đoàn kết, lễ phép với cô và người lớn, yêu quý bạn bè.',
                                  'Trẻ có ý thức giữ gìn vệ sinh, bảo vệ môi trường lớp học.'])[w.v + 1]),
           'prep', jsonb_build_object(
               'teacher', 'Tranh ảnh, video về "' || w.branch_name || '"; nhạc các bài hát trong tuần; đồ dùng cho hoạt động học.',
               'child', 'Vở tạo hình, bút sáp, đất nặn, đồ chơi ở các góc.',
               'parent', 'Phối hợp cùng cô sưu tầm tranh ảnh, nguyên vật liệu tái chế về "' || w.branch_name || '".'),
           'corners', (SELECT jsonb_agg(jsonb_build_object('id', k.id, 'name', k.name, 'content', k.content, 'aims', k.aims,
                                                           'prep', k.prep, 'steps', '') ORDER BY k.n)
                       FROM _e_corner k WHERE k.class_id = w.class_id AND k.week_start = w.week_start),
           'grid', jsonb_build_object(
               'don', jsonb_build_object('allWeek', true, 'common', 'Cô đón trẻ, trao đổi với phụ huynh về tình hình sức khỏe của trẻ; cho trẻ chơi tự do ở các góc.',
                                         'days', jsonb_build_array('', '', '', '', ''), 'extra', ''),
               'tro_chuyen', jsonb_build_object('allWeek', true, 'common',
                   COALESCE((SELECT x.name FROM _e_cell x WHERE x.class_id = w.class_id AND x.week_start = w.week_start AND x.cell_key = 'tro_chuyen.all'), ''),
                   'days', jsonb_build_array('', '', '', '', ''), 'extra', ''),
               'the_duc', jsonb_build_object('allWeek', true, 'common',
                   COALESCE((SELECT x.name FROM _e_cell x WHERE x.class_id = w.class_id AND x.week_start = w.week_start AND x.cell_key = 'the_duc.all'), ''),
                   'days', jsonb_build_array('', '', '', '', ''), 'extra', ''),
               'hoc', jsonb_build_object('allWeek', false, 'common', '', 'extra', '', 'days',
                   (SELECT jsonb_agg(CASE WHEN h.d IS NOT NULL THEN jsonb_build_object('subject', '', 'topic', h.name)
                                          WHEN x.name IS NULL THEN jsonb_build_object('subject', '', 'topic', '')
                                          ELSE jsonb_build_object('subject', split_part(x.name, ' – ', 1),
                                                                  'topic', substr(x.name, strpos(x.name, ' – ') + 3)) END ORDER BY dd)
                    FROM generate_series(0, 4) dd LEFT JOIN _e_hol h ON h.d = w.week_start + dd
                    LEFT JOIN _e_cell x ON x.class_id = w.class_id AND x.week_start = w.week_start AND x.cell_key = 'hoc.' || dd)),
               'goc', jsonb_build_object('allWeek', true, 'extra', '', 'days', jsonb_build_array('', '', '', '', ''), 'common',
                   (SELECT string_agg(k.name || ': ' || k.content, E'\n' ORDER BY k.n) FROM _e_corner k
                    WHERE k.class_id = w.class_id AND k.week_start = w.week_start)),
               'ngoai_troi', jsonb_build_object('allWeek', false, 'common', '',
                   'extra', 'Chơi tự do với đồ chơi ngoài trời, vẽ phấn, chơi với lá cây',
                   'days', (SELECT jsonb_agg(CASE WHEN h.d IS NOT NULL THEN 'Nghỉ lễ' ELSE COALESCE(x.name, '') END ORDER BY dd)
                            FROM generate_series(0, 4) dd LEFT JOIN _e_hol h ON h.d = w.week_start + dd
                            LEFT JOIN _e_cell x ON x.class_id = w.class_id AND x.week_start = w.week_start AND x.cell_key = 'ngoai_troi.' || dd)),
               'an_ngu', jsonb_build_object('allWeek', true, 'common', 'Rèn thói quen rửa tay trước và sau khi ăn, mời cô mời bạn trước khi ăn; ngủ đúng giờ, đủ giấc.',
                                            'days', jsonb_build_array('', '', '', '', ''), 'extra', ''),
               'chieu', jsonb_build_object('allWeek', false, 'common', '', 'extra', 'Chơi tự chọn; nêu gương cuối ngày',
                   'days', (SELECT jsonb_agg(CASE WHEN h.d IS NOT NULL THEN ''
                                                  ELSE COALESCE(x.name, (ARRAY['Ôn bài hát, bài thơ đã học buổi sáng', '',
                                                      'Chơi trò chơi dân gian "Dung dăng dung dẻ"', '', 'Văn nghệ cuối tuần, nêu gương bé ngoan'])[dd + 1]) END ORDER BY dd)
                            FROM generate_series(0, 4) dd LEFT JOIN _e_hol h ON h.d = w.week_start + dd
                            LEFT JOIN _e_cell x ON x.class_id = w.class_id AND x.week_start = w.week_start AND x.cell_key = 'chieu.' || dd)),
               'tra_tre', jsonb_build_object('allWeek', true, 'common', 'Trẻ vệ sinh cá nhân, chuẩn bị đồ dùng ra về; cô trao đổi với phụ huynh về hoạt động trong ngày.',
                                             'days', jsonb_build_array('', '', '', '', ''), 'extra', '')),
           'picks', COALESCE((SELECT jsonb_object_agg(x.cell_key, jsonb_build_array(CASE WHEN x.slot = 'goc'
                                  THEN x.pick || jsonb_build_object('cornerId', (SELECT k.id FROM _e_corner k WHERE k.class_id = x.class_id AND k.week_start = x.week_start AND k.n = 1))
                                  ELSE x.pick END))
                              FROM _e_cell x WHERE x.class_id = w.class_id AND x.week_start = w.week_start), '{}'::jsonb)),
       CASE WHEN w.sem_no = 1 THEN timestamptz '2026-08-25 08:00+07' ELSE timestamptz '2026-12-04 08:00+07' END
           + (w.week_no - 1) % 19 * interval '25 minutes' + cl.rk * interval '2 minutes',
       cl.hr_id, 3
FROM _e_wk w JOIN _e_cls cl ON cl.class_id = w.class_id
WHERE w.sem_state = 'APPROVED';

-- Kế hoạch ngày (thuộc hồ sơ chủ đề): chủ đề đã bắt đầu đủ mọi tuần; chủ đề kế tiếp theo kịch bản; chủ đề sau nữa tuần đầu
INSERT INTO _e_doc
SELECT 'dd_' || w.class_id || '_' || w.week_start, 'day', w.class_id, w.team_id, w.theme_id, w.week_start,
       'ds_' || w.class_id || '_' || w.theme_id,
       jsonb_build_object(
           'days', (SELECT jsonb_agg(CASE WHEN h.d IS NOT NULL THEN jsonb_build_object('date', (w.week_start + dd)::text)
               ELSE jsonb_build_object('date', (w.week_start + dd)::text, 'sections', jsonb_build_object(
                   'hoc', jsonb_build_object(
                       'aims', '- Kiến thức: ' || CASE split_part(xh.name, ' – ', 1)
                               WHEN 'Thể dục' THEN 'Trẻ biết thực hiện vận động "' || t.topic || '" đúng kỹ thuật.'
                               WHEN 'Khám phá' THEN 'Trẻ biết tên gọi, đặc điểm nổi bật qua nội dung "' || t.topic || '".'
                               WHEN 'Toán' THEN 'Trẻ thực hiện được yêu cầu "' || t.topic || '" theo khả năng.'
                               WHEN 'Văn học' THEN 'Trẻ nhớ tên, hiểu nội dung ' || lower(left(t.topic, 1)) || substr(t.topic, 2) || '.'
                               WHEN 'Âm nhạc' THEN 'Trẻ hứng thú tham gia: ' || lower(left(t.topic, 1)) || substr(t.topic, 2) || '; hát đúng giai điệu, thuộc lời.'
                               WHEN 'Tạo hình' THEN 'Trẻ biết sử dụng kỹ năng đã học để ' || lower(left(t.topic, 1)) || substr(t.topic, 2) || '.'
                               ELSE 'Trẻ hiểu nội dung hoạt động.' END
                               || E'\n' || '- Kỹ năng: ' || (ARRAY['Trẻ quan sát, trả lời câu hỏi rõ ràng, đủ câu.',
                                   'Trẻ thực hiện đúng yêu cầu, phối hợp cùng bạn.', 'Trẻ mạnh dạn, tự tin thể hiện ý kiến.'])[w.v + 1]
                               || E'\n' || '- Thái độ: Trẻ hứng thú, tích cực tham gia hoạt động.',
                       'prep', '- Đồ dùng của cô: ' || CASE split_part(xh.name, ' – ', 1)
                               WHEN 'Thể dục' THEN 'sân tập, vạch chuẩn, nhạc bài tập.'
                               WHEN 'Khám phá' THEN 'tranh ảnh, video, vật thật về "' || w.branch_name || '".'
                               WHEN 'Toán' THEN 'thẻ số, lô tô, đồ dùng trực quan.'
                               WHEN 'Văn học' THEN 'tranh truyện khổ lớn, rối tay, nhạc nền.'
                               WHEN 'Âm nhạc' THEN 'nhạc bài hát, xắc xô, phách tre.'
                               WHEN 'Tạo hình' THEN 'tranh mẫu, giấy màu, bút sáp, keo dán.'
                               ELSE 'đồ dùng trực quan.' END
                               || E'\n' || '- Đồ dùng của trẻ: đủ cho mỗi trẻ, chỗ ngồi thoải mái.',
                       'rows', jsonb_build_array(
                           jsonb_build_object('id', substr(md5(w.class_id || w.week_start::text || dd || 'h1'), 1, 8), 'title', 'Ổn định tổ chức, gây hứng thú',
                               'teacher', 'Cô cho trẻ hát bài hát quen thuộc, trò chuyện về "' || w.branch_name || '" và dẫn dắt vào bài.',
                               'child', 'Trẻ hát, trò chuyện cùng cô.', 'note', ''),
                           jsonb_build_object('id', substr(md5(w.class_id || w.week_start::text || dd || 'h2'), 1, 8), 'title', 'Nội dung trọng tâm',
                               'teacher', 'Cô giới thiệu nội dung "' || t.topic || '"; làm mẫu, giải thích rồi cho trẻ thực hiện theo nhóm, cá nhân; cô quan sát, sửa sai và động viên trẻ.',
                               'child', 'Trẻ quan sát, lắng nghe, thực hiện theo hướng dẫn của cô.', 'note', ''),
                           jsonb_build_object('id', substr(md5(w.class_id || w.week_start::text || dd || 'h3'), 1, 8), 'title', 'Kết thúc',
                               'teacher', 'Cô nhận xét, tuyên dương trẻ; cho trẻ thu dọn đồ dùng và chuyển hoạt động.',
                               'child', 'Trẻ cất đồ dùng đúng nơi quy định.', 'note', ''))),
                   'ngoai_troi', jsonb_build_object(
                       'aims', '- Trẻ được hít thở không khí trong lành, quan sát và trò chuyện về đối tượng.' || E'\n' || '- Trẻ chơi đoàn kết, giữ an toàn khi chơi.',
                       'prep', 'Sân chơi sạch, đồ chơi ngoài trời, phấn, bóng, vòng.',
                       'rows', jsonb_build_array(
                           jsonb_build_object('id', substr(md5(w.class_id || w.week_start::text || dd || 'n1'), 1, 8), 'title', 'Hoạt động có mục đích',
                               'teacher', split_part(COALESCE(xn.name, 'Quan sát thời tiết'), '; ', 1), 'child', 'Trẻ quan sát, trả lời câu hỏi của cô.', 'note', ''),
                           jsonb_build_object('id', substr(md5(w.class_id || w.week_start::text || dd || 'n2'), 1, 8), 'title', 'Trò chơi vận động',
                               'teacher', COALESCE(NULLIF(split_part(COALESCE(xn.name, ''), '; ', 2), ''), 'TCVĐ "Mèo đuổi chuột"'), 'child', 'Trẻ chơi đúng luật.', 'note', ''),
                           jsonb_build_object('id', substr(md5(w.class_id || w.week_start::text || dd || 'n3'), 1, 8), 'title', 'Chơi theo ý thích',
                               'teacher', 'Trẻ chơi với đồ chơi ngoài trời, vẽ phấn trên sân; cô bao quát, đảm bảo an toàn.', 'child', '', 'note', ''))),
                   'goc', jsonb_build_object(
                       'aims', 'Trẻ biết nhận vai chơi, thể hiện hành động phù hợp với vai; biết phối hợp, chia sẻ đồ chơi với bạn.',
                       'prep', 'Đồ dùng, đồ chơi ở các góc theo chủ đề "' || tt.name || '".',
                       'rows', (SELECT jsonb_agg(jsonb_build_object('id', substr(md5(k.id || dd), 1, 8), 'title', k.name, 'teacher', k.content,
                                                                    'child', 'Trẻ thỏa thuận vai chơi, chơi cùng bạn.', 'note', '') ORDER BY k.n)
                                FROM _e_corner k WHERE k.class_id = w.class_id AND k.week_start = w.week_start)),
                   'chieu', jsonb_build_object(
                       'aims', 'Trẻ ôn luyện kiến thức, kỹ năng đã học; hứng thú tham gia hoạt động chiều.',
                       'prep', 'Đồ dùng cho hoạt động chiều, đồ chơi ở các góc.',
                       'rows', jsonb_build_array(jsonb_build_object('id', substr(md5(w.class_id || w.week_start::text || dd || 'c1'), 1, 8), 'title', '',
                           'teacher', COALESCE(xc.name, (ARRAY['Ôn bài hát, bài thơ đã học buổi sáng', '', 'Chơi trò chơi dân gian "Dung dăng dung dẻ"', '',
                                                               'Văn nghệ cuối tuần, nêu gương bé ngoan'])[dd + 1]),
                           'child', '', 'note', ''))))) END ORDER BY dd)
               FROM generate_series(0, 4) dd
               LEFT JOIN _e_hol h ON h.d = w.week_start + dd
               LEFT JOIN _e_cell xh ON xh.class_id = w.class_id AND xh.week_start = w.week_start AND xh.cell_key = 'hoc.' || dd
               LEFT JOIN _e_cell xn ON xn.class_id = w.class_id AND xn.week_start = w.week_start AND xn.cell_key = 'ngoai_troi.' || dd
               LEFT JOIN _e_cell xc ON xc.class_id = w.class_id AND xc.week_start = w.week_start AND xc.cell_key = 'chieu.' || dd
               CROSS JOIN LATERAL (SELECT COALESCE(substr(xh.name, strpos(xh.name, ' – ') + 3), 'hoạt động trong ngày') AS topic) t),
           'dayGoals', (SELECT jsonb_agg(COALESCE(gs.ids, '[]'::jsonb) ORDER BY dd)
                        FROM generate_series(0, 4) dd
                        LEFT JOIN LATERAL (SELECT jsonb_agg(DISTINCT g.v) AS ids
                                           FROM _e_cell x CROSS JOIN LATERAL jsonb_array_elements_text(x.goal_ids) g(v)
                                           WHERE x.class_id = w.class_id AND x.week_start = w.week_start
                                             AND (x.d = dd OR x.d IS NULL)
                                             AND NOT EXISTS (SELECT 1 FROM _e_hol h WHERE h.d = w.week_start + dd)) gs ON true)),
       CASE WHEN w.state = 'PAST' THEN ((tt.start_d - 12 + (w.wk - 1)) + time '14:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'
            WHEN w.state = 'NEXT' THEN ((c.d3 - (5 - w.wk)) + time '15:00') AT TIME ZONE 'Asia/Ho_Chi_Minh'
            ELSE (c.d2 + time '16:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' END + cl.rk * interval '3 minutes',
       cl.hr_id, 5
FROM _e_wk w JOIN _e_cls cl ON cl.class_id = w.class_id JOIN _e_tt tt ON tt.theme_id = w.theme_id CROSS JOIN _e_ctx c
WHERE w.sem_state = 'APPROVED'
  AND (w.state = 'PAST'
       OR (w.state = 'NEXT' AND (cl.next_state <> 'DRAFT' OR w.wk <= 2))
       OR (w.state = 'AFTER' AND w.wk = 1));

-- Lịch nhánh của lớp (Lịch lớp v2): lấy nguyên chủ đề và mẫu nhánh của tổ; không mang trạng thái duyệt
INSERT INTO edu_class_docs (doc_key, kind, classroom_id, status, content, approvals, history, created_by, updated_by,
    created_at, updated_at, version, review_round, author_tracking_since, author_coverage, duration_policy_snapshot, team_plan_id)
SELECT 'bp_' || cl.class_id || '_' || cl.team_id, 'branch', cl.class_id, 'DRAFT',
       jsonb_build_object('schemaVersion', 2,
           'themeSchedules', (SELECT jsonb_agg(jsonb_build_object('themeId', tt.theme_id, 'startDate', tt.start_d::text,
                                                                  'endDate', tt.end_d::text) ORDER BY tt.sort)
                              FROM _e_tt tt WHERE tt.team_id = cl.team_id),
           'branches', (SELECT jsonb_agg(jsonb_build_object('key', md5('bp:' || cl.class_id || ':' || w.template_id)::uuid::text,
                                'themeId', w.theme_id, 'sourceTemplateId', w.template_id, 'name', w.branch_name,
                                'startDate', w.week_start::text, 'endDate', (w.week_start + 4)::text,
                                'weekNotes', CASE WHEN w.note IS NULL THEN '{}'::jsonb ELSE jsonb_build_object('0', w.note) END)
                                ORDER BY w.week_start)
                        FROM _e_tw w WHERE w.team_id = cl.team_id),
           'names', (SELECT jsonb_object_agg(w.week_start::text, w.branch_name) FROM _e_tw w WHERE w.team_id = cl.team_id),
           'notes', COALESCE((SELECT jsonb_object_agg(w.week_start::text, w.note) FROM _e_tw w
                              WHERE w.team_id = cl.team_id AND w.note IS NOT NULL), '{}'::jsonb)),
       '[]'::jsonb, '[]'::jsonb, cl.hr_id, cl.hr_id,
       timestamptz '2026-08-24 08:00+07' + cl.rk * interval '7 minutes', timestamptz '2026-08-26 16:00+07' + cl.rk * interval '5 minutes',
       4, 0, timestamptz '2026-08-24 08:00+07' + cl.rk * interval '7 minutes', 'COMPLETE', c.dur_snap, cl.team_id
FROM _e_cls cl CROSS JOIN _e_ctx c
ORDER BY cl.rk
ON CONFLICT DO NOTHING;

-- Gốc gói học kỳ / hồ sơ chủ đề
INSERT INTO edu_class_docs (doc_key, kind, classroom_id, theme_id, semester_no, status, content, approvals, history,
    created_by, updated_by, created_at, updated_at, version, sign_chain, review_round, applied_policy_revision,
    completion_policy, print_snapshot, completion_mode, completed_at, author_tracking_since, author_coverage, team_plan_id)
SELECT r.doc_key, r.kind, r.class_id, r.theme_id, r.sem_no, r.status, '{}'::jsonb, r.approvals, r.history,
       cl.hr_id, r.last_by, r.created_at, r.last_at, r.version, r.sign_chain, r.review_round, r.policy_rev,
       r.completion_policy, r.print_snapshot, CASE WHEN r.status = 'APPROVED' THEN 'HUMAN_APPROVAL' END,
       CASE WHEN r.status = 'APPROVED' THEN r.last_at END, r.created_at, 'COMPLETE', cl.team_id
FROM _e_root r JOIN _e_cls cl ON cl.class_id = r.class_id
WHERE r.kind = 'semester'
ORDER BY cl.rk, r.sem_no
ON CONFLICT DO NOTHING;

-- Hồ sơ chủ đề chỉ có khi kế hoạch chủ đề có (ensureRoots): gói học kỳ chứa nó đúng trạng thái dự kiến hoặc đã có bản soạn
INSERT INTO edu_class_docs (doc_key, kind, classroom_id, theme_id, semester_no, status, content, approvals, history,
    created_by, updated_by, created_at, updated_at, version, sign_chain, review_round, applied_policy_revision,
    completion_policy, print_snapshot, completion_mode, completed_at, author_tracking_since, author_coverage, team_plan_id)
SELECT r.doc_key, r.kind, r.class_id, r.theme_id, r.sem_no, r.status, '{}'::jsonb, r.approvals, r.history,
       cl.hr_id, r.last_by, r.created_at, r.last_at, r.version, r.sign_chain, r.review_round, r.policy_rev,
       r.completion_policy, r.print_snapshot, CASE WHEN r.status = 'APPROVED' THEN 'HUMAN_APPROVAL' END,
       CASE WHEN r.status = 'APPROVED' THEN r.last_at END, r.created_at, 'COMPLETE', cl.team_id
FROM _e_root r JOIN _e_cls cl ON cl.class_id = r.class_id
JOIN _e_tt tt ON tt.theme_id = r.theme_id
JOIN _e_root sr ON sr.doc_key = 'sp_' || cl.class_id || '_' || cl.team_id || '_' || tt.sem_no
WHERE r.kind = 'dossier'
  AND (EXISTS (SELECT 1 FROM edu_class_docs s WHERE s.doc_key = sr.doc_key AND s.status = sr.status)
       OR EXISTS (SELECT 1 FROM edu_class_docs x WHERE x.doc_key = 'tp_' || cl.class_id || '_' || tt.theme_id))
ORDER BY cl.rk, r.theme_id
ON CONFLICT DO NOTHING;

-- Thành viên mang cùng trạng thái, người duyệt, lịch sử của gốc (actOnClassDoc cập nhật cả gốc và thành viên)
INSERT INTO edu_class_docs (doc_key, kind, classroom_id, theme_id, week_start, status, content, approvals, history,
    created_by, updated_by, created_at, updated_at, version, sign_chain, review_round, applied_policy_revision,
    completion_policy, print_snapshot, completion_mode, completed_at, author_tracking_since, author_coverage, team_plan_id)
SELECT d.doc_key, d.kind, d.class_id, d.theme_id, d.week_start, r.status, d.content, r.approvals, r.history,
       d.created_by, CASE WHEN r.review_round > 0 THEN r.last_by ELSE d.created_by END,
       d.created_at, GREATEST(d.created_at, r.last_at), CASE WHEN r.review_round > 0 THEN 2 ELSE 1 END,
       r.sign_chain, r.review_round, r.policy_rev, r.completion_policy, r.print_snapshot,
       CASE WHEN r.status = 'APPROVED' THEN 'HUMAN_APPROVAL' END, CASE WHEN r.status = 'APPROVED' THEN r.last_at END,
       d.created_at, 'COMPLETE', d.team_id
FROM _e_doc d JOIN _e_root r ON r.doc_key = d.root_key
-- Chạy lại ngày sau: gốc đã có ở trạng thái khác (người dùng đã thao tác) thì không thêm thành viên lệch trạng thái
JOIN edu_class_docs rd ON rd.doc_key = r.doc_key AND rd.status = r.status AND rd.review_round = r.review_round
ORDER BY d.ord, d.class_id, d.theme_id, d.week_start
ON CONFLICT DO NOTHING;

-- Bản ban hành của gốc đã duyệt: chụp chữ ký + nội dung thành viên (+ tên nhánh của học kỳ với gói học kỳ)
INSERT INTO edu_class_doc_releases (class_doc_id, dossier_version, print_snapshot, approved_by, released_at)
SELECT rd.id, rd.version,
       jsonb_build_object('signatures', rd.print_snapshot, 'members',
           CASE WHEN r.kind = 'semester'
                THEN jsonb_build_array(jsonb_build_object('kind', 'branch', 'names',
                         COALESCE((SELECT jsonb_object_agg(w.week_start::text, w.branch_name) FROM _e_tw w JOIN _e_cls cl ON cl.team_id = w.team_id
                                   WHERE cl.class_id = r.class_id
                                     AND w.week_start BETWEEN CASE r.sem_no WHEN 1 THEN c.s1s ELSE c.s2s END
                                                          AND CASE r.sem_no WHEN 1 THEN c.s1e ELSE c.s2e END), '{}'::jsonb)))
                ELSE '[]'::jsonb END
           || COALESCE((SELECT jsonb_agg(jsonb_build_object('kind', m.kind, 'docKey', m.doc_key, 'content', m.content)
                                         || CASE WHEN m.week_start IS NOT NULL THEN jsonb_build_object('weekStart', m.week_start::text)
                                                 ELSE '{}'::jsonb END ORDER BY m.id)
                        FROM _e_doc d JOIN edu_class_docs m ON m.doc_key = d.doc_key WHERE d.root_key = r.doc_key), '[]'::jsonb)),
       r.approver, r.last_at
FROM _e_root r CROSS JOIN _e_ctx c
JOIN edu_class_docs rd ON rd.doc_key = r.doc_key
WHERE r.status = 'APPROVED' AND rd.status = 'APPROVED' AND r.approver IS NOT NULL
ON CONFLICT (class_doc_id, dossier_version) DO NOTHING;

-- Người soạn (loại trừ khỏi người duyệt): GVCN tạo lịch nhánh, kế hoạch chủ đề, tuần, ngày
INSERT INTO edu_document_authors (class_doc_id, user_id, origin, first_authored_at, last_authored_at)
SELECT x.id, x.created_by, 'CREATOR', x.created_at,
       CASE WHEN x.review_round > 0 AND x.history <> '[]'::jsonb
            THEN LEAST(x.updated_at, (x.history -> 0 ->> 'at')::timestamptz) ELSE x.updated_at END
FROM edu_class_docs x
WHERE x.kind IN ('branch', 'theme', 'week', 'day')
  AND x.doc_key IN (SELECT doc_key FROM _e_doc UNION ALL SELECT 'bp_' || class_id || '_' || team_id FROM _e_cls)
ON CONFLICT (class_doc_id, user_id) WHERE class_doc_id IS NOT NULL DO NOTHING;

-- ===== Kho học liệu cá nhân (spec 901): chỉ liên kết YouTube / Google Docs, không tệp S3, chưa gắn giáo án =====
CREATE TEMP TABLE _e_res_def (no int, title text, link text, sort int);
INSERT INTO _e_res_def VALUES
 (1, 'Bài hát "Cháu đi mẫu giáo"', 'yt', 1),
 (2, 'Bộ tranh "Trường mầm non của bé"', 'slides', 1),
 (3, 'Video "Bé tập rửa tay đúng cách"', 'yt', 2),
 (4, 'Truyện tranh "Ba cô gái"', 'slides', 3),
 (5, 'Bài hát "Cả nhà thương nhau"', 'yt', 3),
 (6, 'Bài giảng "Một số nghề quen thuộc"', 'slides', 4),
 (7, 'Video "Ngày Nhà giáo Việt Nam 20/11"', 'yt', 4),
 (8, 'Bộ tranh "Con vật nuôi trong gia đình"', 'slides', 5),
 (9, 'Bài hát "Đố bạn"', 'yt', 5),
 (10, 'Video "Hạt đậu nảy mầm"', 'yt', 6),
 (11, 'Truyện "Sự tích quả dưa hấu"', 'doc', 6),
 (12, 'Bài hát "Sắp đến Tết rồi"', 'yt', 7);

-- Mỗi GV đang được phân lớp (cả GV chủ nhiệm và GV cùng lớp) có 5 học liệu chọn theo hash, kèm nhãn chủ đề + MT
INSERT INTO edu_resource_items (kind, title, owner_id, age_group_id, upload_request_id, url, created_at, updated_at)
SELECT 'LINK', x.title, x.user_id, x.ag_id, md5('res:' || x.user_id || ':' || x.no)::uuid,
       CASE x.link WHEN 'yt' THEN 'https://www.youtube.com/watch?v=' || substr(md5('yt:' || x.no), 1, 11)
                   WHEN 'slides' THEN 'https://docs.google.com/presentation/d/' || md5('sl:' || x.user_id || ':' || x.no) || '/edit'
                   ELSE 'https://docs.google.com/document/d/' || md5('dc:' || x.user_id || ':' || x.no) || '/edit' END,
       x.at, x.at
FROM (
    SELECT t.user_id, t.ag_id, r.no, r.title, r.link,
           ((LEAST(c.cd - 1, date '2026-09-05' + abs(hashtext(t.user_id || ':' || r.no)) % 60)) + time '20:15')
               AT TIME ZONE 'Asia/Ho_Chi_Minh' AS at,
           row_number() OVER (PARTITION BY t.user_id ORDER BY abs(hashtext('pick:' || t.user_id || ':' || r.no))) AS rk
    FROM (SELECT DISTINCT ON (sa.user_id) sa.user_id, cr.age_group_id AS ag_id
          FROM staff_assignments sa JOIN user_accounts u ON u.id = sa.user_id JOIN classrooms cr ON cr.id = sa.classroom_id
          CROSS JOIN _e_ctx c
          WHERE sa.staff_role = 'TEACHER' AND u.status = 'ACTIVE' AND cr.school_year_id = c.sy_id
            AND sa.valid_from <= c.cd AND (sa.valid_to IS NULL OR sa.valid_to >= c.cd)
          ORDER BY sa.user_id, sa.is_homeroom DESC) t
    CROSS JOIN _e_res_def r CROSS JOIN _e_ctx c
) x
WHERE x.rk <= 5
ON CONFLICT (owner_id, upload_request_id) DO NOTHING;

INSERT INTO edu_resource_item_tags (item_id, kind, ref_id, curriculum_goal_id, name, school_year_id)
SELECT i.id, 'THEME', tt.theme_id, NULL, tt.name, c.sy_id
FROM edu_resource_items i CROSS JOIN _e_ctx c
JOIN _e_res_def r ON i.upload_request_id = md5('res:' || i.owner_id || ':' || r.no)::uuid
JOIN _e_team t ON t.ag_id = i.age_group_id
JOIN _e_tt tt ON tt.team_id = t.team_id AND tt.sort = r.sort
ON CONFLICT (item_id, kind, ref_id) WHERE attachment_id IS NULL DO NOTHING;

INSERT INTO edu_resource_item_tags (item_id, kind, ref_id, curriculum_goal_id, name, school_year_id)
SELECT i.id, 'GOAL', g.goal_id, g.curriculum_goal_id, NULL, NULL
FROM edu_resource_items i
JOIN _e_res_def r ON i.upload_request_id = md5('res:' || i.owner_id || ':' || r.no)::uuid
JOIN _e_team t ON t.ag_id = i.age_group_id
JOIN _e_tt tt ON tt.team_id = t.team_id AND tt.sort = r.sort
JOIN LATERAL (SELECT g.goal_id, g.curriculum_goal_id FROM _e_tg g WHERE g.theme_id = tt.theme_id
              ORDER BY g.domain_id DESC, g.goal_no LIMIT 1) g ON true
ON CONFLICT (item_id, kind, ref_id) WHERE attachment_id IS NULL DO NOTHING;

DROP TABLE IF EXISTS _e_ctx, _e_ag, _e_theme_def, _e_branch_def, _e_act_def, _e_act_meta, _e_yp, _e_tick,
    _e_team, _e_tt, _e_tw, _e_tg, _e_tact, _e_cls, _e_cc, _e_wk, _e_cell, _e_root, _e_ev, _e_res_def,
    _e_crow, _e_ccont, _e_wkcal, _e_copy, _e_tstate, _e_hol, _e_doc, _e_corner;
