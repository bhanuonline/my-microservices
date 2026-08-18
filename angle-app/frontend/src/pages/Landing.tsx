import { Link } from 'react-router-dom'

export default function Landing() {
  return (
    <div className="landing">
      <div className="landing-card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, marginBottom: 20 }}>
          <svg width="28" height="28" viewBox="0 0 26 26" fill="none">
            <path d="M2 20 L10 9 L15 15 L24 3" stroke="#0E9F6E" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
            <circle cx="24" cy="3" r="2.2" fill="#F2B705" />
          </svg>
          <span style={{ fontFamily: 'var(--font-display)', fontWeight: 600, fontSize: 20 }}>Northbeam</span>
        </div>
        <h1>Dashboard preview</h1>
        <p>Real login lands with OAuth. For now, pick a role to preview both perspectives.</p>
        <div className="landing-choices">
          <Link to="/user/portfolio" className="landing-choice">
            <h3>Trader view →</h3>
            <p>Portfolio, watchlist, orders, place trades, profile &amp; KYC, preferences.</p>
          </Link>
          <Link to="/admin/users" className="landing-choice">
            <h3>Admin view →</h3>
            <p>Users, live trades, revenue &amp; volume charts, system health, feature flags.</p>
          </Link>
        </div>
      </div>
    </div>
  )
}
