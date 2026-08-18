import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from '../components/Sidebar'
import TopBar from '../components/TopBar'

const items = [
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/trades', label: 'Trade activity' },
  { to: '/admin/revenue', label: 'Revenue' },
  { to: '/admin/health', label: 'System health' },
  { to: '/admin/feature-flags', label: 'Feature flags' }
]

const titles: Record<string, string> = {
  '/admin/users': 'Users',
  '/admin/trades': 'Trade activity',
  '/admin/revenue': 'Revenue',
  '/admin/health': 'System health',
  '/admin/feature-flags': 'Feature flags'
}

export default function AdminLayout() {
  const { pathname } = useLocation()
  const title = titles[pathname] ?? 'Admin'
  return (
    <div className="shell">
      <Sidebar title="Admin" items={items} />
      <main className="main">
        <TopBar title={title} role="Admin" />
        <div className="content">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
