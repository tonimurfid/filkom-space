package id.ac.ub.filkom.reservation.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorResponse> business(ApiException e, HttpServletRequest r) {
    return ResponseEntity.status(e.status)
        .body(new ErrorResponse(e.status, e.code, e.getMessage(), r.getRequestURI()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorResponse> forbidden(AccessDeniedException e, HttpServletRequest r) {
    return ResponseEntity.status(403)
        .body(new ErrorResponse(403, "FORBIDDEN", "Akses ditolak.", r.getRequestURI()));
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String path = ((ServletWebRequest) request).getRequest().getRequestURI();
    return new ResponseEntity<>(
        new ErrorResponse(status.value(), "INVALID_REQUEST", "Request tidak valid.", path),
        headers,
        status);
  }
}
