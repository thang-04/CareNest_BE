---
id: ENV-261102-powershell-replacement-pairs
type: env
modules: [education-plan]
rules: [EDU-48]
status: fixed
date: 2026-11-02
keywords: [PowerShell, nested arrays, Replace, tài liệu, approval]
similar_to: []
---

# Cập nhật approval bằng cặp chuỗi PowerShell làm sai chữ tài liệu

## Symptom
Lệnh dùng hashtable chứa @(@(old,new)) cho file chỉ có một cặp bị flatten; foreach nhận từng string rồi dùng pair[0]/pair[1], Replace thay ký tự toàn file thay vì cụm cần sửa.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Batch thay approval bằng nested arrays trong PowerShell | Chữ/hyphen trong spec/research/checklist/tasks/domain bị sai; phát hiện ngay khi xem diff/header, trước code | Không dùng array lồng implicit để biểu diễn cặp khi có một phần tử |
| 2 | Dựng lại4artifact từ Git HEAD + delta đã duyệt, ghi lại domain14; dùng Python JSON/dict và exact patch | Kiểm prefix lịch sử4file,14mục và22task unique đạt; check-ai-layer0lỗi | Không đảo Replace vì đã mất thông tin ký tự gốc |

## Root cause / Fix
Unrolling mảng PowerShell cộng indexing string thành ký tự. Dùng object có thuộc tính old/new hoặc exact apply_patch, không dùng nested array cặp ngầm. Chỉ phục hồi file do lượt này tạo/sửa sau khi đã xác minh baseline sạch, không ghi đè thay đổi người khác.

## Regression / Ảnh hưởng
Kiểm prefix bằng git show HEAD:<file> và assert current.startswith(base), đếm domain14/task IDs. Không đổi source ứng dụng hoặc dữ liệu thật do lỗi này. Lịch sử T001..T011 được giữ nguyên.
