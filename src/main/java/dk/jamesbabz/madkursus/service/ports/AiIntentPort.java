package dk.jamesbabz.madkursus.service.ports;
import dk.jamesbabz.madkursus.service.models.AiChatIntent;
public interface AiIntentPort {
    AiChatIntent interpret(String message);
}
