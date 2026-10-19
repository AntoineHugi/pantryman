# Pantryman

Personal grocery-list app for me, my partner and relatives. Started as a hand-written learning project; it is now developed primarily with AI assistance, and the priority is shipping features quickly without breaking things.

## Product direction

1. **Mobile app (APK)** for family members. Plan: wrap the web app with Capacitor (bundled assets, no PWA step). Not a separate native rewrite.
2. **Sharing via households.** Done (2026-10-17). A list or a meal plan is personal or shared with one of the user's households. Recipes are shared with every user.
3. **Recipes → grocery list.** Done (2026-10-18), except URL import. A shared recipe database; recipes go on a list one at a time (recipe page) or from a meal plan of lunches and dinners, scaled by servings and merged by ingredient + unit.

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

- `api/`: axios client and one module per resource (`lists.ts`, `items.ts`, `households.ts`, `recipes.ts`, `mealPlans.ts`, `auth.ts`), plus `types.ts`. Pages call these modules and never call axios directly.
- `hooks/useGroceryLists.ts`, `hooks/useHouseholds.ts`, `hooks/useRecipes.ts`, `hooks/useMealPlans.ts`: TanStack Query hooks (queries + mutations, optimistic item updates). Pages use these for server data instead of `useEffect` + `useState`.
- `queryClient.ts`: the query client. Its cache is persisted to localStorage (lists, households, recipes, meal plans and usual items only), so the app opens on the last-known data, even offline. `clearQueryCache()` wipes it on logout and on a 401. It also copies each list's detail query into the `['lists']` query, so the item counts on the lists page stay current. After a week without opening the app, the saved cache is dropped (`maxAge`).
- `pages/`, `components/`, `contexts/AuthContext.tsx` (token in localStorage; the context object is in `AuthContextValue.ts`), `hooks/`.
- `contexts/FeedbackContext.tsx` + `hooks/useFeedback.ts`: `toast()` (with an optional action such as Undo) and `confirm()` (a promise-based dialog).
- The list page: `components/AddItemBar.tsx` (always-visible input; "3 eggs" or "500 g flour" sets the quantity and unit, duplicates update the existing item, suggestion chips) and `components/ItemRow.tsx` (tap the row to tick; edit, quantity and delete are in its editor). Parsing and validation are in `utils/itemInput.ts`. Suggestions come from `hooks/useItemSuggestions.ts`, using an item history kept on the device per user (`utils/itemHistory.ts`) plus the items on the user's other lists.
- Recipes: `pages/Recipe*Page.tsx`. The edit form takes ingredients as text, one per line, parsed and formatted by `utils/recipeText.ts`. Import takes JSON in the format of `docs/recipe-format.md`. The main sections (Lists / Recipes / Plan) are in the bottom tab bar (`components/layout/TabBar.tsx`).
- Usual items: `pages/PantryPage.tsx` (in the header menu), `hooks/usePantry.ts`.
- Meal plans: `pages/MealPlansPage.tsx`, `pages/MealPlanPage.tsx` (a card per day; Move is tap the meal, then tap the target slot), `components/RecipePicker.tsx`. Plan dates are `YYYY-MM-DD` strings handled by `utils/dates.ts` as local dates.
- Styling: plain CSS. Color tokens are in `index.css` (`:root`) and the components in `App.css`. Touch targets are at least 44px, and hover styles go inside `@media (hover: hover)`. No Tailwind (decided 2026-10-18).

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
- **App updates:** the app checks GitHub's latest release at start and every 6 hours (`src/appUpdate.ts`, `components/UpdateBanner.tsx`) and offers it when the APK's version code (the number at the end of `pantryman-<name>-<code>.apk`) is higher than its own. Update downloads the APK and opens Android's install screen (`AppUpdatePlugin.java`, `REQUEST_INSTALL_PACKAGES`); the first time, the user must allow Pantryman to install apps. Only tagged builds (`git tag v1.3.0 && git push origin v1.3.0`) make a release. The check only works while the repo is public.
- In the app, the page runs at `https://localhost`. Links meant for other people must use `publicAppUrl` (`src/native.ts`), not `window.location.origin`. pantryman.de links under `/households`, `/login`, `/verify` and `/reset-password` open the app (App Links, `AndroidManifest.xml`) and are routed by `hooks/useNativeAppEvents.ts`.

## Conventions and gotchas

