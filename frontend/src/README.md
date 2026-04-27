# src — Frontend Source

## Files

### `main.jsx`
React entry point. Mounts `<App />` inside a `StrictMode` wrapper onto the `#root` div in `index.html`. No application logic here.

### `App.jsx`
The entire frontend lives here. Contains:

- **Helper components** (defined before `App`, not in separate files):
  - `MapCenter` — syncs the Leaflet map view to the selected country
  - `LoadingSkeleton` — animated placeholder shown while data loads
  - `MetricCard` — single climate metric with icon and color-coded status
- **Pure helper functions** (module-level, not React components):
  - `getMetricStatus(metricType, value)` — maps numeric thresholds to `'good'|'warning'|'danger'`
  - `extractRiskLevel(text)` — parses Gemini projection text for Low/Medium/High risk label
  - `formatNumber(num)` — abbreviates large numbers (e.g. 4713 → "4.7K")
- **`App` component** — manages all state, fetches data, renders the three-panel layout (nav sidebar | map | data panel)

### `countryCoordinates.js`
Static lookup table: ISO3 code → `[latitude, longitude]` for the approximate country center. Used by `App.jsx` to place map markers and pan the map when a country is selected.

### `App.css`
All component-level styles using CSS custom properties (`--bg-primary`, `--text-secondary`, etc.) for the dark theme. Covers layout, metric cards, projection card, skeleton animation, and the risk-level badge colors.

### `index.css`
Global reset and base styles (box-sizing, font stack, body background). Applied before `App.css`.

## Data flow in the UI

```
User selects country (sidebar click or map marker click)
        │
        ▼
handleCountrySelect(iso3)
        │
        ├── pan map to countryCoordinates[iso3]
        └── POST /api/country/{iso3}/projection
                │
                ├── setMetrics(data.metrics)    → MetricCard grid
                └── setProjection(data.projection) → projection card
                                                       + extractRiskLevel()
```
