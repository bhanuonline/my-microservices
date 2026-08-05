import { NavLink } from 'react-router-dom'

type Item = { to: string; label: string }
type Props = { title: string; items: Item[] }

export default function Sidebar({ title, items }: Props) {
  return (
    <aside className="sidebar">
      <div className="logo">
        <svg width="24" height="24" viewBox="0 0 26 26" fill="none">
          <path d="M2 20 L10 9 L15 15 L24 3" stroke="#0E9F6E" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="24" cy="3" r="2.2" fill="#F2B705" />
        </svg>
        Northbeam
      </div>
      <div className="section-label">{title}</div>
      {items.map((item) => (
        <NavLink key={item.to} to={item.to} end className={({ isActive }) => (isActive ? 'active' : '')}>
          {item.label}
        </NavLink>
      ))}
      <div style={{ marginTop: 'auto', paddingTop: 20 }}>
        <NavLink to="/" style={{ color: 'var(--muted)', fontSize: 13 }}>← Back to landing</NavLink>
      </div>
    </aside>
  )
}
