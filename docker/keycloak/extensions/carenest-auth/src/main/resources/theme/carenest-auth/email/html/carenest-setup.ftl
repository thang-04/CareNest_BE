<#ftl output_format="HTML">
<#import "carenest-layout.ftl" as layout>
<@layout.email preheader="Tài khoản CareNest của bạn đã sẵn sàng. Thiết lập mật khẩu để bắt đầu sử dụng.">
<h1 style="margin:0 0 16px;font-size:22px;line-height:1.3;color:#0e3f63;">Chào mừng bạn đến với CareNest</h1>
<p style="margin:0 0 16px;"><@layout.greeting/><br><strong>${schoolName}</strong> đã tạo cho bạn tài khoản<#if roleLabel??> <strong>${roleLabel}</strong></#if> trên CareNest.</p>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 20px;">
  <tr>
    <td style="background-color:#f3f8fd;border-radius:8px;padding:12px 16px;font-size:14px;color:#1f2d3d;">Tên đăng nhập: <strong style="color:#0e3f63;font-size:16px;">${username}</strong></td>
  </tr>
</table>
<p style="margin:0 0 20px;">Để đăng nhập lần đầu, hãy thiết lập mật khẩu của bạn:</p>
<table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px;">
  <tr>
    <td style="border-radius:8px;background-color:#4fb0f5;">
      <a href="${link}" target="_blank" style="display:inline-block;padding:12px 28px;font-family:Arial,Helvetica,sans-serif;font-size:15px;font-weight:bold;color:#0e3f63;text-decoration:none;border-radius:8px;">Thiết lập mật khẩu</a>
    </td>
  </tr>
</table>
<p style="margin:0 0 12px;font-size:13px;color:#6b7c8f;">Trên trang mở ra, nhập địa chỉ email này để nhận mã xác thực, rồi đặt mật khẩu mới. Sau đó đăng nhập bằng tên đăng nhập ở trên.</p>
<p style="margin:0 0 12px;font-size:13px;color:#6b7c8f;">Nếu nút không hoạt động, sao chép đường dẫn sau vào trình duyệt:<br><a href="${link}" target="_blank" style="color:#2b8fd6;word-break:break-all;">${link}</a></p>
<p style="margin:0;font-size:13px;color:#6b7c8f;">Nếu bạn không mong đợi email này, hãy bỏ qua.</p>
</@layout.email>
