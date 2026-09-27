# drops. native (Kotlin Multiplatform) + sync server

The React Native app in `app/` and `src/` is being replaced by native apps.
The new code lives in Gradle modules at the repository root:

| Module | What | Targets |
| --- | --- | --- |
| `core/` | Models, domain logic (roast freshness, care plan, dial-in advice, palate profile, map projections), sync protocol | JVM, iOS |
| `data/` | SQLite (SQLDelight), `DropsRepository`, optional `SyncClient`, sample data | Android, JVM (tests), iOS (`DropsKit.framework`) |
| `androidApp/` | Jetpack Compose app (tabs: Heute, Bohnen, Karte with Anbauregion / Gekauft bei / Entdecken, Setup) | Android 8+ (API 26) |
| `server/` | Ktor sync server with optional accounts, SQLite | JVM 21, Docker |
| `iosApp/` | Placeholder for the SwiftUI app | iOS |

## Local-first, account optional

The app works fully offline without an account. Signing in (Setup → Konto) only
adds backup and sync between devices. On sign-in all local records are uploaded;
after that every change is pushed a few seconds later. Conflicts resolve with
last-write-wins on `updatedAt`. Deletions are synced as tombstones.

## Run locally

```bash
./gradlew :core:jvmTest :server:test :data:jvmTest   # all unit tests
./gradlew :androidApp:installDebug                    # needs Android SDK (local.properties: sdk.dir=…)
DATABASE_PATH=./drops.db BEHIND_PROXY=false ./gradlew :server:run
```

From the Android emulator the local server is reachable at `http://10.0.2.2:8080`
(plain HTTP is only accepted for localhost/emulator addresses).

## Server configuration

| Env | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `DATABASE_PATH` | `/data/drops.db` | SQLite file; mount `/data` as a persistent volume |
| `ALLOW_SIGNUP` | `true` | `false` closes registration (existing accounts keep working) |
| `AUTH_RATE_LIMIT_PER_MINUTE` | `10` | Login/register attempts per client IP |
| `BEHIND_PROXY` | `true` | Trust `X-Forwarded-For` from Coolify's proxy for rate limiting |
| `PUBLIC_URL` | request host | Base URL printed into roaster QR codes, e.g. `https://sync.example.com` |
| `ANDROID_CERT_SHA256` | unset | Comma-separated SHA-256 fingerprints of the app signing key (Play Console → App integrity); served as `/.well-known/assetlinks.json` so `/r/` links open the app directly |
| `SMTP_HOST`, `SMTP_PORT` (587), `SMTP_USER`, `SMTP_PASSWORD`, `MAIL_FROM` | unset | Mail server for waitlist confirmations (double opt-in). Without it sign-ups stay unconfirmed. |
| `PLAY_SERVICE_ACCOUNT_JSON` | unset | Same service account JSON as in GitHub; lets the server confirm purchases with Google (`POST /api/purchases/verify`). In the Play Console the account needs *View financial data*. |
| `PLAY_PACKAGE_NAME` | `de.birneklub.drops` | App id for purchase checks |
| `STATS_TOKEN` | unset | At least 16 characters; enables `GET /api/stats/report` with `Authorization: Bearer <token>` |

Endpoints: `GET /healthz`, `POST /api/auth/register`, `POST /api/auth/login`,
`POST /api/auth/logout`, `GET /api/me`, `DELETE /api/me` (deletes account and
all server data), `POST /api/sync`, `POST /api/stats/events` (anonymous beta
counts, no login), `GET /api/stats/report` (needs `STATS_TOKEN`).

## Website and waitlist

The server image also serves the marketing site from
`server/src/main/resources/website/` (landing page, `/impressum`,
`/datenschutz`). In Coolify add the main domain (e.g. `drops-app.de`) to the
same resource as the sync subdomain; both show the site, the app talks to
`/api`. Fill in the marked placeholders in the two legal pages before going
live.

