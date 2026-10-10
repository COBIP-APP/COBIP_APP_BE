package com.cobip.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_DELAY = Duration.ofSeconds(60);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(30);
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(14);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwtEncoder;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String mailHost;
    private final String mailFrom;
    private final String jwtIssuer;

    AuthService(UserRepository users, StringRedisTemplate redis, PasswordEncoder passwords,
                JwtEncoder jwtEncoder, ObjectProvider<JavaMailSender> mailSender,
                @Value("${spring.mail.host:}") String mailHost,
                @Value("${app.mail.from:}") String mailFrom,
                @Value("${app.jwt.issuer}") String jwtIssuer) {
        this.users = users;
        this.redis = redis;
        this.passwords = passwords;
        this.jwtEncoder = jwtEncoder;
        this.mailSender = mailSender;
        this.mailHost = mailHost;
        this.mailFrom = mailFrom;
        this.jwtIssuer = jwtIssuer;
    }

    AuthDtos.SendEmailResponse sendCode(String rawEmail) {
        String email = normalizedEmail(rawEmail);
        String suffix = digest(email);
        String cooldownKey = "auth:email-cooldown:" + suffix;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(cooldownKey, "1", RESEND_DELAY))) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "CODE_RATE_LIMITED",
                    "잠시 후 인증번호를 다시 요청해주세요.");
        }

        var response = new AuthDtos.SendEmailResponse(
                "가입 가능한 이메일이라면 인증번호를 발송했습니다. 메일함을 확인해주세요.", 300, 60);
        if (users.existsByEmailIgnoreCase(email)) return response;

        String codeKey = "auth:email-code:" + suffix;
        String attemptsKey = "auth:email-attempts:" + suffix;
        String verifiedKey = "auth:email-verified:" + suffix;
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        redis.opsForValue().set(codeKey, passwords.encode(code), CODE_TTL);
        redis.delete(attemptsKey);
        redis.delete(verifiedKey);

        try {
            if (mailHost.isBlank() || mailFrom.isBlank() || mailSender.getIfAvailable() == null) {
                throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE",
                        "현재 인증 메일을 보낼 수 없습니다.");
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(email);
            message.setSubject("[COBIA] 이메일 인증번호");
            message.setText("COBIA 인증번호: " + code + "\n5분 안에 입력해주세요.");
            mailSender.getObject().send(message);
        } catch (MailException | AuthException exception) {
            redis.delete(codeKey);
            redis.delete(cooldownKey);
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE",
                    "현재 인증 메일을 보낼 수 없습니다.");
        }
        return response;
    }

    AuthDtos.ConfirmEmailResponse confirmCode(String rawEmail, String code) {
        String suffix = digest(normalizedEmail(rawEmail));
        String codeKey = "auth:email-code:" + suffix;
        String attemptsKey = "auth:email-attempts:" + suffix;
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
        redis.opsForValue().set("auth:email-verified:" + suffix, "1", VERIFIED_TTL);
        return new AuthDtos.ConfirmEmailResponse(true);
    }

    @Transactional
    AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
        if (!Boolean.TRUE.equals(request.serviceTermsAgreed())
                || !Boolean.TRUE.equals(request.privacyTermsAgreed())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "필수 약관 두 항목에 동의해주세요.");
        }
        String email = normalizedEmail(request.email());
        String verifiedKey = "auth:email-verified:" + digest(email);
        if (!"1".equals(redis.opsForValue().get(verifiedKey))) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "EMAIL_NOT_VERIFIED",
                    "이메일 인증을 완료해주세요.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", "이미 가입된 이메일입니다.");
        }
        String nickname = request.nickname().trim();
        if (nickname.length() < 2) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "닉네임은 2자 이상 입력해주세요.");
        }
        if (users.existsByNickname(nickname)) {
            throw new AuthException(HttpStatus.CONFLICT, "NICKNAME_ALREADY_USED", "이미 사용 중인 닉네임입니다.");
        }
        try {
            User user = users.saveAndFlush(new User(email, nickname,
                    passwords.encode(request.password()), Instant.now()));
            redis.delete(verifiedKey);
            return AuthDtos.UserResponse.of(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_USED",
                    "이미 사용 중인 이메일 또는 닉네임입니다.");
        }
    }

    AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalizedEmail(request.email()))
                .orElseThrow(AuthService::invalidCredentials);
        if (!passwords.matches(request.password(), user.getPasswordHash())
                || !user.isEmailVerified() || !"ACTIVE".equals(user.getStatus())) {
            throw invalidCredentials();
        }
        return issueTokens(user);
    }

    AuthDtos.TokenResponse refresh(String refreshToken) {
        String tokenOwner = redis.opsForValue().getAndDelete(refreshKey(refreshToken));
        if (tokenOwner == null) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                    "다시 로그인해주세요.");
        }
        String[] parts = tokenOwner.split(":", 2);
        User user = users.findById(Long.valueOf(parts[0]))
                .orElseThrow(AuthService::invalidCredentials);
        long tokenVersion = parts.length == 2 ? Long.parseLong(parts[1]) : 0;
        if (!"ACTIVE".equals(user.getStatus()) || user.getAuthVersion() != tokenVersion) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                    "다시 로그인해주세요.");
        }
        return issueTokens(user);
    }

    void logout(Jwt accessToken, String refreshToken) {
        String key = refreshKey(refreshToken);
        String refreshOwner = redis.opsForValue().get(key);
        if (refreshOwner != null && !refreshOwner.split(":", 2)[0].equals(accessToken.getSubject())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "INVALID_REFRESH_TOKEN",
                    "다른 계정의 토큰입니다.");
        }
        redis.delete(key);
        Duration remaining = Duration.between(Instant.now(), accessToken.getExpiresAt());
        if (!remaining.isNegative() && !remaining.isZero()) {
            redis.opsForValue().set("auth:revoked:" + accessToken.getId(), "1", remaining);
        }
    }

    private AuthDtos.TokenResponse issueTokens(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtIssuer).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plus(ACCESS_TTL))
                .id(UUID.randomUUID().toString()).claim("role", user.getRole())
                .claim("authVersion", user.getAuthVersion()).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        redis.opsForValue().set(refreshKey(refreshToken),
                user.getId() + ":" + user.getAuthVersion(), REFRESH_TTL);
        return new AuthDtos.TokenResponse(accessToken, refreshToken, "Bearer", 900,
                AuthDtos.UserResponse.of(user));
    }

    static String normalizedEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String refreshKey(String token) {
        return "auth:refresh:" + digest(token);
    }

    static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static AuthException invalidCode() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_CODE",
                "인증번호가 올바르지 않거나 만료되었습니다. 다시 확인해주세요.");
    }

    private static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "이메일 또는 비밀번호를 확인해주세요.");
    }
}
