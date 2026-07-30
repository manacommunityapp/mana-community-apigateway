package com.manacommunity.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private SecretKey key;
    private String secret;

    @BeforeEach
    void setUp() {
        key = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        secret = Base64.getEncoder().encodeToString(key.getEncoded());

        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpiration(86400000L);
        properties.setRefreshExpiration(604800000L);
        properties.setIssuer("mana-community");

        tokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    void validateToken_withValidToken_returnsTrue() {
        String token = buildToken(Map.of("userId", "user-123"), 3600000);
        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_withExpiredToken_returnsFalse() {
        String token = buildToken(Map.of("userId", "user-123"), -1000);
        assertThat(tokenProvider.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_withInvalidToken_returnsFalse() {
        assertThat(tokenProvider.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    void getUserId_extractsCorrectly() {
        String token = buildToken(Map.of("userId", "user-456"), 3600000);
        assertThat(tokenProvider.getUserId(token)).isEqualTo("user-456");
    }

    @Test
    void getTenantId_extractsCorrectly() {
        String token = buildToken(Map.of("tenantId", "tenant-789"), 3600000);
        assertThat(tokenProvider.getTenantId(token)).isEqualTo("tenant-789");
    }

    @Test
    void getRoles_extractsCorrectly() {
        String token = buildToken(Map.of("roles", List.of("ADMIN", "RESIDENT")), 3600000);
        List<String> roles = tokenProvider.getRoles(token);
        assertThat(roles).containsExactly("ADMIN", "RESIDENT");
    }

    @Test
    void getPermissions_extractsCorrectly() {
        String token = buildToken(Map.of("permissions", List.of("READ", "WRITE")), 3600000);
        List<String> permissions = tokenProvider.getPermissions(token);
        assertThat(permissions).containsExactly("READ", "WRITE");
    }

    @Test
    void getAllClaims_containsAllCustomClaims() {
        Map<String, Object> claims = Map.of(
                "userId", "u1",
                "tenantId", "t1",
                "tenantCode", "tc1",
                "communityId", "c1",
                "email", "test@example.com"
        );
        String token = buildToken(claims, 3600000);

        assertThat(tokenProvider.getUserId(token)).isEqualTo("u1");
        assertThat(tokenProvider.getTenantId(token)).isEqualTo("t1");
        assertThat(tokenProvider.getTenantCode(token)).isEqualTo("tc1");
        assertThat(tokenProvider.getCommunityId(token)).isEqualTo("c1");
        assertThat(tokenProvider.getEmail(token)).isEqualTo("test@example.com");
    }

    private String buildToken(Map<String, Object> claims, long validityMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);
        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }
}
