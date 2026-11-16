"""Login og sessioner. PBKDF2 fra stdlib — ingen native bcrypt-build i Termux."""
import hashlib
import hmac
import os
import secrets
from datetime import datetime, timedelta, timezone

from fastapi import Depends, Header, HTTPException

from .db import get_conn

SESSION_DAYS = int(os.environ.get("PS_SESSION_DAYS", "30"))
ITERATIONS = 260_000


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, ITERATIONS)
    return f"pbkdf2${ITERATIONS}${salt.hex()}${dk.hex()}"


def verify_password(password: str, stored: str) -> bool:
    try:
        _, iters, salt_hex, hash_hex = stored.split("$")
    except ValueError:
        return False
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt_hex), int(iters))
    return hmac.compare_digest(dk.hex(), hash_hex)


def _now() -> datetime:
    return datetime.now(timezone.utc)


def create_session(user_id: int) -> str:
    token = secrets.token_urlsafe(32)
    expires = (_now() + timedelta(days=SESSION_DAYS)).isoformat()
    with get_conn() as conn:
        conn.execute("DELETE FROM sessions WHERE expires_at < ?", (_now().isoformat(),))
        conn.execute("INSERT INTO sessions(token, user_id, expires_at) VALUES (?,?,?)", (token, user_id, expires))
    return token


def delete_session(token: str) -> None:
    with get_conn() as conn:
        conn.execute("DELETE FROM sessions WHERE token = ?", (token,))


def current_user(authorization: str = Header(default="")) -> dict:
    if not authorization.lower().startswith("bearer "):
        raise HTTPException(401, "Mangler login")
    token = authorization[7:].strip()
    with get_conn() as conn:
        row = conn.execute(
            "SELECT u.id, u.username, s.expires_at FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ?",
            (token,),
        ).fetchone()
    if not row or row["expires_at"] < _now().isoformat():
        raise HTTPException(401, "Session udløbet — log ind igen")
    return {"id": row["id"], "username": row["username"], "token": token}


CurrentUser = Depends(current_user)
