from copy import deepcopy
from pathlib import Path
import re

from docx import Document
from docx.oxml.table import CT_Tbl
from docx.oxml.text.paragraph import CT_P
from docx.oxml.ns import qn
from docx.shared import Pt, RGBColor
from docx.table import Table
from docx.text.paragraph import Paragraph


SOURCE = Path(r"D:\Downloads\G94-Report-3_Software Requirement Specification_v3 (1).docx")
OUTPUT = Path(r"D:\Downloads\G94-Report-3_Software Requirement Specification_v3 (1).__business.tmp.docx")
BLUE = RGBColor(0, 0, 255)


def format_run(run, blue=True, bold=None):
    run.font.name = "Times New Roman"
    run.font.size = Pt(11)
    rfonts = run._element.get_or_add_rPr().get_or_add_rFonts()
    for key in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        rfonts.set(qn(key), "Times New Roman")
    if blue:
        run.font.color.rgb = BLUE
    if bold is not None:
        run.bold = bold


def format_paragraph(paragraph, blue=True):
    for run in paragraph.runs:
        format_run(run, blue=blue)


def set_paragraph(paragraph, text, blue=True):
    paragraph.text = text
    format_paragraph(paragraph, blue=blue)


def set_cell(cell, text, blue=True, bold=False):
    cell.text = text
    for paragraph in cell.paragraphs:
        for run in paragraph.runs:
            format_run(run, blue=blue, bold=bold)


def paragraphs(doc):
    return list(doc.paragraphs)


def paragraph_exact(doc, text):
    matches = [p for p in doc.paragraphs if p.text.strip() == text]
    if len(matches) != 1:
        raise RuntimeError(f"Expected one paragraph {text!r}, found {len(matches)}")
    return matches[0]


def table_after(paragraph, doc):
    node = paragraph._p.getnext()
    while node is not None:
        if isinstance(node, CT_Tbl):
            return Table(node, doc._body)
        node = node.getnext()
    raise RuntimeError(f"No table after {paragraph.text!r}")


def new_paragraph_like(base, text):
    element = deepcopy(base._p)
    for child in list(element):
        if child.tag != qn("w:pPr"):
            element.remove(child)
    paragraph = Paragraph(element, base._parent)
    set_paragraph(paragraph, text)
    return paragraph


def clone_table(template, doc, rows):
    element = deepcopy(template._tbl)
    table = Table(element, doc._body)
    while len(table.rows) < rows:
        table.add_row()
    while len(table.rows) > rows:
        table._tbl.remove(table.rows[-1]._tr)
    return table


def set_use_case(table, actor, secondary, description, prerequisites, main_flow, alternatives, postconditions):
    values = [
        ("Tác nhân chính", actor, "Tác nhân phụ", secondary),
        ("Mô tả", description),
        ("Điều kiện tiên quyết", prerequisites),
        ("Luồng chính", main_flow),
        ("Luồng thay thế", alternatives),
        ("Điều kiện sau", postconditions),
    ]
    for ci, value in enumerate(values[0]):
        set_cell(table.rows[0].cells[ci], value, bold=ci in (0, 2))
    for ri, (label, content) in enumerate(values[1:], 1):
        set_cell(table.rows[ri].cells[0], label, bold=True)
        set_cell(table.rows[ri].cells[1], content)


