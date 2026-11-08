from pathlib import Path
import json
import os
import re
import unicodedata


feature = Path(__file__).resolve().parents[1]
audit = feature / 'audit'
items = json.loads((audit / 'addition-placement-review.json').read_text(encoding='utf-8'))
cells = json.loads((Path(os.environ['TEMP']) / 'carenest014-source-audit' / 'age-cell-extract.json').read_text(encoding='utf-8'))
raw = (feature.parents[2] / 'CareNest_FE/src/mocks/curriculumSeed.js').read_text(encoding='utf-8')
seed = json.loads(raw.split('export const CURRICULUM_SEED = ', 1)[1].strip().removesuffix(';'))


def norm(text):
    return re.sub(r'\s+', '', unicodedata.normalize('NFC', text)).casefold()


for index, item in enumerate(items):
    if item['kind'] != 'content':
        continue
    candidates = []
    for cell in cells:
        if cell['page'] != item['pdfPage'] or cell['age'] != item['age']:
            continue
        page = norm(cell['text'])
        pos = page.find(norm(item['text']))
        if pos < 0:
            continue
        anchors = []
        for row in seed['contents']:
            key = norm(row['text'])
            if row['ageGroup'] != item['age'] or len(key) < 16:
                continue
            start = page.find(key)
            if start >= 0:
                anchors.append((start, row['id'], row['sortOrder']))
        prior = [a for a in anchors if a[0] < pos]
        later = [a for a in anchors if a[0] > pos]
        before = max(prior, default=None)
        after = min(later, default=None)
        conflict = bool(before and after and before[2] >= after[2])
        candidates.append({'table': cell['table'], 'offset': pos, 'previous': before,
                           'next': after, 'conflict': conflict})
    item['pdfCandidates'] = candidates

out = audit / 'addition-pdf-placement-review.json'
out.write_text(json.dumps(items, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
missing = [i for i in items if i['kind'] == 'content' and not any(c['previous'] or c['next'] for c in i['pdfCandidates'])]
conflicts = [i for i in items if i['kind'] == 'content' and any(c['conflict'] for c in i['pdfCandidates'])]
print(f"{len(missing)} content items have no same-page anchor; {len(conflicts)} have conflicting PDF order: {out}")
