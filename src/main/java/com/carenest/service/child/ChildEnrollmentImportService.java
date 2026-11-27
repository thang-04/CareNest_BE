package com.carenest.service.child;

import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
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

    /** CHD-11: schoolYearId/startDate để trống = nhập vào năm hiện hành như cũ. */
    record ImportRequest(List<EnrollChildRequest> rows, Long campusId, Long schoolYearId, LocalDate startDate) {
        public ImportRequest(List<EnrollChildRequest> rows, Long campusId) {
            this(rows, campusId, null, null);
        }
    }

    record ImportResult(int created, List<Long> ids) {}
}
