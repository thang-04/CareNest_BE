# Validation — Feature009

## Phase2 BE
Run targeted parser/service tests bằng mvnw hiện có, RED trước code rồi GREEN sau. Chạy `node scripts/verify.mjs` sau thay đổi cuối (full tests/ArchUnit/OpenAPI/Spotless); `node scripts/check-ai-layer.mjs` cho docs. Sample probe dùng file cục bộ ở máy user, chỉ in counts, không đưa nội dung/người ký vào log hoặc repo. Ghi oracle độc lập và giới hạn khi đã đo. Review độc lập và user review trước Web.

## Phase3 FE sau review
Run tests education-plan + `node scripts/verify.mjs`; browser source/CSS thật375/768/1440: row/button baseline/overflow/focus/errors, preview metadata/lịch/năm dời/age conflict, confirm giữ nhập tay/ticks, cancel/race/lostlease. Real-BE E2E file/save/reopen và old formats, không ghi credential hoặc copy file thật vào fixture.

## Evidence
Phase2 đã có output thật sau source/test cuối: `VERIFY PASS full | tests 659 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 278s | log target/verify.log`. Log full giữ tại `target/feature009-final-full.log` trước lần verify hồ sơ cuối. Snapshot thay53dòng additive chỉ3schema mới và3field metadata/themes/breaks, không đổi endpoint/request/schema request cũ.

- TDD domain: RED4fail → GREEN4, cùng10legacy parser đạt14/14. Preview đầu RED5fail→GREEN5; bổ sung regressions metadata warning/boundary/multiple ranges/docx. Trước source boundary cuối targeted+snapshot31/31 đạt; full659 cuối đã chạy11test FullYearPlanFileImportTest và4test YearPlanDomainVariantTest, fail/skip0.
- Metadata sau bảng: probe với classes trước fix RED AssertionError, sau fix GREEN; regression Java `ageAfterGoalTableIsNotMetadata` nằm trong full cuối. Metadata trong bảng không được lấy làm nhóm tuổi; cảnh báo metadata được copy sau khi parse.
- Sample gốc cục bộ qua assertions trên target/classes cuối:5lĩnh vực83MT203nội dung389ticks,10chủ đề đúng từng cặp ngày/viết tắt,2nghỉ16–20/02/2026 và27/04–01/05/2026,metadata2025-2026/36–48tháng,0warning. Số MT và10cặp ngày/viết tắt đối chiếu độc lập; content/ticks là counts reader, không gọi là oracle độc lập. Không copy source/người ký vào repo.
- Hai vòng review độc lập đã phát hiện/sửa warning-copy-order, multiple-date-range truncation và metadata sau bảng; review cuối không còn lỗi chặn. Snapshot full không skip, ArchUnit/Spotless đạt.
- Chưa làm phase3Web: merge/preview toàn bộ và chặn nhóm tuổi/dời năm trên UI, hàng nút đồng kiểu, phần1dạngbảng, browser/real-BE E2E/save-reopen/converge. Không đánh dấu toàn feature hoàn tất, không commit/push/deploy. Cần user review backend theo plan trước Web.

