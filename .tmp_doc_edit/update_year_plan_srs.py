from __future__ import annotations

import argparse
from copy import deepcopy
from pathlib import Path

from docx import Document
from docx.enum.text import WD_COLOR_INDEX
from docx.oxml.ns import qn
from docx.shared import Pt, RGBColor
from docx.table import Table
from docx.text.paragraph import Paragraph


BLUE = RGBColor(0x00, 0x00, 0xFF)
FONT_NAME = "Times New Roman"
FONT_SIZE = Pt(11)


def style_run(run) -> None:
    run.font.name = FONT_NAME
    run.font.size = FONT_SIZE
    run.font.color.rgb = BLUE
    run.font.highlight_color = None
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.get_or_add_rFonts()
    for attr in ("ascii", "hAnsi", "eastAsia", "cs"):
        rfonts.set(qn(f"w:{attr}"), FONT_NAME)


def style_paragraph(paragraph: Paragraph) -> None:
    if not paragraph.runs:
        style_run(paragraph.add_run())
    for run in paragraph.runs:
        style_run(run)


def replace_paragraph(paragraph: Paragraph, text: str) -> None:
    paragraph.text = text
    style_paragraph(paragraph)


def set_cell(cell, text: str) -> None:
    cell.text = text
    for paragraph in cell.paragraphs:
        style_paragraph(paragraph)


def set_row(table: Table, row_index: int, values: list[str], merged_body: bool = False) -> None:
    row = table.rows[row_index]
    if merged_body:
        set_cell(row.cells[0], values[0])
        set_cell(row.cells[1], values[1])
        return
    for index, value in enumerate(values):
        set_cell(row.cells[index], value)


def append_copied_row(table: Table, source_index: int = -1):
    row_xml = deepcopy(table.rows[source_index]._tr)
    table._tbl.append(row_xml)
    return table.rows[-1]


def insert_copied_row_after(table: Table, after_index: int):
    row_xml = deepcopy(table.rows[after_index]._tr)
    table.rows[after_index]._tr.addnext(row_xml)
    return table.rows[after_index + 1]


def paragraph_by_text(doc: Document, text: str) -> Paragraph:
    matches = [p for p in doc.paragraphs if p.text.strip() == text]
    if len(matches) != 1:
        raise RuntimeError(f"Expected one paragraph {text!r}, found {len(matches)}")
    return matches[0]


def table_after(doc: Document, heading_text: str) -> Table:
    heading = paragraph_by_text(doc, heading_text)
    element = heading._p.getnext()
    while element is not None:
        if element.tag == qn("w:tbl"):
            return Table(element, doc)
        if element.tag == qn("w:p"):
            paragraph = Paragraph(element, doc)
            if paragraph.text.strip() and paragraph.style.name.startswith("Heading"):
                break
        element = element.getnext()
    raise RuntimeError(f"No table found after heading {heading_text!r}")


def insert_paragraph_before(reference: Paragraph, template: Paragraph, text: str) -> Paragraph:
    paragraph_xml = deepcopy(template._p)
    reference._p.addprevious(paragraph_xml)
    paragraph = Paragraph(paragraph_xml, reference._parent)
    replace_paragraph(paragraph, text)
    return paragraph


def insert_blank_before(reference: Paragraph, template: Paragraph) -> Paragraph:
    paragraph_xml = deepcopy(template._p)
    reference._p.addprevious(paragraph_xml)
    paragraph = Paragraph(paragraph_xml, reference._parent)
    paragraph.text = ""
    return paragraph


def insert_table_before(reference: Paragraph, template: Table) -> Table:
    table_xml = deepcopy(template._tbl)
    reference._p.addprevious(table_xml)
    return Table(table_xml, reference._parent)


