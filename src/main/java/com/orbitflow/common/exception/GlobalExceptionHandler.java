package com.orbitflow.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("https://orbitflow.local/problems/" + code));
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        pd.setProperty("traceId", UUID.randomUUID().toString());
        pd.setProperty("timestamp", Instant.now().toString());
        pd.setProperty("path", req.getRequestURI());
        return pd;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException e, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, "not_found", e.getMessage(), req);
    }

    @ExceptionHandler({ForbiddenOperationException.class, AccessDeniedException.class})
    public ProblemDetail forbidden(RuntimeException e, HttpServletRequest req) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", e.getMessage(), req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail unauthorized(AuthenticationException e, HttpServletRequest req) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required", req);
    }

    @ExceptionHandler({ConflictException.class, ObjectOptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    public ProblemDetail conflict(RuntimeException e, HttpServletRequest req) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "conflict",
                e.getMessage() != null ? e.getMessage() : "Concurrent modification detected", req);
        if (e instanceof ConflictException ce && ce.getCurrentState() != null) {
            pd.setProperty("currentState", ce.getCurrentState());
        }
        return pd;
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ProblemDetail badRequest(RuntimeException e, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "bad_request", e.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e, HttpServletRequest req) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("Validation failed");
        return problem(HttpStatus.BAD_REQUEST, "validation_failed", detail, req);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail generic(Exception e, HttpServletRequest req) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Unexpected error", req);
    }
}
