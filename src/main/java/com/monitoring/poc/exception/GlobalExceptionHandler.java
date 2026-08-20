package com.monitoring.poc.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.monitoring.poc.aiops.OllamaUnavailableException;
import com.monitoring.poc.servers.ssh.SshProvisioningException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central error mapping. Deliberately never echoes raw exception messages from
 * infrastructure-facing code (SSH, crypto) back to the client - only the
 * explicit, sanitized message on ApiException is exposed.
 * <p>
 * AccessDeniedException from @PreAuthorize method security is thrown inside
 * DispatcherServlet's handler invocation, so it reaches this advice BEFORE it
 * would ever propagate out to SecurityConfig's filter-level
 * accessDeniedHandler - it must be mapped here too, or it falls through to
 * the generic 500 handler below.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Bu islem icin yetkiniz yok"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthentication(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Kimlik dogrulama gerekli"));
    }

    @ExceptionHandler(SshProvisioningException.class)
    public ResponseEntity<Map<String, String>> handleSshProvisioning(SshProvisioningException e) {
        // Messages built in SshProvisioningService/JSchClientFactoryImpl never
        // include the SSH password - only host/command/hostname - safe to surface.
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(OllamaUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleOllamaUnavailable(OllamaUnavailableException e) {
        // e.getMessage() never includes response bodies, only host/timeout info - safe to surface.
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    // Spring's static-resource lookup (e.g. the browser's automatic
    // favicon.ico request) throws this when a file isn't found. Without an
    // explicit handler it falls into the generic 500 handler below instead
    // of the plain 404 a missing static file should be.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Beklenmeyen bir hata olustu"));
    }
}
