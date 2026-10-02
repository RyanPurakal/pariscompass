import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { expectNoAxeViolations } from '../test/axe'
import { renderWithProviders } from '../test/render'
import CountryPage from './CountryPage'

const renderCountry = (iso3: string) => renderWithProviders(<CountryPage />, { route: `/c/${iso3}`, path: '/c/:iso3' })

describe('CountryPage', () => {
  it('shows latest values with the year of each', async () => {
    renderCountry('usa')
    expect(await screen.findByRole('heading', { level: 1, name: /United States/ })).toBeInTheDocument()
    expect(screen.getByText('14.2 t CO2/person')).toBeInTheDocument()
    expect(screen.getByText('+0.83 °C')).toBeInTheDocument()
    expect(screen.getByText('25.6%').parentElement).toHaveTextContent('2025')
    expect(await screen.findByText('30.6')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Trends' })).toBeInTheDocument()
  })

  it('shows a not-found state for an unknown code', async () => {
    renderCountry('XYZ')
    expect(await screen.findByText('No country with code XYZ')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the atlas' })).toBeInTheDocument()
  })

  it('has no axe violations', async () => {
    const { container } = renderCountry('USA')
    await screen.findByText('30.6')
    await screen.findByRole('heading', { name: 'Trends' })
    await screen.findByText(/No projection yet/)
    await expectNoAxeViolations(container)
  })
})
