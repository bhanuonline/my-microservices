import Card from '../../components/Card'
import DataTable, { Column } from '../../components/DataTable'
import { Loading, ErrorMessage } from '../../components/LoadingState'
import { useApi } from '../../hooks/useApi'
import { getWatchlist, WatchlistItem } from '../../api/endpoints'

const columns: Column<WatchlistItem>[] = [
  {
    key: 'symbol',
    header: 'Symbol',
    render: (r) => (
      <>
        <strong>{r.symbol}</strong>
        <div className="sub">{r.name}</div>
      </>
    )
  },
  { key: 'price', header: 'Price', render: (r) => <span className="mono">${r.price.toLocaleString()}</span> },
  {
    key: 'changePct',
    header: '24h',
    render: (r) => (
      <span className={`mono ${r.changePct >= 0 ? 'up' : 'down'}`}>
        {r.changePct >= 0 ? '+' : ''}
        {r.changePct}%
      </span>
    )
  },
  {
    key: 'action',
    header: '',
    render: (r) => (
      <button className="btn btn-secondary" style={{ padding: '6px 12px', fontSize: 13 }}>
        Trade {r.symbol}
      </button>
    )
  }
]

export default function Watchlist() {
  const { data, loading, error } = useApi(getWatchlist)

  if (loading) return <Loading label="Loading watchlist…" />
  if (error) return <ErrorMessage message={error} />
  if (!data) return null

  return (
    <Card
      title="Your watchlist"
      actions={<button className="btn btn-primary" style={{ padding: '8px 14px', fontSize: 13 }}>+ Add symbol</button>}
    >
      <DataTable columns={columns} rows={data} />
    </Card>
  )
}
