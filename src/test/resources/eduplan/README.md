# Fixture nhập Word .doc — EDU-46

Tạo mới bằng Word automation trên máy phát triển ngày 01/11/2026; không chuyển đổi/copy tài liệu user.
Runtime ứng dụng và test chỉ dùng POI/JDK, không cần cài Office.

- goals-merged.doc / goals-merged.docx: cùng bảng 6 dòng, 5 cột, tên/nội dung tổng hợp; có gộp ngang và dọc, hai dấu tích TMN/BT.
- no-table.doc: chỉ có câu văn tổng hợp, không bảng.
- encrypted.doc: câu văn tổng hợp mã hóa bằng mật khẩu test `fixture-only`, dùng để kiểm từ chối; không phải thông tin xác thực thật.
- row-limit.doc: bảng rỗng 2.001 dòng, 1 cột, dưới 2 MB để kiểm giới hạn dòng riêng.

Đã bật RemovePersonalInformation khi tạo; test kiểm author/lastAuthor/company của fixture bảng. Core properties creator/lastModifiedBy của .docx trống.
