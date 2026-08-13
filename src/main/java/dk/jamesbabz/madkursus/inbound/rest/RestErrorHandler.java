package dk.jamesbabz.madkursus.inbound.rest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dk.jamesbabz.madkursus.inbound.rest.dto.ErrorMessageDTO;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.exceptions.DuplicateUsernameException;
import dk.jamesbabz.madkursus.service.exceptions.RegistrationDisabledException;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.exceptions.DuplicateProductException;
import dk.jamesbabz.madkursus.service.exceptions.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

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

    @ExceptionHandler({DuplicateUsernameException.class, DuplicateProductException.class})
    ResponseEntity<ErrorMessageDTO> duplicate(RuntimeException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorMessageDTO> conflict(ConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(RegistrationDisabledException.class)
    ResponseEntity<ErrorMessageDTO> registrationDisabled(RegistrationDisabledException exception) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage(), List.of());
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    ResponseEntity<ErrorMessageDTO> invalidCredentials(RuntimeException exception) {
        return response(HttpStatus.UNAUTHORIZED, "Invalid username or password", List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorMessageDTO> validation(MethodArgumentNotValidException exception) {
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
        return response(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    @ExceptionHandler(InvalidInputException.class)
    ResponseEntity<ErrorMessageDTO> invalidInput(InvalidInputException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), List.of());
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
