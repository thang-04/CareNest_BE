package com.carenest.service;

import com.carenest.config.JwtDecoderCustomizer;
import com.carenest.dto.request.ChangePasswordRequest;
import com.carenest.dto.request.SignInRequest;
import com.carenest.dto.response.SignInResponse;
import com.carenest.entity.User;
import com.carenest.exception.AppException;
import com.carenest.exception.ErrorCode;
import com.carenest.repository.UserRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtDecoderCustomizer jwtDecoder;

    /**
     * Login by email (teachers) or phone number (parents), resolved in UserDetailServiceCustomizer.
     * Wrong identifier/password raises BadCredentialsException, a disabled account raises DisabledException;
     * both are mapped to Vietnamese messages in GlobalExceptionHandler.
     */
    public SignInResponse login(SignInRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.identifier().trim(), request.password()));
        User user = (User) authentication.getPrincipal();
        userRepository.updateLastLoginAt(user.getId(), Instant.now());
        return issueTokens(user);
    }

    /**
     * Refresh token rotation: the old refresh token is revoked and a new pair is returned.
     */
    @Transactional
    public SignInResponse refreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        JWTClaimsSet claims = jwtService.verifyRefreshToken(refreshToken);

        User user = findUserBySubject(claims.getSubject())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));
        if (!user.isEnabled()) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (JwtService.tokenVersion(claims.getClaim(JwtService.CLAIM_VERSION)) != user.getTokenVersion()) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        jwtService.invalidate(claims.getJWTID(), claims.getExpirationTime().toInstant());
        return issueTokens(user);
    }

    /**
     * Revokes whatever tokens the client still has. Invalid or expired tokens are ignored,
     * so logout always succeeds and the client can clear its state.
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (StringUtils.hasText(accessToken)) {
            try {
                Jwt jwt = jwtDecoder.decode(accessToken);
                jwtService.invalidate(jwt.getId(), jwt.getExpiresAt());
            } catch (JwtException e) {
                log.debug("Ignore invalid access token on logout");
            }
        }

        if (StringUtils.hasText(refreshToken)) {
            try {
                JWTClaimsSet claims = jwtService.verifyRefreshToken(refreshToken);
                jwtService.invalidate(claims.getJWTID(), claims.getExpirationTime().toInstant());
            } catch (AppException e) {
                log.debug("Ignore invalid refresh token on logout");
            }
        }
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
    }

    private Optional<User> findUserBySubject(String subject) {
        try {
            return userRepository.findById(Long.valueOf(subject));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private SignInResponse issueTokens(User user) {
        return SignInResponse.bearer(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                jwtService.getAccessTokenExpirationSeconds(),
                user.isMustChangePassword());
    }
}