def update_use_cases(doc: Document) -> None:
    submit_table = table_after(doc, "4.2 Gửi kế hoạch mục tiêu năm học để duyệt")
    set_cell(
        submit_table.rows[4].cells[1],
        "Bước 4 - Không đủ quyền hoặc sai trạng thái\n"
        "Không đúng Tổ trưởng: MSG187. Sai trạng thái: MSG189. Mất phiên hoặc bản dữ liệu cũ: MSG200 / MSG201.\n"
        "Bước 5 - Thiếu thông tin\n"
        "Thiếu tên, chưa có mục tiêu hoặc câu mục tiêu trống: MSG203, MSG194, MSG205. Code hiện tại không bắt buộc mỗi mục tiêu phải có ít nhất một dòng nội dung giáo dục.\n"
        "Bước 5 - Chủ đề hoặc lịch không hợp lệ\n"
        "Thiếu tên hoặc tên viết tắt, viết tắt trùng, ngày đảo ngược, ngày ngoài năm học, chủ đề không trọn Thứ 2-Thứ 6 hoặc trùng lịch: MSG191, MSG192, MSG193, MSG204, MSG195, MSG211, MSG212. Kế hoạch chưa chuyển trạng thái.",
    )

    next_section = paragraph_by_text(doc, "5.")
    heading_template = paragraph_by_text(doc, "4.4 Tạo phiên bản điều chỉnh của kế hoạch đã ban hành")
    blank_template = next(p for p in doc.paragraphs if not p.text)
    use_case_template = table_after(doc, "4.4 Tạo phiên bản điều chỉnh của kế hoạch đã ban hành")

    insert_paragraph_before(next_section, heading_template, "4.5 Nhập file kế hoạch năm học")
    imported = insert_table_before(next_section, use_case_template)
    set_row(imported, 0, ["Tác nhân chính", "Phó Hiệu trưởng; Tổ trưởng của nhóm tuổi", "Tác nhân phụ", "Không có"])
    set_row(
        imported,
        1,
        [
            "Mô tả",
            "Là người đang soạn, tôi muốn xem trước và thay nội dung kế hoạch năm học từ file để giảm nhập lại nhưng vẫn kiểm soát phần bị thay, giữ hoặc xóa trước khi lưu.",
        ],
        merged_body=True,
    )
    set_row(
        imported,
        2,
        [
            "Điều kiện tiên quyết",
            "1. Người dùng đã đăng nhập, có quyền sửa mục tiêu năm học và đang giữ phiên sửa hợp lệ của kế hoạch đích.\n"
            "2. Kế hoạch đích đang ở trạng thái cho phép sửa theo EDU-07 và không bị người khác giữ quyền sửa.\n"
            "3. File thuộc định dạng .docx, .doc, .xlsx hoặc .csv; dung lượng tối đa 2 MB và tối đa 2.000 dòng.",
        ],
        merged_body=True,
    )
    set_row(
        imported,
        3,
        [
            "Luồng chính",
            "Nhập file kế hoạch năm học\n"
            "1. Người dùng mở chức năng Nhập file trên màn soạn kế hoạch.\n"
            "2. Người dùng chọn file; backend kiểm tra quyền, định dạng, dung lượng và đọc bản xem trước nhưng không ghi cơ sở dữ liệu.\n"
            "3. Hệ thống hiển thị metadata, mục tiêu, nội dung, chủ đề, đợt nghỉ, dấu tích và các cảnh báo đọc file.\n"
            "4. Người dùng chọn Thay toàn bộ phần có thể sửa theo file hoặc Chỉ thay những phần file có. Hệ thống tính lại rõ phần sẽ thay, xóa và giữ.\n"
            "5. Hệ thống kiểm tra nhóm tuổi, khoảng năm học, lĩnh vực, tên viết tắt chủ đề và cách ghép dấu tích.\n"
            "6. Người dùng xác nhận; hệ thống áp dụng kết quả vào form đang mở, chưa ghi lên máy chủ.\n"
            "7. Người dùng rà soát rồi bấm Lưu hoặc Ctrl+S; hệ thống mới lưu theo phiên sửa, revision và request ID hiện hành.",
        ],
        merged_body=True,
    )
    set_row(
        imported,
        4,
        [
            "Luồng thay thế",
            "Bước 2 - File không hợp lệ\n"
            "File rỗng, sai định dạng, quá 2 MB, quá 2.000 dòng hoặc không có nội dung hợp lệ bị từ chối; form không thay đổi.\n"
            "Bước 4 - Đổi chế độ nhập\n"
            "Hệ thống tính lại bản xem trước theo chế độ mới trước khi cho xác nhận; không dùng kết quả của chế độ cũ.\n"
            "Bước 5 - Nhóm tuổi hoặc cấu hình không hợp lệ\n"
            "File thuộc nhóm tuổi khác bị chặn. Nếu kế hoạch chưa chọn nhóm tuổi, chỉ nhận nhóm tuổi trong phạm vi người dùng được phép. Thiếu khoảng ngày năm học hợp lệ hoặc có lĩnh vực không thuộc cấu hình đang dùng thì khóa áp dụng.\n"
            "Bước 5 - Chủ đề và dấu tích\n"
            "Dấu tích chỉ ghép theo tên viết tắt chủ đề duy nhất. Liên kết không còn chủ đề đích bị bỏ và được cảnh báo; không tự đoán mã. Ngày ngoài năm học hoặc lịch chủ đề chưa hợp lệ được cảnh báo để sửa trước khi gửi hoặc ban hành.\n"
            "Bước 6 - Hủy, mất quyền hoặc phản hồi cũ\n"
            "Không thay đổi form. Hệ thống không áp dụng phản hồi đến muộn của lần chọn file trước.",
        ],
        merged_body=True,
    )
    set_row(
        imported,
        5,
        [
            "Điều kiện sau",
            "Sau khi xác nhận, chỉ dữ liệu trên form thay đổi. ID kế hoạch, nhóm tuổi đã khóa, người soạn, phạm vi, trạng thái, lịch sử, phiên bản, revision, phiên sửa và năm học của hệ thống được giữ nguyên. Dữ liệu chỉ được ghi lên máy chủ khi người dùng lưu thành công; hủy hoặc lỗi không làm thay đổi bản đang lưu trên hệ thống.",
        ],
        merged_body=True,
    )
    insert_blank_before(next_section, blank_template)


