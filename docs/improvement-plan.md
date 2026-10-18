# Pantryman — Improvement Plan

_Started 2026-10-14. Last updated 2026-10-18: finished work is reduced to the log at the bottom; how things work now is in `CLAUDE.md`._

Goals:

1. Ship a mobile app (APK) usable by me, my partner and relatives.
2. Add a recipe database + weekly meal plan that generates the grocery list automatically.
3. Develop faster, using AI for coding.

The base is solid (routes → services → repositories, Flyway, Koin, real integration tests, CI, typed frontend API layer with TanStack Query). **No rewrite needed.**

---

## Suggested order

1. ~~Bug fixes, monorepo, `CLAUDE.md`, CI~~ (done)
2. ~~`StatusPages`, TanStack Query, Gradle, cleanup, redeploy~~ (done, except Tailwind, moved to step 3)
3. ~~Households/sharing~~ → household follow-ups → Capacitor APK → ~~mobile UX pass~~ (Tailwind still open).
4. Recipes, meal plan, list generation.
5. Later: URL import, aisle sorting, offline editing (offline reading works: the query cache is persisted).

---

## Step 3 (remaining) — Android app for family

**Household follow-ups** (from the 2026-10-17 households work):
- Not yet tested by hand in a browser.
- Anyone with the invite code can join. Make a new code if it leaks. Acceptable for family use.
- Sync is by polling: the open list refetches every 5 s while visible, and other pages refetch on focus. SSE/WebSockets only if that isn't enough (the free Render instance sleeping makes long-lived connections unreliable anyway).

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

**Mobile-first UX pass**: done 2026-10-18 (see the log), tested in a phone-sized browser. Still to do:
- Check it on a real phone with the APK: keyboard behavior when adding several items, the toast above the navigation bar, tap targets.
- Design system: the CSS is now token-based plain CSS (`index.css` + `App.css`) with no inline styles. Moving to Tailwind or a component library is still worth it before the recipe pages add many new screens.
- Ideas not done: swipe to delete; a "new version available" check in the APK; undo for deleting a whole list.

---

## Step 4 — Recipes → grocery list

**Decisions (2026-10-18)**
- Item quantities become decimal (`item_list.quantity NUMERIC`), plus an optional `unit`. The `quantity` field is kept, so older APKs keep working (additive).
- **Recipes are shared with every user**, not scoped to a household, so everyone benefits from what others add. Anyone can view a recipe and use it in a list or a meal plan. Only the user who added it can edit or delete it. If that user deletes their account, the recipe stays (`created_by` set to NULL).
- Meal plans stay per household (or personal), with the list access rule.
- **No LLM in the app** (no billed API account). Recipes are converted from our text documents to JSON outside the app, with a Claude plan (later a skill), and imported. The format is in `docs/recipe-format.md`.
- Everything in English. Ingredient names are lowercase, singular and generic, so merging by name works without a canonical `ingredients` table for now.

**Slices**
1. Recipes: tables, CRUD API, a Recipes section (bottom tab bar: Lists / Recipes / Plan), a readable recipe page.
2. JSON import: `POST /recipes/import` (one or several recipes, validated all-or-nothing), and an import screen (pick a file or paste).
3. "Add to list" from a recipe: servings + target list → items added, scaled and merged, tagged "from: Lasagna".
4. Meal plan: entries of date + recipe + servings; "Generate list" for a date range uses slice 3's function; entries are marked as added so they aren't added twice.
5. Pantry staples skipped during generation; URL import (schema.org `Recipe` JSON-LD).

**Data model**
- `recipes`: created_by, name, servings, description, prep/cook minutes, tags, source URL, steps, notes, created_at
- `recipe_ingredients`: recipe, position, name, amount (NUMERIC, nullable), unit (nullable), note, section, optional
- `meal_plan_entries`: household (or user), date, recipe, servings, added_to_list
- `item_list` gains `unit` and an optional source recipe

**List generation**: one backend function: selected recipes → scale by servings → group by (name, unit) → sum → merge into existing unticked items rather than duplicating. Only g↔kg and ml↔l are converted; other unit mismatches stay as separate lines. Ingredients without an amount are added once, without a quantity. Write it test-first; unit conversion and merging is where bugs hide.

---

## Progress

