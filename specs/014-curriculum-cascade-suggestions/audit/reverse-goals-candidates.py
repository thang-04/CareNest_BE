from pathlib import Path
import hashlib
import json
import os
import re
import unicodedata


base = Path(os.environ['TEMP']) / 'carenest014-source-audit'
feature = Path(__file__).resolve().parents[1]
pdf_hash = '146c1f23b3515d46f2e2e97afcfc0a1d871ef28b6700c7db4fbacfeb56e8572f'
assert hashlib.sha256((base / 'official.pdf').read_bytes()).hexdigest() == pdf_hash
seed_path = feature.parents[2] / 'CareNest_FE' / 'src' / 'mocks' / 'curriculumSeed.js'
raw = seed_path.read_text(encoding='utf-8').split('export const CURRICULUM_SEED = ', 1)[1]
seed = json.loads(raw.strip().removesuffix(';'))
cells = json.loads((base / 'age-cell-extract.json').read_text(encoding='utf-8'))


def norm(text):
    return re.sub(r'\s+', '', unicodedata.normalize('NFC', text)).casefold()


results = []
pattern = re.compile(r'(?<!\d)(\d{1,2}\.\d{1,2})\.\s+')
for cell in cells:
    page, age, text = cell['page'], cell['age'], cell['text']
    if not (23 <= page <= 30 or 56 <= page <= 77):
        continue
    numbers = list(pattern.finditer(text))
    for i, number in enumerate(numbers):
        end = numbers[i + 1].start() if i + 1 < len(numbers) else len(text)
        segment = re.sub(r'\s+', ' ', text[number.end():end]).strip()
        # Bullets belong to a goal, but splitting the first bullet is only a heuristic.
        goal_fragment = re.split(r'(?<!\S)-\s+(?=[^\d\s])', segment, maxsplit=1)[0].strip()
        key = norm(goal_fragment)
        catalog = [g for g in seed['goals'] if g['ageGroup'] == age and g['goalNo'] == number.group(1)]
        matches = [g for g in catalog if norm(g['goalText']) in key or key in norm(g['goalText'])]
        if len(key) >= 12 and not matches:
            results.append({'page': page, 'congBaoPage': page + 12, 'table': cell['table'],
                            'age': age, 'goalNo': number.group(1), 'fragment': goal_fragment,
                            'status': 'requires_visual_review'})

out = feature / 'audit' / 'reverse-goals-candidates.json'
out.write_text(json.dumps({'pdfSha256': pdf_hash, 'candidates': results}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'{len(results)} goal fragments require visual review: {out}')
