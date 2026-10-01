import { Link } from 'react-router'
import { EmptyState } from '../components/States'

export function NotFoundPage() {
  return (
    <EmptyState title="This page is off the map">
      <Link to="/">Back to the atlas</Link>
    </EmptyState>
  )
}
