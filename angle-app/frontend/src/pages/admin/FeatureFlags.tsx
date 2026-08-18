import { useState } from 'react'
import Card from '../../components/Card'
import { featureFlags as initial } from '../../data/mock'

export default function FeatureFlags() {
  const [flags, setFlags] = useState(initial)
  const toggle = (key: string) =>
    setFlags((fs) => fs.map((f) => (f.key === key ? { ...f, enabled: !f.enabled } : f)))

  return (
    <Card title="Feature flags">
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {flags.map((f, i) => (
          <div
            key={f.key}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '16px 4px',
              borderBottom: i < flags.length - 1 ? '1px solid var(--panel)' : 'none'
            }}
          >
            <div>
              <div style={{ fontWeight: 600, marginBottom: 2 }}>{f.label}</div>
              <div style={{ fontSize: 12, color: 'var(--muted)' }}>
                <span className="mono">{f.key}</span> · Rollout {f.rollout} · Updated {f.updated}
              </div>
            </div>
            <label style={{ position: 'relative', width: 44, height: 24, cursor: 'pointer', display: 'block' }}>
              <input
                type="checkbox"
                checked={f.enabled}
                onChange={() => toggle(f.key)}
                style={{ display: 'none' }}
              />
              <span
                style={{
                  position: 'absolute',
                  inset: 0,
                  background: f.enabled ? 'var(--green)' : 'var(--line)',
                  borderRadius: 100,
                  transition: 'background .15s'
                }}
              />
              <span
                style={{
                  position: 'absolute',
                  top: 2,
                  left: f.enabled ? 22 : 2,
                  width: 20,
                  height: 20,
                  background: 'var(--white)',
                  borderRadius: '50%',
                  transition: 'left .15s',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.15)'
                }}
              />
            </label>
          </div>
        ))}
      </div>
    </Card>
  )
}
