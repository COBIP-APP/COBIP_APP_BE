package com.cobip.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public final class AuthDtos {
    private AuthDtos() {}

    public record SendEmailRequest(@NotBlank @Email @Size(max = 255) String email) {}
    public record ConfirmEmailRequest(@NotBlank @Email @Size(max = 255) String email,
                               @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}
    public record RegisterRequest(@NotBlank @Email @Size(max = 255) String email,
                           @NotBlank @Size(min = 2, max = 50) String nickname,
                           @NotBlank @Size(min = 8, max = 64) String password,
                           @NotNull Boolean serviceTermsAgreed,
                           @NotNull Boolean privacyTermsAgreed) {}
    public record LoginRequest(@NotBlank @Email @Size(max = 255) String email,
                               @NotBlank String password) {}
    public record RefreshRequest(@NotBlank @Size(max = 2048) String refreshToken) {}
    public record LogoutRequest(@NotBlank @Size(max = 2048) String refreshToken) {}

    public record SendEmailResponse(String message, int expiresInSeconds, int resendAfterSeconds) {}
    public record ConfirmEmailResponse(boolean verified) {}
    public record UserResponse(Long userId, String email, String nickname, String role) {
        static UserResponse of(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
        }
    }
    public record TokenResponse(String accessToken, String refreshToken, String tokenType,
                         int accessExpiresInSeconds, UserResponse user) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(String code, String message, Map<String, String> fieldErrors) {}
}
