package dk.jamesbabz.madkursus.service.models;

/** Who owns a process value. Only INPUT values are part of the normal recipe form. */
public enum CookingProcessParameterSource {
    INPUT, DEFAULT, OVERRIDEABLE_DEFAULT, DERIVED
}
