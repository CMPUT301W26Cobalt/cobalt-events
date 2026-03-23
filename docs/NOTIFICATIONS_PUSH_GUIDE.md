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
| **type**       | String    | One of four: `"selected"`, `"got-off-waitlist"`, `"not-selected"`, `"private-event"`. Controls icon and whether the user can Accept/Decline (see below). | — |
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
  - **`"selected"`** – user was selected (e.g. lottery winner).
  - **`"got-off-waitlist"`** – user was chosen as a replacement (someone else declined).
  - **`"private-event"`** – invite to a private event (lock icon).
- Ensure the user has a **waitlist entry** at `waitlists/{eventId}/entries/{deviceId}` with **`status`** = **`"pending"`** (or omit status and have your backend set it to `"pending"` when creating the entry). The app shows Accept/Decline when the waitlist entry status is pending.

When the user taps Accept or Decline, the app updates only the **waitlist entry’s** `status` to `"accepted"` or `"rejected"`; it does not update the notification document.

**Example (conceptual):**

- Notification: `type`: `"selected"`, `recipientId`: entrant’s device ID, `eventId`, `title`, `message`, `timestamp`.
- Waitlist: ensure `waitlists/{eventId}/entries/{deviceId}` exists with `status`: `"pending"`.

Result: card with Accept/Decline; after the user chooses, the card shows the corresponding badge and buttons disappear (because the waitlist entry status is updated).

---

## How to push so it shows **Declined badge only** (no Accept/Decline)

- Set **`type`** to **`"not-selected"`**.
- Set the **waitlist entry** `waitlists/{eventId}/entries/{deviceId}.status` to **`"rejected"`** when you create or update the entry (e.g. when you notify them they were not selected).

Then the app **never** shows Accept/Decline for that notification; it shows the X icon and informational copy. You can still set waitlist entry `status` to `"rejected"` / store `read` as declined for your records — the list **does not** show the top-right “Declined” badge for `not-selected` cards.

**Example (conceptual):**

- Notification: `type`: `"not-selected"`, `recipientId`, `eventId`, `title`, `message`, `timestamp`.
- Waitlist: `waitlists/{eventId}/entries/{deviceId}.status`: `"rejected"` (optional, for backend consistency).

Result: card with gray X icon; no Accept/Decline buttons; no declined badge in the corner.

---

## Quick reference

- **Accept/Decline visible:** `type` = `"selected"`, `"got-off-waitlist"`, or `"private-event"`, and waitlist entry `status` = `"pending"` (and the notification document’s `read` field when your pipeline stores it).
- **`not-selected`:** X icon, no buttons, no corner declined badge (even if status is rejected/declined in data).
- **List order:** newest first (by `timestamp` desc). Ensure each new notification has a valid `timestamp`.
- **Response state:** stored only on `waitlists/{eventId}/entries/{deviceId}.status` (`"pending"` | `"accepted"` | `"rejected"`), not on the notification document.
