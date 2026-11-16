# Roadmap — Projektstyring

Baseret på *Projektstyring Masterplan* (Google Drive → Projektstyring). Opdateret 16. nov. 2026.

✅ færdig · 🟡 i gang / klar til test · ⬜ ikke startet

## Fase 1 — Rclone & Google Drive
| | Opgave | Status |
|---|---|---|
| 1.1 | Drive-mappen `Projektstyring` oprettet | ✅ |
| 1.1 | `rclone lsd gdrive:Projektstyring` verificeret på lokal maskine | ⬜ kræver Hennings rclone-opsætning: `./scripts/sync.sh check` |
| 1.2 | Sync-script med stier, logning, fejlhåndtering ved afbrudt forbindelse | ✅ `scripts/sync.sh` |
| 1.2 | Manuel test af `check`, `push`, `pull`, `DRY_RUN` og fejlsti | 🟡 testet med rclone 1.76 mod lokal remote — Google-auth mangler stadig |
| 1.2 | `pull` gemmer slettede/overskrevne lokale filer i `local-trash/` | ✅ |
| 1.3 | Automatisk kørsel (cron / Termux `crond`) | ⬜ |
| 1.4 | Daglig backup-kopi af SQLite (`sqlite3 db ".backup ..."`) ind i den synkroniserede mappe — aldrig den levende db-fil | ⬜ |

## Fase 2 — GitHub Repository
| | Opgave | Status |
|---|---|---|
| 2.1 | `git init`, README med beskrivelse og opsætningsguide | ✅ |
| 2.2 | Første commit `Init` | ✅ |
| 2.2 | Opret repo [`humle93/projektstyring`](https://github.com/humle93/projektstyring) (offentligt) | ✅ |
| 2.2 | Koden pushet til `main` | ✅ 25 filer, fuld historik |
| 2.3 | Branch-beskyttelse på `main` (kræv grøn CI) | ⬜ |

## Fase 3 — Projektstyrings-dashboard

### 3.1 Frontend
| Opgave | Status |
|---|---|
| Responsivt Tailwind-dashboard | 🟡 første version |
| Live projekt-timere (én aktiv ad gangen, korrigeret for serverens ur) | 🟡 |
| Login, førstegangsopsætning, sessioner | 🟡 |
| Opgavetavle (at gøre / i gang / færdig) og tidslog | 🟡 |
| Rediger/tilføj tidslog manuelt (glemt at starte timer) | ⬜ |
| Rapporter: timer pr. projekt pr. uge/måned, CSV-eksport | ⬜ |
| Tailwind bygget lokalt i stedet for CDN (virker offline) | ⬜ |

### 3.2 Backend
| Opgave | Status |
|---|---|
| FastAPI REST-API (`/docs`) | ✅ |
| SQLite: brugere, sessioner, projekter, opgaver, tidslogs | ✅ |
| Automatiske tests + CI | ✅ grøn i Actions |
| Verificeret med rigtig uvicorn-server (login, projekt, opgave, timer start/stop, 401) | ✅ |
| Kørsel i Termux (`run.sh`, `termux-wake-lock`) | 🟡 skal testes på telefonen |
| Autostart ved boot (Termux:Boot) | ⬜ |
| Adgang udefra (Tailscale e.l.) uden at åbne porte | ⬜ |
| Simpel migrations-mekanisme til skemaændringer | ⬜ |

### 3.3 SocialPilot Android-app
| Opgave | Status |
|---|---|
| Nyt Kotlin + Jetpack Compose-projekt fra bunden | ✅ |
| Login, projekter, opgaver, timer-bjælke mod FastAPI | 🟡 klar til installation |
| GitHub Actions: auto-build af APK/AAB ved push til `main` | ✅ [første grønne build](https://github.com/humle93/projektstyring/actions) |
| Installation på telefon og test mod Termux-server | ⬜ |
| Timer-notifikation (foreground service) så den ses uden app åben | ⬜ |
| Offline-kø: start/stop gemmes lokalt og synkes senere | ⬜ |
| Release-signering med rigtig keystore i GitHub Secrets | ⬜ |
| Arkivér gamle `humle93/socialpilot-android` | ⬜ afventer beslutning |

## Næste skridt

1. **Lokal maskine:** GitHub-historikken er oprettet herfra og afviger fra den lokale. Kør ét af følgende
   for at synkronisere (filerne er identiske):
   ```bash
   git remote add origin https://github.com/humle93/projektstyring.git
   git fetch origin && git reset --hard origin/main     # behold GitHub-historikken
   # eller: git push -u origin main --force            # behold din lokale historik
   ```
2. `rclone config reconnect gdrive:` og derefter `./scripts/sync.sh check` + første `push`.
3. Start backend i Termux (`backend/run.sh`) og installér APK'en fra Actions → Artifacts.
4. Slå branch-beskyttelse til på `main`, når den lokale historik er afklaret.