USE_CASES = {
    "4.1 Lấy mục tiêu từ nguồn có sẵn": dict(
        actor="Phó Hiệu trưởng; Tổ trưởng của nhóm tuổi",
        secondary="Không có",
        description="Người soạn lấy mục tiêu và nội dung giáo dục từ kế hoạch được phép xem hoặc từ Chương trình GDMN của Bộ để dùng lại trong bản kế hoạch đang soạn.",
        prerequisites="1. Kế hoạch đích đang ở trạng thái cho phép sửa; người dùng có goals.edit, đúng phạm vi nhóm tuổi và giữ phiên sửa hợp lệ (EDU-02, EDU-03, EDU-08, EDU-10).\n2. Với nguồn kế hoạch có sẵn, người dùng chỉ được chọn bản nằm trong phạm vi xem theo EDU-06.",
        main_flow="1. Người dùng chọn Lấy từ kế hoạch năm học hoặc Lấy từ Chương trình của Bộ trên thanh thao tác.\n2. Hệ thống tải nguồn đúng quyền và nhóm tuổi.\n3. Người dùng chọn từng mục tiêu, một nhóm, một lĩnh vực hoặc tất cả.\n4. Hệ thống đọc lại nguồn, bỏ dòng trùng, ghép các cấp cha cần thiết và thêm mục tiêu/nội dung vào cuối cây đang soạn.\n5. Với nguồn Bộ, hệ thống giữ liên kết nguồn và nhãn Theo Bộ/Đã sửa từ Bộ.\n6. Dữ liệu chỉ nằm trên form; người dùng bấm Lưu hoặc Ctrl+S để ghi lên máy chủ (EDU-22, EDU-24, EDU-28).",
        alternatives="- Nguồn không còn tồn tại, không thuộc phạm vi xem hoặc đã đổi revision: từ chối và yêu cầu tải lại.\n- Mục tiêu Bộ sai nhóm tuổi hoặc không thể ghép vào cấu trúc đích: bỏ mục đó, nêu lý do và giữ dữ liệu hiện có.\n- Mất quyền sửa, mất phiên hoặc bản đích đã đổi revision: không áp dụng kết quả.\n- Người dùng hủy: không thay đổi form.",
        postconditions="Mục tiêu được thêm vào bản đích nhưng kế hoạch nguồn và danh mục Bộ không đổi. Chủ đề, đợt nghỉ, lịch và dấu tích chủ đề không được chép từ chức năng lấy mục tiêu; chỉ lần Lưu thành công mới ghi dữ liệu lên máy chủ.",
    ),
    "4.2 Gửi kế hoạch mục tiêu năm học để duyệt": dict(
        actor="Tổ trưởng của nhóm tuổi",
        secondary="Phó Hiệu trưởng",
        description="Tổ trưởng gửi kế hoạch Nháp hoặc Bị trả lại cho Phó Hiệu trưởng xem xét và ban hành.",
        prerequisites="1. Người gửi là Tổ trưởng có phân công còn hiệu lực đúng nhóm tuổi (EDU-02, EDU-04).\n2. Kế hoạch ở Nháp hoặc Bị trả lại; người dùng đang giữ đúng phiên sửa và revision mới nhất (EDU-03, EDU-10).",
        main_flow="1. Tổ trưởng rà soát thông tin chung, chủ đề/đợt nghỉ, cây mục tiêu và dấu tích.\n2. Tổ trưởng chọn Gửi Phó HT duyệt; giao diện lưu nội dung hiện có trước khi gửi.\n3. Backend kiểm quyền, trạng thái, session, revision và request ID.\n4. Backend kiểm tên kế hoạch, có ít nhất một mục tiêu, câu mục tiêu không trống; kiểm tên/viết tắt chủ đề, ngày trong năm học, trọn tuần Thứ 2-Thứ 6 và không trùng thời gian (EDU-04, EDU-23, EDU-39, EDU-40, EDU-42).\n5. Hệ thống chuyển trạng thái sang Chờ Phó HT duyệt, ghi người gửi/thời điểm/lịch sử và nhả phiên sửa.",
        alternatives="- Thiếu quyền, sai nhóm tuổi hoặc sai trạng thái: từ chối, giữ trạng thái cũ.\n- Session/revision không còn hợp lệ hoặc request đã cũ: không ghi đè dữ liệu mới hơn.\n- Thiếu tên, chưa có mục tiêu, câu mục tiêu trống, chủ đề/lịch không hợp lệ: hiển thị lỗi cụ thể để sửa.\n- Code hiện tại không bắt buộc mỗi mục tiêu phải có ít nhất một dòng nội dung giáo dục.",
        postconditions="Kế hoạch ở Chờ Phó HT duyệt; Tổ trưởng chỉ xem, Phó Hiệu trưởng có thể mở phiên sửa, ban hành hoặc trả lại. Mọi lớp vẫn dùng phiên bản đã ban hành hiện hành cho tới khi bản mới được ban hành.",
    ),
    "4.3 Ban hành hoặc trả lại kế hoạch năm học": dict(
        actor="Phó Hiệu trưởng",
        secondary="Tổ trưởng của nhóm tuổi",
        description="Phó Hiệu trưởng ban hành kế hoạch đủ điều kiện hoặc trả lại kèm lý do để người soạn chỉnh sửa.",
        prerequisites="1. Người dùng là Phó Hiệu trưởng có phân công còn hiệu lực và có goals.approve (EDU-05, EDU-08).\n2. Ban hành bản Chờ duyệt, hoặc ban hành trực tiếp bản Nháp/Bị trả lại do chính Phó Hiệu trưởng tạo. Trả lại chỉ áp dụng cho bản Chờ duyệt.\n3. Session và revision hợp lệ (EDU-10).",
        main_flow="1. Phó Hiệu trưởng mở kế hoạch và xem nội dung, lịch sử.\n2. Khi cần, Phó Hiệu trưởng mở phiên sửa bản Chờ duyệt; thay đổi được ghi lịch sử.\n3. Chọn Ban hành; hệ thống lưu trước, kiểm lại toàn bộ điều kiện như UC 4.2.\n4. Hệ thống chuyển Đã ban hành, lưu người ban hành/thời điểm/ghi chú, nhả phiên sửa.\n5. Kế hoạch trở thành căn cứ hiện hành cho tài liệu lớp và bản in ký tay (EDU-05, EDU-12, EDU-21).",
        alternatives="- Chọn Trả lại: nhập lý do bắt buộc, tối đa 2.000 ký tự; hệ thống chuyển Bị trả lại, lưu lý do và nhả phiên sửa.\n- Thiếu lý do, sai trạng thái, mất quyền, mất session/revision hoặc dữ liệu không đạt validation: giữ nguyên trạng thái và không báo thành công.\n- Nếu lưu trước hành động thất bại, không tiếp tục ban hành/trả lại.",
        postconditions="Kế hoạch ở Đã ban hành hoặc Bị trả lại đúng quyết định, có đầy đủ dấu vết người thực hiện và thời điểm. Hệ thống không tạo ảnh chữ ký hoặc chữ ký điện tử; tài liệu xuất chừa chỗ ký tay.",
    ),
    "4.4 Tạo phiên bản điều chỉnh của kế hoạch đã ban hành": dict(
        actor="Phó Hiệu trưởng; Tổ trưởng của nhóm tuổi",
        secondary="Giáo viên; người duyệt tài liệu lớp",
        description="Người có quyền tạo phiên bản n+1 từ bản đã ban hành hiện hành để điều chỉnh mà không sửa trực tiếp bản chính thức.",
        prerequisites="1. Nguồn là phiên bản đã ban hành mới nhất trong chuỗi.\n2. Không có phiên bản chưa ban hành khác trong cùng chuỗi.\n3. Người dùng có quyền tạo cho nhóm tuổi, revision nguồn còn mới và request ID chưa được dùng cho nguồn khác (EDU-01, EDU-02, EDU-25).",
        main_flow="1. Người dùng chọn Tạo phiên bản điều chỉnh và xác nhận.\n2. Backend khóa chuỗi, kiểm nguồn hiện hành và phiên bản mở.\n3. Tạo phiên bản n+1, chép đủ thông tin chung, mục tiêu/nội dung, chủ đề, đợt nghỉ và dấu tích; các dòng con giữ lineage.\n4. Phiên bản mới ở Nháp, người tạo là owner và nhận phiên sửa.\n5. Người dùng chỉnh sửa rồi gửi/ban hành lại theo EDU-03..05, EDU-10, EDU-22, EDU-23.\n6. Có thể xem so sánh thay đổi theo lineage, không theo số MT.",
        alternatives="- Nguồn không phải bản ban hành mới nhất: từ chối.\n- Đã có phiên bản chưa ban hành hoặc hai người tạo đồng thời: chỉ giữ một phiên bản mở, trả lỗi cho yêu cầu còn lại.\n- Request ID lặp lại cùng nguồn: trả lại kết quả đã tạo; tái sử dụng cho nguồn khác: từ chối.",
        postconditions="Bản đã ban hành cũ bất biến và tiếp tục được lớp sử dụng cho tới khi phiên bản mới được ban hành. Tài liệu lớp của phiên bản cũ chỉ đọc/in; tài liệu mới đối chiếu thay đổi theo lineage (EDU-25, EDU-26).",
    ),
    "4.5 Nhập file kế hoạch năm học": dict(
        actor="Phó Hiệu trưởng; Tổ trưởng của nhóm tuổi",
        secondary="Không có",
        description="Người soạn xem trước và thay nội dung bản đang soạn từ file Word, Excel hoặc CSV mà vẫn giữ kiểm soát phần thay, xóa và giữ.",
        prerequisites="1. Người dùng có goals.edit và đang giữ phiên sửa hợp lệ của kế hoạch đích (EDU-02, EDU-03, EDU-10).\n2. File .docx, .doc, .xlsx hoặc .csv; tối đa 2 MB và 2.000 dòng.\n3. Kế hoạch đích ở trạng thái cho phép sửa (EDU-46).",
        main_flow="1. Người dùng chọn Nhập file, chọn file.\n2. Backend kiểm quyền/định dạng/dung lượng, đọc metadata, mục tiêu/nội dung/dấu tích, chủ đề/đợt nghỉ và trả bản xem trước; không ghi DB.\n3. Người dùng chọn Thay toàn bộ phần có thể sửa theo file hoặc Chỉ thay phần có trong file.\n4. Web tính lại phần sẽ thay, xóa, giữ; kiểm nhóm tuổi, khoảng ngày, chủ đề, mã viết tắt và ánh xạ dấu tích.\n5. Người dùng xác nhận; kết quả chỉ áp dụng vào form đang mở.\n6. Người dùng rà soát rồi bấm Lưu/Ctrl+S; endpoint lưu hiện có mới ghi DB (EDU-22, EDU-46).",
        alternatives="- File rỗng, sai định dạng, quá 2 MB/2.000 dòng hoặc không có nội dung hợp lệ: từ chối, form không đổi.\n- File khác nhóm tuổi: chặn; đích chưa chọn nhóm chỉ nhận nhóm thuộc phạm vi người dùng.\n- Thiếu cấu hình khoảng năm, domain không hỗ trợ hoặc viết tắt chủ đề đích không duy nhất: khóa áp dụng.\n- Liên kết dấu tích không còn chủ đề đích: bỏ liên kết và cảnh báo, không đoán mã.\n- Đổi file/chế độ: tính lại; phản hồi đến muộn của lần trước bị bỏ. Hủy/mất quyền/lỗi: form không đổi.",
        postconditions="Áp dụng thành công chỉ thay trạng thái form. ID kế hoạch, nhóm tuổi đã khóa, người soạn, phạm vi, trạng thái, history, version, revision, phiên sửa và năm học/khoảng ngày hệ thống được giữ nguyên; chỉ Lưu thành công mới ghi máy chủ.",
    ),
}


