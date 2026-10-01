import createClient from 'openapi-fetch'
import type { components, paths } from './schema'

/** Response shapes, generated from the backend's OpenAPI spec (npm run api:types). */
export type Schemas = components['schemas']
export type CountryInfo = Schemas['CountryInfo']
export type CountryMetrics = Schemas['CountryMetrics']
export type MetricInfo = Schemas['MetricInfo']
export type CountrySeriesResponse = Schemas['CountrySeriesResponse']
export type MetricSeries = Schemas['MetricSeries']
export type SeriesPoint = Schemas['SeriesPoint']
export type ComparisonResponse = Schemas['ComparisonResponse']
export type RankingResponse = Schemas['RankingResponse']
export type RankingEntry = Schemas['Entry']
export type AlignmentResponse = Schemas['AlignmentResponse']
export type AlignmentComponent = Schemas['Component']
export type ProjectionResponse = Schemas['ProjectionResponse']
export type CountryProjectionResponse = Schemas['CountryProjectionResponse']
export type AlignmentBand = NonNullable<AlignmentResponse['band']>

export const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8081').replace(/\/+$/, '')

const client = createClient<paths>({ baseUrl: API_URL })

/**
 * Every failure the UI can show. The backend sends RFC 9457 problem+json with a stable `code`;
 * network failures become status 0 / NETWORK_ERROR.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly retryAfterSeconds: number | null

  constructor(status: number, code: string, message: string, retryAfterSeconds: number | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.retryAfterSeconds = retryAfterSeconds
  }
}

interface Problem {
  code?: string
  detail?: string
  title?: string
}

async function call<T>(request: () => Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  let result
  try {
    result = await request()
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the Paris Compass API. Is the backend running?')
  }
  const { data, error, response } = result
  if (response.ok && data !== undefined) return data
  const problem = (error ?? {}) as Problem
  const retryAfter = response.headers.get('Retry-After')
  throw new ApiError(
    response.status,
    problem.code ?? `HTTP_${response.status}`,
    problem.detail ?? problem.title ?? response.statusText,
    retryAfter ? Number(retryAfter) : null,
  )
}

export const api = {
  countries: (signal?: AbortSignal) => call(() => client.GET('/api/countries', { signal })),

  metrics: (signal?: AbortSignal) => call(() => client.GET('/api/metrics', { signal })),

  countryMetrics: (iso3: string, signal?: AbortSignal) =>
    call(() => client.GET('/api/countries/{iso3}', { params: { path: { iso3 } }, signal })),

  series: (iso3: string, query: { metrics?: string[]; from?: number; to?: number }, signal?: AbortSignal) =>
    call(() => client.GET('/api/countries/{iso3}/series', { params: { path: { iso3 }, query }, signal })),

  alignment: (iso3: string, signal?: AbortSignal) =>
    call(() => client.GET('/api/countries/{iso3}/alignment', { params: { path: { iso3 } }, signal })),

  rankings: (query: { metric: string; year?: number; order?: 'asc' | 'desc'; limit?: number }, signal?: AbortSignal) =>
    call(() => client.GET('/api/rankings', { params: { query }, signal })),

  compare: (query: { countries: string[]; metric: string; from?: number; to?: number }, signal?: AbortSignal) =>
    call(() => client.GET('/api/compare', { params: { query }, signal })),

  projection: (iso3: string) =>
    call(() => client.POST('/api/countries/{iso3}/projection', { params: { path: { iso3 } } })),

  projections: (iso3: string, limit: number, signal?: AbortSignal) =>
    call(() =>
      client.GET('/api/countries/{iso3}/projections', { params: { path: { iso3 }, query: { limit } }, signal }),
    ),
}
