import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { api as url, problem, server } from '../test/server'
import { api, ApiError } from './client'

describe('api client', () => {
  it('returns typed data on success', async () => {
    const countries = await api.countries()
    expect(countries.map((c) => c.iso3)).toContain('USA')
  })

  it('turns problem+json into ApiError with the stable code', async () => {
    const error = await api.countryMetrics('XYZ').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404, code: 'COUNTRY_NOT_FOUND', message: "No country found with ISO3 code 'XYZ'" })
  })

  it('reads Retry-After on 429', async () => {
    server.use(http.post(url('/api/countries/:iso3/projection'), () => problem(429, 'RATE_LIMITED', 'Too many', { 'Retry-After': '42' })))
    const error = (await api.projection('USA').catch((e: unknown) => e)) as ApiError
    expect(error.code).toBe('RATE_LIMITED')
    expect(error.retryAfterSeconds).toBe(42)
  })

  it('reports an unreachable backend as NETWORK_ERROR', async () => {
    server.use(http.get(url('/api/metrics'), () => HttpResponse.error()))
    await expect(api.metrics()).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })

  it('sends repeated query params for lists, as Spring expects', async () => {
    let seen: string[] = []
    server.use(
      http.get(url('/api/compare'), ({ request }) => {
        seen = new URL(request.url).searchParams.getAll('countries')
        return HttpResponse.json({ metric: 'm', unit: 'u', from: null, to: null, countries: [] })
      }),
    )
    await api.compare({ countries: ['USA', 'CHN'], metric: 'co2_total_mt' })
    expect(seen).toEqual(['USA', 'CHN'])
  })
})
