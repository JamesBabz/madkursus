# Login og sessioner

Madkursus bruger Spring Security `HttpSession` med Spring Session JDBC. Sessionens tilfældige ID
ligger i en HttpOnly-cookie, mens authentication og øvrige sessiondata ligger i PostgreSQL-tabellerne
`SPRING_SESSION` og `SPRING_SESSION_ATTRIBUTES`. Ingen adgangskoder eller authentication-data gemmes
i frontend-storage eller selve cookien.

Server-sessionen udløber efter 30 dages inaktivitet, og browser-cookien er persistent i 30 dage.
Spring Sessions standardoprydning fjerner udløbne databaseposter. Et eksplicit logout invaliderer
database-sessionen og sender en udløbet
session-cookie til browseren. Sessioner i forskellige browsere/enheder er uafhængige.

Databaseskemaet oprettes af Flyway; Spring må ikke initialisere produktionsskemaet automatisk.
Cookien er HttpOnly og SameSite=Strict. Lokal HTTP-udvikling bruger som standard en cookie uden
Secure-flag. Sæt `SESSION_COOKIE_SECURE=true` i HTTPS-miljøer, så produktionscookien kun sendes over
HTTPS. Databaseforbindelsen følger de eksisterende `DB_URL`, `DB_USERNAME` og `DB_PASSWORD` værdier.
