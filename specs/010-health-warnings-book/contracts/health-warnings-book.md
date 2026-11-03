# Contract — Cảnh báo sức khỏe (Feature010)

Snapshot: `docs/api/openapi.yaml`. Consumer: FE (BE giả `src/services/children/mock/*` mirror contract này); APP chưa gọi.

## Enum `HealthBmiStatus`
`SEVERE_THINNESS` Gầy còm nặng · `THINNESS` Gầy còm · `NORMAL` Bình thường · `OVERWEIGHT_RISK` Nguy cơ thừa cân · `OVERWEIGHT` Thừa cân · `OBESE` Béo phì.

## `HealthMeasurementResponse` (mọi API trả lần đo)
- Thêm: `bmiStatus` (enum | null), `bmiZScore` (number 2 chữ số | null) — chỉ lần đo kỳ cân đo (`source = MEASUREMENT_ONLY`), trẻ 0–84 tháng.
- Bỏ: `followUpId`, `followUpEffect`, `parentContact`.

## `GET /children/{childId}/health` — `ChildHealthRecordResponse`
- Bỏ: `latestHealth`, `openFollowUp`, `externalResults`.
- Thêm: `latestExam {measurementId, campaignId, campaignName, date, followUpLevel, conclusionCategory, conclusion, knownCondition}` | null; `latestBmi {measurementId, campaignId, campaignName, date, ageMonths, bmi, bmiStatus, bmiZScore}` | null.

## `/health-checks`
- `HealthCheckResponse`: bỏ `waitingFollowUpCount`, `overdueFollowUpCount`; `progress.bmi {severeThinness, thinness, overweightRisk, overweight, obese}` (đợt khám luôn 0).
- `HealthCheckDetailResponse.classes[]`: thêm `bmiWarning`. `watchList`: đợt khám — WATCH/CLOSE_WATCH (theo dõi sát trước); kỳ cân đo — BMI khác bình thường (gầy còm nặng/béo phì trước, nguy cơ thừa cân sau cùng); rồi theo tên.
- `HealthCheckClassResponse.children[]`: bỏ `followUp`; thêm `bmi`, `bmiStatus`.
- `POST /{id}/close` — `HealthCheckCloseResponse`: bỏ `waitingFollowUpCount`.

## Phiếu khám `GET|PUT /health-checks/{id}/children/{childId}/exam`
- Bỏ: `openFollowUp`, `latestExternalResult`. `conclusionMode`: `STANDARD`; `FOLLOW_UP` chỉ với phiếu đã kết luận theo mẫu cũ. 409 5032 nếu client gửi mẫu khác (message mới).

## Bỏ
- `GET /health-follow-ups`, `GET /health-follow-ups/{id}`, `POST /{id}/parent-contacts`, `POST /{id}/external-results`, `POST /{id}/close`, `GET /{id}/images/{imageId}`.
- Mã lỗi 5035–5039 (không dùng lại).
