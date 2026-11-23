from datetime import datetime, timedelta
from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session

from database import get_db
import models
import security

router = APIRouter(prefix="/api/auth", tags=["Auth"])


class RefreshRequest(BaseModel):
    refresh_token: str
    device_id: Optional[str] = None


class LogoutRequest(BaseModel):
    refresh_token: Optional[str] = None


def _now() -> datetime:
    return datetime.utcnow()


def _revoke_all_sessions(db: Session, login_id: int) -> None:
    db.query(models.UserSession).filter(
        models.UserSession.LOGIN_ID == login_id,
        models.UserSession.REVOKED_AT.is_(None),
    ).update({models.UserSession.REVOKED_AT: _now()}, synchronize_session=False)
    db.commit()


def create_session(
    db: Session,
    user: models.LoginMaster,
    device_id: Optional[str] = None,
    user_agent: Optional[str] = None,
    ip_hash: Optional[str] = None,
    rotated_from: Optional[int] = None,
):
    """Create a new refresh session row and return (access_token, refresh_token)."""
    refresh_token = security.generate_refresh_token()

    session = models.UserSession(
        LOGIN_ID=user.ID,
        REFRESH_TOKEN_HASH=security.hash_refresh_token(refresh_token),
        DEVICE_ID=(device_id or "")[:128] or None,
        USER_AGENT=(user_agent or "")[:256] or None,
        IP_HASH=ip_hash,
        CREATED_AT=_now(),
        LAST_USED_AT=_now(),
        EXPIRES_AT=_now() + timedelta(days=security.REFRESH_TOKEN_EXPIRE_DAYS),
        ROTATED_FROM=rotated_from,
    )
    db.add(session)
    db.commit()
    db.refresh(session)

    access_token = security.create_access_token(
        {
            "sub": user.USERNAMES,
            "uid": user.ID,
            "code": user.CODE,
        }
    )
    return access_token, refresh_token


def session_response(access_token: str, refresh_token: str, user: models.LoginMaster) -> dict:
    user_data = {c.name: getattr(user, c.name) for c in user.__table__.columns}
    user_data.pop("PASSWORDS", None)
    return {
        "status": "success",
        "Status": "Success",
        "access_token": access_token,
        "Token": access_token,
        "refresh_token": refresh_token,
        "RefreshToken": refresh_token,
        "token_type": "Bearer",
        "expires_in": security.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
        "ExpiresIn": security.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
        "LOGIN_MASTERs": [user_data],
    }


def resolve_user_from_header(authorization: Optional[str], db: Session) -> Optional[models.LoginMaster]:
    """Return the caller for a Bearer access token, or None when it is missing/invalid."""
    if not authorization or not authorization.lower().startswith("bearer "):
        return None

    token = authorization.split(" ", 1)[1].strip()
    try:
        payload = security.decode_access_token(token)
    except Exception:
        return None

    return db.query(models.LoginMaster).filter(
        models.LoginMaster.ID == payload.get("uid"),
        models.LoginMaster.MASTER_STATUS == "Active",
    ).first()


def get_current_user(
    authorization: Optional[str] = Header(default=None),
    db: Session = Depends(get_db),
) -> models.LoginMaster:
    """Resolve the caller from a Bearer access token."""
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Not authenticated")

    user = resolve_user_from_header(authorization, db)
    if not user:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    return user


@router.post("/refresh")
def refresh_session(request: RefreshRequest, db: Session = Depends(get_db)):
    if not request.refresh_token:
        raise HTTPException(status_code=401, detail="Missing refresh token")

    token_hash = security.hash_refresh_token(request.refresh_token)
    session = db.query(models.UserSession).filter(
        models.UserSession.REFRESH_TOKEN_HASH == token_hash
    ).with_for_update().first()

    if not session:
        raise HTTPException(status_code=401, detail="Invalid refresh token")

    # A revoked token being presented again means it was replayed/stolen.
    if session.REVOKED_AT is not None:
        _revoke_all_sessions(db, session.LOGIN_ID)
        raise HTTPException(status_code=401, detail="Session revoked")

    now = _now()
    if session.EXPIRES_AT and session.EXPIRES_AT < now:
        session.REVOKED_AT = now
        db.commit()
        raise HTTPException(status_code=401, detail="Session expired")

    if request.device_id and session.DEVICE_ID and request.device_id != session.DEVICE_ID:
        session.REVOKED_AT = now
        db.commit()
        raise HTTPException(status_code=401, detail="Device mismatch")

    user = db.query(models.LoginMaster).filter(
        models.LoginMaster.ID == session.LOGIN_ID,
        models.LoginMaster.MASTER_STATUS == "Active",
    ).first()
    if not user:
        session.REVOKED_AT = now
        db.commit()
        raise HTTPException(status_code=401, detail="Account is not active")

    # Rotate: revoke the used token and issue a fresh pair.
    session.REVOKED_AT = now
    session.LAST_USED_AT = now
    db.commit()

    access_token, refresh_token = create_session(
        db,
        user,
        device_id=session.DEVICE_ID,
        user_agent=session.USER_AGENT,
        ip_hash=session.IP_HASH,
        rotated_from=session.ID,
    )
    return session_response(access_token, refresh_token, user)


@router.post("/logout")
def logout(request: LogoutRequest, db: Session = Depends(get_db)):
    if request.refresh_token:
        token_hash = security.hash_refresh_token(request.refresh_token)
        session = db.query(models.UserSession).filter(
            models.UserSession.REFRESH_TOKEN_HASH == token_hash
        ).first()
        if session and session.REVOKED_AT is None:
            session.REVOKED_AT = _now()
            db.commit()

    # Always report success so callers cannot probe token validity.
    return {"status": "logged_out", "Status": "Success"}


@router.post("/logout-all")
def logout_all(
    user: models.LoginMaster = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    _revoke_all_sessions(db, user.ID)
    return {"status": "logged_out", "Status": "Success"}


@router.get("/me")
def me(user: models.LoginMaster = Depends(get_current_user)):
    user_data = {c.name: getattr(user, c.name) for c in user.__table__.columns}
    user_data.pop("PASSWORDS", None)
    return {"status": "success", "Status": "Success", "LOGIN_MASTERs": [user_data]}
