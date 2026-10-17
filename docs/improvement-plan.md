# Pantryman — Improvement Plan

_Started 2026-10-14. Last cleaned up 2026-10-17: finished work is reduced to the log at the bottom; how things work now is in `CLAUDE.md`._

Goals:

1. Ship a mobile app (APK) usable by me, my partner and relatives.
2. Add a recipe database + weekly meal plan that generates the grocery list automatically.
3. Develop faster, using AI for coding.

The base is solid (routes → services → repositories, Flyway, Koin, real integration tests, CI, typed frontend API layer with TanStack Query). **No rewrite needed.**

---

## Suggested order

1. ~~Bug fixes, monorepo, `CLAUDE.md`, CI~~ (done)
2. ~~`StatusPages`, TanStack Query, Gradle, cleanup, redeploy~~ (done, except Tailwind, moved to step 3)
3. ~~Households/sharing~~ → household follow-ups → Capacitor APK → mobile UX pass (+ Tailwind).
4. Recipes, meal plan, list generation.
5. Later: URL import, aisle sorting, offline support.

---

## Step 3 (remaining) — Android app for family

**Household follow-ups** (from the 2026-10-17 households work):
- Not yet tested by hand in a browser.
- Anyone with the invite code can join. Make a new code if it leaks. Acceptable for family use.
- No real-time sync: other members' changes show on refetch (window focus, or after 10 s on navigation). If that's not enough, start with polling the open list (`refetchInterval`); SSE/WebSockets only if polling isn't enough.

**Path: Capacitor APK, no PWA step** (decided 2026-10-17: a real installable app is preferred over a PWA).

Capacitor wraps the built React app (`app/dist`) in a native Android WebView shell (`app/android/`, generated) and produces a normal APK. The React code is reused as is.
- **Bundled assets**, not `server.url` pointing at pantryman.de: the app opens without a connection, and that's the setup Capacitor supports for production. The cost is that frontend changes need a new APK, while backend changes go live immediately. Later: an in-app "new version available" check.
- **CORS:** add Capacitor's origin (`https://localhost`) to the allow-list in `Application.kt`.
- **Deep links:** invite links (`/households?join=CODE`) and the email verification redirect must open the app. Use Android App Links for pantryman.de, which needs `app/public/.well-known/assetlinks.json` served by Vercel.
- **Native share sheet** for invite links through the Capacitor Share plugin (the web share API is not reliable in a WebView).
- **Build in CI:** a GitHub Actions job builds the APK and attaches it to a GitHub release. Locally, the Android SDK is needed (Android Studio).
- **Distribution:** sideload the APK ("install unknown apps"), or a Play Store account ($25 once) later if that gets tedious.
- **iPhones:** the APK is Android only. iPhone users keep using pantryman.de in Safari; an iOS build (Xcode + $99/year) only if needed.
- **Avoid** React Native / Compose rewrites; they double the UI work.

