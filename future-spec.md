# Future Spec: Multi-URL & Custom Payload Templates

This document outlines the architectural plan for supporting multiple webhook destinations, preset integrations (like Telegram), and fully customizable JSON payloads with template placeholders.

## 1. Multiple Destinations (URLs)

Currently, the app supports a single global endpoint. The goal is to allow users to configure multiple destinations and assign specific sender rules to specific destinations.

### Data Model Changes
**`DestinationEntity` (New Table)**
- `id`: Long (Primary Key)
- `label`: String (e.g., "Main Server", "Telegram Bot")
- `type`: Enum (`CUSTOM_WEBHOOK`, `TELEGRAM_PRESET`)
- `endpointUrl`: String
- `authHeaderName`: String?
- `authHeaderValue`: String?
- `payloadTemplate`: String? (Null implies the default schema)
- `enabled`: Boolean

**`ConfiguredSenderEntity` (Update)**
- Add `destinationId`: Long (Foreign Key to `DestinationEntity`)

### UI/UX Flow
- **Settings Tab**: Becomes a "Destinations" tab. Lists configured destinations with a FAB to add a new one.
- **Add Destination Screen**:
  - Choose type: "Custom Webhook" or "Telegram Bot" etc.
  - Fill in required fields based on type.
- **Senders Tab**: When adding/editing a sender, add a Dropdown to select which Destination the messages should be routed to.

## 2. Integration Presets (e.g., Telegram)

Presets simplify the setup for common platforms by abstracting away the JSON formatting and API URLs.

### Telegram Example
- **UI Fields required from user**:
  - `Bot Token` (e.g., `123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11`)
  - `Chat ID` (e.g., `-100123456789`)
- **Under the hood**:
  - `endpointUrl` is internally generated as: `https://api.telegram.org/bot{BotToken}/sendMessage`
  - `payloadTemplate` is internally set to:
    ```json
    {
      "chat_id": "{ChatID}",
      "text": "📩 *SMS from {{sender}}*\n\n{{body}}",
      "parse_mode": "Markdown"
    }
    ```

## 3. Custom JSON with Placeholders

For "Custom Webhook" destinations, users shouldn't be locked into the default `ForwardPayload` schema if their receiving server expects something specific (like a Slack webhook or a custom API that can't be modified).

### Template Engine
We will implement a lightweight string replacement engine. 

**Available Placeholders:**
- `{{sender}}`: The raw sender address.
- `{{normalizedSender}}`: The normalized sender.
- `{{body}}`: The SMS message text (JSON escaped automatically).
- `{{receivedAt}}`: Unix epoch timestamp.
- `{{subscriptionId}}`: The SIM ID.
- `{{deviceModel}}`: Phone model.

### Execution (Worker Layer)
In `ForwardWorker` (or a dedicated `PayloadGenerator`), before making the OkHttp call:
1. Check if the `DestinationEntity` has a `payloadTemplate`.
2. If null, use the default `ForwardPayloadFactory` schema.
3. If present, run the replacement:
   ```kotlin
   var rendered = template
       .replace("{{sender}}", sanitizeJsonString(record.senderRaw))
       .replace("{{body}}", sanitizeJsonString(record.messageBody))
       // ... etc
   ```
4. Send the `rendered` string as the `application/json` request body.

### Security & Edge Cases
- **JSON Escaping**: Crucial! If an SMS body contains quotes (`"`) or newlines (`\n`), a simple string replace will create invalid JSON. We must use a utility (like Kotlinx Serialization or a custom escaper) to safely encode `{{body}}` before substitution.
- **Validation**: When the user saves a custom JSON template in the UI, we should attempt to parse it (with dummy data substituted in) using `JSONObject` to ensure they haven't made a syntax error before saving.

## 4. Migration Strategy

To move from the current single-URL setup to this multi-destination setup without breaking existing users:
1. On database upgrade (Room Migration), read the current `AppSettings` (URL and headers).
2. Create a default `DestinationEntity` named "Legacy Webhook" using those settings.
3. Update all existing `ConfiguredSenderEntity` rows to point to this new default destination ID.
4. Clear the old settings from `EncryptedSharedPreferences`.