def update_functional_requirements(doc: Document) -> None:
    paragraph = paragraph_by_text(doc, "Nhập file: chọn file CSV cùng mẫu với file xuất để thêm các mục tiêu chưa có; cột chủ đề được khớp theo tên viết tắt.")
    replace_paragraph(
        paragraph,
        "Nhập file kế hoạch năm học: chọn file Word (.docx, .doc), Excel (.xlsx) hoặc CSV để xem trước và thay nội dung kế hoạch theo một trong hai chế độ; việc áp dụng chỉ thay dữ liệu trên form, người dùng phải bấm Lưu hoặc Ctrl+S để ghi lên hệ thống (UC 4.5, EDU-26 đến EDU-29).",
    )

    goal_table = table_after(doc, "4.1.3 Lập kế hoạch năm học – Mục tiêu, nội dung giáo dục")
    set_cell(
        goal_table.rows[3].cells[1],
        "Nút, mở hộp Nhập file kế hoạch năm học. Nhận .docx, .doc, .xlsx và .csv; tối đa 2 MB và 2.000 dòng. Backend chỉ trả bản xem trước, không lưu dữ liệu. Người dùng chọn chế độ thay toàn bộ phần có thể sửa hoặc chỉ thay những phần file có; xác nhận chỉ áp dụng vào form và phải bấm Lưu/Ctrl+S mới ghi lên hệ thống (UC 4.5, EDU-26 đến EDU-29).",
    )
    insert_copied_row_after(goal_table, 3)
    set_row(
        goal_table,
        4,
        [
            "Xem trước file",
            "Hiển thị thông tin nhận diện được từ file, số mục tiêu/nội dung/dấu tích theo lĩnh vực, chủ đề, đợt nghỉ, cảnh báo theo dòng và phần sẽ thay, xóa hoặc giữ theo chế độ đang chọn.",
        ],
    )
    insert_copied_row_after(goal_table, 4)
    set_row(
        goal_table,
        5,
        [
            "Kiểm tra trước khi áp dụng",
            "Chặn file khác nhóm tuổi, file không có nội dung hợp lệ, thiếu khoảng ngày năm học của hệ thống, lĩnh vực không thuộc cấu hình hoặc tên viết tắt chủ đề bị trùng. Dấu tích không ghép được theo tên viết tắt duy nhất bị bỏ và có cảnh báo.",
        ],
    )

    next_section = paragraph_by_text(doc, "5 Nhập học")
    heading_template = paragraph_by_text(doc, "4.1.13 Xem trước và xuất file")
    normal_template = next(
        p
        for p in doc.paragraphs
        if p.text.strip() == "Màn hình này cho phép Hiệu trưởng, Phó Hiệu trưởng, Tổ trưởng và Giáo viên:"
    )
    blank_template = next(p for p in doc.paragraphs if not p.text)
    table_template = table_after(doc, "4.1.13 Xem trước và xuất file")

    insert_blank_before(next_section, blank_template)
    insert_paragraph_before(next_section, heading_template, "4.1.14 Nhập file kế hoạch năm học")
    insert_blank_before(next_section, blank_template)
    insert_paragraph_before(next_section, normal_template, "Màn hình này cho phép Phó Hiệu trưởng và Tổ trưởng đang giữ quyền sửa kế hoạch:")
    insert_paragraph_before(next_section, normal_template, "Chọn file Word, Excel hoặc CSV để hệ thống đọc và hiển thị bản xem trước mà chưa lưu dữ liệu.")
    insert_paragraph_before(next_section, normal_template, "Chọn chế độ thay toàn bộ phần có thể sửa theo file hoặc chỉ thay những phần xuất hiện trong file.")
    insert_paragraph_before(next_section, normal_template, "Rà soát phần sẽ thay, xóa, giữ; kiểm tra cảnh báo về nhóm tuổi, lịch, chủ đề và dấu tích trước khi áp dụng vào form.")
    insert_paragraph_before(next_section, normal_template, "Trên màn hình, họ cũng có thể:")
    insert_paragraph_before(next_section, normal_template, "Đổi file hoặc đổi chế độ: hệ thống tính lại bản xem trước; kết quả cũ không được áp dụng.")
    insert_paragraph_before(next_section, normal_template, "Hủy: đóng hộp, không thay đổi dữ liệu trên form hoặc trên máy chủ.")
    insert_paragraph_before(next_section, normal_template, "Mô tả trường")
    imported = insert_table_before(next_section, table_template)
    while len(imported.rows) < 9:
        append_copied_row(imported)
    while len(imported.rows) > 9:
        imported._tbl.remove(imported.rows[-1]._tr)
    rows = [
        ["Tên trường", "Mô tả"],
        ["File kế hoạch", "Bắt buộc. Nhận .docx, .doc, .xlsx và .csv; tối đa 2 MB và 2.000 dòng. File rỗng, sai định dạng, quá giới hạn hoặc không có nội dung hợp lệ thì không được áp dụng."],
        ["Chế độ thay nội dung", "Chọn một trong hai giá trị: Thay toàn bộ phần có thể sửa theo file (phần file thiếu trở thành trống) hoặc Chỉ thay những phần file có (phần file thiếu giữ dữ liệu đang soạn). Đổi chế độ phải tính lại bản xem trước."],
        ["Thông tin file", "Chỉ đọc: tên kế hoạch, năm học nguồn và khoảng tuổi nếu đọc được; số mục tiêu, nội dung, dấu tích theo lĩnh vực; danh sách chủ đề, đợt nghỉ và cảnh báo theo dòng."],
        ["Phạm vi thay đổi", "Chỉ đọc: phần thông tin chung, mục tiêu, chủ đề và đợt nghỉ sẽ được thay, xóa hoặc giữ theo chế độ hiện tại. Mô tả không có trong preview: chế độ thay toàn bộ để trống; chế độ chỉ thay phần có giữ nguyên."],
        ["Nhóm tuổi và năm học", "File khác nhóm tuổi đang mở bị chặn. Nếu kế hoạch chưa chọn nhóm tuổi, chỉ nhận nhóm tuổi thuộc phạm vi người dùng. Năm học nguồn chỉ dùng để dời lịch; năm học và khoảng ngày của hệ thống không bị thay."],
        ["Chủ đề và dấu tích", "Chủ đề đích được xác định trước khi ghép dấu tích. Dấu tích chỉ ghép bằng tên viết tắt duy nhất; liên kết không còn chủ đề đích bị bỏ và hiển thị cảnh báo, không tự đoán mã."],
        ["Áp dụng vào bản đang soạn", "Nút chỉ bấm được khi bản xem trước hợp lệ. Xác nhận chỉ cập nhật form; giữ ID kế hoạch, nhóm tuổi đã khóa, người soạn, phạm vi, trạng thái, lịch sử, phiên bản, revision và phiên sửa. Người dùng phải bấm Lưu hoặc Ctrl+S để ghi lên hệ thống."],
        ["Hủy / Chọn file khác", "Hủy không thay đổi form. Khi chọn file khác, phản hồi đến muộn của file cũ bị bỏ qua."],
    ]
    for index, values in enumerate(rows):
        set_row(imported, index, values)
    insert_blank_before(next_section, blank_template)


