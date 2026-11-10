package com.carenest.controller.child;

import com.carenest.dto.child.ChildGuardianResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.SelectedParentAccountService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("@permissionGuard.has('PARENT_ACCOUNT_MANAGE')")
public class SelectedParentAccountController {
    private static final String LOG_PREFIX = "[SelectedParentAccountController]";
    private final SelectedParentAccountService service;

    public record RecipientRequest(@NotNull Long guardianId) {}

    @GetMapping("/children/{childId}/account-recipient")
    public ResponseEntity<ResponseJson<List<ChildGuardianResponse>>> candidates(
            @PathVariable Long childId, CurrentUser actor) {
        String logPrefix = LOG_PREFIX + "|candidates|childId=" + childId;
        log.info("{}|START", logPrefix);
        var result = service.candidates(actor, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Account recipient candidates", result);
    }

    @PutMapping("/children/{childId}/account-recipient")
    public ResponseEntity<ResponseJson<Void>> select(
            @PathVariable Long childId, @Valid @RequestBody RecipientRequest request, CurrentUser actor) {
        String logPrefix = LOG_PREFIX + "|select|childId=" + childId;
        log.info("{}|START", logPrefix);
        service.select(actor, childId, request.guardianId());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Account recipient selected");
    }

    @GetMapping("/children/{childId}/guardians/pending")
    public ResponseEntity<ResponseJson<List<ChildGuardianResponse>>> pending(
            @PathVariable Long childId, CurrentUser actor) {
        String logPrefix = LOG_PREFIX + "|pending|childId=" + childId;
        log.info("{}|START", logPrefix);
        var result = service.candidates(actor, childId).stream()
                .filter(g -> g.accountRecipient()
                        && g.accountStatus() == com.carenest.utils.enums.GuardianAccountStatus.NOT_ACTIVATED)
                .toList();
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Pending account recipient", result);
    }

    @PostMapping("/children/{childId}/guardians/activate")
    public ResponseEntity<ResponseJson<List<SelectedParentAccountService.ActivationResult>>> activate(
            @PathVariable Long childId, @Valid @RequestBody RecipientRequest request, CurrentUser actor) {
        String logPrefix = LOG_PREFIX + "|activate|childId=" + childId;
        log.info("{}|START", logPrefix);
        var result = service.activate(actor, childId, request.guardianId());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Account recipient activated", result);
    }
}
