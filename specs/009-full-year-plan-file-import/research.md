# Research — Feature009

## Decoder và fidelity
Decision: tái dùng GoalFileGrid/POI, giữ giới hạn; parse metadata/chủ đề bằng interpreter riêng cho kế hoạch năm để branch import không bị đổi semantics. Rationale: đã có decoder4format/merged cells/zip bounds. Alternative: parser Word ở Web hoặc thêm thư viện — bỏ vì trùng xử lý và ngoài scope.
Evidence: GoalFilePreviewResponse hiện chỉ domains/themeAbbrs/warnings; GoalFileTable trả bảng A đầu tiên; metadata ngoài bảng bị bỏ. Probe file mẫu cần rebuild/đối chiếu độc lập; nguyên nhân fidelity và oracle sẽ bổ sung khi research xong.

## Liên kết chủ đề
Decision: ưu tiên cột viết tắt rõ ràng; bảng lịch không có mã chỉ ghép ordinal khi STT liên tục 1..N và N đúng số cột chủ đề, không có bảng lịch cạnh tranh; sai/mơ hồ cảnh báo. Rationale: các mã như TN không suy được từ chữ cái đầu; thứ tự trong mẫu là quan hệ rõ có thể kiểm tra. Alternative: tự sinh viết tắt — làm mất dấu tích.

## Contract và persistence
Decision: cùng endpoint additive metadata/themes/breaks, không ghi, không đổi schema. Constructor cũ tương thích source tests. Save/permission/revision/lease cũ vẫn quyết định ghi. Metadata tuổi là khoảng tháng, không ID được lấy từ file; FE đối chiếu cấu hình/nhóm hiện có.

## UI
Decision: cùng outlined primary/sm một hàng desktop, status riêng; overflow-x nằm trong thanh ở màn hẹp, thông tin chung nhập trực tiếp trong bảng theo yêu cầu bổ sung của user, vẫn có label/error và read-only. Rationale: phản hồi user và DESIGN.md hiện có; không bịa quy tắc từ query UX chưa khớp. Alternative: ép chữ nhỏ hoặc giấu nút — bỏ.

## Kết quả fidelity và sample oracle — 02/11
Đã chứng minh kĩ/kỹ và mĩ/mỹ làm domain IV/V không khớp; chỉ chuẩn hóa khóa domainOf/exactDomain, không đổi text. Raw STT thiếu52/53 nên 81 STT không phải oracle; hợp STT và số đầu câu độc lập là1..83, phân bố18/24/15/14/12. Classes sửa đọc5lĩnh vực/83MT/203nội dung/389ticks,10chủ đề,2đợt nghỉ,metadata2025-2026/36-48tháng. Số MT và ngày lịch được đối chiếu độc lập; content/ticks là số reader đo, không giả là oracle độc lập.
Review độc lập chỉ ra warnings metadata bị copy trước khi metadata thêm cảnh báo và multiple date ranges bị lấy hai ngày đầu; đã thêm RED tests và sửa theo cảnh báo không đoán. Metadata chỉ ngoài bảng, tránh coi câu mục tiêu có khoảng tuổi là nhóm tuổi nguồn. New nested schema dùng GoalFileMetadata/GoalFileTheme/GoalFileBreak tránh trùng tên schema request Theme/Break.
