# Pantryman

Personal grocery-list app for me, my partner and relatives. Started as a hand-written learning project; it is now developed primarily with AI assistance, and the priority is shipping features quickly without breaking things.

## Product direction

1. **Mobile app (APK)** for family members. Plan: wrap the web app with Capacitor (bundled assets, no PWA step). Not a separate native rewrite.
2. **Sharing via households.** Done (2026-10-17). A list is personal or shared with one of the user's households. Recipes and meal plans should belong to a household too.
3. **Recipes → grocery list.** Recipe database with ingredients; the user picks recipes for the week and the grocery list is generated (scaled by servings, merged by ingredient + unit, pantry staples skipped).

The full roadmap and progress are in `docs/improvement-plan.md`. Update its Progress section when a step lands.

## Layout

One git repo (GitHub: `AntoineHugi/pantryman`). The old `pantryman-web` repo was merged in on 2026-10-14 with its history.

- `backend/`: backend. Kotlin, Ktor 3 (Netty), Exposed (DSL, not DAO), Flyway, Postgres, Koin DI, JWT auth, Resend for email.
- `app/`: frontend. React 19, TypeScript, Vite, react-router, axios. Also the Android app: `app/android/` is the Capacitor project that bundles the built frontend.
- `docs/`: plans and design notes.

### Backend structure (`backend/src/main/kotlin/org/pantry/`)

- `plugins/`: Ktor route definitions (`*API.kt`), JWT config, rate limiting. Routes do HTTP concerns plus ownership checks.
- `services/`: business rules and validation (throw `IllegalArgumentException` for bad input).
- `repositories/`: Exposed queries, each wrapped in `transaction {}`.
- `postgres/tables/`: Exposed table objects. **They must mirror the Flyway migrations.**
- `models/`: `@Serializable` request/response DTOs.
- `mappers/`: `ResultRow` → model.
- `di/AppModule.kt`: Koin wiring.
- Dependency versions: `gradle/libs.versions.toml`.
- `src/main/resources/db/migration/`: Flyway migrations `V<n>__Description.sql`.

### Frontend structure (`app/src/`)

- `api/`: axios client and one module per resource (`lists.ts`, `items.ts`, `households.ts`, `auth.ts`), plus `types.ts`. Pages call these modules and never call axios directly.
- `hooks/useGroceryLists.ts`, `hooks/useHouseholds.ts`: TanStack Query hooks (queries + mutations, optimistic item updates). Pages use these for server data instead of `useEffect` + `useState`.
- `pages/`, `components/`, `contexts/AuthContext.tsx` (token in localStorage; the context object is in `AuthContextValue.ts`), `hooks/`.

## Commands

Backend (from `backend/`):
- Start the local DB: `docker compose up -d postgres` (pgAdmin is on :5050).
- Run: `set -a; source .env; set +a; ./gradlew run` (serves on :8000). Ktor does **not** load `.env` itself. `JWT_SECRET` is required, and the app refuses to start without it.
- Test: `./gradlew test`. Repository tests (`*RepositoryTest`) use Testcontainers and need Docker running. API tests (`*IT`) and unit tests use H2 or mocks and don't.

Frontend (from `app/`):
- `npm run dev` (Vite on :5173; the API URL comes from `VITE_API_URL`, default `http://localhost:8000`).
- `npx tsc -b`, `npm run lint`, `npm run build`.
- Android: `npm run build:android` builds against the production API (`.env.android`) and copies the result into `android/` (`cap sync`). The Capacitor CLI needs Node 22 (`nvm use 22`). Building the APK locally needs the Android SDK (Android Studio, `npm run cap:open`); otherwise use CI.

Run the relevant tests, plus the typecheck for frontend changes, before calling a change done. CI (`.github/workflows/ci.yml`, one job each for `backend` and `app`) runs the same checks on push and on pull requests.

## Deployment

