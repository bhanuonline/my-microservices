import Card from '../../components/Card'
import DataTable, { Column } from '../../components/DataTable'
import StatTile from '../../components/StatTile'
import { trades, Trade } from '../../data/mock'

const columns: Column<Trade>[] = [
  { key: 'time',   header: 'Time',     render: (r) => <span className="mono" style={{ fontSize: 13 }}>{r.time}</span> },
  { key: 'id',     header: 'Trade ID', render: (r) => <span className="mono">{r.id}</span> },
  { key: 'user',   header: 'User' },
  { key: 'symbol', header: 'Symbol',   render: (r) => <strong>{r.symbol}</strong> },
  {
    key: 'side',
    header: 'Side',
    render: (r) => <span className={`badge ${r.side === 'BUY' ? 'badge-green' : 'badge-red'}`}>{r.side}</span>
  },
  { key: 'qty',      header: 'Qty',      render: (r) => <span className="mono">{r.qty}</span> },
  { key: 'price',    header: 'Price',    render: (r) => <span className="mono">${r.price.toLocaleString()}</span> },
  {
    key: 'notional',
    header: 'Notional',
    render: (r) => (
      <span className="mono">
        ${r.notional.toLocaleString(undefined, { maximumFractionDigits: 2 })}
      </span>
    )
  }
]

export default function TradeActivity() {
  const totalNotional = trades.reduce((s, t) => s + t.notional, 0)
  return (
    <>
      <div className="grid grid-3" style={{ marginBottom: 20 }}>
        <StatTile label="Trades (5 min)" value={String(trades.length)} />
        <StatTile
          label="Notional (5 min)"
          value={`$${totalNotional.toLocaleString(undefined, { maximumFractionDigits: 0 })}`}
        />
        <StatTile label="Active traders" value="1,204" delta="+3.2%" deltaUp />
      </div>
      <Card title="Live trades">
        <DataTable columns={columns} rows={trades} />
      </Card>
    </>
  )
}
