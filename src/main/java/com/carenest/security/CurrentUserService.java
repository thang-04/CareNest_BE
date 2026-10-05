package com.carenest.security;

import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.utils.ApiCode;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nơi duy nhất đọc JWT để xác định người dùng hiện tại; service nghiệp vụ không tự parse token. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public CurrentUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new GlobalException(ApiCode.UNAUTHORIZED, "Unauthorized");
        }

        // Danh tính chỉ lấy từ "sub" đã được kiểm chữ ký, không lấy từ id client gửi
        String keycloakUserId = jwtAuthentication.getToken().getSubject();
        UserAccount account = userAccountRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> {
                    log.warn("[getCurrentUser]|keycloakUserId={}|reason=notProvisioned", keycloakUserId);
                    return new GlobalException(ApiCode.FORBIDDEN, "Account is not provisioned");
                });
        // Token còn hạn vẫn bị chặn khi CareNest đã khóa tài khoản
        if (!account.isActive()) {
            log.warn("[getCurrentUser]|userId={}|reason=inactive", account.getId());
            throw new GlobalException(ApiCode.FORBIDDEN, "Account is inactive");
        }

        return new CurrentUser(account.getId(), keycloakUserId, account.getUsername(), account.getFullName(),
                account.getEmail(), rolesOf(authentication));
    }

    private static Set<CareNestRole> rolesOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(CareNestRole::fromAuthority)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }
}
