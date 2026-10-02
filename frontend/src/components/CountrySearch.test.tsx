import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../test/render'
import { CountrySearch } from './CountrySearch'

describe('CountrySearch', () => {
  it('filters by name and picks with the keyboard', async () => {
    const onSelect = vi.fn()
    const { user } = renderWithProviders(<CountrySearch onSelect={onSelect} />)
    const input = screen.getByRole('combobox', { name: 'Find a country' })

    await user.type(input, 'i')
    const options = await screen.findAllByRole('option')
    // Names starting with the query come first.
    expect(options.map((o) => o.textContent)).toEqual(['IndiaIND', 'United StatesUSA'])
    expect(input).toHaveAttribute('aria-expanded', 'true')

    await user.keyboard('{ArrowDown}{Enter}')
    expect(onSelect).toHaveBeenCalledWith({ iso3: 'USA', name: 'United States' })
    expect(input).toHaveValue('')
  })

  it('matches an exact ISO3 code and closes on Escape', async () => {
    const { user } = renderWithProviders(<CountrySearch onSelect={vi.fn()} />)
    const input = screen.getByRole('combobox')
    await user.type(input, 'deu')
    expect(await screen.findByRole('option', { name: /Germany/ })).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{Escape}')
    expect(input).toHaveAttribute('aria-expanded', 'false')
  })

  it('leaves out excluded countries', async () => {
    const { user } = renderWithProviders(<CountrySearch onSelect={vi.fn()} exclude={['IND']} />)
    await user.type(screen.getByRole('combobox'), 'ind')
    expect(screen.queryByRole('option', { name: /India/ })).not.toBeInTheDocument()
  })
})
