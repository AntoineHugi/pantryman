# Pantryman — Review & Improvement Plan

_Review date: 2026-10-14_

Context: Pantryman started as a learning project (Kotlin/Ktor backend + React/Vite web frontend). Goals going forward:

1. Ship a mobile app (APK) usable by me, my partner and relatives.
2. Add a recipe database + weekly meal plan that generates the grocery list automatically.
3. Develop faster, using AI for coding.

The current base is solid — clean layering (routes → services → repositories), Flyway migrations, Koin DI, real integration tests, typed frontend API layer. **No rewrite needed.**

---

## 1. Bugs to fix now

### Security
- **Item IDOR (high).** Item `DELETE`/`PATCH` in `GroceryListAPI.kt` checked that the caller owns `listId`, then acted on `itemId` without checking the item belongs to that list. Any logged-in user could edit/delete anyone's items given an item UUID. Item `POST` also trusted `listId` from the body instead of the path.
- **JWT secret has a public default.** `application.yaml` fell back to `dev-secret-key-change-in-production`. If `JWT_SECRET` isn't set in production, anyone can forge tokens. Remove the default so the app refuses to start without it.
- **No rate limiting on `/auth/login`.**

### Functional
- **Wrong passwords log you out.** The axios 401 interceptor (`client.ts`) clears the token and reloads to `/login` on *any* 401. Login failure and "wrong current password" (change password / delete account) also return 401 → the login error never shows, and a typo in Settings logs you out.
- **Item order is unstable.** No `ORDER BY` on items; Postgres can reorder rows after an update, so ticking an item can move it. Add `created_at` and sort.
- **Emails aren't normalized.** `Foo@x.com` and `foo@x.com` are separate accounts; login is case-sensitive.
- **Signup with a failed verification email leaves an orphan user.** The user row is created, then the email fails → 500. Retrying signup says "Registration Failed".
- **Password rules disagree.** Frontend Settings allowed ≥6 chars, backend requires ≥8.
- **Malformed UUIDs in URLs → 500**, and error responses are inconsistent (plain strings vs `{"error": ...}`). → Fixed properly by `StatusPages` in step 2.

---

## 2. Setup for fast AI-assisted development

1. **Merge the two repos into a monorepo** (`/backend`, `/app`). Most features touch API + UI; one repo lets an agent change both in one go.
2. **Add `CLAUDE.md`**: how to run, test, architecture, conventions (e.g. "every route checks ownership").
3. **CI (GitHub Actions)**: `./gradlew test`, `tsc`, `eslint` on every push. Tests are the safety net when you're not writing every line.
4. **Ktor `StatusPages`** for centralized error handling — delete per-route try/catch and repeated "parse UUID → 400" code. Consistent JSON error shape.
5. **TanStack Query** on the frontend — replaces hand-rolled "call API then patch local state" code (`ListDetailsPage.tsx` is ~360 lines of it). Caching, refetch, optimistic updates; essential once lists are shared.
6. **Gradle hygiene**: version catalog; align Ktor (plugin 3.1.3, libs 3.2.1, CORS 3.2.3) and the serialization plugin (1.9.23 vs Kotlin 2.1.20).
7. **Remove dead code**: `MenuAPI.kt` hello-world endpoints, `DatabaseFactory.getConnection`, unused `existing` in `GroceryListService.update`, Vite `/api` proxy + nginx config (point at a non-existent `/api` prefix), web Dockerfile if deploying on Vercel, `CreateGroceryListRequest.userId` (ignored by server), duplicate `Item` type in `AddItemForm.tsx` (also bypasses `itemsApi`), tracked `.idea/` in the web repo.
8. **Component library or Tailwind** instead of 615 lines of custom CSS + inline `style={{}}`. A design system makes AI-generated UI consistent.

---

## 3. Goal 1 — Android app for family

