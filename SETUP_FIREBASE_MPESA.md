# Assignment 2 setup: Firebase login + M-Pesa payments

## A. Firebase (once)
1. Put `google-services.json` (downloaded AFTER adding the SHA-1 and enabling Email/Password and Google sign-in) at `app/google-services.json`.
2. Open it and search for `oauth_client`: it must contain an entry with `"client_type": 3`. If it is `[]`, the SHA-1 or Google provider was not saved: fix in Firebase, download the file again.
3. Android Studio: **File > Sync Project with Gradle Files**, then Run.
4. Demo accounts (jane@sacco.demo / Member@123, admin@sacco.demo / Admin@123) are created in Firebase automatically the first time you log in with them.

## B. M-Pesa, option 1: simulated (works with no server)
`app/build.gradle.kts` already has `MPESA_SIMULATED = true`. Deposits and repayments show a "SIMULATED M-Pesa" dialog and complete after 4 seconds with a SIM receipt. A phone number ending in 0000 simulates the customer cancelling.

## C. M-Pesa, option 2: Daraja sandbox through the backend
1. `cd backend`, then `python -m venv venv`, activate it, and `pip install -r requirements.txt`.
2. Copy `.env.example` to `.env` and fill it in (Daraja key, secret, shortcode, passkey, CALLBACK_SECRET, FIREBASE_PROJECT_ID). Leave PUBLIC_URL for step 4.
3. Start the server: `uvicorn main:app --host 0.0.0.0 --port 8000` (check http://localhost:8000/health).
4. In a second terminal run `ngrok http 8000`. Copy the https address it shows into `PUBLIC_URL` in `.env`, then stop and restart uvicorn.
5. In `app/build.gradle.kts` set `BACKEND_URL` to the same address with a trailing slash and set `MPESA_SIMULATED` to `false`. Sync and run the app again.
6. Deposit or repay: the app sends the request, the server calls Daraja, and the result returns through the callback (or a status query).
7. If ngrok gives you a new address (it does each restart on the free plan) repeat steps 4 and 5.

Tests for the backend: `pip install pytest httpx` then `pytest -q` inside `backend`.

## Security notes
- The Daraja key, secret and passkey exist only in `backend/.env`. Never commit it or paste it anywhere.
- The backend checks the user's Firebase ID token on every call and only lets you read your own payments.
- The callback address contains CALLBACK_SECRET, and amounts are checked against what was requested.
- Member and admin roles are stored in the app database for this prototype. A production system would put them in Firebase custom claims and enforce them on a server.
