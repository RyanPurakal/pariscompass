import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { API_URL } from '../api/client'
import * as f from './fixtures'

/** Problem+json exactly as GlobalExceptionHandler writes it. */
export function problem(status: number, code: string, detail: string, headers: Record<string, string> = {}) {
  return HttpResponse.json({ type: 'about:blank', title: 'Error', status, detail, code }, {
    status,
    headers: { 'Content-Type': 'application/problem+json', ...headers },
  })
}

export const api = (path: string) => `${API_URL}${path}`

// Vitest runs from frontend/, and jsdom's import.meta.url is not a file: URL, so resolve from the project root.
const geometry = JSON.parse(readFileSync(resolve(process.cwd(), 'public/geo/countries-110m.json'), 'utf8'))

/** Happy-path handlers; tests override per case with server.use(...). */
export const handlers = [
  http.get('*/geo/countries-110m.json', () => HttpResponse.json(geometry)),
  http.get(api('/api/countries'), () => HttpResponse.json(f.countries)),
  http.get(api('/api/metrics'), () => HttpResponse.json(f.metrics)),
  http.get(api('/api/rankings'), () => HttpResponse.json(f.ranking)),
  http.get(api('/api/countries/:iso3'), ({ params }) =>
    params.iso3 === 'USA' ? HttpResponse.json(f.usaMetrics) : problem(404, 'COUNTRY_NOT_FOUND', `No country found with ISO3 code '${String(params.iso3)}'`),
  ),
  http.get(api('/api/countries/:iso3/series'), () => HttpResponse.json(f.usaSeries)),
  http.get(api('/api/countries/:iso3/alignment'), () => HttpResponse.json(f.usaAlignment)),
  http.get(api('/api/countries/:iso3/projections'), () => HttpResponse.json([])),
  http.post(api('/api/countries/:iso3/projection'), () => HttpResponse.json({ metrics: f.usaMetrics, projection: f.usaProjection })),
  http.get(api('/api/compare'), ({ request }) => {
    const codes = new URL(request.url).searchParams.getAll('countries')
    return HttpResponse.json({
      metric: 'co2_per_capita_t',
      unit: 't CO2/person',
      from: null,
      to: null,
      countries: codes.map((iso3) => ({
        iso3,
        name: f.countries.find((c) => c.iso3 === iso3)?.name ?? iso3,
        points: [{ year: 2023, value: 10 }, { year: 2024, value: iso3 === 'USA' ? 14.2 : 6.8 }],
      })),
    })
  }),
]

export const server = setupServer(...handlers)
