Keep local Firebase and environment-only files in this folder.

Expected local files:
- `firebase-service-account.json`
- `application-dev-secrets.yaml`

These files are ignored by git on purpose. The backend defaults already point to this folder when `config.firebase-enabled=true`.

Copy the `.example` files in this directory when creating new local secrets. If a Firebase key was ever committed, pasted, or shared, rotate it using `FIREBASE_ROTATION.md`.
