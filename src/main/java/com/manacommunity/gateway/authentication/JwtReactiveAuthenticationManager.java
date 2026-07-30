package com.manacommunity.gateway.authentication;

import com.manacommunity.gateway.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = authentication.getCredentials().toString();

        return Mono.just(token)
                .filter(jwtTokenProvider::validateToken)
                .switchIfEmpty(Mono.error(
                        new org.springframework.security.authentication.BadCredentialsException(
                                "Invalid or expired JWT token")))
                .map(validToken -> {
                    String userId = jwtTokenProvider.getUserId(validToken);
                    String username = jwtTokenProvider.getUsername(validToken);
                    List<String> roles = jwtTokenProvider.getRoles(validToken);

                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(role -> new SimpleGrantedAuthority(
                                    role.startsWith("ROLE_") ? role : "ROLE_" + role))
                            .collect(Collectors.toList());

                    log.debug("Authenticated user: {} (userId: {}) with roles: {}",
                            username, userId, roles);

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    username != null ? username : userId,
                                    null,
                                    authorities);
                    authToken.setDetails(validToken);

                    return (Authentication) authToken;
                });
    }
}
