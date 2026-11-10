package com.carenest.service.account.impl;

import com.carenest.dto.child.GuardianCheckRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.ChildEnrollmentService;
import com.carenest.service.account.EnrollmentImportAccountService;
import com.carenest.service.child.ChildEnrollmentImportService;
import com.carenest.service.child.ChildEnrollmentImportService.*;
import com.carenest.utils.ApiCode;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class EnrollmentImportAccountServiceImpl implements EnrollmentImportAccountService {
    private static final String LOG_PREFIX = "[EnrollmentImportAccountServiceImpl]";
    private final ChildEnrollmentImportService profiles;
    private final ChildEnrollmentService accounts;

    @Override
    public Preview preview(CurrentUser actor, MultipartFile file) {
        String logPrefix = LOG_PREFIX + "|preview|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            Preview preview = profiles.preview(actor, file);
            var rows = preview.rows().stream()
                    .map(row -> {
                        var errors = new ArrayList<>(row.errors());
                        try {
                            accounts.checkGuardians(
                                    actor,
                                    new GuardianCheckRequest(
                                            row.payload().child().guardians()));
                        } catch (GlobalException ex) {
                            if (ex.getApiCode() != ApiCode.GUARDIAN_IDENTITY_CONFLICT
                                    && ex.getApiCode() != ApiCode.INVALID_REQUEST_DATA) throw ex;
                            errors.addAll(ex.getFieldErrors().stream()
                                    .map(e -> e.message())
                                    .toList());
                        }
                        return new PreviewRow(
                                row.rowNo(),
                                row.raw(),
                                row.payload(),
                                errors.stream().distinct().toList(),
                                row.selectionError(),
                                row.notes());
                    })
                    .toList();
            log.info("{}|END", logPrefix);
            return new Preview(preview.supported(), preview.fileName(), rows);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public ImportResult importRows(CurrentUser actor, ImportRequest request) {
        String logPrefix = LOG_PREFIX + "|importRows|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            if (request != null && request.rows() != null && request.rows().size() <= 500) {
                for (var row : request.rows())
                    if (row != null && row.child() != null)
                        accounts.checkGuardians(
                                actor, new GuardianCheckRequest(row.child().guardians()));
            }
            ImportResult result = profiles.importRows(actor, request);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
