import { lazy, Suspense } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { AppShell } from './components/AppShell'
import { LoadingBlock } from './components/States'
import { ExplorePage } from './pages/ExplorePage'
import { NotFoundPage } from './pages/NotFoundPage'

// Recharts is only needed on these pages, so they load on demand and the atlas stays light.
const CountryPage = lazy(() => import('./pages/CountryPage'))
const ComparePage = lazy(() => import('./pages/ComparePage'))

const page = (element: React.ReactNode) => (
  <Suspense fallback={<LoadingBlock label="Loading page" height={480} />}>{element}</Suspense>
)

const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      { index: true, element: <ExplorePage /> },
      { path: 'country/:iso3', element: page(<CountryPage />) },
      { path: 'compare', element: page(<ComparePage />) },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])

export function App() {
  return <RouterProvider router={router} />
}
