INSERT INTO edu_plan_defaults (key, value) VALUES
('config_reset', '{
  "domainLabels": {
    "Phát triển thẩm mỹ": "Phát triển thẩm mỹ (Nghệ thuật)"
  },
  "extraDomains": [],
  "workflowPolicy": {
    "schoolYear": {
      "creators": [
        "VICE_PRINCIPAL"
      ],
      "reviewers": [
        "PRINCIPAL"
      ]
    },
    "year": {
      "creators": [
        "TEAM_LEADER"
      ],
      "reviewers": [
        "VICE_PRINCIPAL"
      ]
    },
    "branch": {
      "creators": [
        "TEACHER"
      ],
      "reviewers": [
        "TEAM_LEADER",
        "VICE_PRINCIPAL"
      ]
    },
    "theme": {
      "creators": [
        "TEACHER"
      ],
      "reviewers": [
        "TEAM_LEADER",
        "VICE_PRINCIPAL"
      ]
    }
  },
  "durationPolicy": {
    "theme": {
      "minWeeks": 2,
      "maxWeeks": 4
    },
    "branch": {
      "minWeeks": 1,
      "maxWeeks": 2
    }
  },
  "weekRows": [
    {
      "key": "don",
      "label": "Đón trẻ",
      "canMerge": true,
      "allWeek": true
    },
    {
      "key": "tro_chuyen",
      "label": "Trò chuyện",
      "canMerge": true,
      "allWeek": true
    },
    {
      "key": "the_duc",
      "label": "Thể dục sáng",
      "canMerge": true,
      "allWeek": true
    },
    {
      "key": "hoc",
      "label": "Hoạt động học",
      "canMerge": false,
      "allWeek": false
    },
    {
      "key": "goc",
      "label": "Hoạt động góc",
      "canMerge": true,
      "allWeek": true
    },
    {
      "key": "ngoai_troi",
      "label": "Hoạt động ngoài trời",
      "canMerge": false,
      "allWeek": false,
      "extraLabel": "Chơi tự do (cả tuần)"
    },
    {
      "key": "an_ngu",
      "label": "Ăn, ngủ",
      "canMerge": true,
      "allWeek": true
    },
    {
      "key": "chieu",
      "label": "Hoạt động chiều",
      "canMerge": false,
      "allWeek": false,
      "extraLabel": "Chơi tự chọn; nêu gương cuối ngày (cả tuần)"
    },
    {
      "key": "tra_tre",
      "label": "Trả trẻ",
      "canMerge": true,
      "allWeek": true
    }
  ]
}'::jsonb),
('day_template', '{
  "DAY_SECTIONS": [
    {
      "key": "hoc",
      "row": "hoc",
      "label": "1. Hoạt động học"
    },
    {
      "key": "ngoai_troi",
      "row": "ngoai_troi",
      "label": "2. Hoạt động ngoài trời"
    },
    {
      "key": "goc",
      "row": "goc",
      "label": "3. Hoạt động góc"
    },
    {
      "key": "chieu",
      "row": "chieu",
      "label": "4. Hoạt động chiều"
    }
  ],
  "DAY_REVIEW": [
    {
      "key": "health",
      "label": "1. Tình trạng sức khỏe của trẻ"
    },
    {
      "key": "knowledge",
      "label": "2. Kiến thức, kỹ năng, nhận thức của trẻ"
    },
    {
      "key": "attitude",
      "label": "3. Thái độ, cảm xúc, hành vi của trẻ"
    }
  ],
  "PREP_TARGETS": [
    {
      "key": "teacher",
      "label": "1. Đối với cô"
    },
    {
      "key": "child",
      "label": "2. Đối với trẻ"
    },
    {
      "key": "parent",
      "label": "3. Đối với phụ huynh"
    }
  ],
  "HOC_SUGGESTED_ROWS": [
    "Ổn định tổ chức, gây hứng thú",
    "Nội dung trọng tâm",
    "Kết thúc"
  ],
  "NGOAI_TROI_SUGGESTED_ROWS": [
    "Hoạt động có mục đích",
    "Trò chơi vận động",
    "Chơi theo ý thích"
  ]
}'::jsonb)
ON CONFLICT (key) DO NOTHING;
