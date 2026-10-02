import { http, HttpResponse } from 'msw'
import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { expectNoAxeViolations } from '../test/axe'
import { ranking } from '../test/fixtures'
import { renderWithProviders } from '../test/render'
import { api, server } from '../test/server'
import { ExplorePage } from './ExplorePage'

describe('ExplorePage', () => {
  it('shows the metric, its default year, the map and the ranking', async () => {
    renderWithProviders(<ExplorePage />)
    expect(await screen.findByRole('heading', { level: 1, name: 'CO2 emissions per capita, 2024' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Qatar' })).toBeInTheDocument()
    expect(screen.getByRole('slider', { name: /Year/ })).toHaveValue('2024')
    expect(screen.getByRole('combobox', { name: 'Metric' })).toHaveValue('co2_per_capita_t')
    expect(await screen.findByRole('group', { name: /World map/ })).toBeInTheDocument()
  })

  it('never requests a placeholder year while the metric catalog loads', async () => {
    const years: (string | null)[] = []
    server.use(
      http.get(api('/api/rankings'), ({ request }) => {
        years.push(new URL(request.url).searchParams.get('year'))
        return HttpResponse.json(ranking)
      }),
    )
    renderWithProviders(<ExplorePage />)
    await screen.findByRole('link', { name: 'Qatar' })
    await waitFor(() => expect(years).toEqual(['2024']))
  })

  it('reads the metric and year from the URL', async () => {
    let requested = ''
    server.use(
      http.get(api('/api/rankings'), ({ request }) => {
        requested = new URL(request.url).search
        return HttpResponse.json({ ...ranking, metric: 'co2_total_mt', unit: 'Mt CO2', year: 1990 })
      }),
    )
    renderWithProviders(<ExplorePage />, { route: '/?metric=co2_total_mt&year=1990' })
    expect(await screen.findByRole('heading', { level: 1, name: 'CO2 emissions, 1990' })).toBeInTheDocument()
    expect(requested).toContain('metric=co2_total_mt')
    expect(requested).toContain('year=1990')
  })

  it('has no axe violations', async () => {
    const { container } = renderWithProviders(<ExplorePage />)
    await screen.findByRole('link', { name: 'Qatar' })
    await screen.findByRole('group', { name: /World map/ })
    await expectNoAxeViolations(container)
  })
})
