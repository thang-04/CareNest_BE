from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


root = Path(__file__).parent / "rendered"
pages = sorted(root.glob("page-*.png"))
out = root / "contact"
out.mkdir(exist_ok=True)

cols, rows = 4, 3
thumb_w = 240
label_h = 24
gap = 8

for group_index in range(0, len(pages), cols * rows):
    group = pages[group_index : group_index + cols * rows]
    thumbs = []
    for path in group:
        image = Image.open(path).convert("RGB")
        width = thumb_w
        height = round(image.height * width / image.width)
        thumbs.append((path, image.resize((width, height), Image.Resampling.LANCZOS)))
    cell_h = max(image.height for _, image in thumbs) + label_h
    sheet = Image.new(
        "RGB",
        (cols * thumb_w + (cols + 1) * gap, rows * cell_h + (rows + 1) * gap),
        "#BFC3C8",
    )
    draw = ImageDraw.Draw(sheet)
    for index, (path, image) in enumerate(thumbs):
        row, col = divmod(index, cols)
        x = gap + col * (thumb_w + gap)
        y = gap + row * (cell_h + gap)
        sheet.paste(image, (x, y + label_h))
        draw.rectangle((x, y, x + thumb_w, y + label_h), fill="white")
        draw.text((x + 6, y + 4), path.stem.replace("page-", "Page "), fill="black")
    first = int(group[0].stem.split("-")[1])
    last = int(group[-1].stem.split("-")[1])
    sheet.save(out / f"pages-{first:03d}-{last:03d}.png")

print(len(pages), len(list(out.glob("*.png"))))
