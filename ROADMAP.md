# Roadmap — Projektstyring

Baseret på *Projektstyring Masterplan* (Google Drive → Projektstyring). Opdateret 16. nov. 2026.

✅ færdig · 🟡 i gang / klar til test · ⬜ ikke startet

## Fase 1 — Rclone & Google Drive
| | Opgave | Status |
|---|---|---|
| 1.1 | Drive-mappen `Projektstyring` oprettet | ✅ |
| 1.1 | `rclone lsd gdrive:Projektstyring` verificeret på lokal maskine | ⬜ køres af Henning: `./scripts/sync.sh check` |
| 1.2 | Sync-script med stier, logning, fejlhåndtering ved afbrudt forbindelse | ✅ `scripts/sync.sh` |
| 1.2 | Manuel test af envejs (push/pull) og tovejs (bisync) | ⬜ |
| 1.3 | Automatisk kørsel (cron / Termux `crond`) | ⬜ |
| 1.4 | Daglig backup-kopi af SQLite (`sqlite3 db ".backup ..."`) ind i den synkroniserede mappe — aldrig den levende db-fil | ⬜ |

## Fase 2 — GitHub Repository
| | Opgave | Status |
|---|---|---|
| 2.1 | `git init`, README med beskrivelse og opsætningsguide | ✅ |
| 2.2 | Første commit `Init` | ✅ |
| 2.2 | Opret tomt repo `humle93/projektstyring` (offentligt) | ⬜ Henning |
| 2.2 | `git push -u origin main` | ⬜ venter på 2.2 |
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
| Automatiske tests + CI | ✅ |
| Kørsel i Termux (`run.sh`, `termux-wake-lock`) | 🟡 skal testes på telefonen |
| Autostart ved boot (Termux:Boot) | ⬜ |
| Adgang udefra (Tailscale e.l.) uden at åbne porte | ⬜ |
| Simpel migrations-mekanisme til skemaændringer | ⬜ |

### 3.3 SocialPilot Android-app
| Opgave | Status |
|---|---|
| Nyt Kotlin + Jetpack Compose-projekt fra bunden | ✅ |
| Login, projekter, opgaver, timer-bjælke mod FastAPI | 🟡 første build sker i CI efter push |
| GitHub Actions: auto-build af APK/AAB ved push til `main` | 🟡 |
| Første grønne CI-build + installation på telefon | ⬜ |
| Timer-notifikation (foreground service) så den ses uden app åben | ⬜ |
| Offline-kø: start/stop gemmes lokalt og synkes senere | ⬜ |
| Release-signering med rigtig keystore i GitHub Secrets | ⬜ |
| Arkivér gamle `humle93/socialpilot-android` | ⬜ afventer beslutning |

## Næste skridt
1. Henning opretter det tomme repo → Claude pusher.
2. Ret evt. fejl fra første Android-build i Actions.
3. Kør `./scripts/sync.sh check` og første `push` lokalt.
4. Start backend i Termux og installér APK fra Actions.
