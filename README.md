# Pantryman

A grocery-list app for a small circle of people: me, my partner and relatives. You create shopping lists, add items, and tick them off at the store.

Lists can be shared through **households**: create one, send the invite link to your partner or family, and everyone in it sees and edits the same lists.

Planned next:

- **Mobile app**: the web app becomes a PWA and is wrapped as an Android APK with Capacitor.
- **Recipes**: pick recipes for the week and the grocery list is generated from their ingredients.

The roadmap is in [docs/improvement-plan.md](docs/improvement-plan.md).

Live at [pantryman.de](https://pantryman.de).

## Overview

| Part | Stack | Hosted on |
|------|-------|-----------|
| Backend ([`backend/`](backend/)) | Kotlin, Ktor, Exposed, Flyway, JWT auth | [Render](https://render.com) |
| Database | Postgres | [Neon](https://neon.tech) |
| Web frontend ([`app/`](app/)) | TypeScript, React, Vite, TanStack Query | [Vercel](https://vercel.com) |

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
