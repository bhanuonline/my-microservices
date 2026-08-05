import Card from '../../components/Card'
import StatTile from '../../components/StatTile'
import DataTable, { Column } from '../../components/DataTable'
import LineChart from '../../components/LineChart'
import { Loading, ErrorMessage } from '../../components/LoadingState'
import { useApi } from '../../hooks/useApi'
import { getHoldings, getPortfolioSeries } from '../../api/endpoints'
import type { Holding } from '../../data/mock'

const columns: Column<Holding>[] = [
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
  { key: 'qty',     header: 'Qty',      render: (r) => <span className="mono">{r.qty}</span> },
  { key: 'avgCost', header: 'Avg cost', render: (r) => <span className="mono">${r.avgCost.toFixed(2)}</span> },
  { key: 'price',   header: 'Price',    render: (r) => <span className="mono">${r.price.toFixed(2)}</span> },
  {
    key: 'changePct',
    header: 'Change',
    render: (r) => (
      <span className={`mono ${r.changePct >= 0 ? 'up' : 'down'}`}>
        {r.changePct >= 0 ? '+' : ''}
        {r.changePct}%
      </span>
    )
  },
  {
    key: 'value',
    header: 'Value',
    render: (r) => (
      <span className="mono">
        ${(r.qty * r.price).toLocaleString(undefined, { maximumFractionDigits: 2 })}
      </span>
    )
  }
]

export default function Portfolio() {
  const holdingsQ = useApi(getHoldings)
  const seriesQ = useApi(getPortfolioSeries)

  if (holdingsQ.loading || seriesQ.loading) return <Loading label="Loading portfolio…" />
  if (holdingsQ.error) return <ErrorMessage message={holdingsQ.error} />
  if (seriesQ.error) return <ErrorMessage message={seriesQ.error} />
  if (!holdingsQ.data || !seriesQ.data) return null

  const holdings = holdingsQ.data
  const series = seriesQ.data

  const totalValue = holdings.reduce((s, h) => s + h.qty * h.price, 0)
  const totalCost = holdings.reduce((s, h) => s + h.qty * h.avgCost, 0)
  const totalPL = totalValue - totalCost
  const plPct = totalCost === 0 ? 0 : (totalPL / totalCost) * 100

  return (
    <>
      <div className="grid grid-4" style={{ marginBottom: 20 }}>
        <StatTile label="Portfolio value" value={`$${totalValue.toLocaleString(undefined, { maximumFractionDigits: 0 })}`} />
        <StatTile
          label="Total P/L"
          value={`${totalPL >= 0 ? '+' : ''}$${Math.abs(totalPL).toLocaleString(undefined, { maximumFractionDigits: 0 })}`}
          delta={`${plPct >= 0 ? '+' : ''}${plPct.toFixed(2)}%`}
          deltaUp={totalPL >= 0}
        />
        <StatTile label="Positions" value={String(holdings.length)} />
        <StatTile label="Buying power" value="$12,480" />
      </div>

      <div style={{ marginBottom: 20 }}>
        <Card title="Portfolio value (30d)">
          <LineChart data={series} />
        </Card>
      </div>

      <Card title="Holdings">
        <DataTable columns={columns} rows={holdings} />
      </Card>
    </>
  )
}
