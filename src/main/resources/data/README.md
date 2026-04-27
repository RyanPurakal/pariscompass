# data — CSV Datasets

Static data files loaded into memory at application startup by `DataLoader`.

## Files

| File | Source | Columns | Notes |
|------|--------|---------|-------|
| `countries.csv` | — | `iso3, name` | Master list of supported countries |
| `co2_data.csv` | Our World in Data | `name, iso3, year, co2_total_mt, co2_per_capita` | Total CO₂ (million tonnes) + per-capita |
| `renewables_data.csv` | OWID | `iso3, year, renewables_share_pct` | Renewable energy as % of total electricity |
| `temperature_data.csv` | Berkeley Earth | `iso3, year, temp_anomaly_c` | Temperature anomaly vs. pre-industrial baseline |

## How data is used

`DataLoader` reads all four files once on startup and stores them in nested `HashMap<iso3, HashMap<year, record>>` structures. `CountryMetricsService` queries these maps to build a `CountryMetrics` object for any given ISO3 code, selecting the latest available year across all datasets.

## Adding a new country

1. Add a row to `countries.csv` with the country's ISO3 code and display name.
2. Add data rows to each of `co2_data.csv`, `renewables_data.csv`, and `temperature_data.csv`.
3. Add the ISO3 → coordinates entry to `frontend/src/countryCoordinates.js` so the map marker appears.

Restart the backend after any CSV changes (data is loaded at startup only).
