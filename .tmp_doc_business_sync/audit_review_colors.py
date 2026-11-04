from pathlib import Path
import sys

from docx import Document
from docx.oxml.ns import qn
from docx.table import Table


sys.stdout.reconfigure(encoding="utf-8")
TARGET = Path(r"D:\Downloads\G94-Report-3_Software Requirement Specification_v3 (1).docx")
doc = Document(TARGET)


def paragraph_exact(text):
    return next(p for p in doc.paragraphs if p.text.strip() == text)


def table_after(paragraph):
    node = paragraph._p.getnext()
    while node is not None:
        if node.tag == qn("w:tbl"):
            return Table(node, doc._body)
        node = node.getnext()
    raise RuntimeError(paragraph.text)


def colors_of_paragraph(paragraph):
    return {
        str(run.font.color.rgb) if run.font.color and run.font.color.rgb else None
        for run in paragraph.runs if run.text.strip()
    }


def colors_of_cell(cell):
    return set().union(*(colors_of_paragraph(p) for p in cell.paragraphs))


uc_headings = [
    "4.1 Lấy mục tiêu từ nguồn có sẵn",
    "4.2 Gửi kế hoạch mục tiêu năm học để duyệt",
    "4.3 Ban hành hoặc trả lại kế hoạch năm học",
    "4.4 Tạo phiên bản điều chỉnh của kế hoạch đã ban hành",
    "4.5 Nhập file kế hoạch năm học",
    "4.6 Quản lý phiên sửa, lưu tay và khôi phục bản chưa lưu",
    "4.7 Xóa lĩnh vực con trong bản đang soạn",
]

errors = []
for heading in uc_headings:
    p = paragraph_exact(heading)
    if colors_of_paragraph(p) != {"000000"}:
        errors.append((heading, "heading", colors_of_paragraph(p)))
    table = table_after(p)
    label_cells = [table.rows[0].cells[0], table.rows[0].cells[2]] + [r.cells[0] for r in table.rows[1:]]
    if any(colors_of_cell(c) != {"000000"} for c in label_cells):
        errors.append((heading, "labels", [colors_of_cell(c) for c in label_cells]))
    content_cells = [table.rows[0].cells[1], table.rows[0].cells[3]] + [r.cells[1] for r in table.rows[1:]]
    if any(colors_of_cell(c) != {"0000FF"} for c in content_cells):
        errors.append((heading, "content", [colors_of_cell(c) for c in content_cells]))

new_functional = [
    "4.1.15 Phiên sửa, lưu tay và khôi phục bản chưa lưu",
    "4.1.16 Xóa lĩnh vực con và toàn bộ nhánh",
]
for heading in new_functional:
    p = paragraph_exact(heading)
    if colors_of_paragraph(p) != {"000000"}:
        errors.append((heading, "heading", colors_of_paragraph(p)))
    table = table_after(p)
    for index, row in enumerate(table.rows):
        if colors_of_cell(row.cells[0]) != {"000000"}:
            errors.append((heading, f"row{index}-label", colors_of_cell(row.cells[0])))
        expected = "000000" if index == 0 else "0000FF"
        if colors_of_cell(row.cells[1]) != {expected}:
            errors.append((heading, f"row{index}-description", colors_of_cell(row.cells[1])))

business = table_after(paragraph_exact("1.6 Educational plan (EDU)"))
if any(colors_of_cell(c) != {"000000"} for c in business.rows[0].cells):
    errors.append(("business", "header", [colors_of_cell(c) for c in business.rows[0].cells]))
if any(colors_of_cell(c) != {"0000FF"} for row in business.rows[1:] for c in row.cells):
    errors.append(("business", "changed rows", "not all blue"))

print(f"tables={len(doc.tables)}")
print(f"inline_shapes={len(doc.inline_shapes)}")
print(f"review_color_errors={len(errors)}")
for error in errors[:20]:
    print(error)
