import { api } from './client'
import type { Holding, Order } from '../data/mock'

// -------- Response types (mirror Spring DTO shapes) --------

export type SeriesPoint = { label: string; value: number }

export type WatchlistItem = {
  symbol: string
  name: string
  price: number
  changePct: number
}

export type Profile = {
  name: string
  email: string
  memberSince: string
  kycStatus: 'VERIFIED' | 'PENDING' | 'REJECTED'
  tier: string
}

// -------- API calls --------
// Each call is a thin wrapper. Add error handling / retries here later.

export const getHoldings = () =>
  api.get<Holding[]>('/holdings').then((r) => r.data)

export const getPortfolioSeries = () =>
  api.get<SeriesPoint[]>('/portfolio-series').then((r) => r.data)

export const getWatchlist = () =>
  api.get<WatchlistItem[]>('/watchlist').then((r) => r.data)

export const getOrders = () =>
  api.get<Order[]>('/orders').then((r) => r.data)

export const getProfile = () =>
  api.get<Profile>('/profile').then((r) => r.data)
