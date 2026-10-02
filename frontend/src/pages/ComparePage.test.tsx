import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { expectNoAxeViolations } from '../test/axe'
import { renderWithProviders } from '../test/render'
import ComparePage from './ComparePage'

describe('ComparePage', () => {
  it('asks for at least two countries', async () => {
    renderWithProviders(<ComparePage />, { route: '/compare?countries=USA' })
    expect(await screen.findByText('Pick at least two countries')).toBeInTheDocument()
  })

  it('plots the selected countries and lists their latest values', async () => {
    renderWithProviders(<ComparePage />, { route: '/compare?countries=USA,DEU' })
    const latest = await screen.findByRole('list', { name: 'Latest values' })
    expect(within(latest).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      '14.2 t CO2/personUnited States · 2024',
      '6.8 t CO2/personGermany · 2024',
    ])
  })

  it('keeps a country color when another is removed', async () => {
    const { user } = renderWithProviders(<ComparePage />, { route: '/compare?countries=USA,IND,DEU' })
    const chips = await screen.findByRole('list', { name: 'Selected countries' })
    await within(chips).findByRole('link', { name: 'Germany' }) // names arrive with the country list
    const swatchOf = (name: string) =>
      (within(chips).getByRole('link', { name }).previousElementSibling as HTMLElement).style.background
    const germanyBefore = swatchOf('Germany')

    await user.click(screen.getByRole('button', { name: 'Remove India' }))
    expect(within(chips).queryByRole('link', { name: 'India' })).not.toBeInTheDocument()
    expect(swatchOf('Germany')).toBe(germanyBefore)
  })

  it('disables adding a fifth country', async () => {
    renderWithProviders(<ComparePage />, { route: '/compare?countries=USA,IND,DEU,QAT' })
    expect(await screen.findByRole('combobox', { name: 'Add a country' })).toBeDisabled()
  })

  it('has no axe violations', async () => {
    const { container } = renderWithProviders(<ComparePage />, { route: '/compare?countries=USA,DEU' })
    await screen.findByRole('list', { name: 'Latest values' })
    await expectNoAxeViolations(container)
  })
})
