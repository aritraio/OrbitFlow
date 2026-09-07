package com.orbitflow.identity;

import com.orbitflow.common.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthService.AuthResponse> register(@Valid @RequestBody AuthService.RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    public AuthService.AuthResponse login(@Valid @RequestBody AuthService.LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    public AuthService.AuthResponse refresh(@Valid @RequestBody AuthService.RefreshRequest req) {
        return authService.refresh(req);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@Valid @RequestBody AuthService.RefreshRequest req) {
        authService.logout(req.refreshToken());
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public AuthService.ProfileDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.profile(principal.getId());
    }

    @PatchMapping("/me")
    public AuthService.ProfileDto updateMe(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestBody Map<String, String> body) {
        return authService.updateProfile(principal.getId(),
                body.get("displayName"), body.get("timezone"), body.get("avatarUrl"));
    }

    // For tests that resolve user id from JWT without extra lookup
    @GetMapping("/principal")
    public Map<String, Object> principal(@AuthenticationPrincipal UserPrincipal p) {
        return Map.of("userId", p.getId().toString(), "username", p.getUsername(), "email", p.getEmail());
    }

    @SuppressWarnings("unused")
    private UUID unused() { return null; }
}
