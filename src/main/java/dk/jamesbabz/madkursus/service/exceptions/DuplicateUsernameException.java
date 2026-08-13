package dk.jamesbabz.madkursus.service.exceptions;

public class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException() { super("Username is already in use"); }
}