NEW_USE_CASES = [
    (
        "4.6 Quản lý phiên sửa, lưu tay và khôi phục bản chưa lưu",
        dict(
            actor="Phó Hiệu trưởng; Tổ trưởng của nhóm tuổi",
            secondary="Không có",
            description="Người soạn giữ quyền sửa độc quyền theo phiên, chủ động lưu và có thể khôi phục bản chưa lưu trên thiết bị sau sự cố.",
            prerequisites="Kế hoạch thuộc phạm vi xem/sửa và ở trạng thái cho phép sửa theo EDU-03; người dùng có goals.edit.",
            main_flow="1. Khi mở trang sửa, Web tạo session ID và gọi start-editing kèm revision/request ID.\n2. Backend khóa dòng, kiểm quyền/trạng thái và cấp phiên; cùng tài khoản mở lại bằng phiên mới sẽ nhận quyền ngay.\n3. Web gửi heartbeat mỗi 20 giây; khóa hết hạn sau 90 giây mất liên lạc.\n4. Người dùng chỉnh sửa; hệ thống chỉ ghi khi bấm Lưu/Ctrl+S hoặc gửi/ban hành.\n5. Mỗi ghi kiểm session, revision và request ID để chống ghi đè/lưu trùng.\n6. Khi rời bình thường, Web gọi finish-editing; bản lưu cục bộ được xóa sau Lưu hoặc Không lưu (EDU-10, EDU-22).",
            alternatives="- Người khác đang giữ khóa: chỉ xem và hiển thị người đang sửa.\n- Cùng tài khoản mở phiên mới: phiên cũ mất quyền; heartbeat/lưu từ phiên cũ bị từ chối nhưng bản cục bộ vẫn còn.\n- Revision cũ: yêu cầu tải bản mới, không ghi đè.\n- Rời trang khi chưa lưu: hỏi Hủy/Không lưu/Lưu; đóng hoặc tải lại tab dùng cảnh báo trình duyệt.\n- Mở lại có bản cục bộ phù hợp revision/trạng thái: mời Khôi phục hoặc Bỏ, không tự ghi máy chủ.",
            postconditions="Tại một thời điểm chỉ một session sửa được kế hoạch. Dữ liệu máy chủ chỉ đổi sau thao tác ghi hợp lệ; bản cục bộ không vượt qua kiểm tra session/revision.",
        ),
    ),
    (
        "4.7 Xóa lĩnh vực con trong bản đang soạn",
        dict(
            actor="Phó Hiệu trưởng; Tổ trưởng của nhóm tuổi",
            secondary="Không có",
            description="Người soạn xóa toàn bộ một nhánh lĩnh vực con cùng các dòng con và dấu tích liên quan sau khi xem rõ phạm vi ảnh hưởng.",
            prerequisites="Kế hoạch đang sửa được; người dùng giữ phiên sửa và revision hợp lệ. Lĩnh vực con tồn tại trong cây mục tiêu (EDU-03, EDU-09, EDU-10, EDU-48).",
            main_flow="1. Người dùng chọn Xóa tại dòng lĩnh vực con.\n2. Web tính số nhóm kết quả mong đợi, mục tiêu, nội dung và dấu tích chủ đề nằm trong nhánh.\n3. Hộp xác nhận hiển thị tên lĩnh vực con và các số lượng sẽ bị xóa.\n4. Người dùng xác nhận; Web loại toàn bộ nhánh khỏi form nhưng giữ các nhánh khác, chủ đề, đợt nghỉ và liên kết nguồn Bộ ngoài nhánh.\n5. Người dùng bấm Lưu/Ctrl+S; backend lưu cây mới và xóa các dòng bị bỏ theo thứ tự an toàn (EDU-22, EDU-48).",
            alternatives="- Hủy: không thay đổi form.\n- Mất quyền/phiên hoặc revision cũ khi Lưu: backend từ chối, không ghi đè.\n- Người dùng cố ý gửi dòng con sang cha khác trong request hợp lệ: backend giữ theo cấu trúc request; thao tác Xóa trên Web không tự chuyển cha.",
            postconditions="Sau Lưu thành công, nhánh và mọi nhóm/mục tiêu/nội dung/dấu tích của nhánh bị xóa; cấu trúc ngoài nhánh không đổi. Trước khi Lưu, thay đổi chỉ ở form và có thể hủy/khôi phục theo EDU-22.",
        ),
    ),
]


