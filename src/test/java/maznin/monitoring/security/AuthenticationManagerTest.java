package maznin.monitoring.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationManagerTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private final JwtService jwtService = new JwtService(SECRET, 86_400_000L);
    private final AuthenticationManager authenticationManager = new AuthenticationManager(jwtService);

    private static UsernamePasswordAuthenticationToken credentials(String token) {
        return new UsernamePasswordAuthenticationToken(token, token);
    }

    @Test
    void validTokenYieldsAuthenticationWithRoleUser() {
        String token = jwtService.generateToken(
                new User("admin", "", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        StepVerifier.create(authenticationManager.authenticate(credentials(token)))
                .assertNext(auth -> {
                    assertEquals("admin", auth.getPrincipal());
                    assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));
                })
                .verifyComplete();
    }

    @Test
    void garbageTokenYieldsEmpty() {
        // Регрессия: мусорный токен раньше приводил к 500 вместо 401
        StepVerifier.create(authenticationManager.authenticate(credentials("garbage.token.value")))
                .verifyComplete();
    }

    @Test
    void tokenSignedWithDifferentKeyYieldsEmpty() {
        JwtService foreignIssuer = new JwtService(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 86_400_000L);
        String foreignToken = foreignIssuer.generateToken(
                new User("admin", "", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        StepVerifier.create(authenticationManager.authenticate(credentials(foreignToken)))
                .verifyComplete();
    }

    @Test
    void expiredTokenYieldsEmpty() {
        JwtService expiredIssuer = new JwtService(SECRET, -1000L);
        String expiredToken = expiredIssuer.generateToken(
                new User("admin", "", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        StepVerifier.create(authenticationManager.authenticate(credentials(expiredToken)))
                .verifyComplete();
    }
}
