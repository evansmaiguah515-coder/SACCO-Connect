"""
SACCO Connect payment backend (FastAPI).

Flow
  Android app --(Firebase ID token)--> POST /mpesa/stkpush --> Safaricom Daraja STK Push
  Safaricom   --------------------------> POST /mpesa/callback/<secret>   (payment result)
  Android app --(Firebase ID token)--> GET  /mpesa/status/<checkout id>   (poll for the result)

The Daraja keys live only in this server's .env file. The app never sees them.
"""
import base64
import hmac
import logging
import os
import threading
import time
from datetime import datetime

import requests
from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException, Request
from google.auth.transport import requests as g_requests
from google.oauth2 import id_token as google_id_token
from pydantic import BaseModel, Field

load_dotenv()
log = logging.getLogger("sacco")
logging.basicConfig(level=logging.INFO)

ENVIRONMENT = os.getenv("MPESA_ENV", "sandbox")
BASE_URL = "https://sandbox.safaricom.co.ke" if ENVIRONMENT == "sandbox" else "https://api.safaricom.co.ke"
CONSUMER_KEY = os.getenv("MPESA_CONSUMER_KEY", "")
CONSUMER_SECRET = os.getenv("MPESA_CONSUMER_SECRET", "")
SHORTCODE = os.getenv("MPESA_SHORTCODE", "")
PASSKEY = os.getenv("MPESA_PASSKEY", "")
PUBLIC_URL = os.getenv("PUBLIC_URL", "").rstrip("/")
CALLBACK_SECRET = os.getenv("CALLBACK_SECRET", "")
FIREBASE_PROJECT_ID = os.getenv("FIREBASE_PROJECT_ID", "")

app = FastAPI(title="SACCO Connect payments")

# checkout_request_id -> payment record (in memory: fine for a demo, use a database in production)
payments: dict[str, dict] = {}
lock = threading.Lock()
_token = {"value": None, "expires": 0.0}


def require_config() -> None:
    missing = [n for n, v in [("MPESA_CONSUMER_KEY", CONSUMER_KEY), ("MPESA_CONSUMER_SECRET", CONSUMER_SECRET),
                              ("MPESA_SHORTCODE", SHORTCODE), ("MPESA_PASSKEY", PASSKEY), ("PUBLIC_URL", PUBLIC_URL),
                              ("CALLBACK_SECRET", CALLBACK_SECRET), ("FIREBASE_PROJECT_ID", FIREBASE_PROJECT_ID)] if not v]
    if missing:
        raise HTTPException(500, "Server is not configured: " + ", ".join(missing))


def verify_user(authorization: str | None) -> str:
    """Checks the Firebase ID token and returns the Firebase user id. Raises 401 if invalid."""
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(401, "Missing sign-in token")
    token = authorization.split(" ", 1)[1].strip()
    try:
        claims = google_id_token.verify_firebase_token(token, g_requests.Request(), audience=FIREBASE_PROJECT_ID)
    except Exception:
        claims = None
    if not claims or "sub" not in claims:
        raise HTTPException(401, "Invalid or expired sign-in token")
    return claims["sub"]


def access_token() -> str:
    if _token["value"] and time.time() < _token["expires"] - 60:
        return _token["value"]
    r = requests.get(f"{BASE_URL}/oauth/v1/generate?grant_type=client_credentials", auth=(CONSUMER_KEY, CONSUMER_SECRET), timeout=20)
    if r.status_code != 200:
        log.error("Daraja auth failed: HTTP %s", r.status_code)
        raise HTTPException(502, "Could not authenticate with Safaricom (check consumer key and secret)")
    data = r.json()
    _token["value"] = data["access_token"]
    _token["expires"] = time.time() + int(data.get("expires_in", 3599))
    return _token["value"]


def stk_password() -> tuple[str, str]:
    stamp = datetime.now().strftime("%Y%m%d%H%M%S")
    return base64.b64encode(f"{SHORTCODE}{PASSKEY}{stamp}".encode()).decode(), stamp


class StkRequest(BaseModel):
    phone: str = Field(pattern=r"^254[17]\d{8}$")
    amount: int = Field(ge=1, le=150000)
    reference: str = Field(min_length=1, max_length=20)


@app.get("/health")
def health():
    return {"status": "ok", "environment": ENVIRONMENT}


