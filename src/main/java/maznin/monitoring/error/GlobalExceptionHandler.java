package maznin.monitoring.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;

/**
 * Преобразование исключений контроллеров и сервисов в ответы RFC 7807.
 *
 * <p>Покрывает всё, что возникает после прохождения цепочки безопасности;
 * 401/403 формируются отдельно в {@code SecurityConfig} тем же форматом.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Ожидаемые ошибки бизнес-логики ({@code ResponseStatusException}:
     * 404 «пациент не найден», 401 «неверные учётные данные» и т.п.) —
     * статус и detail берутся из исключения, стектрейс не логируется.
     * Известным статусам присваивается тип из каталога {@link ProblemType}
     * (RFC 9457); неизвестным остаётся {@code about:blank}.
     *
     * @return Problem Details с {@code instance} = путь запроса
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        ProblemType problemType = ProblemType.forStatus(ex.getStatusCode());
        if (problemType != null) {
            pd.setType(problemType.uri());
            pd.setTitle(problemType.getTitle());
        }
        pd.setInstance(URI.create(exchange.getRequest().getPath().value()));
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    /**
     * Последний рубеж: любые непредвиденные исключения → 500 с обезличенным
     * detail (без утечки внутренних деталей клиенту) и полным стектрейсом
     * в логе.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex, ServerWebExchange exchange) {
        logger.error("Unhandled exception for {} {}", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setType(ProblemType.INTERNAL_ERROR.uri());
        pd.setTitle(ProblemType.INTERNAL_ERROR.getTitle());
        pd.setInstance(URI.create(exchange.getRequest().getPath().value()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
