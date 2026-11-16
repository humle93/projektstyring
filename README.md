# Projektstyring

Samlet projektstyrings-setup: tidsregistrering, projekter og opgaver — på web og Android, med backup til Google Drive.

| Del | Mappe | Stak |
|---|---|---|
| Drive-sync (Fase 1) | `scripts/sync.sh` | rclone → `gdrive:Projektstyring` |
| Backend (Fase 3.2) | `backend/` | FastAPI + SQLite, kører i Termux |
| Web-dashboard (Fase 3.1) | `frontend/` | Tailwind, live projekt-timere, login |
| Android-app (Fase 3.3) | `android/` | SocialPilot — Kotlin + Jetpack Compose |
| CI/CD | `.github/workflows/` | Auto-build af APK/AAB + backend-tests ved push til `main` |

Status og næste skridt: se [ROADMAP.md](ROADMAP.md). Arkitektur og API: se [docs/architecture.md](docs/architecture.md) og [docs/api-spec.md](docs/api-spec.md).

## 1. Drive-synkronisering

```bash
./scripts/sync.sh check          # svarer til: rclone lsd gdrive:Projektstyring
DRY_RUN=1 ./scripts/sync.sh push # se hvad der ville blive kopieret
./scripts/sync.sh push           # lokal -> Drive (overskrevne filer gemmes i Projektstyring_backup/)
./scripts/sync.sh bisync --resync   # første tovejs-sync, derefter uden --resync
```

Stier sættes med `LOCAL_DIR` og `REMOTE`. `pull` spejler Drive, så lokale filer der ikke findes på Drive fjernes — de gemmes dog i `~/.local/state/projektstyring/local-trash/<tid>` og kan gendannes. Log: `~/.local/state/projektstyring/sync-<dato>.log`, seneste status i `last_status.json`. Scriptet venter og prøver igen med stigende pause ved afbrudt forbindelse og forhindrer dobbeltkørsel med en lås.

Automatisk hver time (cron / Termux `crond`):
```
0 * * * * /sti/til/projektstyring/scripts/sync.sh push
```

## 2. Backend + dashboard

```bash
cd backend
pip install -r requirements.txt
./run.sh                 # http://localhost:8000 — dashboardet serveres på samme port
```

**Termux (Android):**
```bash
pkg install python python-pydantic git
pip install fastapi uvicorn
git clone https://github.com/humle93/projektstyring && cd projektstyring/backend && ./run.sh
```
`python-pydantic` fra Termux-repoet undgår at bygge pydantic-core (Rust) på telefonen. Hold den kørende med `termux-wake-lock`.

Første besøg opretter administrator-kontoen; derefter er registrering lukket (`PS_ALLOW_REGISTER=1` åbner den igen).

| Miljøvariabel | Standard | |
|---|---|---|
| `PS_DB_PATH` | `backend/projektstyring.db` | SQLite-fil — læg den UDEN for den synkroniserede mappe; synk kun backup-kopier |
| `PS_SESSION_DAYS` | `30` | Login-varighed |
| `PS_CORS_ORIGINS` | tom | Kommasepareret, hvis frontend hostes andetsteds |
| `HOST` / `PORT` | `0.0.0.0` / `8000` | |

API-dokumentation: `http://<server>:8000/docs`. Tests: `cd backend && python -m pytest`.

## 3. SocialPilot Android-app

Byg lokalt med Android Studio (åbn `android/`), eller hent APK fra GitHub Actions → seneste kørsel → Artifacts. Ved første start angives server-URL, fx `http://192.168.1.10:8000`.

Release-builds signeres med debug-nøglen, indtil en keystore lægges i GitHub Secrets.