**Mobile-first UX pass + design system** (after the APK, so it's done on a real phone).
- Item actions as four tiny buttons are awkward in a store; swipe-to-delete and tap-to-check work better.
- Move from ~600 lines of custom CSS + inline `style={{}}` to Tailwind or a component library, so AI-generated UI stays consistent. Do it together with the UX pass, since both touch every page.

---

## Step 4 — Recipes → grocery list

**Data model**
- Quantities: `item.quantity` is still an `INT` with no unit. Migrate to `amount NUMERIC` + `unit`.
- Tables:
  - `ingredients`: canonical name, category/aisle (for sorting the list)
  - `recipes`: household (or personal, like lists), name, servings, instructions, source URL
  - `recipe_ingredients`: recipe, ingredient, amount, unit
  - `meal_plan`: household, week, recipe, servings
  - `items` gains optional `ingredient_id` + source recipe ("from: Lasagna")
- Reuse the list access rule (owner or household member) for recipes and meal plans.

**List generation**: one backend function: selected recipes → scale by servings → group by (ingredient, unit) → sum → merge into existing unchecked items rather than duplicating. Write it test-first; unit conversion and merging is where bugs hide.

**Pantry staples**: the household marks salt, oil, etc. as "always have"; they're skipped during generation.

**Getting recipes in**
- URL import: most recipe sites embed schema.org `Recipe` JSON-LD, so no AI is needed.
- Later: an LLM to parse free text / photos and match ingredients to the canonical list.

---

## Progress

- [x] Step 1: bug fixes, `CLAUDE.md`, monorepo, CI (2026-10-14)
- [x] Redeploy on Render + Neon (2026-10-15)
- [x] Step 2 (2026-10-14), except Tailwind, moved to step 3
- [ ] Step 3: households done (2026-10-17), invite link through signup fixed (2026-10-17), Capacitor project + APK workflow set up (2026-10-17). To do: GitHub secrets + first APK build, hand test on a phone, UX pass.
- [ ] Step 4
- [ ] Step 5

### Done log

- **2026-10-14 — Step 1 bug fixes.** Item IDOR (item queries now scoped by `listId`), JWT secret without a default, auth rate limiting, 401 interceptor no longer logs out on `/auth/*`, stable ordering (`V7` `created_at`), email normalization, signup rollback when the verification email fails, password minimum 8 on both sides. The test suite, which didn't compile before, was repaired.
- **2026-10-14 — Monorepo, `CLAUDE.md`, CI.** `pantryman-web` merged in as `app/` with history; Vercel deploys from this repo and the old repo is archived.
- **2026-10-14 — Step 2.** `StatusPages` (consistent `{"error": ...}`, malformed UUIDs → 400), TanStack Query with optimistic item edits, Gradle version catalog, dead code removed. Server JSON uses `ignoreUnknownKeys` so older clients keep working.
- **2026-10-15 — Hosting.** The Koyeb account was lost, but the backend was actually on Render and the DB on Neon; nothing was lost. Render's root directory set to `backend`, env vars refreshed, `koyeb.yaml` removed.
- **2026-10-15 — Docker + repository tests.** Docker build fixed for Apple Silicon, Testcontainers bumped for Docker Engine 29, new `MigrationTest` (found nullable item columns, fixed by `V8`).
- **2026-10-15 — Rate limit on Render.** Keying on `X-Forwarded-For` did nothing behind Render's rotating proxies; now keyed on `CF-Connecting-IP`.
- **2026-10-17 — Households.** `V9`: households, members, invite codes, owner, `grocery_lists.household_id`. Lists can be shared with a household; ownership passes on when members leave or delete their account. Households page, lists grouped by household, "Shared with" selector. 112 backend tests pass.
- **2026-10-17 — Invite links through signup.** A logged-out user opening an invite link now keeps it through login ↔ signup (`?next=`), the verification email (`/auth/verify?token=…&next=…`) and the login redirect. `SignupRequest` and `ResendVerificationRequest` gain an optional `redirectPath` (additive). Only in-app paths are accepted (`isSafeRedirectPath` on the backend, `safeRedirectPath` in the frontend), so the links can't redirect to another site. 118 backend tests pass.
- **2026-10-17 — Capacitor Android app.** `app/android/` (Capacitor 8, `de.pantryman.app`), bundled assets built against the production API (`npm run build:android`, `.env.android`). CORS allows `https://localhost` (`plugins/Cors.kt`, new `CorsIT`). Invite links use the public URL in the app and go through the native share sheet. App Links for pantryman.de `/households` and `/login` (`assetlinks.json` with the release key's fingerprint), handled by `useNativeAppEvents`, which also refetches when the app returns to the foreground. Release signing from env vars; `.github/workflows/android.yml` builds the signed APK (manual run or `v*` tag → GitHub release), versionCode = run number. Not yet built or run on a device: there's no Android SDK locally, so the first build is in CI.

  To check on the first device test: status bar / navigation bar padding (`SystemBars.insetsHandling: 'native'`), the back button, invite and verification links opening the app (App Links only verify once `assetlinks.json` is live on pantryman.de), and the share sheet.
- **2026-10-17 — Verification link opens the app.** Verification emails now link to the frontend's `/verify` page (`FRONTEND_BASE_URL`), which calls the new `POST /auth/verify` and then goes to login with the invite path. Before, the link went through a backend redirect, which Android doesn't reliably hand to the app. `GET /auth/verify` stays for emails sent earlier. `/verify` is an App Link path. `npm audit fix` applied; the 3 remaining warnings are in `@capacitor/cli`'s iOS tooling (dev only, not shipped).