**Path: PWA → Capacitor.**
- Make it a **PWA** (`vite-plugin-pwa`). Android can install it from Chrome without an APK — may be all that's needed.
- For a real APK, wrap the same React app with **Capacitor** (100% code reuse). Add Capacitor's origin (`https://localhost`) to CORS in `Application.kt`, and make the email-verify redirect work without a URL bar.
- **Avoid** React Native / Compose rewrites — doubles UI work.
- **Mobile-first UX**: item actions as four tiny buttons are awkward in a store; swipe-to-delete and tap-to-check work better.

**Bigger blocker: data model.** Lists belong to one user, so nothing can be shared. Before recipes, add **households**:
- `households` + `household_members` tables.
- Lists and recipes belong to a household; invite members by email or code.
- Ownership checks become "is the user a member of this list's household?"

Do this early — retrofitting after recipes means migrating twice. Near-real-time sync: polling / refetch-on-focus (TanStack Query) is enough to start.

---

## 4. Goal 2 — Recipes → grocery list

**Data model**
- Quantities: `item.quantity` is an `INT` with no unit. Migrate to `amount NUMERIC` + `unit`.
- Tables:
  - `ingredients` — canonical name, category/aisle (for sorting the list)
  - `recipes` — name, servings, instructions, source URL
  - `recipe_ingredients` — recipe, ingredient, amount, unit
  - `meal_plan` — household, week, recipe, servings
  - `items` gains optional `ingredient_id` + source recipe ("from: Lasagna")

**List generation** — one backend function: selected recipes → scale by servings → group by (ingredient, unit) → sum → merge into existing unchecked items rather than duplicating. Write it test-first; unit conversion and merging is where bugs hide.

**Pantry staples** — household marks salt, oil, etc. as "always have"; skipped during generation.

**Getting recipes in**
- URL import: most recipe sites embed schema.org `Recipe` JSON-LD — no AI needed.
- Later: LLM to parse free text / photos and match ingredients to the canonical list.

---

## 5. Hosting (status as of 2026-10-14)

> **Resolved 2026-10-15.** The backend was actually running on **Render** (`pantryman.onrender.com`), and the database is the **Neon** project `pantryman-db`. Nothing was lost. After the monorepo merge, Render's Root Directory was set to `backend` and the env vars were updated (new Neon password, `JWT_SECRET`). The site works again. `koyeb.yaml` was removed. The rest of this section is kept for history.

**Original state (2026-10-14): the backend is down.** I no longer have access to the Koyeb account. `pantryman.koyeb.app` returns Koyeb's own 404 page, so no service exists there any more. The frontend at pantryman.de (Vercel) is still up, but every API call fails.

**Database: location unknown, probably lost.** The production `DB_URL` was only ever set in Koyeb's dashboard. It isn't in the repos, the git history or the local `.env`.
- If it was Koyeb's built-in Postgres (hostname like `ep-….pg.koyeb.app`), it's gone with the account.
- If it was a separate provider, it may still exist. Check email and the password manager for Neon, Supabase, Aiven, etc.
- Losing it is acceptable: it held only a few lists and test accounts. The schema is rebuilt automatically by the Flyway migrations on first start.

**Redeploy plan.** Everything runs from env vars, so moving hosts needs no code changes.

| Piece | Requirement | Options to evaluate |
|---|---|---|
| Postgres | Free, **doesn't expire or get deleted when idle** | Neon (free tier scales to zero, no expiry), Supabase (free tier pauses inactive projects; check whether pausing risks data). Avoid free tiers with a fixed lifetime, e.g. Render's free Postgres. |
| Backend (Docker, port 8000) | Free or cheap, runs the existing `Dockerfile` | New Koyeb account, Fly.io, Render. Free tiers usually sleep when idle, so the first request is slow; fine for family use. |
| Frontend | Already on Vercel | Keep it. |

_Free-tier terms change often. Check the current limits before signing up._

