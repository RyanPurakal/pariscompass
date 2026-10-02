import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ranking } from '../test/fixtures'
import { renderWithProviders } from '../test/render'
import { RankingTable } from './RankingTable'

describe('RankingTable', () => {
  const rows = () => screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell').map((c) => c.textContent))

  it('lists every country with rank and full-precision value, unit in the header', () => {
    renderWithProviders(<RankingTable rows={ranking.entries} unit="t CO2/person" caption="Ranking" />)
    expect(screen.getByRole('columnheader', { name: 't CO2/person' })).toBeInTheDocument()
    expect(rows()[0]).toEqual(['1', 'Qatar', '41.3'])
    expect(screen.getByRole('link', { name: 'India' })).toHaveAttribute('href', '/country/IND')
  })

  it('sorts by country name and reverses on a second click, announcing the order', async () => {
    const { user } = renderWithProviders(<RankingTable rows={ranking.entries} unit="t CO2/person" caption="Ranking" />)
    const nameHeader = screen.getByRole('columnheader', { name: 'Country' })

    await user.click(within(nameHeader).getByRole('button'))
    expect(nameHeader).toHaveAttribute('aria-sort', 'ascending')
    expect(rows().map((r) => r[1])).toEqual(['Germany', 'India', 'Qatar', 'United States'])

    await user.click(within(nameHeader).getByRole('button'))
    expect(nameHeader).toHaveAttribute('aria-sort', 'descending')
    expect(rows()[0]![1]).toBe('United States')
  })
})
