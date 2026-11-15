package com.carenest.service.child;

import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.security.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface ChildEnrollmentImportService {
    Preview preview(CurrentUser actor, MultipartFile file);

    ImportResult importRows(CurrentUser actor, ImportRequest request);

    record Preview(boolean supported, String fileName, List<PreviewRow> rows) {}

    record PreviewRow(
            int rowNo,
            Map<String, String> raw,
            EnrollChildRequest payload,
            List<String> errors,
            String selectionError,
            List<String> notes) {}

    record ImportRequest(List<EnrollChildRequest> rows, Long campusId) {}

    record ImportResult(int created, List<Long> ids) {}
}
