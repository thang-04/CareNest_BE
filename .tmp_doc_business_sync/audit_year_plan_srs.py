from pathlib import Path
import sys
from docx import Document
from docx.oxml.ns import qn

SOURCE = Path(r"D:\Downloads\G94-Report-3_Software Requirement Specification_v3 (1).docx")
OUTPUT = Path(r"D:\Downloads\G94-Report-3_Software Requirement Specification_v3 (1).__business.tmp.docx")

sys.stdout.reconfigure(encoding="utf-8")

src = Document(SOURCE)
doc = Document(OUTPUT)

def all_text(d):
    parts = [p.text for p in d.paragraphs]
    for table in d.tables:
        for row in table.rows:
            seen = set()
            for cell in row.cells:
                if id(cell._tc) in seen:
                    continue
                seen.add(id(cell._tc))
                parts.append(cell.text)
    return "\n".join(parts)

def paragraph_index(d, exact):
    return next(i for i, p in enumerate(d.paragraphs) if p.text.strip() == exact)

def next_table_after_heading(d, exact):
    heading = next(p for p in d.paragraphs if p.text.strip() == exact)
    node = heading._p.getnext()
    while node is not None:
        if node.tag == qn("w:tbl"):
            return next(t for t in d.tables if t._tbl is node)
        node = node.getnext()
    raise RuntimeError(exact)

print(f"tables={len(doc.tables)} source_tables={len(src.tables)}")
src_shapes = [len(t.columns) for t in src.tables]
out_shapes = [len(t.columns) for t in doc.tables]
cursor = 0
for shape in out_shapes:
    if cursor < len(src_shapes) and shape == src_shapes[cursor]:
        cursor += 1
print("column_shapes_preserved_as_subsequence=" + str(cursor == len(src_shapes)))

headings = [
    "4.6 Quản lý phiên sửa, lưu tay và khôi phục bản chưa lưu",
    "4.7 Xóa lĩnh vực con trong bản đang soạn",
    "4.1.15 Phiên sửa, lưu tay và khôi phục bản chưa lưu",
    "4.1.16 Xóa lĩnh vực con và toàn bộ nhánh",
]
for h in headings:
    print(f"heading_count[{h}]={sum(p.text.strip() == h for p in doc.paragraphs)}")

biz = next_table_after_heading(doc, "1.6 Educational plan (EDU)")
ids = [row.cells[0].text.strip() for row in biz.rows[1:]]
expected = [f"EDU-{i:02d}" for i in range(1, 30)] + ["EDU-39", "EDU-40", "EDU-41", "EDU-42", "EDU-43", "EDU-46", "EDU-48"]
print(f"business_rows={len(biz.rows)}")
print(f"business_ids_exact={ids == expected}")
print(f"business_ids_unique={len(ids) == len(set(ids))}")
print("business_ids=" + ",".join(ids))

text = all_text(doc)
checks = {
    "old_range": "EDU-26 đến EDU-29",
    "old_export": "Xuất (EDU-14)",
    "old_submit": "UC 4.2, EDU-11",
    "old_issue": "UC 4.3, EDU-12",
    "old_goal_hierarchy": "EDU-03 - Cấu trúc mục tiêu",
    "old_view_scope": "EDU-15 - Phạm vi xem",
    "old_step_button": "bấm Tiếp theo để sang bước 2",
    "old_confirm_step": "chỉ bước Xác nhận mới có nút Xuất",
}
for name, needle in checks.items():
    print(f"contains[{name}]={needle in text}")

blue = "0000FF"
format_errors = []
for h in headings:
    p = next(p for p in doc.paragraphs if p.text.strip() == h)
    for run in p.runs:
        if not run.text.strip():
            continue
        font = run.font.name or run._element.rPr.rFonts.get(qn("w:ascii")) if run._element.rPr is not None and run._element.rPr.rFonts is not None else run.font.name
        size = run.font.size.pt if run.font.size else None
        color = str(run.font.color.rgb) if run.font.color and run.font.color.rgb else None
        if font != "Times New Roman" or size != 11.0 or color != blue:
            format_errors.append((h, run.text, font, size, color))
new_tables = [next_table_after_heading(doc, h) for h in headings]
for table in new_tables:
    for row in table.rows:
        for cell in row.cells:
            for p in cell.paragraphs:
                for run in p.runs:
                    if not run.text.strip():
                        continue
                    font = run.font.name
                    size = run.font.size.pt if run.font.size else None
                    color = str(run.font.color.rgb) if run.font.color and run.font.color.rgb else None
                    if font != "Times New Roman" or size != 11.0 or color != blue:
                        format_errors.append(("new_table", run.text, font, size, color))
print(f"new_content_format_errors={len(format_errors)}")
if format_errors:
    print(format_errors[:20])

business_format_errors = []
for row in biz.rows[1:]:
    for cell in row.cells:
        for p in cell.paragraphs:
            for run in p.runs:
                if not run.text.strip():
                    continue
                font = run.font.name
                size = run.font.size.pt if run.font.size else None
                color = str(run.font.color.rgb) if run.font.color and run.font.color.rgb else None
                if font != "Times New Roman" or size != 11.0 or color != blue:
                    business_format_errors.append((run.text, font, size, color))
print(f"business_format_errors={len(business_format_errors)}")

for heading in ["4. Educational Planning and Review", "4. Kế hoạch giáo dục", "1.6 Educational plan (EDU)"]:
    print(f"paragraph_index[{heading}]={paragraph_index(doc, heading)}")
