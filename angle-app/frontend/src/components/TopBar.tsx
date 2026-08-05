type Props = { title: string; role: 'User' | 'Admin' }

export default function TopBar({ title, role }: Props) {
  const isAdmin = role === 'Admin'
  return (
    <header className="topbar">
      <h1>{title}</h1>
      <div className="user-chip">
        <span
          style={{
            width: 24,
            height: 24,
            borderRadius: '50%',
            background: isAdmin ? 'var(--amber)' : 'var(--green)',
            color: '#fff',
            fontSize: 11,
            display: 'grid',
            placeItems: 'center',
            fontWeight: 700
          }}
        >
          {isAdmin ? 'P' : 'A'}
        </span>
        {isAdmin ? 'Priya (Admin)' : 'Alex (Trader)'}
      </div>
    </header>
  )
}