The waitlist uses double opt-in: `POST /api/waitlist` sends a confirmation
mail, only confirmed addresses count, unconfirmed ones are deleted after seven
days and every mail has a removal link. `?ref=instagram` on any link to the
site is stored as the source. Export confirmed addresses with
`curl -H "Authorization: Bearer $STATS_TOKEN" https://<server>/api/waitlist/export`;
the report shows `waitlistConfirmed`.

## Beta: what the app measures and sells

The 90-day beta is judged by a handful of numbers. The app can deliver them
without tracking people:

- **Anonymous statistics, opt-in.** Asked on the last onboarding step and
  switchable in Setup. When on, the app counts per day: app used, setup done,
  bean added, shot logged, care task done, reorder or supply link opened,
  founding-member offer shown and bought. Counts go to the configured sync
  server under a random install id that is not linked to the account. Opting
  out deletes the id and anything not yet sent. Mention this in the privacy
  policy (legal basis: consent, Art. 6(1)(a) GDPR).
- **Key-result report.** `curl -H "Authorization: Bearer $STATS_TOKEN"
  https://<server>/api/stats/report` returns setup completion (KR1), day-30
  retention for installs first seen at least 35 days ago (KR2), shots per
  active week and care tasks per active install (KR3), founding-member share
  of the last 30 days' active installs (KR4), link click share (KR5) and weekly
  rows, plus adopted roaster cards (`roasterRecipes`, KR6) and confirmed
  waitlist sign-ups.
- **Founding Member.** One-time in-app product via Google Play Billing. Create
  an in-app product with id `founding_member` in the Play Console (price
  29–39 €) and activate it; until then the Setup tab shows the offer without a
  buy button. Purchases are acknowledged in the app and confirmed by the
  server with Google when `PLAY_SERVICE_ACCOUNT_JSON` is set there; refunded
  or invalid purchases lose the status. Without it the app trusts Play's
  local result. The report shows `verifiedPurchases`. The card promises that
  later Pro features stay unlocked for founding members.
- **Reminders.** A WorkManager job checks twice a day and notifies once per
  cycle about overdue care and bags with three shots or fewer left. Links for
  consumables and reordering open a shop search or the bean's saved shop page.
  Partner (affiliate) links come from two GitHub variables used at build time:
  `REORDER_LINK_TEMPLATE` for beans without a saved shop page and
  `SUPPLY_LINK_TEMPLATE` for consumables. `{q}` is the URL-encoded search
  text, `{qq}` the same encoded twice for deep links that wrap a destination
  (AWIN `ued=`). Examples: `https://www.amazon.de/s?k={q}&tag=<partner-tag>`,
  `https://www.awin1.com/cread.php?awinmid=<mid>&awinaffid=<id>&ued=<encoded shop search URL ending in {qq}>`.
  As soon as a template is set, those links carry an "Anzeige" label; saved
  shop pages stay unlabelled.
- **Roaster QR cards (KR6).** Roasters open `https://<server>/roaster`, enter
  a starting recipe and print the card. The QR code holds the whole recipe in
  the link `https://<server>/r/<card>`; nothing is stored on the server.
  With the app installed (and `ANDROID_CERT_SHA256` set) the link opens a
  preview in drops., which adds the bean and a "Start von <Rösterei>" recipe
  without a grind setting. Without the app the page shows the recipe and a
  Play Store link. The report counts adopted cards as `roasterRecipes`.
  The app claims the host of `DROPS_SYNC_URL` at build time.
- **Backups.** Export/import as JSON in Setup; Android Auto Backup covers the
  database. **Beanconqueror**: the export ZIP can be imported in onboarding or
  Setup (beans and espresso brews).

## CI/CD

| Workflow | Trigger | Does |
| --- | --- | --- |
| `ci.yml` | every PR and push to `main` | core + server tests, data tests, debug APK artifact |
| `android-release.yml` | push to `main` touching app code, tags `v*` | tests → signed release APK + AAB (artifacts) → Play internal track (optional) → GitHub Release on tags |

### One-time setup

**Coolify** (builds and deploys the server itself – no GitHub secrets needed)

