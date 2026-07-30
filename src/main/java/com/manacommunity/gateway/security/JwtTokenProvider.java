package com.manacommunity.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey signingKey;

    private SecretKey getSigningKey() {
        if (signingKey == null) {
            byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
            signingKey = Keys.hmacShaKeyFor(keyBytes);
        }
        return signingKey;
    }

    /**
     * Validates the given JWT token.
     *
     * @param token the JWT token string
     * @return true if the token is valid and not expired, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT token signature validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is null or empty: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error validating JWT token: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Parses and returns all claims from the given JWT token.
     *
     * @param token the JWT token string
     * @return the Claims contained in the token
     */
    public Claims getAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the userId claim from the token.
     */
    public String getUserId(String token) {
        return getStringClaim(token, "userId");
    }

    /**
     * Extracts the username claim from the token.
     */
    public String getUsername(String token) {
        return getStringClaim(token, "username");
    }

    /**
     * Extracts the tenantId claim from the token.
     */
    public String getTenantId(String token) {
        return getStringClaim(token, "tenantId");
    }

    /**
     * Extracts the tenantCode claim from the token.
     */
    public String getTenantCode(String token) {
        return getStringClaim(token, "tenantCode");
    }

    /**
     * Extracts the communityId claim from the token.
     */
    public String getCommunityId(String token) {
        return getStringClaim(token, "communityId");
    }

    /**
     * Extracts the roles claim from the token as a list.
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        try {
            Claims claims = getAllClaims(token);
            Object roles = claims.get("roles");
            if (roles instanceof List<?>) {
                return (List<String>) roles;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to extract roles from token: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts the permissions claim from the token as a list.
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissions(String token) {
        try {
            Claims claims = getAllClaims(token);
            Object permissions = claims.get("permissions");
            if (permissions instanceof List<?>) {
                return (List<String>) permissions;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to extract permissions from token: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts the email claim from the token.
     */
    public String getEmail(String token) {
        return getStringClaim(token, "email");
    }

    /**
     * Extracts the mobile claim from the token.
     */
    public String getMobile(String token) {
        return getStringClaim(token, "mobile");
    }

    /**
     * Extracts the deviceId claim from the token.
     */
    public String getDeviceId(String token) {
        return getStringClaim(token, "deviceId");
    }

    /**
     * Extracts the sessionId claim from the token.
     */
    public String getSessionId(String token) {
        return getStringClaim(token, "sessionId");
    }

    /**
     * Checks whether the token has expired.
     *
     * @param token the JWT token string
     * @return true if the token is expired, false otherwise
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getAllClaims(token);
            Date expiration = claims.getExpiration();
            return expiration != null && expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            log.warn("Failed to check token expiration: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Safely extracts a string claim from the token.
     */
    private String getStringClaim(String token, String claimName) {
        try {
            Claims claims = getAllClaims(token);
            Object value = claims.get(claimName);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to extract claim '{}' from token: {}", claimName, e.getMessage());
            return null;
        }
    }
}