@app.post("/mpesa/stkpush")
def stk_push(req: StkRequest, authorization: str | None = Header(default=None)):
    require_config()
    uid = verify_user(authorization)
    password, stamp = stk_password()
    payload = {
        "BusinessShortCode": SHORTCODE, "Password": password, "Timestamp": stamp,
        "TransactionType": "CustomerPayBillOnline", "Amount": req.amount,
        "PartyA": req.phone, "PartyB": SHORTCODE, "PhoneNumber": req.phone,
        "CallBackURL": f"{PUBLIC_URL}/mpesa/callback/{CALLBACK_SECRET}",
        "AccountReference": req.reference, "TransactionDesc": "SACCO payment",
    }
    try:
        r = requests.post(f"{BASE_URL}/mpesa/stkpush/v1/processrequest", json=payload,
                          headers={"Authorization": f"Bearer {access_token()}"}, timeout=30)
        data = r.json()
    except requests.RequestException:
        raise HTTPException(502, "Could not reach Safaricom")
    if str(data.get("ResponseCode")) != "0":
        log.warning("STK push rejected: %s", data.get("errorMessage") or data.get("ResponseDescription"))
        raise HTTPException(502, data.get("errorMessage") or data.get("ResponseDescription") or "Safaricom rejected the request")
    checkout_id = data["CheckoutRequestID"]
    with lock:
        payments[checkout_id] = {"uid": uid, "amount": req.amount, "status": "PENDING", "receipt": None,
                                 "message": "Waiting for the customer to enter their PIN", "created": time.time(), "last_query": 0.0}
    log.info("STK push accepted for checkout %s", checkout_id)
    return {"checkout_request_id": checkout_id, "message": data.get("CustomerMessage", "Request sent")}


@app.post("/mpesa/callback/{secret}")
async def callback(secret: str, request: Request):
    """Called by Safaricom with the payment result. The secret in the address keeps strangers out."""
    if not CALLBACK_SECRET or not hmac.compare_digest(secret, CALLBACK_SECRET):
        raise HTTPException(404, "Not found")
    try:
        cb = (await request.json())["Body"]["stkCallback"]
        checkout_id = cb["CheckoutRequestID"]
        code = int(cb["ResultCode"])
    except Exception:
        return {"ResultCode": 0, "ResultDesc": "Accepted"}
    items = {i.get("Name"): i.get("Value") for i in (cb.get("CallbackMetadata") or {}).get("Item", [])}
    with lock:
        p = payments.get(checkout_id)
        if p and p["status"] == "PENDING":
            if code == 0:
                paid = items.get("Amount")
                if paid is not None and float(paid) != float(p["amount"]):
                    p.update(status="FAILED", message="Amount paid did not match the amount requested")
                else:
                    p.update(status="SUCCESS", receipt=str(items.get("MpesaReceiptNumber") or ""), message="Payment confirmed")
            elif code == 1032:
                p.update(status="CANCELLED", message="Request cancelled by the customer")
            else:
                p.update(status="FAILED", message=cb.get("ResultDesc", "Payment failed"))
    log.info("Callback for %s: result code %s", checkout_id, code)
    return {"ResultCode": 0, "ResultDesc": "Accepted"}


def query_daraja(checkout_id: str) -> dict | None:
    password, stamp = stk_password()
    try:
        r = requests.post(f"{BASE_URL}/mpesa/stkpushquery/v1/query",
                          json={"BusinessShortCode": SHORTCODE, "Password": password, "Timestamp": stamp, "CheckoutRequestID": checkout_id},
                          headers={"Authorization": f"Bearer {access_token()}"}, timeout=20)
        return r.json()
    except Exception:
        return None


@app.get("/mpesa/status/{checkout_id}")
def status(checkout_id: str, authorization: str | None = Header(default=None)):
    uid = verify_user(authorization)
    with lock:
        p = payments.get(checkout_id)
    if not p:
        raise HTTPException(404, "Unknown payment")
    if p["uid"] != uid:
        raise HTTPException(403, "Not your payment")
    # No callback yet? Ask Safaricom directly (at most every 6 seconds).
    if p["status"] == "PENDING" and time.time() - p["created"] > 12 and time.time() - p["last_query"] > 6:
        with lock:
            p["last_query"] = time.time()
        data = query_daraja(checkout_id)
        if data and "ResultCode" in data:
            code = str(data["ResultCode"])
            with lock:
                if p["status"] == "PENDING":
                    if code == "0":
                        p.update(status="SUCCESS", message="Payment confirmed (status query)")
                    elif code == "1032":
                        p.update(status="CANCELLED", message="Request cancelled by the customer")
                    else:
                        p.update(status="FAILED", message=data.get("ResultDesc", "Payment failed"))
    with lock:
        return {"status": p["status"], "receipt": p["receipt"], "message": p["message"]}
