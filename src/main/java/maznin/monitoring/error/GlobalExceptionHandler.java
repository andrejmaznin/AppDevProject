package maznin.monitoring.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        pd.setInstance(URI.create(exchange.getRequest().getPath().value()));
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex, ServerWebExchange exchange) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setInstance(URI.create(exchange.getRequest().getPath().value()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }
}
