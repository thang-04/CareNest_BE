from pathlib import Path
import json
import re
import unicodedata


feature = Path(__file__).resolve().parents[1]
audit = feature / 'audit'
raw = (feature.parents[2] / 'CareNest_FE/src/mocks/curriculumSeed.js').read_text(encoding='utf-8')
seed = json.loads(raw.split('export const CURRICULUM_SEED = ', 1)[1].strip().removesuffix(';'))
items = json.loads((audit / 'addition-impact-review.json').read_text(encoding='utf-8'))


def norm(text):
    return re.sub(r'\s+', '', unicodedata.normalize('NFC', text))


for index, item in enumerate(items):
    kind, age = item['kind'], item['age']
    rows = seed['contents'] if kind == 'content' else seed['goals']
    key = 'text' if kind == 'content' else 'goalText'
    candidates = item['candidateSources']
    if index < 5:
        item['manualAnchor'] = [35, 35, 53, 53, 56][index]
        item['manualPosition'] = 'before'
        continue
    if index == 49:
        candidates = [c for c in candidates if c['ageGroup'] == 'ag-4']
    if index == 72:
        candidates = [c for c in candidates if c['ageGroup'] == 'ag-4']
    assert len(candidates) == 1, (index, len(candidates))
    source = next(r for r in rows if r['id'] == candidates[0]['id'])
    item['sourceId'] = source['id']
    item['sourceAge'] = source['ageGroup']
    if kind == 'goal':
        target_section = ' › '.join(source['sectionRef'].split(' › ')[:-1])
        target = [r for r in rows if r['ageGroup'] == age and r['sectionRef'].startswith(target_section + ' › ')]
        following = [r for r in target if r['goalNo'] and float(r['goalNo']) > float(source['goalNo'])]
        previous = [r for r in target if r['goalNo'] and float(r['goalNo']) < float(source['goalNo'])]
    else:
        related = [r for r in rows if r['ageGroup'] == source['ageGroup'] and r['sectionRef'] == source['sectionRef']]
        target = [r for r in rows if r['ageGroup'] == age and r['sectionRef'] == source['sectionRef']]
        pairs = [(r, t) for r in related for t in target if norm(r[key]) == norm(t[key])]
        following = [t for r, t in pairs if r['sortOrder'] > source['sortOrder']]
        previous = [t for r, t in pairs if r['sortOrder'] < source['sortOrder']]
    if following:
        item['anchor'] = min(following, key=lambda x: x['sortOrder'])['id']
        item['position'] = 'before'
    elif previous:
        item['anchor'] = max(previous, key=lambda x: x['sortOrder'])['id']
        item['position'] = 'after'
    else:
        item['needsPlacementReview'] = True

out = audit / 'addition-placement-review.json'
out.write_text(json.dumps(items, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f"{len(items)} items; {sum(i.get('needsPlacementReview', False) for i in items)} need placement review: {out}")
