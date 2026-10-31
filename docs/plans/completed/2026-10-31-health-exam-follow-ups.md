---
title: BE phiếu khám Phụ lục II, phiếu theo dõi sức khỏe, mức theo dõi
status: done
owner: carenestg94@gmail.com
jira: G94-180 / BE-FEAT-43
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [health, child]
rules: [HLT-06, HLT-08, HLT-09, HLT-10, HLT-11, HLT-12, HLT-13, HLT-16]
risk: high
created: 2026-10-31
updated: 2026-10-31
---

# BE phiếu khám Phụ lục II, phiếu theo dõi sức khỏe, mức theo dõi

## Context

- User (2026-10-31): "Phân loại WHO, báo cáo 5.1 và 5.3 tạm bỏ… hãy làm đầy đủ API để thay dần cho mock", sau đó "agent chạy xong thì hãy code BE luôn".
- Nền: plan `docs/plans/completed/2026-10-31-health-check-rounds.md` (đợt khám, kỳ cân đo, số đo; commit 805c336, 03e07d7). Plan đó để lại phiếu khám, phiếu theo dõi, mức theo dõi (B1).
- Nguồn hành vi: mock FE `FE:src/services/children/mock/healthExamMockRepository.js`, `healthCheckRule.js`, `healthCheckMockRepository.js`, `healthScreeningRule.js`, `FE:src/models/children/healthExamForm.js`, `FE:src/utils/children/healthExamValidation.js`, `childrenMockRepository.js` (`latestHealthOf`, `getHealthRecord`).

## Quyết định đã chốt (user, 2026-10-31)

1. **Lưu là công bố:** kết luận phiếu khám là công bố cho phụ huynh; nháp không công bố; sửa phiếu đã kết luận là công bố lại (HLT-11 sửa theo).
2. **Phiếu Phụ lục II như mock:** hành chính lấy từ hồ sơ trẻ (chỉ đọc), tiền sử điền sẵn từ khai báo, sinh tồn, dinh dưỡng, phát triển, tiêm chủng, lâm sàng, kết luận; lưu nháp / kết luận; sửa sau kết luận cần lý do; kết luận ghi tiền sử ngược về khai báo.
3. **Mức theo dõi và phiếu theo dõi như mock:** mức lấy từ kết luận của bác sĩ (NONE / WATCH / CLOSE_WATCH / KNOWN_STABLE); chuyển khám chuyên khoa hoặc nguy cơ lao mở phiếu theo dõi; GV lớp ghi trao đổi phụ huynh, kết quả chuyên khoa, đóng phiếu; BGH chỉ xem. HLT-12 chỉ áp cho suy dinh dưỡng/thừa cân.
4. **Chỉ hiện quá hạn:** không có job nhắc, không sự kiện nhắc; hạn trao đổi, số ngày quá hạn tính khi đọc; lọc "quá hạn".
5. **Ảnh kết quả chuyên khoa:** tối đa 3 ảnh, lưu S3, chỉ GV/BGH trong phạm vi xem được.
6. Hoãn: phân loại WHO, báo cáo 5.1, 5.3.

**Mặc định (tự chọn theo chế độ tự chạy, user đổi được):**
- **C1 — Lưu trữ:** phiếu khám là lần đo của đợt khám (`health_measurements` thêm `exam` JSONB, `exam_status`, `concluded_at`, `published_by`); nháp cho phép thiếu chiều cao/cân nặng, chưa công bố. Lịch sử sửa dùng chung `health_measurement_edits` (`changes` thêm `label`, chuỗi hiển thị).
- **C2 — Lỗi theo ô:** 400 + `data: [{ field, message }]` (C-5), không dùng 422 như mock; khóa ô giữ đường dẫn của mock (`exam.vitals.temperatureC`...) để FE nhảy đúng bước.
- **C3 — Kiểu dữ liệu:** số là số JSON hoặc null, ngày `YYYY-MM-DD` hoặc null (mock dùng chuỗi rỗng); nháp vẫn kiểm khoảng chiều cao/cân nặng/vòng đầu và ngày trùng (ràng buộc DB), ngoài ra không kiểm.
- **C4 — Loại đợt:** API phiếu khám chỉ cho đợt `EXAM` (mock không kiểm, spec §7.1); kỳ cân đo trả 400.
- **C5 — Phạm vi:** xem theo `ChildAccessPolicy`/`ClassroomAccessPolicy` (lớp trẻ đang học hôm nay); ngoài phạm vi trả 404 như không tồn tại. Ghi: GV đang phân công lớp hiện tại của trẻ.
- **C6 — Ảnh:** gửi trong JSON dạng data URL (như FE đang gửi), JPG/PNG/WEBP, ≤ 1 MB/ảnh sau giải mã, kiểm chữ ký tệp; lưu cùng bucket tài liệu giáo dục, prefix `health/follow-up-results/`; đọc qua `GET /health-follow-ups/{id}/images/{imageId}` (kiểm phạm vi).
- **C7 — Không thông báo:** BE chưa có module thông báo (như B5 plan trước): không gửi HEALTH_FOLLOW_UP, CHILD_TB_CONTACT_DATE_CHANGED, nhắc hạn.
- **C8 — Trẻ ra trường:** khi chuyển năm học đánh dấu trẻ ra trường (`ChildrenLeftSchoolEvent`), phiếu theo dõi đang mở của trẻ tự đóng (không đổi mức). BE chưa có thao tác cho một trẻ nghỉ giữa năm.
- **C9 — Không còn "mẫu cũ":** BE chưa từng cho nhập số đo vào đợt khám (kỳ cân đo mới nhận số đo) nên không có `legacyMeasurementId`.
- **C10 — Không trả `criteria`, `reference{code,label}` của chuẩn phân loại:** phân loại WHO hoãn; mức chỉ từ kết luận bác sĩ.

