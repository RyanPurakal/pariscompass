import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { ThemeProvider } from '../lib/theme'

/**
 * Renders with the real providers: a fresh QueryClient per test (no retries, so error states show
 * immediately), the theme, and a memory router at `route`.
 */
export function renderWithProviders(ui: ReactElement, { route = '/', path = '*' }: { route?: string; path?: string } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const user = userEvent.setup()
  const result = render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <MemoryRouter initialEntries={[route]}>
          <Routes>
            <Route path={path} element={ui} />
            <Route path="/country/:iso3" element={<p>Country page for navigation</p>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
  return { ...result, user, queryClient }
}
