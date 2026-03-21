# cobalt-events
Winter26 Project

## Firebase (rules & indexes)

Rules and indexes live in the repo:

- `firestore.rules` — events (including `comments` / `replies`), `waitlists`, `notifications`, `profiles`
- `firestore.indexes.json` — composite query indexes (currently notifications)
- `firebase.json` — wires the above for the Firebase CLI

Deploy to your Firebase project (requires [Firebase CLI](https://firebase.google.com/docs/cli)):

```bash
firebase login
firebase use --add    # link project once
firebase deploy --only firestore
```

Rules are permissive for class/demo use; tighten `firestore.rules` before production.
