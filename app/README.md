# Pantryman app

The frontend: React 19, TypeScript, Vite, react-router, TanStack Query (with its cache persisted to localStorage), axios. The same build is served at pantryman.de by Vercel and bundled into the Android app by Capacitor (`android/`).

```sh
npm install
npm run dev            # http://localhost:5173, API from VITE_API_URL (default http://localhost:8000)
npx tsc -b && npm run lint && npm run build
npm run build:android  # production API (.env.android), then cap sync; needs Node 22
```

Where things are, and the conventions, are in the root [CLAUDE.md](../CLAUDE.md).
