"""Projektstyring API — FastAPI + SQLite. Kører på PC, server eller Termux."""
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Literal, Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from .auth import CurrentUser, create_session, delete_session, hash_password, verify_password
from .db import get_conn, init_db

app = FastAPI(title="Projektstyring API", version="0.1.0")

origins = [o for o in os.environ.get("PS_CORS_ORIGINS", "").split(",") if o]
if origins:
    app.add_middleware(CORSMiddleware, allow_origins=origins, allow_methods=["*"], allow_headers=["*"])


@app.on_event("startup")
def _startup() -> None:
    init_db()


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def rows(cur) -> list[dict]:
    return [dict(r) for r in cur.fetchall()]


# ---------- Modeller ----------
class Credentials(BaseModel):
    username: str = Field(min_length=3, max_length=50)
    password: str = Field(min_length=8, max_length=200)


class ProjectIn(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    description: str = ""
    color: str = "#6366f1"


class ProjectPatch(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    color: Optional[str] = None
    archived: Optional[bool] = None


class TaskIn(BaseModel):
    title: str = Field(min_length=1, max_length=300)
    status: Literal["todo", "doing", "done"] = "todo"
    due_date: Optional[str] = None


class TaskPatch(BaseModel):
    title: Optional[str] = None
    status: Optional[Literal["todo", "doing", "done"]] = None
    due_date: Optional[str] = None


class TimerStart(BaseModel):
    project_id: int
    task_id: Optional[int] = None
    note: str = ""


# ---------- Hjælpere ----------
def own_project(conn, project_id: int, user_id: int) -> dict:
    row = conn.execute("SELECT * FROM projects WHERE id=? AND owner_id=?", (project_id, user_id)).fetchone()
    if not row:
        raise HTTPException(404, "Projekt ikke fundet")
    return dict(row)


def own_task(conn, task_id: int, user_id: int) -> dict:
    row = conn.execute(
        "SELECT t.* FROM tasks t JOIN projects p ON p.id=t.project_id WHERE t.id=? AND p.owner_id=?",
        (task_id, user_id),
    ).fetchone()
    if not row:
        raise HTTPException(404, "Opgave ikke fundet")
    return dict(row)


# ---------- System ----------
@app.get("/api/health")
def health():
    return {"ok": True, "time": now_iso()}


# ---------- Auth ----------
@app.post("/api/auth/register", status_code=201)
def register(body: Credentials):
    with get_conn() as conn:
        has_users = conn.execute("SELECT 1 FROM users LIMIT 1").fetchone()
        if has_users and os.environ.get("PS_ALLOW_REGISTER") != "1":
            raise HTTPException(403, "Registrering er lukket")
        try:
            cur = conn.execute("INSERT INTO users(username, password_hash) VALUES (?,?)",
                               (body.username, hash_password(body.password)))
        except Exception:
            raise HTTPException(409, "Brugernavn findes allerede")
        uid = cur.lastrowid
    return {"token": create_session(uid), "username": body.username}


@app.post("/api/auth/login")
def login(body: Credentials):
    with get_conn() as conn:
        row = conn.execute("SELECT id, password_hash FROM users WHERE username=?", (body.username,)).fetchone()
    if not row or not verify_password(body.password, row["password_hash"]):
        raise HTTPException(401, "Forkert brugernavn eller adgangskode")
    return {"token": create_session(row["id"]), "username": body.username}


@app.get("/api/auth/setup")
def setup_needed():
    with get_conn() as conn:
        return {"needs_setup": conn.execute("SELECT 1 FROM users LIMIT 1").fetchone() is None}


@app.post("/api/auth/logout", status_code=204)
def logout(user=CurrentUser):
    delete_session(user["token"])


@app.get("/api/auth/me")
def me(user=CurrentUser):
    return {"id": user["id"], "username": user["username"]}


# ---------- Projekter ----------
@app.get("/api/projects")
def list_projects(include_archived: bool = False, user=CurrentUser):
    q = """SELECT p.*,
             (SELECT COUNT(*) FROM tasks t WHERE t.project_id=p.id) AS task_count,
             (SELECT COUNT(*) FROM tasks t WHERE t.project_id=p.id AND t.status='done') AS done_count,
             (SELECT COALESCE(SUM(strftime('%s', COALESCE(l.stopped_at, ?)) - strftime('%s', l.started_at)),0)
                FROM time_logs l WHERE l.project_id=p.id) AS total_seconds
           FROM projects p WHERE p.owner_id=?"""
    if not include_archived:
        q += " AND p.archived=0"
    with get_conn() as conn:
        return rows(conn.execute(q + " ORDER BY p.created_at DESC", (now_iso(), user["id"])))


@app.post("/api/projects", status_code=201)
def create_project(body: ProjectIn, user=CurrentUser):
    with get_conn() as conn:
        cur = conn.execute("INSERT INTO projects(owner_id,name,description,color) VALUES (?,?,?,?)",
                           (user["id"], body.name, body.description, body.color))
        return own_project(conn, cur.lastrowid, user["id"])


@app.patch("/api/projects/{project_id}")
def update_project(project_id: int, body: ProjectPatch, user=CurrentUser):
    data = body.model_dump(exclude_none=True)
    with get_conn() as conn:
        own_project(conn, project_id, user["id"])
        if data:
            sets = ", ".join(f"{k}=?" for k in data)
            conn.execute(f"UPDATE projects SET {sets} WHERE id=?", (*data.values(), project_id))
        return own_project(conn, project_id, user["id"])


@app.delete("/api/projects/{project_id}", status_code=204)
def delete_project(project_id: int, user=CurrentUser):
    with get_conn() as conn:
        own_project(conn, project_id, user["id"])
        conn.execute("DELETE FROM projects WHERE id=?", (project_id,))


# ---------- Opgaver ----------
@app.get("/api/projects/{project_id}/tasks")
def list_tasks(project_id: int, user=CurrentUser):
    with get_conn() as conn:
        own_project(conn, project_id, user["id"])
        return rows(conn.execute(
            "SELECT * FROM tasks WHERE project_id=? ORDER BY CASE status WHEN 'doing' THEN 0 WHEN 'todo' THEN 1 ELSE 2 END, created_at",
            (project_id,)))


@app.post("/api/projects/{project_id}/tasks", status_code=201)
def create_task(project_id: int, body: TaskIn, user=CurrentUser):
    with get_conn() as conn:
        own_project(conn, project_id, user["id"])
        cur = conn.execute("INSERT INTO tasks(project_id,title,status,due_date) VALUES (?,?,?,?)",
                           (project_id, body.title, body.status, body.due_date))
        return own_task(conn, cur.lastrowid, user["id"])


@app.patch("/api/tasks/{task_id}")
def update_task(task_id: int, body: TaskPatch, user=CurrentUser):
    data = body.model_dump(exclude_none=True)
    with get_conn() as conn:
        own_task(conn, task_id, user["id"])
        if data:
            sets = ", ".join(f"{k}=?" for k in data)
            conn.execute(f"UPDATE tasks SET {sets}, updated_at=? WHERE id=?", (*data.values(), now_iso(), task_id))
        return own_task(conn, task_id, user["id"])


@app.delete("/api/tasks/{task_id}", status_code=204)
def delete_task(task_id: int, user=CurrentUser):
    with get_conn() as conn:
        own_task(conn, task_id, user["id"])
        conn.execute("DELETE FROM tasks WHERE id=?", (task_id,))


# ---------- Timere ----------
def _active(conn, user_id: int):
    row = conn.execute(
        """SELECT l.*, p.name AS project_name, p.color AS project_color, t.title AS task_title
           FROM time_logs l JOIN projects p ON p.id=l.project_id LEFT JOIN tasks t ON t.id=l.task_id
           WHERE l.user_id=? AND l.stopped_at IS NULL""", (user_id,)).fetchone()
    return dict(row) if row else None


@app.get("/api/timers/active")
def active_timer(user=CurrentUser):
    with get_conn() as conn:
        return {"active": _active(conn, user["id"]), "server_time": now_iso()}


@app.post("/api/timers/start", status_code=201)
def start_timer(body: TimerStart, user=CurrentUser):
    with get_conn() as conn:
        own_project(conn, body.project_id, user["id"])
        if body.task_id is not None:
            task = own_task(conn, body.task_id, user["id"])
            if task["project_id"] != body.project_id:
                raise HTTPException(400, "Opgaven hører ikke til projektet")
        # Kun én aktiv timer ad gangen — stop den forrige automatisk
        conn.execute("UPDATE time_logs SET stopped_at=? WHERE user_id=? AND stopped_at IS NULL", (now_iso(), user["id"]))
        conn.execute("INSERT INTO time_logs(user_id,project_id,task_id,started_at,note) VALUES (?,?,?,?,?)",
                     (user["id"], body.project_id, body.task_id, now_iso(), body.note))
        if body.task_id is not None:
            conn.execute("UPDATE tasks SET status='doing', updated_at=? WHERE id=? AND status='todo'", (now_iso(), body.task_id))
        return {"active": _active(conn, user["id"]), "server_time": now_iso()}


@app.post("/api/timers/stop")
def stop_timer(user=CurrentUser):
    with get_conn() as conn:
        active = _active(conn, user["id"])
        if not active:
            raise HTTPException(409, "Ingen aktiv timer")
        conn.execute("UPDATE time_logs SET stopped_at=? WHERE id=?", (now_iso(), active["id"]))
        log = dict(conn.execute("SELECT * FROM time_logs WHERE id=?", (active["id"],)).fetchone())
        return {"stopped": log}


@app.get("/api/timelogs")
def list_timelogs(project_id: Optional[int] = None, days: int = 7, user=CurrentUser):
    since = (datetime.now(timezone.utc) - timedelta(days=days)).isoformat(timespec="seconds")
    q = """SELECT l.*, p.name AS project_name, p.color AS project_color, t.title AS task_title,
             strftime('%s', COALESCE(l.stopped_at, ?)) - strftime('%s', l.started_at) AS seconds
           FROM time_logs l JOIN projects p ON p.id=l.project_id LEFT JOIN tasks t ON t.id=l.task_id
           WHERE l.user_id=? AND l.started_at >= ?"""
    params: list = [now_iso(), user["id"], since]
    if project_id is not None:
        q += " AND l.project_id=?"
        params.append(project_id)
    with get_conn() as conn:
        return rows(conn.execute(q + " ORDER BY l.started_at DESC LIMIT 500", params))


@app.delete("/api/timelogs/{log_id}", status_code=204)
def delete_timelog(log_id: int, user=CurrentUser):
    with get_conn() as conn:
        cur = conn.execute("DELETE FROM time_logs WHERE id=? AND user_id=?", (log_id, user["id"]))
        if cur.rowcount == 0:
            raise HTTPException(404, "Tidslog ikke fundet")


@app.get("/api/stats/summary")
def summary(user=CurrentUser):
    now = datetime.now(timezone.utc)
    today = now.replace(hour=0, minute=0, second=0, microsecond=0)
    week = today - timedelta(days=today.weekday())
    q = """SELECT COALESCE(SUM(strftime('%s', COALESCE(stopped_at, ?)) - strftime('%s', started_at)),0)
           FROM time_logs WHERE user_id=? AND started_at >= ?"""
    with get_conn() as conn:
        t = conn.execute(q, (now_iso(), user["id"], today.isoformat())).fetchone()[0]
        w = conn.execute(q, (now_iso(), user["id"], week.isoformat())).fetchone()[0]
        open_tasks = conn.execute(
            "SELECT COUNT(*) FROM tasks t JOIN projects p ON p.id=t.project_id WHERE p.owner_id=? AND t.status!='done'",
            (user["id"],)).fetchone()[0]
    return {"today_seconds": t, "week_seconds": w, "open_tasks": open_tasks}


# ---------- Frontend ----------
FRONTEND = Path(__file__).resolve().parents[2] / "frontend"
if FRONTEND.is_dir():
    app.mount("/", StaticFiles(directory=FRONTEND, html=True), name="frontend")
