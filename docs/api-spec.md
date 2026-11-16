# API-specifikation — Projektstyring

REST-API'et er bygget med FastAPI og kører som standard på `http://<server>:8000`.
Interaktiv dokumentation: `/docs` (Swagger UI) og `/redoc`. OpenAPI-skema: `/openapi.json`.

## Konventioner

- **Format:** JSON, `Content-Type: application/json`.
- **Auth:** `Authorization: Bearer <session-token>` på alle endpoints undtagen `health`, `setup`,
  `register` og `login`.
- **Tid:** ISO 8601 i UTC, fx `2026-11-16T12:04:32+00:00`.
- **Fejl:** `{"detail": "beskrivelse"}` med passende HTTP-status.
- **Ejerskab:** Alle ressourcer er scoped til den indloggede bruger. Fremmede id'er giver `404`.

| Status | Betydning |
|---|---|
| 200 | OK |
| 201 | Oprettet |
| 204 | Slettet (intet indhold) |
| 400 | Ugyldig kombination (fx opgave uden for projektet) |
| 401 | Mangler/udløbet session |
| 403 | Registrering lukket |
| 404 | Findes ikke / ikke din |
| 409 | Konflikt (fx ingen aktiv timer, brugernavn optaget) |

## System

### `GET /api/health`
Uden auth. → `{"ok": true, "time": "..."}`

## Auth

### `GET /api/auth/setup`
Uden auth. → `{"needs_setup": true}` — `true` når der endnu ikke findes brugere.

### `POST /api/auth/register` → 201
```json
{"username": "henning", "password": "min 8 tegn"}
```
→ `{"token": "...", "username": "henning"}`. Kun tilladt ved første bruger eller med `PS_ALLOW_REGISTER=1`;
ellers `403`.

### `POST /api/auth/login`
```json
{"username": "henning", "password": "..."}
```
→ `{"token": "...", "username": "henning"}`. Fejl → `401`.

### `POST /api/auth/logout` → 204
Sletter den aktuelle session.

### `GET /api/auth/me`
→ `{"id": 1, "username": "henning"}`

## Projekter

### `GET /api/projects?include_archived=false`
```json
[{
  "id": 1, "owner_id": 1, "name": "Steamgears", "description": "",
  "color": "#6366f1", "archived": 0, "created_at": "...",
  "task_count": 4, "done_count": 1, "total_seconds": 5400
}]
```

### `POST /api/projects` → 201
`{"name": "Steamgears", "description": "", "color": "#6366f1"}` → projektobjekt.

### `PATCH /api/projects/{project_id}`
Delvis opdatering: `name`, `description`, `color`, `archived` (bool). → projektobjekt.

### `DELETE /api/projects/{project_id}` → 204
Sletter projektet med tilhørende opgaver og tidslogs (foreign keys).

## Opgaver

### `GET /api/projects/{project_id}/tasks`
→ liste af opgaver, sorteret `doing` → `todo` → `done`.
```json
[{"id": 7, "project_id": 1, "title": "Skriv artikel", "status": "todo",
  "due_date": "2026-11-20", "created_at": "...", "updated_at": "..."}]
```

### `POST /api/projects/{project_id}/tasks` → 201
`{"title": "Skriv artikel", "status": "todo", "due_date": "2026-11-20"}` → opgaveobjekt.

### `PATCH /api/tasks/{task_id}`
`title`, `status` (`todo` | `doing` | `done`), `due_date`. → opgaveobjekt.

### `DELETE /api/tasks/{task_id}` → 204

## Timere og tidslog

### `GET /api/timers/active`
```json
{"active": {"id": 3, "project_id": 1, "project_name": "Steamgears",
  "task_id": 7, "task_title": "Skriv artikel", "started_at": "...", "stopped_at": null},
 "server_time": "2026-11-16T12:04:32+00:00"}
```
`active` er `null`, når ingen timer kører. `server_time` bruges til at korrigere klientens ur.

### `POST /api/timers/start` → 201
```json
{"project_id": 1, "task_id": 7, "note": ""}
```
Stopper automatisk en eventuel forrige timer. Flytter opgaven fra `todo` til `doing`.
→ samme svar som `GET /api/timers/active`. Opgave fra andet projekt → `400`.

### `POST /api/timers/stop`
→ `{"stopped": {...}}`. Ingen aktiv timer → `409`.

### `GET /api/timelogs?project_id=1&days=7`
→ liste, nyeste først (max 500). Hvert element har `project_name`, `project_color`, `task_title`
og `seconds` (beregnet varighed, også for en kørende timer).

### `DELETE /api/timelogs/{log_id}` → 204

## Statistik

### `GET /api/stats/summary`
```json
{"today_seconds": 3600, "week_seconds": 12600, "open_tasks": 3}
```
`today` er fra UTC-midnat, `week` fra mandag. Kørende timere indgår med tiden til "nu".

## Klientbrug

```bash
TOKEN=$(curl -s -X POST localhost:8000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"henning","password":"..."}' | jq -r .token)

curl -s localhost:8000/api/projects -H "Authorization: Bearer $TOKEN"
```
