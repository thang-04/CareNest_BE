---
id: BUG-000            # BUG-xxx | CASE-xxx | ENV-xxx
type: bug              # bug | edge-case | env
modules: [attendance, nutrition]
rules: [ATT-07, NUT-03]
status: fixed          # fixed | workaround | open
date: YYYY-MM-DD
keywords: [đến muộn, meal count, stale]
similar_to: []         # ID incident liên quan
---

# <ID> — <tiêu đề ngắn mô tả triệu chứng>

## Symptom
Hiện tượng quan sát được. Chuỗi lỗi **nguyên văn** (dòng quyết định, không dán cả stack):
```text
<exception / error code / log line>
```

## Điều kiện tái hiện
Dữ liệu tối thiểu, bước, môi trường. Không dùng dữ liệu trẻ thật.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | | | |

## Root cause
Nguyên nhân + **bằng chứng** (file:line, log, test chứng minh).

## Fix
Thay đổi gì, ở đâu (file/PR/commit). Vì sao cách này đúng.

## Regression test
Tên test + vị trí. Test fail trước fix, pass sau fix.

## Ảnh hưởng
Module/API/FE/APP bị ảnh hưởng; dữ liệu cần sửa lại không.

## Lesson
1–2 câu tổng quát. Nếu lặp ở chỗ khác ⇒ thêm vào `docs/knowledge/PATTERNS.md`.
