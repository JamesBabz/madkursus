package dk.jamesbabz.madkursus.inbound.rest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.inbound.rest.dto.ErrorMessageDTO;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class RestErrorHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorMessageDTO> notFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorMessageDTO> dataConflict(DataIntegrityViolationException exception) {
        return response(HttpStatus.CONFLICT, "The resource is still referenced by other data", List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorMessageDTO> validation(MethodArgumentNotValidException exception) {
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
        return response(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorMessageDTO> unexpected(Exception exception) {
        UUID correlationId = UUID.randomUUID();
        log.error("Unexpected REST error, correlationId={}", correlationId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorMessageDTO("Unexpected server error", List.of(), OffsetDateTime.now(), correlationId));
    }

    private ResponseEntity<ErrorMessageDTO> response(HttpStatus status, String message, List<String> errors) {
        return ResponseEntity.status(status)
                .body(new ErrorMessageDTO(message, errors, OffsetDateTime.now(), UUID.randomUUID()));
    }
}