1. New resource → *Private Repository (with GitHub App)* → `robinrehbein/drops.`, branch `main`,
   build pack *Dockerfile*, base directory `/`, Dockerfile `/server/Dockerfile`, port `8080`.
   The Dockerfile builds on the host's own architecture (the Coolify host is arm64).
2. Persistent storage: a volume mounted at `/data`.
3. Health check path `/healthz`, domain `https://drops.robinrehbein.de`.
4. Auto deploy on, *Watch Paths* `server/**`, `core/**`, `gradle/**`, `*.gradle.kts`, `gradle.properties`,
   so only server-relevant merges to `main` trigger a build.

**GitHub → Settings → Secrets and variables → Actions** (environment `production`)

| Name | Kind | For |
| --- | --- | --- |
| `SYNC_URL` | variable, optional | Default server URL in the app (falls back to `https://drops.robinrehbein.de`) |
| `ANDROID_KEYSTORE_BASE64` | secret | `base64 -w0 release.jks` |
| `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | secrets | Signing |
| `PLAY_SERVICE_ACCOUNT_JSON` | secret, optional | Uploads the AAB to the Play internal track as a draft |

Without a keystore the release build is unsigned.

### Google Play

The native app is a new Play listing, **drops.** with the id `de.birneklub.drops`.
The old Expo test app (`de.birneklub.drop`, a typo) is retired: unpublish its
test tracks once testers have the new app.

**Upload key (once, on your own machine):**

```bash
scripts/android-upload-key.sh
```

It creates `~/.drops-signing/drops-upload.jks` with a random password and, if
the GitHub CLI is logged in (`gh auth login`), stores `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` in
the `production` environment. Otherwise it prints them. Put the keystore file
and password into your password manager.

**Service account for automatic uploads (`PLAY_SERVICE_ACCOUNT_JSON`):**

1. [Google Cloud Console](https://console.cloud.google.com/) → create or pick a
   project → *APIs & Services → Library* → enable **Google Play Android
   Developer API**.
2. *IAM & Admin → Service Accounts* → **Create service account** (e.g.
   `drops-ci`), no roles needed → open it → *Keys → Add key → Create new key →
   JSON*. The file downloads once.
3. [Play Console](https://play.google.com/console) → *Users and permissions* →
   **Invite new users** → the service account's e-mail → *App permissions* →
   add **drops.** with *Release apps to testing tracks* (and *Release to
   production* if tags should publish) → invite.
4. GitHub → *Settings → Environments → production* → secret
   `PLAY_SERVICE_ACCOUNT_JSON` = the full content of the JSON file
   (`gh secret set PLAY_SERVICE_ACCOUNT_JSON --env production < key.json`).
   Then delete the downloaded file.

**First release, by hand:** Play's API cannot create an app. In the Play
Console create the app **drops.**, then upload the first AAB (from the
*Android release* workflow artifacts) to *Internal testing* and roll it out.
This also enrols Play App Signing. After that the pipeline takes over:

| Push | Play track | Default status |
| --- | --- | --- |
| `dev` | Internal testing | completed (live for the team) |
| `main` | Closed testing (alpha) | completed (live for beta testers) |
| tag `v1.2.3` | Production | draft; set variable `PLAY_PRODUCTION_STATUS=completed` to roll out automatically |

Release notes come from `distribution/whatsnew/`. The version code is the
workflow run number, so every upload is higher than the one before.

For `ANDROID_CERT_SHA256` on the server take the *App signing key* SHA-256
from Play Console → *Test and release → App integrity*, plus the upload key's
(printed by the script) for builds installed outside Play, comma-separated.

## Open points

- Affiliate IDs for supply and reorder links, with an "Anzeige" label.
- The machine and grinder catalog (`core/.../catalog/EquipmentCatalog.kt`) is a start; grind ranges are typical starting points and should be checked with beta users.

- iOS app (SwiftUI on top of `DropsKit`).
- Label scanning for new beans (CameraX + ML Kit text recognition on device).
- Café/roaster data and bean recommendations are example content; a data source is still to be chosen.
- Remove the React Native app once the native app covers its features.
