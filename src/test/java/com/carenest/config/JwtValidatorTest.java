package com.carenest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Token ký bằng key RSA tạo trong test để kiểm validator thật của SecurityConfig, không cần Keycloak. */
class JwtValidatorTest {

    private static final String ISSUER = "http://localhost:8180/realms/carenest";
    private static final String AUDIENCE = "carenest-api";

    private static KeyPair trustedKey;
    private static KeyPair untrustedKey;
    private static JwtDecoder decoder;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        trustedKey = generator.generateKeyPair();
        untrustedKey = generator.generateKeyPair();

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) trustedKey.getPublic()).build();
        nimbusDecoder.setJwtValidator(SecurityConfig.jwtValidator(ISSUER, AUDIENCE));
        decoder = nimbusDecoder;
    }

    @Test
    void shouldAcceptValidKeycloakAccessToken() throws Exception {
        String token = sign(trustedKey, claims(ISSUER, List.of(AUDIENCE, "account"), Instant.now().plusSeconds(300)));

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("kc-user-1");
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        String token = sign(trustedKey, claims(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(120)));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldRejectTokenFromAnotherIssuer() throws Exception {
        String token = sign(trustedKey,
                claims("http://localhost:8180/realms/other", List.of(AUDIENCE), Instant.now().plusSeconds(300)));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldRejectTokenIssuedForAnotherAudience() throws Exception {
        String token = sign(trustedKey, claims(ISSUER, List.of("account"), Instant.now().plusSeconds(300)));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldRejectTokenWithoutAudience() throws Exception {
        String token = sign(trustedKey, claims(ISSUER, null, Instant.now().plusSeconds(300)));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldRejectTokenSignedByUntrustedKey() throws Exception {
        String token = sign(untrustedKey, claims(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300)));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    private static JWTClaimsSet claims(String issuer, List<String> audience, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("kc-user-1")
                .audience(audience)
                .issueTime(Date.from(expiresAt.minusSeconds(300)))
                .expirationTime(Date.from(expiresAt))
                .claim("preferred_username", "teacher.a")
                .build();
    }

    private static String sign(KeyPair keyPair, JWTClaimsSet claims) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(keyPair.getPrivate()));
        return jwt.serialize();
    }
}
