package com.carenest.config;

import com.carenest.security.ClientPlatformProperties;
import com.carenest.security.CurrentUserService;
import com.carenest.security.KeycloakRealmRoleConverter;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.authorization.PermissionGuard;
import com.carenest.utils.enums.ClientPlatform;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Resource Server giữ Bearer API; Web gateway opt-in dùng cookie và CSRF theo ADR-0013.
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({AuthProperties.class, ClientPlatformProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error"
    };

    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthProperties authProperties,
            KeycloakRealmRoleConverter realmRoleConverter,
            @Value("${carenest.api.prefix}") String apiPrefix)
            throws Exception {
        RequestMatcher authGet = authGetMatcher(apiPrefix);
        RequestMatcher authPost = authPostMatcher(apiPrefix);
        if (authProperties.enabled()) {
            CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
            repository.setCookiePath(apiPrefix + "/auth");
            repository.setCookieCustomizer(
                    cookie -> cookie.secure(authProperties.secureCookies()).sameSite("Lax"));
            // React gửi nguyên token từ cookie/header, không dùng token XOR dành cho form HTML.
            http.csrf(csrf -> csrf.csrfTokenRepository(repository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .requireCsrfProtectionMatcher(authPost)
                    .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                        @Override
                        public <O extends CsrfFilter> O postProcess(O filter) {
                            // Cookie auth luôn kiểm CSRF, kể cả request có thêm Bearer header.
                            filter.setRequireCsrfProtectionMatcher(authPost);
                            return filter;
                        }
                    }));
        } else {
            http.csrf(AbstractHttpConfigurer::disable);
        }
        http.cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PUBLIC_PATHS).permitAll();
                    if (authProperties.enabled())
                        auth.requestMatchers(authGet, authPost).permitAll();
                    // AUTH-21: token không còn role nào dùng được trên nền tảng của client ⇒ 403 mọi API
                    auth.anyRequest()
                            .access(AuthorizationManagers.allOf(
                                    AuthenticatedAuthorizationManager.authenticated(),
                                    AuthorizationManagers.not(AuthorityAuthorizationManager.hasAuthority(
                                            ClientPlatform.DENIED_AUTHORITY))));
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                                jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(realmRoleConverter)))
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                        // Mặc định lỗi không lấy được JWKS bị ném tiếp thành 500; đưa về entry point để trả 503
                        // ResponseJson
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                filter.setAuthenticationFailureHandler(restAuthenticationEntryPoint::commence);
                                return filter;
                            }
                        }))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler));
        return http.build();
    }

    /** Kiểm chữ ký bằng JWKS (fetch lazy, có cache, tự lấy key mới khi Keycloak xoay key); không gọi Keycloak mỗi request. */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${carenest.security.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${carenest.security.jwt.issuer-uri}") String issuerUri,
            @Value("${carenest.security.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(jwtValidator(issuerUri, audience));
        return decoder;
    }

    // aud bắt buộc chứa CareNest API: chặn ID token và token cấp cho client/ứng dụng khác trong cùng realm
    static OAuth2TokenValidator<Jwt> jwtValidator(String issuerUri, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience)));
    }

    // Khai báo ở đây (không @Component) để mọi WebMvcTest đã import SecurityConfig có sẵn bean cho SpEL
    // @permissionGuard
    @Bean
    public PermissionGuard permissionGuard(ObjectProvider<CurrentUserService> currentUserService) {
        return new PermissionGuard(currentUserService);
    }

    @Bean
    public KeycloakRealmRoleConverter keycloakRealmRoleConverter(
            ClientPlatformProperties platforms, AuthProperties authProperties) {
        return new KeycloakRealmRoleConverter(platforms.withWebClient(authProperties.clientId()));
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter(
            KeycloakRealmRoleConverter realmRoleConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(realmRoleConverter);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${carenest.security.cors.allowed-origins}") List<String> allowedOrigins,
            AuthProperties authProperties,
            @Value("${carenest.api.prefix}") String apiPrefix) {
        CorsConfiguration config = new CorsConfiguration();
        var businessOrigins = new java.util.LinkedHashSet<>(allowedOrigins);
        if (authProperties.enabled()) businessOrigins.addAll(authProperties.allowedOrigins());
        config.setAllowedOrigins(List.copyOf(businessOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                "X-Edit-Session",
                "X-Plan-Revision",
                "X-Request-Id",
                HttpHeaders.RANGE));
        config.setExposedHeaders(
                List.of(HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.CONTENT_RANGE, HttpHeaders.ACCEPT_RANGES));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (authProperties.enabled()) {
            CorsConfiguration auth = new CorsConfiguration(config);
            auth.setAllowedOrigins(authProperties.allowedOrigins());
            auth.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, "X-XSRF-TOKEN"));
            auth.setAllowCredentials(true);
            auth.validateAllowCredentials();
            source.registerCorsConfiguration(apiPrefix + "/auth/**", auth);
        }
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static RequestMatcher authGetMatcher(String prefix) {
        var paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                paths.matcher(HttpMethod.GET, prefix + "/auth/csrf"),
                paths.matcher(HttpMethod.GET, prefix + "/auth/password/otp/{requestId}"));
    }

    private static RequestMatcher authPostMatcher(String prefix) {
        var paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, prefix + "/auth/login"),
                paths.matcher(HttpMethod.POST, prefix + "/auth/refresh"),
                paths.matcher(HttpMethod.POST, prefix + "/auth/logout"),
                paths.matcher(HttpMethod.POST, prefix + "/auth/password/forgot"),
                paths.matcher(HttpMethod.POST, prefix + "/auth/password/otp/{requestId}/resend"),
                paths.matcher(HttpMethod.POST, prefix + "/auth/password/otp/{requestId}/verify"),
                paths.matcher(HttpMethod.POST, prefix + "/auth/password/reset"));
    }
}
