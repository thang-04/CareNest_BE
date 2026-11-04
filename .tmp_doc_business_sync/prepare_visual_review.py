from pathlib import Path
from PIL import Image, ImageOps, ImageDraw
from pypdf import PdfReader

ROOT = Path(r"D:\UNIVERSITY_FPT\DOAN\UI-giaoan\CareNest_BE\.tmp_doc_business_sync\rendered_review")
PDF = ROOT / "year_plan_srs_review.pdf"

reader = PdfReader(PDF)
needles = [
    "4. Educational Planning and Review",
    "4.6 Quản lý phiên sửa",
    "4.7 Xóa lĩnh vực con",
    "4. Kế hoạch giáo dục",
    "4.1.5 Lưu, gửi duyệt",
    "4.1.14 Nhập file kế hoạch năm học",
    "4.1.15 Phiên sửa",
    "4.1.16 Xóa lĩnh vực con",
    "1.6 Educational plan (EDU)",
    "EDU-48",
]
hits = {needle: [] for needle in needles}
for index, page in enumerate(reader.pages, 1):
    text = page.extract_text() or ""
    for needle in needles:
        if needle in text:
            hits[needle].append(index)
for needle, pages in hits.items():
    print(f"{needle}: {pages}")

images = sorted(ROOT.glob("page-*.png"), key=lambda p: int(p.stem.split("-")[-1]))
thumb_w, thumb_h = 248, 351
cols, rows = 4, 4
sheet_w, sheet_h = cols * thumb_w, rows * (thumb_h + 24)
font_fill = "black"
for start in range(0, len(images), cols * rows):
    batch = images[start:start + cols * rows]
    sheet = Image.new("RGB", (sheet_w, sheet_h), "#d7d7d7")
    draw = ImageDraw.Draw(sheet)
    for offset, path in enumerate(batch):
        image = Image.open(path).convert("RGB")
        image.thumbnail((thumb_w - 8, thumb_h - 8))
        x = (offset % cols) * thumb_w + (thumb_w - image.width) // 2
        y = (offset // cols) * (thumb_h + 24) + 4
        sheet.paste(ImageOps.expand(image, border=1, fill="black"), (x, y))
        draw.text((offset % cols * thumb_w + 8, y + thumb_h), f"Page {start + offset + 1}", fill=font_fill)
    output = ROOT / f"contact-{start + 1:03d}-{start + len(batch):03d}.png"
    sheet.save(output)
    print(output)

focus_pages = list(range(53, 61)) + list(range(72, 81)) + list(range(89, 97)) + list(range(110, 119))
focus = [images[p - 1] for p in focus_pages]
focus_w, focus_h = 520, 736
for start in range(0, len(focus), 4):
    batch = focus[start:start + 4]
    sheet = Image.new("RGB", (focus_w * 2, (focus_h + 28) * 2), "#d7d7d7")
    draw = ImageDraw.Draw(sheet)
    labels = focus_pages[start:start + 4]
    for offset, (path, page_no) in enumerate(zip(batch, labels)):
        image = Image.open(path).convert("RGB")
        image.thumbnail((focus_w - 10, focus_h - 10))
        x = (offset % 2) * focus_w + (focus_w - image.width) // 2
        y = (offset // 2) * (focus_h + 28) + 4
        sheet.paste(ImageOps.expand(image, border=1, fill="black"), (x, y))
        draw.text((offset % 2 * focus_w + 8, y + focus_h), f"Page {page_no}", fill=font_fill)
    output = ROOT / f"focus-{labels[0]:03d}-{labels[-1]:03d}.png"
    sheet.save(output)
    print(output)
