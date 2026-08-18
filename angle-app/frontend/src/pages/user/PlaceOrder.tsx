import { useState } from 'react'
import Card from '../../components/Card'

export default function PlaceOrder() {
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY')
  const [symbol, setSymbol] = useState('NBEAM')
  const [qty, setQty] = useState('10')
  const [orderType, setOrderType] = useState('LIMIT')
  const [limitPrice, setLimitPrice] = useState('128.40')

  const estCost = (parseFloat(qty || '0') * parseFloat(limitPrice || '0')).toFixed(2)
  const actionColor = side === 'BUY' ? 'var(--green)' : 'var(--red)'

  return (
    <div className="grid grid-2" style={{ maxWidth: 820 }}>
      <Card title="Place order">
        <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
          <button
            className={`btn ${side === 'BUY' ? 'btn-green' : 'btn-secondary'}`}
            style={{ flex: 1 }}
            onClick={() => setSide('BUY')}
          >
            Buy
          </button>
          <button
            className="btn"
            style={{
              flex: 1,
              background: side === 'SELL' ? 'var(--red)' : 'var(--white)',
              color: side === 'SELL' ? '#fff' : 'var(--ink)',
              border: `1px solid ${side === 'SELL' ? 'var(--red)' : 'var(--line)'}`
            }}
            onClick={() => setSide('SELL')}
          >
            Sell
          </button>
        </div>
        <div className="field">
          <label>Symbol</label>
          <input value={symbol} onChange={(e) => setSymbol(e.target.value.toUpperCase())} />
        </div>
        <div className="field">
          <label>Quantity</label>
          <input type="number" value={qty} onChange={(e) => setQty(e.target.value)} />
        </div>
        <div className="field">
          <label>Order type</label>
          <select value={orderType} onChange={(e) => setOrderType(e.target.value)}>
            <option>MARKET</option>
            <option>LIMIT</option>
            <option>STOP</option>
          </select>
        </div>
        {orderType === 'LIMIT' && (
          <div className="field">
            <label>Limit price</label>
            <input
              type="number"
              step="0.01"
              value={limitPrice}
              onChange={(e) => setLimitPrice(e.target.value)}
            />
          </div>
        )}
        <button
          className="btn"
          style={{ width: '100%', marginTop: 8, background: actionColor, color: '#fff' }}
        >
          Review {side.toLowerCase()} order
        </button>
      </Card>

      <Card title="Estimate">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <div className="sub" style={{ marginTop: 0 }}>Side</div>
            <div style={{ fontWeight: 600 }}>
              <span className={`badge ${side === 'BUY' ? 'badge-green' : 'badge-red'}`}>{side}</span>
            </div>
          </div>
          <div>
            <div className="sub" style={{ marginTop: 0 }}>Symbol</div>
            <div style={{ fontWeight: 600 }}>{symbol || '—'}</div>
          </div>
          <div>
            <div className="sub" style={{ marginTop: 0 }}>Order type</div>
            <div style={{ fontWeight: 600 }}>{orderType}</div>
          </div>
          <div>
            <div className="sub" style={{ marginTop: 0 }}>Quantity</div>
            <div className="mono" style={{ fontWeight: 600 }}>{qty || '0'}</div>
          </div>
          <div>
            <div className="sub" style={{ marginTop: 0 }}>Estimated {side === 'BUY' ? 'cost' : 'proceeds'}</div>
            <div className="mono" style={{ fontSize: 22, fontWeight: 600 }}>${estCost}</div>
          </div>
        </div>
      </Card>
    </div>
  )
}
