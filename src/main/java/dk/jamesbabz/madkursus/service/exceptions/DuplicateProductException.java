package dk.jamesbabz.madkursus.service.exceptions;

public class DuplicateProductException extends RuntimeException {
    public DuplicateProductException(String name) { super("Product already exists: " + name); }
}
