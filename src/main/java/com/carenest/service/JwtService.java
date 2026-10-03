package com.carenest.service;

import com.carenest.entity.InvalidatedToken;
import com.carenest.entity.User;
import com.carenest.exception.AppException;
import com.carenest.exception.ErrorCode;
import com.carenest.repository.InvalidatedTokenRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    public static final JWSAlgorithm ALGORITHM = JWSAlgorithm.HS512;
    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_AUTHORITIES = "authorities";
    public static final String CLAIM_VERSION = "ver";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final byte[] secret;
    private final Duration accessTokenExpiration;
    private final Duration refreshTokenExpiration;
    private final InvalidatedTokenRepository invalidatedTokenRepository;

    public JwtService(@Value("${jwt.secret-key}") String secretKey,
                      @Value("${jwt.access-token-expiration}") Duration accessTokenExpiration,
                      @Value("${jwt.refresh-token-expiration}") Duration refreshTokenExpiration,
                      InvalidatedTokenRepository invalidatedTokenRepository) {
        this.secret = secretKey.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 64) {
            throw new IllegalStateException("jwt.secret-key must be at least 64 bytes for HS512");
        }
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.invalidatedTokenRepository = invalidatedTokenRepository;
    }

    public String generateAccessToken(User user) {
        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return sign(user, TYPE_ACCESS, accessTokenExpiration, authorities);
    }

    public String generateRefreshToken(User user) {
        return sign(user, TYPE_REFRESH, refreshTokenExpiration, null);
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration.toSeconds();
    }

    /**
     * Checks signature, expiry, token type and blacklist of a refresh token.
     *
     * @throws AppException INVALID_TOKEN when any check fails
     */
    public JWTClaimsSet verifyRefreshToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!ALGORITHM.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.before(new Date())
                    || !TYPE_REFRESH.equals(claims.getStringClaim(CLAIM_TYPE))
                    || claims.getJWTID() == null
                    || isInvalidated(claims.getJWTID())) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }
            return claims;
        } catch (ParseException | JOSEException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    public boolean isInvalidated(String jwtId) {
        return invalidatedTokenRepository.existsById(jwtId);
    }

    public void invalidate(String jwtId, Instant expiresAt) {
        if (jwtId == null || expiresAt == null || isInvalidated(jwtId)) {
            return;
        }
        invalidatedTokenRepository.save(new InvalidatedToken(jwtId, expiresAt));
    }

    private String sign(User user, String type, Duration ttl, List<String> authorities) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_VERSION, user.getTokenVersion());
        if (authorities != null) {
            claims.claim(CLAIM_AUTHORITIES, authorities);
        }

        SignedJWT jwt = new SignedJWT(new JWSHeader(ALGORITHM), claims.build());
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign JWT", e);
        }
        return jwt.serialize();
    }

    /**
     * Version stored in a token's "ver" claim; tokens issued before the claim existed count as version 0.
     */
    public static int tokenVersion(Object claim) {
        return claim instanceof Number number ? number.intValue() : 0;
    }
}