- Backend: **Render** web service `pantryman` (free instance, sleeps when idle; the first request after that takes ~50 s), https://pantryman.onrender.com. Render builds `backend/Dockerfile` with **Root Directory = `backend`**, and auto-deploys on every push to `main`. Env vars are set in Render's dashboard: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `RESEND_API_KEY`, `RESEND_FROM_EMAIL`, `FRONTEND_BASE_URL` (verification emails link to its `/verify` page). `VERIFICATION_BASE_URL` is no longer used. A gitignored `backend/.env.production` holds a copy for reference. Never commit it.
- Database: **Neon** project `pantryman-db`, branch `production` (Frankfurt, free plan). `DB_URL` uses the direct host, not `-pooler`, because Flyway needs a session connection. Neon archives the branch after inactivity; the first query restores it.
- Render sits behind Cloudflare. The login rate limiter keys on `CF-Connecting-IP` (`plugins/RateLimiting.kt`). Revisit that if the backend moves off Cloudflare.
- Frontend: Vercel (`app/vercel.json` SPA rewrite), served at pantryman.de. The Vercel project's root directory must be `app`. Vercel deploys from this repo (the old `pantryman-web` repo is archived). The CORS allow-list is in `plugins/Cors.kt`.
- Flyway migrations run automatically at backend startup.
- Android app (`de.pantryman.app`): the `Android APK` workflow (`.github/workflows/android.yml`) builds a signed APK when run by hand or on a `v*` tag (tags also create a GitHub release). It needs the secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. The keystore lives outside the repo (`~/.pantryman-android/`). **Never commit it, and don't lose it:** every APK must be signed with the same key to install over the previous one, and `app/public/.well-known/assetlinks.json` holds its SHA-256 fingerprint for App Links.
- The APK bundles the frontend, so frontend changes reach phones only with a new APK; backend changes reach them immediately. Keep API changes additive (see JSON below).
- In the app, the page runs at `https://localhost`. Links meant for other people must use `publicAppUrl` (`src/native.ts`), not `window.location.origin`. pantryman.de links under `/households`, `/login`, `/verify` and `/reset-password` open the app (App Links, `AndroidManifest.xml`) and are routed by `hooks/useNativeAppEvents.ts`.

## Conventions and gotchas

- **Authorization:** every route under `authenticate("auth-jwt")` gets the user from `call.userId` (the JWT claim) and never from the request body. Any query on a child resource must be scoped by its parent. For example, item queries always filter by `listId` as well as `itemId`, after the route has checked that the user can access the list. Add a test for cross-user access whenever you add an endpoint.
- **Households:** a user can access a list if they own it (`grocery_lists.user_id`) or are a member of its household (`grocery_lists.household_id`, NULL = personal). `GroceryListRepository.accessibleBy()` is that rule; scope list queries with it. Any member can rename, edit or delete a shared list; only the owner can change who it's shared with. Members join with an invite code (`/households?join=CODE` link). The household's owner (`households.owner_id`, initially the creator) can rename it and remove members; removing a member also replaces the invite code. When a member leaves, is removed or deletes their account, their shared lists pass to the owner (or, if the owner is leaving, to the longest-standing remaining member, who becomes the owner); when the last member leaves, the household is deleted and its lists become personal (`HouseholdRepository.removeMember`).
- **Migrations:** never edit an applied migration; add a new `V<n+1>__...sql`. Update the matching Exposed table object at the same time. Most tests build their schema from the Exposed objects (`SchemaUtils.create`), not from the migrations. `MigrationTest` is the exception: it runs Flyway and checks that the columns and nullability match. Keep it passing, and add new tables to its list.
- **Ordering:** Postgres has no stable row order. Queries that return lists must `orderBy` (currently `createdAt`, then `id`).
- **Emails** are stored trimmed and lowercase, and looked up case-insensitively.
- **JSON:** the server ignores unknown keys, so older installed clients keep working when request fields are removed. Prefer additive API changes, because mobile clients will lag behind deploys.
- **Errors:** routes don't catch exceptions. They throw, and `plugins/StatusPages.kt` maps them (`IllegalArgumentException` → 400, `ForbiddenException` → 403, Ktor `NotFoundException` → 404, …). Parse path IDs with `call.uuidParameter("listId")`. Responses use `{"error": "message"}`. The frontend reads them with `getErrorMessage()` in `api/client.ts`. The axios 401 interceptor logs the user out, except on `/auth/*` endpoints, which return 401 for wrong credentials.
- **Auth endpoints** (signup, login, resend-verification, forgot-password, reset-password) are rate limited per client IP (`plugins/RateLimiting.kt`).
- **Password reset:** `POST /auth/forgot-password` emails a link to `/reset-password?token=...` and answers the same whether or not the account exists. Only a SHA-256 hash of the token is stored (`users.password_reset_token_hash`), valid 1 hour, single use, latest link only. A successful reset also verifies the email. Existing logins (JWTs) stay valid after a reset.
- Ktor YAML env syntax is `${VAR}` (required) or `${VAR:default}`.
