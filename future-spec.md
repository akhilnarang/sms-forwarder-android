# Future Spec: Multi-URL & Rule-Based Routing

This document outlines the architectural plan for evolving the SMS forwarder from a single-destination, sender-allowlist model to a powerful, rule-based routing engine supporting multiple destinations, conditional logic (sender + body text), and custom payload injection.

## 1. Multiple Destinations (URLs)

The foundation requires defining where messages can be sent. Destinations describe the connection, not the rules of what gets sent there.

### Data Model Changes
**`DestinationEntity` (New Table)**
- `id`: Long (Primary Key)
- `label`: String (e.g., "Main Server", "Personal Telegram Bot")
- `type`: Enum (`CUSTOM_WEBHOOK`, `TELEGRAM_PRESET`)
- `endpointUrl`: String
- `authHeaderName`: String?
- `authHeaderValue`: String?
- `payloadTemplate`: String? (Optional. If null, the app uses the default JSON schema)
- `configJson`: String? (Optional. Stores preset-specific configuration like Bot Tokens and Chat IDs so they can be re-populated in the edit UI)
- `enabled`: Boolean

### UI/UX Flow
- **Destinations Tab (replaces Settings):** Lists configured endpoints with a FAB to add a new one.
- **Add Destination Screen:**
  - Choose type: "Custom Webhook" or "Telegram Bot"
  - Fill in required fields based on type (URL, headers, or bot tokens).

---

## 2. Rule-Based Routing Engine

This replaces the simple `ConfiguredSenderEntity`. We move to a Priority-Ordered Rule system.

### Data Model Changes
**`ForwardingRuleEntity` (Replaces ConfiguredSenderEntity)**
- `id`: Long (Primary Key)
- `priority`: Int (Used for ordering rules: 1 is highest priority)
- `label`: String (e.g., "HDFC OTPs", "All other HDFC")
- `senderPattern`: String (e.g., `*HDFC*`, supporting wildcard `*` by converting it to Regex `.*` under the hood)
- `bodyContains`: String? (Optional. e.g., "OTP")
- `destinationId`: Long (Foreign Key to `DestinationEntity`)
- `customPayloadKeys`: String? (JSON serialized map of custom key/values, e.g., `{"category": "OTP"}`)
- `enabled`: Boolean

### The Routing Logic (First Match Wins)
When a new SMS arrives:
1. The app queries `ForwardingRuleEntity` ordered by `priority ASC`.
2. It evaluates each rule:
   - Does `normalizedSender` match `senderPattern` (using Regex `.*` conversion for `*`)?
   - If `bodyContains` is not null, does `messageBody` contain the text (case-insensitive)?
3. **Strict First Match Wins:** As soon as a rule evaluates to true, the loop stops entirely. The message is routed exclusively to this single destination.
4. The message is queued for the associated `destinationId`, along with any `customPayloadKeys` defined by the rule.

### UI/UX Flow
- **Rules Tab (replaces Senders):** Lists rules in priority order.
- **Reordering:** Users can drag-and-drop rows (or use up/down arrows) to change a rule's `priority`.
- **Add Rule Screen:**
  - Label: "HDFC Transactions"
  - Sender: `*HDFC*`
  - Body contains (optional): `debited`
  - Route to: Dropdown selecting a configured Destination.
  - Custom JSON keys (optional): UI to add Key-Value pairs (e.g., Key: `category`, Value: `transaction`).
  - *Note: New rules are assigned a `priority` of `MAX(priority) + 1` (placed at the bottom of the list by default).*

---

## 3. Integration Presets (e.g., Telegram)

Presets simplify setup for common platforms by abstracting away JSON formatting and API URLs.

### Telegram Example
- **UI Fields required from user:**
  - `Bot Token`
  - `Chat ID`
- **Under the hood:**
  - `configJson`: Stores the raw token and chat ID for the UI.
  - `endpointUrl`: `https://api.telegram.org/bot{BotToken}/sendMessage`
  - `payloadTemplate` (Hidden from user):
    ```json
    {
      "chat_id": "{ChatID}",
      "text": "📩 *SMS from {{sender}}*\n\n{{body}}",
      "parse_mode": "Markdown"
    }
    ```

---

## 4. Custom JSON with Placeholders & Rule Overrides

For "Custom Webhook" destinations, users can define exactly how the payload looks using a template engine, and rules can inject data into that template.

### Template Engine
A lightweight string replacement engine runs before the OkHttp call.

**Available Placeholders:**
- `{{sender}}`: Raw sender.
- `{{body}}`: SMS text.
- `{{receivedAt}}`: Epoch timestamp.
- **Dynamic Rule Placeholders:** Any key defined in the Rule's `customPayloadKeys` can be referenced.
  - *Example:* If the rule defines `{"category": "OTP"}`, the template can use `{{category}}`.

### Security & Escaping
If an SMS body contains quotes (`"`) or newlines (`\n`), a simple string replace creates invalid JSON. The app must safely encode string values using a utility like `JSONObject.quote(text)` or a serialization library before substituting them into the JSON template placeholders.

---

## 5. Migration Strategy

To move existing users to this new architecture safely:
1. **Destinations:** Read current `AppSettings` (URL and headers). Create a default `DestinationEntity` ("Legacy Webhook").
2. **Rules:** Read all existing `ConfiguredSenderEntity` rows. For each:
   - Create a `ForwardingRuleEntity`.
   - Set `senderPattern` to the existing normalized sender.
   - Set `bodyContains` to null.
   - Set `destinationId` to the "Legacy Webhook" ID.
   - Set `priority` sequentially based on `id` (lowest ID becomes priority 1).
3. **Cleanup:** Delete the old `ConfiguredSenderEntity` table and clear legacy settings from `EncryptedSharedPreferences`.
