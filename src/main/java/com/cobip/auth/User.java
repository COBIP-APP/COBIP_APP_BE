package com.cobip.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    @Column(nullable = false)
    private String role = "USER";

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = true;

    @Column(name = "service_terms_agreed_at", nullable = false)
    private Instant serviceTermsAgreedAt;

    @Column(name = "privacy_terms_agreed_at", nullable = false)
    private Instant privacyTermsAgreedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    User(String email, String nickname, String passwordHash, Instant now) {
        this.email = email;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.serviceTermsAgreedAt = now;
        this.privacyTermsAgreedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public String getPasswordHash() { return passwordHash; }
    public long getAuthVersion() { return authVersion; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant getCreatedAt() { return createdAt; }

    void changePassword(String newHash, Instant now) {
        passwordHash = newHash;
        authVersion++;
        updatedAt = now;
    }
}
