from pathlib import Path

from docx import Document
from docx.oxml.ns import qn
from docx.table import Table


TARGET = Path(r"D:\Downloads\G94-Report-3_Software Requirement Specification_v3 (1).docx")
TEMP = TARGET.with_name(TARGET.stem + ".__review_colors.tmp.docx")

BLACK = "000000"


def set_run_black(run):
    run.font.color.rgb = None
    color = run._element.get_or_add_rPr().get_or_add_color()
    color.set(qn("w:val"), BLACK)
    if qn("w:themeColor") in color.attrib:
        del color.attrib[qn("w:themeColor")]


def set_paragraph_black(paragraph):
    for run in paragraph.runs:
        set_run_black(run)


def set_cell_black(cell):
    for paragraph in cell.paragraphs:
        set_paragraph_black(paragraph)


def paragraph_exact(doc, text):
    return next(p for p in doc.paragraphs if p.text.strip() == text)


def table_after(paragraph, doc):
    node = paragraph._p.getnext()
    while node is not None:
        if node.tag == qn("w:tbl"):
            return Table(node, doc._body)
        node = node.getnext()
    raise RuntimeError(f"Không tìm thấy bảng sau: {paragraph.text}")


doc = Document(TARGET)

# Tiêu đề và nhãn cấu trúc là thành phần cố định, không dùng màu review.
use_case_headings = [
    "4. Educational Planning and Review",
    "4.1 Lấy mục tiêu từ nguồn có sẵn",
    "4.2 Gửi kế hoạch mục tiêu năm học để duyệt",
    "4.3 Ban hành hoặc trả lại kế hoạch năm học",
    "4.4 Tạo phiên bản điều chỉnh của kế hoạch đã ban hành",
    "4.5 Nhập file kế hoạch năm học",
    "4.6 Quản lý phiên sửa, lưu tay và khôi phục bản chưa lưu",
    "4.7 Xóa lĩnh vực con trong bản đang soạn",
]
for heading in use_case_headings:
    paragraph = paragraph_exact(doc, heading)
    set_paragraph_black(paragraph)
    if heading.startswith("4.") and heading != "4. Educational Planning and Review":
        table = table_after(paragraph, doc)
        set_cell_black(table.rows[0].cells[0])
        set_cell_black(table.rows[0].cells[2])
        for row in table.rows[1:]:
            set_cell_black(row.cells[0])

functional_headings = [
    "4. Kế hoạch giáo dục",
    "4.1 Mục tiêu năm học",
    "4.1.1 Danh sách kế hoạch năm học",
    "4.1.2 Lập kế hoạch năm học – Thông tin chung",
    "4.1.3 Lập kế hoạch năm học – Mục tiêu, nội dung giáo dục",
    "4.1.4 Lập kế hoạch năm học – Chủ đề và sự kiện",
    "4.1.5 Lưu, gửi duyệt, ban hành và xuất file",
    "4.1.6 Chi tiết kế hoạch năm học",
    "4.1.14 Nhập file kế hoạch năm học",
    "4.1.15 Phiên sửa, lưu tay và khôi phục bản chưa lưu",
    "4.1.16 Xóa lĩnh vực con và toàn bộ nhánh",
]
for heading in functional_headings:
    set_paragraph_black(paragraph_exact(doc, heading))

# Trong bảng mô tả giao diện, tên trường/cột là cố định; phần mô tả thay đổi vẫn giữ màu xanh.
functional_table_headings = [
    "4.1.1 Danh sách kế hoạch năm học",
    "4.1.2 Lập kế hoạch năm học – Thông tin chung",
    "4.1.3 Lập kế hoạch năm học – Mục tiêu, nội dung giáo dục",
    "4.1.4 Lập kế hoạch năm học – Chủ đề và sự kiện",
    "4.1.5 Lưu, gửi duyệt, ban hành và xuất file",
    "4.1.6 Chi tiết kế hoạch năm học",
    "4.1.14 Nhập file kế hoạch năm học",
    "4.1.15 Phiên sửa, lưu tay và khôi phục bản chưa lưu",
    "4.1.16 Xóa lĩnh vực con và toàn bộ nhánh",
]
for heading in functional_table_headings:
    table = table_after(paragraph_exact(doc, heading), doc)
    for row_index, row in enumerate(table.rows):
        set_cell_black(row.cells[0])
        if row_index == 0:
            for cell in row.cells:
                set_cell_black(cell)

# Tiêu đề và hàng tiêu đề bảng business rule là cấu trúc cố định.
business_heading = paragraph_exact(doc, "1.6 Educational plan (EDU)")
set_paragraph_black(business_heading)
business_table = table_after(business_heading, doc)
for cell in business_table.rows[0].cells:
    set_cell_black(cell)

doc.save(TEMP)
TEMP.replace(TARGET)
print(f"output={TARGET}")
