import { http, HttpResponse } from 'msw'
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { noScoreAlignment } from '../test/fixtures'
import { renderWithProviders } from '../test/render'
import { api, problem, server } from '../test/server'
import { AlignmentPanel } from './AlignmentPanel'

describe('AlignmentPanel', () => {
  it('shows the score, a band with icon and words, and every component', async () => {
    renderWithProviders(<AlignmentPanel iso3="USA" />)
    expect(await screen.findByText('30.6')).toBeInTheDocument()
    expect(screen.getByText('Low alignment')).toBeInTheDocument()

    const meters = screen.getAllByRole('meter')
    expect(meters).toHaveLength(4)
    expect(screen.getByRole('meter', { name: 'CO2 trend sub-score' })).toHaveAttribute('aria-valuenow', '37.9')
    expect(screen.getByText(/-1\.03% per year, 2015-2024 · weight 40%/)).toBeInTheDocument()
    expect(screen.getByText(/needs 2\.28 pp\/yr to reach 100% by 2050/)).toBeInTheDocument()
  })

  it('explains why there is no score instead of showing a number', async () => {
    server.use(http.get(api('/api/countries/:iso3/alignment'), () => HttpResponse.json(noScoreAlignment)))
    renderWithProviders(<AlignmentPanel iso3="TUV" />)
    expect(await screen.findByText(/No score: .*have 2/)).toBeInTheDocument()
    expect(screen.queryByText(/alignment$/)).not.toBeInTheDocument()
    expect(screen.getByText(/Not available: need 6 of the last 10 years/)).toBeInTheDocument()
  })

  it('shows the API error and offers a retry on a server error', async () => {
    server.use(http.get(api('/api/countries/:iso3/alignment'), () => problem(500, 'INTERNAL_ERROR', 'An unexpected error occurred.')))
    renderWithProviders(<AlignmentPanel iso3="USA" />)
    expect(await screen.findByRole('alert')).toHaveTextContent('An unexpected error occurred.')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
