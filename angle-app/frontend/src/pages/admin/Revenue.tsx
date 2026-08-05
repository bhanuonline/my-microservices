import Card from '../../components/Card'
import StatTile from '../../components/StatTile'
import LineChart from '../../components/LineChart'
import BarChart from '../../components/BarChart'
import { revenueSeries, volumeSeries } from '../../data/mock'

export default function Revenue() {
  const total = revenueSeries.reduce((s, r) => s + r.value, 0)
  const last = revenueSeries[revenueSeries.length - 1].value
  const prev = revenueSeries[revenueSeries.length - 2].value
  const deltaPct = ((last - prev) / prev) * 100

  return (
    <>
      <div className="grid grid-3" style={{ marginBottom: 20 }}>
        <StatTile
          label="Revenue (12mo)"
          value={`$${(total / 1_000_000).toFixed(2)}M`}
        />
        <StatTile
          label="MoM growth"
          value={`${deltaPct >= 0 ? '+' : ''}${deltaPct.toFixed(1)}%`}
          delta={`vs. prior month`}
          deltaUp={deltaPct >= 0}
        />
        <StatTile
          label="Trade volume (last mo)"
          value={`${volumeSeries[volumeSeries.length - 1].value.toLocaleString()}K`}
        />
      </div>
      <div style={{ marginBottom: 20 }}>
        <Card title="Revenue by month">
          <LineChart data={revenueSeries} color="#0E9F6E" height={280} />
        </Card>
      </div>
      <Card title="Trade volume (thousands of trades)">
        <BarChart data={volumeSeries} color="#10182B" height={240} />
      </Card>
    </>
  )
}
