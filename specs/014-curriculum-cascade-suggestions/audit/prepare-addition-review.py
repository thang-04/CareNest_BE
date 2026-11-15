from pathlib import Path
import json
import re
import unicodedata


feature = Path(__file__).resolve().parents[1]
audit = feature / 'audit'
seed_path = feature.parents[2] / 'CareNest_FE' / 'src' / 'mocks' / 'curriculumSeed.js'
raw = seed_path.read_text(encoding='utf-8').split('export const CURRICULUM_SEED = ', 1)[1]
seed = json.loads(raw.strip().removesuffix(';'))
candidates = json.loads((audit / 'reverse-content-candidates.json').read_text(encoding='utf-8'))['candidates']
merged = json.loads((audit / 'merged-fragments-results.json').read_text(encoding='utf-8'))
goals = json.loads((audit / 'reverse-goals-review-results.json').read_text(encoding='utf-8'))
ids = [3, 4, 5, 6, 7, *range(19, 39), 40, 41, 42, 43, *range(46, 55),
       56, 57, *range(59, 63), *range(64, 71), *range(72, 76), *range(77, 84)]


def norm(text):
    return re.sub(r'\s+', '', unicodedata.normalize('NFC', text))


items = [{'kind': 'content', 'age': candidates[i]['age'], 'text': candidates[i]['text'],
          'pdfPage': candidates[i]['page']} for i in ids]
items.extend({'kind': 'content', 'age': item['age'], 'text': item['text'], 'pdfPage': item['pdfPage']}
             for item in merged if item['status'] == 'missing')
items.extend({'kind': 'goal', 'age': item['age'], 'text': item['fragment'], 'pdfPage': item['page'],
              'goalNo': item['goalNo']} for item in goals if item['status'] == 'missing_after_visual_review')
assert len(items) == 73 and len({(i['kind'], i['age'], i['text']) for i in items}) == 73
for item in items:
    source = seed['contents'] if item['kind'] == 'content' else seed['goals']
    text_field = 'text' if item['kind'] == 'content' else 'goalText'
    same = [row for row in source if row['ageGroup'] != item['age'] and norm(row[text_field]) == norm(item['text'])]
    item['candidateSources'] = [{k: row.get(k) for k in ('id', 'ageGroup', 'domainName', 'subDomain',
                                'categoryNo', 'category', 'groupNo', 'groupText', 'goalNo', 'sectionRef', 'sortOrder')}
                               for row in same]
    item['requiresParentReview'] = len(same) != 1

out = audit / 'addition-impact-review.json'
out.write_text(json.dumps(items, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f"{len(items)} verified additions, {sum(i['requiresParentReview'] for i in items)} with zero or multiple possible source rows: {out}")
