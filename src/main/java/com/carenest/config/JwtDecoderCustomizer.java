package com.carenest.config;

import com.carenest.entity.UserStatus;
import com.carenest.repository.UserAuthState;
import com.carenest.repository.UserRepository;
import com.carenest.security.AccountLockedException;
import com.carenest.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Decodes access tokens: verifies signature and expiry (Nimbus), then rejects
 * refresh tokens and tokens revoked by logout.
 */
@Component
public class JwtDecoderCustomizer implements JwtDecoder {

    private final NimbusJwtDecoder delegate;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtDecoderCustomizer(@Value("${jwt.secret-key}") String secretKey, JwtService jwtService,
                                UserRepository userRepository) {
        SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        this.delegate = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        Jwt jwt = delegate.decode(token);

        if (!JwtService.TYPE_ACCESS.equals(jwt.getClaimAsString(JwtService.CLAIM_TYPE))) {
            throw new BadJwtException("Not an access token");
        }
        if (jwt.getId() == null || jwtService.isInvalidated(jwt.getId())) {
            throw new BadJwtException("Token has been revoked");
        }
        // Checked on every request so that locking an account or changing its roles takes effect immediately,
        // not when the access token expires.
        UserAuthState state = findAuthState(jwt.getSubject());
        if (state == null || state.getStatus() != UserStatus.ACTIVE) {
            throw new AccountLockedException();
        }
        if (JwtService.tokenVersion(jwt.getClaim(JwtService.CLAIM_VERSION)) != state.getTokenVersion()) {
            throw new BadJwtException("Token was issued before the user's last security change");
        }
        return jwt;
    }

    private UserAuthState findAuthState(String subject) {
        try {
            return userRepository.findAuthStateById(Long.valueOf(subject)).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
