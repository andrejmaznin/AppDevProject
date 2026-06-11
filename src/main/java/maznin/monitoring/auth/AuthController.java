package maznin.monitoring.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST-эндпоинт выпуска JWT-токенов — единственная точка API, доступная
 * без аутентификации.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Обменивает учётные данные на JWT-токен.
     *
     * @param request логин и пароль
     * @return токен для заголовка {@code Authorization: Bearer};
     *         401 Problem Details при неверных учётных данных
     */
    @PostMapping("/token")
    public Mono<AuthResponse> authenticate(@RequestBody AuthRequest request) {
        return authService.authenticate(request);
    }
}