BUSINESS_RULES = [
    ("EDU-01", "Each school year and age group has one yearly-plan chain with versions 1..n, and at most one version in that chain may be unissued.", "Yearly-plan identity and versioning", "Prevents competing goal sets for the same age group while allowing controlled revisions.", "A database uniqueness constraint and the service checks creation and version creation."),
    ("EDU-02", "A yearly plan may be created by a Vice Principal or by the active Team Leader of the matching age group. The creator is recorded as owner, while the Vice Principal and that Team Leader co-author the plan.", "Creation and ownership", "Keeps the professional plan within the responsible age-group team.", "EduPlanAccessPolicy checks goals.edit, the effective assignment and age-group scope."),
    ("EDU-03", "Draft or Returned plans may be edited by the Vice Principal and the matching Team Leader, one editor at a time. While Pending VP approval only the Vice Principal may edit. An Issued plan is immutable and must be revised through EDU-25.", "Editing by status", "Protects reviewed and official content while permitting corrections before issue.", "The service checks status, permission, age-group scope and edit session on every write."),
    ("EDU-04", "The active Team Leader of the age group may submit a Draft or Returned plan when no other editor holds it and the plan has at least one goal. Full review validation is performed before submission.", "Submit for approval", "Makes the professional lead accountable for the content sent to the Vice Principal.", "submitYearPlan checks role, status, session, revision and requireReadyForReview."),
    ("EDU-05", "Only the Vice Principal may issue or return a yearly plan. A Pending plan may be issued; a Draft or Returned plan may be issued directly only by the Vice Principal who owns it. Return is allowed only while Pending and requires a reason.", "Issue and return", "Ensures one accountable issuer and a traceable correction request.", "issueYearPlan and returnYearPlan check role, status, owner, session, revision and reason."),
    ("EDU-06", "View scope is status- and assignment-based: the Vice Principal sees all plans; the Principal sees all except Draft and Editing; a Team Leader sees all plans of their age group and Issued plans of other age groups read-only; a Teacher sees Issued plans for age groups they teach.", "Listing, details and source selection", "Exposes only the plans needed for management, reuse and teaching.", "EduPlanAccessPolicy builds a ViewScope from effective assignments and plan status."),
    ("EDU-07", "Only the Vice Principal may delete a plan, and only while its base status is Draft with a valid edit session and revision.", "Delete yearly plan", "Prevents removal of reviewed, returned, pending or issued records.", "deleteYearPlan checks goals.edit, Vice Principal authority, base status, session and revision."),
    ("EDU-08", "Every request is authorized through goals.view, goals.edit, goals.approve or plans.config. The current backend assigns these permissions from one centralized role map and also requires effective Principal/Vice-Principal assignments and age-group scope.", "Authorization", "Centralizes permission checks while matching the authorization mechanism currently implemented.", "EduPlanAccessPolicy evaluates the role map, assignment validity and scope."),
    ("EDU-09", "The goal table has five levels: domain, sub-domain, expected-result group, goal and educational content. Goals are numbered continuously across the year. Content rows are ticked against themes; a theme's goals are derived from those ticks.", "Goal hierarchy", "Matches the MN Thai Duong yearly-plan template and gives downstream plans stable structured content.", "The save service persists the hierarchy, reorders rows, numbers goals and stores content-theme links."),
    ("EDU-10", "Only one edit session may hold a yearly plan. The Web sends a heartbeat every 20 seconds and the lock expires after 90 seconds without contact. A new session from the same account takes over immediately; another account waits for release or expiry. Every write checks session, revision and request ID.", "Concurrent editing", "Prevents overwrites, stale writes and duplicate requests.", "start-editing, heartbeat, finish-editing and all write actions lock the row and verify session/revision/idempotency."),
    ("EDU-11", "The Principal and Vice Principal may configure domain display names and add domains; the five standard domains cannot be deleted. They also configure signer chains and weekly activity rows, while the yearly-plan chain must include the Vice Principal.", "Education-plan configuration", "Lets the school adapt labels and approval workflow without losing mandatory programme structure.", "EduConfigService validates standard domains, signer chains and fixed activity rows when configuration is saved."),
    ("EDU-12", "Class documents can be created only after the age group's yearly plan is issued. Branch themes are one per class/year; theme plans are one per class/theme; weekly lesson plans are one per class/branch week. Each yearly-plan version has a separate class-document set.", "Downstream class documents", "Ensures every class document is based on one approved yearly-plan version.", "Class-document services check the current issued version, class assignment and document uniqueness."),
    ("EDU-13", "The sequence is: approved yearly branch themes, then theme plan, then weekly/day plans for branches of that theme. A later step cannot open until the required earlier step is complete.", "Education planning sequence", "Keeps weekly teaching content aligned with the approved yearly and theme structure.", "The class-document service checks predecessor status before create, save or submit."),
    ("EDU-14", "Each teaching week of a theme has one branch-theme name set by the teacher. Submission is allowed only when every teaching week is named and no unreviewed AI suggestion remains.", "Branch-theme plan", "Provides a complete annual sequence of class themes before detailed planning.", "The service validates all teaching weeks and pending AI suggestions before submit."),
    ("EDU-15", "A theme plan takes only goals and contents ticked for that theme from the yearly plan; teachers cannot edit that source text. Teachers add educational activities and environments. The saved document snapshots its source rows.", "Theme plan", "Approved documents must not silently change when the yearly plan changes later.", "The service resolves theme ticks and stores copied goal/content rows inside the class document."),
    ("EDU-16", "A weekly lesson plan contains weekly purpose/preparation, the configured activity-row by Monday-Friday table, and five day plans. Activities link to theme-plan goals; each non-weekwide activity occupies one day, and day goals are derived from scheduled activities.", "Weekly and daily plans", "Connects approved yearly goals to concrete classroom activities and daily assessment context.", "The service validates activity placement and derives goal references from the saved schedule."),
    ("EDU-17", "A theme dossier combines the theme plan and every weekly/day plan of the theme and is submitted once. It may be submitted only after branch themes are approved and all branch weeks are complete. The theme start date is a reminder, not a blocker.", "Theme dossier submission", "Reviewers approve one complete teaching package instead of separate weekly fragments.", "The service validates dossier completeness and submits the combined document."),
    ("EDU-18", "Branch themes and theme dossiers follow the configured signer chain in order, one actor per step. The Team-Leader step is skipped when no Team Leader exists or that leader is the main teacher of the class. Return requires a reason and restarts the chain after editing.", "Class-document approval", "Avoids self-approval while preserving a configurable, auditable chain.", "The submit action snapshots the chain; approval and return use that snapshot and current assignment rules."),
    ("EDU-19", "Daily child assessment may use only goals from the class's approved weekly/day plan for that date and activity. If no approved plan exists, goal-based assessment is locked.", "Learning observation and assessment", "Assessment evidence must refer to the teaching plan actually approved for the child.", "The observation service resolves approved day lessons before allowing goal-based scores."),
    ("EDU-20", "AI may only draft or rewrite branch-theme names and lesson progress, fills only empty fields and requires the teacher to keep or discard each suggestion. Planning still works when AI is unavailable and all AI-assisted content follows normal approval.", "AI assistance", "Keeps human control and prevents AI from becoming a business dependency or approver.", "The Web marks pending suggestions; the service blocks submit while unreviewed AI content remains."),
    ("EDU-21", "The system does not use image or electronic signatures. Submit, approve and return are recorded digitally; Word export and browser PDF print follow the school template, include actor names and leave space for handwritten signatures.", "Approval record and export", "The school signs the printed official copy while CareNest preserves the workflow history.", "History is recorded on every action; the Web renders Word/print output with handwritten-signature blocks."),
    ("EDU-22", "All planning screens save manually through Save/Ctrl+S or save-before-submit/issue; there is no timed server autosave or automatic retry. Leaving with unsaved changes prompts the user. The Web may keep a hidden per-user/device recovery copy and offers Restore/Discard only when revision and status still match.", "Manual save and recovery", "The author controls server writes without losing recoverable work after an interruption.", "The Web leave guard and recovery store coordinate with the existing session/revision-protected save endpoint."),
    ("EDU-23", "Theme and break dates must be within the school year and an end date cannot precede its start. Drafts may store incomplete or temporarily invalid dates, but the full checks run before submit or issue.", "Themes and breaks", "Allows work in progress while preventing an invalid official schedule.", "requireReadyForReview validates the school-year range and date order on submit and issue."),
    ("EDU-24", "An author may copy selected goals or all goals from one viewable yearly plan, including a different school year. Only goals, contents and required hierarchy are copied; themes, breaks, dates and ticks are not. Target data and the source plan remain unchanged until manual save.", "Copy goals from an existing plan", "Reuses professional content without accidentally importing another plan's schedule.", "The Web filters sources by EDU-06, builds the selected goal patch and saves through the normal endpoint."),
    ("EDU-25", "Editing an Issued yearly plan creates version n+1 from the latest issued version, copying all goals, contents, themes, breaks and ticks with lineage. Only one unissued version may exist; the old version stays current until the new version completes the approval flow.", "Yearly-plan versioning", "Preserves the official record and makes every revision independently reviewable.", "createVersion locks the chain, checks the latest source/open version, copies content and records lineage."),
    ("EDU-26", "Class documents belong to a specific yearly-plan version. Documents of a replaced version become view/print-only, while pending approvals may continue. Copied documents for the new version remap by lineage and block submit until broken/changed references are reviewed.", "Class documents after yearly-plan revision", "Prevents new teaching work from silently using outdated goals or themes.", "The class-document service checks current version, lineage, unresolved references and reviewed-change flags."),
    ("EDU-27", "Each day plan has a separate Adjustment and supplement field, up to 4,000 characters. A class teacher may edit it in any document status, including approved and replaced versions, without changing the document content/status or approval chain.", "Day-plan adjustment", "Lets teachers record real classroom adjustments without reopening the approved plan.", "A dedicated endpoint writes the adjustment and history independently of the document workflow."),
    ("EDU-28", "Ministry goals come from VBHN 01/VBHN-BGDDT dated 13 April 2021 for the matching age group. Users may select individual goals, groups, domains or all; duplicates are skipped; imported hierarchy and source links are retained and labelled Ministry/Edited from Ministry.", "Import from the Ministry programme", "Maintains traceability to the official Early Childhood Education Programme.", "The curriculum APIs filter by age group; save validates every linked sub-domain, group and goal."),
    ("EDU-29", "The education-planning chain is yearly goals by age group, branch themes by class, theme plans, then weekly and daily plans. Downstream documents and child assessment consume the approved goals from the preceding levels.", "End-to-end education planning", "Makes the yearly goals operational in class planning and assessment.", "Services enforce predecessor status, version references and approved-plan lookups across the chain."),
    ("EDU-39", "The author chooses each theme's start and end dates; themes must not overlap. This is enforced before submit/issue, while a draft may keep unfinished dates.", "Theme duration", "Each teaching period must belong to one unambiguous theme.", "requireThemeWeeks sorts dated themes and rejects overlapping ranges."),
    ("EDU-40", "A dated theme must start on Monday and end on Friday so that it covers complete teaching weeks.", "Theme schedule", "Matches the MN Thai Duong weekly planning template.", "requireThemeWeeks rejects non-Monday starts and non-Friday ends before submit/issue."),
    ("EDU-41", "A break may be one day. A week becomes a break week only when the break covers Monday through Friday; shorter breaks leave it as a teaching week and display the affected days as 'Nghi: <break name>'.", "Breaks and teaching weeks", "Short holidays must not remove the whole teaching week.", "The calendar builder calculates teaching/break weeks and day-level break labels."),
    ("EDU-42", "Theme abbreviations must be unique within one plan after trimming spaces and ignoring case. A duplicate blocks draft save immediately; a blank abbreviation is tolerated while drafting but must be filled before submit/issue.", "Theme abbreviations", "Abbreviations identify tick columns and must map to exactly one theme.", "The save service checks duplicates; requireReadyForReview additionally requires non-blank abbreviations."),
    ("EDU-43", "An author may copy selected themes and breaks from a viewable yearly plan. Duplicate theme names/abbreviations are skipped; dates from another school year shift by the year difference; theme ticks are not copied; manual save is still required.", "Copy themes and breaks", "Reuses the annual schedule without importing ambiguous content links.", "The Web applies source-scope checks, deduplication/date shifting and saves through the normal endpoint."),
    ("EDU-46", "While a plan is editable, an author may preview .docx, .doc, .xlsx or .csv files up to 2 MB and 2,000 rows. The backend preview never writes the database. The author chooses Replace all editable sections or Replace only sections present; the Web shows replace/remove/keep, validates scope and mappings, applies only to the form, and requires manual save.", "Full yearly-plan file import", "Lets the author import a complete plan without bypassing permissions, edit sessions, revision control or review validation.", "EduGoalFileService parses the preview; the Web builds the chosen patch; the existing save endpoint performs the only database write."),
    ("EDU-48", "Deleting a sub-domain from an editable draft requires confirmation showing the affected expected-result groups, goals, contents and theme ticks. Confirmation removes the whole branch from the form; other branches, themes, breaks and external-source links remain. Manual save commits the deletion.", "Delete a goal sub-domain branch", "Prevents an apparently small heading deletion from leaving hidden descendants or orphaned ticks.", "The Web computes and confirms the branch impact; the year-plan save service persists the submitted tree and removes omitted descendants."),
]


