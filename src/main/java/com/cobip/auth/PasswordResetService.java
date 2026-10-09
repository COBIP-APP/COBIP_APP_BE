package com.cobip.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PasswordResetService {
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_DELAY = Duration.ofSeconds(60);
    private static final Duration RESET_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwords;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String mailHost;
    private final String mailFrom;

    PasswordResetService(UserRepository users, StringRedisTemplate redis,
                         PasswordEncoder passwords, ObjectProvider<JavaMailSender> mailSender,
                         @Value("${spring.mail.host:}") String mailHost,
                         @Value("${app.mail.from:}") String mailFrom) {
        this.users = users;
        this.redis = redis;
        this.passwords = passwords;
        this.mailSender = mailSender;
        this.mailHost = mailHost;
        this.mailFrom = mailFrom;
    }

    AuthDtos.SendEmailResponse sendCode(String rawEmail) {
        String email = AuthService.normalizedEmail(rawEmail);
        String suffix = AuthService.digest(email);
        String cooldownKey = "auth:password-reset:cooldown:" + suffix;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(cooldownKey, "1", RESEND_DELAY))) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "CODE_RATE_LIMITED",
                    "잠시 후 인증번호를 다시 요청해주세요.");
        }
        if (mailHost.isBlank() || mailFrom.isBlank() || mailSender.getIfAvailable() == null) {
            redis.delete(cooldownKey);
            throw mailUnavailable();
        }

        var response = new AuthDtos.SendEmailResponse(
                "가입된 이메일이라면 인증번호를 발송했습니다.", 300, 60);
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getStatus())) return response;

        String codeKey = "auth:password-reset:code:" + suffix;
        String attemptsKey = "auth:password-reset:attempts:" + suffix;
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        redis.opsForValue().set(codeKey, passwords.encode(code), CODE_TTL);
        redis.delete(attemptsKey);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(email);
            message.setSubject("[COBIA] 비밀번호 재설정 인증번호");
            message.setText("COBIA 비밀번호 재설정 인증번호: " + code + "\n5분 안에 입력해주세요.");
            mailSender.getObject().send(message);
        } catch (MailException exception) {
            redis.delete(codeKey);
            redis.delete(cooldownKey);
            throw mailUnavailable();
        }
        return response;
    }

    AuthDtos.ConfirmPasswordResetResponse confirmCode(String rawEmail, String code) {
        String email = AuthService.normalizedEmail(rawEmail);
        String suffix = AuthService.digest(email);
        String codeKey = "auth:password-reset:code:" + suffix;
        String attemptsKey = "auth:password-reset:attempts:" + suffix;
        String storedHash = redis.opsForValue().get(codeKey);
        if (storedHash == null) throw invalidCode();

        if (!passwords.matches(code, storedHash)) {
            Long attempts = redis.opsForValue().increment(attemptsKey);
            if (attempts != null && attempts == 1) redis.expire(attemptsKey, CODE_TTL);
            if (attempts != null && attempts >= 5) redis.delete(codeKey);
            throw invalidCode();
        }
        redis.delete(codeKey);
        redis.delete(attemptsKey);

        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getStatus())) throw invalidCode();

        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String resetToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        redis.opsForValue().set("auth:password-reset:grant:" + AuthService.digest(resetToken),
                user.getId() + ":" + user.getAuthVersion(), RESET_TTL);
        return new AuthDtos.ConfirmPasswordResetResponse(resetToken, 600);
    }

    @Transactional
    void complete(String resetToken, String newPassword) {
        String owner = redis.opsForValue().getAndDelete(
                "auth:password-reset:grant:" + AuthService.digest(resetToken));
        if (owner == null) throw invalidResetToken();
        String[] parts = owner.split(":", 2);
        User user = users.findById(Long.valueOf(parts[0])).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getStatus())
                || user.getAuthVersion() != Long.parseLong(parts[1])) throw invalidResetToken();

        user.changePassword(passwords.encode(newPassword), Instant.now());
        users.saveAndFlush(user);
    }

    private static AuthException invalidCode() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_CODE",
                "인증번호가 올바르지 않거나 만료되었습니다. 다시 확인해주세요.");
    }

    private static AuthException invalidResetToken() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                "비밀번호 재설정 시간이 지났습니다. 인증번호를 다시 요청해주세요.");
    }

    private static AuthException mailUnavailable() {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE",
                "현재 인증 메일을 보낼 수 없습니다.");
    }
}
