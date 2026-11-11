-- VBHN 01/VBHN-BGDDT: preserve source punctuation and repair a split sentence.
CREATE TEMP TABLE audit014_expected(age_code text, sort_order integer, old_text text, new_text text) ON COMMIT DROP;
INSERT INTO audit014_expected VALUES
    ('ag-2', '28', 'Sử dụng lời nói với các mục đích khác nhau', 'Sử dụng lời nói với các mục đích khác nhau:'),
    ('ag-3', '2', 'Giữ được thăng bằng cơ thể khi thực hiện vận động', 'Giữ được thăng bằng cơ thể khi thực hiện vận động:'),
    ('ag-3', '3', 'Kiểm soát được vận động', 'Kiểm soát được vận động:'),
    ('ag-3', '4', 'Phối hợp tay - mắt trong vận động', 'Phối hợp tay - mắt trong vận động:'),
    ('ag-3', '5', 'Thể hiện nhanh, mạnh, khéo trong thực hiện bài tập tổng hợp', 'Thể hiện nhanh, mạnh, khéo trong thực hiện bài tập tổng hợp:'),
    ('ag-3', '6', 'Thực hiện được các vận động', 'Thực hiện được các vận động:'),
    ('ag-3', '7', 'Phối hợp được cử động bàn tay, ngón tay trong một số hoạt động', 'Phối hợp được cử động bàn tay, ngón tay trong một số hoạt động:'),
    ('ag-4', '2', 'Giữ được thăng bằng cơ thể khi thực hiện vận động', 'Giữ được thăng bằng cơ thể khi thực hiện vận động:'),
    ('ag-4', '4', 'Phối hợp tay- mắt trong vận động', 'Phối hợp tay- mắt trong vận động:'),
    ('ag-4', '5', 'Thể hiện nhanh, mạnh, khéo trong thực hiện bài tập tổng hợp', 'Thể hiện nhanh, mạnh, khéo trong thực hiện bài tập tổng hợp:'),
    ('ag-4', '6', 'Thực hiện được các vận động', 'Thực hiện được các vận động:'),
    ('ag-4', '7', 'Phối hợp được cử động bàn tay, ngón tay, phối hợp tay - mắt trong một số hoạt động', 'Phối hợp được cử động bàn tay, ngón tay, phối hợp tay - mắt trong một số hoạt động:'),
    ('ag-5', '2', 'Giữ được thăng bằng cơ thể khi thực hiện vận động', 'Giữ được thăng bằng cơ thể khi thực hiện vận động:'),
    ('ag-5', '4', 'Phối hợp tay - mắt trong vận động', 'Phối hợp tay - mắt trong vận động:'),
    ('ag-5', '5', 'Thể hiện nhanh, mạnh, khéo trong thực hiện bài tập tổng hợp', 'Thể hiện nhanh, mạnh, khéo trong thực hiện bài tập tổng hợp:'),
    ('ag-5', '6', 'Thực hiện được các vận động', 'Thực hiện được các vận động:'),
    ('ag-5', '7', 'Phối hợp được cử động bàn tay, ngón tay, phối hợp tay - mắt trong một số hoạt động', 'Phối hợp được cử động bàn tay, ngón tay, phối hợp tay - mắt trong một số hoạt động:'),
    ('ag-3', '11', 'Thực hiện được một số việc đơn giản với sự giúp đỡ của người lớn', 'Thực hiện được một số việc đơn giản với sự giúp đỡ của người lớn:'),
    ('ag-3', '14', 'Có một số hành vi tốt trong vệ sinh, phòng bệnh khi được nhắc nhở', 'Có một số hành vi tốt trong vệ sinh, phòng bệnh khi được nhắc nhở:'),
    ('ag-3', '17', 'Biết tránh một số hành động nguy hiểm khi được nhắc nhở', 'Biết tránh một số hành động nguy hiểm khi được nhắc nhở:'),
    ('ag-4', '8', 'Biết một số thực phẩm cùng nhóm', 'Biết một số thực phẩm cùng nhóm:'),
    ('ag-4', '11', 'Thực hiện được một số việc khi được nhắc nhở', 'Thực hiện được một số việc khi được nhắc nhở:'),
    ('ag-4', '13', 'Có một số hành vi tốt trong ăn uống', 'Có một số hành vi tốt trong ăn uống:'),
    ('ag-4', '14', 'Có một số hành vi tốt trong vệ sinh, phòng bệnh khi được nhắc nhở', 'Có một số hành vi tốt trong vệ sinh, phòng bệnh khi được nhắc nhở:'),
    ('ag-4', '17', 'Biết một số hành động nguy hiểm và phòng tránh khi được nhắc nhở', 'Biết một số hành động nguy hiểm và phòng tránh khi được nhắc nhở:'),
    ('ag-4', '18', 'Nhận ra một số trường hợp nguy hiểm và gọi người giúp đỡ', 'Nhận ra một số trường hợp nguy hiểm và gọi người giúp đỡ:'),
    ('ag-5', '8', 'Lựa chọn được một số thực phẩm khi được gọi tên nhóm', 'Lựa chọn được một số thực phẩm khi được gọi tên nhóm:'),
    ('ag-5', '10', 'Thực hiện được một số việc đơn giản', 'Thực hiện được một số việc đơn giản:'),
    ('ag-5', '12', 'Có một số hành vi và thói quen tốt trong ăn uống', 'Có một số hành vi và thói quen tốt trong ăn uống:'),
    ('ag-5', '13', 'Có một số hành vi và thói quen tốt trong vệ sinh, phòng bệnh', 'Có một số hành vi và thói quen tốt trong vệ sinh, phòng bệnh:'),
    ('ag-5', '16', 'Nhận biết được nguy cơ không an toàn khi ăn uống và phòng tránh', 'Nhận biết được nguy cơ không an toàn khi ăn uống và phòng tránh:'),
    ('ag-5', '17', 'Nhận biết được một số trường hợp không an toàn và gọi người giúp đỡ', 'Nhận biết được một số trường hợp không an toàn và gọi người giúp đỡ:'),
    ('ag-5', '18', 'Thực hiện một số quy định ở trường, nơi công cộng về an toàn', 'Thực hiện một số quy định ở trường, nơi công cộng về an toàn:');
