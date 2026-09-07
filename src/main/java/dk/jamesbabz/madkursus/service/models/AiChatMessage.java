package dk.jamesbabz.madkursus.service.models;

public record AiChatMessage(Role role, String content) {
    public enum Role { SYSTEM, USER, ASSISTANT }
}