- **Authorization:** every route under `authenticate("auth-jwt")` gets the user from `call.userId` (the JWT claim) and never from the request body. Any query on a child resource must be scoped by its parent. For example, item queries always filter by `listId` as well as `itemId`, after the route has checked that the user can access the list. Add a test for cross-user access whenever you add an endpoint.
- **Households:** a user can access a list (or a meal plan, same rule in `MealPlanRepository`) if they own it (`grocery_lists.user_id`) or are a member of its household (`grocery_lists.household_id`, NULL = personal). `GroceryListRepository.accessibleBy()` is that rule; scope list queries with it. Any member can rename, edit or delete a shared list; only the owner can change who it's shared with. Members join with an invite code (`/households?join=CODE` link), valid for 7 days (`HouseholdService.INVITE_CODE_VALIDITY`); a member who loads the household after that gets a new code automatically. The household's owner (`households.owner_id`, initially the creator) can rename it and remove members; removing a member also replaces the invite code. When a member leaves, is removed or deletes their account, their shared lists and meal plans pass to the owner (or, if the owner is leaving, to the longest-standing remaining member, who becomes the owner); when the last member leaves, the household is deleted and its lists become personal (`HouseholdRepository.removeMember`).
- **Migrations:** never edit an applied migration; add a new `V<n+1>__...sql`. Update the matching Exposed table object at the same time. Most tests build their schema from the Exposed objects (`SchemaUtils.create`), not from the migrations. `MigrationTest` is the exception: it runs Flyway and checks that the columns and nullability match. Keep it passing, and add new tables to its list.
- **Ordering:** Postgres has no stable row order. Queries that return lists must `orderBy` (currently `createdAt`, then `id`).
- **Emails** are stored trimmed and lowercase, and looked up case-insensitively.
- **JSON:** the server ignores unknown keys, so older installed clients keep working when request fields are removed. Prefer additive API changes, because mobile clients will lag behind deploys.
- **Errors:** routes don't catch exceptions. They throw, and `plugins/StatusPages.kt` maps them (`IllegalArgumentException` → 400, `ForbiddenException` → 403, Ktor `NotFoundException` → 404, …). Parse path IDs with `call.uuidParameter("listId")`. Responses use `{"error": "message"}`. The frontend reads them with `getErrorMessage()` in `api/client.ts`. The axios 401 interceptor logs the user out, except on `/auth/*` endpoints, which return 401 for wrong credentials.
- **Auth endpoints** (signup, login, resend-verification, forgot-password, reset-password) are rate limited per client IP (`plugins/RateLimiting.kt`).
- **Password reset:** `POST /auth/forgot-password` emails a link to `/reset-password?token=...` and answers the same whether or not the account exists. Only a SHA-256 hash of the token is stored (`users.password_reset_token_hash`), valid 1 hour, single use, latest link only. A successful reset also verifies the email. Existing logins (JWTs) stay valid after a reset.
- Ktor YAML env syntax is `${VAR}` (required) or `${VAR:default}`.
- **Frontend UI:** never use `alert()`/`confirm()`, which look out of place in the Android WebView. Use `useFeedback()`. Prefer Undo in a toast over a confirmation for reversible deletes. Back buttons are `<BackLink>` (a link to `/lists`), not `navigate(-1)`, which goes nowhere after a deep link or a reload. Wrap anything that waits on the server in `<SlowServerNotice active={...}>`: the Render backend can take ~50 s to wake.
- **Items** have a decimal `quantity` and an optional `unit` (null = a count). Recipes are added to a list by `POST /lists/{listId}/recipes`; the merge rules are in `services/ListMerge.kt` (pure, unit-tested in `ListMergeTest`). Item `PATCH` reads a missing `unit` as unchanged and `""` as no unit.
- **Usual items** (`pantry_items`, `PantryService`): per household, or per user for personal lists. `default` items go on new lists (the create form sends the chosen `defaultItemIds`; omitted means all). `staple` items are skipped when recipes or meal plans add to a list, unless the user ticks them. A list uses its household's items, or its owner's if personal.
- **Meal plans:** confirming a plan creates the list "Meal plan - 20.10/26.10" (`MealPlanService.listName`) and stores its ID in `meal_plans.list_id`. The plan isn't synced with the list afterwards.
- **Recipes** are shared with every user: reads aren't scoped, but only the creator (`recipes.created_by`) can edit or delete one. The unit list is in `RecipeService.UNITS`, `utils/recipeText.ts` and `docs/recipe-format.md`; keep the three in step. The `recipe-import` Claude skill (an account skill, not in this repo) bundles a copy of `docs/recipe-format.md`; when the format changes, tell me so I can update the skill.
- **JSON defaults:** the server's `Json` doesn't encode default values, so a response field with a default is left out when it equals the default. Give response models no defaults, or mark fields `@EncodeDefault`.
- **Item changes work offline.** Every change to a list's items is an `ItemChange` object (`useGroceryLists.ts`), sent by the mutation defaults registered for `['items']` in `main.tsx` (`itemMutationDefaults`): applied to the cache at once, sent one at a time in order (`scope`), and paused when a request gets no answer (`network.ts` marks the app offline and checks the server every 10 s). Paused changes are saved with the cache and sent after the next start. New items get their UUID on the device (`CreateItemRequest.id`; a retry with the same ID returns the existing item), so later offline changes can refer to them. Keep each `ItemChange` serializable and safe to send twice, and add any new item mutation as a new `ItemChange` type. Other mutations fail at once offline (`networkMode: 'always'`) with a "no connection" message. The open list polls every 5 s so that changes from other household members show up, paused while item changes are pending.