## Làm rõ nghiệp vụ

- **Hiểu nghiệp vụ:** đợt khám đang diễn ra → GV lớp mở phiếu khám từng trẻ → `[S]` điền sẵn hành chính (hồ sơ), tiền sử (khai báo), người đi cùng → GV lưu nháp nhiều lần → nhập kết luận bác sĩ → `[S]` kiểm đủ phiếu, tính mức, công bố, ghi tiền sử về khai báo, mở/cập nhật phiếu theo dõi khi mức "theo dõi sát" → GV ghi trao đổi phụ huynh (hạn 7 ngày từ lúc lưu kết quả), kết quả chuyên khoa (kèm ảnh), đóng phiếu theo lời bác sĩ → `[S]` mức hiện tại của trẻ đổi theo cách đóng.
- **Đối chiếu:** HLT-08 (nội dung khám Phụ lục II), HLT-10 (mức từ kết luận bác sĩ, phân loại hoãn), HLT-11 (lưu là công bố), HLT-12 (chỉ dinh dưỡng), HLT-06 (khai báo cập nhật từ phiếu khám/phiếu theo dõi) ⇒ sửa `BUSINESS_RULES.md`.
- **Ảnh hưởng:** module health (phiếu khám, phiếu theo dõi, danh sách lớp, chi tiết đợt, sổ sức khỏe), child (đọc/ghi tiền sử của khai báo), integration/storage (ảnh). FE: `/children/health-checks/*/exam`, `/children/health-follow-ups*`, Sổ sức khỏe (đang dùng mock). APP: chưa dùng.

### Câu hỏi mở

Không còn câu hỏi chặn; mặc định C1–C10 ghi ở trên.

## API

| Method | Path | Ai | Ghi chú |
| --- | --- | --- | --- |
| GET | `/health-checks/{id}/children/{childId}/exam` | HT, PHT, GV | Phiếu khám của trẻ trong đợt: phiếu đã lưu hoặc bản nháp điền sẵn, mốc tuổi, lần khám trước, phiếu theo dõi đang mở |
| PUT | `/health-checks/{id}/children/{childId}/exam` | GV lớp | `conclude=false` lưu nháp; `true` kết luận (công bố); sửa phiếu đã kết luận cần `reason` |
| POST | `/health-checks/{id}/children/{childId}/exam/review-preview` | HT, PHT, GV | `{ reviewPoints }`, không ghi |
| GET | `/health-follow-ups?campusId&status&overdue&schoolYear` | HT, PHT, GV | `{ items, counts }` |
| GET | `/health-follow-ups/{id}` | HT, PHT, GV | Chi tiết: dòng thời gian, các lần khám |
| POST | `/health-follow-ups/{id}/parent-contacts` | GV lớp | `{ contactedOn, note }` |
| POST | `/health-follow-ups/{id}/external-results` | GV lớp | `{ resultOn, note, facility, images[], nextVisitOn }` |
| POST | `/health-follow-ups/{id}/close` | GV lớp | `{ outcome, conditionText, reason }` |
| GET | `/health-follow-ups/{id}/images/{imageId}` | HT, PHT, GV | Ảnh kết quả chuyên khoa |

Đổi API đã có: danh sách/chi tiết/danh sách lớp/đóng đợt thêm số liệu phiếu khám, phiếu theo dõi; Sổ sức khỏe thêm `latestHealth`, `openFollowUp`, `externalResults`, trường phiếu khám ở mỗi lần đo; sửa số đo của phiếu khám qua API số đo trả 409.

## DB (V41)