def rewrite_existing_use_cases(doc):
    for heading, data in USE_CASES.items():
        p = paragraph_exact(doc, heading)
        table = table_after(p, doc)
        set_use_case(table, **data)


def insert_new_use_cases(doc):
    if any(p.text.strip().startswith("4.6 Quản lý phiên sửa") for p in doc.paragraphs):
        raise RuntimeError("New use cases are already present")
    base_heading = paragraph_exact(doc, "4.5 Nhập file kế hoạch năm học")
    template = table_after(base_heading, doc)
    section_start = paragraph_exact(doc, "4. Educational Planning and Review")
    boundary = None
    started = False
    for node in doc.element.body.iterchildren():
        if node is section_start._p:
            started = True
            continue
        if started and isinstance(node, CT_P):
            p = Paragraph(node, doc._body)
            if p.text.strip() in {"5.", "5 Nhập học"} or p.text.strip().startswith("5.1 "):
                boundary = node
                break
    if boundary is None:
        raise RuntimeError("Cannot find use-case section boundary")
    for heading, data in NEW_USE_CASES:
        hp = new_paragraph_like(base_heading, heading)
        tbl = clone_table(template, doc, 6)
        set_use_case(tbl, **data)
        boundary.addprevious(hp._p)
        boundary.addprevious(tbl._tbl)


