package maznin.monitoring.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long ONE_DAY_MS = 86_400_000L;

    private static UserDetails user(String username) {
        return new User(username, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void generatedTokenContainsUsername() {
        JwtService jwtService = new JwtService(SECRET, ONE_DAY_MS);
        String token = jwtService.generateToken(user("admin"));

        assertEquals("admin", jwtService.extractUsername(token));
    }

    @Test
    void tokenIsValidForItsOwner() {
        JwtService jwtService = new JwtService(SECRET, ONE_DAY_MS);
        String token = jwtService.generateToken(user("admin"));

        assertTrue(jwtService.isTokenValid(token, user("admin")));
        assertFalse(jwtService.isTokenValid(token, user("someone-else")));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expiringService = new JwtService(SECRET, -1000L); // выпущен уже просроченным
        String token = expiringService.generateToken(user("admin"));

        assertThrows(ExpiredJwtException.class, () -> expiringService.extractUsername(token));
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        JwtService issuer = new JwtService(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", ONE_DAY_MS);
        JwtService verifier = new JwtService(SECRET, ONE_DAY_MS);
        String foreignToken = issuer.generateToken(user("admin"));

        assertThrows(JwtException.class, () -> verifier.extractUsername(foreignToken));
    }

    @Test
    void garbageTokenIsRejected() {
        JwtService jwtService = new JwtService(SECRET, ONE_DAY_MS);

        assertThrows(JwtException.class, () -> jwtService.extractUsername("not-a-jwt"));
    }
}
