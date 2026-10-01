# frontend — React SPA

Single-page application built with React 19 and Vite. Displays an interactive world map and shows per-country climate metrics with AI projections.

## Stack

- **React 19** — component model and state
- **Vite** — dev server (port 5173) and bundler
- **Leaflet / React Leaflet** — interactive tile map
- **Fetch API** — all backend calls (no HTTP client library)

## Directory layout

```
frontend/
├── index.html              # HTML shell that Vite injects the bundle into
├── vite.config.js          # Vite config (no proxy — backend URL is hardcoded in App.jsx)
├── eslint.config.js        # Lint rules
├── package.json
└── src/
    ├── main.jsx            # React entry point — mounts <App /> into #root
    ├── App.jsx             # Entire UI: map, country sidebar, data panel, components
    ├── countryCoordinates.js # Static ISO3 → [lat, lng] lookup table
    ├── App.css             # Component-scoped styles (CSS variables, layout, cards)
    └── index.css           # Global reset and base styles
```

## Running locally

```bash
npm install
npm run dev   # http://localhost:5173
```

Backend must be running on `http://localhost:8081` (see root README).

## Key design notes

- All UI state lives in the single `App` component (`useState`). There is intentionally no state management library.
- The backend URL comes from `VITE_API_BASE_URL` (see `.env.example`), defaulting to `http://localhost:8081/api`.
- Country markers on the map are driven by `countryCoordinates.js`. Countries absent from that file will appear in the sidebar list but will have no map marker.
- The Leaflet default marker icon path is patched at module load time (Vite doesn't bundle assets the same way Webpack does).
