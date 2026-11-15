<#ftl output_format="HTML">
<#-- Khung chung mail CareNest: table + CSS inline để Gmail/Outlook/mail điện thoại hiển thị giống nhau -->
<#macro email preheader="">
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CareNest</title>
</head>
<body style="margin:0;padding:0;background-color:#eef4f9;">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;">${preheader}</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#eef4f9;">
  <tr>
    <td align="center" style="padding:32px 12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;background-color:#ffffff;border-radius:10px;border-top:4px solid #4fb0f5;">
        <tr>
          <td style="padding:18px 32px;border-bottom:1px solid #e6eef5;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <#if logoUrl??>
                <td style="padding-right:10px;vertical-align:middle;"><img src="${logoUrl}" width="36" height="36" alt="" style="display:block;border:0;outline:none;text-decoration:none;"></td>
                </#if>
                <td style="vertical-align:middle;font-family:Arial,Helvetica,sans-serif;font-size:20px;font-weight:bold;color:#0e3f63;">CareNest</td>
              </tr>
            </table>
          </td>
        </tr>
        <tr>
          <td style="padding:28px 32px 32px;font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.6;color:#1f2d3d;">
            <#nested>
          </td>
        </tr>
      </table>
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;">
        <tr>
          <td align="center" style="padding:16px 12px;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:1.5;color:#8a99a8;">© CareNest · Email tự động, vui lòng không trả lời</td>
        </tr>
      </table>
    </td>
  </tr>
</table>
</body>
</html>
</#macro>

<#macro greeting><#if fullName?has_content>Chào <strong>${fullName}</strong>,<#else>Xin chào,</#if></#macro>
