// All fake data lives here. When we wire real APIs later, we'll replace
// these exports with fetch() calls returning the same shapes.

// -------- User data --------

export type Holding = {
  symbol: string
  name: string
  qty: number
  avgCost: number
  price: number
  changePct: number
}

export const holdings: Holding[] = [
  { symbol: 'NBEAM', name: 'Northbeam Composite', qty: 42, avgCost: 118.20, price: 128.42, changePct: 2.14 },
  { symbol: 'AUR', name: 'Auralite Metals', qty: 120, avgCost: 58.00, price: 64.10, changePct: -0.42 },
  { symbol: 'VLTX', name: 'Voltix Energy', qty: 15, avgCost: 195.40, price: 212.87, changePct: 1.05 },
  { symbol: 'BTC', name: 'Bitcoin', qty: 0.35, avgCost: 58200, price: 61240, changePct: 3.10 },
  { symbol: 'CORV', name: 'Corvex Bio', qty: 200, avgCost: 12.50, price: 11.90, changePct: -1.80 }
]

export const portfolioSeries = Array.from({ length: 30 }, (_, i) => ({
  label: `D${i + 1}`,
  value: 45000 + Math.round(Math.sin(i * 0.4) * 2000 + i * 180)
}))

export const watchlist = [
  { symbol: 'NBEAM', name: 'Northbeam Composite', price: 128.42, changePct: 2.14 },
  { symbol: 'AUR', name: 'Auralite Metals', price: 64.10, changePct: -0.42 },
  { symbol: 'VLTX', name: 'Voltix Energy', price: 212.87, changePct: 1.05 },
  { symbol: 'BTC', name: 'Bitcoin', price: 61240, changePct: 3.10 },
  { symbol: 'TSLA', name: 'Tesla', price: 245.10, changePct: -0.85 },
  { symbol: 'AAPL', name: 'Apple', price: 189.50, changePct: 0.62 }
]

export type Order = {
  id: string
  symbol: string
  side: 'BUY' | 'SELL'
  qty: number
  price: number
  status: 'FILLED' | 'PENDING' | 'CANCELLED'
  date: string
}

export const orders: Order[] = [
  { id: 'O-24019', symbol: 'NBEAM', side: 'BUY',  qty: 10,   price: 128.40, status: 'FILLED',    date: '2026-07-30 10:12' },
  { id: 'O-24018', symbol: 'AUR',   side: 'SELL', qty: 50,   price: 64.20,  status: 'FILLED',    date: '2026-07-29 15:44' },
  { id: 'O-24017', symbol: 'BTC',   side: 'BUY',  qty: 0.05, price: 60800,  status: 'PENDING',   date: '2026-07-29 09:22' },
  { id: 'O-24016', symbol: 'VLTX',  side: 'BUY',  qty: 5,    price: 210.00, status: 'CANCELLED', date: '2026-07-28 12:00' },
  { id: 'O-24015', symbol: 'CORV',  side: 'BUY',  qty: 200,  price: 12.50,  status: 'FILLED',    date: '2026-07-27 11:33' }
]

export const profile = {
  name: 'Alex Kim',
  email: 'alex.kim@example.com',
  memberSince: 'Mar 2024',
  kycStatus: 'VERIFIED' as 'VERIFIED' | 'PENDING' | 'REJECTED',
  tier: 'Silver'
}

// -------- Admin data --------

export type AdminUser = {
  id: string
  name: string
  email: string
  tier: string
  kyc: 'VERIFIED' | 'PENDING' | 'REJECTED'
  joined: string
  status: 'ACTIVE' | 'SUSPENDED'
}

