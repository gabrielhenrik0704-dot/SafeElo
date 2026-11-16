package br.com.safeelo;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(
            Map.of("error", e.getReason() == null ? "Não foi possível concluir." : e.getReason()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<?> validation(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of("error", "Confira os campos informados. Use os formatos e limites indicados."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict() {
    return ResponseEntity.status(409)
        .body(Map.of("error", "Este registro já existe ou mudou. Recarregue e tente novamente."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> failure(Exception e) {
    return ResponseEntity.status(500)
        .body(
            Map.of(
                "error", "Não foi possível concluir agora. Nenhum resultado deve ser presumido."));
  }
}
