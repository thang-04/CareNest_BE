-- Bộ dữ liệu dev (profile "dev"): đánh giá định kỳ (nhận xét tháng theo trẻ, nhận xét chung lớp), phiếu bé ngoan,
-- xếp loại lớp, mở lại ngày đánh giá; khám sức khỏe đầu năm, kỳ cân đo đang diễn ra, giải thích xu hướng tăng trưởng.
-- đánh giá kiến thức kỹ năng theo hoạt động và đánh giá cuối chủ đề (theo giáo án đã ban hành của file 04); nhận xét tuần
-- theo trẻ, mở lại nhận xét tuần và đánh giá cuối chủ đề.
-- Dữ liệu giả nhìn như thật. Chạy sau R__dev_04_education.sql. Neo CURRENT_DATE.
--
-- Có mặt theo công thức điểm danh dùng chung: abs(hashtext(mã trẻ || ':' || ngày)) % 100 >= 5 (khớp attendance_records).
-- Cờ bé ngoan và lượt đánh giá ngày đọc từ daily_observations nếu đã có (bảng rỗng ⇒ không phát phiếu bé ngoan).
-- Không seed (theo code): danh sách khen thưởng cuối năm (mở từ ngày kết thúc HK II — OBS-14), đánh giá học kỳ/cuối năm
-- và đánh giá học kỳ của lớp (học kỳ I chưa kết thúc — OBS-06), phiếu theo dõi sức khỏe (đã bỏ — HLT-16).

-- ---------- Hàm phụ trong phiên (xóa cuối file) ----------
CREATE FUNCTION pg_temp.d05_h(k text) RETURNS bigint LANGUAGE sql IMMUTABLE AS
$$ SELECT abs(hashtext(k)::bigint) $$;

CREATE FUNCTION pg_temp.d05_at(d date, t time) RETURNS timestamptz LANGUAGE sql IMMUTABLE AS
$$ SELECT (d + t) AT TIME ZONE 'Asia/Ho_Chi_Minh' $$;

CREATE FUNCTION pg_temp.d05_pick(arr text[], h bigint) RETURNS text LANGUAGE sql IMMUTABLE AS
$$ SELECT arr[1 + (h % array_length(arr, 1))::int] $$;

-- Tháng tuổi tròn như HealthExamReview.ageMonths
CREATE FUNCTION pg_temp.d05_age(dob date, d date) RETURNS int LANGUAGE sql IMMUTABLE AS
$$ SELECT ((extract(year FROM d) - extract(year FROM dob)) * 12 + extract(month FROM d) - extract(month FROM dob)
          - CASE WHEN extract(day FROM d) < extract(day FROM dob) THEN 1 ELSE 0 END)::int $$;