- [x] Step 1: bug fixes, `CLAUDE.md`, monorepo, CI (2026-10-14)
- [x] Redeploy on Render + Neon (2026-10-15)
- [x] Step 2 (2026-10-14), except Tailwind, moved to step 3
- [ ] Step 3: households done (2026-10-17), invite link through signup fixed (2026-10-17), Capacitor project + APK workflow set up (2026-10-17), mobile UX pass (2026-10-18), first APK built in CI and tested on a phone (2026-10-18). To do: Tailwind.
- [ ] Step 4: recipes section + JSON import done (2026-10-18). To do: add to list (decimal quantities), meal plan, pantry staples, URL import.
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
- **2026-10-18 — Password reset.** "Forgot your password?" on the login page → email with a one-hour, single-use link → `/reset-password` page. `V10` adds `users.password_reset_token_hash` / `password_reset_expires_at` (only a hash of the token is stored). The forgot-password answer is the same whether or not the account exists. A reset also verifies the email, which unblocks users who never got their verification email. The duplicate-signup error now says the account exists and suggests a reset. 132 backend tests pass. Not done: logging out other devices after a reset (JWTs stay valid until they expire after 7 days).
- **2026-10-18 — Android back button.** Back went nowhere, so the app could only be closed from the app switcher. Now back goes to the previous page, and on the main pages (`/`, `/lists`, `/login`) or with no history it sends the app to the background (`App.minimizeApp`), like other Android apps. In `useNativeAppEvents`.
- **2026-10-18 — Mobile UX pass (frontend only, no API changes).**
  - **List page:** an always-visible add bar at the top. Enter adds the item and keeps the keyboard open; "3 eggs" or "eggs x3" sets the quantity. Adding an item already on the list raises its quantity, or unticks it if it was ticked. Suggestion chips come from a per-user item history on the device plus the items on other lists.
  - **Items:** a real checkbox, and the whole row is the tap target. Ticked items move to a collapsible "In the cart" section with "Clear ticked items". Edit, quantity (a stepper, validated 1–999, which fixes quantity 0 being sent) and delete are in the row's editor. Deletes are immediate, with Undo in a toast. The page shows progress ("6 of 14 left"), long names wrap instead of being cut off, and the quantity is hidden when it is 1.
  - **Sync:** the open list polls every 5 s, paused during item mutations so polling can't undo optimistic updates.
  - **Lists page:** item counts on each card. Delete moved into the list page. The create form submits on Enter and opens the new list. A hint to create a household when the user has none.
  - **App-wide:** `alert()`/`confirm()` replaced by toasts and a dialog (`useFeedback`). The TanStack Query cache is persisted to localStorage (opens instantly and offline; wiped on logout and on 401). A "server is waking up" notice after 3 s of waiting, and pending states on Login and Signup. `BackLink` replaces `navigate(-1)`. A menu icon replaces the gear, with a Lists entry and Escape to close. Real title, favicon and `theme-color`. The Vite template CSS (dark mode) is gone, inline styles are replaced by classes, touch targets are at least 44px, and hover effects are limited to `(hover: hover)`. Checkboxes and icon buttons are accessible (`aria-label`).
  - **Testing:** typecheck, lint and build pass. A Playwright walkthrough at 390×844 against a local backend covered adding, duplicates, ticking, editing, undo, clearing, suggestions, deleting a list, a second member's tick appearing within about 4 s, an offline reload and logout clearing the cache.
- **2026-10-18 — `isFavorite` removed.** It was never used in the UI (suggestions use the on-device item history), so `V11` drops `item_list.is_favorite`, and it's gone from the model, the update request and the frontend type. Older clients that still send `isFavorite` keep working because unknown keys are ignored. 132 backend tests pass.
- **2026-10-18 — Recipes and JSON import (step 4, slices 1–2).** `V12`: `recipes`, `recipe_ingredients`, `recipe_steps`, `recipe_tags`. Recipes are shared with every user; only the creator can edit or delete one (403 otherwise), and they stay when the creator deletes their account (`created_by` → NULL). API: `GET/POST /recipes`, `GET/PUT/DELETE /recipes/{id}`, `POST /recipes/import` (one recipe or an array, format in `docs/recipe-format.md`, validated all or nothing, at most 200, names the user already added are skipped). The service normalizes recipes (trims, lowercases ingredient names, units and tags; units from a fixed list). Frontend: a bottom tab bar (Lists / Recipes; hidden while typing on phones), a recipes page with search, a recipe page (servings stepper that scales amounts, sections, steps), a create/edit form where ingredients are typed one per line ("200 g flour, sifted", "Sauce:" starts a section; `utils/recipeText.ts`), and an import page (file or paste). Recipes are persisted in the query cache, so a recipe opened before can be read offline. Found while testing: the app's server `Json` doesn't encode default values, so response models must not rely on defaults (`@EncodeDefault` on `RecipeIngredient`); `RecipeAPIIT` now uses the same `Json` settings. 152 backend tests pass; a Playwright walkthrough at 390×844 covered import, re-import, errors, scaling, edit, create, search, another user's read-only view and delete.
- **2026-10-18 — Bulk delete for "Clear ticked items".** `POST /lists/{listId}/items/bulk-delete` with `{"itemIds": [...]}` returns `{"deleted": n}`. One statement scoped by `listId` (all or nothing); at most 500 IDs; IDs no longer on the list are skipped. It deletes the IDs the client showed as ticked, not "every ticked item", so an item another member ticks meanwhile isn't deleted unseen. The frontend's `useDeleteItems` uses it instead of one request per item. 138 backend tests pass, including cross-user access.
