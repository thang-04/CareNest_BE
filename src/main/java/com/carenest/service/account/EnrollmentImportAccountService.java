package com.carenest.service.account;

import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildEnrollmentImportService.*;
import org.springframework.web.multipart.MultipartFile;

public interface EnrollmentImportAccountService {
    Preview preview(CurrentUser actor, MultipartFile file);

    ImportResult importRows(CurrentUser actor, ImportRequest request);
}
