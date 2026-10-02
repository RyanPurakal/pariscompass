import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { expectNoAxeViolations } from '../test/axe'
import { ThemeProvider } from '../lib/theme'
import { AppShell } from './AppShell'

function renderShell() {
  const router = createMemoryRouter(
    [{ element: <AppShell />, children: [{ index: true, element: <h1>Home</h1> }, { path: 'country/:iso3', element: <h1>Country</h1> }] }],
    { initialEntries: ['/'] },
  )
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <RouterProvider router={router} />
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('AppShell', () => {
  it('has a skip link, main navigation and a main landmark', () => {
    renderShell()
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main')
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument()
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main')
  })

  it('switches theme and remembers the choice', async () => {
    renderShell()
    await userEvent.click(screen.getByRole('radio', { name: 'Dark' }))
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem('paris-compass-theme')).toBe('dark')
    await userEvent.click(screen.getByRole('radio', { name: 'Auto' }))
    expect(localStorage.getItem('paris-compass-theme')).toBeNull()
  })

  it('navigates to a country chosen in the search', async () => {
    renderShell()
    const user = userEvent.setup()
    await user.type(screen.getByRole('combobox', { name: 'Find a country' }), 'germ')
    await user.click(await screen.findByRole('option', { name: /Germany/ }))
    expect(await screen.findByRole('heading', { name: 'Country' })).toBeInTheDocument()
  })

  it('has no axe violations', async () => {
    const { container } = renderShell()
    await expectNoAxeViolations(container)
  })
})