def update_business_rules(doc: Document) -> None:
    table = table_after(doc, "1.6 Educational plan (EDU)")

    set_cell(table.rows[2].cells[0], "EDU-02")
    set_cell(
        table.rows[3].cells[3],
        "Follows the structured yearly-plan template used by MN Thai Duong. Goal numbers are referenced by theme plans, lesson plans and child assessment.",
    )
    set_cell(
        table.rows[8].cells[1],
        "Only one edit session can hold a yearly plan at a time. The editor sends a heartbeat every 20 seconds and the session expires after 90 seconds without contact. Reopening with the same account takes over immediately; a different account must wait until the active session is released or expires.",
    )
    set_cell(
        table.rows[10].cells[1],
        "A yearly plan can be submitted or issued only when it has a title, at least one goal, no blank goal text, and every theme has a name and a unique non-blank abbreviation. Theme and break dates must satisfy the school-year and weekly-schedule checks. The current service does not require every goal to have a content row.",
    )
    set_cell(
        table.rows[10].cells[3],
        "Ensures the issued plan satisfies the validations currently enforced by the service without documenting checks that the implementation does not perform.",
    )
    set_cell(
        table.rows[10].cells[4],
        "The service validates these conditions whenever the plan is submitted or issued.",
    )
    set_cell(
        table.rows[16].cells[1],
        "Every request is checked against goals.view, goals.edit, goals.approve or plans.config. The current backend assigns these permissions from one role-based policy map; dynamic permission configuration has not replaced that map yet.",
    )
    set_cell(
        table.rows[16].cells[3],
        "Keeps permission checks centralized while reflecting the authorization mechanism currently implemented.",
    )
    set_cell(
        table.rows[16].cells[4],
        "EduPlanAccessPolicy checks the role-based permission map and the age-group view or edit scope on each request.",
    )

    new_rules = [
        [
            "EDU-26",
            "An author with goals.edit can preview a yearly-plan file in .docx, .doc, .xlsx or .csv format. The file must not exceed 2 MB or 2,000 rows. Preview reads metadata, goals, contents, theme ticks, themes and breaks but does not write the database.",
            "Yearly-plan file preview",
            "Allows the author to inspect parsed data and warnings before any draft content changes.",
            "The backend checks permission, file type, size and row limits, then returns GoalFilePreviewResponse without a write transaction.",
        ],
        [
            "EDU-27",
            "Before applying a file, the author chooses either Replace all editable sections from the file or Replace only sections present in the file. Missing sections become empty in the first mode and keep the current draft in the second mode. Changing the mode recalculates the preview of replaced, removed and retained data.",
            "Applying a yearly-plan file",
            "The author must know exactly which draft sections will change before confirming the import.",
            "The web client recalculates the replacement patch and preview for the selected mode before enabling confirmation.",
        ],
        [
            "EDU-28",
            "Applying a file changes only the open form. It preserves the plan ID, locked age group, author, scope, status, history, version, revision, edit session and system school year. The author must press Save or Ctrl+S before the imported content is written to the server.",
            "File import and manual save",
            "File import must not bypass the edit-session, revision and manual-save controls of the yearly plan.",
            "The web client applies the preview to local form state; the existing save endpoint performs the only database write.",
        ],
        [
            "EDU-29",
            "A file for a different age group is blocked. A blank target may adopt the detected age group only within the author's allowed scope. Source-year dates are shifted to the system school year. Theme ticks are mapped only by a unique abbreviation; unresolved links are removed with a warning, and invalid school-year configuration or unsupported domains block application.",
            "File import validation and mapping",
            "Prevents goals, schedules and theme links from being applied to the wrong age group or an ambiguous target.",
            "The web client validates age-group scope, school-year dates, active domains and unique theme abbreviations before building the import patch.",
        ],
    ]
    for values in new_rules:
        append_copied_row(table)
        set_row(table, len(table.rows) - 1, values)


def verify_blue_formatting(doc: Document) -> None:
    checks = [
        paragraph_by_text(doc, "4.5 Nhập file kế hoạch năm học"),
        paragraph_by_text(doc, "4.1.14 Nhập file kế hoạch năm học"),
    ]
    for paragraph in checks:
        for run in paragraph.runs:
            if run.text and (run.font.color.rgb != BLUE or run.font.size != FONT_SIZE or run.font.name != FONT_NAME):
                raise RuntimeError(f"Formatting check failed for paragraph {paragraph.text!r}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    doc = Document(args.input)
    update_use_cases(doc)
    update_functional_requirements(doc)
    update_business_rules(doc)
    verify_blue_formatting(doc)
    doc.save(args.output)
    print(args.output)


if __name__ == "__main__":
    main()
