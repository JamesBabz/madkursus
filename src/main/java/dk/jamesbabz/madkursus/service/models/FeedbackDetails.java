package dk.jamesbabz.madkursus.service.models;

/** Admin read data; creator identity remains stored on Feedback as a UUID. */
public record FeedbackDetails(Feedback feedback, String createdByUsername) {}
