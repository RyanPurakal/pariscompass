import type { ReactNode } from 'react'
import { ApiError } from '../api/client'
import styles from './States.module.css'

/** Shown only on first load. Refetches keep the previous render (dimmed) instead of flashing this. */
export function LoadingBlock({ label, height = 240 }: { label: string; height?: number }) {
  return (
    <div className={styles.loading} style={{ minHeight: height }} role="status" aria-live="polite">
      <span className={styles.shimmer} aria-hidden="true" />
      <span className="visually-hidden">{label}</span>
    </div>
  )
}

/** Explains what failed using the backend's problem code, and offers a retry when that could help. */
export function ErrorState({ error, onRetry, title = 'Something went wrong' }: { error: unknown; onRetry?: () => void; title?: string }) {
  const apiError = error instanceof ApiError ? error : null
  const message = apiError?.message ?? (error instanceof Error ? error.message : 'Unknown error')
  const retryable = !apiError || apiError.status === 0 || apiError.status >= 500
  return (
    <div className={styles.error} role="alert">
      <p className={styles.title}>{title}</p>
      <p className={styles.detail}>{message}</p>
      {apiError && apiError.code !== 'NETWORK_ERROR' && <p className={styles.code}>{apiError.code}</p>}
      {onRetry && retryable && (
        <button type="button" className="button button-quiet" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className={styles.empty}>
      <p className={styles.title}>{title}</p>
      {children && <div className={styles.detail}>{children}</div>}
    </div>
  )
}
