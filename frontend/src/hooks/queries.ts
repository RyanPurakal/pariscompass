import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Topology } from 'topojson-specification'
import { api, type ApiError, type CountryProjectionResponse } from '../api/client'

/**
 * Query keys in one place, so invalidation and prefetching never drift from the fetchers.
 * Reference data (countries, metrics, map shapes) only changes when the ETL runs, so it stays fresh
 * for an hour; per-country data for five minutes.
 */
export const queryKeys = {
  countries: ['countries'] as const,
  metrics: ['metrics'] as const,
  geo: ['geo'] as const,
  countryMetrics: (iso3: string) => ['country', iso3, 'metrics'] as const,
  series: (iso3: string, metrics: string[], from?: number, to?: number) =>
    ['country', iso3, 'series', metrics, from ?? null, to ?? null] as const,
  alignment: (iso3: string) => ['country', iso3, 'alignment'] as const,
  projections: (iso3: string) => ['country', iso3, 'projections'] as const,
  rankings: (metric: string, year?: number) => ['rankings', metric, year ?? 'default'] as const,
  compare: (countries: string[], metric: string, from?: number, to?: number) =>
    ['compare', countries, metric, from ?? null, to ?? null] as const,
}

const HOUR = 60 * 60 * 1000
const FIVE_MINUTES = 5 * 60 * 1000

export function useCountries() {
  return useQuery({ queryKey: queryKeys.countries, queryFn: ({ signal }) => api.countries(signal), staleTime: HOUR })
}

export function useMetrics() {
  return useQuery({ queryKey: queryKeys.metrics, queryFn: ({ signal }) => api.metrics(signal), staleTime: HOUR })
}

export function useWorldGeometry() {
  return useQuery({
    queryKey: queryKeys.geo,
    queryFn: async ({ signal }) => {
      const url = new URL(`${import.meta.env.BASE_URL}geo/countries-110m.json`, window.location.origin)
      const res = await fetch(url, { signal })
      if (!res.ok) throw new Error(`Map shapes failed to load (HTTP ${res.status})`)
      return (await res.json()) as Topology
    },
    staleTime: Infinity,
  })
}

export function useCountryMetrics(iso3: string) {
  return useQuery({
    queryKey: queryKeys.countryMetrics(iso3),
    queryFn: ({ signal }) => api.countryMetrics(iso3, signal),
    staleTime: FIVE_MINUTES,
  })
}

export function useCountrySeries(iso3: string, metrics: string[], from?: number, to?: number) {
  return useQuery({
    queryKey: queryKeys.series(iso3, metrics, from, to),
    queryFn: ({ signal }) => api.series(iso3, { metrics, from, to }, signal),
    staleTime: FIVE_MINUTES,
    placeholderData: keepPreviousData,
  })
}

export function useAlignment(iso3: string) {
  return useQuery({
    queryKey: queryKeys.alignment(iso3),
    queryFn: ({ signal }) => api.alignment(iso3, signal),
    staleTime: FIVE_MINUTES,
  })
}

/** Rankings drive the map; the previous year's render is kept while the next one loads. */
export function useRankings(metric: string | undefined, year: number | undefined) {
  return useQuery({
    queryKey: queryKeys.rankings(metric ?? '', year),
    queryFn: ({ signal }) => api.rankings({ metric: metric!, year, limit: 300 }, signal),
    enabled: Boolean(metric),
    staleTime: HOUR,
    placeholderData: keepPreviousData,
  })
}

export function useComparison(countries: string[], metric: string, from?: number, to?: number) {
  return useQuery({
    queryKey: queryKeys.compare(countries, metric, from, to),
    queryFn: ({ signal }) => api.compare({ countries, metric, from, to }, signal),
    enabled: countries.length >= 2 && countries.length <= 4 && Boolean(metric),
    staleTime: FIVE_MINUTES,
    placeholderData: keepPreviousData,
  })
}

export function useProjectionHistory(iso3: string) {
  return useQuery({
    queryKey: queryKeys.projections(iso3),
    queryFn: ({ signal }) => api.projections(iso3, 10, signal),
    staleTime: FIVE_MINUTES,
  })
}

/** POST, because it may call a paid model and is rate limited; never fired automatically. */
export function useGenerateProjection(iso3: string) {
  const queryClient = useQueryClient()
  return useMutation<CountryProjectionResponse, ApiError>({
    mutationFn: () => api.projection(iso3),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.projections(iso3) }),
  })
}
