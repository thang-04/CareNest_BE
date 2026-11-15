<#ftl output_format="HTML">
<#import "carenest-layout.ftl" as layout>
<@layout.email preheader="Mã xác thực CareNest của bạn có hiệu lực trong ${minutes} phút.">
<h1 style="margin:0 0 16px;font-size:22px;line-height:1.3;color:#0e3f63;">Mã xác thực của bạn</h1>
<p style="margin:0 0 16px;"><@layout.greeting/><br>Bạn vừa yêu cầu đặt lại mật khẩu CareNest. Nhập mã dưới đây trên trang đặt lại mật khẩu:</p>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 20px;">
  <tr>
    <td align="center">
      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="background-color:#f3f8fd;border:1px dashed #4fb0f5;border-radius:10px;padding:14px 28px;font-family:Consolas,'Courier New',monospace;font-size:32px;font-weight:bold;letter-spacing:8px;color:#0e3f63;">${code}</td>
        </tr>
      </table>
    </td>
  </tr>
</table>
<p style="margin:0 0 16px;">Mã có hiệu lực trong <strong>${minutes} phút</strong>. Không chia sẻ mã này với bất kỳ ai, kể cả người tự xưng là nhân viên nhà trường.</p>
<p style="margin:0;font-size:13px;color:#6b7c8f;">Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này. Mật khẩu hiện tại của bạn vẫn an toàn.</p>
</@layout.email>
