import { http, HttpResponse } from 'msw'
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fallbackProjection, usaMetrics, usaProjection, usaSeries } from '../test/fixtures'
import { renderWithProviders } from '../test/render'
import { api, problem, server } from '../test/server'
import { ProjectionPanel } from './ProjectionPanel'

const observed = usaSeries.series.find((s) => s.metric === 'co2_total_mt')!.points

describe('ProjectionPanel', () => {
  it('waits for the user, then shows the AI projection with its provenance', async () => {
    const { user } = renderWithProviders(<ProjectionPanel iso3="USA" name="United States" observed={observed} />)
    expect(await screen.findByText(/No projection yet/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Generate projection' }))
    expect(await screen.findByText('AI projection · gemini-2.5-flash')).toBeInTheDocument()
    expect(screen.getByText(usaProjection.projection.summary)).toBeInTheDocument()
    expect(screen.getByText('Rising low-carbon share of electricity')).toBeInTheDocument()
    expect(screen.getByText(/2029: 4,654 Mt CO2 vs 4,904 Mt CO2 in 2024 \(decreasing\)/)).toBeInTheDocument()
  })

  it('shows the newest stored projection right away, labeled when it is the statistical fallback', async () => {
    server.use(http.get(api('/api/countries/:iso3/projections'), () => HttpResponse.json([fallbackProjection])))
    renderWithProviders(<ProjectionPanel iso3="USA" name="United States" observed={observed} />)
    expect(await screen.findByText('Statistical fallback · trend extrapolation')).toBeInTheDocument()
    expect(screen.getByText(/AI model not configured/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Regenerate' })).toBeInTheDocument()
  })

  it('tells the user how long to wait when rate limited', async () => {
    server.use(http.post(api('/api/countries/:iso3/projection'), () => problem(429, 'RATE_LIMITED', 'Too many', { 'Retry-After': '42' })))
    const { user } = renderWithProviders(<ProjectionPanel iso3="USA" name="United States" observed={observed} />)
    await user.click(await screen.findByRole('button', { name: 'Generate projection' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Try again in 42 seconds')
  })

  it('marks a cached result', async () => {
    server.use(http.post(api('/api/countries/:iso3/projection'), () =>
      HttpResponse.json({ metrics: usaMetrics, projection: { ...usaProjection, cached: true } })))
    const { user } = renderWithProviders(<ProjectionPanel iso3="USA" name="United States" observed={observed} />)
    await user.click(await screen.findByRole('button', { name: 'Generate projection' }))
    expect(await screen.findByText(/served from cache/)).toBeInTheDocument()
  })
})
