# Module — learning-observation

Feature: `observation` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đã có quan sát tối thiểu + nhận xét tuần/kỳ (AI/template)

## Mục đích
Hồ sơ phát triển liên tục của trẻ: quan sát hằng ngày có cấu trúc, ngữ cảnh hoạt động, đánh giá, follow-up, nhận xét tổng hợp.

## Owns
ObservationCriteria/Option (cấu hình), Observation, ActivityContext (source-agnostic), ActivityParticipation, Assessment, FollowUp, Summary (DRAFT/APPROVED), Reward (PROPOSED — OBS-10), DevelopmentProfile (read model).

## KHÔNG owns
Giáo án soạn/duyệt (GoKids — OBS-08), dữ liệu sức khỏe/điểm danh/bữa ăn (chỉ đọc).

## Rules
OBS-01..11, AI-01..06. Flow: `docs/business/flows/child-observation.md`.

## Phụ thuộc
- Dùng: service `child`, service `attendance`, service `meals`, service `health` (chỉ query), `AiClient` (`integration/`).
- Được dùng bởi: reporting.
- Module downstream nhất trong child record — không module domain nào được import learning.

## API & bảng
- **Bảng:**
  - `observation_criteria`, `criteria_options` (V8): tiêu chí cấu hình, có score.
  - `observations` + `observation_values` (V8): 1 bản/trẻ/ngày, upsert.
  - `child_summaries` (V9): `ai_draft` bất biến, `content` GV sửa, DRAFT → APPROVED.
- **API:**

  | Method | Path | Role |
  |---|---|---|
  | GET | `/observation-criteria` | GV, VP, HT |
  | POST | `/classrooms/{classroomId}/observations` | GV của lớp; batch cả lớp cho 1 ngày |
  | GET | `/children/{childId}/observations?from&to` | GV, VP, HT; tối đa 184 ngày |
  | POST | `/children/{childId}/summaries/draft` | GV |
  | GET | `/children/{childId}/summaries` | GV, VP, HT |
  | PUT | `/summaries/{summaryId}` | GV |
  | POST | `/summaries/{summaryId}/approve` | GV |

  `POST /children/{childId}/summaries/draft` body là `{periodType: WEEK|TERM, periodFrom, periodTo}`. Kỳ WEEK ≤ 7 ngày, TERM ≤ 185 ngày. Nếu kỳ đó đã có nháp thì trả lại bản cũ.
- **Luồng nhận xét:**
  1. Facts tính bằng code: đếm theo tiêu chí và theo tuần, tối đa 20 ghi chú × 300 ký tự.
  2. Ẩn danh dữ liệu.
  3. RAG lấy tài liệu `CHILD_DEVELOPMENT_STANDARD` và `SCHOOL_SOP`.
  4. `AiClient` soạn nháp.
  5. Lưu DRAFT `source=AI`. AI tắt/lỗi thì dùng template `source=TEMPLATE`, response có `aiErrorCode`.
- Dev seed: 5 tiêu chí mẫu, 2 tuần quan sát cho 2 trẻ lớp A1.

## PENDING
P-01/OBS-07 (ActivityContext source — ADR-0006 OPEN), P-11 (tiêu chí), P-12 (summary có gửi phụ huynh), P-16b (assessment).

## Known pitfalls
- Không enum cứng tiêu chí quan sát (OBS-02).
- DevelopmentProfile không copy dữ liệu nguồn vào bảng riêng; đọc qua service của các feature nguồn.
- Summary template baseline phải chạy khi AI tắt (OBS-04).
- Không mở rộng thành quản lý giáo án khi làm ActivityContext.

## Related issues
Chưa có.

## Đọc thêm khi
Đụng ActivityContext ⇒ ADR-0006 (bắt buộc). Đụng AI summary ⇒ card ai-assistance, ADR-0008.
