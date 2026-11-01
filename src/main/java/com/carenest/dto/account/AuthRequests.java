package com.carenest.dto.account;

import jakarta.validation.constraints.*;

public final class AuthRequests {
    private AuthRequests() {}

    public record Login(
            @NotBlank @Size(max = 255) String identifier,
            @NotNull @Size(min = 1, max = 128) String password,
            boolean remember) {
        @Override
        public String toString() {
            return "Login[redacted]";
        }
    }

    public record Refresh(boolean force) {}

    public record Forgot(@NotBlank @Email @Size(max = 255) String email) {
        @Override
        public String toString() {
            return "Forgot[redacted]";
        }
    }

    public record VerifyOtp(
            @NotNull @Pattern(regexp = "[0-9]{6}") String otp) {
        @Override
        public String toString() {
            return "VerifyOtp[redacted]";
        }
    }

    public record Reset(
            @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
            @NotNull @Size(min = 8, max = 128) String newPassword,
            @NotNull @Size(min = 8, max = 128) String confirmPassword) {
        @Override
        public String toString() {
            return "Reset[redacted]";
        }
    }

    public record ChangePassword(
            @NotNull @Size(min = 1, max = 128) String currentPassword,
            @NotNull @Size(min = 8, max = 128) String newPassword,
            @NotNull @Size(min = 8, max = 128) String confirmPassword) {
        @Override
        public String toString() {
            return "ChangePassword[redacted]";
        }
    }
}
