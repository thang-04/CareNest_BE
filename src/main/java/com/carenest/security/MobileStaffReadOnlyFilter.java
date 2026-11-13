package com.carenest.security;

import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ClientPlatform;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * ADR-0026, AUTH-21: Ban giám hiệu dùng App chỉ xem.
 *
 * <ul>
 *   <li>Lớp 1: lệnh ghi từ App bỏ role BGH; không còn role nào ⇒ 403 1301.
 *   <li>Lớp 3: tài khoản chỉ có role BGH chỉ gọi được GET trong {@code mobile-staff-read-paths}.
 *   <li>Lớp 2: GET đó chạy trong transaction chỉ đọc ⇒ PostgreSQL chặn mọi lệnh ghi (1302 ở GlobalExceptionHandler).
 * </ul>
 *
 * Không phải bean: SecurityConfig tự tạo để Spring Boot không đăng ký thêm thành servlet filter.
 */
@Slf4j
public class MobileStaffReadOnlyFilter extends OncePerRequestFilter {

    /** Đánh dấu request đang chạy trong transaction chỉ đọc của lớp 2. */
    public static final String READ_ONLY_REQUEST_ATTRIBUTE = MobileStaffReadOnlyFilter.class.getName() + ".readOnly";

    private static final String LOG_PREFIX = "[MobileStaffReadOnlyFilter]";
    private static final String AUTHORIZED_PARTY_CLAIM = "azp";
    private static final String READ_ONLY_SQL_STATE = "25006";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    private final ClientPlatformProperties platforms;
    private final RequestMatcher readPaths;
    private final RequestMatcher writeExceptions;
    private final RequestMatcher systemWritePaths;
    private final TransactionTemplate readOnlyTransaction;
    private final ObjectMapper objectMapper;

    public MobileStaffReadOnlyFilter(
            ClientPlatformProperties platforms,
            String apiPrefix,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper) {
        this.platforms = platforms;
        this.readPaths = matcher(apiPrefix, platforms.mobileStaffReadPaths());
        this.writeExceptions = matcher(apiPrefix, platforms.mobileStaffWriteExceptions());
        this.systemWritePaths = matcher(apiPrefix, platforms.mobileStaffSystemWritePaths());
        this.readOnlyTransaction = transactionManager == null ? null : readOnlyTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    /** PostgreSQL từ chối ghi trong transaction chỉ đọc bằng SQLState 25006. */
    public static boolean isReadOnlyViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && READ_ONLY_SQL_STATE.equals(sql.getSQLState())) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !isMobile(jwt)) {
            chain.doFilter(request, response);
            return;
        }
        Set<String> readOnly = authorities(ClientPlatform.MOBILE.readOnlyRoles());
        List<GrantedAuthority> others = jwt.getAuthorities().stream()
                .filter(authority -> !readOnly.contains(authority.getAuthority()))
                .toList();
        boolean hasReadOnlyRole = others.size() < jwt.getAuthorities().size();
        if (!hasReadOnlyRole || writeExceptions.matches(request)) {
            chain.doFilter(request, response);
            return;
        }
        // Chỉ xét role: Spring Security còn gắn authority khác cho token (vd. FACTOR_BEARER)
        boolean staffOnly = others.stream().noneMatch(authority -> authority.getAuthority() != null
                && authority.getAuthority().startsWith(ROLE_PREFIX));
        if (!isSafe(request)) {
            if (staffOnly) {
                deny(request, response, ApiCode.MOBILE_STAFF_READ_ONLY, "write");
                return;
            }
            // Tài khoản BGH kiêm GV/phụ huynh: lệnh ghi chỉ còn quyền của role khác
            withAuthentication(new JwtAuthenticationToken(jwt.getToken(), others, jwt.getName()), request, response, chain);
            return;
        }
        if (!staffOnly) {
            chain.doFilter(request, response);
            return;
        }
        if (!readPaths.matches(request)) {
            deny(request, response, ApiCode.MOBILE_STAFF_READ_ONLY, "path");
            return;
        }
        if (systemWritePaths.matches(request)) {
            chain.doFilter(request, response);
            return;
        }
        if (readOnlyTransaction == null) {
            // Không có transaction manager thì không bảo đảm được chỉ đọc ⇒ từ chối thay vì cho qua
            deny(request, response, ApiCode.MOBILE_STAFF_WRITE_BLOCKED, "no-transaction-manager");
            return;
        }
        runReadOnly(request, response, chain);
    }

    private boolean isMobile(JwtAuthenticationToken jwt) {
        return platforms
                .platformOf(jwt.getToken().getClaimAsString(AUTHORIZED_PARTY_CLAIM))
                .filter(platform -> platform == ClientPlatform.MOBILE)
                .isPresent();
    }

    private void runReadOnly(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(READ_ONLY_REQUEST_ATTRIBUTE, Boolean.TRUE);
        try {
            readOnlyTransaction.executeWithoutResult(status -> {
                // Luôn rollback: không có gì để commit, và tránh lỗi khi code bên trong đánh dấu rollback-only
                status.setRollbackOnly();
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException ex) {
                    throw new ChainException(ex);
                }
            });
        } catch (ChainException ex) {
            if (ex.getCause() instanceof IOException io) throw io;
            throw (ServletException) ex.getCause();
        }
    }

    private static void withAuthentication(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContext original = SecurityContextHolder.getContext();
        SecurityContext reduced = SecurityContextHolder.createEmptyContext();
        reduced.setAuthentication(authentication);
        SecurityContextHolder.setContext(reduced);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.setContext(original);
        }
    }

    private void deny(HttpServletRequest request, HttpServletResponse response, ApiCode apiCode, String reason)
            throws IOException {
        log.warn(
                "{}|deny|reason={}|method={}|path={}|errorCode={}",
                LOG_PREFIX,
                reason,
                request.getMethod(),
                request.getRequestURI(),
                apiCode.getErrorCode());
        SecurityResponseWriter.write(response, objectMapper, apiCode, apiCode.getDefaultDesc());
    }

    private static boolean isSafe(HttpServletRequest request) {
        return SAFE_METHODS.contains(HttpMethod.valueOf(request.getMethod().toUpperCase(Locale.ROOT)));
    }

    private static Set<String> authorities(Collection<CareNestRole> roles) {
        return roles.stream().map(CareNestRole::authority).collect(Collectors.toUnmodifiableSet());
    }

    static TransactionTemplate readOnlyTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        template.setName("mobileStaffReadOnly");
        return template;
    }

    /** Mỗi mục dạng {@code "GET /children/{childId:\\d+}"}; sai định dạng ⇒ lỗi lúc khởi động. */
    static RequestMatcher matcher(String apiPrefix, List<String> entries) {
        if (entries.isEmpty()) return request -> false;
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        List<RequestMatcher> matchers = entries.stream()
                .map(entry -> {
                    String[] parts = entry.trim().split("\\s+", 2);
                    if (parts.length != 2 || !parts[1].startsWith("/")) {
                        throw new IllegalArgumentException("Sai định dạng 'METHOD /path': " + entry);
                    }
                    return (RequestMatcher) paths.matcher(
                            HttpMethod.valueOf(parts[0].toUpperCase(Locale.ROOT)), apiPrefix + parts[1]);
                })
                .toList();
        return new OrRequestMatcher(matchers);
    }

    private static final class ChainException extends RuntimeException {
        private ChainException(Exception cause) {
            super(cause);
        }
    }
}