- `health_measurements`: thêm `exam`, `exam_status`, `concluded_at`, `published_by`; `height_cm`, `weight_kg`, `published_at` cho phép null chỉ với nháp (CHECK).
- `health_follow_ups` (mỗi trẻ tối đa một phiếu mở), `health_follow_up_events` (dòng thời gian; mỗi lần khám thuộc tối đa một phiếu), `health_follow_up_images`.
- `child_health_declaration_history.action` thêm `UPDATED_FROM_EXAM`.

## Acceptance criteria

- [x] AC-1: GV lớp lưu nháp phiếu khám nhiều lần; nháp không công bố, không tính là kết quả ở Sổ sức khỏe; danh sách lớp hiện "Đang khám".
- [x] AC-2: Kết luận thiếu thông tin trả 400 kèm ô sai; đủ thì công bố, mức theo dõi theo kết luận; sửa phiếu đã kết luận bắt buộc lý do, lưu lịch sử thay đổi, công bố lại; lưu nháp phiếu đã kết luận trả 409.
- [x] AC-3: Kết luận có nguy cơ lao hoặc chuyển khám chuyên khoa mở phiếu theo dõi "Chờ trao đổi phụ huynh" hạn 7 ngày; có người đi cùng thì ghi trao đổi tại buổi khám.
- [x] AC-4: GV ghi trao đổi, kết quả chuyên khoa (tối đa 3 ảnh), đóng phiếu; quá hạn tính khi đọc; BGH xem, không ghi.
- [x] AC-5: Kết luận ghi tiền sử về khai báo (`UPDATED_FROM_EXAM`); đóng phiếu "vấn đề đã biết" thêm vào tiền sử bản thân (`UPDATED_FROM_FOLLOW_UP`).
- [x] AC-6: Sổ sức khỏe trả mức hiện tại (`latestHealth`) theo lần khám gần nhất và phiếu theo dõi.
- [x] AC-7: Không đổi hành vi kỳ cân đo; `node scripts/verify.mjs` PASS.

Bằng chứng: `HealthExamFollowUpIntegrationTest` (4 test: nháp → kết luận → sửa có lý do; phiếu theo dõi đủ vòng tới đóng "vấn đề đã biết"; người đi cùng tại buổi khám, quá hạn tính khi đọc, đóng đợt; ảnh), `HealthExamLogicTest` (32 test, đối chiếu FE `validateHealthExam`, `examReviewPoints`, `examReferenceFor`, `diffExam` chạy trong Node), `HealthCheckIntegrationTest` (6 test kỳ cân đo giữ nguyên hành vi, trừ ngày cuối đo vòng đầu).

## Ghi chú cho FE khi bỏ mock

- Lỗi theo ô: 400 + `data: [{ field, message }]` (mock: 422 + map). Khóa ô giữ như mock.
- Số là số JSON hoặc null, ngày `YYYY-MM-DD` hoặc null (mock dùng chuỗi rỗng).
- Không có `legacyMeasurementId`, `child.schoolName`, `criteria`, `reference{code,label}`, `byConclusion` (C9, C10).
- Sổ sức khỏe: `latestHealth`, `openFollowUp`, `externalResults` ở cấp ngoài (mock để `latestHealth` trong `record.child`).
- Ảnh: `images: [{ id, path }]`; tải `path` bằng axios (blob, có token), không đặt thẳng vào `<img src>`.
- Diff sửa phiếu khám: chuỗi hiển thị như mock (số dạng `98,5`, ngày khám `YYYY-MM-DD`); diff sửa số đo kỳ cân đo vẫn số dạng `98.5` (có thêm `label`).
- Ngày cuối đo vòng đầu: trước ngày trẻ đủ 61 tháng (như mock `headCircumferenceUntil`; trước đây BE dừng ở ngày đủ 60 tháng).

## Progress log

- 2026-10-31: tạo plan; agent trích đặc tả mock (phiếu khám, phiếu theo dõi, mức hiện tại).
- 2026-10-31: V41; entity, repo; logic thuần (catalog, chuẩn hóa, kiểm phiếu, điểm cần xem lại, diff — agent port, đối chiếu FE); `HealthRecords` (đọc một lần), `HealthFollowUpState`, `HealthFollowUpTracker`; service + controller phiếu khám, phiếu theo dõi; nối số liệu vào đợt/kỳ, danh sách lớp, sổ sức khỏe; ghi tiền sử về khai báo (module child); ảnh S3 (`HealthImageStorage`); tự đóng phiếu khi trẻ ra trường.
- 2026-10-31: rule HLT-08, HLT-10, HLT-11, HLT-12 sửa, thêm HLT-16; DATA_DICTIONARY, ERROR_CONTRACT (5027–5039), module card, flow, CURRENT_STATE, OpenAPI snapshot.
- 2026-10-31: `node scripts/verify.mjs` PASS full (693 test, 0 lỗi; ArchUnit, snapshot OpenAPI, Spotless ok); `check-ai-layer` 0 lỗi. Chưa commit.
