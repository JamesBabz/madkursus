# Local AI chat

`POST /v1/ai/chat` uses the existing authenticated session and CSRF header flow.
Send `{"message":"What can I make?"}` and receive `{"answer":"..."}` (HTTP 200).
The message must contain non-whitespace text and be at most 4000 characters.
Invalid input returns 400; missing authentication returns 401 (with valid CSRF);
missing/invalid CSRF returns 403. Provider failures return 503 using `ErrorMessage`.

The generated OpenAPI controller delegates to `AiApiDelegateImpl`, then
`AiChatService`. The service calls `InventoryService.getAll()`, which resolves
`CurrentUserProvider` and loads the authenticated user's database inventory.
Only food names, quantities and units (or presence with unknown quantity) enter
the prompt. No persistence entities, user identifiers or browser inventory enter
the AI port. The database is read again for every request. No database transaction
is held open by the chat service while waiting for the model.

`AiChatPort` accepts immutable ordered messages with provider-independent roles
and returns a textual answer. `OllamaChatAdapter` alone handles the configured
model, HTTP transport, wire DTOs and provider failures. It calls Ollama's
[chat API](https://docs.ollama.com/api/chat) with streaming disabled.
There are no startup requests or automatic retries. Connection/read errors,
HTTP failures and malformed/empty/incomplete responses become `AiUnavailableException`.
Provider response bodies are not included in the public error.

Configuration in `application.yaml`:

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Backend-reachable Ollama server URL |
| `OLLAMA_MODEL` | `llama3.1:8b` | Installed model name |
| `OLLAMA_CONNECT_TIMEOUT` | `3s` | Positive connection timeout |
| `OLLAMA_READ_TIMEOUT` | `120s` | Positive read timeout, allowing local inference/model loading |

Set `OLLAMA_BASE_URL` to the home server address reachable from the backend.
Inside a container, localhost refers to that container. Ensure the selected
model is already installed in Ollama. The browser only talks to Madkursus.

The prompt asks for concise realistic dinner ideas, respects exclusions,
identifies missing ingredients (including staples), and suggests useful options
requiring one or two purchases. It does not force counts or categories, and only
asks for detailed cooking instructions when requested. Model output is advisory
natural text; no inventory or recipe mutations are performed.

## Next iteration: conversation history

This endpoint is stateless. A preference applies only to the current message;
"What would I need for that one?" cannot reliably refer to a prior answer.
The prompt asks for clarification when the referent is missing. Users can name
the dish explicitly in the meantime.

To add multi-turn support, extend the API with a conversation identifier and an
application-level history provider, scoped to `CurrentUserProvider`. Load prior
user/assistant messages, refresh inventory from the database each turn, then
append the new message using the existing ordered message model. Store successful
turns and define retention, a token/context budget, and concurrent-turn behavior.
Do not retain stale inventory system messages or accept client-supplied system
roles. Update the stateless prompt instruction. In-memory history with explicit
expiry may precede database persistence; persistence is not part of this version.

No recipe JSON generation/import, embeddings, RAG, fine-tuning, conversation
persistence, frontend chat UI, or direct browser/database access for Ollama is added.

Tests use JUnit 5, Mockito, Spring MockMvc and MockRestServiceServer; no running
Ollama or database is needed for the new tests.

## Changed files

All Java paths below are relative to `src/main/java/dk/jamesbabz/madkursus/`:

- `inbound/rest/ai/AiApiDelegateImpl.java`
- `inbound/rest/RestErrorHandler.java`
- `service/applications/AiChatService.java`
- `service/ports/AiChatPort.java`
- `service/models/AiChatMessage.java`
- `service/models/AiChatRequest.java`
- `service/models/AiChatResponse.java`
- `service/exceptions/AiUnavailableException.java`
- `outbound/ollama/OllamaChatAdapter.java`

Other files:

- `src/main/resources/openapi/madkursus-api.yaml`
- `src/main/resources/application.yaml`
- `src/test/java/dk/jamesbabz/madkursus/inbound/rest/AiApiTest.java`
- `src/test/java/dk/jamesbabz/madkursus/service/applications/AiChatServiceTest.java`
- `src/test/java/dk/jamesbabz/madkursus/outbound/ollama/OllamaChatAdapterTest.java`
- `src/test/java/dk/jamesbabz/madkursus/outbound/ollama/OllamaConfigurationTest.java`
- `docs/ai-chat.md`

API controller/interfaces and REST DTOs are generated under `build/generated/openapi`
by Gradle, following existing conventions; generated sources are not checked in.