def replace_functional_content(doc):
    exact = {
        "Lập kế hoạch năm học: mở màn Lập kế hoạch giáo dục năm học ở bước 1 bằng nút Lập kế hoạch năm học (UC-38).": "Lập kế hoạch năm học: mở trang soạn một trang bằng nút Lập kế hoạch năm học; trang hiển thị lần lượt Thông tin chung, Chủ đề, sự kiện trong năm và Mục tiêu, nội dung giáo dục.",
        "Gửi Phó HT duyệt / Ban hành: mở hộp gửi duyệt (Tổ trưởng) hoặc hộp ban hành (Phó Hiệu trưởng) ở bất kỳ bước nào (UC 4.2, UC 4.3).": "Gửi Phó HT duyệt / Ban hành: thanh thao tác cố định hiển thị hành động theo quyền; hệ thống lưu nội dung hiện có trước khi gửi hoặc ban hành (UC 4.2, UC 4.3).",
        "Chuyển bước: bấm Tiếp theo để sang bước 2 Mục tiêu, nội dung và chủ đề; hệ thống kiểm tra các trường bắt buộc trước khi chuyển.": "Cuộn nội dung: toàn bộ ba phần nằm trên cùng một trang, không có stepper, tab bước hoặc nút Tiếp theo/Quay lại; lỗi được hiển thị tại đúng phần và trường liên quan.",
        "Chuyển sang tab Chủ đề và sự kiện: nhập chủ đề, đợt nghỉ trong năm (mục 4.1.4).": "Chuyển đến phần Chủ đề, sự kiện trong năm để nhập chủ đề và đợt nghỉ (mục 4.1.4).",
        "Chuyển sang tab Mục tiêu, nội dung giáo dục: tích nội dung vào các chủ đề vừa nhập (mục 4.1.3).": "Chuyển đến phần Mục tiêu, nội dung giáo dục để tích nội dung vào các chủ đề vừa nhập (mục 4.1.3).",
        "4.1.5 Lập kế hoạch năm học – Xác nhận": "4.1.5 Lưu, gửi duyệt, ban hành và xuất file",
        "Màn hình này cho phép Phó Hiệu trưởng và Tổ trưởng:": "Trang soạn một trang cho phép Phó Hiệu trưởng và Tổ trưởng:",
        "Duyệt lại nội dung: xem lại thông tin chung, bảng mục tiêu, nội dung giáo dục và bảng chủ đề, sự kiện ở dạng chỉ đọc trước khi gửi hoặc ban hành.": "Rà soát trực tiếp: xem và sửa Thông tin chung, Chủ đề, sự kiện trong năm và Mục tiêu, nội dung giáo dục trên cùng một trang trước khi lưu hoặc chuyển trạng thái.",
        "Trên màn hình, họ cũng có thể:": "Trên thanh thao tác cố định, họ cũng có thể:",
        "Xuất: mở hộp Xem trước để in hoặc xuất Word; trên màn soạn, chỉ bước Xác nhận mới có nút Xuất (EDU-14).": "Xuất file: mở hộp Xem trước để in/lưu PDF hoặc xuất Word; nút nằm trên thanh thao tác của trang soạn và được hiển thị theo quyền (EDU-21).",
        "Quay lại: trở về bước trước để sửa.": "Trạng thái lưu: hiển thị ngay bên trái nút Lưu; khi rời trang có thay đổi chưa lưu, hệ thống hỏi Hủy, Không lưu hoặc Lưu (EDU-22).",
        "Gửi kế hoạch để duyệt: Tổ trưởng xác nhận gửi kế hoạch cho Phó Hiệu trưởng, kèm ghi chú nếu cần; kế hoạch chuyển sang Chờ duyệt và bị khóa với người gửi (UC 4.2, EDU-11).": "Gửi kế hoạch để duyệt: Tổ trưởng xác nhận gửi kế hoạch cho Phó Hiệu trưởng, kèm ghi chú nếu cần; kế hoạch chuyển sang Chờ Phó HT duyệt và nhả phiên sửa (UC 4.2, EDU-04).",
        "Ban hành kế hoạch: Phó Hiệu trưởng xác nhận ban hành, kèm ghi chú nếu cần; các lớp của nhóm tuổi dùng kế hoạch đã ban hành làm căn cứ lập kế hoạch chủ đề và giáo án (UC 4.3, EDU-12).": "Ban hành kế hoạch: Phó Hiệu trưởng xác nhận ban hành, kèm ghi chú nếu cần; các lớp của nhóm tuổi dùng phiên bản hiện hành làm căn cứ lập tài liệu lớp (UC 4.3, EDU-05, EDU-12).",
        "Trả lại kế hoạch: Phó Hiệu trưởng trả lại kế hoạch đang Chờ duyệt kèm lý do để Tổ trưởng sửa (UC 4.3, EDU-12).": "Trả lại kế hoạch: Phó Hiệu trưởng trả lại bản đang Chờ duyệt kèm lý do bắt buộc để người soạn sửa và gửi lại (UC 4.3, EDU-05).",
    }
    start = paragraph_exact(doc, "4. Kế hoạch giáo dục")._p

    # Translate legacy SRS-local EDU references before writing any new canonical IDs.
    id_map = {
        "03": "09", "04": "24", "05": "28", "06": "11", "07": "03", "08": "10",
        "09": "22", "11": "04", "12": "05", "13": "21", "14": "21", "15": "06",
        "17": "07", "18": "25", "19": "23", "20": "39", "21": "40", "22": "41",
        "23": "42", "25": "43", "26": "46", "27": "46", "28": "46", "29": "46",
    }

    def remap(text):
        text = text.replace("EDU-26 đến EDU-29", "EDU-46")
        text = text.replace("EDU-10, EDU-19…EDU-23", "EDU-04, EDU-05, EDU-23, EDU-39, EDU-40, EDU-42")
        return re.sub(r"EDU-(\d{2})", lambda m: "EDU-" + id_map.get(m.group(1), m.group(1)), text)

    exact_after_remap = {remap(old): new for old, new in exact.items()}

    started = False
    for node in doc.element.body.iterchildren():
        if node is start:
            started = True
            continue
        if not started:
            continue
        if isinstance(node, CT_P):
            p = Paragraph(node, doc._body)
            if p.text.strip().startswith("5 Nhập học"):
                break
            new = remap(p.text)
            if new != p.text:
                set_paragraph(p, new)
        elif isinstance(node, CT_Tbl):
            table = Table(node, doc._body)
            for row in table.rows:
                seen = set()
                for cell in row.cells:
                    key = id(cell._tc)
                    if key in seen:
                        continue
                    seen.add(key)
                    old = cell.text
                    new = remap(old)
                    if new != old:
                        set_cell(cell, new)

    started = False
    for node in doc.element.body.iterchildren():
        if node is start:
            started = True
            continue
        if not started:
            continue
        if isinstance(node, CT_P):
            p = Paragraph(node, doc._body)
            if p.text.strip().startswith("5 Nhập học"):
                break
            if p.text in exact_after_remap:
                set_paragraph(p, exact_after_remap[p.text])

    # Correct the old three-step field descriptions to the implemented single-page layout.
    t15 = table_after(paragraph_exact(doc, "4.1.2 Lập kế hoạch năm học – Thông tin chung"), doc)
    set_cell(t15.rows[4].cells[1], "Nút theo quyền. Tổ trưởng thấy Gửi Phó HT duyệt; Phó Hiệu trưởng thấy Ban hành. Hệ thống lưu nội dung hiện có trước khi chuyển trạng thái và kiểm đầy đủ điều kiện ở backend (EDU-04, EDU-05, EDU-22).")
    set_cell(t15.rows[5].cells[0], "Bố cục trang", bold=True)
    set_cell(t15.rows[5].cells[1], "Một trang gồm ba phần theo thứ tự: Thông tin chung; Chủ đề, sự kiện trong năm; Mục tiêu, nội dung giáo dục. Không có stepper hoặc tab bước.")
    set_cell(t15.rows[14].cells[0], "Thanh thao tác cố định", bold=True)
    set_cell(t15.rows[14].cells[1], "Bên trái: Lấy từ kế hoạch năm học, Lấy từ Chương trình của Bộ, Nhập file. Bên phải: trạng thái lưu, Lưu, Gửi duyệt/Ban hành và Xuất file. Trên điện thoại các nút tự xuống dòng.")

    t16 = table_after(paragraph_exact(doc, "4.1.3 Lập kế hoạch năm học – Mục tiêu, nội dung giáo dục"), doc)
    set_cell(t16.rows[3].cells[1], "Nút mở hộp Nhập file kế hoạch năm học. Nhận .docx, .doc, .xlsx và .csv; tối đa 2 MB và 2.000 dòng. Backend chỉ trả preview, Web cho chọn hai chế độ thay nội dung và chỉ áp dụng vào form; Lưu/Ctrl+S mới ghi máy chủ (UC 4.5, EDU-46).")
    set_cell(t16.rows[7].cells[0], "Phần mục tiêu, nội dung", bold=True)
    set_cell(t16.rows[7].cells[1], "Hiển thị trực tiếp trên trang, sau phần Chủ đề, sự kiện trong năm; tiêu đề kèm tổng số mục tiêu. Không dùng tab.")
    set_cell(t16.rows[15].cells[1], "Ô tích; mỗi chủ đề ở phần Chủ đề, sự kiện trong năm là một cột có tiêu đề là tên viết tắt. Bấm để tích/bỏ tích. Chưa có chủ đề thì hiển thị hướng dẫn thêm chủ đề ở phần 2.")
    set_cell(t16.rows[17].cells[0], "Kiểm tra khi gửi/ban hành", bold=True)
    set_cell(t16.rows[17].cells[1], "Kế hoạch phải có ít nhất một mục tiêu và câu mục tiêu không được trống. Code hiện tại không bắt buộc mỗi mục tiêu có một dòng nội dung. Bản nháp vẫn được lưu dở (EDU-04, EDU-05, EDU-22).")

    t17 = table_after(paragraph_exact(doc, "4.1.4 Lập kế hoạch năm học – Chủ đề và sự kiện"), doc)
    set_cell(t17.rows[5].cells[1], "Chỉ đọc, ô xám: danh sách mục tiêu có nội dung được tích vào chủ đề ở phần Mục tiêu, nội dung giáo dục.")

    t18 = table_after(paragraph_exact(doc, "4.1.5 Lưu, gửi duyệt, ban hành và xuất file"), doc)
    rows18 = [
        ("Tên trường", "Mô tả"),
        ("Thanh thao tác", "Cố định ở đầu vùng nội dung; bên trái là các nguồn nhập, bên phải là trạng thái lưu và các hành động theo quyền."),
        ("Trạng thái lưu", "Hiển thị Đã lưu/Chưa lưu/Đang lưu hoặc lỗi; đặt ngay bên trái nút Lưu."),
        ("Lưu", "Ghi thủ công bằng nút Lưu hoặc Ctrl+S; kiểm session, revision và request ID; không tự lưu theo thời gian (EDU-10, EDU-22)."),
        ("Gửi duyệt / Ban hành", "Lưu trước rồi gọi hành động theo quyền. Backend kiểm trạng thái, quyền, mục tiêu, tên/viết tắt chủ đề và lịch trước khi chuyển trạng thái (EDU-04, EDU-05, EDU-23, EDU-39, EDU-40, EDU-42)."),
        ("Xuất file", "Mở bản xem trước; cho in/lưu PDF hoặc xuất Word đúng mẫu, chừa chỗ ký tay (EDU-21)."),
    ]
    for ri, (left, right) in enumerate(rows18):
        set_cell(t18.rows[ri].cells[0], left, bold=ri == 0)
        set_cell(t18.rows[ri].cells[1], right, bold=ri == 0)

    t19 = table_after(paragraph_exact(doc, "4.1.6 Chi tiết kế hoạch năm học"), doc)
    set_cell(t19.rows[12].cells[0], "Ba phần nội dung", bold=True)
    set_cell(t19.rows[12].cells[1], "Thông tin chung; Chủ đề, sự kiện trong năm; Mục tiêu, nội dung giáo dục hiển thị nối tiếp trên cùng trang, chỉ đọc và không dùng tab.")

