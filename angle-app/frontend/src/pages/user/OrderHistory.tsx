import Card from '../../components/Card'
import DataTable, { Column } from '../../components/DataTable'
import { Loading, ErrorMessage } from '../../components/LoadingState'
import { useApi } from '../../hooks/useApi'
import { getOrders } from '../../api/endpoints'
import type { Order } from '../../data/mock'

const statusBadge = (s: string) => {
  const cls = s === 'FILLED' ? 'badge-green' : s === 'PENDING' ? 'badge-amber' : 'badge-muted'
  return <span className={`badge ${cls}`}>{s}</span>
}

const columns: Column<Order>[] = [
  { key: 'id',     header: 'Order ID', render: (r) => <span className="mono">{r.id}</span> },
  { key: 'symbol', header: 'Symbol',   render: (r) => <strong>{r.symbol}</strong> },
  {
    key: 'side',
    header: 'Side',
    render: (r) => <span className={`badge ${r.side === 'BUY' ? 'badge-green' : 'badge-red'}`}>{r.side}</span>
  },
  { key: 'qty',    header: 'Qty',    render: (r) => <span className="mono">{r.qty}</span> },
  { key: 'price',  header: 'Price',  render: (r) => <span className="mono">${r.price.toLocaleString()}</span> },
  { key: 'status', header: 'Status', render: (r) => statusBadge(r.status) },
  { key: 'date',   header: 'Date',   render: (r) => <span className="mono" style={{ fontSize: 13 }}>{r.date}</span> }
]

export default function OrderHistory() {
  const { data, loading, error } = useApi(getOrders)

  if (loading) return <Loading label="Loading orders…" />
  if (error) return <ErrorMessage message={error} />
  if (!data) return null

  return (
    <Card title={`Orders (${data.length})`}>
      <DataTable columns={columns} rows={data} />
    </Card>
  )
}
