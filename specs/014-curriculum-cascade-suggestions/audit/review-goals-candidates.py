from pathlib import Path
import json
import re
import unicodedata


feature = Path(__file__).resolve().parents[1]
seed_path = feature.parents[2] / 'CareNest_FE' / 'src' / 'mocks' / 'curriculumSeed.js'
raw = seed_path.read_text(encoding='utf-8').split('export const CURRICULUM_SEED = ', 1)[1]
seed = json.loads(raw.strip().removesuffix(';'))
source = feature / 'audit' / 'reverse-goals-candidates.json'
candidates = json.loads(source.read_text(encoding='utf-8'))['candidates']


def norm(text):
    return re.sub(r'\s+', '', unicodedata.normalize('NFC', text)).casefold()


results = []
for index in [0, 3, 4, 5]:
    item = candidates[index]
    assert item['age'] == 'ag-5'
    matches = [g for g in seed['goals'] if norm(g['goalText']) == norm(item['fragment'])]
    assert any(g['ageGroup'] == 'ag-4' for g in matches)
    assert not any(g['ageGroup'] == 'ag-5' for g in matches)
    results.append({**item, 'index': index, 'status': 'missing_after_visual_review',
                    'sameTextInOtherAges': [{'age': g['ageGroup'], 'id': g['id'], 'ref': g['sectionRef']} for g in matches]})

for index in [1, 2]:
    item = candidates[index]
    results.append({**item, 'index': index, 'status': 'wrong_age_column_extraction',
                    'actualAge': 'ag-4' if index == 1 else 'ag-5'})

out = feature / 'audit' / 'reverse-goals-review-results.json'
out.write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f"4 missing, 2 extraction artifacts after visual review: {out}")
