import Card from '../../components/Card'

export default function Settings() {
  return (
    <div style={{ maxWidth: 640 }}>
      <Card title="Preferences">
        <div className="field">
          <label>Default order type</label>
          <select defaultValue="LIMIT">
            <option>MARKET</option>
            <option>LIMIT</option>
            <option>STOP</option>
          </select>
        </div>
        <div className="field">
          <label>Trade confirmations</label>
          <select defaultValue="ALWAYS">
            <option>ALWAYS</option>
            <option>ORDERS OVER $1,000</option>
            <option>NEVER</option>
          </select>
        </div>
        <div className="field">
          <label>Notifications</label>
          <select defaultValue="EMAIL">
            <option>EMAIL</option>
            <option>EMAIL + SMS</option>
            <option>PUSH ONLY</option>
          </select>
        </div>
        <button className="btn btn-primary">Save preferences</button>
      </Card>
    </div>
  )
}
