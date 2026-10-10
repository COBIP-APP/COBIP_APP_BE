package com.cobip.auth;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
class UserController {
    private final UserRepository users;

    UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/me")
    UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        User user = users.findById(Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getNickname(),
                user.getRole(), user.getCreatedAt());
    }

    record UserProfileResponse(Long userId, String email, String nickname, String role, Instant createdAt) {}
}
