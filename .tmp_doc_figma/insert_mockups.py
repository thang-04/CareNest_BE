from copy import deepcopy
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


SOURCE = Path(r"D:\Downloads\G94-Report-3_Software Requirement Specification_v3 (1).docx")
OUTPUT = Path(r"D:\Downloads\G94-Report-3_Software Requirement Specification_v3 (1).__figma.tmp.docx")
SHOT_DIR = Path(r"D:\UNIVERSITY_FPT\DOAN\UI-giaoan\CareNest_FE\node_modules\.cache\goal-ui-shots")

FIGURES = [
    (
        "Hình 4.1.14.1. Màn hình chỉnh sửa kế hoạch năm học theo giao diện hiện tại",
        SHOT_DIR / "edit-1440.png",
        Inches(6.0),
        "Màn hình chỉnh sửa kế hoạch năm học gồm thông tin chung, chủ đề và mục tiêu giáo dục",
    ),
    (
        "Hình 4.1.14.2. Xem trước khi chọn Thay toàn bộ theo file",
        SHOT_DIR / "file-full-1440.png",
        Inches(6.7),
        "Hộp nhập file với chế độ thay toàn bộ nội dung có thể sửa",
    ),
    (
        "Hình 4.1.14.3. Xem trước khi chọn Chỉ thay phần có trong file",
        SHOT_DIR / "file-present-1440.png",
        Inches(6.7),
        "Hộp nhập file với chế độ chỉ thay những phần xuất hiện trong file",
    ),
]


def new_paragraph_after(paragraph):
    new_p = OxmlElement("w:p")
    paragraph._p.addnext(new_p)
    from docx.text.paragraph import Paragraph

    return Paragraph(new_p, paragraph._parent)


def set_run_font(run, color=None):
    run.font.name = "Times New Roman"
    run.font.size = Pt(11)
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:ascii"), "Times New Roman")
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:hAnsi"), "Times New Roman")
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), "Times New Roman")
    if color:
        run.font.color.rgb = RGBColor(*color)


def set_alt_text(picture_run, title):
    drawing = picture_run._r.find(qn("w:drawing"))
    if drawing is None:
        return
    doc_pr = drawing.find(".//" + qn("wp:docPr"))
    if doc_pr is not None:
        doc_pr.set("title", title)
        doc_pr.set("descr", title)


def add_figure(after, caption, image_path, width, alt_text, page_break_before=False, page_break_after=False):
    paragraph = new_paragraph_after(after)
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.paragraph_format.keep_together = True
    paragraph.paragraph_format.space_before = Pt(0)
    paragraph.paragraph_format.space_after = Pt(6)
    if page_break_before:
        paragraph.paragraph_format.page_break_before = True

    caption_run = paragraph.add_run(caption)
    caption_run.bold = True
    set_run_font(caption_run, (0, 0, 255))
    caption_run.add_break()

    picture_run = paragraph.add_run()
    set_run_font(picture_run)
    picture_run.add_picture(str(image_path), width=width)
    set_alt_text(picture_run, alt_text)

    if page_break_after:
        paragraph.add_run().add_break(WD_BREAK.PAGE)
    return paragraph


def main():
    for _, image_path, _, _ in FIGURES:
        if not image_path.exists():
            raise FileNotFoundError(image_path)

    doc = Document(SOURCE)
    headings = [p for p in doc.paragraphs if p.text.strip() == "4.1.14 Nhập file kế hoạch năm học"]
    if len(headings) != 1:
        raise RuntimeError(f"Expected one target heading, found {len(headings)}")
    heading = headings[0]

    # Idempotency: do not insert the same figures twice.
    existing = {p.text.strip() for p in doc.paragraphs}
    if any(caption in existing for caption, *_ in FIGURES):
        raise RuntimeError("The mockup figures are already present")

    anchor = heading
    for index, (caption, image_path, width, alt_text) in enumerate(FIGURES):
        anchor = add_figure(
            anchor,
            caption,
            image_path,
            width,
            alt_text,
            page_break_before=index > 0,
            page_break_after=index == len(FIGURES) - 1,
        )

    doc.save(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    main()
