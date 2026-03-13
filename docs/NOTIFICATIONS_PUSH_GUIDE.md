# Pushing Notifications (Firestore)

All user-facing notifications live in the **`notifications`** collection. Each document is read by the app when `recipientId` matches the current user’s device ID. Notifications are shown **newest first** (sorted by `timestamp` descending).

**User response status (pending / accepted / rejected)** is **not** stored on the notification document. It is stored on the **waitlist entry**: `waitlists/{eventId}/entries/{deviceId}` in the **`status`** field. When the user taps Accept or Decline, the app updates that waitlist entry’s `status` to `"accepted"` or `"rejected"`. The notification list UI derives the displayed state from the waitlist entry status.

---

## Field reference (data types and meaning)

| Field          | Data type | Meaning | Order |
|----------------|-----------|--------|--------|
| **recipientId**| String    | Device ID of the user who receives this notification. Must match the entrant’s device ID so the notification appears in their list. | Index: **ascending** (equality in query; composite index uses this first). |
| **eventId**    | String    | ID of the event this notification is about (e.g. Firestore event document ID). Used to look up the user’s response status from `waitlists/{eventId}/entries/{deviceId}.status`. | — |
| **title**      | String    | Short title shown in the notification card (e.g. event name). | — |
| **message**    | String    | Body text shown in the card. | — |
| **type**       | String    | One of: `"select"`, `"got-off-waitlist"`, `"not-selected"`. Controls icon and whether the user can Accept/Decline (see below). | — |
| **timestamp**  | Timestamp | When the notification was created. Used for ordering (newest first). Set when you create the document (e.g. server/client `Timestamp.now()` or equivalent). | **Descending** (list is sorted newest first). |

**Note:** `id` is the Firestore document ID and is set when the document is created (e.g. auto-generated). You don’t need to send it when pushing. The notification document does **not** have a `read` field; response status is stored on the waitlist entry only.

---

## Waitlist entry status (source of truth for Accept/Decline)

For each event the user is on the waitlist for, their response is stored at:

- **Path:** `waitlists/{eventId}/entries/{deviceId}`
- **Field:** `status` — one of `"pending"`, `"accepted"`, `"rejected"`

When the user taps **Accept**, the app sets `waitlists/{eventId}/entries/{deviceId}.status` to `"accepted"`. When they tap **Decline**, it sets it to `"rejected"`. The notification list and badges (Accepted / Declined) are driven by this waitlist entry status, not by any field on the notification document.

---

## How to push so the user sees **Accept / Decline**

- Set **`type`** to either:
  - **`"select"`** – user was selected (e.g. lottery winner).
  - **`"got-off-waitlist"`** – user was chosen as a replacement (someone else declined).
- Ensure the user has a **waitlist entry** at `waitlists/{eventId}/entries/{deviceId}` with **`status`** = **`"pending"`** (or omit status and have your backend set it to `"pending"` when creating the entry). The app shows Accept/Decline when the waitlist entry status is pending.

When the user taps Accept or Decline, the app updates only the **waitlist entry’s** `status` to `"accepted"` or `"rejected"`; it does not update the notification document.

**Example (conceptual):**

- Notification: `type`: `"select"`, `recipientId`: entrant’s device ID, `eventId`, `title`, `message`, `timestamp`.
- Waitlist: ensure `waitlists/{eventId}/entries/{deviceId}` exists with `status`: `"pending"`.

Result: card with Accept/Decline; after the user chooses, the card shows the corresponding badge and buttons disappear (because the waitlist entry status is updated).

---

## How to push so it shows **Declined badge only** (no Accept/Decline)

- Set **`type`** to **`"not-selected"`**.
- Set the **waitlist entry** `waitlists/{eventId}/entries/{deviceId}.status` to **`"rejected"`** when you create or update the entry (e.g. when you notify them they were not selected).

Then the app **never** shows Accept/Decline for that notification; it only shows the “Declined” (X) style and the Declined badge, based on the waitlist entry status.

**Example (conceptual):**

- Notification: `type`: `"not-selected"`, `recipientId`, `eventId`, `title`, `message`, `timestamp`.
- Waitlist: `waitlists/{eventId}/entries/{deviceId}.status`: `"rejected"`.

Result: card with gray X icon and “Declined” badge only; no buttons.

---

## Quick reference

- **Accept/Decline visible:** `type` = `"select"` or `"got-off-waitlist"`, and waitlist entry `status` = `"pending"`.
- **Declined badge only (no buttons):** `type` = `"not-selected"`, waitlist entry `status` = `"rejected"`.
- **List order:** newest first (by `timestamp` desc). Ensure each new notification has a valid `timestamp`.
- **Response state:** stored only on `waitlists/{eventId}/entries/{deviceId}.status` (`"pending"` | `"accepted"` | `"rejected"`), not on the notification document.
