from pathlib import Path
import pymupdf,os,json,re,unicodedata,sys
sys.stdout.reconfigure(encoding='utf-8')
base=Path(os.environ['TEMP'])/'carenest014-source-audit'
doc=pymupdf.open(base/'official.pdf')
entries=[]
for pi,page in enumerate(doc):
 pg=pi+1
 if not(14<=pg<=31 or 40<=pg<=77):continue
 vertical=[]
 for drawing in page.get_drawings():
  for item in drawing['items']:
   if item[0]=='re':
    rect=item[1]
    if rect.width<2 and rect.height>3:vertical.append(rect)
 for ti,table in enumerate(page.find_tables().tables):
  nursery=pg<32
  if table.col_count<3 or (not nursery and table.col_count!=4):continue
  choices=[('ag-2',table.col_count-1)] if nursery else [('ag-3',1),('ag-4',2),('ag-5',3)]
  headers=table.extract()[0];scopes=[]
  for age,ci in choices:
   cell=table.rows[0].cells[ci]
   if cell is None:continue
   x0,y0,x1,y1=cell;scopes.append((age,(x0+x1)/2,y1))
  mine={age:[] for age,_,_ in scopes}
  evidence={age:[] for age,_,_ in scopes}
  for block in page.get_text('dict')['blocks']:
   for line in block.get('lines',[]):
    bbox=pymupdf.Rect(line['bbox']);yc=(bbox.y0+bbox.y1)/2
    if yc<table.bbox[1] or yc>table.bbox[3] or bbox.x0<table.bbox[0]-1 or bbox.x1>table.bbox[2]+1:continue
    borders=sorted(set(round((r.x0+r.x1)/2,2) for r in vertical if r.y0-1<=yc<=r.y1+1))
    left=max([x for x in borders if x<=bbox.x0+.5],default=table.bbox[0])
    right=min([x for x in borders if x>bbox.x0+2],default=table.bbox[2])
    text=''.join(re.sub(r'\d+','',sp['text']) if sp['flags']&1 else sp['text'] for sp in line['spans'])
    for age,center,top in scopes:
     if yc>top and left<center<right:
      mine[age].append((round(bbox.y0,1),bbox.x0,text))
      evidence[age].append({'bbox':list(bbox),'cellX':[left,right],'text':text})
  for age in mine:
   entries.append({'page':pg,'table':ti,'age':age,'header':headers,'text':' '.join(t for _,_,t in sorted(mine[age])),'lines':evidence[age]})
base.joinpath('age-cell-extract.json').write_text(json.dumps(entries,ensure_ascii=False,indent=2),encoding='utf-8')
n=lambda s:re.sub(r'\s+','',unicodedata.normalize('NFC',s))
seed=Path('D:/UNIVERSITY_FPT/DOAN/UI-giaoan/CareNest_BE/src/main/resources/db/migration/V34__seed_edu_curriculum_vbhn_01_2021.sql')
rows=[]
for lineno,line in enumerate(seed.read_text(encoding='utf-8').splitlines(),1):
 vals=[v.replace("''","'") for v in re.findall(r"'((?:[^']|'')*)'",line)]
 if line.startswith('INSERT INTO edu_curriculum_goals '):rows.append(dict(kind='goal',line=lineno,age=vals[1],text=vals[-2],ref=vals[-1]))
 elif line.startswith('INSERT INTO edu_curriculum_goal_contents '):rows.append(dict(kind='contentD',line=lineno,age=vals[1],text=vals[-1],parentSortOrder=int(re.search(r'AND sort_order = (\d+)\)',line).group(1))))
 elif line.startswith('INSERT INTO edu_curriculum_contents '):rows.append(dict(kind='contentC',line=lineno,age=vals[1],text=vals[-2],ref=vals[-1]))
results=[]
for row in rows:
 if row['kind']=='group':continue
 lo,hi=((14,22) if row['kind']=='contentC' else (23,30)) if row['age']=='ag-2' else ((40,55) if row['kind']=='contentC' else (56,77))
 matches=[dict(page=e['page'],table=e['table']) for e in entries if e['age']==row['age'] and lo<=e['page']<=hi and n(row['text']) in n(e['text'])]
 results.append(dict(row,match=matches))
base.joinpath('age-cell-audit.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
for kind in ['goal','contentD','contentC']:
 rs=[r for r in results if r['kind']==kind];missing=[r for r in rs if not r['match']]
 print(kind,len(rs),'matched',len(rs)-len(missing),'unmatched',len(missing))
 for r in missing:print(json.dumps(r,ensure_ascii=False))
