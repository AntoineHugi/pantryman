# Pantryman

A grocery-list app for a small circle of people: me, my partner and relatives. You create shopping lists, add items, and tick them off at the store.

- **Quick adding:** type "2 milk" and press Enter; the keyboard stays open for the next item. Items you've added before show up as suggestions, and adding something already on the list updates it instead of duplicating it.
- **At the store:** tap an item to tick it; ticked items move to an "In the cart" section that can be cleared in one go. Deleting can be undone.
- **Sharing through households:** create one, send the invite link to your partner or family, and everyone in it sees and edits the same lists. An open list refreshes every few seconds, so you can shop together.
- **Android app:** the web app wrapped with Capacitor (`app/android/`). It opens on the last-known lists, even offline.

Planned next: **recipes**. Pick recipes for the week and the grocery list is generated from their ingredients.

The roadmap is in [docs/improvement-plan.md](docs/improvement-plan.md).

Live at [pantryman.de](https://pantryman.de).

## Overview

| Part | Stack | Hosted on |
|------|-------|-----------|
| Backend ([`backend/`](backend/)) | Kotlin, Ktor, Exposed, Flyway, JWT auth | [Render](https://render.com) |
| Database | Postgres | [Neon](https://neon.tech) |
| Web frontend ([`app/`](app/)) | TypeScript, React, Vite, TanStack Query | [Vercel](https://vercel.com) |
| Android app ([`app/android/`](app/android/)) | Capacitor, bundling the web frontend | Signed APK built by GitHub Actions |

Render and Vercel both deploy automatically on every push to `main`. Flyway applies database migrations when the backend starts.

## Running locally

Backend (from `backend/`, needs Docker and a `.env` with at least `JWT_SECRET`):

```sh
docker compose up -d postgres
set -a; source .env; set +a; ./gradlew run   # http://localhost:8000
```

Frontend (from `app/`):

```sh
npm install
npm run dev   # http://localhost:5173
```

Developer notes and conventions are in [CLAUDE.md](CLAUDE.md).
