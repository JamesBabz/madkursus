# Global systemdata

Madkursus har tre typer globale, applikationsstyrede templates. De er læsbare
for normale brugere, men har ingen offentlig mutations-API.

| Systemdata | Kanonisk kilde | Databaseversionering |
|---|---|---|
| ProductTemplates | `src/main/resources/seed/product-templates.json` | V6, V10 og V14.1 etablerede den aktuelle tilstand; fremtidige ændringer kræver nye migrationer |
| RecipeTemplates | `src/main/resources/seed/recipe-templates.json` | V21 importerede den kanoniske v2-kilde |
| CookingProcesses | `src/main/resources/seed/cooking-processes.json` | V19 importerede den identiske legacy-kilde; fremtidige ændringer kræver nye migrationer |

Seed-filerne er udviklerens reviewbare repræsentation af den ønskede aktuelle
tilstand. De er ikke runtime-konfiguration og bliver ikke automatisk synkroniseret
til databasen ved opstart. Enhver databaseændring skal være bevidst, additiv og
versioneret med Flyway. Allerede anvendte migrationsfiler og deres legacy-input
bevares uændrede.

Valideringstests kontrollerer enums, identiteter, interne strukturer og
krydsreferencer mellem de tre kilder. Se de domænespecifikke dokumenter for
ændringsprocedurer og SQL-inspektion.
