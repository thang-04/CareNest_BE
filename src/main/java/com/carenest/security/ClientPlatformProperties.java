package com.carenest.security;

import com.carenest.utils.enums.ClientPlatform;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Map client id Keycloak (claim {@code azp}) sang nền tảng; client không có trong danh sách bị từ chối.
 *
 * <p>Ba danh sách {@code mobileStaff*} (ADR-0026) dạng {@code "METHOD /path"}, path không có tiền tố API.
 */
@ConfigurationProperties("carenest.security.platform")
public record ClientPlatformProperties(
        @DefaultValue("carenest-auth-gateway,carenest-web") List<String> webClients,
        @DefaultValue("carenest-mobile") List<String> mobileClients,
        @DefaultValue List<String> devClients,
        @DefaultValue List<String> mobileStaffReadPaths,
        @DefaultValue List<String> mobileStaffWriteExceptions,
        @DefaultValue List<String> mobileStaffSystemWritePaths) {

    public ClientPlatformProperties {
        webClients = clean(webClients);
        mobileClients = clean(mobileClients);
        devClients = clean(devClients);
        mobileStaffReadPaths = clean(mobileStaffReadPaths);
        mobileStaffWriteExceptions = clean(mobileStaffWriteExceptions);
        mobileStaffSystemWritePaths = clean(mobileStaffSystemWritePaths);
    }

    /** Thêm client của Web gateway để đổi {@code carenest.auth.client-id} không làm khóa đăng nhập Web. */
    public ClientPlatformProperties withWebClient(String clientId) {
        if (clientId == null || clientId.isBlank() || webClients.contains(clientId)) return this;
        return new ClientPlatformProperties(
                Stream.concat(webClients.stream(), Stream.of(clientId)).toList(),
                mobileClients,
                devClients,
                mobileStaffReadPaths,
                mobileStaffWriteExceptions,
                mobileStaffSystemWritePaths);
    }

    public Optional<ClientPlatform> platformOf(String clientId) {
        if (clientId == null) return Optional.empty();
        if (webClients.contains(clientId)) return Optional.of(ClientPlatform.WEB);
        if (mobileClients.contains(clientId)) return Optional.of(ClientPlatform.MOBILE);
        if (devClients.contains(clientId)) return Optional.of(ClientPlatform.DEV);
        return Optional.empty();
    }

    private static List<String> clean(List<String> values) {
        if (values == null) return List.of();
        return values.stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
    }
}
