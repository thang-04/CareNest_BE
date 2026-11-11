from pathlib import Path
import hashlib
import json
import os
import re
import unicodedata


base = Path(os.environ['TEMP']) / 'carenest014-source-audit'
feature = Path(__file__).resolve().parents[1]
seed_path = feature.parents[2] / 'CareNest_FE' / 'src' / 'mocks' / 'curriculumSeed.js'
pdf = base / 'official.pdf'
expected_hash = '146c1f23b3515d46f2e2e97afcfc0a1d871ef28b6700c7db4fbacfeb56e8572f'
assert hashlib.sha256(pdf.read_bytes()).hexdigest() == expected_hash
raw = seed_path.read_text(encoding='utf-8').split('export const CURRICULUM_SEED = ', 1)[1]
seed = json.loads(raw.strip().removesuffix(';'))
extracted = json.loads((base / 'age-cell-extract.json').read_text(encoding='utf-8'))


def norm(text):
    return re.sub(r'\s+', '', unicodedata.normalize('NFC', text)).casefold()


results = []
for cell in extracted:
    page, age = cell['page'], cell['age']
    if not (14 <= page <= 22 or 40 <= page <= 55):
        continue
    catalog = [norm(item['text']) for item in seed['contents'] if item['ageGroup'] == age]
    # Raw extraction loses page breaks and cell boundaries; results require visual review.
    parts = re.split(r'(?<!\S)[-+]\s+(?=[^\d\s])', cell['text'])
    for part in parts:
        text = re.sub(r'\s+', ' ', part).strip()
        key = norm(text)
        if len(key) < 12 or any(key in item or item in key for item in catalog):
            continue
        results.append({'page': page, 'congBaoPage': page + 12, 'table': cell['table'],
                        'age': age, 'text': text, 'status': 'requires_visual_review'})

out = feature / 'audit' / 'reverse-content-candidates.json'
out.write_text(json.dumps({'pdfSha256': expected_hash, 'candidates': results}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'{len(results)} candidate fragments; each requires page/column and boundary review: {out}')
