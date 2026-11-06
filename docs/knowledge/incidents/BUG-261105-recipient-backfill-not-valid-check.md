---
id: BUG-261105-recipient-backfill-not-valid-check
type: bug
modules: [child]
rules: [AUTH-18]
status: fixed
date: 2026-11-05
keywords: [account_recipient, NOT VALID, citizen_id, Flyway, backfill]
similar_to: []
---
# Backfill người nhận tài khoản thất bại trên hồ sơ legacy thiếu CCCD

## Symptom
Test nâng cấp schema cũ thất bại khi đánh dấu account_recipient: SQL State 23514; violates check constraint ck_child_guardians_citizen_id_required.

## Điều kiện tái hiện
ChildEnrollmentMigrationIntegrationTest.upgradesExistingChildrenAndGuardianLinks tạo liên kết trước V63. V63 chuyển sang child_guardians nhưng giữ CCCD null ở hồ sơ cũ bằng CHECK NOT VALID. UPDATE flag mới đụng dòng đó.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | UPDATE flag trực tiếp cho singleton/sole-linked guardian | Test nâng cấp thất bại 23514 | NOT VALID vẫn kiểm mọi UPDATE mới |
| 2 | Bỏ CHECK trong cùng transaction migration, backfill flag, tạo lại đúng CHECK NOT VALID | Test nâng cấp và dữ liệu mơ hồ đạt | Giữ dữ liệu cũ và ràng buộc cho các lần ghi tiếp theo |

## Root cause
CHECK NOT VALID miễn kiểm dữ liệu đang có khi thêm constraint; không miễn kiểm UPDATE sau đó, kể cả chỉ đổi flag khác.

## Fix
V81__selected_parent_account.sql gỡ rồi phục hồi đúng CHECK trong transaction Flyway. Không bịa CCCD/xóa guardian/link. Sole-linked dựa guardian_child_links hiện hữu, không suy từ user_id đã gỡ link.

## Regression test
ChildEnrollmentMigrationIntegrationTest.upgradesExistingChildrenAndGuardianLinks đã fail trước fix và pass sau fix trên PostgreSQL. Thêm backfillUsesExistingLinksAndLeavesAmbiguousProfilesUnselected và selectedRecipientIndexRejectsTwoRecipientsAndAllowsExplicitReplacement.

## Ảnh hưởng
Chỉ schema nâng cấp recipient; không sửa dữ liệu thật, tài khoản, ảnh hoặc migration đã commit. FE/APP không đổi do lỗi này.

## Lesson
Khi backfill metadata trên bảng có CHECK NOT VALID, kiểm chứng dữ liệu legacy không thỏa CHECK thay vì chỉ schema sạch.