def add_functional_sections(doc):
    if any(p.text.strip().startswith("4.1.15 Phiên sửa") for p in doc.paragraphs):
        raise RuntimeError("New functional sections are already present")
    base_heading = paragraph_exact(doc, "4.1.14 Nhập file kế hoạch năm học")
    base_body = paragraph_exact(doc, "Màn hình này cho phép Phó Hiệu trưởng và Tổ trưởng đang giữ quyền sửa kế hoạch:")
    template = table_after(base_heading, doc)
    boundary = paragraph_exact(doc, "5 Nhập học")._p

    sections = [
        (
            "4.1.15 Phiên sửa, lưu tay và khôi phục bản chưa lưu",
            [
                "Phần này mô tả cơ chế bảo vệ bản soạn khi Phó Hiệu trưởng hoặc Tổ trưởng chỉnh sửa kế hoạch năm học.",
                "Mỗi tab sử dụng một session ID; backend kiểm session, revision và request ID cho mọi thao tác ghi hoặc chuyển trạng thái.",
                "Hệ thống chỉ ghi khi người dùng bấm Lưu/Ctrl+S hoặc gửi/ban hành; bản cục bộ chỉ dùng để mời khôi phục sau sự cố.",
                "Mô tả trường",
            ],
            [
                ("Tên trường", "Mô tả"),
                ("Bắt đầu chỉnh sửa", "Web gọi start-editing kèm session ID, revision và request ID. Backend khóa dòng, kiểm quyền/trạng thái và cấp phiên (EDU-03, EDU-10)."),
                ("Heartbeat", "Gửi mỗi 20 giây. Khóa hết hạn sau 90 giây mất liên lạc; job dọn khóa chạy nền nhưng mọi request cũng tự coi khóa hết hạn là trống."),
                ("Cùng tài khoản mở lại", "Phiên mới nhận quyền ngay; phiên cũ bị từ chối khi heartbeat/lưu. Người khác vẫn phải chờ phiên được nhả hoặc hết hạn."),
                ("Lưu / Ctrl+S", "Ghi toàn bộ form qua endpoint PUT hiện có. Request ID lặp lại trả kết quả an toàn; revision cũ hoặc session sai bị từ chối."),
                ("Rời màn", "Có thay đổi chưa lưu thì hỏi Hủy, Không lưu hoặc Lưu; đóng/tải lại tab dùng cảnh báo trình duyệt. Rời bình thường gọi finish-editing."),
                ("Bản phục hồi", "Ẩn theo tài khoản + kế hoạch/slot trên thiết bị. Chỉ mời Khôi phục/Bỏ khi bản máy chủ chưa đổi revision/trạng thái; không tự ghi máy chủ (EDU-22)."),
                ("Mất quyền hoặc xung đột", "Giữ bản cục bộ để người dùng sao chép/khôi phục khi hợp lệ; không ghi đè phiên hoặc revision mới hơn."),
                ("Trạng thái Chờ duyệt", "Phó Hiệu trưởng có thể mở phiên sửa nhưng trạng thái nền vẫn là Chờ Phó HT duyệt; mọi sửa được ghi lịch sử."),
            ],
        ),
        (
            "4.1.16 Xóa lĩnh vực con và toàn bộ nhánh",
            [
                "Phần này cho phép người đang giữ quyền sửa xóa một lĩnh vực con cùng toàn bộ dữ liệu nằm dưới nhánh đó.",
                "Hộp xác nhận phải nêu rõ số nhóm kết quả mong đợi, mục tiêu, nội dung và dấu tích chủ đề sẽ bị xóa.",
                "Thao tác chỉ đổi form; người dùng phải bấm Lưu/Ctrl+S để ghi lên hệ thống (EDU-22, EDU-48).",
                "Mô tả trường",
            ],
            [
                ("Tên trường", "Mô tả"),
                ("Xóa lĩnh vực con", "Nút thùng rác tại dòng lĩnh vực con; chỉ bật khi kế hoạch đang sửa được và session còn hợp lệ."),
                ("Phạm vi ảnh hưởng", "Chỉ đọc: tên nhánh và số nhóm kết quả mong đợi, mục tiêu, nội dung, dấu tích chủ đề sẽ bị xóa."),
                ("Hủy", "Đóng hộp xác nhận, giữ nguyên form."),
                ("Xác nhận xóa", "Loại toàn bộ nhánh khỏi form; giữ các nhánh khác, chủ đề, đợt nghỉ và nguồn Bộ ngoài nhánh."),
                ("Lưu", "Backend lưu cây mới và xóa các dòng bị bỏ sau khi cập nhật/chuyển các dòng còn giữ, tránh cascade nhầm. Session/revision sai thì từ chối."),
            ],
        ),
    ]

    for heading, body_lines, rows in sections:
        hp = new_paragraph_like(base_heading, heading)
        boundary.addprevious(hp._p)
        for line in body_lines:
            bp = new_paragraph_like(base_body, line)
            boundary.addprevious(bp._p)
        tbl = clone_table(template, doc, len(rows))
        for ri, (left, right) in enumerate(rows):
            set_cell(tbl.rows[ri].cells[0], left, bold=ri == 0)
            set_cell(tbl.rows[ri].cells[1], right, bold=ri == 0)
        boundary.addprevious(tbl._tbl)


def sync_business_rules(doc):
    heading = paragraph_exact(doc, "1.6 Educational plan (EDU)")
    table = table_after(heading, doc)
    required_rows = 1 + len(BUSINESS_RULES)
    while len(table.rows) < required_rows:
        table.add_row()
    while len(table.rows) > required_rows:
        table._tbl.remove(table.rows[-1]._tr)
    for ri, record in enumerate(BUSINESS_RULES, 1):
        for ci, value in enumerate(record):
            set_cell(table.rows[ri].cells[ci], value, bold=ci == 0)


def main():
    doc = Document(SOURCE)
    original_tables = len(doc.tables)
    rewrite_existing_use_cases(doc)
    insert_new_use_cases(doc)
    replace_functional_content(doc)
    add_functional_sections(doc)
    sync_business_rules(doc)
    doc.save(OUTPUT)
    print(f"output={OUTPUT}")
    print(f"tables_before={original_tables}")
    print(f"tables_after={len(doc.tables)}")
    print(f"paragraphs_after={len(doc.paragraphs)}")


if __name__ == "__main__":
    main()
