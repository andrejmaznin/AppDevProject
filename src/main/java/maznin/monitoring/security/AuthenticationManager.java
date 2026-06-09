package maznin.monitoring.security;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtService jwtService;

    public AuthenticationManager(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String authToken = authentication.getCredentials().toString();
        String username;
        try {
            username = jwtService.extractUsername(authToken);
        } catch (Exception e) {
            // Malformed/expired/forged token — treat as unauthenticated, not a server error
            return Mono.empty();
        }

        return Mono.justOrEmpty(username)
                .flatMap(user -> {
                    if (jwtService.isTokenValid(authToken, new org.springframework.security.core.userdetails.User(username, "", List.of(new SimpleGrantedAuthority("ROLE_USER"))))) {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                        return Mono.just(auth);
                    } else {
                        return Mono.empty();
                    }
                });
    }
}
