import { createBrowserRouter, Navigate } from 'react-router-dom'
import Landing from './pages/Landing'
import UserLayout from './layouts/UserLayout'
import AdminLayout from './layouts/AdminLayout'
import Portfolio from './pages/user/Portfolio'
import Watchlist from './pages/user/Watchlist'
import OrderHistory from './pages/user/OrderHistory'
import PlaceOrder from './pages/user/PlaceOrder'
import Profile from './pages/user/Profile'
import Settings from './pages/user/Settings'
import Users from './pages/admin/Users'
import TradeActivity from './pages/admin/TradeActivity'
import SystemHealth from './pages/admin/SystemHealth'
import FeatureFlags from './pages/admin/FeatureFlags'
import Revenue from './pages/admin/Revenue'

export const router = createBrowserRouter([
  { path: '/', element: <Landing /> },
  {
    path: '/user',
    element: <UserLayout />,
    children: [
      { index: true, element: <Navigate to="portfolio" replace /> },
      { path: 'portfolio', element: <Portfolio /> },
      { path: 'watchlist', element: <Watchlist /> },
      { path: 'orders', element: <OrderHistory /> },
      { path: 'place-order', element: <PlaceOrder /> },
      { path: 'profile', element: <Profile /> },
      { path: 'settings', element: <Settings /> }
    ]
  },
  {
    path: '/admin',
    element: <AdminLayout />,
    children: [
      { index: true, element: <Navigate to="users" replace /> },
      { path: 'users', element: <Users /> },
      { path: 'trades', element: <TradeActivity /> },
      { path: 'revenue', element: <Revenue /> },
      { path: 'health', element: <SystemHealth /> },
      { path: 'feature-flags', element: <FeatureFlags /> }
    ]
  }
])
