# Paris Compass frontend

React 19 + TypeScript (strict) + Vite. Three views, all state in the URL so any view can be shared:

| Route | View |
|-------|------|
| `/?metric=&year=` | Atlas: choropleth world map for one metric and year, with its ranking table |
| `/country/:iso3` | Country profile: latest values, alignment score breakdown, five-year projection, trend charts |
| `/compare?countries=USA,CHN&metric=&since=` | 2 to 4 countries on one metric |

## Running

```bash
npm install
cp .env.example .env.local     # VITE_API_URL, the backend origin (default http://localhost:8081)
npm run dev                    # http://localhost:5173 (the backend's default CORS origin)
```

| Script | Does |
|--------|------|
| `npm run build` | Typecheck (`tsc -b`) then production build |
| `npm run lint` | ESLint with typescript-eslint and the React Hooks rules |
| `npm run api:spec` | Fetch the OpenAPI spec from a running backend into `openapi.json` (`API_DOCS_URL` overrides the URL) |
| `npm run api:types` | Regenerate `src/api/schema.d.ts` from `openapi.json` |
| `npm run geo` | Rebuild `public/geo/countries-110m.json` from Natural Earth |

## Structure

```
src/
├── api/           client.ts (typed openapi-fetch client, ApiError) + schema.d.ts (generated, do not edit)
├── hooks/         queries.ts: every TanStack Query hook and query key; useDebouncedValue
├── lib/           palette (validated chart colors), scales (map classes), format, theme
├── components/    AppShell, WorldMap, MapLegend, RankingTable, LineChart (+ ChartTable), AlignmentPanel,
│                  ProjectionPanel, StatTile, CountrySearch (combobox), Filters, ThemeToggle, States
├── pages/         ExplorePage, CountryPage (lazy), ComparePage (lazy), NotFoundPage
└── styles/        tokens.css (light/dark design tokens), global.css
```

## Design decisions

- **Types come from the backend.** `schema.d.ts` is generated from the springdoc OpenAPI spec, and `openapi-fetch` checks every path, parameter and response at compile time. The backend marks every field required and only genuinely nullable fields `nullable`, so types read `score: number | null` rather than optional everywhere.
- **TanStack Query owns server state.** Query keys live in one file. Reference data (countries, metrics, map shapes) stays fresh for an hour. Map and chart refetches keep the previous render, dimmed, instead of flashing a skeleton. 4xx responses are not retried. Projections are a mutation: never generated automatically, because they may call a paid, rate-limited model.
- **Errors show the backend's problem `code`.** `ApiError` carries status, code and `Retry-After`; a 429 shows the wait time.
- **The map is SVG with d3-geo, on the Equal Earth projection.** No tile server is needed for a choropleth, and an equal-area projection keeps visual weight proportional to land area (Mercator inflates high latitudes). Shapes are Natural Earth 1:110m, prebuilt to 90 KB of TopoJSON (28.6 KB gzipped) by `scripts/build-geo.mjs`. ISO codes come from `ISO_A3_EH`, because `ISO_A3` is `-99` for France and Norway.
- **Map classes.** Seven classes at most. Quantile classes for skewed metrics (China emits about 12,000 Mt and most countries under 100, so equal intervals would paint almost every country the same). Temperature anomalies use a diverging blue-gray-red scale centered on zero.
- **Chart colors are validated, not eyeballed.** The four categorical slots pass the dataviz palette validator in both themes (worst adjacent color-blind separation dE 9.1 light, 8.4 dark). Two light-mode slots are under 3:1 contrast, so every multi-series chart also has a legend, direct end labels and a table view. A country keeps its color when others are removed from a comparison.
- **Accessibility.**
  - Skip link, landmarks, visible focus, and text colors checked for at least 4.5:1 contrast.
  - The country search is an ARIA combobox.
  - The map is one tab stop: arrow keys move between countries, Enter opens one, and the focused country gets the same outline and tooltip as hover. The ranking table beside it carries the same data.
  - Every chart has a data table, and the alignment band pairs color with an icon and a word.
  - Motion respects `prefers-reduced-motion`.
- **Bundle.** Recharts only loads on the country and compare pages (route-level code splitting). Fonts are self-hosted with `@fontsource`; Newsreader uses its weight-only variable file (58 KB) instead of the optical-size one (132 KB).
