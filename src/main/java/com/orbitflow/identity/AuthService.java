package com.orbitflow.identity;

import com.orbitflow.common.exception.BadRequestException;
import com.orbitflow.common.exception.ResourceNotFoundException;
import com.orbitflow.common.security.JwtProvider;
import com.orbitflow.common.security.UserPrincipal;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[a-zA-Z0-9_.-]+") String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank String displayName) {}

    public record LoginRequest(@NotBlank String emailOrUsername, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record AuthResponse(String accessToken, String refreshToken, UUID userId, String username, String email) {}
    public record ProfileDto(UUID id, String email, String username, String displayName, String timezone, String avatarUrl) {}

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwt;
    private final CacheManager cacheManager;

    @Value("${orbitflow.jwt.refresh-token-ttl-days:14}")
    private long refreshTtlDays = 14;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        String username = req.username().trim();
        if (users.existsByEmailIgnoreCase(email)) throw new BadRequestException("Email already registered");
        if (users.existsByUsernameIgnoreCase(username)) throw new BadRequestException("Username already taken");
        User user = User.builder()
                .email(email).username(username)
                .displayName(req.displayName().trim())
                .passwordHash(passwordEncoder.encode(req.password()))
                .emailVerified(false)
                .build();
        users.save(user);
        return issueTokens(user, "register");
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String key = req.emailOrUsername().trim();
        User user = users.findByEmailIgnoreCase(key).or(() -> users.findByUsernameIgnoreCase(key))
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));
        if ("SUSPENDED".equals(user.getStatus()) || "DELETED".equals(user.getStatus()))
            throw new BadRequestException("Account is suspended");
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash()))
            throw new BadRequestException("Invalid credentials");
        return issueTokens(user, "login");
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        String hash = sha256(req.refreshToken());
        UserSession s = sessions.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
        if (s.isRevoked() || s.getExpiresAt().isBefore(Instant.now()))
            throw new BadRequestException("Refresh token expired");
        if (!jwt.isValid(req.refreshToken())) throw new BadRequestException("Invalid refresh token");
        s.setRevoked(true);
        sessions.save(s);
        return issueTokens(s.getUser(), "refresh");
    }

    @Transactional
    public void logout(String refreshToken) {
        sessions.findByRefreshTokenHash(sha256(refreshToken)).ifPresent(s -> {
            s.setRevoked(true);
            sessions.save(s);
            // Evict the cached principal so revoked sessions cannot be re-resolved.
            try {
                Objects.requireNonNull(cacheManager.getCache("user_principals"))
                        .evict(s.getUser().getId().toString());
            } catch (Exception ignored) {
                // caching is best-effort; logout itself must succeed
            }
        });
    }

    @Transactional(readOnly = true)
    public ProfileDto profile(UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return new ProfileDto(u.getId(), u.getEmail(), u.getUsername(), u.getDisplayName(), u.getTimezone(), u.getAvatarUrl());
    }

    @Transactional
    @CacheEvict(value = "user_principals", key = "#userId.toString()")
    public ProfileDto updateProfile(UUID userId, String displayName, String timezone, String avatarUrl) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (displayName != null && !displayName.isBlank()) u.setDisplayName(displayName.trim());
        if (timezone != null && !timezone.isBlank()) u.setTimezone(timezone.trim());
        if (avatarUrl != null) u.setAvatarUrl(avatarUrl.trim().isEmpty() ? null : avatarUrl.trim());
        users.save(u);
        return profile(userId);
    }

    private AuthResponse issueTokens(User user, String device) {
        String access = jwt.generateAccessToken(user.getId(), user.getEmail(), user.getUsername());
        String refresh = jwt.generateRefreshToken(user.getId());
        sessions.save(UserSession.builder()
                .user(user).refreshTokenHash(sha256(refresh))
                .deviceInfo(device)
                .expiresAt(Instant.now().plusSeconds(refreshTtlDays * 24 * 3600))
                .build());
        return new AuthResponse(access, refresh, user.getId(), user.getUsername(), user.getEmail());
    }

    static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "user_principals", key = "#userId")
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        try {
            User u = users.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            return new UserPrincipal(u.getId(), u.getEmail(), u.getUsername(), u.getPasswordHash(), "USER");
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("Bad user id");
        }
    }
}
