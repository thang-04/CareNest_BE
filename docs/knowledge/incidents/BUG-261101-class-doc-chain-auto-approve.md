---
id: BUG-261101-class-doc-chain-auto-approve
type: bug
modules: [education-plan]
rules: [EDU-18]
status: fixed
date: 2026-11-01
keywords: [EduClassDocServiceImpl, actOnClassDoc, PENDING_TL, signers, chuỗi người ký, indexOf, tự APPROVED, hết Tổ trưởng, sign_chain]
similar_to: []
---

# BUG-261101-class-doc-chain-auto-approve — Hồ sơ lớp kẹt `PENDING_TL` khi hết Tổ trưởng; đổi chuỗi ký giữa chừng làm hồ sơ tự APPROVED

## Symptom
- Hồ sơ chủ đề/chủ đề nhánh đang `PENDING_TL`, HT gỡ Tổ trưởng của nhóm tuổi ⇒ không ai duyệt được (`NOT_YOUR_REVIEW_STEP`), FE vẫn hiện "Chờ tổ trưởng duyệt".
- Hồ sơ `PENDING_TL`, HT đổi cấu hình chuỗi `branch` thành `[VICE_PRINCIPAL, PRINCIPAL]` ⇒ Tổ trưởng bấm duyệt ⇒ `APPROVED` ngay, bỏ qua Phó HT và HT.

## Điều kiện tái hiện
Hồ sơ đang chờ duyệt + (nhóm tuổi không còn Tổ trưởng hợp lệ) hoặc (cấu hình chuỗi bỏ bước đang chờ). Nguồn: review E2E 01/11 FLOW-012 (đọc tĩnh, cần seed có nhóm tuổi/Tổ trưởng để chạy tay).

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Suy thứ tự bước theo hạng cố định TL → PHT → HT | Bỏ | Cấu hình cho phép thứ tự bất kỳ (`EduConfigServiceImpl.requireSigners` chỉ kiểm role hợp lệ, không trùng) |
| 2 | Tính bước kế từ cấu hình hiện tại + danh sách người đã duyệt | Bỏ làm cách chính | Cấu hình đổi thành chỉ còn bước đã duyệt ⇒ hết người, vẫn phải chọn giữa kẹt hoặc tự duyệt; giữ làm fallback cho hồ sơ cũ |

## Root cause
`actOnClassDoc` tính lại `leaders`, `skipLeader` và chuỗi từ cấu hình mỗi lần thao tác: bước đang chờ không còn trong chuỗi ⇒ `chain.indexOf(waiting) == -1` ⇒ bước kế null ⇒ `APPROVED`; bước Tổ trưởng bị lọc khỏi chuỗi nhưng trạng thái vẫn `PENDING_TL` nên không ai qua được `requireWaitingFor`.

## Fix
V41 thêm `edu_class_docs.sign_chain` (JSONB). Gửi ⇒ chụp chuỗi (đã áp EDU-18 bỏ Tổ trưởng); duyệt/trả lại theo chuỗi đã chụp. Bước Tổ trưởng không còn người hợp lệ ⇒ bước chờ thực tế là bước kế (`effectiveWaiting`), `getClassDocs` hiện trạng thái đó để FE hiện nút cho đúng người. Hồ sơ gửi trước V41: dùng cấu hình; cấu hình đã bỏ bước đang chờ thì chuỗi = người đã duyệt + bước đang chờ + bước cấu hình còn lại.

## Regression test
`EduClassDocServiceIntegrationTest.pendingLeaderStepMovesOnWhenLeaderRemoved`, `EduClassDocServiceIntegrationTest.signerChainChangeDoesNotAutoApprove` (gồm nhánh hồ sơ cũ `sign_chain` NULL).

## Ảnh hưởng
Không đổi API. Chuỗi chỉ còn bước Tổ trưởng mà hết Tổ trưởng ⇒ vẫn chờ tới khi HT phân công Tổ trưởng mới (không tự duyệt). V41 có thể trùng số với nhánh khác khi merge.

## Lesson
Trạng thái quy trình nhiều bước phải lưu chuỗi đã áp lúc bắt đầu; tính lại từ cấu hình sống làm mất bước hoặc tự hoàn tất.