export const users: AdminUser[] = [
  { id: 'U-1001', name: 'Alex Kim',    email: 'alex.kim@example.com', tier: 'Silver',   kyc: 'VERIFIED', joined: '2024-03-11', status: 'ACTIVE' },
  { id: 'U-1002', name: 'Priya Rao',   email: 'priya@example.com',    tier: 'Gold',     kyc: 'VERIFIED', joined: '2023-11-04', status: 'ACTIVE' },
  { id: 'U-1003', name: 'John Turner', email: 'jt@example.com',       tier: 'Bronze',   kyc: 'PENDING',  joined: '2026-06-19', status: 'ACTIVE' },
  { id: 'U-1004', name: 'Maria Sato',  email: 'maria@example.com',    tier: 'Silver',   kyc: 'VERIFIED', joined: '2025-01-22', status: 'SUSPENDED' },
  { id: 'U-1005', name: 'Diego Vega',  email: 'diego@example.com',    tier: 'Platinum', kyc: 'VERIFIED', joined: '2022-08-01', status: 'ACTIVE' },
  { id: 'U-1006', name: 'Yuki Ito',    email: 'yuki@example.com',     tier: 'Bronze',   kyc: 'REJECTED', joined: '2026-07-14', status: 'ACTIVE' }
]

export type Trade = {
  id: string
  user: string
  symbol: string
  side: 'BUY' | 'SELL'
  qty: number
  price: number
  notional: number
  time: string
}

export const trades: Trade[] = [
  { id: 'T-93021', user: 'Alex Kim',    symbol: 'NBEAM', side: 'BUY',  qty: 10,   price: 128.40, notional: 1284,    time: '10:12:04' },
  { id: 'T-93020', user: 'Priya Rao',   symbol: 'BTC',   side: 'SELL', qty: 0.12, price: 61140,  notional: 7336.80, time: '10:11:52' },
  { id: 'T-93019', user: 'Diego Vega',  symbol: 'VLTX',  side: 'BUY',  qty: 25,   price: 212.90, notional: 5322.50, time: '10:11:38' },
  { id: 'T-93018', user: 'Maria Sato',  symbol: 'AUR',   side: 'SELL', qty: 100,  price: 64.05,  notional: 6405,    time: '10:11:19' },
  { id: 'T-93017', user: 'John Turner', symbol: 'AAPL',  side: 'BUY',  qty: 8,    price: 189.60, notional: 1516.80, time: '10:10:57' },
  { id: 'T-93016', user: 'Yuki Ito',    symbol: 'TSLA',  side: 'BUY',  qty: 4,    price: 245.20, notional: 980.80,  time: '10:10:31' }
]

export const health = {
  uptime: '99.98%',
  medianLatency: '42ms',
  p99Latency: '186ms',
  apiRequestsMin: 12840,
  errorRate: '0.03%',
  services: [
    { name: 'Order matching',  status: 'UP' as const,       latency: '9ms' },
    { name: 'Market data',     status: 'UP' as const,       latency: '4ms' },
    { name: 'Auth',            status: 'UP' as const,       latency: '22ms' },
    { name: 'Wallet gateway',  status: 'DEGRADED' as const, latency: '410ms' },
    { name: 'KYC provider',    status: 'UP' as const,       latency: '180ms' }
  ]
}

export type FeatureFlag = {
  key: string
  label: string
  enabled: boolean
  rollout: string
  updated: string
}

export const featureFlags: FeatureFlag[] = [
  { key: 'options-trading',  label: 'Options trading',       enabled: true,  rollout: '100%', updated: '2026-07-20' },
  { key: 'crypto-margin',    label: 'Crypto margin',         enabled: false, rollout: '0%',   updated: '2026-07-14' },
  { key: 'new-order-ticket', label: 'New order ticket UI',   enabled: true,  rollout: '50%',  updated: '2026-07-28' },
  { key: 'dark-mode',        label: 'Dark mode',             enabled: false, rollout: '0%',   updated: '2026-05-01' },
  { key: 'ai-signals',       label: 'AI trade signals',      enabled: true,  rollout: '10%',  updated: '2026-07-31' }
]

const months = ['Aug', 'Sep', 'Oct', 'Nov', 'Dec', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul']

export const revenueSeries = months.map((m, i) => ({
  label: m,
  value: 180000 + Math.round(i * 12000 + Math.sin(i * 0.7) * 22000)
}))

export const volumeSeries = months.map((m, i) => ({
  label: m,
  value: 1200 + Math.round(i * 55 + Math.sin(i) * 90)
}))
