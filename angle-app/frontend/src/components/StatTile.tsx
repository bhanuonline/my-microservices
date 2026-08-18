type Props = { label: string; value: string; delta?: string; deltaUp?: boolean }

export default function StatTile({ label, value, delta, deltaUp }: Props) {
  return (
    <div className="card stat-tile">
      <div className="label">{label}</div>
      <div className="value">{value}</div>
      {delta && <div className={`delta ${deltaUp ? 'up' : 'down'}`}>{delta}</div>}
    </div>
  )
}
