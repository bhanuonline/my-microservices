import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from '../components/Sidebar'
import TopBar from '../components/TopBar'

const items = [
  { to: '/user/portfolio', label: 'Portfolio' },
  { to: '/user/watchlist', label: 'Watchlist' },
  { to: '/user/orders', label: 'Order history' },
  { to: '/user/place-order', label: 'Place order' },
  { to: '/user/profile', label: 'Profile' },
  { to: '/user/settings', label: 'Settings' }
]

const titles: Record<string, string> = {
  '/user/portfolio': 'Portfolio',
  '/user/watchlist': 'Watchlist',
  '/user/orders': 'Order history',
  '/user/place-order': 'Place order',
  '/user/profile': 'Profile',
  '/user/settings': 'Settings'
}

export default function UserLayout() {
  const { pathname } = useLocation()
  const title = titles[pathname] ?? 'Dashboard'
  return (
    <div className="shell">
      <Sidebar title="Trader" items={items} />
      <main className="main">
        <TopBar title={title} role="User" />
        <div className="content">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