DO $$
DECLARE n integer;
BEGIN
 SELECT count(*) INTO n
 FROM audit014_expected e
 JOIN age_groups a ON a.code=e.age_code
 JOIN edu_curriculum_goals g ON g.age_group_id=a.id AND g.sort_order=e.sort_order
 JOIN edu_curriculum_sources s ON s.id=g.source_id AND s.code='VBHN-01-2021-GDMN'
 WHERE g.goal_text=e.old_text;
 IF n<>33 THEN RAISE EXCEPTION 'Expected 33 matching unchanged source rows, found %',n; END IF;
 SELECT count(*) INTO n
 FROM edu_curriculum_goals g
 JOIN age_groups a ON a.id=g.age_group_id AND a.code='ag-2'
 JOIN edu_curriculum_sources s ON s.id=g.source_id AND s.code='VBHN-01-2021-GDMN'
 JOIN edu_curriculum_goal_contents c ON c.curriculum_goal_id=g.id
 WHERE g.sort_order=27 AND g.goal_text='Nói được câu đơn, câu có 5'
   AND c.sort_order=0 AND c.text='7 tiếng, có các từ thông dụng chỉ sự vật, hoạt động, đặc điểm quen thuộc.';
 IF n<>1 THEN RAISE EXCEPTION 'Expected one unchanged split range, found %',n; END IF;
END $$;
UPDATE edu_curriculum_goals g SET goal_text=e.new_text
FROM audit014_expected e, age_groups a, edu_curriculum_sources s
WHERE a.code=e.age_code AND s.code='VBHN-01-2021-GDMN'
  AND g.source_id=s.id AND g.age_group_id=a.id AND g.sort_order=e.sort_order AND g.goal_text=e.old_text;
DELETE FROM edu_curriculum_goal_contents c USING edu_curriculum_goals g,age_groups a,edu_curriculum_sources s
WHERE c.curriculum_goal_id=g.id AND g.age_group_id=a.id AND a.code='ag-2'
  AND g.source_id=s.id AND s.code='VBHN-01-2021-GDMN' AND g.sort_order=27
  AND c.sort_order=0 AND c.text='7 tiếng, có các từ thông dụng chỉ sự vật, hoạt động, đặc điểm quen thuộc.';
UPDATE edu_curriculum_goals g SET goal_text='Nói được câu đơn, câu có 5 - 7 tiếng, có các từ thông dụng chỉ sự vật, hoạt động, đặc điểm quen thuộc.'
FROM age_groups a,edu_curriculum_sources s
WHERE g.age_group_id=a.id AND a.code='ag-2' AND g.source_id=s.id AND s.code='VBHN-01-2021-GDMN'
  AND g.sort_order=27 AND g.goal_text='Nói được câu đơn, câu có 5';
