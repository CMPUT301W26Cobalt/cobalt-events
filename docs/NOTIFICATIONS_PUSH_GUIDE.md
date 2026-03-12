# Pushing Notifications (Firestore)

All user-facing notifications live in the **`notifications`** collection. Each document is read by the app when `recipientId` matches the current user’s device ID. Notifications are shown **newest first** (sorted by `timestamp` descending).

---

## Field reference (data types and meaning)

| Field          | Data type | Meaning | Order |
|----------------|-----------|--------|--------|
| **recipientId**| String    | Device ID of the user who receives this notification. Must match the entrant’s device ID so the notification appears in their list. | Index: **ascending** (equality in query; composite index uses this first). |
| **eventId**    | String    | ID of the event this notification is about (e.g. Firestore event document ID). | — |
| **title**      | String    | Short title shown in the notification card (e.g. event name). | — |
| **message**    | String    | Body text shown in the card. | — |
| **type**       | String    | One of: `"select"`, `"got-off-waitlist"`, `"not-selected"`. Controls icon and whether the user can Accept/Decline (see below). | — |
| **read**       | String    | User’s response: `"pending"`, `"accepted"`, or `"rejected"`. Use `"pending"` when the user must still choose; use `"rejected"` for not-selected so it shows as Declined only. | — |
| **timestamp**  | Timestamp | When the notification was created. Used for ordering (newest first). Set when you create the document (e.g. server/client `Timestamp.now()` or equivalent). | **Descending** (list is sorted newest first). |

**Note:** `id` is the Firestore document ID and is set when the document is created (e.g. auto-generated). You don’t need to send it when pushing.

---

## How to push so the user sees **Accept / Decline**

- Set **`type`** to either:
  - **`"select"`** – user was selected (e.g. lottery winner).
  - **`"got-off-waitlist"`** – user was chosen as a replacement (someone else declined).
- Set **`read`** to **`"pending"`** (or omit it and have your backend set it to `"pending"` when creating the doc).

Then the app shows the **Accept** and **Decline** buttons. When the user taps one, the app updates `read` to `"accepted"` or `"rejected"` in Firestore.

**Example (conceptual):**

- `type`: `"select"`
- `read`: `"pending"`
- `recipientId`: entrant’s device ID  
- `eventId`, `title`, `message`, `timestamp`: set as usual  

Result: card with Accept/Decline; after the user chooses, the card shows the corresponding badge and buttons disappear.

---

## How to push so it shows **Declined badge only** (no Accept/Decline)

- Set **`type`** to **`"not-selected"`**.
- Set **`read`** to **`"rejected"`** when you create the document.

Then the app **never** shows Accept/Decline for that notification; it only shows the “Declined” (X) style and the Declined badge. The user is informed they were not selected and doesn’t need to take an action.

**Example (conceptual):**

- `type`: `"not-selected"`
- `read`: `"rejected"`
- `recipientId`, `eventId`, `title`, `message`, `timestamp`: set as usual  

Result: card with gray X icon and “Declined” badge only; no buttons.

---

## Quick reference

- **Accept/Decline visible:** `type` = `"select"` or `"got-off-waitlist"`, `read` = `"pending"`.
- **Declined badge only (no buttons):** `type` = `"not-selected"`, `read` = `"rejected"`.
- **List order:** newest first (by `timestamp` desc). Ensure each new notification has a valid `timestamp`.
