import os, tempfile
os.environ["PS_DB_PATH"] = os.path.join(tempfile.mkdtemp(), "test.db")
from fastapi.testclient import TestClient
from app.main import app


def test_full_flow():
    with TestClient(app) as c:
        assert c.get("/api/auth/setup").json()["needs_setup"] is True
        tok = c.post("/api/auth/register", json={"username": "henning", "password": "hemmelig123"}).json()["token"]
        h = {"Authorization": f"Bearer {tok}"}
        # anden registrering er lukket
        assert c.post("/api/auth/register", json={"username": "x" * 3, "password": "y" * 8}).status_code == 403
        assert c.get("/api/projects").status_code == 401
        p = c.post("/api/projects", json={"name": "Steamgears"}, headers=h).json()
        t = c.post(f"/api/projects/{p['id']}/tasks", json={"title": "Skriv artikel"}, headers=h).json()
        r = c.post("/api/timers/start", json={"project_id": p["id"], "task_id": t["id"]}, headers=h)
        assert r.status_code == 201 and r.json()["active"]["task_title"] == "Skriv artikel"
        assert c.patch(f"/api/tasks/{t['id']}", json={}, headers=h).json()["status"] == "doing"
        assert c.post("/api/timers/stop", headers=h).status_code == 200
        assert c.post("/api/timers/stop", headers=h).status_code == 409
        assert len(c.get("/api/timelogs", headers=h).json()) == 1
        s = c.get("/api/stats/summary", headers=h).json()
        assert s["open_tasks"] == 1
        assert c.post("/api/auth/login", json={"username": "henning", "password": "forkert123"}).status_code == 401
        assert c.post("/api/auth/login", json={"username": "henning", "password": "hemmelig123"}).status_code == 200
        c.post("/api/auth/logout", headers=h)
        assert c.get("/api/auth/me", headers=h).status_code == 401
