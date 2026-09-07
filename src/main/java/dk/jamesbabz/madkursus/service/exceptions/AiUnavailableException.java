package dk.jamesbabz.madkursus.service.exceptions;

public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException() {
        super("The meal-planning assistant is temporarily unavailable. Please try again later.");
    }

    public AiUnavailableException(Throwable cause) {
        super("The meal-planning assistant is temporarily unavailable. Please try again later.", cause);
    }
}
