import Card from '../../components/Card'
import StatTile from '../../components/StatTile'
import DataTable, { Column } from '../../components/DataTable'
import { health } from '../../data/mock'

type Service = (typeof health.services)[number]

const statusBadge = (s: Service['status']) => {
  const cls = s === 'UP' ? 'badge-green' : s === 'DEGRADED' ? 'badge-amber' : 'badge-red'
  return <span className={`badge ${cls}`}>{s}</span>
}

const columns: Column<Service>[] = [
  { key: 'name',    header: 'Service', render: (r) => <strong>{r.name}</strong> },
  { key: 'status',  header: 'Status',  render: (r) => statusBadge(r.status) },
  { key: 'latency', header: 'Latency', render: (r) => <span className="mono">{r.latency}</span> }
]

export default function SystemHealth() {
  return (
    <>
      <div className="grid grid-4" style={{ marginBottom: 20 }}>
        <StatTile label="Uptime (12mo)"    value={health.uptime} />
        <StatTile label="Median latency"   value={health.medianLatency} />
        <StatTile label="p99 latency"      value={health.p99Latency} />
        <StatTile label="Error rate"       value={health.errorRate} />
      </div>
      <Card title="Services">
        <DataTable columns={columns} rows={health.services} />
      </Card>
    </>
  )
}