**Steps once hosts are picked:**
1. Create the database and note its JDBC URL (`jdbc:postgresql://host:5432/db?sslmode=require`), user and password.
2. Deploy the backend with `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (new random value), `RESEND_API_KEY`, `RESEND_FROM_EMAIL`, `VERIFICATION_BASE_URL` (the new backend URL) and `FRONTEND_BASE_URL` (https://pantryman.de).
3. Check that Flyway ran (tables V1–V7 exist) and that signup → verification email → login works.
4. On Vercel, set `VITE_API_URL` to the new backend URL and redeploy.
5. Update the CORS allow-list in `Application.kt` if the frontend origin changes, and update `CLAUDE.md` § Deployment.
6. Remove leftovers that point at Koyeb: `nginx.conf` `proxy_pass`, `koyeb.yaml` (if leaving Koyeb), and the README mentions.

Redeploying is needed before step 3, because family members need a running backend. Steps 2 and 3 can be developed locally until then.

---

## Suggested order

1. Fix the security issue and bugs in §1, merge repos, add `CLAUDE.md` and CI.
2. `StatusPages`, TanStack Query, cleanup (§2).
   - In parallel: pick hosts and redeploy (§5).
3. Households/sharing → PWA → Capacitor (partner can start using it).
4. Recipes, meal plan, list generation.
5. Later: URL import, aisle sorting, offline support.

---

## Progress

_Updated as work lands._

- [x] Step 1: bug fixes (2026-10-14, see below)
- [x] Step 1: `CLAUDE.md` (project root)
- [x] Step 1: merge repos (2026-10-14). `pantryman-web` was merged into the `pantryman` repo as `app/`, and the backend moved to `backend/`. Both histories are kept. Pending: point Vercel at the new repo and archive `pantryman-web` on GitHub.
- [x] Step 1: CI (2026-10-14). A single `.github/workflows/ci.yml` with `backend` and `app` jobs.
- [x] Redeploy (2026-10-15): Render + Neon, see §5
- [ ] Step 2: done except component library/Tailwind (§2.8) (2026-10-14, see below)
- [ ] Step 3: households and sharing done (2026-10-17, see below). PWA and Capacitor still to do.
- [ ] Step 4
- [ ] Step 5

### Step 1 bug fixes (2026-10-14)

| Bug | Fix | Where |
|---|---|---|
| Item IDOR | `ItemRepository` get/update/delete now filter by `listId` **and** `itemId`; routes take `listId` from the path only. `CreateItemRequest.listId` removed. | `ItemRepository.kt`, `ItemService.kt`, `GroceryListAPI.kt` |
| JWT secret default | `secret: ${JWT_SECRET}`, with no default. Ktor refuses to start if it's missing. | `application.yaml` |
| No login rate limit | Ktor `RateLimit`: 10 requests/min per client IP on signup, login and resend-verification. Keyed on the last `X-Forwarded-For` entry (Koyeb proxy). | `plugins/RateLimiting.kt`, `AuthAPI.kt` |
| 401 logs you out | Interceptor ignores 401s from `/auth/*`. Pages show the backend's error message through the new `getErrorMessage()`. | `client.ts`, `LoginPage`, `SignUpPage`, `SettingsPage` |
| Unstable item order | Migration `V7` adds `created_at` to lists and items; queries `ORDER BY created_at, id`. | `V7__Add_created_at_for_ordering.sql`, tables, repositories |
| Email case | Emails are trimmed and lowercased on signup, login and resend; lookups are case-insensitive, so older mixed-case accounts still match. | `AuthService.kt`, `UserRepository.kt` |
| Orphan user on email failure | If the verification email fails, the new user is deleted so signup can be retried. | `AuthService.kt` |
| Password min 6 vs 8 | Frontend now requires 8. | `SettingsPage.tsx` |
| Malformed UUID → 500 | **Deferred to step 2** (`StatusPages`). | |

Also changed:
- Server JSON now has `ignoreUnknownKeys = true`, so older clients that still send removed fields (e.g. `listId`) keep working.
- Fixed a pre-existing TS type-only import error in `AuthContext.tsx`, so `tsc -b` passes.

**Test suite repaired.** Before this work, `./gradlew test` didn't compile. The auth tests predated email verification, the API tests had no JSON client and created lists without a user row, and `UserMapperTest` didn't cover the verification fields. All of these were updated, and new tests cover cross-user item access, item ordering, email normalization, signup rollback, `/auth/verify` and rate limiting.

Verification:
- Backend: 59 tests across 8 classes pass (services, mappers, H2 API tests).
- Frontend: `tsc -b` and `npm run build` pass.
- Not run: `*RepositoryTest` (Testcontainers needs Docker, which wasn't running) and `RoutingMenuAPIIT`. Migration `V7` hasn't been run against Postgres yet; run the repository tests or start the app locally once Docker is up.

⚠️ **Before deploying the backend:** set `JWT_SECRET` on the backend host (a long random string, e.g. `openssl rand -base64 48`), or the app won't start. (Koyeb is gone, so this applies to whichever host replaces it; see §5.)

Notes for step 2:
- `application.yaml` uses `${VAR:-default}` (shell syntax). Ktor's syntax is `${VAR:default}`, so the defaults probably include a leading `-`. Only local-dev defaults are affected.
- Remaining lint error: `AuthContext.tsx` exports a context alongside a component (react-refresh rule). Move the context to its own file.
- `ListsPage` still sends `userId` when creating a list; the server ignores it.

### Step 2 + CI (2026-10-14)

| Item | What changed |
|---|---|
| CI | `.github/workflows/ci.yml` in each repo. Backend: `./gradlew test` on JDK 17 (Testcontainers runs, since the runner has Docker). Web: `npm ci`, `tsc -b`, `lint`, `build`. |
| `StatusPages` | `plugins/StatusPages.kt` maps exceptions to `{"error": ...}`: `IllegalArgumentException`/`BadRequestException` → 400, `SecurityException` → 401, `ForbiddenException`/`EmailNotVerifiedException` → 403, `NotFoundException` → 404, anything else → 500 (logged). `call.uuidParameter()` turns malformed path UUIDs into 400. Per-route try/catch removed from `AuthAPI.kt` and `GroceryListAPI.kt`. |
| TanStack Query | `hooks/useGroceryLists.ts` holds the query and mutation hooks. Item edits are optimistic, roll back on error and refetch afterwards. Windows refetch on focus. `ListsPage`, `ListDetailsPage` (362 → 259 lines) and `AddItemForm` use them. Logout clears the cache. |
| Gradle | `gradle/libs.versions.toml` version catalog. Ktor plugin and libraries are all on 3.2.3, and the serialization plugin matches Kotlin 2.1.20. Unused `exposed-dao` removed. |
| Dead code | Removed `MenuAPI.kt` (+ its test), `DatabaseFactory.getConnection`, unused `existing` lookup in `GroceryListService.update`, `CreateGroceryListRequest.userId` (old clients still work through `ignoreUnknownKeys`), Vite `/api` proxy, web `Dockerfile` + `nginx.conf`, duplicate `Item` type in `AddItemForm`, tracked `.idea/`. |
| Step 1 notes | `application.yaml` defaults now use `${VAR:default}`. Auth context moved to `contexts/AuthContextValue.ts`, so lint passes. `ListsPage` no longer sends `userId`. List rename takes a typed `UpdateGroceryListRequest`. |

Behaviour changes: item `PATCH` now returns `{"message": "Item updated"}` (JSON) instead of plain text. Unexpected auth errors now return "Something went wrong" instead of "Signup failed" / "Login failed". The frontend ignores both bodies.

Verification: backend 63 tests pass (H2 API tests + unit tests, including new tests for malformed UUIDs, bad JSON bodies, unknown lists and service error messages). The 3 `*RepositoryTest` classes still need Docker and weren't run locally. They will run in CI. Web: `tsc -b`, `lint` (0 errors) and `build` pass.

Not done in step 2: component library / Tailwind (§2.8). It's a UI-wide restyle and worth doing together with the mobile-first UX pass in step 3.

### Docker and repository tests (2026-10-15)

- **Docker image:** `gradle:8.8.0-jdk17-alpine` has no arm64 build, so the image didn't build on Apple Silicon. The build stage now uses `eclipse-temurin:17-jdk` + `./gradlew`, so it builds with the same Gradle version as local and CI. A new `.dockerignore` keeps `.env` and `build/` out of the build context.
- **Testcontainers 1.20.1 → 1.21.4.** Docker Engine 29 rejects API versions below 1.44, and the old Testcontainers got a 400 from it.
- **Repository tests ran for the first time and failed:** they created lists for users that didn't exist. Fixed.
- **New `MigrationTest`** runs the real Flyway migrations on Postgres and checks that every Exposed column exists with the same nullability. It found that `item_list.quantity/is_checked/is_favorite/list_id` were nullable in the database (V3) but NOT NULL in Exposed. **`V8`** fixes this. It deletes items with no list (unreachable anyway) and fills NULLs with the defaults.
- V7 and V8 are now verified against Postgres 15. All 86 backend tests pass locally.

### Rate limit fix on Render (2026-10-15)

A check against production showed the login rate limit did nothing: 15 failed logins in 8 s were never blocked. It keyed on the last `X-Forwarded-For` entry, which on Render is a proxy address that changes per request, so every request got a fresh allowance. Render is behind Cloudflare, so the key is now `CF-Connecting-IP`. Cloudflare sets that header itself and overwrites any client value. Without the header (local dev) the key falls back to the socket address. A new test checks that rotating `X-Forwarded-For` values doesn't get around the limit.

### Step 3: households (2026-10-17)

Users can create households, join one with an invite code, and share lists with them.

| Piece | What changed |
|---|---|
| Data model | Migration `V9`: `households` (name, unique `invite_code`, `owner_id`), `household_members` (household, user, `joined_at`), and a nullable `grocery_lists.household_id` (`ON DELETE SET NULL`). NULL = personal list. Existing lists stay personal; nothing is migrated. |
| Access rule | `GroceryListRepository.accessibleBy()`: the owner, or any member of the list's household. All list queries and the item routes' list check use it. Only the owner can change who a list is shared with. |
| API | `GET/POST /households`, `POST /households/join` (rate limited per IP, 10/min), `POST /households/{id}/invite-code` (new code, old one stops working), `POST /households/{id}/leave`. Owner only: `PATCH /households/{id}` (rename) and `DELETE /households/{id}/members/{userId}` (remove a member, which also replaces the invite code). `POST /lists` takes an optional `householdId`. `PUT /lists/{id}/household` with `{"householdId": "..." \| null}` shares or unshares. `GroceryList` responses gain `householdId`. All additive. |
| Owner | The creator owns the household and can rename it and remove members. When the owner leaves, the longest-standing remaining member becomes the owner. |
| Leaving | A leaving or removed member's shared lists pass to the owner (to the new owner if the owner leaves), so the household keeps them. The last member leaving deletes the household and its lists become theirs again. Account deletion does the same before deleting the user, so a partner deleting their account doesn't delete the shared lists. |
| Invite codes | 8 characters without 0/O/1/I. Input ignores case, spaces and dashes. Any member can see the code, share it and replace it. |
| Frontend | New Households page (menu → Households): members, invite code, share invite link (phone share sheet or clipboard), new code, leave, join, create. The owner also gets rename and remove-member buttons. Lists page groups lists by household and lets you pick a household when creating a list. The list page has a "Shared with" selector for the owner. Login now returns to the page that asked for it, so an invite link works when logged out. |

Verification: 112 backend tests pass, including the Testcontainers tests and `MigrationTest` (V9 checked against Postgres 15). New `HouseholdAPIIT` covers joining, sharing, cross-user access (non-members, non-owners, unknown codes), leaving and account deletion. Frontend `tsc -b`, `lint` and `build` pass. Not tested by hand in a browser yet.

Not done / follow-ups:
- A new user who opens an invite link, signs up and verifies their email lands on the login page without the code. They have to open the link again.
- Anyone with the invite code can join. Make a new code if it leaks.
- No real-time sync: other members' changes show on refetch (window focus, or after 10 s on navigation). If it's needed, start with polling the open list every few seconds (one `refetchInterval` option); SSE/WebSockets only if that's not enough.

