---
title: Giữ lịch sử chuyển lớp cho phiếu bé ngoan và bé chăm
status: done
owner: G94 team
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [child, attendance, observation]
rules: [G94-180/CHD-03, G94-180/CHD-09, OBS-10, OBS-23]
risk: high
created: 2026-11-21
updated: 2026-11-21
---
# Giữ lịch sử chuyển lớp cho phiếu bé ngoan và bé chăm

## Context

Trẻ chuyển lớp giữa tuần/tháng phải giữ cờ, ngày có mặt và phiếu đã phát để lớp mới
xét tiếp, nhưng lớp cũ không được phát phiếu mới sau ngày hiệu lực. Code hiện cộng cờ
và ngày có mặt theo trẻ (`BehaviourTicketServiceImpl:484-497`) nhưng DTO không trả
nguồn lớp/lịch sử, nên Web không thể giải thích số liệu cho cô.

## Quyết định đã chốt

1. Phiếu, cờ và chuyên cần thuộc trẻ; lớp chỉ là nơi phát/ghi nhận.
2. Lớp mới xem tổng số trong kỳ và phần đóng góp của từng lớp; chỉ lớp trẻ đang thuộc
vào ngày phát mới được phát phiếu.
3. Lớp cũ chỉ xem lịch sử, không phát/sửa sau ngày hiệu lực chuyển hoặc nghỉ.
4. Lớp ghép ngoài phạm vi theo chỉ đạo user ngày 21/11/2026.

## Làm rõ nghiệp vụ

- **Hiểu nghiệp vụ:** GV xem căn cứ cờ/chuyên cần của trẻ qua lớp cũ để phát phiếu đúng
  kỳ; BE quyết định quyền và dữ liệu. Rule `OBS-10`, `OBS-23`, `G94-180/CHD-03`,
  `G94-180/CHD-09` (CONFIRMED).
- **Ảnh hưởng:** child enrollment lịch sử; attendance; observation/ticket; Web
  `assessmentApi.js` và màn phiếu bé ngoan. Không chạm kế hoạch giáo dục.
- **Dữ liệu cũ:** không migration; suy nguồn từ attendance/observation/enrollment và
  phiếu đã lưu `classroom_id`.

### Câu hỏi mở

Không có: user đã chốt hiển thị tổng và nguồn lớp, lớp cũ chỉ xem.

## Acceptance criteria

- **AC-1** (`OBS-10`): Given trẻ chuyển lớp trong một tuần, When lớp mới mở bảng tuần,
  Then thấy tổng ngày có mặt/cờ của cả tuần và chi tiết theo lớp.
- **AC-2** (`OBS-10`): Given trẻ không còn thuộc lớp tại ngày phát, When GV lớp cũ phát,
  Then BE từ chối; lớp mới có thể phát nếu đủ điều kiện và chưa phát.
- **AC-3** (`OBS-23`): Given trẻ chuyển hoặc nghỉ trong tháng, When xem chất lượng tháng,
  Then ngày phải đi/chuyên cần chỉ dùng thời gian thuộc từng lớp, không nhân đôi trẻ.
- **AC-4** (`G94-180/CHD-03`): Given trẻ đã có dữ liệu hôm nay, When chuyển/nghỉ,
  Then chỉ cho hiệu lực ngày học kế tiếp theo lịch năm học.

## Key decisions

| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Nguồn cờ tuần | Theo trẻ, phân tách lớp ghi nhận | Chỉ tính lớp hiện tại | Không làm mất căn cứ trước chuyển |
| Phát phiếu | Theo lớp đang có enrollment tại ngày phát | Lớp từng dạy đều phát được | Chặn phát trùng và đúng trách nhiệm lớp |
| Ngày hiệu lực | Ngày học tiếp theo theo lịch trường | Ngày lịch tiếp theo | Không mở chuyển lớp vào ngày nghỉ/lễ |

## Phases

### Phase 1 — API và quy tắc dữ liệu
- Files: M `ChildRecordServiceImpl`, `BehaviourTicketServiceImpl`, `TicketDtos`, tests gần nhất.
- Steps: tính ngày học kế tiếp; trả phần căn cứ tổng/theo lớp và trạng thái lịch sử; giữ endpoint cũ,
  chỉ bổ sung field response tương thích ngược.
- Tests: AC-1..4 → service/integration tests.

### Phase 2 — Web hiển thị và khóa thao tác
- Files: M `src/services/assessment/api/assessmentApi.js`, trang/hook phiếu bé ngoan, tests.
- Steps: hiện tổng, chi tiết lớp nguồn, nhãn chuyển/nghỉ; ẩn/khóa phát ở lớp cũ theo `canIssue` BE.
- Tests: API adapter/component tests; build/lint.

## Test matrix

| AC | Test | Phase | Kết quả |
| --- | --- | --- | --- |
| AC-1 | chuyển giữa tuần, 2 lớp có cờ | 1-2 | passed (`AssessmentContinuityIntegrationTest`) |
| AC-2 | phát phiếu chỉ ở lớp cuối kỳ | 1-2 | passed (`AssessmentContinuityIntegrationTest`) |
| AC-3 | chuyển/nghỉ giữa tháng | 1 | passed (quality-report integration regression) |
| AC-4 | ngày nghỉ/lễ sau chuyển | 1 | passed (`ChildRecordServiceImplTest`) |

## Rủi ro & rollback

| Rủi ro | Giảm thiểu | Rollback |
| --- | --- | --- |
| Client cũ chưa đọc field mới | Chỉ thêm field DTO, giữ field cũ | Không dùng field mới |
| Lệch báo cáo lịch sử | Test trường hợp chuyển giữa tuần/tháng | Khôi phục cách hiển thị cũ, không sửa dữ liệu |

## Validation log

- [x] Khẳng định code: `BehaviourTicketServiceImpl:438-497`.
- [x] Consumer: `FE:src/services/assessment/api/assessmentApi.js:276-286`.
- [x] Không dùng rule PENDING/OPEN.

## Progress log

### 2026-11-21 — Implemented and verified
- User yêu cầu sửa các phần cần thiết, trừ lớp ghép.
- BE bổ sung `evidence` và `historical` vào bảng phát/tổng hợp phiếu; lớp nhận trẻ xem được căn cứ theo từng lớp,
  còn lớp cũ chỉ xem số liệu lịch sử của mình.
- BE chỉ cho chuyển lớp/nghỉ học vào ngày học và thao tác nghỉ học dùng ngày hiện tại để không viết lại lịch sử.
- FE hiển thị căn cứ từng lớp và nhãn chỉ xem lịch sử; không mở lại quyền phát phiếu ở lớp cũ.
- Đã chạy: `ChildRecordServiceImplTest` + `BehaviourTicketRulesTest` (26/26),
  `AssessmentContinuityIntegrationTest` (4/4), FE lint + dashboard tests (87/87) + production build.
- Chưa commit theo yêu cầu hiện tại.

### 2026-11-22 — Bổ sung sau review
- Tổng hợp cả năm lấy phiếu tuần trên toàn khoảng đang chọn; bộ lọc tháng chỉ áp dụng khi chọn tháng.
- Căn cứ trả cho dòng lịch sử chỉ có lớp đang xem, không trả dữ liệu lớp nhận trẻ.
- `TicketChild` bổ sung `code`, đọc theo lô từ hồ sơ trẻ để cột mã trẻ trong Excel có dữ liệu.
- Bổ sung regression trong `AssessmentContinuityIntegrationTest`: căn cứ cả năm, giới hạn lớp cũ và mã trẻ từ DB.
