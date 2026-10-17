"""Run with:  pip install pytest httpx  then  pytest -q   (uses fake Safaricom + fake Firebase, no keys needed)"""
import os
os.environ.update(MPESA_CONSUMER_KEY="k", MPESA_CONSUMER_SECRET="s", MPESA_SHORTCODE="174379", MPESA_PASSKEY="p",
                  PUBLIC_URL="https://example.test", CALLBACK_SECRET="topsecret", FIREBASE_PROJECT_ID="demo-project")
import main
from fastapi.testclient import TestClient

client = TestClient(main.app)


class FakeResp:
    def __init__(self, data, code=200): self._d, self.status_code = data, code
    def json(self): return self._d


def fake_post(url, **kw):
    if "processrequest" in url:
        return FakeResp({"ResponseCode": "0", "CheckoutRequestID": "ws_CO_1", "CustomerMessage": "Success. Request accepted"})
    return FakeResp({"ResultCode": "1037", "ResultDesc": "timeout"})


def setup_module(_):
    main.requests.post = fake_post
    main.requests.get = lambda *a, **k: FakeResp({"access_token": "t", "expires_in": "3599"})
    main.google_id_token.verify_firebase_token = lambda t, r, audience=None: {"sub": "user-1"} if t == "good" else None


H = {"Authorization": "Bearer good"}
BODY = {"phone": "254712345678", "amount": 500, "reference": "SACCO-1"}


def test_requires_valid_token():
    assert client.post("/mpesa/stkpush", json=BODY).status_code == 401
    assert client.post("/mpesa/stkpush", json=BODY, headers={"Authorization": "Bearer bad"}).status_code == 401


def test_rejects_bad_input():
    assert client.post("/mpesa/stkpush", json={**BODY, "phone": "0712345678"}, headers=H).status_code == 422
    assert client.post("/mpesa/stkpush", json={**BODY, "amount": 0}, headers=H).status_code == 422


def test_full_flow_success_and_ownership():
    r = client.post("/mpesa/stkpush", json=BODY, headers=H)
    assert r.status_code == 200 and r.json()["checkout_request_id"] == "ws_CO_1"
    assert client.get("/mpesa/status/ws_CO_1", headers=H).json()["status"] == "PENDING"
    cb = {"Body": {"stkCallback": {"CheckoutRequestID": "ws_CO_1", "ResultCode": 0, "ResultDesc": "ok",
          "CallbackMetadata": {"Item": [{"Name": "Amount", "Value": 500}, {"Name": "MpesaReceiptNumber", "Value": "QKX1ABC23"}]}}}}
    assert client.post("/mpesa/callback/wrong", json=cb).status_code == 404
    assert client.post("/mpesa/callback/topsecret", json=cb).status_code == 200
    s = client.get("/mpesa/status/ws_CO_1", headers=H).json()
    assert s["status"] == "SUCCESS" and s["receipt"] == "QKX1ABC23"
    main.google_id_token.verify_firebase_token = lambda t, r, audience=None: {"sub": "someone-else"}
    assert client.get("/mpesa/status/ws_CO_1", headers=H).status_code == 403


def test_amount_mismatch_fails():
    main.google_id_token.verify_firebase_token = lambda t, r, audience=None: {"sub": "user-1"}
    main.payments["ws_CO_2"] = {"uid": "user-1", "amount": 500, "status": "PENDING", "receipt": None, "message": "", "created": 0, "last_query": 9e12}
    cb = {"Body": {"stkCallback": {"CheckoutRequestID": "ws_CO_2", "ResultCode": 0, "CallbackMetadata": {"Item": [{"Name": "Amount", "Value": 1}]}}}}
    client.post("/mpesa/callback/topsecret", json=cb)
    assert client.get("/mpesa/status/ws_CO_2", headers=H).json()["status"] == "FAILED"


def test_cancelled():
    main.payments["ws_CO_3"] = {"uid": "user-1", "amount": 500, "status": "PENDING", "receipt": None, "message": "", "created": 0, "last_query": 9e12}
    cb = {"Body": {"stkCallback": {"CheckoutRequestID": "ws_CO_3", "ResultCode": 1032, "ResultDesc": "cancelled"}}}
    client.post("/mpesa/callback/topsecret", json=cb)
    assert client.get("/mpesa/status/ws_CO_3", headers=H).json()["status"] == "CANCELLED"
