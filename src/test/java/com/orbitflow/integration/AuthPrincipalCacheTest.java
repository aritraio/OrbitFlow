package com.orbitflow.integration;

import com.orbitflow.identity.AuthService;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.*;

class AuthPrincipalCacheTest extends BaseIntegrationTest {
    @Autowired AuthService authService;
    @Autowired CacheManager cacheManager;

    @Test
    void principalIsCachedAndEvictedOnProfileUpdate() throws Exception {
        var alice = register("alice.cache@example.com", "alice_cache");
        String key = alice.userId().toString();

        assertThat(cacheManager.getCache("user_principals")).isNotNull();

        // First load populates the cache
        var first = authService.loadUserByUsername(key);
        assertThat(Objects.requireNonNull(cacheManager.getCache("user_principals")).get(key)).isNotNull();

        // Second load is served from cache (same cached value, no error)
        var second = authService.loadUserByUsername(key);
        assertThat(second.getUsername()).isEqualTo(first.getUsername());

        // Profile update evicts the entry
        authService.updateProfile(UUID.fromString(key), "Alice Updated", "UTC", null);
        assertThat(Objects.requireNonNull(cacheManager.getCache("user_principals")).get(key)).isNull();

        // Reload repopulates transparently
        var third = authService.loadUserByUsername(key);
        assertThat(third.getUsername()).isEqualTo(first.getUsername());
    }
}
