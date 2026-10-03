package com.carenest.service;

import com.carenest.entity.ForgotPassword;
import com.carenest.entity.User;
import com.carenest.exception.AppException;
import com.carenest.exception.ErrorCode;
import com.carenest.repository.ForgotPasswordRepository;
import com.carenest.repository.UserRepository;
import com.carenest.utils.ChangePassword;
import com.carenest.utils.IdentifierUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;
import java.util.Random;

/**
 * Forgot password: sendOtp -> verifyOtp -> changePassword.
 * Identifier is an email (OTP sent by email) or a phone number (OTP sent by SMS).
 * Errors are thrown as AppException and turned into JSON by GlobalExceptionHandler.
 */
@Service
@RequiredArgsConstructor
public class ForgotPasswordService {

    private final UserRepository userRepository;
    private final ForgotPasswordRepository forgotPasswordRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final SmsService smsService;

    /**
     * @return true when the OTP was sent by email, false when sent by SMS
     */
    public boolean sendOtp(String identifier) {
        Optional<User> userOpt = userRepository.findByIdentifier(identifier);
        if (userOpt.isEmpty()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        User user = userOpt.get();

        Optional<ForgotPassword> existingOtp = forgotPasswordRepository.findByUser(user);
        if (existingOtp.isPresent()) {
            Date expiration = existingOtp.get().getExpirationTime();
            if (expiration.after(new Date())) {
                long secondsLeft = (expiration.getTime() - System.currentTimeMillis()) / 1000;
                throw new AppException(ErrorCode.OTP_ALREADY_SENT, secondsLeft);
            }

            user.setForgotPassword(null);
            userRepository.save(user);
            userRepository.flush();
            forgotPasswordRepository.deleteById(existingOtp.get().getFpid());
        }

        Integer otp = generateOtp();
        ForgotPassword forgotPassword = ForgotPassword.builder()
                .otp(otp)
                .expirationTime(new Date(System.currentTimeMillis() + 70_000))
                .user(user)
                .otpVerified(false)
                .build();
        forgotPasswordRepository.save(forgotPassword);

        boolean byEmail = IdentifierUtils.isEmail(identifier);
        try {
            if (byEmail) {
                mailService.sendMail("Đổi Mật Khẩu", "Mã OTP của bạn là: " + otp, user.getEmail());
            } else {
                smsService.sendSms(user.getPhoneNumber(), "Mã OTP CareNest của bạn là: " + otp);
            }
        } catch (Exception e) {
            user.setForgotPassword(null);
            userRepository.save(user);
            userRepository.flush();
            forgotPasswordRepository.deleteById(forgotPassword.getFpid());
            if (e instanceof AppException appException) {
                throw appException;
            }
            throw new AppException(ErrorCode.MAIL_SEND_FAILED);
        }

        return byEmail;
    }

    public void verifyOtp(Integer otp, String identifier) {
        Optional<User> userOpt = userRepository.findByIdentifier(identifier);
        if (userOpt.isEmpty()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        User user = userOpt.get();

        Optional<ForgotPassword> fpOpt = forgotPasswordRepository.findByOtpAndUser(otp, user);
        if (fpOpt.isEmpty()) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        ForgotPassword fp = fpOpt.get();

        if (fp.getExpirationTime().before(new Date())) {
            user.setForgotPassword(null);
            userRepository.save(user);
            forgotPasswordRepository.deleteById(fp.getFpid());
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        fp.setOtpVerified(true);
        forgotPasswordRepository.save(fp);
    }

    @Transactional
    public void changePassword(String identifier, ChangePassword changePassword) {
        if (!changePassword.password().equals(changePassword.confirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }

        Optional<User> userOpt = userRepository.findByIdentifier(identifier);
        if (userOpt.isEmpty()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        User user = userOpt.get();

        Optional<ForgotPassword> fpOpt = forgotPasswordRepository.findByUser(user);
        if (fpOpt.isEmpty() || !fpOpt.get().isOtpVerified()) {
            throw new AppException(ErrorCode.OTP_NOT_VERIFIED);
        }

        userRepository.updatePassword(user.getId(), passwordEncoder.encode(changePassword.password()));
        user.setForgotPassword(null);
        userRepository.save(user);
    }

    private Integer generateOtp() {
        return 100_000 + new Random().nextInt(900_000);
    }
}
