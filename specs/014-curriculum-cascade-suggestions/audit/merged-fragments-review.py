from pathlib import Path
import json
import re
import unicodedata


feature = Path(__file__).resolve().parents[1]
seed_path = feature.parents[2] / 'CareNest_FE' / 'src' / 'mocks' / 'curriculumSeed.js'
raw = seed_path.read_text(encoding='utf-8').split('export const CURRICULUM_SEED = ', 1)[1]
seed = json.loads(raw.strip().removesuffix(';'))

# Boundaries and age spans checked against the rendered official PDF pages.
items = [
    (46, ['ag-4', 'ag-5'], 'Nguyên nhân gây ô nhiễm nguồn nước và cách bảo vệ nguồn nước.'),
    (46, ['ag-4', 'ag-5'], 'Không khí, các nguồn ánh sáng và sự cần thiết của nó với cuộc sống con người, con vật và cây.'),
    (46, ['ag-3', 'ag-4', 'ag-5'], 'Một vài đặc điểm, tính chất của đất, đá, cát, sỏi.'),
    (49, ['ag-4', 'ag-5'], 'Tên gọi, công cụ, sản phẩm, các hoạt động và ý nghĩa của các nghề phổ biến, nghề truyền thống của địa phương.'),
    (49, ['ag-4', 'ag-5'], 'Đặc điểm nổi bật của một số di tích, danh lam, thắng cảnh, ngày lễ hội, sự kiện văn hóa của quê hương, đất nước.'),
    (53, ['ag-4', 'ag-5'], 'Yêu mến, quan tâm đến người thân trong gia đình.'),
]


def norm(text):
    return re.sub(r'\s+', '', unicodedata.normalize('NFC', text)).casefold()


results = []
for page, ages, text in items:
    for age in ages:
        matches = [c for c in seed['contents'] if c['ageGroup'] == age and norm(c['text']) == norm(text)]
        results.append({'pdfPage': page, 'congBaoPage': page + 12, 'age': age, 'text': text,
                        'status': 'present' if matches else 'missing',
                        'matchingIds': [c['id'] for c in matches]})

out = feature / 'audit' / 'merged-fragments-results.json'
out.write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f"{len(results)} pairs checked; {sum(r['status'] == 'missing' for r in results)} missing: {out}")
