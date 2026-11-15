package com.carenest.service.account.impl;

import com.carenest.dto.child.GuardianCheckRequest;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.ChildEnrollmentService;
import com.carenest.service.account.EnrollmentImportAccountService;
import com.carenest.service.child.ChildEnrollmentImportService;
import com.carenest.service.child.ChildEnrollmentImportService.*;
import com.carenest.utils.ApiCode;
import com.carenest.utils.CitizenIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final UserAccountRepository userAccountRepository;

    @Override
    public Preview preview(CurrentUser actor, MultipartFile file) {
        String logPrefix = LOG_PREFIX + "|preview|userId=" + actor.userId();
        log.info("{}|START", logPrefix);
        try {
            Preview preview = profiles.preview(actor, file);
            Map<String, UserAccount> accountsByCitizenId = accountsOf(preview.rows());
            var rows = preview.rows().stream()
                    .map(row -> {
                        var errors = new ArrayList<>(row.errors());
                        // Như BE giả FE: phụ huynh trùng CCCD tài khoản ⇒ ghi chú trước ghi chú hồ sơ (G94-180/AUTH-18)
                        var notes = new ArrayList<String>();
                        row.payload().child().guardians().stream()
                                .map(g -> CitizenIds.normalize(g.idNumber()).map(accountsByCitizenId::get))
                                .flatMap(Optional::stream)
                                .distinct()
                                .forEach(
                                        account -> notes.add(
                                                "Phụ huynh đã có tài khoản (trùng CCCD): "
                                                        + account.getFullName() + " · " + account.getUsername()
                                                        + ". Dùng thông tin tài khoản, chỉ liên kết người được chọn khi kích hoạt sau khi nhập."));
                        notes.addAll(row.notes());
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
                                List.copyOf(notes));
                    })
                    .toList();
            log.info("{}|END", logPrefix);
            return new Preview(preview.supported(), preview.fileName(), rows);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Tài khoản theo CCCD của mọi phụ huynh trong file, đọc một lần (tối đa 500 dòng × 3 phụ huynh). */
    private Map<String, UserAccount> accountsOf(List<PreviewRow> rows) {
        Set<String> citizenIds = rows.stream()
                .flatMap(row -> row.payload().child().guardians().stream())
                .map(g -> CitizenIds.normalize(g.idNumber()))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        if (citizenIds.isEmpty()) return Map.of();
        return userAccountRepository.findAllByCitizenIdIn(citizenIds).stream()
                .collect(Collectors.toMap(UserAccount::getCitizenId, Function.identity(), (a, b) -> a));
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
