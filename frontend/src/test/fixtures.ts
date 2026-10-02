import type { Schemas } from '../api/client'

/**
 * API fixtures typed by the generated schema: if the backend contract changes, these stop compiling.
 * Values are taken from the real database (Phase 1 load) so components see realistic data.
 */
export const countries: Schemas['CountryInfo'][] = [
  { iso3: 'DEU', name: 'Germany' },
  { iso3: 'IND', name: 'India' },
  { iso3: 'QAT', name: 'Qatar' },
  { iso3: 'USA', name: 'United States' },
]

const coverage = (countries: number, firstYear: number, lastYear: number, defaultYear: number) => ({
  values: countries * (lastYear - firstYear + 1),
  countries,
  firstYear,
  lastYear,
  defaultYear,
})
const owidCo2 = { code: 'OWID_CO2', name: 'Our World in Data: CO2 and Greenhouse Gas Emissions', homepage: 'https://github.com/owid/co2-data', citation: 'GCP' }

export const metrics: Schemas['MetricInfo'][] = [
  { code: 'co2_per_capita_t', name: 'CO2 emissions per capita', unit: 't CO2/person', description: 'Annual territorial CO2 emissions divided by population.', source: owidCo2, coverage: coverage(213, 1750, 2024, 2024) },
  { code: 'co2_total_mt', name: 'CO2 emissions', unit: 'Mt CO2', description: 'Annual territorial CO2 emissions.', source: owidCo2, coverage: coverage(215, 1750, 2024, 2024) },
  {
    code: 'temperature_anomaly_c',
    name: 'Temperature anomaly',
    unit: '°C',
    description: 'Annual mean surface air temperature minus the 1991-2020 mean (Copernicus ERA5).',
    source: { code: 'OWID_TEMPERATURE', name: 'Our World in Data: Annual temperature anomalies (Copernicus ERA5)', homepage: 'https://ourworldindata.org', citation: 'C3S' },
    coverage: coverage(193, 1940, 2025, 2025),
  },
]

export const ranking: Schemas['RankingResponse'] = {
  metric: 'co2_per_capita_t',
  unit: 't CO2/person',
  year: 2024,
  order: 'desc',
  countriesWithData: 4,
  entries: [
    { rank: 1, iso3: 'QAT', name: 'Qatar', value: 41.271 },
    { rank: 2, iso3: 'USA', name: 'United States', value: 14.197 },
    { rank: 3, iso3: 'DEU', name: 'Germany', value: 6.769 },
    { rank: 4, iso3: 'IND', name: 'India', value: 2.201 },
  ],
}

export const usaMetrics: Schemas['CountryMetrics'] = {
  iso3: 'USA',
  name: 'United States',
  co2PerCapita: 14.197,
  co2TotalMt: 4904.12,
  temperatureAnomalyC: 0.83337736,
  renewablesSharePct: 25.638,
  years: { co2PerCapita: 2024, co2TotalMt: 2024, temperatureAnomalyC: 2025, renewablesSharePct: 2025 },
  source: { co2: owidCo2.name, temp: 'Copernicus ERA5', renewables: 'Our World in Data: Energy' },
}

export const usaAlignment: Schemas['AlignmentResponse'] = {
  iso3: 'USA',
  name: 'United States',
  formulaVersion: 'v1',
  score: 30.6,
  band: 'LOW',
  reason: null,
  disclaimer: 'Indicator built from historical emissions and electricity data only.',
  components: [
    { key: 'emissions_trend', weight: 0.4, effectiveWeight: 0.4, available: true, metric: 'co2_total_mt', value: -1.03, unit: '%/yr', fromYear: 2015, toYear: 2024, points: 10, subScore: 37.9, note: null },
    { key: 'emissions_level', weight: 0.25, effectiveWeight: 0.25, available: true, metric: 'co2_per_capita_t', value: 14.197, unit: 't CO2/person', fromYear: 2024, toYear: 2024, points: 1, subScore: 6.2, note: null },
    { key: 'clean_power_level', weight: 0.2, effectiveWeight: 0.2, available: true, metric: 'low_carbon_share_elec_pct', value: 43.002, unit: '%', fromYear: 2025, toYear: 2025, points: 1, subScore: 43.0, note: null },
    { key: 'clean_power_momentum', weight: 0.15, effectiveWeight: 0.15, available: true, metric: 'low_carbon_share_elec_pct', value: 0.803, unit: 'pp/yr', fromYear: 2016, toYear: 2025, points: 10, subScore: 35.2, note: 'needs 2.28 pp/yr to reach 100% by 2050' },
  ],
}

export const noScoreAlignment: Schemas['AlignmentResponse'] = {
  ...usaAlignment,
  iso3: 'TUV',
  name: 'Tuvalu',
  score: null,
  band: null,
  reason: 'No score: emissions trend unavailable (need 6 of the last 10 years, have 2)',
  components: usaAlignment.components.map((c) =>
    c.key === 'emissions_trend'
      ? { ...c, available: false, value: null, subScore: null, effectiveWeight: null, fromYear: null, toYear: null, points: 2, note: 'need 6 of the last 10 years, have 2' }
      : c,
  ),
}

const years = (from: number, to: number, f: (y: number) => number) =>
  Array.from({ length: to - from + 1 }, (_, i) => ({ year: from + i, value: f(from + i) }))

export const usaSeries: Schemas['CountrySeriesResponse'] = {
  iso3: 'USA',
  name: 'United States',
  from: null,
  to: null,
  series: [
    { metric: 'co2_total_mt', unit: 'Mt CO2', points: years(2000, 2024, (y) => 6000 - (y - 2000) * 45) },
    { metric: 'co2_per_capita_t', unit: 't CO2/person', points: years(2000, 2024, (y) => 21 - (y - 2000) * 0.28) },
    { metric: 'low_carbon_share_elec_pct', unit: '%', points: years(2000, 2025, (y) => 29 + (y - 2000) * 0.55) },
    { metric: 'renewables_share_elec_pct', unit: '%', points: years(2000, 2025, (y) => 9 + (y - 2000) * 0.65) },
    { metric: 'temperature_anomaly_c', unit: '°C', points: years(2000, 2025, (y) => ((y % 5) - 2) * 0.3) },
  ],
}

export const usaProjection: Schemas['ProjectionResponse'] = {
  id: 1,
  iso3: 'USA',
  country: 'United States',
  generatedBy: 'model',
  model: 'gemini-2.5-flash',
  promptVersion: 'p1',
  status: 'VALID',
  attempts: 1,
  fallbackReason: null,
  validationErrors: [],
  generatedAt: '2026-10-01T20:20:00Z',
  cached: false,
  latencyMs: 16829,
  baseYear: 2024,
  baseYearCo2Mt: 4904.12,
  alignmentScore: 30.6,
  alignmentBand: 'LOW',
  projection: {
    summary: 'Emissions have declined since 2005 and are projected to keep falling slowly.',
    co2Direction: 'decreasing',
    projectedCo2Mt: [2025, 2026, 2027, 2028, 2029].map((year, i) => ({ year, co2Mt: 4854.12 - i * 50 })),
    keyDrivers: ['Rising low-carbon share of electricity'],
    risks: ['Policy reversal'],
    confidence: 'medium',
  },
}

export const fallbackProjection: Schemas['ProjectionResponse'] = {
  ...usaProjection,
  id: 2,
  generatedBy: 'trend-extrapolation',
  model: null,
  status: 'FALLBACK',
  attempts: 0,
  fallbackReason: 'AI model not configured',
  projection: { ...usaProjection.projection, summary: 'Statistical fallback, not an AI projection.', confidence: 'low' },
}
