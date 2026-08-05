import { useEffect, useState } from 'react'

/**
 * Tiny hook to fetch data on mount.
 *
 * const { data, loading, error } = useApi(getHoldings)
 *
 * Returns null for data until the request completes. If the request fails,
 * `error` is set. If the component unmounts before it finishes, state updates
 * are skipped (that's what the `alive` flag guards against).
 *
 * For anything real (caching, refetching, background updates) you'd reach
 * for React Query or SWR. This is deliberately minimal for learning.
 */
export function useApi<T>(fetcher: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true
    setLoading(true)
    setError(null)

    fetcher()
      .then((d) => {
        if (alive) setData(d)
      })
      .catch((e) => {
        if (alive) setError(e?.message ?? 'Request failed')
      })
      .finally(() => {
        if (alive) setLoading(false)
      })

    return () => {
      alive = false
    }
    // fetcher is intentionally not in deps — we only want to fetch once on mount.
  }, [])

  return { data, loading, error }
}
