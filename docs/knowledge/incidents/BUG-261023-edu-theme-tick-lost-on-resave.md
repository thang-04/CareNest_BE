---
id: BUG-261023-edu-theme-tick-lost-on-resave
type: bug
modules: [education-plan]
rules: [EDU-09]
status: fixed
date: 2026-10-23
keywords: [dấu tích chủ đề, theme tick, edu_content_theme_ticks, merge, persistence context, JPQL delete, đổi thứ tự chủ đề]
similar_to: []
---

# BUG-261023-edu-theme-tick-lost-on-resave — Lưu lại kế hoạch năm thì mất dấu tích chủ đề vẫn còn tích

## Symptom
Test lưu lại kế hoạch (đổi thứ tự chủ đề, vẫn tích nội dung vào chủ đề cũ) trả về `themeIds` rỗng:
```text
Expecting actual: [] to contain exactly (and in same order): [10L]
```

## Điều kiện tái hiện
Trong cùng một persistence context: tạo kế hoạch có 1 nội dung tích chủ đề X, rồi `updateYearPlan` gửi lại đúng tick (nội dung id cũ, chủ đề X id cũ). Ở production mỗi request là 1 transaction nên tick chưa được nạp trước `saveContent` — lỗi chỉ lộ khi tick đã managed (test `@Transactional`, hoặc code sau này nạp chi tiết trước khi lưu).

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Viết test đổi thứ tự chủ đề, giữ tick | Fail: tick mất | Lộ lỗi |
| 2 | Xóa tick bị bỏ bằng entity + chỉ `saveAll` tick mới (so chênh lệch) | Pass 10/10 | Không còn phụ thuộc trạng thái persistence context |

## Root cause
`saveContent` xóa hết tick bằng JPQL `deleteByThemeIds` (`clearAutomatically = false`) rồi `saveAll(ticks)`. Tick có khóa gán sẵn nên `save` → `merge`; entity cùng khóa còn managed trong context ⇒ Hibernate coi là đã tồn tại, không INSERT, trong khi dòng DB đã bị xóa.

## Fix
`EduYearPlanServiceImpl.saveContent`: nạp tick hiện có của các chủ đề giữ lại, `deleteAll` tick không còn trong request, `saveAll` chỉ tick chưa có. Bỏ `EduContentThemeTickRepository.deleteByThemeIds`; thêm `EduContentThemeTick.key()`.

## Regression test
`EduYearPlanServiceIntegrationTest.reordersThemesAndBreaksKeepingIdsAndTicks` — fail trước fix, pass sau fix.

## Ảnh hưởng
Chỉ BE education-plan. Không đổi API, không cần sửa dữ liệu.

## Lesson
Không xóa bằng JPQL rồi `save` lại entity khóa gán sẵn trong cùng transaction; so chênh lệch hoặc clear context.
