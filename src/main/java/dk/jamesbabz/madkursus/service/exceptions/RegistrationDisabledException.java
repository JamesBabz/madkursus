package dk.jamesbabz.madkursus.service.exceptions;

public class RegistrationDisabledException extends RuntimeException {
    public RegistrationDisabledException() { super("Registration is disabled"); }
}
