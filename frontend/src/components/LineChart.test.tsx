import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../test/render'
import { LineChart, type LineSeries } from './LineChart'

const a: LineSeries = { key: 'USA', label: 'United States', color: '#2a78d6', points: [{ year: 2023, value: 14.6 }, { year: 2024, value: 14.197 }] }
const b: LineSeries = { key: 'DEU', label: 'Germany', color: '#eb6834', points: [{ year: 2024, value: 6.769 }] }

describe('LineChart', () => {
  it('has no legend box for a single series (the title names it)', () => {
    renderWithProviders(<LineChart series={[a]} unit="t CO2/person" title="USA" />)
    expect(screen.queryByRole('list', { name: 'Legend' })).not.toBeInTheDocument()
  })

  it('has a legend for two or more series', () => {
    renderWithProviders(<LineChart series={[a, b]} unit="t CO2/person" title="Comparison" />)
    const legend = screen.getByRole('list', { name: 'Legend' })
    expect(within(legend).getAllByRole('listitem').map((i) => i.textContent)).toEqual(['United States', 'Germany'])
  })

  it('has a table twin with every value, newest year first', async () => {
    const { user } = renderWithProviders(<LineChart series={[a, b]} unit="t CO2/person" title="Comparison" />)
    await user.click(screen.getByText('Show data table'))
    const table = screen.getByRole('table', { name: 'Comparison' })
    const rows = within(table).getAllByRole('row').map((r) => r.textContent)
    expect(rows).toEqual(['YearUnited StatesGermany', '202414.2 t CO2/person6.8 t CO2/person', '202314.6 t CO2/person'])
  })

  it('says so when there is nothing to plot', () => {
    renderWithProviders(<LineChart series={[{ ...a, points: [] }]} unit="%" title="Empty" />)
    expect(screen.getByText('No data for this period.')).toBeInTheDocument()
  })
})
