# Event comments & replies (Firestore)

The Android app stores discussion threads under each event document:

```
events/{eventId}/comments/{commentId}
  userId: string
  userName: string
  text: string
  likes: number
  createdAt: timestamp

events/{eventId}/comments/{commentId}/replies/{replyId}
  (same fields; comment is implied by path)
```

## Indexes

No composite index is required for current comment/reply queries (`orderBy(createdAt)` only).

If you later add a compound query (for example filtering + ordering across multiple fields), add it to **`firestore.indexes.json`** and redeploy.

## Security rules

**`firestore.rules`** in the repo root defines rules for `events` (including `comments` / `replies` subcollections), `waitlists`, `notifications`, and `profiles`.

They are intentionally permissive so the app works without Firebase Auth; **tighten before production**.

Deploy:

```bash
firebase login
firebase use <your-project-id>   # or firebase init
firebase deploy --only firestore
```
