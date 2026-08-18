import { useState } from 'react'
import Card from '../../components/Card'
import DataTable, { Column } from '../../components/DataTable'
import { users, AdminUser } from '../../data/mock'

const kycBadge = (s: AdminUser['kyc']) => {
  const cls = s === 'VERIFIED' ? 'badge-green' : s === 'PENDING' ? 'badge-amber' : 'badge-red'
  return <span className={`badge ${cls}`}>{s}</span>
}

const statusBadge = (s: AdminUser['status']) => (
  <span className={`badge ${s === 'ACTIVE' ? 'badge-green' : 'badge-red'}`}>{s}</span>
)

const columns: Column<AdminUser>[] = [
  { key: 'id',     header: 'ID',     render: (r) => <span className="mono">{r.id}</span> },
  { key: 'name',   header: 'Name',   render: (r) => <strong>{r.name}</strong> },
  { key: 'email',  header: 'Email',  render: (r) => <span style={{ color: 'var(--muted)' }}>{r.email}</span> },
  { key: 'tier',   header: 'Tier',   render: (r) => <span className="badge badge-muted">{r.tier}</span> },
  { key: 'kyc',    header: 'KYC',    render: (r) => kycBadge(r.kyc) },
  { key: 'joined', header: 'Joined', render: (r) => <span className="mono" style={{ fontSize: 13 }}>{r.joined}</span> },
  { key: 'status', header: 'Status', render: (r) => statusBadge(r.status) }
]

export default function Users() {
  const [q, setQ] = useState('')
  const filtered = users.filter((u) =>
    (u.name + u.email + u.id).toLowerCase().includes(q.toLowerCase())
  )

  return (
    <Card
      title={`All users (${filtered.length})`}
      actions={
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search…"
          style={{
            padding: '8px 12px',
            border: '1px solid var(--line)',
            borderRadius: 8,
            width: 260,
            fontSize: 13,
            fontFamily: 'inherit'
          }}
        />
      }
    >
      <DataTable columns={columns} rows={filtered} />
    </Card>
  )
}