-- Bảng L, M, S của WHO (chép từ src/main/resources/health/*.csv, tháng 20–84): H chiều cao, W cân nặng, B BMI theo tuổi
CREATE TEMP TABLE d05_lms (k char(1), sex char(1), month int, l numeric, m numeric, s numeric);
INSERT INTO d05_lms VALUES
    ('H','M',20,1,84.1996,0.03342), ('H','M',21,1,85.1348,0.03376), ('H','M',22,1,86.0477,0.0341), ('H','M',23,1,86.941,0.03445), ('H','M',24,1,87.1161,0.03507), ('H','M',25,1,87.972,0.03542),
    ('H','M',26,1,88.8065,0.03576), ('H','M',27,1,89.6197,0.0361), ('H','M',28,1,90.412,0.03642), ('H','M',29,1,91.1828,0.03674), ('H','M',30,1,91.9327,0.03704), ('H','M',31,1,92.6631,0.03733),
    ('H','M',32,1,93.3753,0.03761), ('H','M',33,1,94.0711,0.03787), ('H','M',34,1,94.7532,0.03812), ('H','M',35,1,95.4236,0.03836), ('H','M',36,1,96.0835,0.03858), ('H','M',37,1,96.7337,0.03879),
    ('H','M',38,1,97.3749,0.039), ('H','M',39,1,98.0073,0.03919), ('H','M',40,1,98.631,0.03937), ('H','M',41,1,99.2459,0.03954), ('H','M',42,1,99.8515,0.03971), ('H','M',43,1,100.4485,0.03986),
    ('H','M',44,1,101.0374,0.04002), ('H','M',45,1,101.6186,0.04016), ('H','M',46,1,102.1933,0.04031), ('H','M',47,1,102.7625,0.04045), ('H','M',48,1,103.3273,0.04059), ('H','M',49,1,103.8886,0.04073),
    ('H','M',50,1,104.4473,0.04086), ('H','M',51,1,105.0041,0.041), ('H','M',52,1,105.5596,0.04113), ('H','M',53,1,106.1138,0.04126), ('H','M',54,1,106.6668,0.04139), ('H','M',55,1,107.2188,0.04152),
    ('H','M',56,1,107.7697,0.04165), ('H','M',57,1,108.3198,0.04177), ('H','M',58,1,108.8689,0.0419), ('H','M',59,1,109.417,0.04202), ('H','M',60,1,109.9638,0.04214), ('H','M',61,1,110.2647,0.04164),
    ('H','M',62,1,110.8006,0.04172), ('H','M',63,1,111.3338,0.0418), ('H','M',64,1,111.8636,0.04187), ('H','M',65,1,112.3895,0.04195), ('H','M',66,1,112.911,0.04203), ('H','M',67,1,113.428,0.04211),
    ('H','M',68,1,113.941,0.04218), ('H','M',69,1,114.45,0.04226), ('H','M',70,1,114.9547,0.04234), ('H','M',71,1,115.4549,0.04241), ('H','M',72,1,115.9509,0.04249), ('H','M',73,1,116.4432,0.04257),
    ('H','M',74,1,116.9325,0.04264), ('H','M',75,1,117.4196,0.04272), ('H','M',76,1,117.9046,0.0428), ('H','M',77,1,118.388,0.04287), ('H','M',78,1,118.87,0.04295), ('H','M',79,1,119.3508,0.04303),
    ('H','M',80,1,119.8303,0.04311), ('H','M',81,1,120.3085,0.04318), ('H','M',82,1,120.7853,0.04326), ('H','M',83,1,121.2604,0.04334), ('H','M',84,1,121.7338,0.04342), ('H','F',20,1,82.7036,0.03643),
    ('H','F',21,1,83.6654,0.03666), ('H','F',22,1,84.604,0.03688), ('H','F',23,1,85.5202,0.03711), ('H','F',24,1,85.7153,0.03764), ('H','F',25,1,86.5904,0.03786), ('H','F',26,1,87.4462,0.03808),
    ('H','F',27,1,88.283,0.0383), ('H','F',28,1,89.1004,0.03851), ('H','F',29,1,89.8991,0.03872), ('H','F',30,1,90.6797,0.03893), ('H','F',31,1,91.443,0.03913), ('H','F',32,1,92.1906,0.03933),
    ('H','F',33,1,92.9239,0.03952), ('H','F',34,1,93.6444,0.03971), ('H','F',35,1,94.3533,0.03989), ('H','F',36,1,95.0515,0.04006), ('H','F',37,1,95.7399,0.04024), ('H','F',38,1,96.4187,0.04041),
    ('H','F',39,1,97.0885,0.04057), ('H','F',40,1,97.7493,0.04073), ('H','F',41,1,98.4015,0.04089), ('H','F',42,1,99.0448,0.04105), ('H','F',43,1,99.6795,0.0412), ('H','F',44,1,100.3058,0.04135),
    ('H','F',45,1,100.9238,0.0415), ('H','F',46,1,101.5337,0.04164), ('H','F',47,1,102.136,0.04179), ('H','F',48,1,102.7312,0.04193), ('H','F',49,1,103.3197,0.04206), ('H','F',50,1,103.9021,0.0422),
    ('H','F',51,1,104.4786,0.04233), ('H','F',52,1,105.0494,0.04246), ('H','F',53,1,105.6148,0.04259), ('H','F',54,1,106.1748,0.04272), ('H','F',55,1,106.7295,0.04285), ('H','F',56,1,107.2788,0.04298),
    ('H','F',57,1,107.8227,0.0431), ('H','F',58,1,108.3613,0.04322), ('H','F',59,1,108.8948,0.04334), ('H','F',60,1,109.4233,0.04347), ('H','F',61,1,109.6016,0.04355), ('H','F',62,1,110.1258,0.04364),
    ('H','F',63,1,110.6451,0.04373), ('H','F',64,1,111.1596,0.04382), ('H','F',65,1,111.6696,0.0439), ('H','F',66,1,112.1753,0.04399), ('H','F',67,1,112.6767,0.04407), ('H','F',68,1,113.174,0.04415),
    ('H','F',69,1,113.6672,0.04423), ('H','F',70,1,114.1565,0.04431), ('H','F',71,1,114.6421,0.04439), ('H','F',72,1,115.1244,0.04447), ('H','F',73,1,115.6039,0.04454), ('H','F',74,1,116.0812,0.04461),
    ('H','F',75,1,116.5568,0.04469), ('H','F',76,1,117.0311,0.04475), ('H','F',77,1,117.5044,0.04482), ('H','F',78,1,117.9769,0.04489), ('H','F',79,1,118.4489,0.04495), ('H','F',80,1,118.9208,0.04502),
    ('H','F',81,1,119.3926,0.04508), ('H','F',82,1,119.8648,0.04514), ('H','F',83,1,120.3374,0.0452), ('H','F',84,1,120.8105,0.04525),
    ('W','M',20,0.0087,11.3462,0.11211), ('W','M',21,0.0029,11.5486,0.11261), ('W','M',22,-0.0028,11.7504,0.11314), ('W','M',23,-0.0083,11.9514,0.11369), ('W','M',24,-0.0137,12.1515,0.11426), ('W','M',25,-0.0189,12.3502,0.11485),
    ('W','M',26,-0.024,12.5466,0.11544), ('W','M',27,-0.0289,12.7401,0.11604), ('W','M',28,-0.0337,12.9303,0.11664), ('W','M',29,-0.0385,13.1169,0.11723), ('W','M',30,-0.0431,13.3,0.11781), ('W','M',31,-0.0476,13.4798,0.11839),
    ('W','M',32,-0.052,13.6567,0.11896), ('W','M',33,-0.0564,13.8309,0.11953), ('W','M',34,-0.0606,14.0031,0.12008), ('W','M',35,-0.0648,14.1736,0.12062), ('W','M',36,-0.0689,14.3429,0.12116), ('W','M',37,-0.0729,14.5113,0.12168),
    ('W','M',38,-0.0769,14.6791,0.1222), ('W','M',39,-0.0808,14.8466,0.12271), ('W','M',40,-0.0846,15.014,0.12322), ('W','M',41,-0.0883,15.1813,0.12373), ('W','M',42,-0.092,15.3486,0.12425), ('W','M',43,-0.0957,15.5158,0.12478),
    ('W','M',44,-0.0993,15.6828,0.12531), ('W','M',45,-0.1028,15.8497,0.12586), ('W','M',46,-0.1063,16.0163,0.12643), ('W','M',47,-0.1097,16.1827,0.127), ('W','M',48,-0.1131,16.3489,0.12759), ('W','M',49,-0.1165,16.515,0.12819),
    ('W','M',50,-0.1198,16.6811,0.1288), ('W','M',51,-0.123,16.8471,0.12943), ('W','M',52,-0.1262,17.0132,0.13005), ('W','M',53,-0.1294,17.1792,0.13069), ('W','M',54,-0.1325,17.3452,0.13133), ('W','M',55,-0.1356,17.5111,0.13197),
    ('W','M',56,-0.1387,17.6768,0.13261), ('W','M',57,-0.1417,17.8422,0.13325), ('W','M',58,-0.1447,18.0073,0.13389), ('W','M',59,-0.1477,18.1722,0.13453), ('W','M',60,-0.1506,18.3366,0.13517), ('W','M',61,-0.2026,18.5057,0.12988),
    ('W','M',62,-0.213,18.6802,0.13028), ('W','M',63,-0.2234,18.8563,0.13067), ('W','M',64,-0.2338,19.034,0.13105), ('W','M',65,-0.2443,19.2132,0.13142), ('W','M',66,-0.2548,19.394,0.13178), ('W','M',67,-0.2653,19.5765,0.13213),
    ('W','M',68,-0.2758,19.7607,0.13246), ('W','M',69,-0.2864,19.9468,0.13279), ('W','M',70,-0.2969,20.1344,0.13311), ('W','M',71,-0.3075,20.3235,0.13342), ('W','M',72,-0.318,20.5137,0.13372), ('W','M',73,-0.3285,20.7052,0.13402),
    ('W','M',74,-0.339,20.8979,0.13432), ('W','M',75,-0.3494,21.0918,0.13462), ('W','M',76,-0.3598,21.287,0.13493), ('W','M',77,-0.3701,21.4833,0.13523), ('W','M',78,-0.3804,21.681,0.13554), ('W','M',79,-0.3906,21.8799,0.13586),
    ('W','M',80,-0.4007,22.08,0.13618), ('W','M',81,-0.4107,22.2813,0.13652), ('W','M',82,-0.4207,22.4837,0.13686), ('W','M',83,-0.4305,22.6872,0.13722), ('W','M',84,-0.4402,22.8915,0.13759), ('W','F',20,-0.2762,10.6464,0.12323),
    ('W','F',21,-0.2815,10.8534,0.12335), ('W','F',22,-0.2862,11.0608,0.1235), ('W','F',23,-0.2903,11.2688,0.12369), ('W','F',24,-0.2941,11.4775,0.1239), ('W','F',25,-0.2975,11.6864,0.12414), ('W','F',26,-0.3005,11.8947,0.12441),
    ('W','F',27,-0.3032,12.1015,0.12472), ('W','F',28,-0.3057,12.3059,0.12506), ('W','F',29,-0.308,12.5073,0.12545), ('W','F',30,-0.3101,12.7055,0.12587), ('W','F',31,-0.312,12.9006,0.12633), ('W','F',32,-0.3138,13.093,0.12683),
    ('W','F',33,-0.3155,13.2837,0.12737), ('W','F',34,-0.3171,13.4731,0.12794), ('W','F',35,-0.3186,13.6618,0.12855), ('W','F',36,-0.3201,13.8503,0.12919), ('W','F',37,-0.3216,14.0385,0.12988), ('W','F',38,-0.323,14.2265,0.13059),
    ('W','F',39,-0.3243,14.414,0.13135), ('W','F',40,-0.3257,14.601,0.13213), ('W','F',41,-0.327,14.7873,0.13293), ('W','F',42,-0.3283,14.9727,0.13376), ('W','F',43,-0.3296,15.1573,0.1346), ('W','F',44,-0.3309,15.341,0.13545),
    ('W','F',45,-0.3322,15.524,0.1363), ('W','F',46,-0.3335,15.7064,0.13716), ('W','F',47,-0.3348,15.8882,0.138), ('W','F',48,-0.3361,16.0697,0.13884), ('W','F',49,-0.3374,16.2511,0.13968), ('W','F',50,-0.3387,16.4322,0.14051),
    ('W','F',51,-0.34,16.6133,0.14132), ('W','F',52,-0.3414,16.7942,0.14213), ('W','F',53,-0.3427,16.9748,0.14293), ('W','F',54,-0.344,17.1551,0.14371), ('W','F',55,-0.3453,17.3347,0.14448), ('W','F',56,-0.3466,17.5136,0.14525),
    ('W','F',57,-0.3479,17.6916,0.146), ('W','F',58,-0.3492,17.8686,0.14675), ('W','F',59,-0.3505,18.0445,0.14748), ('W','F',60,-0.3518,18.2193,0.14821), ('W','F',61,-0.4681,18.2579,0.14295), ('W','F',62,-0.4711,18.4329,0.1435),
    ('W','F',63,-0.4742,18.6073,0.14404), ('W','F',64,-0.4773,18.7811,0.14459), ('W','F',65,-0.4803,18.9545,0.14514), ('W','F',66,-0.4834,19.1276,0.14569), ('W','F',67,-0.4864,19.3004,0.14624), ('W','F',68,-0.4894,19.473,0.14679),
    ('W','F',69,-0.4924,19.6455,0.14735), ('W','F',70,-0.4954,19.818,0.1479), ('W','F',71,-0.4984,19.9908,0.14845), ('W','F',72,-0.5013,20.1639,0.149), ('W','F',73,-0.5043,20.3377,0.14955), ('W','F',74,-0.5072,20.5124,0.1501),
    ('W','F',75,-0.51,20.6885,0.15065), ('W','F',76,-0.5129,20.8661,0.1512), ('W','F',77,-0.5157,21.0457,0.15175), ('W','F',78,-0.5185,21.2274,0.1523), ('W','F',79,-0.5213,21.4113,0.15284), ('W','F',80,-0.524,21.5979,0.15339),
    ('W','F',81,-0.5268,21.7872,0.15393), ('W','F',82,-0.5294,21.9795,0.15448), ('W','F',83,-0.5321,22.1751,0.15502), ('W','F',84,-0.5347,22.374,0.15556),
    ('B','M',20,-0.5846,15.9743,0.07836), ('B','M',21,-0.6014,15.9039,0.07818), ('B','M',22,-0.6174,15.8412,0.07802), ('B','M',23,-0.6328,15.7852,0.07786), ('B','M',24,-0.6187,16.0189,0.07785), ('B','M',25,-0.584,15.98,0.07792),
    ('B','M',26,-0.5497,15.9414,0.078), ('B','M',27,-0.5166,15.9036,0.07808), ('B','M',28,-0.485,15.8667,0.07818), ('B','M',29,-0.4552,15.8306,0.07829), ('B','M',30,-0.4274,15.7953,0.07841), ('B','M',31,-0.4016,15.7606,0.07854),
    ('B','M',32,-0.3782,15.7267,0.07867), ('B','M',33,-0.3572,15.6934,0.07882), ('B','M',34,-0.3388,15.661,0.07897), ('B','M',35,-0.3231,15.6294,0.07914), ('B','M',36,-0.3101,15.5988,0.07931), ('B','M',37,-0.3,15.5693,0.0795),
    ('B','M',38,-0.2927,15.541,0.07969), ('B','M',39,-0.2884,15.514,0.0799), ('B','M',40,-0.2869,15.4885,0.08012), ('B','M',41,-0.2881,15.4645,0.08036), ('B','M',42,-0.2919,15.442,0.08061), ('B','M',43,-0.2981,15.421,0.08087),
    ('B','M',44,-0.3067,15.4013,0.08115), ('B','M',45,-0.3174,15.3827,0.08144), ('B','M',46,-0.3303,15.3652,0.08174), ('B','M',47,-0.3452,15.3485,0.08205), ('B','M',48,-0.3622,15.3326,0.08238), ('B','M',49,-0.3811,15.3174,0.08272),
    ('B','M',50,-0.4019,15.3029,0.08307), ('B','M',51,-0.4245,15.2891,0.08343), ('B','M',52,-0.4488,15.2759,0.0838), ('B','M',53,-0.4747,15.2633,0.08418), ('B','M',54,-0.5019,15.2514,0.08457), ('B','M',55,-0.5303,15.24,0.08496),
    ('B','M',56,-0.5599,15.2291,0.08536), ('B','M',57,-0.5905,15.2188,0.08577), ('B','M',58,-0.6223,15.2091,0.08617), ('B','M',59,-0.6552,15.2,0.08659), ('B','M',60,-0.6892,15.1916,0.087), ('B','M',61,-0.7387,15.2641,0.0839),
    ('B','M',62,-0.7621,15.2616,0.08414), ('B','M',63,-0.7856,15.2604,0.08439), ('B','M',64,-0.8089,15.2605,0.08464), ('B','M',65,-0.8322,15.2619,0.0849), ('B','M',66,-0.8554,15.2645,0.08516), ('B','M',67,-0.8785,15.2684,0.08543),
    ('B','M',68,-0.9015,15.2737,0.0857), ('B','M',69,-0.9243,15.2801,0.08597), ('B','M',70,-0.9471,15.2877,0.08625), ('B','M',71,-0.9697,15.2965,0.08653), ('B','M',72,-0.9921,15.3062,0.08682), ('B','M',73,-1.0144,15.3169,0.08711),
    ('B','M',74,-1.0365,15.3285,0.08741), ('B','M',75,-1.0584,15.3408,0.08771), ('B','M',76,-1.0801,15.354,0.08802), ('B','M',77,-1.1017,15.3679,0.08833), ('B','M',78,-1.123,15.3825,0.08865), ('B','M',79,-1.1441,15.3978,0.08898),
    ('B','M',80,-1.1649,15.4137,0.08931), ('B','M',81,-1.1856,15.4302,0.08964), ('B','M',82,-1.206,15.4473,0.08998), ('B','M',83,-1.2261,15.465,0.09033), ('B','M',84,-1.246,15.4832,0.09068), ('B','F',20,-0.5372,15.5855,0.08612),
    ('B','F',21,-0.5537,15.5278,0.08594), ('B','F',22,-0.5695,15.4787,0.08577), ('B','F',23,-0.5846,15.438,0.0856), ('B','F',24,-0.5684,15.6881,0.08454), ('B','F',25,-0.5684,15.659,0.08452), ('B','F',26,-0.5684,15.6308,0.08449),
    ('B','F',27,-0.5684,15.6037,0.08446), ('B','F',28,-0.5684,15.5777,0.08444), ('B','F',29,-0.5684,15.5523,0.08443), ('B','F',30,-0.5684,15.5276,0.08444), ('B','F',31,-0.5684,15.5034,0.08448), ('B','F',32,-0.5684,15.4798,0.08455),
    ('B','F',33,-0.5684,15.4572,0.08467), ('B','F',34,-0.5684,15.4356,0.08484), ('B','F',35,-0.5684,15.4155,0.08506), ('B','F',36,-0.5684,15.3968,0.08535), ('B','F',37,-0.5684,15.3796,0.08569), ('B','F',38,-0.5684,15.3638,0.08609),
    ('B','F',39,-0.5684,15.3493,0.08654), ('B','F',40,-0.5684,15.3358,0.08704), ('B','F',41,-0.5684,15.3233,0.08757), ('B','F',42,-0.5684,15.3116,0.08813), ('B','F',43,-0.5684,15.3007,0.08872), ('B','F',44,-0.5684,15.2905,0.08931),
    ('B','F',45,-0.5684,15.2814,0.08991), ('B','F',46,-0.5684,15.2732,0.09051), ('B','F',47,-0.5684,15.2661,0.0911), ('B','F',48,-0.5684,15.2602,0.09168), ('B','F',49,-0.5684,15.2556,0.09227), ('B','F',50,-0.5684,15.2523,0.09286),
    ('B','F',51,-0.5684,15.2503,0.09345), ('B','F',52,-0.5684,15.2496,0.09403), ('B','F',53,-0.5684,15.2502,0.0946), ('B','F',54,-0.5684,15.2519,0.09515), ('B','F',55,-0.5684,15.2544,0.09568), ('B','F',56,-0.5684,15.2575,0.09618),
    ('B','F',57,-0.5684,15.2612,0.09665), ('B','F',58,-0.5684,15.2653,0.09709), ('B','F',59,-0.5684,15.2698,0.0975), ('B','F',60,-0.5684,15.2747,0.09789), ('B','F',61,-0.8886,15.2441,0.09692), ('B','F',62,-0.9068,15.2434,0.09738),
    ('B','F',63,-0.9248,15.2433,0.09783), ('B','F',64,-0.9427,15.2438,0.09829), ('B','F',65,-0.9605,15.2448,0.09875), ('B','F',66,-0.978,15.2464,0.0992), ('B','F',67,-0.9954,15.2487,0.09966), ('B','F',68,-1.0126,15.2516,0.10012),
    ('B','F',69,-1.0296,15.2551,0.10058), ('B','F',70,-1.0464,15.2592,0.10104), ('B','F',71,-1.063,15.2641,0.10149), ('B','F',72,-1.0794,15.2697,0.10195), ('B','F',73,-1.0956,15.276,0.10241), ('B','F',74,-1.1115,15.2831,0.10287),
    ('B','F',75,-1.1272,15.2911,0.10333), ('B','F',76,-1.1427,15.2998,0.10379), ('B','F',77,-1.1579,15.3095,0.10425), ('B','F',78,-1.1728,15.32,0.10471), ('B','F',79,-1.1875,15.3314,0.10517), ('B','F',80,-1.2019,15.3439,0.10562),
    ('B','F',81,-1.216,15.3572,0.10608), ('B','F',82,-1.2298,15.3717,0.10654), ('B','F',83,-1.2433,15.3871,0.107), ('B','F',84,-1.2565,15.4036,0.10746);

-- Giá trị tại z-score theo tháng tuổi lẻ (nội suy tuyến tính giữa hai tháng tròn)
CREATE FUNCTION pg_temp.d05_who(p_k text, p_sex text, p_age numeric, p_z numeric) RETURNS numeric LANGUAGE sql STABLE AS
$$ SELECT a.v + (b.v - a.v) * (p_age - floor(p_age))
   FROM (SELECT m * power(1 + l * s * p_z, 1 / l) AS v FROM d05_lms
         WHERE k = p_k AND sex = p_sex AND month = floor(p_age)::int) a,
        (SELECT m * power(1 + l * s * p_z, 1 / l) AS v FROM d05_lms
         WHERE k = p_k AND sex = p_sex AND month = floor(p_age)::int + 1) b $$;

-- Z-score như WhoLmsTable.zScore: làm tròn 2 chữ số, giới hạn đuôi ngoài ±3SD cho cân nặng và BMI
CREATE FUNCTION pg_temp.d05_z(p_k text, p_sex text, p_month int, p_value numeric, p_restricted boolean)
    RETURNS numeric LANGUAGE sql STABLE AS
$$ SELECT round(CASE WHEN p_restricted AND t.z > 3 THEN 3 + (p_value - t.p3) / (t.p3 - t.p2)
                     WHEN p_restricted AND t.z < -3 THEN -3 + (p_value - t.n3) / (t.n2 - t.n3)
                     ELSE t.z END, 2)
   FROM (SELECT (power(p_value / m, l) - 1) / (l * s) AS z,
                m * power(1 + l * s * 3, 1 / l) AS p3, m * power(1 + l * s * 2, 1 / l) AS p2,
                m * power(1 + l * s * -3, 1 / l) AS n3, m * power(1 + l * s * -2, 1 / l) AS n2
         FROM d05_lms WHERE k = p_k AND sex = p_sex AND month = p_month) t $$;

-- ---------- Nền: năm học, lớp, ngày học, có mặt, giáo viên, cờ ----------
CREATE TEMP TABLE d05_year AS
SELECT id, start_date, end_date, semester1_start, semester1_end FROM school_years WHERE name = '2026-2027';

CREATE TEMP TABLE d05_class AS
SELECT cl.id AS classroom_id, cl.name, cl.campus_id, ca.code AS campus_code, cl.school_year_id,
       coalesce(ag.age_from_months, 36) AS age_from,
       row_number() OVER (PARTITION BY cl.campus_id ORDER BY coalesce(ag.age_from_months, 36), cl.name) AS k
FROM classrooms cl
JOIN d05_year y ON y.id = cl.school_year_id
JOIN campuses ca ON ca.id = cl.campus_id
LEFT JOIN age_groups ag ON ag.id = cl.age_group_id;

CREATE TEMP TABLE d05_days AS
SELECT g::date AS d
FROM d05_year y, generate_series(y.start_date, LEAST(y.end_date, CURRENT_DATE - 1), INTERVAL '1 day') g
WHERE extract(isodow FROM g) < 6
  AND NOT EXISTS (SELECT 1 FROM school_holidays h WHERE g::date BETWEEN h.start_date AND h.end_date);

CREATE TEMP TABLE d05_att AS
SELECT e.child_id, ch.code, e.classroom_id, x.d,
       abs(hashtext(ch.code || ':' || x.d::text)::bigint) % 100 >= 5 AS present
FROM d05_days x
JOIN enrollments e ON e.valid_from <= x.d AND (e.valid_to IS NULL OR e.valid_to >= x.d)
JOIN d05_class c ON c.classroom_id = e.classroom_id
JOIN children ch ON ch.id = e.child_id;

CREATE TEMP TABLE d05_teach AS
SELECT a.classroom_id, a.user_id, a.valid_from, a.valid_to, a.is_homeroom
FROM staff_assignments a
WHERE a.staff_role = 'TEACHER' AND a.classroom_id IS NOT NULL;

-- GV ghi/xác nhận của lớp tại ngày: ưu tiên chủ nhiệm, phân công còn hiệu lực
CREATE FUNCTION pg_temp.d05_teacher(p_class bigint, p_day date) RETURNS bigint LANGUAGE sql STABLE AS
$$ SELECT t.user_id FROM d05_teach t
   WHERE t.classroom_id = p_class AND t.valid_from <= p_day AND (t.valid_to IS NULL OR t.valid_to >= p_day)
   ORDER BY t.is_homeroom DESC, t.user_id LIMIT 1 $$;

CREATE TEMP TABLE d05_vp AS
SELECT DISTINCT ON (a.campus_id) a.campus_id, a.user_id
FROM staff_assignments a
WHERE a.staff_role = 'VICE_PRINCIPAL' AND a.valid_to IS NULL
ORDER BY a.campus_id, a.valid_from;

CREATE TEMP TABLE d05_principal AS
SELECT a.user_id FROM staff_assignments a WHERE a.staff_role = 'PRINCIPAL' AND a.valid_to IS NULL
ORDER BY a.valid_from LIMIT 1;

CREATE TEMP TABLE d05_obs AS
SELECT o.child_id, o.classroom_id, o.date, coalesce(o.flag, FALSE) AS flag FROM daily_observations o;

-- ---------- 1. Nhận xét tháng theo trẻ (OBS-05, OBS-06) ----------
-- Tháng đã kết thúc của năm học. Tháng cũ: GV đã xác nhận hết. Tháng gần nhất: lớp thứ lẻ đã xác nhận gần hết, lớp thứ
-- chẵn còn chờ GV (bản nháp, vài bé GV đã sửa chưa xác nhận) ⇒ có việc cần làm ở màn GV.
CREATE TEMP TABLE d05_month AS
SELECT m::date AS month_start, GREATEST(m::date, y.start_date) AS period_start,
       LEAST((m + INTERVAL '1 month - 1 day')::date, y.end_date) AS period_end
FROM d05_year y, generate_series(date_trunc('month', y.start_date), date_trunc('month', y.end_date), INTERVAL '1 month') m
WHERE LEAST((m + INTERVAL '1 month - 1 day')::date, y.end_date) < CURRENT_DATE;

ALTER TABLE d05_month ADD COLUMN last_month boolean, ADD COLUMN open_days int;
UPDATE d05_month SET last_month = month_start = (SELECT max(month_start) FROM d05_month),
                     open_days = CURRENT_DATE - period_end - 1;

CREATE TEMP TABLE d05_eval_src AS
SELECT a.child_id, a.code, ch.full_name, a.classroom_id, c.campus_id, c.school_year_id, c.age_from, c.k,
       m.month_start, m.period_start, m.period_end, m.last_month, m.open_days,
       count(*) FILTER (WHERE a.present) AS present_days,
       count(o.child_id) AS observed_days,
       count(o.child_id) FILTER (WHERE o.flag) AS flags
FROM d05_att a
JOIN d05_month m ON a.d BETWEEN m.period_start AND m.period_end
JOIN d05_class c ON c.classroom_id = a.classroom_id
JOIN children ch ON ch.id = a.child_id
LEFT JOIN d05_obs o ON a.present AND o.child_id = a.child_id AND o.date = a.d AND o.classroom_id = a.classroom_id
GROUP BY a.child_id, a.code, ch.full_name, a.classroom_id, c.campus_id, c.school_year_id, c.age_from, c.k,
         m.month_start, m.period_start, m.period_end, m.last_month, m.open_days
HAVING count(*) FILTER (WHERE a.present) > 0;

CREATE TEMP TABLE d05_eval AS
SELECT s.*, regexp_replace(s.full_name, '^.* ', '') AS given,
       pg_temp.d05_h(s.code || ':nx:' || s.month_start) AS h,
       (NOT s.last_month OR (s.open_days >= 3 AND s.k % 2 = 1
                         AND pg_temp.d05_h(s.code || ':nx:' || s.month_start) % 7 <> 0)) AS confirm_wanted
FROM d05_eval_src s;

ALTER TABLE d05_eval ADD COLUMN act_day date, ADD COLUMN actor bigint, ADD COLUMN confirmed boolean,
    ADD COLUMN edited boolean;
-- Ngày GV sửa/xác nhận: tháng cũ 2–6 ngày sau cuối tháng; tháng gần nhất trong khoảng từ cuối tháng tới hôm qua
UPDATE d05_eval SET act_day = CASE WHEN NOT last_month THEN period_end + 2 + (h % 5)::int
                                   WHEN open_days >= 1 THEN period_end + 1 + (h % open_days)::int END;
UPDATE d05_eval SET actor = pg_temp.d05_teacher(classroom_id, act_day) WHERE act_day IS NOT NULL;
UPDATE d05_eval SET confirmed = confirm_wanted AND actor IS NOT NULL,
                    edited = actor IS NOT NULL AND (h / 7) % 10 < CASE WHEN confirm_wanted THEN 6 ELSE 3 END;

ALTER TABLE d05_eval ADD COLUMN draft_text text, ADD COLUMN final_text text;
UPDATE d05_eval SET draft_text =
    'Trong tháng ' || to_char(month_start, 'MM/YYYY') || ', bé ' || given || ' đi học ' || present_days
        || ' ngày, cô đánh giá hằng ngày ' || observed_days || ' ngày và bé nhận ' || flags || ' cờ bé ngoan.'
        || CASE WHEN observed_days = 0
                    THEN E'\nKỳ này chưa có đánh giá hằng ngày nào được ghi nhận, cô bổ sung nhận xét về bé.'
                ELSE E'\nCảm xúc: phần lớn thời gian bé vui vẻ.'
                    || E'\nGợi ý phối hợp với gia đình: khen ngợi khi bé cố gắng.' END;

UPDATE d05_eval SET final_text =
    'Tháng ' || to_char(month_start, 'MM/YYYY') || ': '
    || replace(pg_temp.d05_pick(ARRAY[
           '{ten} đi học đều, sáng đến lớp vui vẻ, biết chào cô, chào bạn.',
           'Tháng này {ten} đã quen nề nếp lớp, tự cất ba lô, dép vào đúng chỗ.',
           '{ten} đến lớp đúng giờ, chủ động chào cô và nhanh chóng vào hoạt động cùng các bạn.',
           '{ten} vào lớp không còn quấy, biết vẫy tay chào bố mẹ rồi tự vào chơi.',
           '{ten} ngoan, lễ phép, biết dạ thưa khi cô hỏi.',
           '{ten} đi học chuyên cần, giữ được nề nếp xếp hàng, rửa tay trước khi ăn.'], h), '{ten}', given)
    || ' ' || pg_temp.d05_pick(ARRAY[
           'Bé ăn hết suất, biết tự xúc cơm gọn gàng, ngủ trưa ngon giấc.',
           'Bé ăn được nhiều rau hơn tháng trước, giấc ngủ trưa sâu và đủ giờ.',
           'Bé ăn ngoan nhưng còn chậm, cô động viên để bé ăn hết suất.',
           'Bé ngủ trưa tốt, biết tự lấy gối, cất gối sau khi ngủ dậy.',
           'Bé tự xúc ăn, biết mời cô, mời bạn trước khi ăn.',
           'Bé còn kén một số món rau, cô đang khuyến khích bé ăn đa dạng hơn.'], h / 11)
    || ' ' || CASE
           WHEN age_from < 36 THEN pg_temp.d05_pick(ARRAY[
               'Bé thích nghe cô đọc thơ, kể chuyện, đã nói được câu ngắn để bày tỏ mong muốn.',
               'Bé nhận biết được màu đỏ, màu xanh và gọi đúng tên một số con vật quen thuộc.',
               'Bé thích chơi xếp khối, xâu vòng và tham gia vận động theo nhạc cùng cô.',
               'Bé biết chỉ và gọi tên các bộ phận trên cơ thể, hát theo cô được vài câu.'], h / 13)
           WHEN age_from < 48 THEN pg_temp.d05_pick(ARRAY[
               'Bé hứng thú với giờ hoạt động âm nhạc, thuộc bài hát về chủ đề trường mầm non.',
               'Bé nhận biết hình tròn, hình vuông, đếm được trong phạm vi 3.',
               'Bé biết cầm bút tô màu, tham gia chơi ở góc phân vai cùng các bạn.',
               'Bé đọc thuộc bài thơ ngắn, gọi được tên các bạn trong lớp.'], h / 13)
           WHEN age_from < 60 THEN pg_temp.d05_pick(ARRAY[
               'Bé tích cực phát biểu, đếm và so sánh được số lượng trong phạm vi 5.',
               'Bé vẽ, nặn sáng tạo, biết kể lại nội dung câu chuyện cô kể.',
               'Bé tham gia tốt các trò chơi vận động, biết chơi cùng nhóm bạn.',
               'Bé nhận biết và phân biệt được một số phương tiện giao thông quen thuộc.'], h / 13)
           ELSE pg_temp.d05_pick(ARRAY[
               'Bé nhận biết và phát âm đúng các chữ cái o, ô, ơ, a, ă, â, tô chữ khá đẹp.',
               'Bé đếm, thêm bớt được trong phạm vi 7, mạnh dạn trả lời câu hỏi của cô.',
               'Bé tự tin kể chuyện theo tranh, biết giúp cô chuẩn bị đồ dùng học tập.',
               'Bé tham gia tích cực các hoạt động, ngồi đúng tư thế và cầm bút đúng cách.'], h / 13) END
    || coalesce(' ' || nullif(pg_temp.d05_pick(ARRAY[
           'Bé cần mạnh dạn hơn khi trả lời câu hỏi trước lớp.',
           'Bé cần tập trung hơn trong giờ học, tránh nói chuyện riêng.',
           'Bé cần nhường nhịn bạn hơn khi chơi đồ chơi chung.',
           'Bé cần tự giác cất đồ chơi sau khi chơi.',
           'Bé còn hay quên rửa tay sau khi đi vệ sinh, cô tiếp tục nhắc nhở.',
           ''], h / 17), ''), '')
    || ' ' || pg_temp.d05_pick(ARRAY[
           'Gia đình cho bé đi ngủ sớm để sáng dậy đúng giờ đến lớp.',
           'Gia đình trò chuyện, hỏi bé về các hoạt động ở lớp mỗi ngày.',
           'Gia đình khuyến khích bé tự xúc ăn, tự mặc quần áo ở nhà.',
           'Gia đình cùng bé đọc sách tranh, kể chuyện trước giờ ngủ.',
           'Gia đình tiếp tục phối hợp với cô để bé ăn thêm rau, trái cây.'], h / 19)
WHERE edited OR confirmed;

CREATE TEMP TABLE d05_eval_new (id bigint, child_id bigint, month_start date);

WITH ins AS (
    INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id, period_type, period_start,
                                   period_end, status, draft_content, draft_at, final_content, confirmed_by,
                                   confirmed_at, created_at, updated_at)
    SELECT e.child_id, e.classroom_id, e.campus_id, e.school_year_id, 'MONTH', e.period_start, e.period_end,
           CASE WHEN e.confirmed THEN 'CONFIRMED' ELSE 'AWAITING_REVIEW' END,
           e.draft_text, LEAST(pg_temp.d05_at(e.period_end + 1, TIME '06:30'), now()),
           CASE WHEN e.confirmed OR e.edited THEN e.final_text END,
           CASE WHEN e.confirmed THEN e.actor END,
           CASE WHEN e.confirmed THEN pg_temp.d05_at(e.act_day, TIME '13:00') + (e.h % 75) * INTERVAL '1 minute' END,
           LEAST(pg_temp.d05_at(e.period_end + 1, TIME '06:30'), now()),
           CASE WHEN e.confirmed OR e.edited
                    THEN pg_temp.d05_at(e.act_day, TIME '13:00') + (e.h % 75) * INTERVAL '1 minute'
                ELSE LEAST(pg_temp.d05_at(e.period_end + 1, TIME '06:30'), now()) END
    FROM d05_eval e
    ON CONFLICT ON CONSTRAINT uk_child_evaluations_child_period DO NOTHING
    RETURNING id, child_id, period_start
)
INSERT INTO d05_eval_new SELECT i.id, i.child_id, date_trunc('month', i.period_start)::date FROM ins i;

-- Lịch sử: hệ thống tạo nháp, GV sửa (nếu có), GV xác nhận — chỉ cho bản vừa tạo
INSERT INTO child_evaluation_events (evaluation_id, action, user_id, at)
SELECT n.id, v.action, v.user_id, v.at
FROM d05_eval_new n
JOIN d05_eval e ON e.child_id = n.child_id AND e.month_start = n.month_start
JOIN child_evaluations ev ON ev.id = n.id
CROSS JOIN LATERAL (
    VALUES ('AI_DRAFTED', NULL::bigint, ev.draft_at, TRUE),
           ('EDITED', e.actor, ev.updated_at - INTERVAL '12 minutes', e.edited),
           ('CONFIRMED', e.actor, ev.confirmed_at, e.confirmed)
) v (action, user_id, at, wanted)
WHERE v.wanted;

-- ---------- 2. Nhận xét chung cả lớp theo tháng (OBS-26) ----------
CREATE TEMP TABLE d05_cmc AS
SELECT c.classroom_id, c.name, c.campus_id, c.school_year_id, c.k, m.month_start, m.period_end, m.last_month, m.open_days,
       count(DISTINCT a.child_id) AS children, count(DISTINCT a.d) AS days,
       round(100.0 * count(*) FILTER (WHERE a.present) / count(*))::int AS rate,
       (SELECT count(*) FROM d05_obs o WHERE o.classroom_id = c.classroom_id
           AND o.date BETWEEN m.period_start AND m.period_end) AS assessed,
       (SELECT count(*) FROM d05_obs o WHERE o.classroom_id = c.classroom_id AND o.flag
           AND o.date BETWEEN m.period_start AND m.period_end) AS flags,
       pg_temp.d05_h(c.name || ':nxl:' || m.month_start) AS h,
       m.month_start = (SELECT min(month_start) FROM d05_month) AS first_month
FROM d05_class c
JOIN d05_month m ON TRUE
JOIN d05_att a ON a.classroom_id = c.classroom_id AND a.d BETWEEN m.period_start AND m.period_end
GROUP BY c.classroom_id, c.name, c.campus_id, c.school_year_id, c.k, m.month_start, m.period_start, m.period_end,
         m.last_month, m.open_days;

ALTER TABLE d05_cmc ADD COLUMN act_day date, ADD COLUMN actor bigint, ADD COLUMN state text;
UPDATE d05_cmc SET act_day = CASE WHEN NOT last_month THEN period_end + 3 + (h % 4)::int
                                  WHEN open_days >= 1 THEN period_end + 1 + (h % open_days)::int END;
UPDATE d05_cmc SET actor = pg_temp.d05_teacher(classroom_id, act_day) WHERE act_day IS NOT NULL;
-- Tháng gần nhất: lớp thứ lẻ đã xác nhận, lớp thứ 2 đã lưu chưa xác nhận, các lớp chẵn khác còn bản nháp hệ thống
UPDATE d05_cmc SET state = CASE WHEN actor IS NULL THEN 'DRAFT'
                                WHEN NOT last_month OR (open_days >= 3 AND k % 2 = 1) THEN 'CONFIRMED'
                                WHEN k = 2 THEN 'SAVED'
                                ELSE 'DRAFT' END;

INSERT INTO class_monthly_comments (classroom_id, campus_id, school_year_id, month, draft_content, content,
                                    confirmed_by, confirmed_at, updated_by, updated_at)
SELECT x.classroom_id, x.campus_id, x.school_year_id, x.month_start,
       'Tháng ' || to_char(x.month_start, 'MM/YYYY') || ', ' || x.name || ' có ' || x.children || ' trẻ, học '
           || x.days || ' ngày, tỷ lệ chuyên cần ' || x.rate || E'%.\nCô đánh giá hằng ngày ' || x.assessed
           || ' lượt, các bé nhận ' || x.flags || E' cờ bé ngoan.\nCô bổ sung điểm nổi bật, việc cần cố gắng của lớp tháng tới.',
       CASE WHEN x.state <> 'DRAFT' THEN
           'Tháng ' || to_char(x.month_start, 'MM/YYYY') || ', lớp có ' || x.children || ' trẻ, tỷ lệ chuyên cần đạt '
           || x.rate || '%. '
           || CASE WHEN x.first_month
                       THEN 'Đây là tháng đầu năm học, các bé đã dần quen nề nếp lớp, giờ đón trẻ chỉ còn vài bé khóc. '
                   ELSE pg_temp.d05_pick(ARRAY[
                       'Các bé ăn ngoan hơn, nhiều bé đã tự xúc ăn và ăn hết suất. ',
                       'Lớp tham gia tích cực các hoạt động ngoài trời và trò chơi dân gian của trường. ',
                       'Các bé mạnh dạn hơn trong giao tiếp, thích kể chuyện và đọc thơ cùng cô. '], x.h) END
           || pg_temp.d05_pick(ARRAY[
               'Một số bé còn chưa tập trung trong giờ hoạt động học, hay nói chuyện riêng. ',
               'Vẫn còn vài bé đến lớp muộn sau giờ đón trẻ, cô đã trao đổi với phụ huynh. ',
               'Nề nếp vệ sinh cá nhân sau giờ ăn của một số bé cần nhắc nhở thêm. '], x.h / 7)
           || pg_temp.d05_pick(ARRAY[
               'Tháng tới lớp tiếp tục rèn kỹ năng tự phục vụ và nề nếp giờ ăn, giờ ngủ.',
               'Tháng tới cô tăng cường hoạt động góc và trò chơi dân gian để các bé giao lưu nhiều hơn.',
               'Tháng tới lớp phối hợp với phụ huynh để các bé đến lớp đúng giờ.'], x.h / 11) END,
       CASE WHEN x.state = 'CONFIRMED' THEN x.actor END,
       CASE WHEN x.state = 'CONFIRMED' THEN pg_temp.d05_at(x.act_day, TIME '15:30') + (x.h % 60) * INTERVAL '1 minute' END,
       CASE WHEN x.state <> 'DRAFT' THEN x.actor END,
       CASE WHEN x.state <> 'DRAFT'
                THEN pg_temp.d05_at(x.act_day, TIME '15:30') + (x.h % 60) * INTERVAL '1 minute'
            ELSE LEAST(pg_temp.d05_at(x.period_end + 1, TIME '06:30'), now()) END
FROM d05_cmc x
ON CONFLICT ON CONSTRAINT uk_class_monthly_comments DO NOTHING;

-- ---------- 3. Phiếu bé ngoan tuần / tháng (OBS-10) ----------
-- Tuần: ≥3 cờ trong các ngày có mặt; GV phát thứ Hai tuần sau (tuần vừa kết thúc chưa phát ⇒ việc cần làm). Tháng: ≥3
-- phiếu tuần có thứ Hai trong tháng. Lớp của phiếu = lớp trẻ học ngày cuối kỳ.
CREATE TEMP TABLE d05_week AS
SELECT w::date AS monday, w::date + 4 AS friday
FROM d05_year y, generate_series(date_trunc('week', y.start_date), y.end_date, INTERVAL '1 week') w
WHERE w::date + 4 < CURRENT_DATE;

CREATE TEMP TABLE d05_week_stat AS
SELECT w.monday, w.friday, a.child_id,
       count(DISTINCT a.d) FILTER (WHERE a.present) AS present_days,
       count(DISTINCT o.date) FILTER (WHERE a.present AND o.flag) AS flags
FROM d05_week w
JOIN d05_att a ON a.d BETWEEN w.monday AND w.friday
LEFT JOIN d05_obs o ON o.child_id = a.child_id AND o.date = a.d
GROUP BY w.monday, w.friday, a.child_id;

INSERT INTO behaviour_tickets (child_id, classroom_id, campus_id, school_year_id, type, period_start, period_end,
                               present_days, flag_count, weekly_tickets, issued_by, issued_at)
SELECT s.child_id, c.classroom_id, c.campus_id, c.school_year_id, 'WEEKLY', s.monday, s.friday, s.present_days,
       s.flags, NULL, t.user_id,
       pg_temp.d05_at(s.monday + 7, TIME '15:30') + (pg_temp.d05_h(s.child_id || ':bn:' || s.monday) % 40) * INTERVAL '1 minute'
FROM d05_week_stat s
JOIN d05_year y ON TRUE
JOIN enrollments e ON e.child_id = s.child_id
    AND e.valid_from <= LEAST(GREATEST(s.friday, y.start_date), y.end_date)
    AND (e.valid_to IS NULL OR e.valid_to >= LEAST(GREATEST(s.friday, y.start_date), y.end_date))
JOIN d05_class c ON c.classroom_id = e.classroom_id
CROSS JOIN LATERAL (SELECT pg_temp.d05_teacher(c.classroom_id, s.monday + 7) AS user_id) t
WHERE s.flags >= 3 AND s.monday + 7 < CURRENT_DATE AND t.user_id IS NOT NULL
ON CONFLICT ON CONSTRAINT uk_behaviour_tickets_child_type_period DO NOTHING;

INSERT INTO behaviour_tickets (child_id, classroom_id, campus_id, school_year_id, type, period_start, period_end,
                               present_days, flag_count, weekly_tickets, issued_by, issued_at)
SELECT w.child_id, c.classroom_id, c.campus_id, c.school_year_id, 'MONTHLY', m.month_start,
       (m.month_start + INTERVAL '1 month - 1 day')::date, NULL, NULL, w.n, t.user_id, w.at
FROM d05_month m
CROSS JOIN LATERAL (
    SELECT b.child_id, count(*)::int AS n,
           GREATEST(pg_temp.d05_at(m.period_end + 5, TIME '16:00'), max(b.issued_at) + INTERVAL '10 minutes') AS at
    FROM behaviour_tickets b
    WHERE b.type = 'WEEKLY' AND date_trunc('month', b.period_start) = m.month_start
    GROUP BY b.child_id
    HAVING count(*) >= 3
) w
JOIN enrollments e ON e.child_id = w.child_id AND e.valid_from <= m.period_end
    AND (e.valid_to IS NULL OR e.valid_to >= m.period_end)
JOIN d05_class c ON c.classroom_id = e.classroom_id
CROSS JOIN LATERAL (SELECT pg_temp.d05_teacher(c.classroom_id, (w.at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date) AS user_id) t
WHERE w.at < pg_temp.d05_at(CURRENT_DATE, TIME '00:00') AND t.user_id IS NOT NULL
ON CONFLICT ON CONSTRAINT uk_behaviour_tickets_child_type_period DO NOTHING;

-- ---------- 3b. Đánh giá kiến thức kỹ năng theo hoạt động (OBS-15, EDU-19) ----------
-- Giáo án ngày như EduLessonQueryServiceImpl.findApprovedDayLessons: tuần có kế hoạch tuần và giáo án ngày đã ban hành;
-- mỗi dòng của cấu hình tuần (edu_settings.week_rows) có MT từ lượt xếp (ô của ngày + phần chung, hoặc ô cả tuần) là một
-- hoạt động; MT của lượt xếp = MT của thẻ trong hồ sơ chủ đề, không thấy thẻ thì dùng goalIds chụp ở lượt xếp.
-- Chỉ trẻ có mặt (attendance_records PRESENT). GV chấm Đạt / Chưa đạt từng MT; vài ngày lớp quên chấm. Hôm nay (nếu là
-- ngày học) Cơ sở 1 mới chấm Hoạt động học, Cơ sở 2 chưa chấm.
CREATE TEMP TABLE d05_rows AS
SELECT r.v ->> 'key' AS row_key, coalesce((r.v ->> 'canMerge')::boolean, FALSE) AS can_merge,
       coalesce((r.v ->> 'allWeek')::boolean, FALSE) AS all_week
FROM (SELECT week_rows::jsonb AS rows FROM edu_settings ORDER BY id LIMIT 1) s,
     jsonb_array_elements(s.rows) r (v);

CREATE TEMP TABLE d05_lesson_week AS
SELECT DISTINCT ON (w.classroom_id, w.week_start) w.id AS doc_id, w.classroom_id, w.team_plan_id, w.theme_id,
       w.week_start,
       CASE WHEN jsonb_typeof(dd.content -> 'week') = 'object' THEN dd.content -> 'week' ELSE w.content END AS content,
       CASE WHEN jsonb_typeof(dd.content -> 'week') = 'object' THEN dd.content -> 'week' -> 'cards' END AS day_cards
FROM edu_class_docs w
JOIN d05_class c ON c.classroom_id = w.classroom_id
JOIN LATERAL (
    SELECT x.content FROM edu_class_docs x
    WHERE x.kind = 'day' AND x.classroom_id = w.classroom_id AND x.team_plan_id = w.team_plan_id
      AND x.week_start = w.week_start AND (x.status = 'APPROVED' OR x.history::text LIKE '%"revise_future"%')
    ORDER BY x.id LIMIT 1
) dd ON TRUE
WHERE w.kind = 'week' AND (w.status = 'APPROVED' OR w.history::text LIKE '%"revise_future"%')
  AND w.week_start <= CURRENT_DATE
ORDER BY w.classroom_id, w.week_start, w.id DESC;

CREATE TEMP TABLE d05_activity AS
SELECT lw.doc_id, lw.classroom_id, (lw.week_start + i)::date AS d, r.row_key,
       array_agg(DISTINCT eg.id ORDER BY eg.id) AS goals
FROM d05_lesson_week lw
CROSS JOIN generate_series(0, 4) i
CROSS JOIN d05_rows r
CROSS JOIN LATERAL (
    SELECT r.can_merge AND coalesce((lw.content -> 'grid' -> r.row_key ->> 'allWeek')::boolean, r.all_week) AS merged
) mg
CROSS JOIN LATERAL unnest(CASE WHEN mg.merged THEN ARRAY[r.row_key || '.all']
                               ELSE ARRAY[r.row_key || '.' || i, r.row_key || '.extra'] END) k (key)
CROSS JOIN LATERAL jsonb_array_elements(coalesce(lw.content -> 'picks' -> k.key, '[]'::jsonb)) p (pick)
LEFT JOIN LATERAL (
    SELECT card FROM (
        SELECT a.card, 1 AS src FROM edu_class_docs t, jsonb_array_elements(t.content -> 'activities') a (card)
        WHERE t.id = (SELECT t2.id FROM edu_class_docs t2
                      WHERE t2.kind = 'theme' AND t2.classroom_id = lw.classroom_id
                        AND t2.team_plan_id = lw.team_plan_id AND t2.theme_id = lw.theme_id
                      ORDER BY t2.id LIMIT 1)
          AND jsonb_typeof(t.content -> 'activities') = 'array'
        UNION ALL
        SELECT a.card, 2 FROM jsonb_array_elements(coalesce(lw.day_cards, '[]'::jsonb)) a (card)
    ) cs
    WHERE cs.card ->> 'id' = coalesce(nullif(p.pick ->> 'cardId', ''), p.pick ->> 'id')
    ORDER BY cs.src LIMIT 1
) cd ON TRUE
CROSS JOIN LATERAL jsonb_array_elements_text(
    CASE WHEN cd.card IS NOT NULL THEN coalesce(cd.card -> 'goalIds', '[]'::jsonb)
         ELSE coalesce(p.pick -> 'goalIds', '[]'::jsonb) END) gx (gid)
JOIN edu_plan_goals eg ON gx.gid ~ '^\d+$' AND eg.id = gx.gid::bigint
WHERE lw.week_start + i <= CURRENT_DATE
GROUP BY lw.doc_id, lw.classroom_id, lw.week_start + i, r.row_key;

CREATE TEMP TABLE d05_dar AS
SELECT a.child_id, a.classroom_id, c.campus_id, a.date AS d, act.row_key, act.doc_id, act.goals,
       pg_temp.d05_teacher(a.classroom_id, a.date) AS teacher_id,
       pg_temp.d05_h(ch.code || ':ktkn:' || a.date || ':' || act.row_key) AS h,
       10 + CASE WHEN pg_temp.d05_h(ch.code || ':hocluc') % 100 < 10 THEN 25 ELSE 0 END
          + CASE WHEN c.age_from < 36 THEN 5 ELSE 0 END AS miss_pct
FROM attendance_records a
JOIN d05_class c ON c.classroom_id = a.classroom_id
JOIN children ch ON ch.id = a.child_id
JOIN d05_activity act ON act.classroom_id = a.classroom_id AND act.d = a.date
WHERE a.status = 'PRESENT' AND a.date <= CURRENT_DATE
  AND pg_temp.d05_h(c.name || ':quencham:' || a.date) % 100 >= 4
  AND (a.date < CURRENT_DATE OR (c.campus_code = 'CS-A' AND act.row_key = 'hoc'));

INSERT INTO daily_activity_results (child_id, classroom_id, campus_id, date, activity_key, class_doc_id, participation,
                                    goals_met, goals_effort, goals_not_met, recorded_by, recorded_at, updated_by,
                                    updated_at)
SELECT x.child_id, x.classroom_id, x.campus_id, x.d, x.row_key, x.doc_id,
       CASE WHEN g.not_met <> '' AND x.h % 100 < 30 THEN 'CHUA_TICH_CUC'
            WHEN g.not_met <> '' THEN 'BINH_THUONG'
            WHEN x.h % 100 < 60 THEN 'TICH_CUC' ELSE 'BINH_THUONG' END,
       g.met, '', g.not_met, x.teacher_id, ts.at, x.teacher_id, ts.at
FROM d05_dar x
CROSS JOIN LATERAL (
    SELECT coalesce(string_agg(v::text, ',' ORDER BY v) FILTER (WHERE NOT miss), '') AS met,
           coalesce(string_agg(v::text, ',' ORDER BY v) FILTER (WHERE miss), '') AS not_met
    FROM (SELECT v, pg_temp.d05_h(x.child_id || ':' || x.d || ':' || v) % 100 < x.miss_pct AS miss
          FROM unnest(x.goals) v) gg
) g
CROSS JOIN LATERAL (
    SELECT LEAST(pg_temp.d05_at(x.d, CASE WHEN x.row_key = 'hoc' THEN TIME '10:05' ELSE TIME '15:40' END)
                     + (x.h % 35) * INTERVAL '1 minute',
                 now() - INTERVAL '5 minutes') AS at
) ts
WHERE x.teacher_id IS NOT NULL
ON CONFLICT ON CONSTRAINT uk_daily_activity_results DO NOTHING;

-- ---------- 3c. Đánh giá cuối chủ đề (OBS-27) ----------
-- Chủ đề của kế hoạch năm nhóm tuổi đã duyệt bản mới nhất (lớp chưa có lịch nhánh duyệt). Chủ đề đã kết thúc: GV chủ nhiệm
-- xác nhận thứ Hai sau ngày kết thúc. Bảng trẻ × MT tính như autoResults: MT Đạt khi có ít nhất một hoạt động trong thời
-- gian chủ đề trẻ có mặt được chấm Đạt (nối MT qua dòng gốc), còn lại Chưa đạt. Lưu cả bảng như lúc xác nhận; bản chụp
-- trình bày để trống (service dựng lại từ cells).
CREATE TEMP TABLE d05_theme AS
SELECT c.classroom_id, c.campus_id, c.school_year_id, t.id AS theme_id, coalesce(t.lineage_id, t.id) AS lineage,
       t.name, t.start_date, t.end_date, (t.end_date + 3) AS act_day,
       pg_temp.d05_teacher(c.classroom_id, t.end_date + 3) AS teacher_id,
       pg_temp.d05_h(c.name || ':cd:' || t.id) AS h
FROM d05_class c
JOIN classrooms cl ON cl.id = c.classroom_id
JOIN LATERAL (
    SELECT p.id FROM edu_team_plans p
    WHERE p.school_year_id = c.school_year_id AND p.age_group_id = cl.age_group_id AND p.status = 'APPROVED'
    ORDER BY p.version_no DESC, p.id DESC LIMIT 1
) tp ON TRUE
JOIN edu_themes t ON t.team_plan_id = tp.id AND t.start_date IS NOT NULL AND t.end_date IS NOT NULL
WHERE t.end_date + 3 < CURRENT_DATE
  AND NOT EXISTS (SELECT 1 FROM edu_class_docs b WHERE b.kind = 'branch' AND b.classroom_id = c.classroom_id
                      AND b.team_plan_id = tp.id AND b.status = 'APPROVED');

CREATE TEMP TABLE d05_theme_cell AS
SELECT th.classroom_id, th.theme_id, kid.child_id, gl.lineage,
       EXISTS (
           SELECT 1 FROM daily_activity_results r
           JOIN attendance_records a ON a.child_id = r.child_id AND a.date = r.date AND a.status = 'PRESENT'
           CROSS JOIN LATERAL unnest(string_to_array(nullif(r.goals_met, ''), ',')) m (gid)
           JOIN edu_plan_goals g ON g.id = m.gid::bigint
           WHERE r.child_id = kid.child_id AND r.classroom_id = th.classroom_id
             AND r.date BETWEEN th.start_date AND th.end_date AND coalesce(g.lineage_id, g.id) = gl.lineage
       ) AS met
FROM d05_theme th
CROSS JOIN LATERAL (
    SELECT DISTINCT e.child_id FROM enrollments e
    WHERE e.classroom_id = th.classroom_id AND e.valid_from <= th.end_date
      AND (e.valid_to IS NULL OR e.valid_to >= th.start_date)
) kid
CROSS JOIN LATERAL (
    SELECT DISTINCT coalesce(g.lineage_id, g.id) AS lineage FROM edu_plan_goals g
    WHERE EXISTS (SELECT 1 FROM edu_goal_contents gc JOIN edu_content_theme_ticks k ON k.content_id = gc.id
                  WHERE gc.goal_id = g.id AND k.theme_id = th.theme_id)
) gl;

INSERT INTO theme_evaluations (classroom_id, campus_id, school_year_id, theme_id, theme_name, start_date, end_date, cells,
                               note, confirmed_by, confirmed_at, updated_by, updated_at)
SELECT th.classroom_id, th.campus_id, th.school_year_id, th.lineage, th.name, th.start_date, th.end_date,
       coalesce((SELECT jsonb_agg(jsonb_build_object('c', x.child_id, 'g', x.lineage, 'm', x.met, 'x', FALSE)
                                  ORDER BY x.child_id, x.lineage)
                 FROM d05_theme_cell x WHERE x.classroom_id = th.classroom_id AND x.theme_id = th.theme_id),
                '[]'::jsonb)::text,
       pg_temp.d05_pick(ARRAY[
           'Đa số trẻ đạt mục tiêu của chủ đề, hứng thú tham gia các hoạt động.',
           'Trẻ nắm được nội dung chủ đề; một số trẻ cần ôn thêm ở hoạt động học.',
           'Trẻ tích cực tham gia hoạt động; vài trẻ nghỉ nhiều ngày nên chưa đạt một số mục tiêu.',
           'Lớp hoàn thành tốt chủ đề, cô tiếp tục củng cố kỹ năng ở chủ đề sau.'], th.h),
       th.teacher_id, pg_temp.d05_at(th.act_day, TIME '15:00') + (th.h % 50) * INTERVAL '1 minute',
       th.teacher_id, pg_temp.d05_at(th.act_day, TIME '15:00') + (th.h % 50) * INTERVAL '1 minute'
FROM d05_theme th
WHERE th.teacher_id IS NOT NULL
ON CONFLICT ON CONSTRAINT uk_theme_evaluations_class_theme DO NOTHING;

-- ---------- 4. Xếp loại lớp (OBS-24) ----------
-- Học kỳ I mở chấm từ ngày bắt đầu học kỳ. Đã qua học kỳ I: mọi lớp được PHT điểm trường chấm. Đang giữa học kỳ (từ 60
-- ngày sau khi bắt đầu): PHT Cơ sở 2 chấm sơ bộ 3 lớp, Cơ sở 1 chưa chấm.
CREATE TEMP TABLE d05_rating AS
SELECT c.classroom_id, c.campus_id, c.school_year_id, v.user_id,
       CASE WHEN CURRENT_DATE > y.semester1_end + 3 THEN pg_temp.d05_at(y.semester1_end + 3, TIME '10:00')
            ELSE pg_temp.d05_at(CURRENT_DATE - 2, TIME '10:00') END
           + (c.k * 7) * INTERVAL '1 minute' AS rated_at,
       CURRENT_DATE <= y.semester1_end + 3 AS interim,
       ARRAY(SELECT pg_temp.d05_pick(ARRAY['TOT', 'TOT', 'KHA', 'XUAT_SAC', 'TOT', 'KHA', 'TOT'],
                                     pg_temp.d05_h(c.name || ':xl:' || i))
             FROM generate_series(1, 7) i) AS lv
FROM d05_class c
JOIN d05_year y ON TRUE
JOIN d05_vp v ON v.campus_id = c.campus_id
WHERE CURRENT_DATE > y.semester1_end + 3
   OR (CURRENT_DATE >= y.semester1_start + 60 AND c.campus_code = 'CS-B' AND c.k IN (1, 2, 4));

INSERT INTO class_ratings (classroom_id, campus_id, school_year_id, period, records, program, theme, environment,
                           routine, health, parents, overall, note, rated_by, rated_at)
SELECT r.classroom_id, r.campus_id, r.school_year_id, 'SEMESTER_1', r.lv[1], r.lv[2], r.lv[3], r.lv[4], r.lv[5],
       r.lv[6], r.lv[7],
       CASE WHEN (SELECT count(*) FROM unnest(r.lv) x WHERE x = 'XUAT_SAC') >= 3 THEN 'XUAT_SAC'
            WHEN (SELECT count(*) FROM unnest(r.lv) x WHERE x IN ('XUAT_SAC', 'TOT')) >= 4 THEN 'TOT'
            ELSE 'KHA' END,
       CASE WHEN r.interim THEN 'Chấm sơ bộ giữa học kỳ I sau dự giờ, cập nhật lại khi kết thúc học kỳ.'
            ELSE 'Lớp thực hiện tốt chương trình, hồ sơ sổ sách đầy đủ.' END,
       r.user_id, r.rated_at
FROM d05_rating r
ON CONFLICT ON CONSTRAINT uk_class_ratings_class_period DO NOTHING;

-- ---------- 5. Mở lại ngày đánh giá (OBS-19) ----------
-- Ba lần mở lại: một lần đã hết hạn (HT), một lần kết thúc sớm (PHT Cơ sở 2), một lần còn hiệu lực tới ngày kia (PHT Cơ
-- sở 1). Mỗi lớp được chọn chỉ seed một lần mở lại nên chạy lại ngày khác không thêm bản ghi.
CREATE TEMP TABLE d05_unlock AS
SELECT c.classroom_id, c.campus_id, v.day, v.kind, v.reason,
       CASE v.kind WHEN 'EXPIRED' THEN (SELECT user_id FROM d05_principal)
                   ELSE (SELECT p.user_id FROM d05_vp p WHERE p.campus_id = c.campus_id) END AS user_id
FROM (VALUES ('CS-A', 2, 12, 'EXPIRED', 'Cô giáo nghỉ ốm đột xuất, chưa kịp ghi đánh giá ngày cho lớp.'),
             ('CS-B', 3, 7, 'REVOKED', 'Mất điện buổi chiều, giáo viên chưa lưu được đánh giá ngày.'),
             ('CS-A', 4, 0, 'ACTIVE', 'Giáo viên dự tập huấn chuyên môn cả ngày, xin mở lại để bổ sung đánh giá.'))
     AS u (campus_code, k, back, kind, reason)
JOIN d05_class c ON c.campus_code = u.campus_code AND c.k = u.k
CROSS JOIN LATERAL (
    SELECT x.d AS day, u.kind, u.reason FROM d05_days x ORDER BY x.d DESC OFFSET u.back LIMIT 1
) v;

INSERT INTO assessment_day_unlocks (classroom_id, campus_id, date, valid_until, reason, unlocked_by, unlocked_at,
                                    revoked_by, revoked_at)
SELECT u.classroom_id, u.campus_id, u.day,
       CASE u.kind WHEN 'EXPIRED' THEN pg_temp.d05_at(u.day + 1, TIME '17:00')
                   WHEN 'REVOKED' THEN pg_temp.d05_at(u.day + 1, TIME '11:20')
                   ELSE pg_temp.d05_at(CURRENT_DATE + 2, TIME '17:00') END,
       u.reason, u.user_id,
       CASE WHEN u.kind = 'ACTIVE' THEN LEAST(pg_temp.d05_at(CURRENT_DATE, TIME '07:40'), now() - INTERVAL '5 minutes')
            ELSE pg_temp.d05_at(u.day + 1, TIME '07:45') END,
       CASE WHEN u.kind = 'REVOKED' THEN u.user_id END,
       CASE WHEN u.kind = 'REVOKED' THEN pg_temp.d05_at(u.day + 1, TIME '11:20') END
FROM d05_unlock u
WHERE u.user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM assessment_day_unlocks a WHERE a.classroom_id = u.classroom_id);

-- ---------- 6. Sức khỏe: chỉ số tăng trưởng riêng của từng trẻ ----------
-- Mỗi trẻ có z-score chiều cao/cân nặng cố định (gần phân phối chuẩn, lệch nhẹ dưới trung vị WHO); ~4% nhẹ cân, ~4% thừa
-- cân. Số đo = giá trị WHO tại z-score đó theo tuổi lẻ (HLT-03), nên trẻ lớn đều giữa các lần đo.
CREATE TEMP TABLE d05_kid AS
SELECT ch.id AS child_id, ch.code, ch.full_name, ch.date_of_birth AS dob, left(ch.gender, 1) AS sex,
       zh.z AS zh,
       CASE WHEN g.v < 4 THEN -2.25 - (pg_temp.d05_h(ch.code || ':zt') % 4) / 10.0
            WHEN g.v < 8 THEN zh.z + 1.7 + (pg_temp.d05_h(ch.code || ':zo') % 4) / 10.0
            ELSE 0.6 * zh.z + 0.7 * zn.z - 0.15 END AS zw
FROM children ch
CROSS JOIN LATERAL (SELECT ((pg_temp.d05_h(ch.code || ':zh1') % 100 + pg_temp.d05_h(ch.code || ':zh2') % 100
                             + pg_temp.d05_h(ch.code || ':zh3') % 100) - 148.5) / 50.0 - 0.2 AS z) zh
CROSS JOIN LATERAL (SELECT ((pg_temp.d05_h(ch.code || ':zn1') % 100 + pg_temp.d05_h(ch.code || ':zn2') % 100
                             + pg_temp.d05_h(ch.code || ':zn3') % 100) - 148.5) / 50.0 AS z) zn
CROSS JOIN LATERAL (SELECT pg_temp.d05_h(ch.code || ':dd') % 100 AS v) g
WHERE ch.gender IN ('MALE', 'FEMALE') AND ch.date_of_birth IS NOT NULL;

-- ---------- 7. Đợt khám sức khỏe đầu năm (HLT-08, HLT-17) ----------
-- Mỗi điểm trường một đợt khám (bác sĩ trạm y tế xã) giữa tháng 9, mỗi lớp khám một ngày; GV lớp nhập phiếu Phụ lục II và
-- kết luận theo lời bác sĩ. Đợt đã tự đóng sau ngày kết thúc: trẻ vắng ngày khám (công thức điểm danh) thành vắng, mỗi
-- điểm trường còn một phiếu nháp chưa kết luận.
INSERT INTO health_check_campaigns (kind, campus_id, name, start_date, end_date, examiner, note, created_by, created_at,
                                    updated_at, closed_at)
SELECT 'EXAM', c.id, 'Khám sức khỏe đầu năm học 2026-2027', v.start_date, v.start_date + 4, 'Trạm Y tế xã Thượng Hồng',
       'Khám theo mẫu Phụ lục II. Phụ huynh cho trẻ ăn sáng nhẹ, mặc áo dễ cởi trong ngày khám.',
       p.user_id, pg_temp.d05_at(DATE '2026-09-07', TIME '08:00'), pg_temp.d05_at(DATE '2026-09-07', TIME '08:00'),
       CASE WHEN v.start_date + 4 < CURRENT_DATE THEN pg_temp.d05_at(v.start_date + 5, TIME '00:00') END
FROM (VALUES ('CS-A', DATE '2026-09-14'), ('CS-B', DATE '2026-09-21')) v (code, start_date)
JOIN campuses c ON c.code = v.code
JOIN d05_vp p ON p.campus_id = c.id
WHERE NOT EXISTS (SELECT 1 FROM health_check_campaigns x WHERE x.campus_id = c.id AND x.kind = 'EXAM'
                      AND x.name = 'Khám sức khỏe đầu năm học 2026-2027');

CREATE TEMP TABLE d05_exam_camp AS
SELECT x.id AS campaign_id, x.campus_id, x.start_date, x.end_date, x.created_by, x.created_at, x.closed_at,
       x.closed_by, x.cancelled_at
FROM health_check_campaigns x
WHERE x.kind = 'EXAM' AND x.name = 'Khám sức khỏe đầu năm học 2026-2027';

INSERT INTO health_check_campaign_history (campaign_id, action, user_id, reason, created_at)
SELECT e.campaign_id, 'CREATED', e.created_by, NULL, e.created_at
FROM d05_exam_camp e
WHERE NOT EXISTS (SELECT 1 FROM health_check_campaign_history h WHERE h.campaign_id = e.campaign_id AND h.action = 'CREATED')
UNION ALL
SELECT e.campaign_id, 'CLOSED', NULL, 'Hệ thống tự đóng khi qua ngày kết thúc khám', e.closed_at
FROM d05_exam_camp e
WHERE e.closed_at IS NOT NULL AND e.closed_by IS NULL
  AND NOT EXISTS (SELECT 1 FROM health_check_campaign_history h WHERE h.campaign_id = e.campaign_id AND h.action = 'CLOSED');

-- Trẻ của lớp vào ngày khám; lớp thứ k của điểm trường khám ngày thứ k của đợt
CREATE TEMP TABLE d05_exam AS
SELECT e.campaign_id, e.campus_id, c.classroom_id, c.k, ec.d, k.child_id, k.code, k.full_name, k.dob, k.sex, k.zh, k.zw,
       pg_temp.d05_age(k.dob, ec.d) AS age, (ec.d - k.dob) / 30.4375 AS age_f,
       abs(hashtext(k.code || ':' || ec.d::text)::bigint) % 100 >= 5 AS present,
       pg_temp.d05_teacher(c.classroom_id, ec.d) AS teacher_id,
       pg_temp.d05_h(k.code || ':kham') AS h
FROM d05_exam_camp e
JOIN d05_class c ON c.campus_id = e.campus_id
CROSS JOIN LATERAL (SELECT e.start_date + ((c.k - 1) % 5)::int AS d) ec
JOIN enrollments en ON en.classroom_id = c.classroom_id AND en.valid_from <= ec.d
    AND (en.valid_to IS NULL OR en.valid_to >= ec.d)
JOIN d05_kid k ON k.child_id = en.child_id
WHERE e.cancelled_at IS NULL AND ec.d < CURRENT_DATE;

ALTER TABLE d05_exam ADD COLUMN height numeric(4, 1), ADD COLUMN weight numeric(4, 1), ADD COLUMN head numeric(3, 1),
    ADD COLUMN draft boolean;
UPDATE d05_exam SET
    height = round(pg_temp.d05_who('H', sex, age_f, zh) + ((h % 3) - 1) / 10.0, 1),
    weight = round(pg_temp.d05_who('W', sex, age_f, zw) + (((h / 3) % 3) - 1) / 10.0, 1),
    head = CASE WHEN age <= 60 THEN round(48.0 + 0.06 * (age - 24) + 0.4 * zh + ((h / 9) % 9 - 4) / 10.0, 1) END;
-- Mỗi điểm trường một phiếu còn nháp lúc đóng đợt (chưa kết luận)
UPDATE d05_exam x SET draft = x.code = (SELECT y.code FROM d05_exam y WHERE y.campus_id = x.campus_id AND y.present
                                         ORDER BY pg_temp.d05_h(y.code || ':nhap'), y.code LIMIT 1);

-- Kết luận bác sĩ: dinh dưỡng theo z-score WHO của số đo, vài trẻ sâu răng/viêm mũi họng/thiếu máu/lác mắt, trẻ khai
-- tiếp xúc người bệnh lao ⇒ nguy cơ lao; một trẻ Cơ sở 2 khai thêm tiền sử bản thân tại buổi khám (ghi ngược về khai báo)
CREATE TEMP TABLE d05_exam_row AS
SELECT x.*, pr.id_number, pr.premature, pr.gestation_weeks, pr.ethnicity, pr.beneficiary, pr.payment_source,
       pr.blood_type, pr.address_province, pr.address_ward, pr.address_street,
       g.full_name AS g_name, g.relationship AS g_rel, g.phone AS g_phone, g.citizen_id AS g_id,
       d.self_history_state, d.self_history_text, d.family_history_state, d.family_history_text, d.tb_contact_state,
       d.tb_contact_date,
       pg_temp.d05_z('W', x.sex, x.age, x.weight, TRUE) AS wz,
       pg_temp.d05_z('H', x.sex, x.age, x.height, FALSE) AS hz,
       pg_temp.d05_z('B', x.sex, x.age, x.weight / power(x.height / 100, 2), TRUE) AS bz,
       x.h % 100 < 9 AS dental, (x.h / 101) % 100 < 4 AS runny, (x.h / 10007) % 100 = 7 AS squint,
       (x.h / 1009) % 100 = 3 AS pale, (x.h / 503) % 100 < 3 AS vaccine_gap,
       (x.h / 7) % 100 < 22 AND g.id IS NOT NULL AS companion,
       FALSE AS self_new
FROM d05_exam x
LEFT JOIN child_profiles pr ON pr.child_id = x.child_id
LEFT JOIN child_guardians g ON g.child_id = x.child_id AND g.position = 0
LEFT JOIN child_health_declarations d ON d.child_id = x.child_id
WHERE x.present AND x.teacher_id IS NOT NULL;

UPDATE d05_exam_row r SET self_new = TRUE
WHERE r.code = (SELECT y.code FROM d05_exam_row y
                JOIN campuses ca ON ca.id = y.campus_id AND ca.code = 'CS-B'
                WHERE NOT y.draft AND y.self_history_state = 'NONE_REPORTED' AND y.family_history_state = 'NONE_REPORTED'
                  AND y.tb_contact_state = 'NONE_REPORTED'
                ORDER BY pg_temp.d05_h(y.code || ':tiensu'), y.code LIMIT 1);

ALTER TABLE d05_exam_row ADD COLUMN malnut_w boolean, ADD COLUMN malnut_h boolean, ADD COLUMN overw boolean,
    ADD COLUMN tb boolean, ADD COLUMN issues text[], ADD COLUMN advice text[], ADD COLUMN action text;
UPDATE d05_exam_row SET malnut_w = wz < -2, malnut_h = hz < -2, overw = bz > 2,
                        tb = tb_contact_state = 'REPORTED' AND tb_contact_date <= d;
UPDATE d05_exam_row SET
    issues = array_remove(ARRAY[
        CASE WHEN malnut_w THEN 'Suy dinh dưỡng thể nhẹ cân' END,
        CASE WHEN malnut_h THEN 'Suy dinh dưỡng thể thấp còi' END,
        CASE WHEN overw THEN 'Thừa cân' END,
        CASE WHEN pale THEN 'Có dấu hiệu thiếu máu (lòng bàn tay nhợt)' END,
        CASE WHEN dental THEN 'Sâu răng' END,
        CASE WHEN runny THEN 'Viêm mũi họng' END,
        CASE WHEN squint THEN 'Lác mắt' END], NULL),
    advice = array_remove(ARRAY[
        CASE WHEN malnut_w OR malnut_h THEN 'Tăng bữa phụ, bổ sung thịt, trứng, sữa; theo dõi cân nặng hằng tháng' END,
        CASE WHEN overw THEN 'Hạn chế đồ ngọt, nước có ga, đồ chiên rán; tăng vận động ngoài trời' END,
        CASE WHEN pale THEN 'Bổ sung thực phẩm giàu sắt như thịt bò, trứng, rau xanh đậm' END,
        CASE WHEN dental THEN 'Hướng dẫn trẻ chải răng sau ăn và trước khi ngủ, gia đình đưa trẻ khám nha khoa' END,
        CASE WHEN runny THEN 'Giữ ấm cổ, vệ sinh mũi bằng nước muối sinh lý' END,
        CASE WHEN squint THEN 'Gia đình đưa trẻ khám chuyên khoa mắt để được tư vấn' END,
        CASE WHEN tb THEN 'Gia đình đưa trẻ đi khám sàng lọc lao tại trung tâm y tế' END,
        CASE WHEN vaccine_gap THEN 'Gia đình đưa trẻ tiêm bổ sung các mũi vắc xin còn thiếu theo lịch' END], NULL);
UPDATE d05_exam_row SET action = CASE WHEN squint THEN 'REFERRAL'
                                      WHEN cardinality(advice) > 0 THEN 'ADVICE' ELSE 'NONE' END;

CREATE TEMP TABLE d05_exam_json AS
SELECT r.campaign_id, r.child_id, r.classroom_id, r.d, r.height, r.weight, r.head, r.teacher_id, r.draft, r.h,
       pg_temp.d05_at(r.d, TIME '08:30') + (r.h % 150) * INTERVAL '1 minute' AS created_at,
       pg_temp.d05_at(r.d, TIME '14:00') + (r.h % 120) * INTERVAL '1 minute' AS concluded_at,
       jsonb_build_object(
           'step', CASE WHEN r.draft THEN 3 ELSE 4 END,
           'admin', jsonb_build_object(
               'childIdNumber', coalesce(r.id_number, ''), 'premature', coalesce(r.premature, ''),
               'gestationWeeks', r.gestation_weeks, 'ethnicity', coalesce(r.ethnicity, ''),
               'beneficiary', coalesce(r.beneficiary, ''), 'paymentSource', coalesce(r.payment_source, ''),
               'bloodType', coalesce(r.blood_type, ''),
               'address', jsonb_build_object('province', coalesce(r.address_province, ''),
                                             'ward', coalesce(r.address_ward, ''),
                                             'street', coalesce(r.address_street, '')),
               'hasCompanion', CASE WHEN r.companion THEN 'YES' ELSE 'NO' END,
               'companion', CASE WHEN r.companion
                   THEN jsonb_build_object('guardianIndex', 0, 'fullName', r.g_name,
                                           'relation', CASE r.g_rel WHEN 'FATHER' THEN 'Cha' WHEN 'MOTHER' THEN 'Mẹ'
                                                           WHEN 'GRANDFATHER' THEN 'Ông/bà' WHEN 'GRANDMOTHER' THEN 'Ông/bà'
                                                           WHEN 'SIBLING' THEN 'Anh/chị' ELSE 'Khác' END,
                                           'phone', coalesce(r.g_phone, ''), 'idNumber', coalesce(r.g_id, ''))
                   ELSE jsonb_build_object('guardianIndex', NULL, 'fullName', '', 'relation', '', 'phone', '',
                                           'idNumber', '') END),
           'history', jsonb_build_object(
               'self', CASE WHEN r.self_new OR r.self_history_state = 'REPORTED' THEN 'YES' ELSE 'NO' END,
               'selfText', CASE WHEN r.self_new THEN 'Viêm phế quản năm 2025'
                                ELSE coalesce(r.self_history_text, '') END,
               'family', CASE WHEN r.family_history_state = 'REPORTED' THEN 'YES' ELSE 'NO' END,
               'familyText', coalesce(r.family_history_text, ''),
               'tbContact', CASE WHEN r.tb THEN 'YES' ELSE 'NO' END,
               'tbContactDate', CASE WHEN r.tb THEN to_jsonb(r.tb_contact_date::text) END),
           'vitals', jsonb_build_object(
               'temperatureC', round(36.4 + ((r.h / 13) % 8) / 10.0, 1), 'temperatureStatus', 'NORMAL',
               'pulse', CASE WHEN r.age <= 35 THEN 102 WHEN r.age <= 71 THEN 88 ELSE 82 END + (r.h / 17) % 20,
               'pulseStatus', 'NORMAL',
               'respiratoryRate', CASE WHEN r.age <= 35 THEN 26 WHEN r.age <= 71 THEN 22 ELSE 20 END + (r.h / 19) % 5,
               'respiratoryStatus', 'NORMAL'),
           'nutrition', jsonb_build_object(
               'lengthForAgeSd', round(r.hz, 1), 'weightForAgeSd', round(r.wz, 1),
               'headStatus', CASE WHEN r.age <= 60 THEN 'NORMAL' ELSE '' END,
               'armCircumferenceMm', round(158 + 9 * r.zw + (r.h / 23) % 9),
               'statuses', CASE WHEN NOT (r.malnut_w OR r.malnut_h OR r.overw OR r.pale) THEN '["NORMAL"]'::jsonb
                                ELSE to_jsonb(array_remove(ARRAY[CASE WHEN r.pale THEN 'ANEMIA_SIGNS' END,
                                                                 CASE WHEN r.malnut_w OR r.malnut_h THEN 'MALNUTRITION' END,
                                                                 CASE WHEN r.overw THEN 'OVERWEIGHT' END], NULL)) END),
           'development', jsonb_build_object('mental', 'YES', 'motor', 'YES',
                                             'autismRisk', CASE WHEN r.age BETWEEN 16 AND 30 THEN 'NO' ELSE '' END),
           'immunization', jsonb_build_object('bcg', 'YES', 'hepB1', 'YES',
                                              'fullSchedule', CASE WHEN r.vaccine_gap THEN 'NO' ELSE 'YES' END),
           'clinical', jsonb_build_object(
               'skinColor', 'PINK', 'palms', CASE WHEN r.pale THEN 'PALE' ELSE 'NORMAL' END, 'headShape', 'NORMAL',
               'neckMotion', 'NORMAL', 'neckMass', 'NO', 'eyePosition', 'NORMAL', 'eyelids', 'NORMAL',
               'strabismus', CASE WHEN r.squint THEN 'YES' ELSE 'NO' END, 'hearing', 'NORMAL', 'earLump', 'NO',
               'earDischarge', 'NO', 'noseShape', 'NORMAL', 'runnyNose', CASE WHEN r.runny THEN 'YES' ELSE 'NO' END,
               'nasalCongestion', CASE WHEN r.runny THEN 'YES' ELSE 'NO' END, 'mouthShape', 'NORMAL',
               'tongueShape', 'NORMAL', 'tongueTie', 'NO', 'smallChin', 'NO',
               'dentalIssue', CASE WHEN r.dental THEN 'YES' ELSE 'NO' END, 'asymmetricMotion', 'NO', 'spine', 'NORMAL',
               'gait', 'NORMAL', 'prolongedSadness', 'NO', 'selfHarmRisk', 'NO', 'behaviorNote', ''),
           'conclusion', CASE WHEN r.draft
               THEN jsonb_build_object('normal', FALSE, 'tbRisk', FALSE, 'healthIssue', FALSE, 'issueKind', '',
                                       'followUpFinding', '', 'action', '', 'issueText', '', 'adviceText', '',
                                       'specialty', '', 'doctorName', '', 'parentOpinion', '')
               ELSE jsonb_build_object(
                   'normal', cardinality(r.issues) = 0 AND NOT r.tb, 'tbRisk', r.tb,
                   'healthIssue', cardinality(r.issues) > 0, 'issueKind', '', 'followUpFinding', '',
                   'action', r.action, 'issueText', array_to_string(r.issues, '; '),
                   'adviceText', CASE WHEN cardinality(r.advice) > 0 THEN array_to_string(r.advice, '. ') || '.' ELSE '' END,
                   'specialty', CASE WHEN r.squint THEN 'Mắt' ELSE '' END,
                   'doctorName', CASE WHEN ca.code = 'CS-A' AND r.k % 2 = 1 THEN 'Nguyễn Thị Hằng'
                                      WHEN ca.code = 'CS-A' THEN 'Phạm Văn Đức'
                                      WHEN r.k % 2 = 1 THEN 'Lương Thị Mai' ELSE 'Phạm Văn Đức' END,
                   'parentOpinion', CASE WHEN r.companion AND (r.tb OR r.squint)
                                         THEN 'Gia đình đồng ý, sẽ đưa cháu đi khám theo hướng dẫn của bác sĩ.'
                                         ELSE '' END) END) AS exam
FROM d05_exam_row r
JOIN campuses ca ON ca.id = r.campus_id;

INSERT INTO health_measurements (child_id, campaign_id, classroom_id, date, height_cm, weight_kg, head_circumference_cm,
                                 note, measured_by, published_at, published_by, created_at, updated_at, exam,
                                 exam_status, concluded_at)
SELECT j.child_id, j.campaign_id, j.classroom_id, j.d, j.height, j.weight, j.head, '', j.teacher_id,
       CASE WHEN NOT j.draft THEN j.concluded_at END, CASE WHEN NOT j.draft THEN j.teacher_id END,
       j.created_at, CASE WHEN j.draft THEN j.created_at + INTERVAL '25 minutes' ELSE j.concluded_at END,
       j.exam, CASE WHEN j.draft THEN 'DRAFT' ELSE 'CONCLUDED' END, CASE WHEN NOT j.draft THEN j.concluded_at END
FROM d05_exam_json j
WHERE NOT EXISTS (SELECT 1 FROM health_measurements m WHERE m.child_id = j.child_id AND m.campaign_id = j.campaign_id)
ON CONFLICT DO NOTHING;

-- Đợt đã đóng: trẻ trong danh sách lớp chưa có kết quả là vắng (lớp lúc khám)
INSERT INTO health_check_absences (campaign_id, child_id, classroom_id)
SELECT x.campaign_id, x.child_id, x.classroom_id
FROM d05_exam x
JOIN d05_exam_camp e ON e.campaign_id = x.campaign_id AND e.closed_at IS NOT NULL
WHERE NOT EXISTS (SELECT 1 FROM health_measurements m WHERE m.child_id = x.child_id AND m.campaign_id = x.campaign_id)
ON CONFLICT DO NOTHING;

-- Sửa phiếu đã kết luận (HLT-13): mỗi điểm trường một phiếu cân lại ngày hôm sau, lưu ô đổi (số kiểu vi-VN như FE)
CREATE TEMP TABLE d05_exam_edit AS
SELECT DISTINCT ON (j.campaign_id) j.campaign_id, m.id AS measurement_id, j.teacher_id, m.weight_kg,
       m.weight_kg + 0.5 AS old_weight,
       pg_temp.d05_at(j.d + 1, TIME '09:15') AS at
FROM d05_exam_json j
JOIN health_measurements m ON m.child_id = j.child_id AND m.campaign_id = j.campaign_id AND m.exam_status = 'CONCLUDED'
WHERE NOT j.draft
  AND NOT EXISTS (SELECT 1 FROM health_measurement_edits ed JOIN health_measurements hm ON hm.id = ed.measurement_id
                  WHERE hm.campaign_id = j.campaign_id)
ORDER BY j.campaign_id, pg_temp.d05_h(j.child_id || ':sua'), j.child_id;

INSERT INTO health_measurement_edits (measurement_id, user_id, reason, changes, created_at)
SELECT x.measurement_id, x.teacher_id, 'Cân lại vì lần đầu bé mặc áo khoác dày.',
       jsonb_build_array(jsonb_build_object('field', 'weightKg', 'label', 'Cân nặng (kg)',
                                            'from', replace(x.old_weight::text, '.', ','),
                                            'to', replace(x.weight_kg::text, '.', ','))),
       x.at
FROM d05_exam_edit x
WHERE NOT EXISTS (SELECT 1 FROM health_measurement_edits e WHERE e.measurement_id = x.measurement_id);

UPDATE health_measurements m SET published_at = x.at, updated_at = x.at
FROM d05_exam_edit x
WHERE m.id = x.measurement_id AND m.updated_at < x.at
  AND EXISTS (SELECT 1 FROM health_measurement_edits e WHERE e.measurement_id = x.measurement_id AND e.created_at = x.at);

-- Kết luận ghi tiền sử ngược về khai báo sức khỏe (HLT-06, HLT-08): chỉ khi khai báo còn như lúc tiếp nhận
CREATE TEMP TABLE d05_self AS
SELECT r.child_id, m.measured_by AS user_id, m.concluded_at AS at, r.d, cd.self_history_state, cd.self_history_text,
       cd.family_history_state, cd.family_history_text, cd.tb_contact_state, cd.tb_contact_date
FROM d05_exam_row r
JOIN health_measurements m ON m.child_id = r.child_id AND m.campaign_id = r.campaign_id AND m.exam_status = 'CONCLUDED'
    AND m.exam -> 'history' ->> 'self' = 'YES'
JOIN child_health_declarations cd ON cd.child_id = r.child_id AND cd.self_history_state = 'NONE_REPORTED'
WHERE r.self_new
  AND NOT EXISTS (SELECT 1 FROM child_health_declaration_history h
                  WHERE h.child_id = r.child_id AND h.action = 'UPDATED_FROM_EXAM');

INSERT INTO child_health_declaration_history (child_id, action, user_id, note, allergy_change, history_change, created_at)
SELECT s.child_id, 'UPDATED_FROM_EXAM', s.user_id, 'Cập nhật từ phiếu khám ngày ' || to_char(s.d, 'DD/MM/YYYY'), NULL,
       jsonb_build_object(
           'from', jsonb_build_object(
               'self', jsonb_build_object('state', s.self_history_state, 'text', s.self_history_text),
               'family', jsonb_build_object('state', s.family_history_state, 'text', s.family_history_text),
               'tbContact', jsonb_build_object('state', s.tb_contact_state, 'contactDate', s.tb_contact_date)),
           'to', jsonb_build_object(
               'self', jsonb_build_object('state', 'REPORTED', 'text', 'Viêm phế quản năm 2025'),
               'family', jsonb_build_object('state', s.family_history_state, 'text', s.family_history_text),
               'tbContact', jsonb_build_object('state', s.tb_contact_state, 'contactDate', s.tb_contact_date))),
       s.at
FROM d05_self s;

UPDATE child_health_declarations cd SET self_history_state = 'REPORTED', self_history_text = 'Viêm phế quản năm 2025'
FROM d05_self s
WHERE cd.child_id = s.child_id;

-- ---------- 8. Kỳ cân đo đang diễn ra (HLT-01, HLT-09, HLT-18) ----------
-- Mở từ thứ Hai gần nhất (12 ngày), khi đợt khám đầu năm đã đóng đủ lâu. Lớp đo cách ngày: Cơ sở 1 các lớp 1, 3, 5 đã
-- đo (ngày hôm nay nếu là ngày học thì Cơ sở 1 đang đo), Cơ sở 2 lệch một ngày nên ít lớp đã đo hơn; trẻ vắng hôm đo
-- chưa có kết quả. Kỳ cân đo không đo vòng đầu.
INSERT INTO health_check_campaigns (kind, campus_id, name, start_date, end_date, examiner, note, created_by, created_at,
                                    updated_at)
SELECT 'MEASUREMENT', c.campus_id, 'Cân đo định kỳ tháng ' || to_char(s.start_date, 'MM/YYYY'), s.start_date,
       s.start_date + 11, '', 'Cân đo buổi sáng trước giờ ăn phụ, trẻ mặc quần áo mỏng, bỏ giày dép.',
       CASE WHEN c.code = 'CS-A' THEN (SELECT user_id FROM d05_principal) ELSE v.user_id END,
       pg_temp.d05_at(s.start_date - 3, TIME '09:00'), pg_temp.d05_at(s.start_date - 3, TIME '09:00')
FROM (SELECT DISTINCT e.campus_id, ca.code FROM d05_exam_camp e JOIN campuses ca ON ca.id = e.campus_id
      WHERE e.closed_at IS NOT NULL) c
JOIN d05_vp v ON v.campus_id = c.campus_id
CROSS JOIN LATERAL (SELECT date_trunc('week', CURRENT_DATE - 3)::date AS start_date) s
JOIN d05_year y ON s.start_date + 11 <= y.end_date
WHERE s.start_date >= DATE '2026-09-25' + 45
  AND NOT EXISTS (SELECT 1 FROM health_check_campaigns x
                  WHERE x.campus_id = c.campus_id AND x.closed_at IS NULL AND x.cancelled_at IS NULL)
  AND NOT EXISTS (SELECT 1 FROM health_check_campaigns x
                  WHERE x.campus_id = c.campus_id AND x.kind = 'MEASUREMENT' AND x.start_date = s.start_date);

CREATE TEMP TABLE d05_meas_camp AS
SELECT x.id AS campaign_id, x.campus_id, ca.code AS campus_code, x.start_date, x.end_date, x.created_by, x.created_at
FROM health_check_campaigns x
JOIN campuses ca ON ca.id = x.campus_id
WHERE x.kind = 'MEASUREMENT' AND x.name LIKE 'Cân đo định kỳ tháng %' AND x.closed_at IS NULL AND x.cancelled_at IS NULL;

INSERT INTO health_check_campaign_history (campaign_id, action, user_id, reason, created_at)
SELECT m.campaign_id, 'CREATED', m.created_by, NULL, m.created_at
FROM d05_meas_camp m
WHERE NOT EXISTS (SELECT 1 FROM health_check_campaign_history h WHERE h.campaign_id = m.campaign_id AND h.action = 'CREATED');

CREATE TEMP TABLE d05_meas AS
SELECT mc.campaign_id, mc.campus_id, c.classroom_id, sd.d, k.child_id, k.code, k.sex, k.zh, k.zw,
       (sd.d - k.dob) / 30.4375 AS age_f, pg_temp.d05_teacher(c.classroom_id, sd.d) AS teacher_id,
       pg_temp.d05_h(k.code || ':cando:' || mc.start_date) AS h
FROM d05_meas_camp mc
JOIN d05_class c ON c.campus_id = mc.campus_id
CROSS JOIN LATERAL (
    SELECT x.d FROM (
        SELECT g::date AS d, row_number() OVER (ORDER BY g) - 1 AS idx
        FROM generate_series(mc.start_date, mc.end_date, INTERVAL '1 day') g
        WHERE extract(isodow FROM g) < 6
          AND NOT EXISTS (SELECT 1 FROM school_holidays hd WHERE g::date BETWEEN hd.start_date AND hd.end_date)
    ) x
    WHERE x.idx = (c.k - 1) * 2 + CASE WHEN mc.campus_code = 'CS-A' THEN 0 ELSE 1 END
) sd
JOIN enrollments en ON en.classroom_id = c.classroom_id AND en.valid_from <= sd.d
    AND (en.valid_to IS NULL OR en.valid_to >= sd.d)
JOIN d05_kid k ON k.child_id = en.child_id
WHERE (sd.d < CURRENT_DATE OR (sd.d = CURRENT_DATE AND mc.campus_code = 'CS-A'))
  AND abs(hashtext(k.code || ':' || sd.d::text)::bigint) % 100 >= 5;

-- Vài trẻ sụt cân nhẹ so với lần khám đầu năm (ốm trong tháng), để có cảnh báo xu hướng
INSERT INTO health_measurements (child_id, campaign_id, classroom_id, date, height_cm, weight_kg, note, measured_by,
                                 published_at, published_by, created_at, updated_at)
SELECT m.child_id, m.campaign_id, m.classroom_id, m.d,
       round(pg_temp.d05_who('H', m.sex, m.age_f, m.zh) + ((m.h % 3) - 1) / 10.0, 1),
       CASE WHEN pg_temp.d05_h(m.code || ':sut') % 100 < 4 AND ex.weight_kg IS NOT NULL
                THEN ex.weight_kg - 0.3 - (m.h % 3) / 10.0
            ELSE round(pg_temp.d05_who('W', m.sex, m.age_f, m.zw) + (((m.h / 3) % 3) - 1) / 10.0, 1) END,
       CASE WHEN m.h % 100 < 3 THEN 'Cân sau bữa ăn sáng.' WHEN m.h % 100 < 5 THEN 'Bé vừa khỏi ốm, ăn kém mấy hôm trước.'
            ELSE '' END,
       m.teacher_id, ts.at, m.teacher_id, ts.at, ts.at
FROM d05_meas m
CROSS JOIN LATERAL (SELECT LEAST(pg_temp.d05_at(m.d, TIME '07:45') + (m.h % 90) * INTERVAL '1 minute',
                                 now() - INTERVAL '10 minutes') AS at) ts
LEFT JOIN LATERAL (
    SELECT x.weight_kg FROM health_measurements x JOIN d05_exam_camp e ON e.campaign_id = x.campaign_id
    WHERE x.child_id = m.child_id AND x.exam_status = 'CONCLUDED' LIMIT 1
) ex ON TRUE
WHERE m.teacher_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM health_measurements x WHERE x.child_id = m.child_id AND x.campaign_id = m.campaign_id)
ON CONFLICT DO NOTHING;

-- Sửa số đo kỳ cân đo (HLT-13): một trẻ Cơ sở 1 nhập nhầm chiều cao, GV đo lại ngay trong ngày
CREATE TEMP TABLE d05_meas_edit AS
SELECT DISTINCT ON (mc.campaign_id) m.id AS measurement_id, m.measured_by AS user_id, m.height_cm,
       m.height_cm - 0.9 AS old_height, m.created_at + INTERVAL '50 minutes' AS at
FROM d05_meas_camp mc
JOIN health_measurements m ON m.campaign_id = mc.campaign_id AND m.date < CURRENT_DATE
WHERE mc.campus_code = 'CS-A'
  AND NOT EXISTS (SELECT 1 FROM health_measurement_edits ed JOIN health_measurements hm ON hm.id = ed.measurement_id
                  WHERE hm.campaign_id = mc.campaign_id)
ORDER BY mc.campaign_id, pg_temp.d05_h(m.child_id || ':suacao'), m.id;

INSERT INTO health_measurement_edits (measurement_id, user_id, reason, changes, created_at)
SELECT x.measurement_id, x.user_id, 'Nhập nhầm chiều cao, đã đo lại cho bé.',
       jsonb_build_array(jsonb_build_object('field', 'heightCm', 'label', 'Chiều cao (cm)',
                                            'from', x.old_height::text, 'to', x.height_cm::text)),
       x.at
FROM d05_meas_edit x
WHERE NOT EXISTS (SELECT 1 FROM health_measurement_edits e WHERE e.measurement_id = x.measurement_id);

UPDATE health_measurements m SET published_at = x.at, updated_at = x.at
FROM d05_meas_edit x
WHERE m.id = x.measurement_id AND m.updated_at < x.at
  AND EXISTS (SELECT 1 FROM health_measurement_edits e WHERE e.measurement_id = x.measurement_id AND e.created_at = x.at);

-- ---------- 9. Giải thích xu hướng tăng trưởng (HLT-02..05) ----------
-- Trẻ có đúng 2 kết quả đã công bố (phiếu khám đầu năm + số đo kỳ này, đo trước hôm nay). Mỗi điểm trường: 2 trẻ bình
-- thường đã duyệt (phụ huynh xem được), 1 trẻ bình thường còn nháp; trẻ sụt cân: 1 bản đã duyệt, còn lại nháp chờ duyệt.
-- Cảnh báo tính như HealthGrowth.warnings.
CREATE TEMP TABLE d05_trend AS
SELECT k.child_id, k.code, k.sex, k.dob, mc.campus_id, n.classroom_id,
       e.id AS e_id, e.date AS d1, e.height_cm AS h1, e.weight_kg AS w1,
       n.id AS n_id, n.date AS d2, n.height_cm AS h2, n.weight_kg AS w2, n.updated_at AS n_at,
       e.weight_kg - n.weight_kg >= 0.2 AS wdrop
FROM d05_meas_camp mc
JOIN health_measurements n ON n.campaign_id = mc.campaign_id AND n.date < CURRENT_DATE
JOIN d05_kid k ON k.child_id = n.child_id
JOIN health_measurements e ON e.child_id = n.child_id AND e.exam_status = 'CONCLUDED'
JOIN d05_exam_camp ec ON ec.campaign_id = e.campaign_id
WHERE (SELECT count(*) FROM health_measurements x WHERE x.child_id = n.child_id AND x.published_at IS NOT NULL) = 2
  -- Chọn một lần cho mỗi kỳ cân đo: chạy lại (kể cả ngày sau, khi có thêm lớp đã đo) không chọn thêm trẻ
  AND NOT EXISTS (SELECT 1 FROM health_interpretations i JOIN health_measurements hm ON hm.child_id = i.child_id
                  WHERE hm.campaign_id = mc.campaign_id);

ALTER TABLE d05_trend ADD COLUMN wz numeric, ADD COLUMN hz numeric, ADD COLUMN bz numeric, ADD COLUMN bz_prev numeric;
UPDATE d05_trend SET
    wz = pg_temp.d05_z('W', sex, pg_temp.d05_age(dob, d2), w2, TRUE),
    hz = pg_temp.d05_z('H', sex, pg_temp.d05_age(dob, d2), h2, FALSE),
    bz = pg_temp.d05_z('B', sex, pg_temp.d05_age(dob, d2), w2 / power(h2 / 100, 2), TRUE),
    bz_prev = pg_temp.d05_z('B', sex, pg_temp.d05_age(dob, d1), w1 / power(h1 / 100, 2), TRUE);

-- Trẻ không sụt cân chỉ chọn khi mọi chỉ số trong khoảng bình thường (bản giải thích bình thường)
DELETE FROM d05_trend WHERE NOT wdrop AND (wz < -2 OR hz < -2 OR bz < -2 OR bz > 2 OR abs(bz - bz_prev) >= 1);

ALTER TABLE d05_trend ADD COLUMN rk int, ADD COLUMN status text;
UPDATE d05_trend t SET rk = r.rk
FROM (SELECT child_id, row_number() OVER (PARTITION BY campus_id, wdrop ORDER BY pg_temp.d05_h(code || ':xh'), code) AS rk
      FROM d05_trend) r
WHERE r.child_id = t.child_id;
UPDATE d05_trend SET status = CASE WHEN wdrop THEN CASE WHEN rk = 1 THEN 'APPROVED' ELSE 'DRAFT' END
                                   WHEN rk <= 2 THEN 'APPROVED' WHEN rk = 3 THEN 'DRAFT' END;
DELETE FROM d05_trend WHERE status IS NULL;

INSERT INTO health_interpretations (child_id, status, summary, warnings, measurement_ids, source, model, generated_by,
                                    generated_at, approved_by, approved_at, created_at, updated_at)
SELECT t.child_id, t.status,
       CASE WHEN t.wdrop THEN
           'Ở lần đo ngày ' || to_char(t.d2, 'DD/MM/YYYY') || ', cân nặng giảm ' || replace(rtrim(rtrim((t.w1 - t.w2)::text, '0'), '.'), '.', ',')
           || ' kg so với lần khám ngày ' || to_char(t.d1, 'DD/MM/YYYY') || ', trong khi chiều cao vẫn tăng từ '
           || replace(t.h1::text, '.', ',') || ' cm lên ' || replace(t.h2::text, '.', ',') || ' cm. Nhà trường nên trao đổi với gia đình về bữa ăn và giấc ngủ của trẻ ở'
           || ' nhà, theo dõi cân nặng ở lần đo tiếp theo. Nếu cân nặng tiếp tục giảm, gia đình nên đưa trẻ đến cơ sở y'
           || ' tế để được tư vấn.'
       ELSE
           'Hai lần đo ngày ' || to_char(t.d1, 'DD/MM/YYYY') || ' và ' || to_char(t.d2, 'DD/MM/YYYY')
           || ' cho thấy chiều cao tăng từ ' || replace(t.h1::text, '.', ',') || ' cm lên ' || replace(t.h2::text, '.', ',')
           || ' cm, cân nặng từ ' || replace(t.w1::text, '.', ',') || ' kg lên '
           || replace(t.w2::text, '.', ',') || ' kg. Các chỉ số nằm trong khoảng bình thường của chuẩn WHO cho trẻ cùng tuổi và giới. Gia đình và'
           || ' nhà trường tiếp tục duy trì bữa ăn đủ chất, ngủ đủ giấc và cho trẻ vận động hằng ngày.' END,
       coalesce(nullif(w.arr, '[]'::jsonb),
                jsonb_build_array(jsonb_build_object(
                    'level', 'info', 'text', 'Các chỉ số lần đo gần nhất nằm trong khoảng bình thường theo chuẩn WHO.'))),
       jsonb_build_array(t.e_id, t.n_id), 'AI', 'gemini-2.0-flash', g.user_id, g.at,
       CASE WHEN t.status = 'APPROVED' THEN v.user_id END,
       CASE WHEN t.status = 'APPROVED' THEN g.at + INTERVAL '25 minutes' END,
       g.at, g.at + CASE WHEN t.status = 'APPROVED' THEN INTERVAL '25 minutes' ELSE INTERVAL '0' END
FROM d05_trend t
JOIN d05_vp v ON v.campus_id = t.campus_id
CROSS JOIN LATERAL (SELECT pg_temp.d05_teacher(t.classroom_id, t.d2) AS user_id, t.n_at + INTERVAL '2 hours' AS at) g
CROSS JOIN LATERAL (
    SELECT coalesce(jsonb_agg(jsonb_build_object('level', x.level, 'text', x.text) ORDER BY x.ord), '[]'::jsonb) AS arr
    FROM (VALUES
        (1, t.w1 - t.w2 >= 0.2, 'warning',
         'Cân nặng lần đo ngày ' || to_char(t.d2, 'DD/MM/YYYY') || ' giảm ' || rtrim(rtrim((t.w1 - t.w2)::text, '0'), '.')
             || ' kg so với lần đo ngày ' || to_char(t.d1, 'DD/MM/YYYY') || '.'),
        (2, t.d2 - t.d1 >= 90 AND t.h2 <= t.h1, 'warning',
         'Chiều cao chưa tăng giữa hai lần đo ngày ' || to_char(t.d1, 'DD/MM/YYYY') || ' và '
             || to_char(t.d2, 'DD/MM/YYYY') || '.'),
        (3, t.wz < -2, 'warning', 'Cân nặng theo tuổi lần đo gần nhất dưới mức -2SD của chuẩn WHO.'),
        (4, t.hz < -2, 'warning', 'Chiều cao theo tuổi lần đo gần nhất dưới mức -2SD của chuẩn WHO.'),
        (5, t.bz < -2 OR t.bz > 2, 'warning', 'BMI theo tuổi lần đo gần nhất ngoài khoảng -2SD đến +2SD của chuẩn WHO.'),
        (6, abs(t.bz - t.bz_prev) >= 1, 'info',
         'BMI theo tuổi thay đổi nhanh giữa hai lần đo gần nhất (z-score từ ' || to_char(t.bz_prev, 'FM990.00')
             || ' sang ' || to_char(t.bz, 'FM990.00') || ').')
    ) x (ord, cond, level, text)
    WHERE x.cond
) w
WHERE g.user_id IS NOT NULL;

-- ---------- 10. Nhận xét tuần theo trẻ (OBS-29) ----------
-- Tuần đã kết thúc của năm học: tuần cũ GV đã gửi phụ huynh chiều thứ Sáu. Tuần gần nhất: lớp thứ lẻ đã gửi, lớp thứ
-- chẵn còn bản nháp hệ thống ⇒ việc cần làm ở màn GV. Vài bản đã gửi GV sửa lại sáng thứ Hai (lưu lịch sử nội dung).
-- Lớp của bản nhận xét = lớp trẻ học nhiều ngày nhất trong tuần.
CREATE TEMP TABLE d05_wc AS
SELECT DISTINCT ON (s.child_id, s.monday)
       s.child_id, s.classroom_id, c.campus_id, c.school_year_id, c.k, s.monday, s.friday, s.present_days, s.flags,
       s.monday = (SELECT max(monday) FROM d05_week) AS last_week,
       pg_temp.d05_teacher(s.classroom_id, s.friday) AS teacher_id,
       pg_temp.d05_h(s.code || ':wc:' || s.monday) AS h
FROM (
    SELECT a.child_id, a.code, a.classroom_id, w.monday, w.friday, count(*) AS days,
           count(*) FILTER (WHERE a.present) AS present_days,
           count(o.child_id) FILTER (WHERE a.present AND o.flag) AS flags
    FROM d05_week w
    JOIN d05_att a ON a.d BETWEEN w.monday AND w.friday
    LEFT JOIN d05_obs o ON o.child_id = a.child_id AND o.date = a.d
    GROUP BY a.child_id, a.code, a.classroom_id, w.monday, w.friday
) s
JOIN d05_class c ON c.classroom_id = s.classroom_id
WHERE s.present_days > 0
ORDER BY s.child_id, s.monday, s.days DESC, s.classroom_id;

DELETE FROM d05_wc WHERE teacher_id IS NULL;
ALTER TABLE d05_wc ADD COLUMN sent boolean, ADD COLUMN revised boolean, ADD COLUMN opening text, ADD COLUMN body text;
UPDATE d05_wc SET sent = NOT last_week OR k % 2 = 1;
UPDATE d05_wc SET revised = sent AND NOT last_week AND h % 23 = 0,
                  opening = 'Tuần ' || to_char(monday, 'DD/MM') || ' – ' || to_char(friday, 'DD/MM/YYYY') || ', bé đi học '
                      || present_days || ' ngày'
                      || CASE WHEN flags > 0 THEN ', được ' || flags || ' cờ bé ngoan' ELSE '' END || '. ',
                  body = pg_temp.d05_pick(ARRAY[
                      'Bé vui vẻ đến lớp, ăn hết suất và ngủ trưa ngoan.',
                      'Bé hứng thú với hoạt động góc, chơi hòa thuận cùng các bạn.',
                      'Bé mạnh dạn phát biểu trong giờ học, thuộc bài thơ của tuần.',
                      'Bé tự xúc ăn, tự cất dép và gấp khăn sau giờ ngủ.',
                      'Bé tham gia tích cực giờ vận động ngoài trời và trò chơi dân gian.'], h)
                      || E'\n' || pg_temp.d05_pick(ARRAY[
                      'Tuần tới cô tiếp tục rèn cho bé thói quen rửa tay trước khi ăn.',
                      'Gia đình cho bé đi ngủ sớm để sáng đến lớp đúng giờ.',
                      'Gia đình khuyến khích bé kể lại các hoạt động ở lớp mỗi tối.',
                      'Bé cần ăn thêm rau, gia đình phối hợp cùng cô nhắc bé.',
                      'Cô tiếp tục động viên bé nhường nhịn bạn khi chơi đồ chơi chung.'], h / 7);

CREATE TEMP TABLE d05_wc_new (id bigint, child_id bigint, monday date);

WITH ins AS (
    INSERT INTO weekly_comments (child_id, classroom_id, campus_id, school_year_id, week_start, content, status,
                                 draft_source, sent_at, sent_by, created_by, created_at, updated_by, updated_at)
    SELECT w.child_id, w.classroom_id, w.campus_id, w.school_year_id, w.monday,
           w.opening || w.body || CASE WHEN w.revised THEN ' Cô đã trao đổi thêm với phụ huynh lúc đón bé.' ELSE '' END,
           CASE WHEN w.sent THEN 'SENT' ELSE 'DRAFT' END, 'TEMPLATE',
           CASE WHEN w.revised THEN pg_temp.d05_at(w.friday + 3, TIME '08:15')
                WHEN w.sent THEN pg_temp.d05_at(w.friday, TIME '16:30') + (w.h % 40) * INTERVAL '1 minute' END,
           CASE WHEN w.sent THEN w.teacher_id END,
           w.teacher_id, LEAST(pg_temp.d05_at(w.friday, TIME '06:30'), now()),
           CASE WHEN w.sent THEN w.teacher_id END,
           CASE WHEN w.revised THEN pg_temp.d05_at(w.friday + 3, TIME '08:15')
                WHEN w.sent THEN pg_temp.d05_at(w.friday, TIME '16:30') + (w.h % 40) * INTERVAL '1 minute'
                ELSE LEAST(pg_temp.d05_at(w.friday, TIME '06:30'), now()) END
    FROM d05_wc w
    ON CONFLICT ON CONSTRAINT uk_weekly_comments_child_class_week DO NOTHING
    RETURNING id, child_id, week_start
)
INSERT INTO d05_wc_new SELECT i.id, i.child_id, i.week_start FROM ins i;

-- Lịch sử nội dung: bản gửi chiều thứ Sáu, GV sửa và gửi lại sáng thứ Hai — chỉ cho bản vừa tạo
INSERT INTO weekly_comment_revisions (weekly_comment_id, previous_content, previous_sent_at, previous_sent_by, changed_by,
                                      changed_at)
SELECT n.id, w.opening || w.body, pg_temp.d05_at(w.friday, TIME '16:30') + (w.h % 40) * INTERVAL '1 minute',
       w.teacher_id, w.teacher_id, pg_temp.d05_at(w.friday + 3, TIME '08:15')
FROM d05_wc_new n
JOIN d05_wc w ON w.child_id = n.child_id AND w.monday = n.monday
WHERE w.revised;

-- ---------- 11. Mở lại nhận xét tuần theo lớp – tuần, mở lại đánh giá cuối chủ đề ----------
-- Như mục 5: một lần đã hết hạn (HT), một lần kết thúc sớm (PHT Cơ sở 2), một lần còn hiệu lực tới ngày kia (PHT Cơ sở
-- 1). Tuần được mở là tuần đã khóa (trước tuần gần nhất). Mỗi lớp chỉ seed một lần mở lại nên chạy lại không thêm bản ghi.
CREATE TEMP TABLE d05_wc_unlock AS
SELECT c.classroom_id, c.campus_id, v.monday, v.friday, u.kind, u.reason,
       CASE u.kind WHEN 'EXPIRED' THEN (SELECT user_id FROM d05_principal)
                   ELSE (SELECT p.user_id FROM d05_vp p WHERE p.campus_id = c.campus_id) END AS user_id
FROM (VALUES ('CS-A', 2, 3, 'EXPIRED', 'Cô giáo nghỉ phép cả tuần, chưa kịp gửi nhận xét tuần cho phụ huynh.'),
             ('CS-B', 3, 2, 'REVOKED', 'Giáo viên xin bổ sung nhận xét cho trẻ mới chuyển lớp giữa tuần.'),
             ('CS-A', 4, 1, 'ACTIVE', 'Giáo viên dự tập huấn chuyên môn, xin mở lại để gửi nhận xét tuần còn thiếu.'))
     AS u (campus_code, k, back, kind, reason)
JOIN d05_class c ON c.campus_code = u.campus_code AND c.k = u.k
CROSS JOIN LATERAL (
    SELECT w.monday, w.friday FROM d05_week w ORDER BY w.monday DESC OFFSET u.back LIMIT 1
) v;

INSERT INTO weekly_comment_unlocks (classroom_id, campus_id, week_start, valid_until, reason, unlocked_by, unlocked_at,
                                    revoked_by, revoked_at)
SELECT u.classroom_id, u.campus_id, u.monday,
       CASE u.kind WHEN 'EXPIRED' THEN pg_temp.d05_at(u.friday + 5, TIME '17:00')
                   WHEN 'REVOKED' THEN pg_temp.d05_at(u.friday + 4, TIME '11:20')
                   ELSE pg_temp.d05_at(CURRENT_DATE + 2, TIME '17:00') END,
       u.reason, u.user_id,
       CASE WHEN u.kind = 'ACTIVE' THEN LEAST(pg_temp.d05_at(CURRENT_DATE, TIME '07:40'), now() - INTERVAL '5 minutes')
            ELSE pg_temp.d05_at(u.friday + 4, TIME '07:45') END,
       CASE WHEN u.kind = 'REVOKED' THEN u.user_id END,
       CASE WHEN u.kind = 'REVOKED' THEN pg_temp.d05_at(u.friday + 4, TIME '11:20') END
FROM d05_wc_unlock u
WHERE u.user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM weekly_comment_unlocks x WHERE x.classroom_id = u.classroom_id);

-- Đánh giá cuối chủ đề (OBS-27): mỗi điểm trường mở lại chủ đề kết thúc gần nhất của một lớp; period_key = dòng gốc
-- chủ đề. Cơ sở 1 đã hết hạn (HT), Cơ sở 2 còn hiệu lực tới ngày kia (PHT).
CREATE TEMP TABLE d05_ev_unlock AS
SELECT DISTINCT ON (c.campus_code)
       th.classroom_id, th.campus_id, th.lineage, th.end_date, c.campus_code,
       CASE c.campus_code WHEN 'CS-A' THEN (SELECT user_id FROM d05_principal)
                          ELSE (SELECT p.user_id FROM d05_vp p WHERE p.campus_id = th.campus_id) END AS user_id
FROM d05_theme th
JOIN d05_class c ON c.classroom_id = th.classroom_id
WHERE th.teacher_id IS NOT NULL
ORDER BY c.campus_code, th.end_date DESC, c.k, th.lineage;

INSERT INTO evaluation_unlocks (kind, classroom_id, campus_id, period_key, valid_until, reason, unlocked_by, unlocked_at)
SELECT 'THEME', u.classroom_id, u.campus_id, u.lineage::text,
       CASE u.campus_code WHEN 'CS-A' THEN pg_temp.d05_at(u.end_date + 6, TIME '17:00')
                          ELSE pg_temp.d05_at(CURRENT_DATE + 2, TIME '17:00') END,
       CASE u.campus_code WHEN 'CS-A' THEN 'Bổ sung đánh giá cho trẻ nghỉ ốm dài ngày trong chủ đề.'
                          ELSE 'Giáo viên xin chỉnh lại kết quả mục tiêu chấm nhầm của vài trẻ.' END,
       u.user_id,
       CASE u.campus_code WHEN 'CS-A' THEN pg_temp.d05_at(u.end_date + 5, TIME '08:00')
                          ELSE LEAST(pg_temp.d05_at(CURRENT_DATE, TIME '07:50'), now() - INTERVAL '5 minutes') END
FROM d05_ev_unlock u
WHERE u.user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM evaluation_unlocks x WHERE x.classroom_id = u.classroom_id);

-- ---------- Dọn ----------
DROP TABLE d05_lms, d05_year, d05_class, d05_days, d05_att, d05_teach, d05_vp, d05_principal, d05_obs, d05_month,
    d05_eval_src, d05_eval, d05_eval_new, d05_cmc, d05_week, d05_week_stat, d05_rating, d05_unlock, d05_kid,
    d05_exam_camp, d05_exam, d05_exam_row, d05_exam_json, d05_exam_edit, d05_self, d05_meas_camp, d05_meas,
    d05_meas_edit, d05_trend, d05_rows, d05_lesson_week, d05_activity, d05_dar, d05_theme, d05_theme_cell,
    d05_wc, d05_wc_new, d05_wc_unlock, d05_ev_unlock;
DROP FUNCTION pg_temp.d05_h(text), pg_temp.d05_at(date, time), pg_temp.d05_pick(text[], bigint),
    pg_temp.d05_age(date, date), pg_temp.d05_who(text, text, numeric, numeric),
    pg_temp.d05_z(text, text, int, numeric, boolean), pg_temp.d05_teacher(bigint, date);
